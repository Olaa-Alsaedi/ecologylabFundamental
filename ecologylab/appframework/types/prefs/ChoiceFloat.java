package ecologylab.appframework.types.prefs;

import ecologylab.serialization.ElementState;
import ecologylab.serialization.ElementState.simpl_scalar;
import ecologylab.serialization.annotations.simpl_inherit;

/**
 * A Float Choice object, for a multi-choice preference.
 * @author awebb
 *
 */
@simpl_inherit
public class ChoiceFloat extends Choice<Float>
{
    /**
     * Value of the choice
     */
    @simpl_scalar float      value;

    public ChoiceFloat()
    {
        super();
    }
    
    /**
     * Get the value of a choice
     * 
     * @return value    Value of the choice
     */
    @Override public void setValue(Float newValue)
    {
        this.value = newValue;
    }
    
    /**
     * Set the value of a choice.
     * 
     * @param newValue  New value the choice will be set to.
     */
    @Override public Float getValue()
    {
        return this.value;
    }
}
