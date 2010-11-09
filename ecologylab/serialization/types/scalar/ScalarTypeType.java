/**
 * 
 */
package ecologylab.serialization.types.scalar;

import ecologylab.serialization.ScalarUnmarshallingContext;
import ecologylab.serialization.simpl_inherit;

/**
 * For storing actual ScalarType values as meta-metadata.
 * 
 * @author andruid
 *
 */
public class ScalarTypeType extends ReferenceType<ScalarType>
{
	/**
	 * @param thatClass
	 */
	public ScalarTypeType()
	{
		super(ScalarType.class);
	}

	/**
	 * Capitalize the value if  it wasn't.
	 * Append "Type".
	 * Use this to call TypeRegistry.getType().
	 */
	@Override
	public ScalarType getInstance(String value, String[] formatStrings, ScalarUnmarshallingContext scalarUnmarshallingContext)
	{
		ScalarType result	= null;
		int length			= value.length();
		if ((value != null) && (length > 0))
		{
			char firstChar	= value.charAt(0);
			StringBuilder buffy	= new StringBuilder(length + 4);	// includes room for "Type"
			if (Character.isLowerCase(firstChar))
			{
				buffy.append(Character.toUpperCase(firstChar));
				if (length > 1)
					buffy.append(value, 1, length - 1);
			}
			else
				buffy.append(value);
			buffy.append("Type");
			
			result	= TypeRegistry.getType(buffy.toString());
		}
		return result;			
	}

	@Override
	public String getCSharptType()
	{
		return MappingConstants.DOTNET_SCALAR_TYPE;
	}

	@Override
	public String getDbType()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getObjectiveCType()
	{
		return MappingConstants.OBJC_SCALAR_TYPE;
	}

}
