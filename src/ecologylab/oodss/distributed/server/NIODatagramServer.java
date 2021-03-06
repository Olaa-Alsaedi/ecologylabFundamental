package ecologylab.oodss.distributed.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.DatatypeConverter;

import ecologylab.collections.Scope;
import ecologylab.generic.CharBufferPool;
import ecologylab.generic.HashMapArrayList;
import ecologylab.generic.StringBuilderPool;
import ecologylab.io.ByteBufferPool;
import ecologylab.oodss.distributed.common.SessionObjects;
import ecologylab.oodss.distributed.impl.NIODatagramCore;
import ecologylab.oodss.distributed.server.clientsessionmanager.BaseSessionManager;
import ecologylab.oodss.distributed.server.clientsessionmanager.DatagramClientSessionManager;
import ecologylab.oodss.distributed.server.clientsessionmanager.SessionHandle;
import ecologylab.oodss.messages.MultiRequestMessage;
import ecologylab.oodss.messages.RequestMessage;
import ecologylab.oodss.messages.ResponseMessage;
import ecologylab.oodss.messages.ServiceMessage;
import ecologylab.serialization.SimplTypesScope;

/**
 * OODSS Datagram server.
 * 
 * @author bilhamil
 * 
 * @param <S>
 *          Application scope type parameter.
 */
public class NIODatagramServer<S extends Scope> extends NIODatagramCore<S> implements
		NIOServerProcessor
{
	private long																										sidIndex								= 1;

	private ConcurrentHashMap<String, DatagramClientSessionManager>	sidToSessionManager			= new ConcurrentHashMap<String, DatagramClientSessionManager>();

	private ConcurrentHashMap<SocketAddress, String>								socketAddressesToSids		= new ConcurrentHashMap<SocketAddress, String>();

	private ConcurrentHashMap<String, SocketAddress>								sidsToSocketAddresses		= new ConcurrentHashMap<String, SocketAddress>();

	/** Map in which keys are sessionTokens, and values are associated SessionHandles */
	private HashMapArrayList<Object, SessionHandle>									clientSessionHandleMap	= new HashMapArrayList<Object, SessionHandle>();

	// protected S applicationObjectScope;

	private MessageDigest																						digester;

	private int																											dispensedTokens					= 1;

	private int																											portNumber;

	protected DatagramChannel																				chan;

	/**
	 * Initializes and starts the datagram Server. Open's up the server on all interfaces.
	 * 
	 * @param portNumber
	 *          service's port number
	 * @param translationScope
	 * @param objectRegistry
	 *          application scope
	 * @param useCompression
	 *          whether or not to use compression
	 */
	public NIODatagramServer(	int portNumber,
														SimplTypesScope translationScope,
														S objectRegistry,
														boolean useCompression)
	{
		super(translationScope, objectRegistry, useCompression);

		// this.applicationObjectScope = objectRegistry;

		this.objectRegistry.put(SessionObjects.SESSIONS_MAP, clientSessionHandleMap);
		// applicationObjectScope.put(SessionObjects.SESSIONS_MAP, clientSessionHandleMap);

		this.portNumber = portNumber;
		try
		{
			chan = DatagramChannel.open();
			chan.socket().bind(new InetSocketAddress(portNumber));
			chan.configureBlocking(false);
			chan.register(selector, SelectionKey.OP_READ);
		}
		catch (ClosedChannelException e)
		{
			debug("Channel isn't open but it should be!: " + e.getMessage());
			e.printStackTrace();
		}
		catch (SocketException e)
		{
			debug("Failed to open socket!: " + e.getMessage());
			e.printStackTrace();
		}
		catch (IOException e)
		{
			debug("Failed to open socket!: " + e.getMessage());
			e.printStackTrace();
		}

		try
		{
			digester = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			weird("This can only happen if the local implementation does not include the given hash algorithm.");
			e.printStackTrace();
		}

		this.start();
	}

	/**
	 * Initializes and starts the datagram server. Open's up the server on all interfaces. Doesn't use
	 * compression by default.
	 * 
	 * @param portNumber
	 *          service's port number
	 * @param translationScope
	 * @param objectRegistry
	 *          application scope
	 */
	public NIODatagramServer(int portNumber, SimplTypesScope translationScope, S objectRegistry)
	{
		this(portNumber, translationScope, objectRegistry, false);
	}

	@Override
	protected final void handleMessage(long uid, ServiceMessage<S> message, SelectionKey key,
			InetSocketAddress address)
	{
		DatagramClientSessionManager clientSessionManager = null;
		String sid;

		// if (message instanceof InitConnectionRequest)
		// {
		// InitConnectionRequest initReq = (InitConnectionRequest) message;
		synchronized (socketAddressesToSids)
		{
			// if (initReq.getSessionId() == null)
			// {
			sid = socketAddressesToSids.get(address);

			if (sid == null)
			{ // no session manager created yet; create one
				sid = this.generateSessionToken(address);

				debug("New session: " + sid + " at: " + address);

				socketAddressesToSids.put(address, sid);
				sidsToSocketAddresses.put(sid, address);

				DatagramClientSessionManager mngr = this.generateContextManager(sid,
																																				key,
																																				this.objectRegistry,
																																				// this.applicationObjectScope,
																																				address);

				SessionHandle hndl = new SessionHandle(mngr);

				sidToSessionManager.put(sid, mngr);
				clientSessionHandleMap.put(sid, hndl);

				mngr.getScope().put(SessionObjects.SESSION_HANDLE, new SessionHandle(mngr));
			}

			clientSessionManager = sidToSessionManager.get(sid);

			/*
			 * Have to keep track of the most recent key if messages are coming in on multiple ports on
			 * the server.
			 */
			clientSessionManager.updateKey(key);

			ResponseMessage response = clientSessionManager.processRequest(	(RequestMessage) message,
																																			address.getAddress());

			if (response != null)
				this.sendMessage(response, key, uid, address);

		}
	}

	//
	//
	//
	//
	//
	// // client expecting a new sid back
	// // try to restore previous sid
	// if ((sid = socketAddressesToSids.get(address)) != null)
	// {
	// debug("Restoring session id: " + sid + " at :" + address);
	// this.sendMessage(new InitConnectionResponse(socketAddressesToSids.get(address)), key,
	// uid, address);
	// }
	// else
	// {
	// sid = this.generateSessionToken((InetSocketAddress) address);
	//
	// debug("New session: " + sid + " at: " + address);
	//
	// socketAddressesToSids.put(address, sid);
	// sidsToSocketAddresses.put(sid, address);
	//
	// clientSessionManager = new Scope(this.applicationObjectScope);
	// clientSessionManager.put(BaseSessionManager.SESSION_ID, sid);
	// onSessionCreation(sid, clientSessionManager);
	//
	// sidToSessionManager.put(sid, clientSessionManager);
	// this.sendMessage(new InitConnectionResponse(sid), key, uid, address);
	// }
	// }
	// else
	// {
	// if (sidToSessionManager.containsKey((initReq.getSessionId())))
	// {
	// sid = initReq.getSessionId();
	//
	// debug("Session: " + sid + " moved to " + address);
	//
	// socketAddressesToSids.put(address, sid);
	// sidsToSocketAddresses.put(sid, address);
	// this.sendMessage(new InitConnectionResponse(sid), key, uid, address);
	// }
	// else
	// {
	// if ((sid = reassignedSessions.get(initReq.getSessionId())) == null)
	// {
	// sid = this.generateSessionToken((InetSocketAddress) address);
	// reassignedSessions.put(initReq.getSessionId(), sid);
	//
	// socketAddressesToSids.put(address, sid);
	// sidsToSocketAddresses.put(sid, address);
	//
	// clientSessionManager = new Scope(this.applicationObjectScope);
	// clientSessionManager.put(BaseSessionManager.SESSION_ID, sid);
	// sidToSessionManager.put(sid, new Scope(this.applicationObjectScope));
	// }
	//
	// debug("Unknown session: "
	// + initReq.getSessionId()
	// + " at "
	// + address
	// + " reassinged to "
	// + sid);
	//
	// this.sendMessage(new InitConnectionResponse(sid), key, uid, address);
	// }
	// }
	// }
	// }
	// else
	// {
	// synchronized (socketAddressesToSids)
	// {
	// if ((sid = socketAddressesToSids.get(address)) != null)
	// {
	// clientSessionManager = sidToSessionManager.get(sid);
	// }
	// else
	// {
	// this.sendMessage(new InitConnectionRequest(), key, uid, address);
	// }
	// }
	//
	// if (clientSessionManager != null)
	// {
	// handleAssociatedMessage(message, clientSessionManager, key, uid, address);
	// }
	//
	// }
	//
	// }

	protected void handleAssociatedMessage(ServiceMessage<S> message, Scope clientRegistry,
			SelectionKey key, Long uid, InetSocketAddress address)
	{
		if (message instanceof RequestMessage)
		{
			ResponseMessage<S> response = ((RequestMessage) message).performService(clientRegistry);
			if (response != null)
			{
				this.sendMessage(response, key, uid, address);
			}
		}
		if (message instanceof MultiRequestMessage)
		{
			Collection<ResponseMessage> responses = ((MultiRequestMessage) message).performService(clientRegistry);
			for (ResponseMessage response : responses)
			{
				this.sendMessage(response, key, uid, address);
			}
		}
	}

	@Override
	protected void waitForReconnect()
	{
	}

	synchronized protected long getNextSid()
	{
		return sidIndex++;
	}

	protected String generateSessionToken(InetSocketAddress incomingSocket)
	{
		// clear digester
		digester.reset();

		// we make a string consisting of the following:
		// time of initial connection (when this method is called),
		// client ip, client actual port
		digester.update(String.valueOf(System.currentTimeMillis()).getBytes());
		// digester.update(String.valueOf(System.nanoTime()).getBytes());
		// digester.update(this.incomingConnectionSockets[0].getInetAddress()
		// .toString().getBytes());
		digester.update(incomingSocket.getAddress().toString().getBytes());
		digester.update(String.valueOf(incomingSocket.getPort()).getBytes());

		digester.update(String.valueOf(this.dispensedTokens).getBytes());

		dispensedTokens++;

		// convert to normal characters and return as a String
		return DatatypeConverter.printBase64Binary(digester.digest());
	}

	public int getPortNumber()
	{
		return portNumber;
	}

	/**
	 * @return the global scope for this server
	 */
	public S getApplicationObjectScope()
	{
		return this.objectRegistry;
		// return applicationObjectScope;
	}

	/**
	 * Hook method to allow changing the ContextManager to enable specific extra functionality.
	 * 
	 * @param token
	 * @param sc
	 * @param translationScopeIn
	 * @param registryIn
	 * @return
	 */
	protected DatagramClientSessionManager generateContextManager(String sessionId, SelectionKey sk,
			S registryIn, InetSocketAddress address)
	{
		return new DatagramClientSessionManager(sessionId, this, sk, registryIn, address);
	}

	/**
	 * @see ecologylab.oodss.distributed.server.NIOServerProcessor#invalidate(java.lang.String,
	 *      boolean)
	 */
	@Override
	public boolean invalidate(String sessionId, boolean forcePermanent)
	{
		DatagramClientSessionManager cm = this.sidToSessionManager.get(sessionId);

		// figure out if the disconnect is permanent; will be permanent if forcing
		// (usually bad client), if there is no context manager (client never sent
		// data), or if the client manager says it is invalidating (client
		// disconnected properly)
		boolean permanent = (forcePermanent ? true : (cm == null ? true : cm.isInvalidating()));

		// get the context manager...
		if (permanent)
		{
			synchronized (sidToSessionManager)
			{ // ...if this session will not be restored, remove the context
				// manager
				sidToSessionManager.remove(sessionId);
				clientSessionHandleMap.remove(sessionId);
				SocketAddress address = this.sidsToSocketAddresses.remove(sessionId);
				this.socketAddressesToSids.remove(address);
			}
		}

		if (cm != null)
		{
			cm.shutdown();
		}

		return permanent;
	}

	/**
	 * @see ecologylab.oodss.distributed.server.NIOServerProcessor#restoreContextManagerFromSessionId(java.lang.String,
	 *      ecologylab.oodss.distributed.server.clientsessionmanager.BaseSessionManager)
	 */
	@Override
	public boolean restoreContextManagerFromSessionId(String oldId,
			BaseSessionManager newContextManager)
	{
		SocketAddress oldAddress = this.sidsToSocketAddresses.get(oldId);

		if (oldAddress != null)
		{ // the old session manager is still there
			// connect it to the new address through the maps

			SocketAddress newAddress = ((DatagramClientSessionManager) newContextManager).getAddress();

			this.sidsToSocketAddresses.put(oldId, newAddress);
			this.socketAddressesToSids.remove(oldAddress);
			this.socketAddressesToSids.put(newAddress, oldId);

			return true;
		}

		return false;
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
	}

	@Override
	public ByteBufferPool getSharedByteBufferPool()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CharBufferPool getSharedCharBufferPool()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StringBuilderPool getSharedStringBuilderPool()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @see ecologylab.oodss.distributed.impl.NIODatagramCore#stop()
	 */
	@Override
	public void stop()
	{
		super.stop();

		try
		{
			this.chan.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void increaseSharedBufferPoolSize(int newCapacity) {
		// TODO Auto-generated method stub
		
	}
}
