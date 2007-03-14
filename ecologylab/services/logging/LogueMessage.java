package ecologylab.services.logging;

import ecologylab.xml.XmlTools;
import ecologylab.xml.xml_inherit;

/**
 * Base class for SendPrologue and SendEpilogue.
 * Probably should not be used for anything else.
 * Enables passing of the logName, in order to write custom log XML element
 * open and close tags.
 *
 * @author andruid
 */
@xml_inherit
public class LogueMessage extends LogRequestMessage
{

	@xml_attribute protected String logName;

	/**
	 * Constructor for building from the Logging class.
	 * @param logging
	 */
	public LogueMessage(Logging logging)
	{
		logName			= XmlTools.getXmlTagName(logging.getClass(), "State", false);
	}
	/*
	 * Constructor for automatic translation;
	 */
	public LogueMessage()
	{
		super();
	}

	public String logName() {
		return (logName != null) ? logName : "logging";
	}

}