package ecologylab.platformspecifics;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import ecologylab.appframework.types.prefs.MetaPrefSet;
import ecologylab.appframework.types.prefs.PrefSet;
import ecologylab.net.ParsedURL;
import ecologylab.serialization.ClassDescriptor;
import ecologylab.serialization.FieldDescriptor;
import ecologylab.serialization.GenericTypeVar;
import ecologylab.serialization.SIMPLTranslationException;
import ecologylab.serialization.deserializers.pullhandlers.stringformats.XMLParser;

public interface IFundamentalPlatformSpecifics
{
	
	// in ApplicationEnvironment
	void initializePlatformSpecificTranslation();

	// in ecologylab.serialization.ClassDescriptor;
	void deriveSuperClassGenericTypeVars(ClassDescriptor classDescriptor);

	// in ecologylab.serialization.FieldDescriptor;
	void deriveFieldGenericTypeVars(FieldDescriptor fieldDescriptor);

	Class<?> getTypeArgClass(Field field, int i, FieldDescriptor fiedlDescriptor);

	// in ecologylab.serialization.GenericTypeVar;
	void checkBoundParameterizedTypeImpl(GenericTypeVar g, Type bound);

	void checkTypeWildcardTypeImpl(GenericTypeVar g, Type type);

	void checkTypeParameterizedTypeImpl(GenericTypeVar g, Type type);

	// more stuff
	Object getOrCreatePrefsEditor(MetaPrefSet metaPrefSet, PrefSet prefSet, ParsedURL savePrefsPURL,
			final boolean createJFrame, final boolean isStandalone);

	// in ParsedUrl
	String[] getReaderFormatNames();

	// in Generic
	void beep();

	void showDialog(String msg, String[] digital_options);

	// in Pref
	Object usePrefColor(String name, Object defaultValue);

	Object lookupColor(String name, Object defaultValue) throws ClassCastException;

	Class[] addtionalPrefOpTranslations();

	Class[] additionalPrefSetBaseTranslations();

	// platform specific types
	void initializePlatformSpecificTypes();

	
	// XMLParser
	XMLParser getXMLParser(InputStream inputStream, Charset charSet) throws SIMPLTranslationException;

	XMLParser getXMLParser(InputStream inputStream) throws SIMPLTranslationException;

	XMLParser getXMLParser(CharSequence charSequence) throws SIMPLTranslationException;
	
	
	
}
