package ecologylab.xml;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import org.w3c.dom.Node;

import ecologylab.generic.Debug;
import ecologylab.generic.ReflectionTools;
import ecologylab.xml.types.element.Mappable;
import ecologylab.xml.types.scalar.ScalarType;
import ecologylab.xml.types.scalar.TypeRegistry;

/**
 * Holds optimizations for translating from XML a single tag or attribute
 * within a contextualizing ElementState subclass.
 *
 * @author andruid
 */
class ElementDescriptor extends Debug
implements ClassTypes
{
	private  String				tag;
	
	private boolean				isID;
	
	private int					type;
	
	private final ClassDescriptor	optimizations;
	
	private String				nameSpaceID;
	
	/**
	 * The Field object that we should set. 
	 * Usually this is defined in the context of the original ElementState object passed to
	 * the constructor. It corresponds to the tag originally passed in.
	 * However, in the case of an XML Namespace, it may be defined in the context of a nested field.
	 */
	private Field				field;
	
	private Method				setMethod;
	
	private ScalarType<?>	 	scalarType;
	
	/**
	 * Array of format annotation Strings.
	 */
	private String[] 			format;

	/**
	 * true for LEAF_NODE_VALUE entries. Value not used for other types.
	 */
	private	boolean				isCDATA;
	
	/**
	 * Usually the class of the field corresponding to the tag in the context of the original ElementState 
	 * object passed to the constructor. However, n the case of an XML Namespace, it may be the class
	 * of a nested Namespace object.
	 */
	private Class<? extends ElementState>	classOp;
	
	private TranslationScope				translationSpace;
	
	private ElementDescriptor			nestedPTE;
	
	/**
	 * Construct from a Field object, for Fields declared with @xml_tag
	 * @param translationSpace
	 * @param optimizations
	 * @param field
	 * @param tag
	 * @param isAttribute
	 */
	ElementDescriptor(TranslationScope translationSpace, ClassDescriptor optimizations, Field field, String tag, boolean isAttribute)
	{
		super();
		this.tag				= tag;
		this.translationSpace	= translationSpace;
		this.optimizations		= optimizations;
		
		this.field				= field;
		
		boolean isScalar		= false;
		
		if (isAttribute)
		{
			isScalar			= true;
			this.type			= REGULAR_ATTRIBUTE;
		}
		else if (XMLTools.representAsLeafNode(field))
		{
			isScalar			= true;
			this.type			= LEAF_NODE_VALUE;
			setCDATA(field);
		}
		else
		{
			this.type			= REGULAR_NESTED_ELEMENT;
			Class<?> fieldClass = field.getType();
			if (!ElementState.class.isAssignableFrom(fieldClass))
			{	//FIXME -- should throw XMLTranslationException!? (but its *so* messy!!!)
				error("Java Field " + field.getName() + 
						" (mapped to XML tag <" + tag + ">) has metalanguage declarations indicating it should be a subclass of ElementState, but it is not.");
				return;
			}
			Class<? extends ElementState> esFieldClass	= (Class<? extends ElementState>) fieldClass;
			Class<? extends ElementState> classFromTS	= translationSpace.getClassBySimpleNameOfClass(esFieldClass);
			this.setClassOp((classFromTS != null) ? classFromTS : esFieldClass);
		}
		if (isScalar)
		{
			this.scalarType		= TypeRegistry.getType(field);
			format						= XMLTools.getFormatAnnotation(field);
		}
	}
	/**
	 * Construct from a field with @xml_class or @xml_classes.
	 * 
	 * @param translationSpace
	 * @param optimizations
	 * @param field
	 * @param thatClass
	 */
	ElementDescriptor(TranslationScope translationSpace, ClassDescriptor optimizations, Field field, Class thatClass)
	{
		this(translationSpace, optimizations, field, thatClass, XMLTools.getXmlTagName(thatClass, "State"));
	}
	ElementDescriptor(TranslationScope translationSpace, ClassDescriptor optimizations, Field field, Class thatClass, String tag)
	{
		super();
		this.tag							= tag;
		this.translationSpace	= translationSpace;
		this.optimizations		= optimizations;
		
		this.field						= field;
		this.type							= REGULAR_NESTED_ELEMENT;
		setClassOp(thatClass);
	}
	
	/**
	 * Construct from a Field for @xml_tag. NB: MUST BE A SCALAR TYPED FIELD.
	 * @param optimizations
	 * @param field
	 */
	ElementDescriptor(ClassDescriptor optimizations, Field field)
	{
		super();
		Class<?> scalarTypeClass = field.getType();
		this.tag				= XMLTools.getXmlTagName(scalarTypeClass, "");
		this.optimizations		= optimizations;
		
		this.field				= field;
		this.type				= TEXT_NODE_VALUE;
		this.scalarType			= TypeRegistry.getType(scalarTypeClass);
	}
	
	void setTranslationScope(TranslationScope translationSpace)
	{
		this.translationSpace	= translationSpace;
	}

	/**
	 * Normal constructor.
	 * 
	 * @param translationSpace
	 * @param optimizations
	 * @param context
	 * @param tag		Tag corresponding to the value in the XML that we're translating.
	 * @param isAttribute TODO
	 */
	ElementDescriptor(TranslationScope translationSpace, ClassDescriptor optimizations, ElementState context, String tag, boolean isAttribute)
	{
		super();
		this.tag				= tag;
		this.translationSpace	= translationSpace;
		this.optimizations		= optimizations;
		
		Class<? extends ElementState> contextClass 		= context.getClass();
		int colonIndex			= tag.indexOf(':');
		
		String fieldName	= XMLTools.fieldNameFromElementName(tag);
		Field field			= optimizations.getField(fieldName);
		this.field			= field;
		
		if (isAttribute)
		{
			if ("id".equals(tag))
				isID			= true;
			
			if (colonIndex > -1)
			{	
				if (tag.startsWith("xmlns:"))
				{
					this.type			= XMLNS_ATTRIBUTE;
					if (tag.length() > 6)
					{
						this.tag		= tag.substring(6);	// save the id that comes after xmlns
					}
				}
				else	//TODO -- support namespace attributes?! (whatever that is)
				{
					this.type			= IGNORED_ATTRIBUTE;
				}
				return;
			}
			
			setupScalarValue(tag, field, contextClass, true);
			return;
		}
		
		// element, not attribute
		if (colonIndex > 0)
		{	// there is an XML namespace specified in the XML!
			this.nameSpaceID		= tag.substring(0, colonIndex);
			String subTag			= tag.substring(colonIndex+1);
			
			// the new way
			if (!setupNamespaceElement(translationSpace, subTag, context))
				return;
		}
		else
		{	// no XML namespace; life is simpler.

			int diagnosedType	= ((field != null) && field.isAnnotationPresent(ElementState.xml_nested.class)) ? 
					REGULAR_NESTED_ELEMENT : setupScalarValue(tag, field, contextClass, false);

			switch (diagnosedType)
			{
			case LEAF_NODE_VALUE:
				this.classOp		= contextClass;
				setCDATA(field);
 				return;
			case REGULAR_NESTED_ELEMENT:    // this may be a temporary label -- not a leaf node or an attribute
				this.type	= REGULAR_NESTED_ELEMENT;
				// changed 12/2/06 by andruid -- use the type in the TranslationSpace!
				// ah, but that was wrong to do. you must get the class from the Field object,
				// because the Field object, and not the class is supposed to be a source.
				// but we need to support having 2 classes with the same simple name, in which one overrides the other,
				// for internal vs external versions of APIs.
				//
				// thus, what we do is:
				// 1) get the class name from the field
				// 2) use it as a key into the TranslationSpace (seeking an override)
				// 3) if that fails, then just use the Class from the field.
				Class fieldClass			= field.getType();
				Class<? extends ElementState> classFromTS			= translationSpace.getClassBySimpleNameOfClass(fieldClass);
				if (classFromTS == null)
				{
					setClassOp(fieldClass);
					//TODO - if warnings mode, warn user that class is not in the TranslationSpace
				}
				else if (ElementState.class.isAssignableFrom(classFromTS))// the way it should be :-)
					setClassOp(classFromTS);
				else
					warning("Field for " + tag + " not a subclass of ElementState. Ignored.");
				return;
			default:
				break;
			}

			// else there is no Field to resolve. but there may be a class!
			
			// was collection declared explicitly?
			Field collectionFieldByTag	= optimizations.getCollectionFieldByTag(tag);
			if (collectionFieldByTag != null)
			{
				ElementState.xml_classes classesAnnotation	= collectionFieldByTag.getAnnotation(ElementState.xml_classes.class);
				if (classesAnnotation != null)
				{
					Class classFromTag	= translationSpace.getClassByTag(tag);
					if (classFromTag != null)
					{
						setClassOp(classFromTag);
						this.field		= collectionFieldByTag;
						this.type		= COLLECTION_ELEMENT;
						return;
					}
				}
				java.lang.reflect.Type[] typeArgs	= ReflectionTools.getParameterizedTypeTokens(collectionFieldByTag);
				if (typeArgs != null)
				{
					Type typeArg0 								= typeArgs[0];
					Class	collectionElementsType	= null;
					if (typeArg0 instanceof Class)
						collectionElementsType			= (Class) typeArg0;
					else
					{
						collectionElementsType			= (Class) ((ParameterizedType) typeArg0).getRawType();
					}
//					debug("!!!collection elements are of type: " + collectionElementsType.getName());
					setClassOp(collectionElementsType);
					this.field				= collectionFieldByTag;
					// is collectionElementsType a scalar or a nested element
					if (ElementState.class.isAssignableFrom(collectionElementsType))
					{	// nested element
						this.type				= COLLECTION_ELEMENT;						
					}
					else
					{	// scalar
						this.type				= COLLECTION_SCALAR;
						this.scalarType			= translationSpace.getType(collectionElementsType);
						format					= XMLTools.getFormatAnnotation(collectionFieldByTag);
					}
				}
				else
				{
					warning("Ignoring declaration @xml_collection(\"" + tag + 
							  	 "\") because it is not annotating a parameterized generic Collection defined with a <type> token.");
					this.type	= IGNORED_ELEMENT;
					//!! remove entry from map !!
				}
			}
			else
			{
				Field mapField	= optimizations.getMapFieldByTag(tag); 
				if (mapField != null)
				{
					java.lang.reflect.Type[] typeArgs	= ReflectionTools.getParameterizedTypeTokens(mapField);
					if (typeArgs != null)
					{
						Class	mapElementsType		= (Class) typeArgs[1];
						debug("!!!map elements are of type: " + mapElementsType.getName());
						setClassOp(mapElementsType);
						this.field				= mapField;
						// is mapElementsType a scalar or a nested element
						if (ElementState.class.isAssignableFrom(mapElementsType))
						{	// nested element
							this.type				= MAP_ELEMENT;						
						}
						else
						{	// scalar
							this.type				= MAP_SCALAR;
							this.scalarType			= translationSpace.getType(mapElementsType);
						}
					}
					else
					{
						warning("Ignoring declaration @xml_map(\"" + tag + 
								  	 "\") because it is not annotating a parameterized generic Map defined with a <type> token.");
						this.type	= IGNORED_ELEMENT;
						//!! remove entry from map !!
					}
				}
				else
				{
					Class classOp	= translationSpace.xmlTagToClass(tag);
					if (classOp != null)
					{
						Map map					= context.getMap(classOp);
						if (map != null)
							this.type			= MAP_ELEMENT;
						else
						{
							Collection collection = context.getCollection(classOp);
							this.type	= (collection != null) ?
									COLLECTION_ELEMENT : OTHER_NESTED_ELEMENT;
						}
						setClassOp(classOp);
					}
					else
					{
						context.debugA("WARNING - ignoring <" + tag() +"/>");
						this.type	= IGNORED_ELEMENT;
					}
				}
			}
		}
		
	}
	/**
	 * Set-up this for a Namespace Element. 
	 * Begin by finding using the nameSpaceID to look for a NestedNameSpace already in scope.
	 * If its there, then we can just use it as the context of the subtag.
	 * <p/>
	 * If not, we will set type to NAMESPACE_TRIAL_ELEMENT, and return.
	 * In case the xmlns attribute we need turns out to be present in the *current* element.
	 * 
	 * @param translationSpace
	 * @param subTag
	 */
	private boolean setupNamespaceElement(TranslationScope translationSpace, String subTag, ElementState context)
	{
		ElementState nsContext	= context.getNestedNameSpace(nameSpaceID);
		if (nsContext == null)
		{
			this.type			= NAMESPACE_IGNORED_ELEMENT;
			//TODO -- new idea!
//			this.type			= NAMESPACE_TRIAL_ELEMENT;
			return false;
		}
		ClassDescriptor nsOpti			= nsContext.classDescriptor;

		ElementDescriptor nsN2jo	= nsOpti.nodeToJavaOptimizations(translationSpace, nsContext, subTag, false);
		final int nsN2joType 			= nsN2jo.type();
		if (nsN2joType != IGNORED_ELEMENT)
		{
			this.type			= nsN2joType + NAME_SPACE_MASK;
			//TODO -- what else do we need here
			this.field			= nsN2jo.field;
			this.setMethod		= nsN2jo.setMethod;
			this.scalarType		= nsN2jo.scalarType;
			this.classOp		= nsN2jo.classOp;
			this.isCDATA		= nsN2jo.isCDATA;
		}
		else
			this.type			= NAMESPACE_IGNORED_ELEMENT;
		
		return true;
	}
	private void setCDATA(Field field)
	{
		ElementState.xml_leaf leafAnnotation		= field.getAnnotation(ElementState.xml_leaf.class);
		if ((leafAnnotation != null) && (leafAnnotation.value() == ElementState.CDATA))
				this.isCDATA= true;
	}
	
	/**
	 * Create a name space object, nested in the context, using info saved in this.
	 * 
	 * @param context
	 * @param urn		The value of the xmlns:id attribute is the URL that is mapped to the class.
	 */
	void registerXMLNS(ElementState context, String urn)
	{
		optimizations.mapNamespaceIdToClass(translationSpace, tag, urn);
//		context.nestNameSpace(tag, nsClass);
	}

	
	private void setClassOp(Class<? extends ElementState> thatClass)
	{
		this.classOp	= thatClass;
	}
/**
 * Set-up PTE for scalar valued field (attribute or leaf node).
 * First look for a set method.
 * Then, look in the type registry.
 * Finally, if all else fails, get the list of @xml_name tags in the context class, and see if any match
 * Else, we must ignore the field.
 * 
 * @param tag
 * @param thatField TODO
 * @param contextClass
 * @param isAttribute		true for attribute; false for leaf node.
 */
	private int setupScalarValue(String tag, Field thatField, Class contextClass, boolean isAttribute)
	{
		int type			= UNSET_TYPE;
		String methodName	= XMLTools.methodNameFromTagName(tag);
        
		Method setMethod	= 
			ReflectionTools.getMethod(contextClass, methodName, ElementState.MARSHALLING_PARAMS);
		if (setMethod != null)
		{
			this.setMethod	= setMethod;
			type			= isAttribute ? REGULAR_ATTRIBUTE : LEAF_NODE_VALUE;
			// set method is custom code on a per field basis, and so doesnt need field object
		}
//		else
//		{
//            // TODO this might need to be done somewhere else...maybe another method
//            if (field == null)
//            { // we still haven't found the right one; have to check @xml_name in the context
//                for (Field contextField : contextClass.getDeclaredFields())
//                { // iterate through all the fields, and look for ones with xml_name's
//                    if (contextField.isAnnotationPresent(xml_tag.class))
//                    {
//                        if (tag.equals(contextField.getAnnotation(xml_tag.class).value()))
//                        { // if we found a matching xml_name, then that's our field!
//                            field = contextField;
//                            field.setAccessible(true);
//                            break;
//                        }
//                    }
//                }
//            }
//		}
		if (field != null)
		{
			ScalarType fieldType		= TypeRegistry.getType(field);
			if (fieldType != null)
			{
				this.scalarType	= fieldType;
				type			= isAttribute ? REGULAR_ATTRIBUTE : LEAF_NODE_VALUE;
//				this.field		= field;
			}
//			else if (!isAttribute)
//				this.field	= field; // for leaf node seekers that can be nested elements
		}

		if (type == UNSET_TYPE)
		{
			type					= isAttribute ? IGNORED_ATTRIBUTE : IGNORED_ELEMENT;
			if (isAttribute)
				error(XMLTools.fieldNameFromElementName(tag)+": no set method or type to set value for this tag in " + 
						contextClass.getName() + ".");
		}
		this.type					= type;
		return type;
	}
	
	/**
	 * Use this as the spec for obtaining a new ElementState (subclass),
	 * springing forth from the parent, and based on the XML Node.
	 * 
	 * @param parent
	 * @param node
	 * @param useExistingTree
	 * @return
	 * @throws XMLTranslationException
	 */
	ElementState domFormChildElement(ElementState parent, Node node, boolean useExistingTree)
	throws XMLTranslationException
	{
		ElementState childElement	= null;

		if (useExistingTree)
		{
			try
			{
				childElement		= (ElementState) field.get(parent);
			} catch (Exception e)
			{
				throw fieldAccessException(parent, e);
			}
		}
		boolean newChild			= false;
		
		if (childElement == null)
		{
			childElement			= constructChildElementState(parent);
			newChild				= true;
		}
			
		if (node != null)
			childElement.translateFromXMLNode(node, translationSpace);
		
		if (newChild)
			parent.createChildHook(childElement);

		childElement.postTranslationProcessingHook();
		return childElement;
	}

/**
 * Based on the classOp in this, form a child element.
 * Set it's parent field and elementByIdMap.
 * Look-up Optimizations for it, using the parent's Optimizations as the scope.
 *
 * @param parent
 * @return
 * @throws XMLTranslationException
 */
	ElementState constructChildElementState(ElementState parent)
	throws XMLTranslationException
	{
		ElementState childElementState		= XMLTools.getInstance(classOp);
		parent.setupChildElementState(childElementState);
		
		return childElementState;
	}
	
	/**
	 * Use a set method or the type system to set our field in the context to the value.
	 * 
	 * @param context
	 * @param value
	 * @param scalarUnmarshallingContext TODO
	 */
	void setFieldToScalar(Object context, String value, ScalarUnmarshallingContext scalarUnmarshallingContext)
	{
		if ((value == null) /*|| (value.length() == 0) removed by Alex to allow empty delims*/)
		{
//			error("Can't set scalar field with empty String");
			return;
		}
		if (setMethod != null)
		{
			// if the method is found, invoke the method
			// fill the String value with the value of the attr node
			// args is the array of objects containing arguments to the method to be invoked
			// in our case, methods have only one arg: the value String
			Object[] args = new Object[1];
			args[0]		  = value;
			try
			{
				setMethod.invoke(context, args); // run set method!
			}
			catch (InvocationTargetException e)
			{
				weird("couldnt run set method for " + tag +
						  " even though we found it");
				e.printStackTrace();
			}
			catch (IllegalAccessException e)
			{
				weird("couldnt run set method for " + tag +
						  " even though we found it");
				e.printStackTrace();
			}	  
			
		}
		else if (scalarType != null)
		{
			scalarType.setField(context, field, value, format, null);
		}
	}
		
	public String toString()
	{
		String tagString	= (tag == null) ? "NO_TAG?" : tag;
		return super.toString() + "[" + tagString + " - " + type + "]";
	}
	/**
	 * Set a scalar value using the textElementChild Node as the source,
	 * the stateClass as the template for where the field is located, 
	 * the childFieldName as the name of the field to select in the template,
	 * and this as the object to do the set in.
	 * 
	 * @param context	The object in which we are setting the field's value.
	 * @param leafNode	The leaf node with the text element value.
	 */
	void setScalarFieldWithLeafNode(Object context, Node leafNode)
	{
		String textNodeValue	= getLeafNodeValue(leafNode);
		setFieldToScalar(context, textNodeValue, null);
	}
	/**
	 * Assume the first child of the leaf node is a text node.
	 * Pull the text of out that text node. Trim it, and if necessary, unescape it.
	 * 
	 * @param leafNode	The leaf node with the text element value.
	 * @return			Null if there's not really any text, or the useful text from the Node, if there is some.
	 */
	String getLeafNodeValue(Node leafNode)
	{
		String result	= null;
		Node textElementChild			= leafNode.getFirstChild();
		if (textElementChild != null)
		{
			if (textElementChild != null)
			{
				String textNodeValue	= textElementChild.getNodeValue();
				if (textNodeValue != null)
				{
					textNodeValue		= textNodeValue.trim();
					if (!isCDATA && (scalarType != null) && scalarType.needsEscaping())
						textNodeValue	= XMLTools.unescapeXML(textNodeValue);
					//debug("setting special text node " +childFieldName +"="+textNodeValue);
					if (textNodeValue.length() > 0)
					{
						result			= textNodeValue;
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Used to set a field in this to a nested ElementState object.
	 * Alas, this currently corresponds to @xml_collection and @xml_map, as well as @xml_nested.
	 * 
	 * his method is called during translateFromXML(...).
	 * @param nestedElementState	the nested state-object to be added
	 * @param childNode				XML doc subtree to use as the source of translation
	 * @param useExistingTree		if true, re-fill in existing objects, instead of creating new ones.
	 */
	protected void domFormNestedElementAndSetField(ElementState context, Node childNode)
		throws XMLTranslationException
	{
		ElementState nestedObject =  domFormChildElement(context, childNode, false);
				//isElementStateSubclass ?: ReflectionTools.getInstance(classOp);
		
		setFieldToNestedObject(context, nestedObject);
	}

	void setFieldToNestedObject(ElementState context, Object nestedObject) 
	throws XMLTranslationException
	{
		try
		{
			field.set(context, nestedObject);
		}
		catch (Exception e)
		{
			throw fieldAccessException(nestedObject, e);
		}
	}

	/**
	 * Generate an exception about problems accessing a field.
	 * 
	 * @param nestedElementState
	 * @param e
	 * @return
	 */
	private XMLTranslationException fieldAccessException(Object nestedElementState, Exception e)
	{
		return new XMLTranslationException(
					"Unexpected Object / Field set problem. \n\t"+
					"Field = " + field +"\n\ttrying to set to " + nestedElementState.getClass(), e);
	}
	
	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES
	 * @param childNode
	 * @throws XMLTranslationException
	 */
	void domFormElementAndAddToCollection(ElementState activeES, Node childNode)
	throws XMLTranslationException
	{
		Collection collection = getCollection(activeES);

		if (collection != null)
		{
			
			ElementState childElement = domFormChildElement(activeES, childNode, false);
			collection.add(childElement);
		}
		else
		{
			warning("Can't add <" + tag + "> to " + activeES.getClassName() + ", because the collection is null.");
		}
	}

	/**
	 * Get the collection object from the appropriate field, or create it, if necessary.
	 * @param activeES
	 * @return
	 */
	Collection getCollection(ElementState activeES)
	{
		Collection collection	= null;
		if (field != null)
		{
			collection			= (Collection) automaticLazyGetCollectionOrMap(activeES);
		}
		else
			collection			= activeES.getCollection(classOp());
		return collection;
	}

	/**
	 * Use the Field of this to seek a Collection or Map object in the activeES.
	 * If non-null, great -- return it.
	 * <p/>
	 * Otherwise, lazy evaluation.
	 * Since the value of the field is null, use the Type of the Field to instantiate a newInstance.
	 * Set the instance of the Field in activeES to this newInstance, and return it.
	 * 
	 * @param activeES
	 * @return
	 */
	private Object automaticLazyGetCollectionOrMap(ElementState activeES)
	{
		Object collection	= null;
		try
		{
			collection		= field.get(activeES);
			if (collection == null)
			{
				// initialize the collection for the caller! automatic lazy evaluation :-)
				Class collectionType	= field.getType();
				try
				{
					collection	= collectionType.newInstance();
					// set the field to the new collection
					field.set(activeES, collection);
				} catch (InstantiationException e)
				{
					warning("Can't instantiate collection of type" + collectionType + " for field " + field.getName() + " in " + activeES);
					e.printStackTrace();
					// return
				}
			}
		} catch (IllegalArgumentException e)
		{
			weird("Trying to addElementToCollection(). Can't access collection field " + field.getType() + " in " + activeES);
			e.printStackTrace();
			//return;
		} catch (IllegalAccessException e)
		{
			weird("Trying to addElementToCollection(). Can't access collection field " + field.getType() + " in " + activeES);
			e.printStackTrace();
			//return;
		}
		return collection;
	}
		
	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES
	 * @param childNode
	 * @throws XMLTranslationException
	 */
	void domFormElementAndToMap(ElementState activeES, Node childNode)
	throws XMLTranslationException
	{
		Map map = getMap(activeES);
			
		if (map != null)
		{
			Mappable mappable	= (Mappable) domFormChildElement(activeES, childNode, false);
			map.put(mappable.key(), mappable);
		}
		else
		{
			warning("Can't add <" + tag + "> to " + activeES.getClassName() + ", because the map is null.");
		}
	}

	Map getMap(ElementState activeES)
	{
		Map map		= null;
		if (field != null)
		{
			try
			{
				map		= (Map) automaticLazyGetCollectionOrMap(activeES);
//			map		= (Map) field.get(activeES);
			} catch (Exception e)
			{
				weird("Trying to addElementToMap(). Can't access map field " + field.getType() + " in " + activeES +"\n\t" + e);
			}
		}
		else
			map		= activeES.getMap(classOp());
		return map;
	}
		
	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES		Contextualizing object that has the Collection slot we're adding to.
	 * @param childLeafNode	XML leafNode that has the value we need to add, after type conversion.
	 * 
	 * @throws XMLTranslationException
	 */
	void addLeafNodeToCollection(ElementState activeES, Node childLeafNode)
	throws XMLTranslationException
	{
		addLeafNodeToCollection(activeES, getLeafNodeValue(childLeafNode), null);
	}
	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES		Contextualizing object that has the Collection slot we're adding to.
	 * @param scalarUnmarshallingContext TODO
	 * @param childLeafNode	XML leafNode that has the value we need to add, after type conversion.
	 * @throws XMLTranslationException
	 */
	void addLeafNodeToCollection(ElementState activeES, String leafNodeValue, ScalarUnmarshallingContext scalarUnmarshallingContext)
	throws XMLTranslationException
	{
		if  (leafNodeValue != null)
		{
			// silently ignore null leaf node values
		}
		if (scalarType != null)
		{
			//TODO -- for performance reasons, should we call without format if format is null, and
			// let the ScalarTypes that don't use format implement the 1 argument signature?!
			Object typeConvertedValue		= scalarType.getInstance(leafNodeValue, format, scalarUnmarshallingContext);
			try
			{
				//TODO -- should we be doing this check for null here??
				if (typeConvertedValue != null)
				{
//					Collection collection	= (Collection) field.get(activeES);
//					if (collection == null)
//					{
//						// well, why not create the collection object for them?!
//						Collection thatCollection	= 
//							ReflectionTools.getInstance((Class<Collection>) field.getType());
//
//					}
					Collection<Object> collection	= (Collection<Object>) automaticLazyGetCollectionOrMap(activeES);
					collection.add(typeConvertedValue);
				}
			} catch (Exception e)
			{
				throw fieldAccessException(typeConvertedValue, e);
			}
		}
		else
		{
			reportFieldTypeError(leafNodeValue);
		}
	}

//	void addLeafNodeToMap(ElementState activeES, Node childLeafNode)
//	throws XmlTranslationException
//	{
//		if (scalarType != null)
//		{
//			String textNodeValue			= getLeafNodeValue(childLeafNode);
//			
//			Object typeConvertedValue		= scalarType.getInstance(textNodeValue);
//			try
//			{
//					Map map					= (Map) automaticLazyGetCollectionOrMap(activeES);
//					map.put(this.typeConvertedValue);
//			} catch (Exception e)
//			{
//				throw fieldAccessException(typeConvertedValue, e);
//			}
//		}
//		else
//		{
//			reportFieldTypeError(childLeafNode);
//		}
//	}

	
	private void reportFieldTypeError(String textNodeValue)
	{
		error("Can't set to " + textNodeValue + " because fieldType is unknown.");
	}
			
	private void fillValues(ElementDescriptor other)
	{
		//this.classOp			= other.classOp;
		this.type				= other.type;
		// i think this field could be gotten from here (the child) or be propagated from us
		// no difference, cause the current Field has to appear in both TranslationSpaces!
		this.translationSpace	= other.translationSpace;
	}

	/**
	 * @return the Class operand that we need to work with in translation from XML.
	 */
	Class<? extends ElementState> classOp()
	{
		return classOp;
	}

	/**
	 * @return the field
	 */
	Field field()
	{
		return field;
	}

	/**
	 * @return the nameSpaceID
	 */
	String nameSpaceName()
	{
		return nameSpaceID;
	}

	/**
	 * @return the nestedNameSpaceParseTableEntry
	 */
	ElementDescriptor nestedPTE()
	{
		return nestedPTE;
	}

	/**
	 * @return the type
	 */
	int type()
	{
		return type;
	}

	/**
	 * @return the translationSpace
	 */
	TranslationScope translationSpace()
	{
		return translationSpace;
	}

	/**
	 * @return the tag
	 */
	String tag()
	{
		return tag;
	}

	/**
	 * @return the isID
	 */
	boolean isID()
	{
		return isID;
	}

	/**
	 * 
	 * @return true if this entry is a leaf node.
	 */
	boolean isLeafNode()
	{
		return this.type == LEAF_NODE_VALUE;
	}
	/**
	 * 
	 * @return true if this entry is a collection of scalar valued leaf nodes.
	 */
	boolean isCollectionScalar()
	{
		return this.type == COLLECTION_SCALAR;
	}
	
	/**
	 * 
	 * @return true if this entry is a collection of scalar valued leaf nodes.
	 */
	boolean isMapScalar()
	{
		return this.type == MAP_SCALAR;
	}
	
	ScalarType scalarType()
	{
		return scalarType;
	}
	
	ElementDescriptor(String tag)
	{
		this.tag		= tag;
		this.type		= IGNORED_ELEMENT;
		optimizations	= null;
	}
	static final ElementDescriptor IGNORED_ELEMENT_OPTIMIZATIONS;
	static final ElementDescriptor ROOT_ELEMENT_OPTIMIZATIONS;
	static
	{
		IGNORED_ELEMENT_OPTIMIZATIONS		= new ElementDescriptor("IGNORED");
		IGNORED_ELEMENT_OPTIMIZATIONS.type	= IGNORED_ELEMENT;
		
		ROOT_ELEMENT_OPTIMIZATIONS			= new ElementDescriptor("ROOT");
		ROOT_ELEMENT_OPTIMIZATIONS.type		= ROOT;
	}
	/**
	 * Used for fields that are within a nested Namespace object.
	 * 
	 * @return the nameSpaceID
	 */
	String nameSpaceID()
	{
		return nameSpaceID;
	}
}
