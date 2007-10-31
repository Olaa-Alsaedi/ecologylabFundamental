package ecologylab.xml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Node;

import ecologylab.generic.Debug;
import ecologylab.xml.ElementState.xml_tag;

/**
 * Cached object that holds all of the structures needed to optimize
 * translations to and from XML for a single subclass of ElementState.
 * A registry keeps track of these, using the XML tag as the key.
 * <p/>
 * This structure itself, as well as the structure within it, are created just in time,
 * by lazy evaluation.
 *
 * @author andruid
 */
public class Optimizations extends Debug
implements OptimizationTypes
{
	/**
	 * Class object that we are holding optimizations for.
	 */
	Class							thatClass;
	/**
	 * A map of all the Optimizations objects.
	 * The keys are simple class names.
	 */
	private static final HashMap<String, Optimizations>	registry	= new HashMap<String, Optimizations>();
	
	/**
	 * Keys are URNs (not NameSpaceIdentifiers!).
	 * 
	 * Values are ElementState subclasses.
	 */
	private static final HashMap<String, Class<? extends ElementState>>	nameSpaceRegistryByURN = new HashMap<String, Class<? extends ElementState>>();
		
	/**
	 * Found before the colon while translating from;
	 * emitted with a colon while translating to.
	 */
	private String					nameSpacePrefix;
	
	/**
	 * Used to optimize translateToXML().
	 */
	private HashMap<Object, FieldToXMLOptimizations>		fieldOrClassToTagMap	= new HashMap<Object, FieldToXMLOptimizations>();
	
	/**
	 * Map of ParseTableEntrys. The keys are tag names.
	 * Used to optimize translateFromXML(...).
	 */
	private HashMap<String, ElementToJavaOptimizations>	parseTableByTagNames	= new HashMap<String, ElementToJavaOptimizations>();
	
	/**
	 * Map of ParseTableEntrys. The keys are field names.
	 * Used to optimize translateFromXML(...).
	 */
	private HashMap<String, ElementToJavaOptimizations>	parseTableByFieldNames	= new HashMap<String, ElementToJavaOptimizations>();
	
	private HashMap<String, Class<? extends ElementState>>	nameSpacesByID	= new HashMap<String, Class<? extends ElementState>>();
	
	/**
	 * The fields that are represented as attributes for the class we're optimizing.
	 */
	private ArrayList<Field>					attributeFields;
	
	/**
	 * The fields that are represented as nested elements (including leaf nodes)
	 * for the class we're optimizing.
	 */
	private ArrayList<Field>					elementFields;
	
	private HashMap<String, Field>				fieldsMap;
	
	private HashMap<String, Field>				mapFieldsByTag;
	
	private HashMap<String, Field>				collectionFieldsByTag;
	
	
	private Optimizations(Class thatClass)
	{
		super();
		this.thatClass		= thatClass;
	}

	/**
	 * The only mechanism for obtaining an Optimizations object, since the constructor is private.
	 * Uses just-in-time / lazy evaluation.
	 * The first time this is called for a given ElementState class, it constructs a new Optimizations
	 * saves it in our registry, and returns it.
	 * <p/>
	 * Subsequent calls merely pass back the already created object from the registry.
	 * 
	 * @param elementState
	 * @return
	 */
	static Optimizations lookup(ElementState elementState)
	{
		Class thatClass		= elementState.getClass();
		String className	= classSimpleName(thatClass);
		// stay out of the synchronized block most of the time
		Optimizations result= registry.get(className);
		if (result == null)
		{
			// but still be thread safe!
			synchronized (registry)
			{
				result 		= registry.get(className);
				if (result == null)
				{
					result				= new Optimizations(thatClass);
					registry.put(className, result);
				}
			}
		}
		return result;
	}
	
	/**
	 * Get a tag translation object that corresponds to the fieldName,
	 * with this class. If necessary, form that tag translation object,
	 * and cache it.
	 * @param type TODO
	 */
	FieldToXMLOptimizations getTagMapEntry(Field field, Class<? extends ElementState> thatClass)
	{
		FieldToXMLOptimizations result= fieldOrClassToTagMap.get(thatClass);
		if (result == null)
		{
			synchronized (fieldOrClassToTagMap)
			{
				result		= fieldOrClassToTagMap.get(thatClass);
				if (result == null)
				{
				    result = new FieldToXMLOptimizations(field, thatClass);
                    
                    fieldOrClassToTagMap.put(thatClass, result);
					//debug(tagName.toString());
				}
			}
		}
		return result;
	}
	FieldToXMLOptimizations getRootTagMapEntry(Class rootClass)
	{
		FieldToXMLOptimizations result= fieldOrClassToTagMap.get(rootClass);
		if (result == null)
		{
			synchronized (fieldOrClassToTagMap)
			{
				result		= fieldOrClassToTagMap.get(rootClass);
				if (result == null)
				{
				    result = new FieldToXMLOptimizations(rootClass);
                    
                    fieldOrClassToTagMap.put(rootClass, result);
				}
			}
		}
		return result;
		
	}
	
	FieldToXMLOptimizations getTagMapEntry(FieldToXMLOptimizations collectionTagMapEntry, Class<? extends ElementState> actualCollectionElementClass)
	{
		FieldToXMLOptimizations result= fieldOrClassToTagMap.get(actualCollectionElementClass);
		if (result == null)
		{
			synchronized (fieldOrClassToTagMap)
			{
				result		= fieldOrClassToTagMap.get(actualCollectionElementClass);
				if (result == null)
				{
				    result = new FieldToXMLOptimizations(collectionTagMapEntry, actualCollectionElementClass);
                    
                    fieldOrClassToTagMap.put(actualCollectionElementClass, result);
					//debug(tagName.toString());
				}
			}
		}
		return result;
	}
	
	/**
	 * Get a tag translation object that corresponds to the fieldName,
	 * with this class. If necessary, form that tag translation object,
	 * and cache it.
	 */
	FieldToXMLOptimizations getTagMapEntry(Field field)
	{
		FieldToXMLOptimizations result= fieldOrClassToTagMap.get(field);
		if (result == null)
		{
			synchronized (fieldOrClassToTagMap)
			{
				result		= fieldOrClassToTagMap.get(field);
				if (result == null)
				{
					result	= new FieldToXMLOptimizations(field);
//					debug(tagName.toString());
					fieldOrClassToTagMap.put(field, result);
				}
			}
		}
		return result;
	}
	/**
	 * @return Returns the nameSpacePrefix.
	 */
	String nameSpacePrefix()
	{
		return nameSpacePrefix;
	}
	/**
	 * @param nameSpacePrefix The nameSpacePrefix to set.
	 */
	void setNameSpacePrefix(String nameSpacePrefix)
	{
		this.nameSpacePrefix = nameSpacePrefix;
	}

	/**
	 * Get an object that describes how the field gets translated from XML.
	 * We cache these in the parseTable for each class, to optimize speed.
	 * <p/>
	 * These entries are created as needed, just-in-time, by lazy evaluation.
	 * @param translationSpace TODO
	 * @param context TODO
	 * @param node TODO
	 */
	ElementToJavaOptimizations elementToJavaOptimizations(TranslationSpace translationSpace, ElementState context, Node node)
	{
		String tag				= node.getNodeName();
		return elementToJavaOptimizations(translationSpace, context, tag);
	}
	ElementToJavaOptimizations elementToJavaOptimizations(TranslationSpace translationSpace, ElementState context, String tag)
	{
		ElementToJavaOptimizations result	= parseTableByTagNames.get(tag);
		
		if (result == null)
		{
			result				= new ElementToJavaOptimizations(translationSpace, this, context, tag, false);
			putInParseTables(tag, result);
		}
		return result;
	}

	/**
	 * Add a ElementToJavaOptimizations to the parseTables.
	 * 
	 * @param tag
	 * @param result
	 */
	private void putInParseTables(String tag, ElementToJavaOptimizations result)
	{
		parseTableByTagNames.put(tag, result);
		switch (result.type())
		{
		case IGNORED_ATTRIBUTE:
		case XMLNS_ATTRIBUTE:
			break;
		default:
			Field field = result.field();
			if (field != null)
			{
				String fieldName	= field.getName();
				parseTableByFieldNames.put(fieldName, result);
			}
			break;
		}
	}
	
	/**
	 * Lookup, and create if necessary, the ElementToJavaOptimizations for an attribute.
	 * 
	 * @param translationSpace
	 * @param context
	 * @param node
	 * @return
	 */
	ElementToJavaOptimizations parseTableAttrEntry(TranslationSpace translationSpace, ElementState context, Node node)
	{
		String tag				= node.getNodeName();
		ElementToJavaOptimizations result	= parseTableByTagNames.get(tag);
		
		if (result == null)
		{
			if (tag.startsWith("xmlns:"))
			{
				String nameSpaceID	= tag.substring(6);
				if (!containsNameSpaceByNSID(nameSpaceID))
				{
					registerNameSpace(translationSpace, nameSpaceID, node.getNodeValue());
				}
			}
			result				= new ElementToJavaOptimizations(translationSpace, this, context, tag, true);
			putInParseTables(tag, result);
		}
		return result;
	}
	ElementToJavaOptimizations parseTableAttrEntry(TranslationSpace translationSpace, ElementState context, String tag)
	{
		ElementToJavaOptimizations result	= parseTableByTagNames.get(tag);
		
		if (result == null)
		{
			result				= new ElementToJavaOptimizations(translationSpace, this, context, tag, true);
			if (result.type() == XMLNS_ATTRIBUTE)
			{
				String nameSpaceID	= tag.substring(6);
				// register namespace here!
				//optimizations.registerNameSpace(translationSpace, )
			}

			putInParseTables(tag, result);
		}
		return result;
	}
	
	/**
	 * Lookup a ElementToJavaOptimizations, using a Field name as the key.
	 * 
	 * @param fieldName
	 * @return
	 */
	ElementToJavaOptimizations getPTEByFieldName(String fieldName)
	{
		return parseTableByFieldNames.get(fieldName);
	}
	
	/**
	 * Get the fields that are represented as attributes for the class we're optimizing.
	 * Uses lazy evaluation -- while derive the answer for attributefieldbs and elementFields, and cache it.
	 * 
	 * @return	ArrayList of Field objects.
	 */
	ArrayList<Field> attributeFields()
	{
		ArrayList<Field> result	= attributeFields;
		if (result == null)
		{
			synchronized (this)
			{
				result		= attributeFields;	// check again inside the synchronized block
				if (result == null)
				{
					getAndOrganizeFields();
				}
			}
			result			= attributeFields;
		}
		return result;
	}
	/**
	 * Get the fields that are represented as nested elements (including leaf nodes)
	 * for the class we're optimizing.
	 * Uses lazy evaluation -- while derive the answer for attributefieldbs and elementFields, and cache it.
	 * 
	 * @return	ArrayList of Field objects.
	 */
	public ArrayList<Field> elementFields()
	{
		ArrayList<Field> result	= elementFields;
		if (result == null)
		{
			synchronized (this)
			{
				result		= elementFields;	// check again inside the synchronized block
				if (result == null)
				{
					getAndOrganizeFields();
				}
			}
			result			= elementFields;
		}
		return result;
	}
	
	/**
	 * Get the map of fields that we translate for this class.
	 * Uses lazy evaluation; will call getAndOrganizeFields() to build it
	 * (and also the attributeFields and elementFields ArrayLists) if this is the 1st
	 * call for the class.
	 * 
	 * @return
	 */
	private HashMap<String, Field> fieldsMap()
	{
		HashMap<String, Field> result	= fieldsMap;
		if (result == null)
		{
			synchronized (this)
			{
				result		= fieldsMap;	// check again inside the synchronized block
				if (result == null)
				{
					getAndOrganizeFields();
				}
			}
			result			= fieldsMap;
		}
		return result;
	}
	
	/**
	 * Performs the lazy evaluation to get attribute and element field collections.
	 * Dispatches to the correct routine for this, based on ElementState.declarationStyle().
	 *
	 */
	private synchronized void getAndOrganizeFields()
	{
		attributeFields		= new ArrayList<Field>();
		elementFields		= new ArrayList<Field>();
		fieldsMap			= new HashMap<String, Field>();
		ElementState.DeclarationStyle ds = ElementState.declarationStyle();
		switch (ds)
		{
		case PUBLIC:
			getAndOrganizeFieldsPublic(thatClass, attributeFields, elementFields);
			break;
		case ANNOTATION:
		case TRANSIENT:
			getAndOrganizeFieldsRecursive(thatClass, attributeFields, elementFields,
										  (ds == ElementState.DeclarationStyle.TRANSIENT));
			break;
		default:
			throw new RuntimeException("Unsupported declaration style: " + ds);
		}
	}
	/**
	 * Performs the lazy evaluation to get attribute and element field collections.
	 * Uses the hip new annotation stylee.
	 * Uses the old PUBLIC declaration stylee.
	 * <p/>
	 * Must be called from getAndOrganizeFields() !
	 */
	private void getAndOrganizeFieldsPublic(Class thatClass,
			ArrayList<Field> attributeFields, ArrayList<Field> elementFields)
	{
		Field[] fields		= thatClass.getFields();
			
		for (int i = 0; i < fields.length; i++)
		{
			Field thatField	= fields[i];
			int fieldModifiers		= thatField.getModifiers();

			// skip static fields, since we're saving instances,
			// and inclusion w each instance would be redundant.
			if ((fieldModifiers & Modifier.STATIC) == Modifier.STATIC)
			{
//				debug("Skipping " + thatField + " because its static!");
				continue;
			}
			mapField(thatField);
			if (XmlTools.isScalarValue(thatField) && !XmlTools.representAsLeafNode(thatField))
			{
				attributeFields.add(thatField);
			}
			else
			{
				elementFields.add(thatField);
			}
		}
	}
	/**
	 * Performs the lazy evaluation to get attribute and element field collections.
	 * Uses the hip new annotation declaration stylee.
	 * Recurses up the chain of inherited Java classes, if @xml_inherit is specified.
	 */
	private void getAndOrganizeFieldsRecursive(Class thatClass,
			ArrayList<Field> attributeFields, ArrayList<Field> elementFields, 
			boolean transientDeclarationStyle)
	{
		Field[] fields		= thatClass.getDeclaredFields();
		
		for (int i = 0; i < fields.length; i++)
		{
			Field thatField	= fields[i];
			int fieldModifiers		= thatField.getModifiers();
			
			// skip static fields, since we're saving instances,
			// and inclusion w each instance would be redundant.
			if ((fieldModifiers & Modifier.STATIC) == Modifier.STATIC)
			{
//				debug("Skipping " + thatField + " because its static!");
				continue;
			}
			//mapField(thatField);
			if (XmlTools.representAsAttribute(thatField))
			{
				mapField(thatField);
				attributeFields.add(thatField);
				thatField.setAccessible(true);
			}
			else if (XmlTools.representAsLeafOrNested(thatField))
			{
				mapField(thatField);
				elementFields.add(thatField);
				thatField.setAccessible(true);
				
				// look for declared tag for @xml_collection
				ElementState.xml_collection collectionAnnotation		
					= thatField.getAnnotation(ElementState.xml_collection.class);
				if (collectionAnnotation != null)
				{
					processCollectionDeclaredWithTag(thatField, collectionAnnotation);
				}
				else
				{
					// look for declared tag for @xml_map
					ElementState.xml_map mapAnnotation
						= thatField.getAnnotation(ElementState.xml_map.class);
					if (mapAnnotation != null)
					{
						processMapDeclaredWithTag(thatField, mapAnnotation);
					}				
				}
			}
			// else -- ignore non-annotated fields
		}
		if (thatClass.isAnnotationPresent(xml_inherit.class) || transientDeclarationStyle) 
		{	// recurse on super class
			Class superClass	= thatClass.getSuperclass();
			if (superClass != null)
				getAndOrganizeFieldsRecursive(superClass, attributeFields, elementFields, transientDeclarationStyle);
		}
	}

	/**
	 * @param thatField
	 * @param tagFromAnnotation
	 */
	private void processCollectionDeclaredWithTag(Field thatField, ElementState.xml_collection collectionAnnotation)
	{
		String tagFromAnnotation	= collectionAnnotation.value();
		Class fieldClass			= thatField.getType();
		if (Collection.class.isAssignableFrom(fieldClass))
		{
			if ((tagFromAnnotation != null) && !"".equals(tagFromAnnotation))
			{ 
				//note! we *really* want to wait until here before we allocate storage for this HashMap!
				collectionFieldsByTag().put(tagFromAnnotation, thatField);
			}
		} 
		else
			annotatedFieldError(thatField, tagFromAnnotation, "collection");
	}

	/**
	 * @param thatField
	 * @param mapAnnotation
	 */
	private void processMapDeclaredWithTag(Field thatField, ElementState.xml_map mapAnnotation)
	{
		String tagFromAnnotation	= mapAnnotation.value();
		Class fieldClass			= thatField.getType();
		if (Map.class.isAssignableFrom(fieldClass))
		{
			if ((tagFromAnnotation != null) && !"".equals(tagFromAnnotation))
			{ 
				//note! we *really* want to wait until here before we allocate storage for this HashMap!
				mapFieldsByTag().put(tagFromAnnotation, thatField);
			}
		} 
		else
			annotatedFieldError(thatField, tagFromAnnotation, "map");
	}

	HashMap<String, Field> collectionFieldsByTag()
	{
		HashMap<String, Field> result	= this.collectionFieldsByTag;
		if (result == null)
		{
			result	= new HashMap<String, Field>();
			this.collectionFieldsByTag	= result;
		}
		return result;
	}
	HashMap<String, Field> mapFieldsByTag()
	{
		HashMap<String, Field> result	= this.mapFieldsByTag;
		if (result == null)
		{
			result	= new HashMap<String, Field>();
			this.mapFieldsByTag			= result;
		}
		return result;
	}
	/**
	 * @param thatField
	 * @param tagFromAnnotation
	 * @param required
	 */
	private void annotatedFieldError(Field thatField, String tagFromAnnotation, String required)
	{
		String tagMsg = ((tagFromAnnotation == null) || (tagFromAnnotation.length() == 0)) ? "" 
				: ("\"" + tagFromAnnotation + "\"");

		error("@xml_collection(" + tagMsg + ") declared as type " + 
				thatField.getType().getSimpleName() +" for field named "+ thatField.getName() + ", which is not a " + required+ ".");
	}
	
	
	/**
	 * Add an entry to our map of Field objects, using the field's name as the key.
	 * Can only be called by routines that guaranty that the fieldsMap has already been
	 * created and populated with entries.
	 * 
	 * @param fieldsMap
	 * @param thatField
	 */
	private void mapField(Field thatField)
	{
		fieldsMap.put(thatField.getName(), thatField);
	}
/*
	private void mapMap(Field thatField)
	{
		mapsMap.put(thatField.getName(), thatField);
	}

	private void mapCollection(Field thatField)
	{
		collectionsMap.put(thatField.getName(), thatField);
	}
*/
	/**
	 * Get the field asssociated with fieldName.
	 * If necessary (that is, the first time), this routine may make calls to do all the work
	 * for creating the fieldsMap and fields ArrayLists.
	 */
	Field getField(String fieldName)
	{
		return fieldsMap().get(fieldName);
	}
	
	/**
	 * Lookup a collection object by its tag name.
	 * This seeks a field declared with @xml_collection(tag).
	 * 
	 * @param tag
	 * @return	The Field object for the correctly parameterized Collection.
	 */
	Field getCollectionFieldByTag(String tag)
	{
		return (collectionFieldsByTag == null) ? null : collectionFieldsByTag.get(tag);
	}
	/**
	 * Lookup a collection object by its tag name.
	 * This seeks a field declared with @xml_collection(tag).
	 * 
	 * @param tag
	 * @return	The Field object for the correctly parameterized Collection.
	 */
	Field getMapFieldByTag(String tag)
	{
		return (mapFieldsByTag == null) ? null : mapFieldsByTag.get(tag);
	}
	/**
	 * If a field declared with @xml_collection(tag) turns out not to be a parameterized
	 * Collection type, then clear its entry!
	 * 
	 * @param tag
	 */
	void clearCollectionFieldByTag(String tag)
	{
		if (collectionFieldsByTag != null)
			collectionFieldsByTag.put(tag, null);
	}
	/**
	 * If a field declared with @xml_collection(tag) turns out not to be a parameterized
	 * Collection type, then clear its entry!
	 * 
	 * @param tag
	 */
	void clearMapFieldByTag(String tag)
	{
		if (mapFieldsByTag != null)
			mapFieldsByTag.put(tag, null);
	}
	
	public String toString()
	{
		return "Optimizations[" + thatClass.getName() + "]"; 
	}
	
	/**
	 * Add an entry to the global table of class to URNs.
	 * 
	 * @param urn
	 * @param nameSpaceElementState
	 */
	public void registerNameSpaceByURN(String urn, Class<? extends ElementState> nameSpaceElementState)
	{
		nameSpaceRegistryByURN.put(urn, nameSpaceElementState);
	}
	void registerNameSpace(TranslationSpace translationSpace, String nsID, String urn)
	{
		//TODO first look in the TranslationSpace for a mapping
		Class<? extends ElementState> nameSpaceClass	= translationSpace.lookupNameSpaceByURN(urn);
		
		if (nameSpaceClass == null)
		{	// now check the global registry
			nameSpaceClass								= nameSpaceRegistryByURN.get(urn);
			if (nameSpaceClass == null)
				return;
		}
		
		nameSpacesByID.put(nsID, nameSpaceClass);
		//TODO add entries to other useful data structures
	}
	boolean containsNameSpaceByNSID(String nsID)
	{
		return nameSpacesByID.containsKey(nsID);
	}
	Class<? extends ElementState> lookupNameSpaceByNSID(String nsID)
	{
		return nameSpacesByID.get(nsID);
	}
}

