package ecologylab.services.messages;

import ecologylab.collections.Scope;
import ecologylab.xml.xml_inherit;

/**
 * Abstract base class for asynchronous server-to-client updates.
 * 
 * @author bill
 */
@xml_inherit abstract public class UpdateMessage<S extends Scope> extends ServiceMessage<S>
{
    public UpdateMessage()
    {
    }

    /**
     * Allows for custom processing of ResponseMessages by ServicesClient,
     * without extending that.
     * 
     * @param objectRegistry
     *            provide a context for response message processing.
     * 
     */
    public void processUpdate(S objectRegistry)
    {

    }
}