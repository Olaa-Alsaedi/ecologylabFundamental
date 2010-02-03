package ecologylab.services.distributed.common;

import ecologylab.services.messages.DefaultServicesTranslations;
import ecologylab.xml.TranslationScope;

/**
 * Constants that define general ecologylab objects that get stored in the
 * Session ObjectRegistry.
 * 
 * @author andruid
 * 
 */
public interface SessionObjects
{
    public static final String           MAIN_START_AND_STOPPABLE      = "main_start_and_stoppable";

    public static final String           MAIN_SHUTDOWNABLE             = "main_shutdownable";

    public static final TranslationScope BROWSER_SERVICES_TRANSLATIONS = DefaultServicesTranslations.get();
    	/*TranslationScope
                                                                               .get(
                                                                                       "Browse",
                                                                                       "ecologylab.services.messages"); */

    public static final String           BROWSER_SERVICES_CLIENT       = "browser_services_client";

    public static final String           LOGGING                       = "logging";

    public static final String           TOP_LEVEL                     = "top_level";

    public static final String           NAMED_STYLES_MAP              = "named_styles_map";
    
    public static final String			INTEREST_MODEL_SOURCE 			= "interest_model_source";
    
    public static final String					GRAPHICS_CONFIGURATION	= "graphics_configuration";
}
