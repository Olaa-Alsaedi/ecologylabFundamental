/**
 * 
 */
package ecologylab.serialization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Node;
import org.xml.sax.Attributes;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl;
import ecologylab.generic.HashMapArrayList;
import ecologylab.generic.ReflectionTools;
import ecologylab.generic.StringTools;
import ecologylab.serialization.ElementState.simpl_map_key_field;
import ecologylab.serialization.TranslationScope.GRAPH_SWITCH;
import ecologylab.serialization.library.html.A;
import ecologylab.serialization.library.html.Div;
import ecologylab.serialization.library.html.Input;
import ecologylab.serialization.library.html.Td;
import ecologylab.serialization.library.html.Tr;
import ecologylab.serialization.types.CollectionType;
import ecologylab.serialization.types.FundamentalTypes;
import ecologylab.serialization.types.ScalarType;
import ecologylab.serialization.types.TypeRegistry;
import ecologylab.serialization.types.element.Mappable;

/**
 * Used to provide convenient access for setting and getting values, using the
 * ecologylab.serialization type system. Provides marshalling and unmarshalling from Strings.
 * 
 * @author andruid
 */
@SuppressWarnings("rawtypes")
@simpl_inherit
public class FieldDescriptor extends DescriptorBase implements FieldTypes, Mappable<String>
{
	
	public static final String	NULL	= ScalarType.DEFAULT_VALUE_STRING;

	@simpl_scalar
	protected Field							field;		//TODO -- will not need to serialize this field, but lets keep doing it
																				// but lets keep doing it; 
																				// otherwise: that will temporarily break de/serialization in Objective C
	/**
	 * For nested elements, and collections or maps of nested elements. The class descriptor
	 */

	@simpl_composite
	private ClassDescriptor			elementClassDescriptor; // TODO: reading this representation in any other language
																											// will require it to have graph serialization working!
	
	@simpl_scalar
	private	String							mapKeyFieldName;
	
	/**
	 * Descriptor for the class that this field is declared in.
	 */
	@simpl_composite
	protected ClassDescriptor		declaringClassDescriptor; 

	@simpl_scalar
	private Class								elementClass; //TODO -- do not serialize this field 
	
	@simpl_scalar
	private boolean isGeneric;

	/////////////////// next fields are for polymorphic fields ////////////////////////////////////////
	/**
	 * Null if the tag for this field is derived from its field declaration. For most fields, tag is
	 * derived from the field declaration (using field name or @xml_tag).
	 * <p/>
	 * However, for polymorphic fields, such as those declared using @xml_class, @xml_classes, or @xml_scope,
	 * the tag is derived from the class declaration (using class name or @xml_tag). This is, for
	 * example, required for polymorphic nested and collection fields. For these fields, this slot
	 * contains an array of the legal classes, which will be bound to this field during
	 * translateFromXML().
	 */
	@simpl_map("polymorph_class_descriptor")
	@simpl_map_key_field("tagName")
	private HashMapArrayList<String, ClassDescriptor>	polymorphClassDescriptors; //TODO serialize this

	@simpl_map("polymorph_class")
	private HashMap<String, Class>										polymorphClasses;					//TODO do not serialize this

	@simpl_map("library_namespace")
	private HashMap<String, String>	libraryNamespaces						= new HashMap<String, String>();
	
	@simpl_scalar
	private int									type;

	/**
	 * This slot makes sense only for attributes and leaf nodes
	 */
	@simpl_scalar
	private ScalarType					scalarType;
	
	@simpl_composite
	private CollectionType			collectionType;
	
	@simpl_scalar
	private Hint								xmlHint;
	
	@simpl_scalar
	private boolean																		isEnum;

	/**
	 * An option for scalar formatting.
	 */
	private String[]																	format;

	@simpl_scalar
	private boolean																		isCDATA;

	@simpl_scalar
	private boolean																		needsEscaping;

	@simpl_scalar
	Pattern																						filterRegex;

	@simpl_scalar
	String																						filterReplace;

	/**
	 * The FieldDescriptor for the field in a wrap.
	 */
	private FieldDescriptor														wrappedFD;


	private HashMap<Integer, ClassDescriptor>					tlvClassDescriptors;

	@simpl_scalar
	private String																		unresolvedScopeAnnotation	= null;

	/**
 * 
 */
	@simpl_scalar
	private String																		collectionOrMapTagName;
	
	@simpl_scalar
	private String																		compositeTagName;

	/**
	 * Used for Collection and Map fields. Tells if the XML should be wrapped by an intermediate
	 * element.
	 */
	@simpl_scalar
	private boolean																		wrapped;

	private Method																		setValueMethod;

	public static final Class[]												SET_METHOD_STRING_ARG			=
																																							{ String.class };


	private String																		bibtexTag									= "";

	private boolean																		isBibtexKey								= false;
	
	@simpl_scalar
	private String fieldType ;
	
	@simpl_scalar
	private String genericParametersString;
	
	private ArrayList<Class> dependencies = new ArrayList<Class>();
	
	/**
	 * Default constructor only for use by translateFromXML().
	 */
	public FieldDescriptor()
	{
		super();

	}

	/**
	 * Constructor for the pseudo-FieldDescriptor associated with each ClassDesctiptor, for
	 * translateToXML of fields that deriveTagFromClass.
	 * 
	 * @param baseClassDescriptor
	 */
	public FieldDescriptor(ClassDescriptor baseClassDescriptor)
	{
		super(baseClassDescriptor.getTagName(), null);
		this.declaringClassDescriptor = baseClassDescriptor;
		this.field 			= null;
		this.type 			= PSEUDO_FIELD_DESCRIPTOR;
		this.scalarType = null;
		this.bibtexTag	= baseClassDescriptor.getBibtexType();
	}

	/**
	 * Constructor for wrapper FieldDescriptor.
	 * (Seems to not have a name; i guess the constituent field inside the wrapper is where the name is.)
	 * 
	 * @param baseClassDescriptor
	 * @param wrappedFD
	 * @param wrapperTag
	 */
	public FieldDescriptor(ClassDescriptor baseClassDescriptor, FieldDescriptor wrappedFD, String wrapperTag)
	{
		super(wrapperTag, null);
		this.declaringClassDescriptor = baseClassDescriptor;
		this.wrappedFD 								= wrappedFD;
		this.type 										= WRAPPER;
	}

	/**
	 * This is the normal constructor.
	 * 
	 * @param declaringClassDescriptor
	 * @param field
	 * @param annotationType
	 *          Coarse pre-evaluation of the field's annotation type. Does not differentiate scalars
	 *          from elements, or check for semantic consistency.
	 */
	public FieldDescriptor(ClassDescriptor declaringClassDescriptor, Field field, int annotationType) // String
	// nameSpacePrefix
	{
		super(XMLTools.getXmlTagName(field), field.getName()); // uses field name or @xml_tag declaration
		this.declaringClassDescriptor = declaringClassDescriptor;
		this.field 			= field;
		this.field.setAccessible(true);
		this.fieldType 	= field.getType().getSimpleName();
		if (field.isAnnotationPresent(simpl_map_key_field.class))
			this.mapKeyFieldName = field.getAnnotation(simpl_map_key_field.class).value();
//		this.name = (field != null) ? field.getName() : "NULL";

		derivePolymorphicDescriptors(field);

		this.bibtexTag = XMLTools.getBibtexTagName(field);
		this.isBibtexKey = XMLTools.getBibtexKey(field);

		// TODO XmlNs
		// if (nameSpacePrefix != null)
		// {
		// tagName = nameSpacePrefix + tagName;
		// }
		type = UNSET_TYPE; // for debugging!

		if (annotationType == SCALAR)
			type = deriveScalarSerialization(field);
		else
			type = deriveNestedSerialization(field, annotationType);

		// looks old: -- implement this next???
		// if (XMLTools.isNested(field))
		// setupXmlText(ClassDescriptor.getClassDescriptor((Class<ElementState>) field.getType()));

		String fieldName = field.getName();
		StringBuilder capFieldName = new StringBuilder(fieldName);
		capFieldName.setCharAt(0, Character.toUpperCase(capFieldName.charAt(0)));
		String setMethodName = "set" + capFieldName;

		setValueMethod = ReflectionTools.getMethod(declaringClassDescriptor.getDescribedClass(),
				setMethodName, SET_METHOD_STRING_ARG);
		
		addNamespaces();
		if(javaParser != null)
		{
			comment = javaParser.getJavaDocComment(field);
		}
		isGeneric = (field.getGenericType() instanceof ParameterizedType)?true:false;		
		if(isGeneric)
		{
			genericParametersString = XMLTools.getJavaGenericParametersString(field);
			dependencies = XMLTools.getJavaGenericDependencies(field);
		}
	}

	protected FieldDescriptor(
			String tagName,
			String comment,
			int type,
			ClassDescriptor elementClassDescriptor,
			ClassDescriptor declaringClassDescriptor,
			String fieldName,
			ScalarType scalarType,
			Hint xmlHint,
			String fieldType)
	{
		this(tagName, comment, type, elementClassDescriptor, declaringClassDescriptor, fieldName, 
				scalarType, xmlHint, fieldType, 
				(type == COLLECTION_ELEMENT || type == COLLECTION_SCALAR) ? FundamentalTypes.ARRAYLIST_TYPE : null);
	}
	protected FieldDescriptor(
			String tagName,
			String comment,
			int type,
			ClassDescriptor elementClassDescriptor,
			ClassDescriptor declaringClassDescriptor,
			String fieldName,
			ScalarType scalarType,
			Hint xmlHint,
			String fieldType, CollectionType collectionType)
	{
		super(tagName, fieldName, comment);
		assert(type != COMPOSITE_ELEMENT || elementClassDescriptor != null);

		this.type											= type;
		this.elementClassDescriptor		= elementClassDescriptor;
		this.declaringClassDescriptor	= declaringClassDescriptor;

		this.scalarType 							= scalarType;
		this.xmlHint 									= xmlHint;
		this.fieldType								= fieldType;
		this.collectionType						= collectionType;
	}
	
	public String getUnresolvedScopeAnnotation()
	{
		return this.unresolvedScopeAnnotation;
	}
	
	protected void setUnresolvedScopeAnnotation(String scopeName)
	{
		this.unresolvedScopeAnnotation = scopeName;
	}

	/**
	 * Process annotations for polymorphic fields.
	 * These use meta-language to map tags for translate from based on classes
	 * (instead of field names).
	 * 
	 * @param field
	 * @return
	 */
	private boolean derivePolymorphicDescriptors(Field field)
	{
		// @xml_scope
		final ElementState.simpl_scope scopeAnnotationObj = field
				.getAnnotation(ElementState.simpl_scope.class);
		final String scopeAnnotation = (scopeAnnotationObj == null) ? null : scopeAnnotationObj.value();

		if (scopeAnnotation != null && scopeAnnotation.length() > 0)
		{
			if (!resolveScopeAnnotation(scopeAnnotation))
			{
				unresolvedScopeAnnotation = scopeAnnotation;
				declaringClassDescriptor.registerUnresolvedScopeAnnotationFD(this);
			}
		}
		// @xml_classes
		final ElementState.simpl_classes classesAnnotationObj = field
				.getAnnotation(ElementState.simpl_classes.class);
		final Class[] classesAnnotation = (classesAnnotationObj == null) ? null : classesAnnotationObj
				.value();
		if ((classesAnnotation != null) && (classesAnnotation.length > 0))
		{
			initPolymorphClassDescriptorsArrayList(classesAnnotation.length);
			for (Class thatClass : classesAnnotation)
				if (ElementState.class.isAssignableFrom(thatClass))
				{
					ClassDescriptor classDescriptor = ClassDescriptor.getClassDescriptor(thatClass);
					registerPolymorphicDescriptor(classDescriptor);
					polymorphClasses.put(classDescriptor.getTagName(), classDescriptor.getDescribedClass());
				}
		}
		return polymorphClassDescriptors != null;
	}

	/**
	 * Register a ClassDescriptor that is polymorphically engaged with this field.
	 * 
	 * @param classDescriptor
	 */
	protected void registerPolymorphicDescriptor(ClassDescriptor classDescriptor)
	{
		if (polymorphClassDescriptors == null)
			initPolymorphClassDescriptorsArrayList(1);

		String classTag = classDescriptor.getTagName();
		polymorphClassDescriptors.put(classTag, classDescriptor);
		tlvClassDescriptors.put(classTag.hashCode(), classDescriptor);

		ArrayList<String> otherTags = classDescriptor.otherTags();
		if (otherTags != null)
			for (String otherTag : otherTags)
			{
				if ((otherTag != null) && (otherTag.length() > 0))
				{
					polymorphClassDescriptors.put(otherTag, classDescriptor);
					tlvClassDescriptors.put(otherTag.hashCode(), classDescriptor);
				}
			}
	}

	/**
	 * Generate tag -> class mappings for a @serial_scope declaration.
	 * 
	 * @param scopeAnnotation
	 *          Name of the scope to lookup in the global space. Must be non-null.
	 * 
	 * @return true if the scope annotation is successfully resolved to a TranslationScope.
	 */
	private boolean resolveScopeAnnotation(final String scopeAnnotation)
	{
		TranslationScope scope = TranslationScope.get(scopeAnnotation);
		if (scope != null)
		{
			Collection<ClassDescriptor> scopeClassDescriptors = scope.getClassDescriptors();
			initPolymorphClassDescriptorsArrayList(scopeClassDescriptors.size());
			for (ClassDescriptor classDescriptor : scopeClassDescriptors)
			{
				String tagName = classDescriptor.getTagName();
				polymorphClassDescriptors.put(tagName, classDescriptor);
				polymorphClasses.put(tagName, classDescriptor.getDescribedClass());
				tlvClassDescriptors.put(tagName.hashCode(), classDescriptor);
			}
		}
		return scope != null;
	}

	/**
	 * If there is an unresolvedScopeAnnotation, because a scope had not yet been declared when a
	 * ClassDescriptor that uses it was constructed, try again.
	 * 
	 * @return
	 */
	boolean resolveUnresolvedScopeAnnotation()
	{
		if (unresolvedScopeAnnotation == null)
			return true;

		boolean result = resolveScopeAnnotation(unresolvedScopeAnnotation);
		if (result)
		{
			unresolvedScopeAnnotation = null;
			declaringClassDescriptor.mapPolymorphicClassDescriptors(this);
		}
		return result;
	}

	private void initPolymorphClassDescriptorsArrayList(int initialSize)
	{
		if (polymorphClassDescriptors == null)
			polymorphClassDescriptors = new HashMapArrayList<String, ClassDescriptor>(initialSize);
		if (polymorphClasses == null)
			polymorphClasses = new HashMap<String, Class>(initialSize);
		if (tlvClassDescriptors == null)
			tlvClassDescriptors = new HashMap<Integer, ClassDescriptor>(initialSize);
	}

	/**
	 * Bind the ScalarType for a scalar typed field (attribute, leaf node, text). As appropriate,
	 * derive other context for scalar fields (is leaf, format).
	 * <p/>
	 * This method should only be called when you already know the field has a scalar annotation.
	 * 
	 * @param scalarField
	 *          Source for class & for annotations.
	 * 
	 * @return SCALAR, IGNORED_ATTRIBUTE< or IGNORED_ELEMENT
	 */
	private int deriveScalarSerialization(Field scalarField)
	{
		int result = deriveScalarSerialization(scalarField.getType(), scalarField);
		if (xmlHint == Hint.XML_TEXT || xmlHint == Hint.XML_TEXT_CDATA)
			this.declaringClassDescriptor.setScalarTextFD(this);
		return result;
	}

	/**
	 * Check for serialization hints for the field.
	 * 
	 * Lookup the scalar type for the class, and any serialization details, such as needsEscaping &
	 * format.
	 * 
	 * @param thatClass
	 *          Class that we seek a ScalarType for.
	 * @param field
	 *          Field to acquire annotations about the serialization.
	 * 
	 * @return SCALAR, IGNORED_ATTRIBUTE< or IGNORED_ELEMENT
	 */
	private int deriveScalarSerialization(Class thatClass, Field field)
	{
		isEnum = XMLTools.isEnum(field);
		xmlHint = XMLTools.simplHint(field); // TODO -- confirm that default case is acceptable
		scalarType = TypeRegistry.getScalarType(thatClass);

		if (scalarType == null)
		{
			String msg = "Can't find ScalarType to serialize field: \t\t" + thatClass.getSimpleName()
					+ "\t" + field.getName() + ";";
			warning("In class " + declaringClassDescriptor.getDescribedClass().getName(), msg);
			return (xmlHint == Hint.XML_ATTRIBUTE) ? IGNORED_ATTRIBUTE : IGNORED_ELEMENT;
		}

		format = XMLTools.getFormatAnnotation(field);
		if (xmlHint != Hint.XML_ATTRIBUTE)
		{
			needsEscaping = scalarType.needsEscaping();
			isCDATA = xmlHint == Hint.XML_LEAF_CDATA || xmlHint == Hint.XML_TEXT_CDATA;
		}

		ElementState.simpl_filter filterAnnotation = field
				.getAnnotation(ElementState.simpl_filter.class);
		if (filterAnnotation != null)
		{
			String regexString = filterAnnotation.regex();
			if (regexString != null && regexString.length() > 0)
			{
				filterRegex = Pattern.compile(regexString);
				filterReplace = filterAnnotation.replace();
			}
		}
		return SCALAR;
	}

	/**
	 * Figure out the type of field. Build associated data structures, such as collection or element
	 * class & tag. Process @xml_other_tags.
	 * 
	 * @param field
	 * @param annotationType
	 *          Partial type information from the field declaration annotations, which are required.
	 */
	// FIXME -- not complete!!!! return to finish other cases!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	@SuppressWarnings("unchecked")
	private int deriveNestedSerialization(Field field, int annotationType)
	{
		int result = annotationType;
		Class fieldClass = field.getType();
		switch (annotationType)
		{
		case COMPOSITE_ELEMENT:
		
			String compositeTag = field.getAnnotation(ElementState.simpl_composite.class).value();
			Boolean isWrap = field.isAnnotationPresent(ElementState.simpl_wrap.class);
			
			if (!checkAssignableFrom(ElementState.class, field, fieldClass, "@simpl_composite"))
				result = IGNORED_ELEMENT;

			boolean compositeTagIsNullOrEmpty = StringTools.isNullOrEmpty(compositeTag);
			if (!isPolymorphic())
			{
				if (isWrap && compositeTagIsNullOrEmpty)
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tCan't translate  @simpl_composite() " + field.getName()
							+ " because its tag argument is missing.");
					return IGNORED_ELEMENT;
				}
				
				if(!isWrap & !compositeTagIsNullOrEmpty)
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tIgnoring argument to  @simpl_composite() " + field.getName()
							+ " because it is declared polymorphic.");
				}
			
				 elementClassDescriptor = ClassDescriptor.getClassDescriptor(fieldClass);
				 elementClass = elementClassDescriptor.getDescribedClass();
				 compositeTag = XMLTools.getXmlTagName(field);
			}
			else
			{
				if (!compositeTagIsNullOrEmpty)
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tIgnoring argument to  @simpl_composite() " + field.getName()
							+ " because it is declared polymorphic.");
				}
			}
			compositeTagName = compositeTag;
			break;
		case COLLECTION_ELEMENT:
			final String collectionTag = field.getAnnotation(ElementState.simpl_collection.class).value();
			if (!checkAssignableFrom(Collection.class, field, fieldClass, "@xml_collection"))
				return IGNORED_ELEMENT;

			if (!isPolymorphic())
			{
				Class collectionElementClass = getTypeArgClass(field, 0); // 0th type arg for
				// Collection<FooState>

				if (collectionTag == null || collectionTag.isEmpty())
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tCan't translate  @xml_collection() " + field.getName()
							+ " because its tag argument is missing.");
					return IGNORED_ELEMENT;
				}
				if (collectionElementClass == null)
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tCan't translate  @xml_collection() " + field.getName()
							+ " because the parameterized type argument for the Collection is missing.");
					return IGNORED_ELEMENT;
				}
				if (ElementState.class.isAssignableFrom(collectionElementClass) && !TypeRegistry.containsScalarType(collectionElementClass))
				{
					elementClassDescriptor = ClassDescriptor.getClassDescriptor(collectionElementClass);
					elementClass = elementClassDescriptor.getDescribedClass();
				}
				else
				{
					result = COLLECTION_SCALAR;
					deriveScalarSerialization(collectionElementClass, field);
					// FIXME -- add error handling for IGNORED due to scalar type lookup fails
					if (scalarType == null)
					{
						result = IGNORED_ELEMENT;
						warning("Can't identify ScalarType for serialization of " + collectionElementClass);
					}
				}
			}
			else
			{
				if (collectionTag != null && !collectionTag.isEmpty())
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tIgnoring argument to  @xml_collection() " + field.getName()
							+ " because it is declared polymorphic with @xml_classes.");
				}
			}
			collectionOrMapTagName	= collectionTag;
			collectionType					= TypeRegistry.getCollectionType(field);
			break;
		case MAP_ELEMENT:
			String mapTag = field.getAnnotation(ElementState.simpl_map.class).value();
			if (!checkAssignableFrom(Map.class, field, fieldClass, "@xml_map"))
				return IGNORED_ELEMENT;

			if (!isPolymorphic())
			{
				Class mapElementClass = getTypeArgClass(field, 1); // "1st" type arg for Map<FooState>

				if (mapTag == null || mapTag.isEmpty())
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tCan't translate  @xml_map() " + field.getName()
							+ " because its tag argument is missing.");
					return IGNORED_ELEMENT;
				}
				if (mapElementClass == null)
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tCan't translate  @xml_map() " + field.getName()
							+ " because the parameterized type argument for the Collection is missing.");
					return IGNORED_ELEMENT;
				}

				if (ElementState.class.isAssignableFrom(mapElementClass))
				{
					elementClassDescriptor = ClassDescriptor.getClassDescriptor(mapElementClass);
					elementClass = elementClassDescriptor.getDescribedClass();
				}
				else
				{
					result = MAP_SCALAR; // TODO -- do we really support this case??
					// FIXME -- add error handling for IGNORED due to scalar type lookup fails
					deriveScalarSerialization(mapElementClass, field);
				}
			}
			else
			{
				if (mapTag != null && !mapTag.isEmpty())
				{
					warning("In " + declaringClassDescriptor.getDescribedClass()
							+ "\n\tIgnoring argument to  @xml_map() " + field.getName()
							+ " because it is declared polymorphic with @xml_classes.");
				}
			}
			collectionOrMapTagName = mapTag;
			collectionType					= TypeRegistry.getCollectionType(field);
			break;
		default:
			break;
		}
		switch (annotationType)	// set-up wrap as appropriate
		{
		case COLLECTION_ELEMENT:
		case MAP_ELEMENT:
			if (!field.isAnnotationPresent(ElementState.simpl_nowrap.class))
				wrapped = true;
			collectionType	= TypeRegistry.getCollectionType(field);
			break;
		case COMPOSITE_ELEMENT:
			if(field.isAnnotationPresent(ElementState.simpl_wrap.class))
				wrapped = true;
		}
	
		/*
		 * else { // deriveTagFromClasses // TODO Monday }
		 */
		if (result == UNSET_TYPE)
		{
			warning("Programmer error -- can't derive type.");
			result = IGNORED_ELEMENT;
		}

		return result;
	}

	public CollectionType getCollectionType()
	{
		return collectionType;
	}

	private boolean checkAssignableFrom(Class targetClass, Field field, Class fieldClass,
			String annotationDescription)
	{
		boolean result = targetClass.isAssignableFrom(fieldClass);
		if (!result)
		{
			warning("In " + declaringClassDescriptor.getDescribedClass() + "\n\tCan't translate  "
					+ annotationDescription + "() " + field.getName()
					+ " because the annotated field is not an instance of " + targetClass.getSimpleName()
					+ ".");
		}
		return result;
	}

	/**
	 * Get the value of the ith declared type argument from a field declaration. Only works when the
	 * type variable is directly instantiated in the declaration.
	 * <p/>
	 * DOES NOT WORK when the type variable is instantiated outside the declaration, and passed in.
	 * This is because in Java, generic type variables are (lamely!) erased after compile time. They
	 * do not exist at runtime :-( :-( :-(
	 * 
	 * @param field
	 * @param i
	 *          Index of the type variable in the field declaration.
	 * 
	 * @return The class of the type variable, if it exists.
	 */
	@SuppressWarnings("unchecked")
	public Class<?> getTypeArgClass(Field field, int i)
	{
		Class result = null;

		java.lang.reflect.Type[] typeArgs = ReflectionTools.getParameterizedTypeTokens(field);
		if (typeArgs != null)
		{
			final int max = typeArgs.length - 1;
			if (i > max)
				i = max;
			final Type typeArg0 = typeArgs[i];
			if (typeArg0 instanceof Class)
			{
				result = (Class) typeArg0;
			}
			else if (typeArg0 instanceof ParameterizedTypeImpl)
			{ // nested parameterized type
				ParameterizedTypeImpl pti = (ParameterizedTypeImpl) typeArg0;
				result = pti.getRawType();
			}
			else if (typeArg0 instanceof TypeVariableImpl)
			{
				TypeVariableImpl tvi = (TypeVariableImpl) typeArg0;
				Type[] tviBounds = tvi.getBounds();
				result = (Class) tviBounds[0];
				debug("yo! " + result);
			}

			else
			{
				error("getTypeArgClass(" + field + ", " + i
						+ " yucky! Consult s.im.mp serialization developers.");
			}
		}
		return result;
	}

	/**
	 * 
	 * @return true if this field represents a ScalarType, not a nested element or collection thereof.
	 */
	public boolean isScalar()
	{
		return scalarType != null;
	}

	public boolean isCollection()
	{
		switch (type)
		{
		case MAP_ELEMENT:
		case MAP_SCALAR:
		case COLLECTION_ELEMENT:
		case COLLECTION_SCALAR:
			return true;
		default:
			return false;
		}
	}

	public boolean isNested()
	{
		return type == COMPOSITE_ELEMENT;
	}

	public Hint getXmlHint()
	{
		return xmlHint;
	}
	
	public boolean set(ElementState context, String valueString)
	{
		return set(context, valueString, null);
	}

	/**
	 * In the supplied context object, set the *typed* value of the field, using the valueString
	 * passed in. Unmarshalling is performed automatically, by the ScalarType already stored in this.
	 * <p/>
	 * Use a set method, if one is defined.
	 * 
	 * @param context
	 *          ElementState object to set the Field in this.
	 * 
	 * @param valueString
	 *          The value to set, which this method will use with the ScalarType, to create the value
	 *          that will be set.
	 */
	public boolean set(ElementState context, String valueString,
			ScalarUnmarshallingContext scalarUnMarshallingContext)
	{
		boolean result = false;
		// if ((valueString != null) && (context != null)) andruid & andrew 4/14/09 -- why not allow set
		// to null?!
		if (context != null && isScalar() /* do we really need this check??? */)
		{
			scalarType.setField(context, field, valueString, null, scalarUnMarshallingContext);
			result = true;
		}
		return result;
	}

	/**
	 * In the supplied context object, set the non-scalar field to a non-scalar value.
	 * 
	 * @param context
	 * 
	 * @param value
	 *          An ElementState, or a Collection, or a Map.
	 */
	public void set(ElementState context, Object value)
	{
		if (!isScalar())
		{
			setField(context, value);
		}
	}

	public void setField(ElementState context, Object value)
	{
		try
		{
			field.set(context, value);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Get the String representation of the value of the field, in the context object, using the
	 * ScalarType.
	 * 
	 * @param context
	 * @return
	 */
	public String getValueString(Object context)
	{
		String result = NULL;
		if (context != null && isScalar())
		{
			result = scalarType.toString(field, context);

		}
		return result;
	}

	/**
	 * NB: For polymorphic fields, the value of this field is meaningless, except for wrapped
	 * collections and maps.
	 * 
	 * @return The tag name that this field is translated to XML with.
	 */
	public String getBibtexTagName()
	{
		if (bibtexTag == null || bibtexTag.equals(""))
			return tagName;
		return bibtexTag;
	}

	/**
	 * @return the scalarType of the field
	 */
	public ScalarType<?> getScalarType()
	{
		return scalarType;
	}

	/**
	 * @return the field
	 */
	public Field getField()
	{
		return field;
	}

	/**
	 * @return the class of the field
	 */
	public Class<?> getFieldType()
	{
		return field.getType();
	}

	/**
	 * 
	 * @return The OptimizationTypes type of the field.
	 */
	public int getType()
	{
		return type;
	}

	public ElementState getNested(ElementState context)
	{
		return (ElementState) ReflectionTools.getFieldValue(context, field);
	}

	public Map getMap(ElementState context)
	{
		return (Map) ReflectionTools.getFieldValue(context, field);
	}

	public Collection getCollection(ElementState context)
	{
		return (Collection) ReflectionTools.getFieldValue(context, field);
	}

	public boolean isMixin()
	{
		return false;
	}

	public ElementState getAndPerhapsCreateNested(ElementState context)
	{
		ElementState result = getNested(context);

		if (result == null)
		{
			result = (ElementState) ReflectionTools.getInstance(field.getType());
			ReflectionTools.setFieldValue(context, field, result);
		}
		return result;
	}

	public boolean isWrapped()
	{
		return wrapped;
	}
	
	protected void setWrapped(boolean wrapped)
	{
		this.wrapped = wrapped;
	}

	/**
	 * Use this and the context to append an attribute / value pair to the StringBuilder passed in.
	 * 
	 * @param buffy
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	public void appendValueAsAttribute(StringBuilder buffy, Object context)
			throws IllegalArgumentException, IllegalAccessException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			Field field = this.field;

			if (scalarType == null)
			{
				weird("scalarType = null!");
			}
			else if (!scalarType.isDefaultValue(field, context))
			{
				// for this field, generate tags and attach name value pair

				// TODO if type.isFloatingPoint() -- deal with floatValuePrecision here!
				// (which is an instance variable of this) !!!

				buffy.append(' ');
				buffy.append(this.tagName);
				buffy.append('=');
				buffy.append('"');

				scalarType.appendValue(buffy, this, context);
				buffy.append('"');
			}
		}
	}

	public boolean isDefaultValue(Object context) throws IllegalArgumentException,
			IllegalAccessException
	{
		if (context != null)
			return scalarType.isDefaultValue(this.field, context);
		return false;
	}

	public void appendValueAsJSONAttribute(Appendable appendable, Object context, boolean isFirst)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			Field field = this.field;

			if (!scalarType.isDefaultValue(field, context))
			{
				if (!isFirst)
					appendable.append(", ");

				appendable.append('"');
				appendable.append(tagName);
				appendable.append('"');
				appendable.append(':');
				appendable.append('"');

				scalarType.appendValue(appendable, this, context, null, FORMAT.JSON);
				appendable.append('"');

			}
		}
	}

	/**
	 * Use this and the context to append an attribute / value pair to the Appendable passed in.
	 * 
	 * @param appendable
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void appendTLV(DataOutputStream dataOutputStream, Object context)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			Field field = this.field;

			if (!scalarType.isDefaultValue(field, context))
			{
				dataOutputStream.writeInt(getTLVId());

				StringBuilder buffy = new StringBuilder();
				scalarType.appendValue(buffy, this, context);

				ByteArrayOutputStream temp = new ByteArrayOutputStream();
				DataOutputStream tempStream = new DataOutputStream(temp);
				tempStream.writeBytes(buffy.toString());

				dataOutputStream.writeInt(tempStream.size());
				temp.writeTo(dataOutputStream);
			}
		}
	}

	/**
	 * Use this and the context to append an attribute / value pair to the Appendable passed in.
	 * 
	 * @param appendable
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void appendTLVCollectionItem(DataOutputStream dataOutputStream, Object instance)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (instance != null)
		{
			ScalarType scalarType = this.scalarType;

			dataOutputStream.writeInt(getTLVId());

			StringBuilder buffy = new StringBuilder();
			scalarType.appendValue(instance, buffy, true, null);

			ByteArrayOutputStream temp = new ByteArrayOutputStream();
			DataOutputStream tempStream = new DataOutputStream(temp);
			tempStream.writeBytes(buffy.toString());

			dataOutputStream.writeInt(tempStream.size());
			temp.writeTo(dataOutputStream);

		}
	}

	/**
	 * Use this and the context to append an attribute / value pair to the Appendable passed in.
	 * 
	 * @param appendable
	 * @param context
	 * @param serializationContext
	 *          TODO
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void appendValueAsAttribute(Appendable appendable, Object context,
			TranslationContext serializationContext) throws IllegalArgumentException,
			IllegalAccessException, IOException
	{
		if (context != null)
		{
			Object value = this.getValue(context);
			String valueString = value == null ? NULL : value.toString();
			ScalarType scalarType = this.scalarType;
			if (value != null && !scalarType.isDefaultValue(valueString))
			{
				// for this field, generate tags and attach name value pair

				// TODO if type.isFloatingPoint() -- deal with floatValuePrecision here!
				// (which is an instance variable of this) !!!

				appendable.append(' ');
				appendable.append(tagName);
				appendable.append('=');
				appendable.append('"');

				scalarType.appendValue(appendable, this, context, serializationContext, FORMAT.XML);
				appendable.append('"');
			}
		}
	}

	public Object getValue(Object context)
	{
		Object value = null;
		try
		{
			if (context != null)
				value = this.field.get(context);
		}
		catch (IllegalArgumentException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			debugA("WARNING re-trying access! " + e.getStackTrace()[0]);
			this.field.setAccessible(true);
			try
			{
				value = this.field.get(this);
			}
			catch (IllegalAccessException e1)
			{
				error("Can't access " + this.field.getName());
				e1.printStackTrace();
			}
		}
		return value;
	}
	
	/**
	 * Appends the label and value of a metadata field to HTML elements, including anchors where appropriate
	 * 
	 * @param context
	 * @param serializationContext
	 * @param tr
	 * @param labelString
	 * @param labelCssClass
	 * @param valueCssClass
	 * @param navigatesFD
	 * @param schemaOrgItemProp
	 * 
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void appendHtmlValueAsAttribute(Object context, TranslationContext serializationContext,
			Tr tr, String labelString, String labelCssClass, String valueCssClass, FieldDescriptor navigatesFD, String schemaOrgItemProp)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (!scalarType.isDefaultValue(field, context))
		{
			Td labelTd 		= new Td();
			Td valueTd 		= new Td();
			Div valueDiv	= new Div();
			Div labelDiv	= new Div();
			A labelAnchor = new A();
			A valueAnchor = new A();
			
			if (valueCssClass != null)			// does this cause problems? if so, is it because mmd is wrong? andruid & aaron 7/8/11
				valueDiv.setCssClass(valueCssClass);
			if (schemaOrgItemProp != null)
				valueDiv.setSchemaOrgItemProp(schemaOrgItemProp);
			
			labelTd.setAlign("right");
			labelTd.setCssClass(labelCssClass);
			
			ScalarType navigatesScalarType	= null;
			if (navigatesFD != null)
			{
				StringBuilder navigatesToBuffy	= new StringBuilder();
				navigatesScalarType							= navigatesFD.getScalarType();
				navigatesScalarType.appendValue(navigatesToBuffy, navigatesFD, context, serializationContext, FORMAT.XML);
				labelAnchor.setHref(navigatesToBuffy.toString());
				labelAnchor.setLink(labelString);
				labelDiv.members.add(labelAnchor);
			}
			else
			{
				labelDiv.setText(labelString);
			}

			labelTd.items.add(labelDiv);
			tr.cells.add(labelTd);

			StringBuilder valueBuffy = new StringBuilder();
//			scalarType.appendValue(valueBuffy, this, context, serializationContext, FORMAT.XML);
			Object instance = this.getValue(context);
			scalarType.appendValue(instance, valueBuffy, false, serializationContext, FORMAT.XML);
			
			if (navigatesFD != null)
			{
				StringBuilder buffy = new StringBuilder();
				navigatesScalarType.appendValue(buffy, navigatesFD, context, serializationContext, FORMAT.XML);
				valueAnchor.setHref(buffy.toString());
				valueAnchor.setLink(valueBuffy.toString());
				valueDiv.members.add(valueAnchor);
			}
			else
			{
				valueDiv.setText(valueBuffy.toString());
			}
			
			valueTd.items.add(valueDiv);
			tr.cells.add(valueTd);
		}
	}

	/**
	 * Use this and the context to append an attribute / value pair to the Appendable passed in.
	 * 
	 * @param appendable
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public void appendValueAsBibtexAttribute(Appendable appendable, Object context, boolean isFirst)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			Field field = this.field;

			if (!scalarType.isDefaultValue(field, context))
			{
				// for this field, generate tags and attach name value pair

				// TODO if type.isFloatingPoint() -- deal with floatValuePrecision here!
				// (which is an instance variable of this) !!!

				if (!isFirst)
					appendable.append(",");

				if (!isBibtexKey)
				{
					appendable.append('\n');
					appendable.append(' ');
					String bibTeXTagName = getBibtexTagName();
					bibTeXTagName = bibTeXTagName.replace('_', ' ');
					appendable.append(getBibtexTagName());
					appendable.append('=');
					appendable.append('{');
				}

				scalarType.appendValue(appendable, this, context, null, FORMAT.BIBTEX);

				if (!isBibtexKey)
					appendable.append('}');
			}
		}
	}

	static final String	START_CDATA	= "<![CDATA[";

	static final String	END_CDATA		= "]]>";

	/**
	 * Use this and the context to append a leaf node with value to the StringBuilder passed in,
	 * unless it turns out that the value is the default.
	 * 
	 * @param buffy
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	void appendLeaf(StringBuilder buffy, Object context) throws IllegalArgumentException,
			IllegalAccessException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			Field field = this.field;
			if (!scalarType.isDefaultValue(field, context))
			{
				// for this field, generate <tag>value</tag>

				// TODO if type.isFloatingPoint() -- deal with floatValuePrecision here!
				// (which is an instance variable of this) !!!
				writeOpenTag(buffy);

				appendTextValue(buffy, context, scalarType);

				writeCloseTag(buffy);
			}
		}
	}

	/**
	 * Write the value to the buffy, with appropraite marshalling, and, if specified, a CDATA wrapper.
	 * 
	 * @param buffy
	 *          Place to write to.
	 * @param context
	 *          Object to get the value from.
	 * @param scalarType
	 *          Performs the marshalling.
	 * @throws IllegalAccessException
	 */
	void appendTextValue(StringBuilder buffy, Object context, ScalarType scalarType)
			throws IllegalAccessException
	{
		if (isCDATA)
			buffy.append(START_CDATA);
		scalarType.appendValue(buffy, this, context); // escape if not CDATA! :-)
		if (isCDATA)
			buffy.append(END_CDATA);
	}

	/**
	 * Use this and the context to append a text node value to the StringBuilder passed in, unless it
	 * turns out that the value is the default.
	 * 
	 * @param buffy
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	void appendXMLTextScalar(StringBuilder buffy, Object context) throws IllegalArgumentException,
			IllegalAccessException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
			if (!scalarType.isDefaultValue(field, context))
				appendTextValue(buffy, context, scalarType);
		}
	}

	/**
	 * Use this and the context to append a text node value to the StringBuilder passed in, unless it
	 * turns out that the value is the default.
	 * 
	 * @param appendable
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	void appendXMLScalarText(Appendable appendable, Object context) throws IllegalArgumentException,
			IllegalAccessException, IOException
	{
		ScalarType scalarType = this.scalarType;
		if (!scalarType.isDefaultValue(field/* GO AWAY! xmlTextScalarField */, context))
			appendTextValue(appendable, context, scalarType);
	}

	/**
	 * Use this and the context to append a leaf node with value to the StringBuilder passed in.
	 * Consideration of default values is not evaluated.
	 * 
	 * @param buffy
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	void appendCollectionLeaf(StringBuilder buffy, Object instance) throws IllegalArgumentException,
			IllegalAccessException
	{
		if (instance != null)
		{
			ScalarType scalarType = this.scalarType;

			writeOpenTag(buffy);

			if (isCDATA)
				buffy.append(START_CDATA);
			scalarType.appendValue(instance, buffy, !isCDATA, null); // escape if not CDATA! :-)
			if (isCDATA)
				buffy.append(END_CDATA);

			writeCloseTag(buffy);
		}
	}

	void appendBibtexCollectionAttribute(Appendable appendable, Object instance, boolean isFirst,
			String delim) throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (instance != null)
		{
			if (!isFirst)
			{
				appendable.append(delim);
			}

			ScalarType scalarType = this.scalarType;
			scalarType.appendValue(instance, appendable, false, null, FORMAT.BIBTEX);
		}
	}

	void appendBibtexCollectionCompositeAttribute(Appendable appendable, Object instance,
			boolean isFirst) throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (instance != null)
		{
			if (!isFirst)
			{
				appendable.append(", ");
			}

			ScalarType scalarType = this.scalarType;
			scalarType.appendValue(appendable, this, instance, null, FORMAT.BIBTEX);

		}
	}

	public String getHtmlCompositeCollectionValue(Object instance, boolean isFirst)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		StringBuilder value = new StringBuilder();
		
		if (instance != null)
		{
			if (!isFirst)
				value.append(", ");
			scalarType.appendValue(value, this, instance, null, FORMAT.XML);			
		}
		
		return value.toString();
	}

	void appendJSONCollectionAttribute(Appendable appendable, Object instance, boolean isFirst)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (instance != null)
		{
			if (!isFirst)
			{
				appendable.append(',');
			}

			ScalarType scalarType = this.scalarType;
			appendable.append('"');
			scalarType.appendValue(instance, appendable, false, null, FORMAT.JSON);
			appendable.append('"');
		}
	}

	/**
	 * Use this and the context to append a leaf node with value to the Appendable passed in.
	 * Consideration of default values is not evaluated.
	 * 
	 * @param appendable
	 * @param context
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	void appendCollectionLeaf(Appendable appendable, Object instance)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (instance != null)
		{
			ScalarType scalarType = this.scalarType;

			writeOpenTag(appendable);

			if (isCDATA)
				appendable.append(START_CDATA);
			scalarType.appendValue(instance, appendable, !isCDATA, null, FORMAT.XML); // escape if not CDATA! :-)
			if (isCDATA)
				appendable.append(END_CDATA);

			writeCloseTag(appendable);
		}
	}

	/**
	 * Append just the text value to the appendable. No element tags, but it does account for CDATA.
	 * 
	 * @param appendable
	 * @param context
	 * @param scalarType
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	void appendTextValue(Appendable appendable, Object context, ScalarType scalarType)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (isCDATA)
			appendable.append(START_CDATA);
		scalarType.appendValue(appendable, this, context, null, FORMAT.XML); // escape if not CDATA! :-)
		if (isCDATA)
			appendable.append(END_CDATA);
	}

	/**
	 * Use this and the context to append a leaf node with value to the Appendable passed in.
	 * 
	 * @param context
	 * @param serializationContext
	 *          TODO
	 * @param buffy
	 * @param isAtXMLText
	 * 
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	void appendLeaf(Appendable appendable, Object context, TranslationContext serializationContext)
			throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (context != null)
		{
			ScalarType scalarType = this.scalarType;
//			Field field = this.field;
//			if (!scalarType.isDefaultValue(field, context)) // this line fails with proxy classes
			Object value = this.getValue(context);
			if (value != null && !scalarType.isDefaultValue(value.toString()))
			{
				// for this field, generate <tag>value</tag>

				// TODO if type.isFloatingPoint() -- deal with floatValuePrecision here!
				// (which is an instance variable of this) !!!

				writeOpenTag(appendable);

				if (isCDATA)
					appendable.append(START_CDATA);
				scalarType.appendValue(appendable, this, context, serializationContext, FORMAT.XML); // escape if not
																																									// CDATA! :-)
				if (isCDATA)
					appendable.append(END_CDATA);

				writeCloseTag(appendable);
			}
		}
	}

	public boolean isCDATA()
	{
		return isCDATA;
	}

	public boolean isNeedsEscaping()
	{
		return needsEscaping;
	}

	public String[] getFormat()
	{
		return format;
	}

	public String toString()
	{
		String name = (field != null) ? field.getName() : "NO_FIELD";
		return this.getClassSimpleName() + "[" + name + " < " + declaringClassDescriptor.getDescribedClass()
				+ " type=0x" + Integer.toHexString(type) + "]";
	}

	/**
	 * If this field is polymorphic, a Collection of ClassDescriptors for the polymorphically associated classes.
	 * 
	 * @return	Collection, or null, if the field is not polymorphic
	 */
	public Collection<ClassDescriptor> getPolymorphicClassDescriptors()
	{
		return (polymorphClassDescriptors == null || polymorphClassDescriptors.size() == 0) ?
			null :
			polymorphClassDescriptors.values();
	}
	
	/**
	 * If this field is polymorphic, a Collection of Strings of all possible tags
	 * for the polymorphically associated classes.
	 * This is usually the tagName field of each ClassDescriptor.
	 * But it may be more, specifically if any of the classes are defined with @xml_other_tags.
	 * 
	 * @return	Collection, or null, if the field is not polymorphic
	 */
	public Collection<String> getPolymorphicTags()
	{
		return (polymorphClassDescriptors == null || polymorphClassDescriptors.size() == 0) ?
				null :
				polymorphClassDescriptors.keySet();
	}
	
	public HashMap<String, Class> getPolymorphicClasses()
	{
		if (polymorphClasses == null)
			return null;
		else
			return polymorphClasses;
	}

	public void writeElementStart(StringBuilder buffy)
	{
		buffy.append('<').append(elementStart());
	}

	public void writeElementStart(Appendable appendable) throws IOException
	{
		appendable.append('<').append(elementStart());
	}

	void writeOpenTag(StringBuilder buffy)
	{
		buffy.append('<').append(elementStart()).append('>');
	}

	void writeCloseTag(StringBuilder buffy)
	{
		buffy.append('<').append('/').append(elementStart()).append('>');
	}

	void writeOpenTag(Appendable buffy) throws IOException
	{
		buffy.append('<').append(elementStart()).append('>');
	}

	void writeCloseTag(Appendable buffy) throws IOException
	{
		buffy.append('<').append('/').append(elementStart()).append('>');
	}

	/**
	 * Write the tags for opening and closing a wrapped collection.
	 * 
	 * @param buffy
	 * @param close
	 */
	public void writeWrap(StringBuilder buffy, boolean close)
	{
		buffy.append('<');
		if (close)
			buffy.append('/');
		buffy.append(tagName).append('>');
	}

	public void writeJSONWrap(Appendable appendable, boolean close) throws IOException
	{
		if (!close)
		{
			appendable.append('"');
			appendable.append(tagName);
			appendable.append('"').append(':');
			appendable.append('{');
		}
		else
		{
			appendable.append('}');
		}
	}

	/**
	 * Write the tags for opening and closing a wrapped collection.
	 * 
	 * @param appendable
	 * @param close
	 * @throws IOException
	 */
	public void writeWrap(Appendable appendable, boolean close) throws IOException
	{
		appendable.append('<');
		if (close)
			appendable.append('/');
		appendable.append(tagName).append('>');
	}

	public void writeHtmlWrap(boolean close, int size, String displayLabel, Tr tr) throws IOException
	{
		Input button = new Input();
		
		button.setType("image");
		button.setCssClass("general");
		button.setSrc("http://ecologylab.net/cf/compositionIncludes/button.jpg");
		button.setValue("");
		
//		Td td = new Td();
		Td fieldName = new Td();
		Div text = new Div();
		
		text.setCssClass("metadata_text");
		fieldName.setCssClass("metadata_field_name");
//		td.setCssClass("nested_field_value");
		
			if (size >= 1)
				text.members.add(button);
			String s = displayLabel;
			if (size > 1)
			{
				s += " (" + Integer.toString(size) + ")";
			}
			
			text.setText(s);

			fieldName.items.add(text);
			tr.cells.add(fieldName);
	}

	public void writeCompositeHtmlWrap(boolean close, String displayLabel, String schemaItemType, Tr tr) throws IOException
	{		
//			Td td = new Td();
			Td fieldName = new Td();
			Div text = new Div();
			if (schemaItemType != null)
			{
				text.setSchemaOrgItemType(schemaItemType);
			}
			text.setCssClass("metadata_text");
			fieldName.setCssClass("metadata_field_name");
//			td.setCssClass("nested_field_value");

			text.setText(displayLabel);
			fieldName.items.add(text);
			tr.cells.add(fieldName);
	}

	// ----------------------------- methods from TagDescriptor
	// ---------------------------------------//
	/**
	 * Use a set method or the type system to set our field in the context to the value.
	 * 
	 * @param context
	 * @param value
	 * @param scalarUnmarshallingContext
	 *          TODO
	 */
	protected void setFieldToScalar(Object context, String value,
			ScalarUnmarshallingContext scalarUnmarshallingContext)
	{
		if ((value == null) /* || (value.length() == 0) removed by Alex to allow empty delims */)
		{
			// error("Can't set scalar field with empty String");
			return;
		}
		value = filterValue(value);
		if (!isCDATA)
			value = XMLTools.unescapeXML(value);
		if (setValueMethod != null)
		{
			// if the method is found, invoke the method
			// fill the String value with the value of the attr node
			// args is the array of objects containing arguments to the method to be invoked
			// in our case, methods have only one arg: the value String
			Object[] args = new Object[1];
			args[0] = value;
			try
			{
				setValueMethod.invoke(context, args); // run set method!
			}
			catch (InvocationTargetException e)
			{
				weird("couldnt run set method for " + tagName + " even though we found it");
				e.printStackTrace();
			}
			catch (IllegalAccessException e)
			{
				weird("couldnt run set method for " + tagName + " even though we found it");
				e.printStackTrace();
			}
			catch (IllegalArgumentException e)
			{
				e.printStackTrace();
			}
		}
		else if (scalarType != null && !scalarType.isMarshallOnly())
		{
			scalarType.setField(context, field, value, format, scalarUnmarshallingContext);
		}
	}

	public void setRegexFilter(Pattern regex, String replacement)
	{
		filterRegex = regex;
		filterReplace = replacement;
	}

	/**
	 * Filter value using filterRegex.
	 * 
	 * @param value
	 * @return
	 */
	String filterValue(String value)
	{
		if (filterRegex != null)
		{
			Matcher matcher = filterRegex.matcher(value);
			if (filterReplace == null)
			{
				if (matcher.find())
				{
					value = matcher.group();
				}
				else
				{
					value = "";
				}
			}
			else
			{
				value = matcher.replaceAll(filterReplace);
			}
		}
		return value;
	}

	/**
	 * Assume the first child of the leaf node is a text node. Pull the text of out that text node.
	 * Trim it, and if necessary, unescape it.
	 * 
	 * @param leafNode
	 *          The leaf node with the text element value.
	 * @return Null if there's not really any text, or the useful text from the Node, if there is
	 *         some.
	 */
	String getLeafNodeValue(Node leafNode)
	{
		String result = null;
		Node textElementChild = leafNode.getFirstChild();
		if (textElementChild != null)
		{
			if (textElementChild != null)
			{
				String textNodeValue = textElementChild.getNodeValue();
				if (textNodeValue != null)
				{
					textNodeValue = textNodeValue.trim();
					if (!isCDATA && (scalarType != null) && scalarType.needsEscaping())
						textNodeValue = XMLTools.unescapeXML(textNodeValue);
					// debug("setting special text node " +childFieldName +"="+textNodeValue);
					if (textNodeValue.length() > 0)
					{
						result = textNodeValue;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES
	 *          Contextualizing object that has the Collection slot we're adding to.
	 * @param childLeafNode
	 *          XML leafNode that has the value we need to add, after type conversion.
	 * 
	 * @throws SIMPLTranslationException
	 */
	void addLeafNodeToCollection(ElementState activeES, Node childLeafNode)
			throws SIMPLTranslationException
	{
		addLeafNodeToCollection(activeES, getLeafNodeValue(childLeafNode), null);
	}

	/**
	 * Add element derived from the Node to a Collection.
	 * 
	 * @param activeES
	 *          Contextualizing object that has the Collection slot we're adding to.
	 * @param scalarUnmarshallingContext
	 *          TODO
	 * @param childLeafNode
	 *          XML leafNode that has the value we need to add, after type conversion.
	 * @throws SIMPLTranslationException
	 */
	void addLeafNodeToCollection(ElementState activeES, String leafNodeValue,
			ScalarUnmarshallingContext scalarUnmarshallingContext) throws SIMPLTranslationException
	{
		if (leafNodeValue != null)
		{
			// silently ignore null leaf node values
		}
		if (scalarType != null)
		{
			// TODO -- for performance reasons, should we call without format if format is null, and
			// let the ScalarTypes that don't use format implement the 1 argument signature?!
			Object typeConvertedValue = scalarType.getInstance(leafNodeValue, format,
					scalarUnmarshallingContext);
			try
			{
				// TODO -- should we be doing this check for null here??
				if (typeConvertedValue != null)
				{
					// Collection collection = (Collection) field.get(activeES);
					// if (collection == null)
					// {
					// // well, why not create the collection object for them?!
					// Collection thatCollection =
					// ReflectionTools.getInstance((Class<Collection>) field.getType());
					//
					// }
					Collection<Object> collection = (Collection<Object>) automaticLazyGetCollectionOrMap(activeES);
					collection.add(typeConvertedValue);
				}
			}
			catch (Exception e)
			{
				throw fieldAccessException(typeConvertedValue, e);
			}
		}
		else
		{
			reportFieldTypeError(leafNodeValue);
		}
	}

	private void reportFieldTypeError(String textNodeValue)
	{
		error("Can't set to " + textNodeValue + " because fieldType is unknown.");
	}

	/**
	 * Generate an exception about problems accessing a field.
	 * 
	 * @param nestedElementState
	 * @param e
	 * @return
	 */
	private SIMPLTranslationException fieldAccessException(Object nestedElementState, Exception e)
	{
		return new SIMPLTranslationException("Unexpected Object / Field set problem. \n\t" + "Field = "
				+ field + "\n\ttrying to set to " + nestedElementState.getClass(), e);
	}

	/**
	 * Use the Field of this to seek a Collection or Map object in the activeES. If non-null, great --
	 * return it.
	 * <p/>
	 * Otherwise, lazy evaluation. Since the value of the field is null, use the Type of the Field to
	 * instantiate a newInstance. Set the instance of the Field in activeES to this newInstance, and
	 * return it.
	 * 
	 * @param activeES
	 * @return
	 */
	Object automaticLazyGetCollectionOrMap(ElementState activeES)
	{
		Object collection = null;
		try
		{
			collection 			= field.get(activeES);
			if (collection == null)
			{
				collection 		= collectionType.getInstance();
				field.set(activeES, collection);
			}
		}
		catch (IllegalArgumentException e)
		{
			weird("Trying to addElementToCollection(). Can't access collection field " + field.getType()
					+ " in " + activeES);
			e.printStackTrace();
			// return;
		}
		catch (IllegalAccessException e)
		{
			weird("Trying to addElementToCollection(). Can't access collection field " + field.getType()
					+ " in " + activeES);
			e.printStackTrace();
			// return;
		}
		return collection;
	}

	/**
	 * Based on the classOp in this, form a child element. Set it's parent field and elementByIdMap.
	 * Look-up Optimizations for it, using the parent's Optimizations as the scope.
	 * 
	 * @param parent
	 * @param tagName
	 *          TODO
	 * @param attributes
	 * @return
	 * @throws SIMPLTranslationException
	 */
	ElementState constructChildElementState(ElementState parent, String tagName,
			Attributes attributes, TranslationContext graphContext) throws SIMPLTranslationException
	{
		ClassDescriptor childClassDescriptor = !isPolymorphic() ? elementClassDescriptor
				: polymorphClassDescriptors.get(tagName);
		ElementState result = null;
		if (childClassDescriptor != null)
		{
			result = getInstance(attributes, childClassDescriptor, graphContext);

			if (result != null)
				result.setupInParent(parent, childClassDescriptor);
		}
		return result;
	}
	
	public ClassDescriptor getChildClassDescriptor(String tagName)
	{
		ClassDescriptor childClassDescriptor = !isPolymorphic() ? elementClassDescriptor
				: polymorphClassDescriptors.get(tagName);
		
		return childClassDescriptor;
	}

	private ElementState getInstance(Attributes attributes, ClassDescriptor childClassDescriptor,
			TranslationContext graphContext) throws SIMPLTranslationException
	{
		ElementState result;

		if (TranslationScope.graphSwitch == GRAPH_SWITCH.ON)
		{
			ElementState alreadyUnmarshalledObject = graphContext.getFromMap(attributes);

			if (alreadyUnmarshalledObject != null)
				result = alreadyUnmarshalledObject;
			else
				result = childClassDescriptor.getInstance();
		}
		else
		{
			result = childClassDescriptor.getInstance();
		}

		return result;
	}

	ElementState constructChildElementState(ElementState parent, String tagName)
			throws SIMPLTranslationException
	{
		ClassDescriptor childClassDescriptor = !isPolymorphic() ? elementClassDescriptor
				: polymorphClassDescriptors.get(tagName);
		ElementState result = null;
		if (childClassDescriptor != null)
		{
			result = getInstance(childClassDescriptor);

			if (result != null)
				result.setupInParent(parent, childClassDescriptor);
		}
		return result;
	}

	private ElementState getInstance(ClassDescriptor childClassDescriptor)
			throws SIMPLTranslationException
	{

		return childClassDescriptor.getInstance();

	}

	void setFieldToComposite(ElementState context, Object nestedObject)
			throws SIMPLTranslationException
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

	// ----------------------------- constant instances ---------------------------------------//
	FieldDescriptor(String tag)
	{
		this.tagName = tag;
		this.type = IGNORED_ELEMENT;
		this.field = null;
		this.declaringClassDescriptor = null;
	}

	static final FieldDescriptor	IGNORED_ELEMENT_FIELD_DESCRIPTOR;

	static
	{
		IGNORED_ELEMENT_FIELD_DESCRIPTOR = new FieldDescriptor("IGNORED");
	}

	// ----------------------------- convenience methods ---------------------------------------//

	public String elementName(int tlvId)
	{
		return isPolymorphic() ? elementClassDescriptor(tlvId).pseudoFieldDescriptor().getTagName()
				: isCollection() ? collectionOrMapTagName : tagName;
	}

	public String elementStart()
	{
		return isCollection() ? collectionOrMapTagName : isNested() ? compositeTagName :  tagName;
	}

	/**
	 * Most fields derive their tag from Field name for marshaling. However, some, such as those
	 * annotated with @xml_class, @xml_classes, @xml_scope, derive their tag from the class of an
	 * instance. This includes all polymorphic fields.
	 * 
	 * @return true if the tag name name is derived from the class name ( not the usual case, but
	 *         needed for polymorphism).
	 * 
	 *         else if the tag name is derived from the class name for @xml_nested or, for @xml_collection
	 *         and @xml_map), the tag name is derived from the annotation's value
	 */
	public boolean isPolymorphic()
	{
		return (polymorphClassDescriptors != null) || (unresolvedScopeAnnotation != null);
		// else return true;
		// return tagClassDescriptors != null;
	}

	public String getCollectionOrMapTagName()
	{
		return collectionOrMapTagName;
	}
	
	protected void setCollectionOrMapTagName(String collectionOrMapTagName)
	{
		this.collectionOrMapTagName = collectionOrMapTagName;
	}

	// FIXME -- these are temporary bullshit declarations which need to be turned into something real
	public boolean hasXmlText()
	{
		return false;
	}

	public boolean isXmlNsDecl()
	{
		return false;
	}

	/**
	 * Used to describe scalar types used for serializing the type system, itself. They cannot be
	 * unmarshalled in Java, only marshalled. Code may be written to access their String
	 * representations in other languages.
	 * 
	 * @return false for almost all ScalarTypes and for all element fields
	 */
	public boolean isMarshallOnly()
	{
		return scalarType != null && scalarType.isMarshallOnly();
	}

	public FieldDescriptor getWrappedFD()
	{
		return wrappedFD;
	}

	public boolean belongsTo(ClassDescriptor c)
	{
		// FIXME here should we use ClassDescriptor instead of Class? this is used by java code gen.
		return (this.getDeclaringClassDescriptor()== c);
		//return this.getDeclaringClassDescriptor().getDescribedClass() == c.getDescribedClass();
	}
	
	public ArrayList<String> otherTags()
	{
		ArrayList<String> result = this.otherTags;
		if (result == null)
		{
			result = new ArrayList<String>();
			if (this.getField() != null)
			{
				final ElementState.xml_other_tags otherTagsAnnotation = this.getField().getAnnotation(xml_other_tags.class);
		
				// commented out since getAnnotation also includes inherited annotations
				// ElementState.xml_other_tags otherTagsAnnotation =
				// thisClass.getAnnotation(ElementState.xml_other_tags.class);
				if (otherTagsAnnotation != null)
					for (String otherTag : otherTagsAnnotation.value())
						result.add(otherTag);
			}
			this.otherTags = result;
		}
		return result;
	}

	public String getObjectiveCType()
	{
		if (collectionType != null)
		{
			return collectionType.deriveObjectiveCTypeName();
		}
		else if (scalarType != null)
		{
			return scalarType.deriveObjectiveCTypeName();
		}
//		if (isCollection())
//		{
//			Class<?> type = this.field.getType();
//
//			if (ArrayList.class == type || ArrayList.class == type.getSuperclass())
//			{
//				return CrossLanguageTypeConstants.OBJC_ARRAYLIST;
//			}
//			else if (HashMap.class == type || HashMap.class == type.getSuperclass())
//			{
//				return CrossLanguageTypeConstants.OBJC_HASHMAP;
//			}
//			else if (HashMapArrayList.class == type)
//			{
//				return CrossLanguageTypeConstants.OBJC_HASHMAPARRAYLIST;
//			}
//			else if (Scope.class == type)
//			{
//				return CrossLanguageTypeConstants.OBJC_SCOPE;
//			}
//		}

		return null;
	}

	public String getCSharpType()
	{
		String result = null;

		if (collectionType != null)
		{
			result	= collectionType.deriveCSharpTypeName();
		}
		else if (scalarType != null /* && !isCollection() */)
		{
			result = scalarType.deriveCSharpTypeName();
		}
		else
		{
			Class<?> type = this.field.getType();
//			if (isCollection())
//			{
//				if (ArrayList.class == type || ArrayList.class == type.getSuperclass())
//				{
//					result = CrossLanguageTypeConstants.DOTNET_ARRAYLIST;
//				}
//				else if (HashMap.class == type || HashMap.class == type.getSuperclass())
//				{
//					result = CrossLanguageTypeConstants.DOTNET_HASHMAP;
//				}
//				else if (HashMapArrayList.class == type)
//				{
//					result = CrossLanguageTypeConstants.DOTNET_HASHMAPARRAYLIST;
//				}
//				else if (Scope.class == type)
//				{
//					result = CrossLanguageTypeConstants.DOTNET_SCOPE;
//				}
//			}
//			else
//			{
				// Simpl composite ?
				String name = type.getSimpleName();
				if (name != null && !name.contains("$")) // FIXME:Dealing with inner classes is not done yet
					result = name;
//			}
		}

		if (XMLTools.isGeneric(this.field))
		{
			result += XMLTools.getCSharpGenericParametersString(this.field);
		}

		return result;
	}
	
	public String getJavaType()
	{
		String result = null;
		
		if (collectionType != null)
		{
			result	= collectionType.getJavaTypeName();
		}
		if (scalarType != null && !isCollection())
		{
			result = scalarType.getSimpleName();
		}
		else
		{
			//Class<?> type = this.field.getType();
//			if (isCollection())
//			{
//				/*
//				if (ArrayList.class == type || ArrayList.class == type.getSuperclass())
//				{
//					result = MappingConstants.JAVA_ARRAYLIST;
//				}
//				else if (HashMap.class == type || HashMap.class == type.getSuperclass())
//				{
//					result = MappingConstants.JAVA_HASHMAP;
//				}
//				else if (HashMapArrayList.class == type)
//				{
//					result = MappingConstants.JAVA_HASHMAPARRAYLIST;
//				}
//				else if (Scope.class == type)
//				{
//					result = MappingConstants.JAVA_SCOPE;
//				}*/
//				result = fieldType;
//			}
//			else
//			{
				// Simpl composite ?
				String name = fieldType;
				if (name != null && !name.contains("$")) // FIXME:Dealing with inner classes is not done yet
					result = name;
//			}
		}

		if (this.IsGeneric())
		{
			result += getGenericParametersString();
		}

		return result;
	}

	public int getTLVId()
	{
		return elementStart().hashCode();
	}

	public void writeTLVWrap(DataOutputStream outputBuffer, ByteArrayOutputStream collectionBuffy)
			throws IOException
	{
		outputBuffer.writeInt(getWrappedTLVId());
		outputBuffer.writeInt(collectionBuffy.size());
		collectionBuffy.writeTo(outputBuffer);
	}

	public int getWrappedTLVId()
	{

		int tempTLVId = 0;

		if (tagName != null)
			tempTLVId = tagName.hashCode();

		return tempTLVId;

	}

	public void writeJSONElementStart(Appendable appendable, boolean withTag) throws IOException
	{
		if (withTag)
		{
			appendable.append('"').append(elementStart()).append('"');
			appendable.append(':');
		}
		appendable.append('{');
	}

	public void writeJSONCloseTag(Appendable appendable) throws IOException
	{
		appendable.append('}');
	}

	public void writeJSONCollectionStart(PrintStream appendable)
	{
		appendable.append('"').append(elementStart()).append('"');
		appendable.append(':');
		appendable.append('[');

	}

	public void writeJSONPolymorphicCollectionStart(PrintStream appendable)
	{
		appendable.append('"').append(tagName).append('"');
		appendable.append(':');
		appendable.append('[');

	}

	public void writeJSONCollectionClose(PrintStream appendable)
	{
		appendable.append(']');
	}

	public ClassDescriptor getDeclaringClassDescriptor()
	{
		return declaringClassDescriptor;
	}
	
	public ClassDescriptor getElementClassDescriptor()
	{
		return elementClassDescriptor;
	}
	
	/**
	 * @return the name of the field used for key in this map. this is indicated through
	 * {@code @simpl_map_key_field}, and is de/serializable.
	 */
	public String getMapKeyFieldName()
	{
		return this.mapKeyFieldName;
	}
	
	public void setElementClassDescriptor(ClassDescriptor elementClassDescriptor)
	{
		this.elementClassDescriptor = elementClassDescriptor;
		Class elementClass = elementClassDescriptor.getDescribedClass();
		if (elementClass != null)
			this.elementClass = elementClass;
	}

	public ClassDescriptor elementClassDescriptor(String tagName)
	{
		return (!isPolymorphic()) ? elementClassDescriptor : polymorphClassDescriptors.get(tagName);
	}

	public ClassDescriptor elementClassDescriptor(int tlvId)
	{
		return (!isPolymorphic()) ? elementClassDescriptor : tlvClassDescriptors.get(tlvId);
	}

	public void writeBibtexCollectionStart(PrintStream appendable)
	{
		appendable.append('\n');
		appendable.append(' ');
		appendable.append(tagName);
		appendable.append('=');
	}	
	
	/**
	 * A method to add the namespaces corresponds to the field descriptor.
	 */
	
	private void addNamespaces()
	{
		ArrayList<Class<?>> genericClasses = XMLTools.getGenericParameters(field);
		Class typeClass = field.getType();

		if (genericClasses != null)
			for (Class genericClass : genericClasses)
			{
				if (ElementState.class.isAssignableFrom(genericClass))
				{
					libraryNamespaces.put(genericClass.getPackage().getName(), genericClass.getPackage()
							.getName());					
				}
			}

		if (typeClass != null)
		{
			if (ElementState.class.isAssignableFrom(typeClass))
			{
				libraryNamespaces.put(typeClass.getPackage().getName(), typeClass.getPackage().getName());
			}
		}
	}
	
	/**
	 * method to access the namespace information related to field descriptor
	 * 
	 * @return HashMap <String, String>
	 */
	public HashMap<String, String> getNamespaces()
	{
		return libraryNamespaces;
	}

	@Override
	public String key()
	{
		return this.name;
	}
	
	public boolean IsGeneric()
	{
		return isGeneric;
	}
	
	public String getGenericParametersString()
	{
		return genericParametersString;
	}
	
	public ArrayList<Class> getDependencies()
	{
		return dependencies;
	}
	
	@Override
	protected void deserializationPostHook(TranslationContext translationContext)
	{
//		switch (type)
//		{
//		case COLLECTION_ELEMENT:
//		case COLLECTION_SCALAR:
//		case MAP_ELEMENT:
//		case MAP_SCALAR:
//			collectionType	= TypeRegistry.getCollectionTypeBySimpleName(fieldType);
//			break;
//		}
	}

	/**
	 * @return	The Java name of the ElementState subclass or ScalarType of the this, depending on whether it is composite or scalar.
	 */
	@Override
	public String getJavaTypeName()
	{
		return elementClassDescriptor != null ? elementClassDescriptor.getJavaTypeName() : scalarType.getJavaTypeName();
	}
	
	@Override
	public String getCSharpTypeName()
	{
		return elementClassDescriptor != null ? elementClassDescriptor.getCSharpTypeName() : scalarType.getCSharpTypeName();
	}
	
	@Override
	public String getObjectiveCTypeName()
	{
		return elementClassDescriptor != null ? elementClassDescriptor.getObjectiveCTypeName() : scalarType.getObjectiveCTypeName();
	}
	
	@Override
	public String getDbTypeName()
	{
		return elementClassDescriptor != null ? elementClassDescriptor.getDbTypeName() : scalarType.getDbTypeName();
	}
	
}
