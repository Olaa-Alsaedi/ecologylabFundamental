package ecologylab.services;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Vector;

import ecologylab.generic.ObjectRegistry;
import ecologylab.services.messages.RequestMessage;
import ecologylab.xml.ElementState;
import ecologylab.xml.NameSpace;
import ecologylab.xml.XmlTranslationException;

/**
 * Interface Ecology Lab Distributed Computing Services framework<p/>
 * 
 * Multi-threaded services server. 
 * Accepts XML RequestMessages via TCP/IP.
 * Translates these into ElementState objects via ecologylab.xml (using reflection).
 * Performs services based on the messages, and acknowledges with responses.
 * <p/>
 * In some cases, you may wish to extend this class to provided application specific functionalities.
 * In many cases, all you will need to do is define your messaging semantics, and let the framework do the work.
 * 
 * @author andruid
 * @author blake
 */
public class ServicesServer extends ServicesServerBase
{
	private int				portNumber;
	protected ServerSocket	serverSocket;
	
	boolean					finished;
	
	Thread					thread;
	
	/**
	 * Space that defines mappings between xml names, and Java class names,
	 * for request messages.
	 */
	NameSpace		requestTranslationSpace;
	
	/**
	 * Provides a context for request processing.
	 */
	protected ObjectRegistry	objectRegistry;
	
	Vector			serverToClientConnections		= new Vector();
	/**
	 * Limit the maximum number of client connection to the server
	 */
	private static int		maxConnectionSize				= 50;
	private int				connectionCount					= 0;

	/**
	 * This is the actual way to create an instance of this.
	 * 
	 * @param portNumber
	 * @param requestTranslationSpace
	 * @param objectRegistry
	 * @return	A server instance, or null if it was not possible to open a ServerSocket
	 * 			on the port on this machine.
	 */
	public static ServicesServer get(int portNumber,
						  NameSpace requestTranslationSpace, ObjectRegistry objectRegistry)
	{
		ServicesServer newServer	= null;
		try
		{
			newServer	= new ServicesServer(portNumber, requestTranslationSpace, objectRegistry);
		} catch (IOException e)
		{
			println("ServicesServer ERROR: can't open ServerSocket on port " + portNumber);
			e.printStackTrace();
		}
		return newServer;
	}

	/**
	 * Create a services server, that listens on the specified port, and
	 * uses the specified TranslationSpaces for operating on messages.
	 * 
	 * @param portNumber
	 * @param requestTranslationSpace
	 * @param objectRegistry Provides a context for request processing.
	 * @throws IOException 
	 */
	public ServicesServer(int portNumber,
						  NameSpace requestTranslationSpace, ObjectRegistry objectRegistry)
	throws IOException, java.net.BindException
	{
        super(portNumber, requestTranslationSpace, objectRegistry);
		
		serverSocket = new ServerSocket(portNumber);
		debug("created.");
	}
	private String toString;
	
	public String toString()
	{
		String toString	= this.toString;
		if (toString == null)
		{
			toString		=  this.getClassName() + "[" + portNumber + "]";
			this.toString	= toString;
		}
		return toString;
	}

    /**
     * Remove the argument passed in from the set of connections we know about.
     * 
     * @param serverToClientConnection
     */
    protected void connectionTerminated(ServerToClientConnection serverToClientConnection)
    {
        serverToClientConnections.remove(serverToClientConnection);
        
        super.connectionTerminated();
    }
    
	public void run()
	{
       while (!finished)
        {
            try
            {
            	Socket sock = serverSocket.accept();
            	ServerToClientConnection s2c = getConnection(sock);
            	synchronized (this)
            	{	// avoid race conditions near stop()
	            	if (!finished && (connectionCount < maxConnectionSize))
	            	{
	            		debugA("created " + s2c);
		                serverToClientConnections.add(s2c);
		                connectionCount ++;
		                Thread  thread = new Thread(s2c, "ServerToClientConnection " + serverToClientConnections.size());
		                thread.start();
	            	}
	            	else   // print the debug message to the server (reason why connection refused)
	            	{
	            		debug("No more connection allowed OR ServicesServer stopped, connectionCount=" + 
	            				connectionCount + "  finished=" + finished);
	            		debug("Connection Refused: between client: " + sock.getLocalSocketAddress() + " and server: " 
	            				+ sock.getLocalAddress() );
	            	}
            	}
            } catch (SocketException e)
            {
            	if (!finished)
            	{
                	debug("ERROR during serverSocket accept!");
                    e.printStackTrace();
            	}
            } catch (IOException e)
            {
            	debug("ERROR during serverSocket accept!");
                e.printStackTrace();
            }
        }

        close();
	}

	private void close()
	{
		try
        {
			debug("closing");
            serverSocket.close();
        } catch (IOException e)
        {
        	debug("ERROR: Could not close ServerSocket on port " + this.portNumber);
            e.printStackTrace();
        }
	}
	
	/**
	 * Create a ServerToClientConnection, the object that handles the connection to
	 * each incoming client.
	 * To extend the functionality of the client, you can override this method in your subclass of this,
	 * to return a subclass of ServerToClientConnection.
	 * 
	 * @param incomingSocket
	 * @return
	 * @throws IOException
	 */
	protected ServerToClientConnection getConnection(Socket incomingSocket)
	throws IOException
	{
		return new ServerToClientConnection(incomingSocket, this);
	}
	
	public RequestMessage translateXMLStringToRequestMessage(String messageString, boolean doRecursiveDescent)
	throws XmlTranslationException
	{
		RequestMessage requestMessage
					= (RequestMessage) ElementState.translateFromXMLString(messageString, requestTranslationSpace, doRecursiveDescent);
		return requestMessage;
	}
	/**
	 * Start the ServicesServer, at the specified priority.
	 * @param priority
	 */
	public synchronized void start(int priority)
	{
 		if (thread == null)
		{
 			if (serverSocket == null)
 			{
 				debug("ERROR: can't startup because server socket creation failed.");
 				return;
 			}
 			else
 			{
				Thread t	= new Thread(this, this.toString());
				t.setPriority(priority);
				thread		= t;
				t.start();
 			}
		}
	}
    
	public synchronized boolean stop()
	{
		debug("stopping.");
        
		if (thread != null)
		{
			finished	= true;
			close();
			thread		= null;
		}
        
		Object[] connections	= serverToClientConnections.toArray();
        
		for (int i=0; i<connections.length; i++)
		{
			ServerToClientConnection s2c	= (ServerToClientConnection) connections[i];
			// this will also remove the element by calling connectionTerminated
			//debug("stop connection["+i+"] " +s2c);
			s2c.stop();
		}
        
		connectionCount = 0;
        
        return true;
	}
    
	public boolean start()
	{
		start(Thread.NORM_PRIORITY);
        
        return true;
	}
}
