/**
 * 
 */
package ecologylab.appframework.types.prefs;

import ecologylab.appframework.types.prefs.MetaPref;
import ecologylab.xml.xml_inherit;

/**
 * Metadata about a Float Preference.
 * Defines information to enable editing the Preference.
 * 
 * @author andruid
 *
 */

@xml_inherit
public class MetaPrefFloat extends MetaPref<Float>
{
    /**
     * Default value for this MetaPref
     */
	@xml_attribute	float		defaultValue;
	
    /**
     * Instantiate.
     */
	public MetaPrefFloat()
	{
		super();
	}
	
    /**
     * Gets the default value of a MetaPref. 
     * 
     * @return Default value of MetaPref
     */
	public Float getDefaultValue()
	{
		return defaultValue;
	}

    /**
     * Construct a new instance of the Pref that matches this.
     * Use this to fill-in the default value.
     * 
     * @return
     */
	protected @Override Pref<Float> getPrefInstance()
	{
		return new PrefFloat();
	}
	
/*
	public boolean isWithinRange(Float newValue)
	{
		return (range == null) ? true :  range.isWithinRange(newValue);
	}
	*/
}