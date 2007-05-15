/*
 * Created on Dec 31, 2004 at the Interface Ecology Lab.
 */
package ecologylab.xml.types.scalar;

import java.lang.reflect.Field;

/**
 * Type system entry for int, a built-in primitive.
 * 
 * @author andruid
 */
public class IntType extends ScalarType<Integer>
{
/**
 * This constructor should only be called once per session, through
 * a static initializer, typically in TypeRegistry.
 * <p>
 * To get the instance of this type object for use in translations, call
 * <code>TypeRegistry.get("int")</code>.
 * 
 */
	public IntType()
	{
		super(int.class);
	}

	/**
	 * Convert the parameter to int.
	 */
	public int getValue(String valueString)
	{
		return Integer.parseInt(valueString);
	}
	
    /**
     * If <code>this</code> is a reference type, build an appropriate Object, given a String
     * representation. If it is a primitive type, return a boxed value.
     * 
     * @param value
     *            String representation of the instance.
     */
    public Integer getInstance(String value)
    {
        return new Integer(getValue(value));
    }

	/**
	 * This is a primitive type, so we set it specially.
	 * 
	 * @see ecologylab.xml.types.scalar.ScalarType#setField(Object, Field, String)
	 */
	public boolean setField(Object object, Field field, String value) 
	{
		boolean result	= false;
		int converted	= Integer.MIN_VALUE;
		try
		{
		   converted	= getValue(value);
		   field.setInt(object, converted);
		   result		= true;
		} catch (Exception e)
		{
		   error("Got " + e + " while setting field " +
				 field + " to " + value+"->"+converted + " in " + object);
		}
		return result;
	}
/**
 * The string representation for a Field of this type
 */
	public String toString(Object object, Field field)
	{
	   String result	= "COULDN'T CONVERT!";
	   try
	   {
		  result		= Integer.toString(field.getInt(object));
	   } catch (Exception e)
	   {
		  e.printStackTrace();
	   }
	   return result;
	}

/**
 * The default value for this type, as a String.
 * This value is the one that translateToXML(...) wont bother emitting.
 * 
 * In this case, "0".
 */
	public String defaultValue()
	{
	   return "0";
	}
}
