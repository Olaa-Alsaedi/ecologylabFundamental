package ecologylab.appframework.types.prefs;

import ecologylab.xml.ElementState;

abstract public class RangeState<T> extends ElementState
{

	public RangeState()
	{
		super();
	}

	abstract protected boolean isWithinRange(T newValue);
}
