package ecologylab.xml.library.rss;

import ecologylab.xml.xml_inherit;
import ecologylab.xml.types.element.ArrayListState;

/**
 * RSS parser <code>channel</code> element {@link ecologylab.xml.ElementState ElementState} declaration.
 * Used with most RSS versions.
 *
 * @author andruid
 */
public @xml_inherit class Channel extends ArrayListState<Item>
{
   @xml_leaf	String			title;
   @xml_leaf	String			description;
   
   /**
    * @return Returns the description.
    */
   public String getDescription()
   {
	   return description;
   }
   /**
    * @param description The description to set.
    */
   public void setDescription(String description)
   {
	   this.description = description;
   }
   /**
    * @return Returns the title.
    */
   public String getTitle()
   {
	   return title;
   }
   /**
    * @param title The title to set.
    */
   public void setTitle(String title)
   {
	   this.title = title;
   }
}