/**
 * 
 */
package ecologylab.tests;

import java.util.ArrayList;

import ecologylab.net.ParsedURL;
import ecologylab.xml.ElementState;
import ecologylab.xml.TranslationScope;
import ecologylab.xml.XMLTranslationException;

/**
 *
 * @author andruid
 */
public class TestScalarCollection extends ElementState
{
	@xml_collection("person")	ArrayList<String>	stuff;
	
	@xml_collection("link")		ArrayList<ParsedURL> purls;
	
	static final TranslationScope TS	= TranslationScope.get("test_scalar", null, TestScalarCollection.class);
	
	static final String	xml	= "<test_scalar_collection><person>fred</person><person>wilma</person><link>http://www.google.com</link><link>http://ecologylab.cs.tamu.edu</link></test_scalar_collection>";
	
	public static void main(String[] a)
	{
		try
		{
			ElementState es	= ElementState.translateFromXMLSAX(xml, TS);
			
			es.translateToXML(System.out);
			
		} catch (XMLTranslationException e)
		{
			e.printStackTrace();
		}
	}
}
