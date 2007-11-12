/**
 * 
 */
package ecologylab.xml.library.icdl;

import ecologylab.xml.xml_inherit;
import ecologylab.xml.types.element.ArrayListState;

/**
 * The root element in a reply to ICDL BookXMLResults.
 * 
 * http://www.childrenslibrary.org/icdl/BookXMLResults?ids=133,265&viewids=&prefcollids=474&langid=&text=&lang=English&match=all&sort=title&pnum=1&pgct=8&ptype=simple
 * can be reduced to
 * http://www.childrenslibrary.org/icdl/BookXMLResults?ids=133,265&prefcollids=474&lang=English&sort=title&ptype=simple
 * @author andruid
 */
@xml_inherit
public class Response extends ArrayListState<Book>
{
	@xml_leaf	int		pnum;
	@xml_leaf	int		total;
	
	/**
	 * 
	 */
	public Response()
	{
		super();

	}

}
