/**
 * 
 */
package ecologylab.xml.types.scalar;

import java.io.IOException;

import ecologylab.xml.XMLTools;

/**
 *
 * @author andruid
 */
abstract public class ReferenceType<T> extends ScalarType<T>
{

	/**
	 * @param thatClass
	 */
	public ReferenceType(Class<T> thatClass)
	{
		super(thatClass);
	}

	/**
	 * Append the String directly, unless it needs escaping, in which case, call escapeXML.
	 * 
	 * @param instance
	 * @param buffy
	 * @param needsEscaping
	 */
	@Override 
	protected void appendValue(T instance, StringBuilder buffy, boolean needsEscaping)
    {
		String instanceString	= instance.toString();
		if (needsEscaping)
			XMLTools.escapeXML(buffy, instanceString);
		else
			buffy.append(instanceString);
    }
    protected void appendValue(T instance, Appendable buffy, boolean needsEscaping)
    throws IOException
    {
		String instanceString	= instance.toString();
		if (needsEscaping)
			XMLTools.escapeXML(buffy, instanceString);
		else
			buffy.append(instanceString);
    }
	
}
