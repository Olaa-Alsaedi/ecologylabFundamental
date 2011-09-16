/**
 * 
 */
package ecologylab.appframework.types.prefs;

import ecologylab.generic.Debug;
import ecologylab.serialization.TranslationScope;

/**
 * @author andrew
 *
 */
public class PrefOpTranslations extends Debug
{
	public static final String 	SCOPE_NAME 		= "pref_op_translations";
	
	public static final Class[] TRANSLATIONS 	= 
	{
		PrefOp.class,
		PrefDelayedOp.class,
	};
	
	/**
	 * Do not use this accessor.
	 */
	private PrefOpTranslations()
	{
	}
	
	public static TranslationScope get(TranslationScope inheritedScope)
	{
		return TranslationScope.get(SCOPE_NAME, inheritedScope, TRANSLATIONS);
	}
}
