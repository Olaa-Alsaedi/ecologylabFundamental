/**
 * 
 */
package ecologylab.tests;

import ecologylab.serialization.ClassDescriptor;
import ecologylab.serialization.ElementState;
import ecologylab.serialization.Format;
import ecologylab.serialization.SIMPLTranslationException;
import ecologylab.serialization.StringFormat;
import ecologylab.serialization.TranslationScope;
import ecologylab.serialization.annotations.simpl_format;
import ecologylab.serialization.annotations.simpl_scalar;

/**
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 * 
 */
public class TestXMLFormat extends ElementState
{
	@simpl_scalar
	@simpl_format("#")
	double	decimal0	= 1.654654654654645321321;

	@simpl_scalar
	@simpl_format("#.#")
	double	decimal1	= -1.654654654654645321321;

	@simpl_scalar
	@simpl_format("#.0#")
	double	decimal2	= -11111.654654654654645321321;

	@simpl_scalar
	@simpl_format("#.###")
	double	decimal3	= 0.654654654654645321321;

	public TestXMLFormat()
	{

	}

	/**
	 * @param args
	 * @throws SIMPLTranslationException
	 */
	public static void main(String[] args) throws SIMPLTranslationException
	{
		TestXMLFormat t = new TestXMLFormat();

		ClassDescriptor.serialize(t, System.out, StringFormat.XML);

		System.out.println();

		TranslationScope translationScope = TranslationScope.get("test", TestXMLFormat.class);
		ClassDescriptor.serialize(translationScope.deserialize(ClassDescriptor.serialize(t,
				StringFormat.XML).toString(), StringFormat.XML), System.out, StringFormat.XML);
		System.out.println();

		System.out.println(ClassDescriptor.serialize(t, StringFormat.XML));
	}
}