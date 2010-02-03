package ecologylab.services.logging;

import java.util.ArrayList;

import ecologylab.collections.Scope;
import ecologylab.services.messages.ResponseMessage;
import ecologylab.xml.ElementState;
import ecologylab.xml.TranslationScope;
import ecologylab.xml.XMLTranslationException;
import ecologylab.xml.xml_inherit;

/**
 * Bundle a sequence of {@link MixedInitiativeOp MixedInitiativeOp}s, and send them to the logging server.
 * 
 * @author eunyee
 * @author andruid
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 */
@xml_inherit public final class LogOps extends LogEvent
{
	/** Constructor for XML translation. */
	public LogOps()
	{
		super();
	}

	public LogOps(int bufferSize)
	{
		super(bufferSize);
	}

	static final Class[]				CLASSES	=
														{ LogOps.class, LogEvent.class };

	static final TranslationScope	TS			= TranslationScope.get("lo", CLASSES);

	public static void main(String[] a)
	{
		LogOps l = new LogOps();

		l.bufferToLog = new StringBuilder(
				"<cf_text_token delims_before=\" \" string=\"&quot;an\"/><cf_text_token delims_before=\" \" string=\"unprepared\"/>");

		try
		{
			l.translateToXML(System.out);

			StringBuilder buffy = l.translateToXML((StringBuilder) null);
			System.out.println("");
			ElementState l2 = ElementState.translateFromXMLCharSequence(buffy.toString(), TS);
			l2.translateToXML(System.out);
		}
		catch (XMLTranslationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}