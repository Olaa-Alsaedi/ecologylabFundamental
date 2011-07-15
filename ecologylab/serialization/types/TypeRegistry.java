package ecologylab.serialization.types;

import java.lang.reflect.Field;
import java.util.HashMap;

import ecologylab.collections.Scope;
import ecologylab.generic.Debug;
import ecologylab.generic.ReflectionTools;
import ecologylab.serialization.XMLTools;
import ecologylab.serialization.types.scalar.BooleanType;
import ecologylab.serialization.types.scalar.ByteType;
import ecologylab.serialization.types.scalar.CharType;
import ecologylab.serialization.types.scalar.ClassType;
import ecologylab.serialization.types.scalar.ColorType;
import ecologylab.serialization.types.scalar.CompositeAsScalarType;
import ecologylab.serialization.types.scalar.DateType;
import ecologylab.serialization.types.scalar.DoubleType;
import ecologylab.serialization.types.scalar.EnumeratedType;
import ecologylab.serialization.types.scalar.FieldType;
import ecologylab.serialization.types.scalar.FileType;
import ecologylab.serialization.types.scalar.FloatType;
import ecologylab.serialization.types.scalar.IntType;
import ecologylab.serialization.types.scalar.LongType;
import ecologylab.serialization.types.scalar.ParsedURLType;
import ecologylab.serialization.types.scalar.PatternType;
import ecologylab.serialization.types.scalar.RectangleType;
import ecologylab.serialization.types.scalar.ReferenceBooleanType;
import ecologylab.serialization.types.scalar.ReferenceDoubleType;
import ecologylab.serialization.types.scalar.ReferenceFloatType;
import ecologylab.serialization.types.scalar.ReferenceIntegerType;
import ecologylab.serialization.types.scalar.ReferenceLongType;
import ecologylab.serialization.types.scalar.ScalarTypeType;
import ecologylab.serialization.types.scalar.ShortType;
import ecologylab.serialization.types.scalar.StringBuilderType;
import ecologylab.serialization.types.scalar.StringType;
import ecologylab.serialization.types.scalar.URLType;
import ecologylab.serialization.types.scalar.UUIDType;

/**
 * This class implements registries of instances of ScalarType and CollectionType. 
 * <p/>
 * Thus, for example, the key for the type translated by IntType is "int", not "IntType". 
 * (But for the type translated by IntegerType, it is Integer :-)
 * It must be
 * this way, because automatic translation is performed based on Field declarations, and the Field
 * declarations do not know about these Types, only about the underlying Java types.
 */
public class TypeRegistry extends Debug
implements CrossLanguageTypeConstants
{
	private static TypeRegistry	singleton;
	
	/**
	 * Maps Strings that represent classes to integers. These integers, some of which are defined in
	 * the interface {@link BuiltinTypeIndices Types}. Type to integer mappings that are defined in
	 * this class use positive integers. All other extended types should use negative integers.
	 */
	private final HashMap<String, ScalarType>	allScalarTypes		= new HashMap<String, ScalarType>(32);

	private final  HashMap<String, CollectionType>	collectionTypesByName 			= new HashMap<String, CollectionType>();
	
	private final  HashMap<String, CollectionType>	collectionTypesByClassName	= new HashMap<String, CollectionType>();
	

	static Class[]																		BASIC_SCALAR_TYPES	=
	{ StringType.class,
		StringBuilderType.class, IntType.class, ReferenceIntegerType.class, BooleanType.class,
		ReferenceBooleanType.class, FloatType.class, ReferenceFloatType.class, DoubleType.class,
		ReferenceDoubleType.class, LongType.class, ReferenceLongType.class, ShortType.class,
		ByteType.class, CharType.class, ColorType.class, URLType.class, ParsedURLType.class,
		FileType.class, UUIDType.class, RectangleType.class,

		DateType.class,

		ScalarTypeType.class,

		PatternType.class, EnumeratedType.class,

		ClassType.class, FieldType.class,											

		//This scalar type is not used anywhere as of now. see class commemts.
		CompositeAsScalarType.class
	};

	static
	{
		init();
	}
	private static boolean init;
	
	/**
	 * 
	 */
	public static void init()
	{
		if (!init)
		{
			init	= true;
			registerScalarType(BASIC_SCALAR_TYPES);
			new FundamentalTypes();
		}
	}

	public TypeRegistry()
	{
		
	}
	/**
	 * Enter this type in the registry, which is a map in which the Type's Class object's fully
	 * qualified named is used as a key.
	 */
	public static boolean registerScalarType(Class<? extends ScalarType> typeClass)
	{
		ScalarType type = (ScalarType) ReflectionTools.getInstance(typeClass);
		if (type == null)
		{
			error(typeClass, "Can't register this Type, because we can't instantiate it!");
			return false;
		}
		return registerScalarType(type);
	}

	/**
	 * Enter this type in the registry, which is a map in which the Type's Class object's fully
	 * qualified named is used as a key.
	 */
	static boolean registerScalarType(ScalarType type)
	{
		String typeName = type.getTypeClass().getName();
		String simpleName = type.getClass().getSimpleName();
		singleton().registerScalarType(simpleName, type);
		return singleton().registerScalarType(typeName, type);
	}

	private static TypeRegistry singleton()
	{
		TypeRegistry result	= singleton;
		if (result == null)
		{
			synchronized (TypeRegistry.class)
			{
				result			= singleton;
				if (result == null)
				{
					result		= new TypeRegistry();
					singleton	= result;
				}
			}
		}
		return result;
	}
	private boolean registerScalarType(String typeName, ScalarType type)
	{
		boolean definingNewType;

		synchronized (allScalarTypes)
		{
			ScalarType previous	= allScalarTypes.put(typeName, type);
			definingNewType 		= previous != null && !previous.equals(type);
			if (definingNewType)
			{
				Debug.warning(TypeRegistry.class, "register(): Redefining scalar type: " + typeName);
			}
		}
		return definingNewType;
	}

	/**
	 * Register a batch of Types.
	 * 
	 * @param thoseTypeClasses
	 */
	public static void registerScalarType(Class<? extends ScalarType> thoseTypeClasses[])
	{
		int size = thoseTypeClasses.length;
		for (int i = 0; i < size; i++)
			registerScalarType(thoseTypeClasses[i]);
	}

	/**
	 * Get the Type corresponding to the Field, by using the Field's Class.
	 * 
	 * @param field
	 * @return Type associated with the class of the specified Field
	 */
	public static ScalarType getScalarType(Field field)
	{
		return getScalarType(field.getType(), field);
	}

	/**
	 * Get the Scalar Type corresponding to the Class, by using its name.
	 * 
	 * @param thatClass
	 * @return Type associated with thatClass
	 */
	public static <U> ScalarType<U> getScalarType(Class<U> thatClass)
	{
		return getScalarType(thatClass, null);
	}

	public static <U> ScalarType<U> getScalarType(Class<U> thatClass, Field field)
	{
		if (field == null)
		{
			return (XMLTools.isEnum(thatClass)) ? getScalarType(Enum.class.getName()) : getScalarType(thatClass
					.getName());
		}
		else if (XMLTools.isComposite(thatClass))
		{
			return getScalarType(CompositeAsScalarType.class.getName());
		}
		else
		{	
			return (XMLTools.isEnum(thatClass)) ? getScalarType(Enum.class.getName()) : getScalarType(thatClass
					.getName());
		}
	}

	/**
	 * Get the Scalar Type corresponding to the Class name.
	 * 
	 * @param className
	 * @return Type associated with class of the specified name
	 */
	public static final ScalarType getScalarType(String className)
	{
		return singleton().allScalarTypes.get(className);
	}

	/**
	 * Check to see if we have a Type corresponding to the Class, by using its name.
	 * 
	 * @param thatClass
	 * @return true if thatClass is in this TypeRegistry
	 */
	public static boolean containsScalarType(Class thatClass)
	{
		return containsScalarType(thatClass.getName());
	}

	/**
	 * Check to see if we have a Type corresponding to the Class name.
	 * 
	 * @param className
	 * @return true if a class with this name is in this TypeRegistry
	 */
	public static boolean containsScalarType(String className)
	{
		return singleton().allScalarTypes.containsKey(className);
	}
	
	/**
	 * This method is only called by the constructor of CollectionType.
	 * 
	 * @param collectionType
	 */
	static void registerCollectionType(CollectionType collectionType)
	{
		TypeRegistry registrySingleton = singleton();
		registrySingleton.collectionTypesByName.put(collectionType.getName(), collectionType);
		registrySingleton.collectionTypesByClassName.put(collectionType.getJavaTypeName(), collectionType);
	}

	/**
	 * Get by unique, cross-platform name.
	 * 
	 * @param crossPlatformName
	 * @return
	 */
	public static CollectionType getCollectionTypeByCrossPlatformName(String crossPlatformName)
	{
		return singleton().collectionTypesByName.get(crossPlatformName);
	}

	/**
	 * Lookup a collection type using the Java class or its full unqualifiedName.
	 * 
	 * @param javaField	Declaring class of this field is key for lookup
	 * 
	 * @return
	 */
	public static CollectionType getCollectionType(Field javaField)
	{
		return getCollectionType(javaField.getType());
	}

	/**
	 * Lookup a collection type using the Java class or its full unqualifiedName.
	 * If it does not exist, construct a new CollectionType, but with no capabilities for Cross-Language Code Generation.
	 * 
	 * @param javaClass
	 * @return
	 */
	public static CollectionType getCollectionType(Class javaClass)
	{
		CollectionType result = getCollectionType(javaClass.getName());
		if (result == null)
		{
			String simplName			= SIMPL_COLLECTION_TYPES_PREFIX + javaClass.getSimpleName();
			singleton().warning("No CollectionType was pre-defined for " + simplName + ", so constructing one on the fly.\nCross-language code for fields defined with this type cannot be generated.");
			result							= new CollectionType(javaClass, null, null);
		}
		return result;
	}
	/**
	 * Lookup a collection type using the Java class or its full unqualifiedName.
	 * 
	 * @param javaClassName
	 * @return
	 */
	public static CollectionType getCollectionType(String javaClassName)
	{
		return singleton().collectionTypesByClassName.get(javaClassName);
	}

	public static TypeRegistry typeRegistry()
	{
		return singleton;
	}
}