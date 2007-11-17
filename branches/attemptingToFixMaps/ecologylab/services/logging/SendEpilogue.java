package ecologylab.services.logging;

import ecologylab.xml.XMLTranslationException;
import ecologylab.xml.xml_inherit;

/**
 * Allows the application to send application-specific content to the log, at the end of a session. <p/> NB: this class
 * should *never* be extended in an application specific way, because the LoggingServer should never need to know the
 * TranslationSpace for such a super class. What you do extend is the {@link Epilogue Epilogue} object.
 * 
 * @author andruid
 * @author eunyee
 */
@xml_inherit public final class SendEpilogue extends LogueMessage
{
	public SendEpilogue(Logging logging, Epilogue epilogue)
	{
		super(logging);
		try
		{
			bufferToLog = epilogue.translateToXML((StringBuilder) null);
			bufferToLog.insert(0, Logging.OP_SEQUENCE_END);
			bufferToLog.append(endLog());
		}
		catch (XMLTranslationException e)
		{
			e.printStackTrace();
		}
	}

	public SendEpilogue()
	{
		super();
	}

	public String endLog()
	{
		return "</" + logName() + ">";
	}
}