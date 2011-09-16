/**
 * 
 */
package ecologylab.standalone.xmlpolymorph;

import java.util.ArrayList;

import ecologylab.serialization.ElementState;
import ecologylab.serialization.SIMPLTranslationException;
import ecologylab.serialization.TranslationScope;
import ecologylab.serialization.library.rss.Item;
import ecologylab.serialization.library.rss.RssTranslations;

/**
 * @author andruid
 *
 */
public class Schmannel extends ElementState
{
  private static final TranslationScope	
  TRANSLATION_SPACE	= TranslationScope.get("schm_rss", RssTranslations.get(), Schmannel.class, SchmItem.class, BItem.class);

  @simpl_classes({Item.class, SchmItem.class})
  @simpl_collection ArrayList<Item> schmItems;

  @simpl_classes({BItem.class})
  @simpl_composite 							Item 						polyItem;
  
	/**
	 * 
	 */
	public Schmannel()
	{
		super();
	}
	public void polyAdd(Item item)
	{
		if (schmItems == null)
			schmItems	= new ArrayList<Item>();
		schmItems.add(item);
	}

	public static final String WRAP_OUT = "<schmannel><schm_items><item></item></schm_items></schmannel>"; // "<channel><items></items></channel>";
	public static final String ITEMS = "<schmannel><schm_items><item><title>it is called rogue!</title><description>its a game</description><link>http://ecologylab.cs.tamu.edu/rogue/</link><author>zach</author></item><item><title>it is called cf!</title><description>its a creativity support tool</description><author>andruid</author></item></schm_items></schmannel>";
	public static void main(String[] s)
	{
		Item item	= new Item("t2ec");
		Item schmItem	= new SchmItem("cf");
		Item nested	= new BItem("nested");
		
		Schmannel schmannel	= new Schmannel();
		
		schmannel.polyItem	= nested;
		
		schmannel.polyAdd(item);
		schmannel.polyAdd(schmItem);
		
		try
		{
			StringBuilder buffy	= new StringBuilder();
			schmannel.serialize(buffy);
			
			System.out.println(buffy);
			System.out.println('\n');
			TRANSLATION_SPACE.serialize(System.out);
			System.out.println('\n');
			ElementState s2	= TRANSLATION_SPACE.deserializeCharSequence(buffy);
			s2.serialize(System.out);
		}
		catch (SIMPLTranslationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		testTranslateFrom();

	}
	protected static void testTranslateFrom()
	{
		try
		{
			ElementState rap	= TRANSLATION_SPACE.deserializeCharSequence(ITEMS);
			rap.serialize(System.out);
			System.out.println('\n');
//			println(c.translateToXML());
		} catch (SIMPLTranslationException e)
		{
			e.printStackTrace();
		}
	}

}
