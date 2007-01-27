/*
 * Created on May 4, 2006
 */
package ecologylab.services.nio;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Iterator;

import ecologylab.generic.Debug;
import ecologylab.generic.ObjectRegistry;
import ecologylab.generic.StartAndStoppable;
import ecologylab.services.ServerConstants;
import ecologylab.xml.NameSpace;

/**
 * Used as a worker thread and client information container.
 * 
 * @author Zach Toups (toupsz@gmail.com)
 */
public class MessageProcessor2Threads extends Debug implements Runnable,
        ServerConstants, StartAndStoppable
{
    private Thread         thread;

    private boolean        running = false;

    private HashMap contexts = new HashMap();

    protected SelectionKey key;
    
    private NameSpace translationSpace;
    private ObjectRegistry registry;

    public MessageProcessor2Threads(NameSpace translationSpace, ObjectRegistry registry)
    {
        this.translationSpace = translationSpace;
        this.registry = registry;
    }

    protected ContextManager generateClientContext(Object token,
            SelectionKey key, NameSpace translationSpace,
            ObjectRegistry registry)
    {
        return new ContextManager(token, key, translationSpace, registry);
    }

    /**
     * Processes the next String in the messageQueue, sleeps when there are none
     * left.
     */
    public synchronized void run()
    {
        Iterator contextIterator;
        while (running)
        {
            synchronized(contexts)
            {
                contextIterator = contexts.values().iterator();
            
                // process all of the messages in the queues
                while (contextIterator.hasNext())
                {
                    ((ContextManager)contextIterator.next()).processAllMessagesAndSendResponses();
                }
            }
            
            // sleep until notified of new messages
            try
            {
                wait();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                Thread.interrupted();
            }
        }

        debug("Message Processor " + key.attachment() + " terminating.");
    }
    
    public void readKey(SelectionKey key)
    {
        ContextManager temp = (ContextManager) contexts.get(key.attachment());
        {
            if (temp == null)
            {
                synchronized(contexts)
                {
                    contexts.put(key.attachment(), this.generateClientContext(key.attachment(), key, translationSpace, registry));
                }
                
                temp = (ContextManager) contexts.get(key.attachment());
            }
            
            temp.readChannel();
        }
    }

    protected void removeKey(SelectionKey key)
    {
        debug("Key " + key.attachment()
                + " invalid; shutting down message processor.");
        
        synchronized(contexts)
        {
            if (contexts.containsKey(key.attachment()))
            {
                ((MessageProcessor) contexts.remove(key.attachment())).stop();
            }
        }
    }
    
    public void start()
    {
        running = true;

        if (thread == null)
        {
            thread = new Thread(this, "Message Processor");
            thread.start();
        }
    }

    public void stop()
    {
        running = false;
        
        contexts.clear();
    }

}