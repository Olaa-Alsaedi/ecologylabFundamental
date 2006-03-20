package ecologylab.generic;


/**
 * Reusable String constants for getting properties from the environment.
 * 
 * @author andruid
 * @author blake
 */
public interface ApplicationProperties
{
	public static final String	USERINTERFACE_NAME		= "userinterface";
	
	public static final String	USERINTERFACE			= Generic.parameter(USERINTERFACE_NAME);
	
	public static final String	USE_ASSETS_CACHE_NAME	= "use_assets_cache";
	
	public static final boolean	USE_ASSETS_CACHE		= Generic.parameterBool(USE_ASSETS_CACHE_NAME);
	
	public static final String	CODEBASE				= "codebase";
}
