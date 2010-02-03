/**
 * 
 */
package ecologylab.xml.types.scalar;

import java.util.regex.Pattern;

import ecologylab.xml.ScalarUnmarshallingContext;

/**
 * @author andruid
 *
 */
public class PatternType extends ReferenceType<Pattern>
{

	public PatternType()
	{
		super(Pattern.class);
	}

	@Override
	public Pattern getInstance(String value, String[] formatStrings,
			ScalarUnmarshallingContext scalarUnmarshallingContext)
	{
		// TODO Auto-generated method stub
		return (formatStrings == null) ? Pattern.compile(value) : Pattern.compile(value, Integer.parseInt(formatStrings[0]));
	}

}
