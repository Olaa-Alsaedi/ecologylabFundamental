/*
 * Created on Dec 31, 2004 at the Interface Ecology Lab.
 */
package ecologylab.generic;

import ecologylab.types.Type;

/**
 * Type system entry for java.awt.Color. Uses a hex string as initialization.
 * 
 * @author andruid
 */
public class ParsedURLType extends Type
{
/**
 * This constructor should only be called once per session, through
 * a static initializer, typically in TypeRegistry.
 * <p>
 * To get the instance of this type object for use in translations, call
 * <code>TypeRegistry.get("cm.generic.ParsedURL")</code>.
 * 
 */
	public ParsedURLType()
	{
		super("ecologylab.generic.ParsedURL", /*TYPE_PARSED_URL, */ false);
	}

	/**
	 * @param value is interpreted as hex-encoded RGB value, in the
	 * same style as HTML & CSS. A # character at the start is unneccesary,
	 * but acceptable.
	 * 
	 * @see ecologylab.types.Type#getInstance(java.lang.String)
	 */
	public Object getInstance(String value)
	{
	   return ParsedURL.getAbsolute(value, " getInstance()");
	}
}
