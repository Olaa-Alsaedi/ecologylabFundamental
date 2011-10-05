/**
 * 
 */
package ecologylab.tests.serialization;

import ecologylab.net.ParsedURL;
import ecologylab.serialization.ClassDescriptor;
import ecologylab.serialization.ElementState;
import ecologylab.serialization.Format;
import ecologylab.serialization.SIMPLTranslationException;
import ecologylab.serialization.StringFormat;
import ecologylab.serialization.TranslationScope;
import ecologylab.serialization.annotations.simpl_composite;
import ecologylab.serialization.annotations.simpl_tag;

/**
 * @author andruid
 * 
 */
@simpl_tag("rss")
public class RssPolyWrapTest extends Base
{
	@simpl_composite
	ChannelTest														channel;

	public static final String						POLY_WRAP_TEST_TRANSLATIONS_NAME	= "poly_wrap_test_translations";

	public static final TranslationScope	POLY_WRAP_TEST_TRANSLATIONS				= TranslationScope
																																							.get(
																																									POLY_WRAP_TEST_TRANSLATIONS_NAME,
																																									Base.BASE_TRANSLATIONS,
																																									RssPolyWrapTest.class,
																																									ChannelTest.class,
																																									ItemTest.class);

	public static final ParsedURL					BUZZ_SPORTS												= ParsedURL
																																							.getAbsolute("http://buzzlog.yahoo.com/feeds/buzzsportm.xml");

	/**
	 * 
	 */
	public RssPolyWrapTest()
	{

	}

	public static void main(String[] args)
	{
		try
		{
			Object result = POLY_WRAP_TEST_TRANSLATIONS.deserialize(BUZZ_SPORTS, Format.XML);
			System.out.println('\n');
			ClassDescriptor.serialize(result, System.out, StringFormat.XML);

			System.out.println('\n');
		}
		catch (SIMPLTranslationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("\n");
	}

}