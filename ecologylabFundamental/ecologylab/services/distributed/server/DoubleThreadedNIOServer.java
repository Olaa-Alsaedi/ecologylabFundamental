/**
 * 
 */
package ecologylab.services.distributed.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;

import ecologylab.collections.Scope;
import ecologylab.generic.CharBufferPool;
import ecologylab.generic.HashMapArrayList;
import ecologylab.net.NetTools;
import ecologylab.services.distributed.common.ServerConstants;
import ecologylab.services.distributed.impl.AbstractNIOServer;
import ecologylab.services.distributed.impl.NIOServerIOThread;
import ecologylab.services.distributed.server.clientsessionmanager.AbstractClientSessionManager;
import ecologylab.services.distributed.server.clientsessionmanager.ClientSessionManager;
import ecologylab.services.exceptions.BadClientException;
import ecologylab.xml.TranslationScope;

/**
 * A server that uses NIO and two threads (one for handling IO, the other for
 * handling interfacing with messages).
 * 
 * Automatically processes and responds to any client RequestMessages.
 * 
 * Subclasses should generally override the generateContextManager hook method,
 * so that they can use their own, specific ContextManager in place of the
 * default.
 * 
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 */
public class DoubleThreadedNIOServer extends AbstractNIOServer implements
		ServerConstants
{
	protected static InetAddress[] addressToAddresses(InetAddress address)
	{
		InetAddress[] addresses =
		{ address };
		return addresses;
	}

	public static DoubleThreadedNIOServer getInstance(int portNumber,
			InetAddress[] inetAddress, TranslationScope requestTranslationScope,
			Scope applicationObjectScope, int idleConnectionTimeout,
			int maxPacketSize) throws IOException, BindException
	{
		return new DoubleThreadedNIOServer(portNumber, inetAddress,
				requestTranslationScope, applicationObjectScope,
				idleConnectionTimeout, maxPacketSize);
	}

	public static DoubleThreadedNIOServer getInstance(int portNumber,
			InetAddress inetAddress, TranslationScope requestTranslationScope,
			Scope applicationObjectScope, int idleConnectionTimeout,
			int maxPacketSize) throws IOException, BindException
	{
		InetAddress[] address =
		{ inetAddress };
		return getInstance(portNumber, address, requestTranslationScope,
				applicationObjectScope, idleConnectionTimeout, maxPacketSize);
	}

	Thread																			t								= null;

	boolean																			running						= false;

	/**
	 * Map in which keys are sessionTokens, and values are associated
	 * ClientSessionManagers.
	 */
	private HashMapArrayList<Object, AbstractClientSessionManager>	clientSessionManagerMap	= new HashMapArrayList<Object, AbstractClientSessionManager>();

	private static final Charset												ENCODED_CHARSET			= Charset
																																.forName(CHARACTER_ENCODING);

	private static CharsetDecoder												DECODER						= ENCODED_CHARSET
																																.newDecoder();

	protected int																	maxMessageSize;

	/**
	 * CharBuffers for use with translating from bytes to chars; may need to
	 * support having many messages come through at once.
	 */
	protected CharBufferPool													charBufferPool;

	/**
	 * 
	 */
	protected DoubleThreadedNIOServer(int portNumber,
			InetAddress[] inetAddresses, TranslationScope requestTranslationScope,
			Scope applicationObjectScope, int idleConnectionTimeout,
			int maxMessageSize) throws IOException, BindException
	{
		super(portNumber, inetAddresses, requestTranslationScope,
				applicationObjectScope, idleConnectionTimeout, maxMessageSize);

		this.maxMessageSize = maxMessageSize;

		instantiateCharBufferPool(maxMessageSize);
	}

	/**
	 * @param maxMessageSize
	 */
	protected void instantiateCharBufferPool(int maxMessageSize)
	{
		// make them a little bigger, in case more than one mega-huge message
		// comes in completely unlikely, but just to be safe
		this.charBufferPool = new CharBufferPool(maxMessageSize * 2);
	}

	/**
	 * 
	 */
	protected DoubleThreadedNIOServer(int portNumber, InetAddress inetAddress,
			TranslationScope requestTranslationScope,
			Scope applicationObjectScope, int idleConnectionTimeout,
			int maxPacketSize) throws IOException, BindException
	{
		this(portNumber, NetTools.wrapSingleAddress(inetAddress),
				requestTranslationScope, applicationObjectScope,
				idleConnectionTimeout, maxPacketSize);
	}

	/**
	 * Assumes that the server should be running on the local host (including
	 * external interfaces) with default sizes for everything.
	 * 
	 * @param portNumber -
	 *           the port number the server will run on.
	 * @param requestTranslationScope -
	 *           the scope of translation for incoming requests.
	 * @param applicationObjectScope -
	 *           the application object scope, containing application state
	 *           objects that messages will access and manipulate.
	 * @throws IOException
	 * @throws BindException
	 */
	protected DoubleThreadedNIOServer(int portNumber,
			TranslationScope requestTranslationScope, Scope applicationObjectScope)
			throws BindException, IOException
	{
		this(portNumber, NetTools.getAllInetAddressesForLocalhost(),
				requestTranslationScope, applicationObjectScope,
				DEFAULT_IDLE_TIMEOUT, DEFAULT_MAX_MESSAGE_LENGTH_CHARS);
	}

	public void processRead(Object sessionToken, NIOServerIOThread base,
			SelectionKey sk, ByteBuffer bs, int bytesRead)
			throws BadClientException
	{
		if (bytesRead > 0)
		{
			synchronized (clientSessionManagerMap)
			{
				AbstractClientSessionManager cm = clientSessionManagerMap
						.get(sessionToken);

				if (cm == null)
				{
					debug("server creating context manager for " + sessionToken);

					cm = generateContextManager(sessionToken, sk, translationSpace,
							applicationObjectScope);
					clientSessionManagerMap.put(sessionToken, cm);
				}

				try
				{
					CharBuffer buf = this.charBufferPool.acquire();

					DECODER.decode(bs, buf, true);
					buf.flip();
					cm.processIncomingSequenceBufToQueue(buf);

					buf = this.charBufferPool.release(buf);
				}
				catch (CharacterCodingException e)
				{
					e.printStackTrace();
				}
			}

			synchronized (this)
			{
				this.notify();
			}
		}
	}

	/**
	 * Hook method to allow changing the ContextManager to enable specific extra
	 * functionality.
	 * 
	 * @param token
	 * @param sc
	 * @param translationSpaceIn
	 * @param registryIn
	 * @return
	 */
	@Override protected AbstractClientSessionManager generateContextManager(
			Object token, SelectionKey sk, TranslationScope translationSpaceIn,
			Scope registryIn)
	{
		return new ClientSessionManager(token, maxMessageSize, this.getBackend(),
				this, sk, translationSpaceIn, registryIn);
	}

	public void run()
	{
		Iterator<AbstractClientSessionManager> contextIter;

		while (running)
		{
			synchronized (clientSessionManagerMap)
			{
				contextIter = clientSessionManagerMap.values().iterator();

				// process all of the messages in the queues
				while (contextIter.hasNext())
				{
					AbstractClientSessionManager cm = contextIter.next();

					try
					{
						cm.processAllMessagesAndSendResponses();
					}
					catch (BadClientException e)
					{
						// Handle BadClientException! -- remove it
						error(e.getMessage());

						// invalidate the manager's key
						this.getBackend().setPendingInvalidate(cm.getSocketKey(),
								true);

						// remove the manager from the collection
						contextIter.remove();
					}
				}
			}

			// sleep until notified of new messages
			synchronized (this)
			{
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
		}
	}

	/**
	 * @see ecologylab.generic.StartAndStoppable#start()
	 */
	@Override public void start()
	{
		running = true;

		if (t == null)
		{
			t = new Thread(this);
		}

		t.start();

		super.start();
	}

	/**
	 * @see ecologylab.generic.StartAndStoppable#stop()
	 */
	@Override public void stop()
	{
		debug("Server stopping.");
		running = false;

		super.stop();
	}

	/**
	 * @see ecologylab.services.distributed.impl.Shutdownable#shutdown()
	 */
	public void shutdown()
	{
		// TODO Auto-generated method stub

	}

	/**
	 * @see ecologylab.services.distributed.server.NIOServerProcessor#invalidate(java.lang.Object,
	 *      ecologylab.services.distributed.impl.NIOServerIOThread,
	 *      java.nio.channels.SocketChannel)
	 */
	public boolean invalidate(Object sessionId, boolean forcePermanent)
	{
		AbstractClientSessionManager cm = clientSessionManagerMap.get(sessionId);

		// figure out if the disconnect is permanent; will be permanent if forcing
		// (usually bad client), if there is no context manager (client never sent
		// data), or if the client manager says it is invalidating (client
		// disconnected properly)
		boolean permanent = (forcePermanent ? true : (cm == null ? true : cm
				.isInvalidating()));

		// get the context manager...
		if (permanent)
		{
			synchronized (clientSessionManagerMap)
			{ // ...if this session will not be restored, remove the context
				// manager
				clientSessionManagerMap.remove(sessionId);
			}
		}

		if (cm != null)
		{
			/*
			 * if we've gotten here, then the client has disconnected already, no
			 * reason to deal w/ the remaining messages // finish what the context
			 * manager was working on while (cm.isMessageWaiting()) { try {
			 * cm.processAllMessagesAndSendResponses(); } catch (BadClientException
			 * e) { e.printStackTrace(); } }
			 */
			cm.shutdown();
		}

		return permanent;
	}

	/**
	 * Attempts to switch the ContextManager for a SocketChannel. oldId indicates
	 * the session id that was used for the connection previously (in order to
	 * find the correct ContextManager) and newContextManager is the
	 * recently-created (and now, no longer necessary) ContextManager for the
	 * connection.
	 * 
	 * @param oldId
	 * @param newContextManager
	 * @return true if the restore was successful, false if it was not.
	 */
	public boolean restoreContextManagerFromSessionId(Object oldSessionId,
			AbstractClientSessionManager newContextManager)
	{
		debug("attempting to restore old session...");

		AbstractClientSessionManager oldContextManager;

		synchronized (clientSessionManagerMap)
		{
			oldContextManager = this.clientSessionManagerMap.get(oldSessionId);
		}
		if (oldContextManager == null)
		{ // cannot restore old context
			debug("restore failed.");
			return false;
		}
		else
		{
			oldContextManager.setSocket(newContextManager.getSocketKey());

			this.getBackend().

			debug("old session restored!");
			return true;
		}
	}

	/**
	 * 
	 * @return status of server in boolean
	 */
	public boolean isRunning()
	{
		return running;
	}

	@Override protected void shutdownImpl()
	{
		// TODO Auto-generated method stub

	}
}
