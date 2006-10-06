package ecologylab.types;

import java.lang.reflect.Field;
import java.util.HashMap;

import ecologylab.xml.IO;

/**
 * This class implements a registry of instances of Type.
 */
public class TypeRegistry extends IO
{
/**
 * Maps Strings that represent classes to integers.
 * These integers, some of which are defined in the interface 
 * {@link BuiltinTypeIndices Types}. Type to integer mappings that are defined in
 * this class use positive integers. All other extended types should
 * use negative integers.
 */	
	private static final HashMap allTypes	  = new HashMap(32);
	
	static
	{
	   new StringType();
	   new IntType();
	   new BooleanType();
	   new FloatType();
	   new DoubleType();
	   new LongType();
	   new ShortType();
	   new ByteType();
	   new CharType();
	   new ColorType();
	   new URLType();
	   new ParsedURLType();

	   new DateType();
//	   new CurrencyType();
	}
	
/**
 * Enter this type in the registry, which is a map in which the Type's className is used as a key.
 */
	static boolean register(Type type)
	{ 
		boolean result;
		String typeName	= type.getClassName();
		
		synchronized (allTypes)
		{
			result	= !allTypes.containsKey(typeName);
			if (result)
				allTypes.put(typeName, type);
			else
				println("TypeRegistry.register() ERROR! Cant redefine int mapping for "+
						  typeName);
		}
		return result;
	}
/**
 * Get the Type corresponding to the Field, by using the Field's Class.
 * @param field
 * @return
 */
	public static Type getType(Field field)
	{
	   return getType(field.getType());
	}
	/**
	 * Get the Type corresponding to the Class, by using its name.
	 * @param thatClass
	 * @return
	 */
	public static Type getType(Class thatClass)
	{
	   return getType(thatClass.getName());
	}
	/**
	 * Get the Type corresponding to the Class name.
	 * @param className
	 * @return
	 */
	public static final Type getType(String className)
	{
	   return (Type) allTypes.get(className);
	}
	/**
	 * Check to see if we have a Type corresponding to the Class, by using its name.
	 * @param thatClass
	 * @return
	 */
	public static boolean contains(Class thatClass)
	{
	   return contains(thatClass.getName());
	}
	/**
	 * Check to see if we have a Type corresponding to the Class name.
	 * @param thatClass
	 * @return
	 */
	public static boolean contains(String className)
	{
	   return allTypes.containsKey(className);
	}
	/**
	 * Set the Field to a value, converted using the Field's Type.
	 * 
	 * @param that
	 * @param field
	 * @param fieldValue
	 * @return
	 */
	public static boolean setField(Object that, Field field, String fieldValue)
	{
		boolean result		= false;
		Type fieldType		= getType(field);
		if (fieldType != null)
			result			= fieldType.setField(that, field, fieldValue);
		else
			println("TypeRegistry: Can't set type for " + field + " with value=" + fieldValue+
					", in "+ that);
		return result;
	}
	
}
