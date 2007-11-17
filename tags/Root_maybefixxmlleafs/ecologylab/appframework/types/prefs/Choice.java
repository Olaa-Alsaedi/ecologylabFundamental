package ecologylab.appframework.types.prefs;

import ecologylab.xml.ElementState;

/**
 * Multi-choice option for a preference.
 * This should be used for options that have
 * non-standard text associated with them.
 * (Standard text would be "Yes"/"No" for Booleans.)
 */
abstract public class Choice<T> extends ElementState
{
    /**
     * The name for a choice
     */
    @xml_attribute String      name;
    /**
     * The label text for a choice
     */
    @xml_attribute String      label;
    //@xml_attribute T           value;

    public Choice()
    {
        super();
    }
    
    /**
     * Get the value of a choice. Type-specific.
     */
    public abstract T getValue();
    
    /**
     * Set the value of a choice. Type-specific.
     */
    public abstract void setValue(T newValue);
    
    /**
     * Get the name of a choice
     * 
     * @return Name of a choice
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Get the label of a choice
     * 
     * @return Label of a choice
     */
    public String getLabel()
    {
        return this.label;
    }
}