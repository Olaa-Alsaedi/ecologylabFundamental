/**
 * 
 */
package ecologylab.services.messages.cf;

import ecologylab.generic.Debug;
import ecologylab.services.messages.*;
import ecologylab.xml.TranslationSpace;

/**
 * TranslationSpace for client-side CFServices.
 * 
 * @author andruid
 */
public class CFMessagesTranslations extends Debug
{
	public static final String	NAME			= "messages.cf";
	
	public static final String	PACKAGE_NAME	= "ecologylab.services.messages.cf";
	
	public static final Class	TRANSLATIONS[]	= 
	{ 
		RequestMessage.class,
		ResponseMessage.class,
		StopMessage.class,
		OkResponse.class,
		BadSemanticContentResponse.class,
		ErrorResponse.class,

		SeedCf.class,

		SeedSet.class,
		Seed.class,
		DocumentState.class,
		SearchState.class,
				
	};

	/**
	 * 
	 */
	public CFMessagesTranslations()
	{
		super();

	}

	/**
	 * This accessor will work from anywhere, in any order, and stay efficient.
	 * @return	TranslationSpace for cF services.
	 */
	public static TranslationSpace get()
	{
		return TranslationSpace.get(NAME, PACKAGE_NAME, TRANSLATIONS);
	}
}