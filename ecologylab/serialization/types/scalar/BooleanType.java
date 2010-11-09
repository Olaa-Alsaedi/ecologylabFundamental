/*
 * Created on Dec 31, 2004 at the Interface Ecology Lab.
 */
package ecologylab.serialization.types.scalar;

import java.io.IOException;
import java.lang.reflect.Field;

import ecologylab.serialization.FieldDescriptor;
import ecologylab.serialization.ScalarUnmarshallingContext;

/**
 * Type system entry for boolean, a built-in primitive.
 * 
 * @author andruid
 */
public class BooleanType extends ScalarType<Boolean>
{
	public static final boolean	DEFAULT_VALUE			= false;

	public static final String		DEFAULT_VALUE_STRING	= "false";

/**
 * This constructor should only be called once per session, through
 * a static initializer, typically in TypeRegistry.
 * <p>
 * To get the instance of this type object for use in translations, call
 * <code>TypeRegistry.get("boolean")</code>.
 * 
 */
	public BooleanType()
	{
		super(boolean.class);
	}
	
	public BooleanType(Class<Boolean> thatClass) 
    {
		super(thatClass);
	}

	/**
	 * Convert the parameter to boolean.
	 */
	public static boolean getValue(String valueString)
	{
	   String lcValue= valueString.toLowerCase();
	   return lcValue.equals("t") || lcValue.equals("true") ||
		  lcValue.equals("yes") || (lcValue.equals("1"));
	}
	
    /**
     * If <code>this</code> is a reference type, build an appropriate Object, given a String
     * representation. If it is a primitive type, return a boxed value.
     * 
     * @param value
     *            String representation of the instance.
     */
    @Override public Boolean getInstance(String value, String[] formatStrings, ScalarUnmarshallingContext scalarUnmarshallingContext)
    {
        return new Boolean(getValue(value));
    }

	/**
	 * This is a primitive type, so we set it specially.
	 * 
	 * @see ecologylab.serialization.types.scalar.ScalarType#setField(java.lang.Object, java.lang.reflect.Field, java.lang.String)
	 */
	@Override public boolean setField(Object object, Field field, String value) 
	{
		boolean result	= false;
		try
		{
			field.setBoolean(object, getValue(value));
			result		= true;
		} catch (Exception e)
		{
            setFieldError(field, value, e);
		}
		return result;
	}
/**
 * The string representation for a Field of this type
 */
	@Override
	public String toString(Field field, Object object)
	{
	   String result	= "COULDN'T CONVERT!";
	   try
	   {
		  result		= Boolean.toString(field.getBoolean(object));
	   } 
       catch (Exception e)
	   {
		  e.printStackTrace();
	   }
	   return result;
	}

    /**
     * Get the value from the Field, in the context.
     * Append its value to the buffy.
     * 
     * @param buffy
     * @param field
     * @param context
     * @throws IllegalAccessException 
     * @throws IllegalArgumentException 
     */
    @Override
    public void appendValue(StringBuilder buffy, FieldDescriptor f2xo, Object context) 
    throws IllegalArgumentException, IllegalAccessException
    {
        buffy.append(getValueToAppend(f2xo, context));
    }
    static final String TRUE	= "true";
    static final String FALSE	= "false";
    static final String SHORT_TRUE = "t";
    static final String SHORT_FALSE = "f";
    
    /**
     * Get the value from the Field, in the context.
     * Append its value to the buffy.
     * 
     * @param buffy
     * @param field
     * @param context
     * @throws IllegalAccessException 
     * @throws IllegalArgumentException 
     */
    @Override
    public void appendValue(Appendable buffy, FieldDescriptor fieldDescriptor, Object context) 
    throws IllegalArgumentException, IllegalAccessException, IOException
    {
        buffy.append(getValueToAppend(fieldDescriptor, context));
    }
    
    public static String getValueToAppend(FieldDescriptor descriptor, Object context) 
    throws IllegalArgumentException, IllegalAccessException
    {
    	boolean value = (Boolean) descriptor.getField().get(context);
        
        if(descriptor.getFormat() != null)
        {
      	  return (value ? SHORT_TRUE : SHORT_FALSE);
        }
        else
        {
      	  return (value ? TRUE : FALSE);
        }
    }
    

/**
 * The default value for this type, as a String.
 * This value is the one that translateToXML(...) wont bother emitting.
 * 
 * @return		"false"
 */
	protected String defaultValueString()
	{
	   return DEFAULT_VALUE_STRING;
	}
	
	/**
	 * True if the value in the Field object matches the default value for this type.
	 * 
	 * @param field
	 * @return
	 */
	@Override
	public boolean isDefaultValue(Field field, Object context) 
    throws IllegalArgumentException, IllegalAccessException
    {
    	return (Boolean) field.get(context) == DEFAULT_VALUE;
    }

	@Override
	public String getObjectiveCType()
	{
		return MappingConstants.OBJC_BOOLEAN;
	}

	@Override
	public String getCSharptType()
	{
		return MappingConstants.DOTNET_BOOLEAN;
	}

	@Override
	public String getDbType()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
