package ecologylab.serialization.deserializers.pullhandlers;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;

import ecologylab.generic.Debug;
import ecologylab.net.ConnectionAdapter;
import ecologylab.net.PURLConnection;
import ecologylab.net.ParsedURL;
import ecologylab.platformspecifics.FundamentalPlatformSpecifics;
import ecologylab.serialization.DeserializationHookStrategy;
import ecologylab.serialization.FieldDescriptor;
import ecologylab.serialization.FieldTypes;
import ecologylab.serialization.SIMPLTranslationException;
import ecologylab.serialization.TranslationContext;
import ecologylab.serialization.SimplTypesScope;
import ecologylab.serialization.deserializers.ISimplDeserializationPre;
import ecologylab.serialization.deserializers.ISimplDeserializationPost;
import ecologylab.serialization.deserializers.pullhandlers.binaryformats.BinaryPullDeserializer;
import ecologylab.serialization.deserializers.pullhandlers.binaryformats.TLVPullDeserializer;
import ecologylab.serialization.deserializers.pullhandlers.stringformats.JSONPullDeserializer;
import ecologylab.serialization.deserializers.pullhandlers.stringformats.StringPullDeserializer;
import ecologylab.serialization.formatenums.BinaryFormat;
import ecologylab.serialization.formatenums.Format;
import ecologylab.serialization.formatenums.StringFormat;
import ecologylab.serialization.serializers.ISimplSerializationPost;
import ecologylab.serialization.serializers.ISimplSerializationPre;

public abstract class PullDeserializer extends Debug implements FieldTypes
{

	protected SimplTypesScope							translationScope;

	protected TranslationContext					translationContext;

	protected DeserializationHookStrategy	deserializationHookStrategy;

	static final ConnectionAdapter				connectionAdapter	= new ConnectionAdapter();

	/**
	 * Constructs that creates a JSON deserialization handler
	 * 
	 * @param translationScope
	 *          translation scope to use for de/serializing subsequent char sequences
	 * @param translationContext
	 *          used for graph handling
	 */
	public PullDeserializer(SimplTypesScope translationScope, TranslationContext translationContext)
	{
		this.translationScope = translationScope;
		this.translationContext = translationContext;
		this.deserializationHookStrategy = null;
	}

	/**
	 * Constructs that creates a JSON deserialization handler
	 * 
	 * @param translationScope
	 *          translation scope to use for de/serializing subsequent char sequences
	 * @param translationContext
	 *          used for graph handling
	 */
	public PullDeserializer(SimplTypesScope translationScope, TranslationContext translationContext,
			DeserializationHookStrategy deserializationHookStrategy)
	{
		this.translationScope = translationScope;
		this.translationContext = translationContext;
		this.deserializationHookStrategy = deserializationHookStrategy;
	}

	/**
	 * 
	 * @param file
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public Object parse(File file) throws SIMPLTranslationException
	{
		try
		{
			FileInputStream fileInputStream = new FileInputStream(file);
			BufferedInputStream bufferedStream = new BufferedInputStream(fileInputStream);

			this.translationContext.setBaseDirFile(file.getParentFile());

			Object object = parse(bufferedStream);
			bufferedStream.close();
			return object;
		}
		catch (FileNotFoundException e)
		{
			throw new SIMPLTranslationException("Can't open file " + file.getAbsolutePath(), e);
		}
		catch (IOException e)
		{
			throw new SIMPLTranslationException("Can't close file " + file.getAbsolutePath(), e);
		}

	}

	/**
	 * 
	 * @param purl
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public Object parse(ParsedURL purl) throws SIMPLTranslationException
	{
		if (purl.isFile())
			return parse(purl.file());

		PURLConnection purlConnection = purl.connect(connectionAdapter);
		Object result = parse(purlConnection.inputStream());
		purlConnection.recycle();
		return result;
	}

	/**
	 * 
	 * @param url
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public Object parse(URL url) throws SIMPLTranslationException
	{
		return parse(new ParsedURL(url));
	}

	/**
	 * 
	 * @param inputStream
	 * @param charSet
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public abstract Object parse(InputStream inputStream, Charset charSet)
			throws SIMPLTranslationException;

	/**
	 * 
	 * @param inputStream
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public abstract Object parse(InputStream inputStream) throws SIMPLTranslationException;

	/**
	 * 
	 * @param object
	 * @param translationContext
	 *          TODO
	 */
	protected void deserializationPostHook(Object object, TranslationContext translationContext)
	{
		if (object instanceof ISimplDeserializationPost)
		{
			((ISimplDeserializationPost) object).deserializationPostHook(translationContext, object);
		}
	}

	/**
	 * 
	 * @param object
	 * @param translationContext
	 *          TODO
	 */
	protected void deserializationPreHook(Object object, TranslationContext translationContext)
	{
		if (object instanceof ISimplDeserializationPre)
		{
			((ISimplDeserializationPre) object).deserializationPreHook(translationContext);
		}
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param format
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public static PullDeserializer getDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext, Format format) throws SIMPLTranslationException
	{
		return getDeserializer(translationScope, translationContext, null, format);
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param deserializationHookStrategy
	 * @param format
	 * @return
	 * @throws SIMPLTranslationException
	 */

	public static PullDeserializer getDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext,
			DeserializationHookStrategy deserializationHookStrategy, Format format)
			throws SIMPLTranslationException
	{
		switch (format)
		{
		case XML:
			return FundamentalPlatformSpecifics.get().getXMLPullDeserializer(translationScope,
					translationContext, deserializationHookStrategy);
		case JSON:
			return new JSONPullDeserializer(translationScope, translationContext,
					deserializationHookStrategy);
		case TLV:
			return new TLVPullDeserializer(translationScope, translationContext,
					deserializationHookStrategy);
		case BIBTEX:

		default:
			throw new SIMPLTranslationException(format + " format not supported");
		}
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param stringFormat
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public static StringPullDeserializer getStringDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext, StringFormat stringFormat)
			throws SIMPLTranslationException
	{
		return getStringDeserializer(translationScope, translationContext, null, stringFormat);
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param deserializationHookStrategy
	 * @param stringFormat
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public static StringPullDeserializer getStringDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext,
			DeserializationHookStrategy deserializationHookStrategy, StringFormat stringFormat)
			throws SIMPLTranslationException
	{
		switch (stringFormat)
		{
		case XML:
			return FundamentalPlatformSpecifics.get().getXMLPullDeserializer(translationScope,
					translationContext, deserializationHookStrategy);
		case JSON:
			return new JSONPullDeserializer(translationScope, translationContext,
					deserializationHookStrategy);
		case BIBTEX:
		default:
			throw new SIMPLTranslationException(stringFormat + " format not supported");
		}
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param binaryFormat
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public static BinaryPullDeserializer getBinaryDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext, BinaryFormat binaryFormat)
			throws SIMPLTranslationException
	{
		return getBinaryDeserializer(translationScope, translationContext, null, binaryFormat);
	}

	/**
	 * 
	 * @param translationScope
	 * @param translationContext
	 * @param deserializationHookStrategy
	 * @param binaryFormat
	 * @return
	 * @throws SIMPLTranslationException
	 */
	public static BinaryPullDeserializer getBinaryDeserializer(SimplTypesScope translationScope,
			TranslationContext translationContext,
			DeserializationHookStrategy deserializationHookStrategy, BinaryFormat binaryFormat)
			throws SIMPLTranslationException
	{
		switch (binaryFormat)
		{
		case TLV:
		default:
			throw new SIMPLTranslationException(binaryFormat + " format not supported");
		}
	}

}