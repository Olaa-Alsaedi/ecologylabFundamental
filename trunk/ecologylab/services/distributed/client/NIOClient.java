/*
 * Created on May 12, 2006
 */
package ecologylab.services.distributed.client;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import ecologylab.appframework.ObjectRegistry;
import ecologylab.generic.Generic;
import ecologylab.generic.StringTools;
import ecologylab.services.distributed.common.ClientConstants;
import ecologylab.services.distributed.common.ServerConstants;
import ecologylab.services.distributed.impl.NIONetworking;
import ecologylab.services.distributed.impl.PreppedRequest;
import ecologylab.services.distributed.impl.PreppedRequestPool;
import ecologylab.services.exceptions.BadClientException;
import ecologylab.services.messages.InitConnectionRequest;
import ecologylab.services.messages.InitConnectionResponse;
import ecologylab.services.messages.RequestMessage;
import ecologylab.services.messages.ResponseMessage;
import ecologylab.xml.ElementState;
import ecologylab.xml.TranslationSpace;
import ecologylab.xml.XMLTranslationException;

/**
 * Services Client using NIO; a major difference with the NIO version is state tracking. Since the sending methods do
 * not wait for the server to return.
 * 
 * This object will listen for incoming messages from the server, and will send any messages that it recieves on its
 * end.
 * 
 * Since the underlying implementation is TCP/IP, messages sent should be sent in order, and the responses should match
 * that order.
 * 
 * Another major difference between this and the non-NIO version of ServicesClient is that it is StartAndStoppable.
 * 
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 */
public class NIOClient extends NIONetworking implements Runnable, ClientConstants
{
	protected String									serverAddress;

	protected final CharBuffer						outgoingChars						= CharBuffer
																											.allocate(MAX_PACKET_SIZE_CHARACTERS);

	protected final StringBuilder					requestBuffer						= new StringBuilder(
																											MAX_PACKET_SIZE_CHARACTERS);

	/**
	 * Stores incoming character data until it can be parsed into an XML message and turned into a Java object.
	 */
	protected final StringBuilder					incomingMessageBuffer			= new StringBuilder(
																											MAX_PACKET_SIZE_CHARACTERS);

	/** Stores outgoing character data for ResponseMessages. */
	protected final StringBuilder					outgoingMessageBuffer			= new StringBuilder(
																											MAX_PACKET_SIZE_CHARACTERS);

	/** Stores outgoing header character data. */
	protected final StringBuilder					outgoingMessageHeaderBuffer	= new StringBuilder(
																											MAX_PACKET_SIZE_CHARACTERS);

	/**
	 * stores the sequence of characters read from the header of an incoming message, may need to persist across read
	 * calls, as the entire header may not be sent at once.
	 */
	private final StringBuilder					currentHeaderSequence			= new StringBuilder();

	/**
	 * stores the sequence of characters read from the header of an incoming message and identified as being a key for a
	 * header entry; may need to persist across read calls.
	 */
	private final StringBuilder					currentKeyHeaderSequence		= new StringBuilder();

	private ResponseMessage							responseMessage					= null;

	private volatile boolean						blockingRequestPending			= false;

	private final Queue<ResponseMessage>		blockingResponsesQueue			= new LinkedBlockingQueue<ResponseMessage>();

	protected final Queue<PreppedRequest>		requestsQueue						= new LinkedBlockingQueue<PreppedRequest>();

	/**
	 * A map that stores all the requests that have not yet gotten responses. Maps UID to RequestMessage.
	 */
	protected final Map<Long, PreppedRequest>	unfulfilledRequests				= new HashMap<Long, PreppedRequest>();

	/**
	 * The number of times a call to reconnect() should attempt to contact the server before giving up and calling
	 * stop().
	 */
	protected int										reconnectAttempts					= RECONNECT_ATTEMPTS;

	/** The number of milliseconds to wait between reconnect attempts. */
	protected int										waitBetweenReconnectAttempts	= WAIT_BEWTEEN_RECONNECT_ATTEMPTS;

	private String										sessionId							= null;

	/**
	 * selectInterval is passed to select() when it is called in the run loop. It is set to 0 indicating that the loop
	 * should block until the selector picks up something interesting. However, if this class is subclassed, it is
	 * possible to modify this value so that the select() will only block for the number of ms supplied by this field.
	 * Thus, it is possible (by also subclassing the sendData() method) to have this send data on an interval, and then
	 * select.
	 */
	protected long										selectInterval						= 0;

	protected boolean									isSending							= false;

	/** Contains the unique identifier for the next message that the client will send. */
	private long										uidIndex								= 1;

	private int											endOfFirstHeader					= -1;

	protected int										startReadIndex						= 0;

	/**
	 * Counts how many characters still need to be extracted from the incomingMessageBuffer before they can be turned
	 * into a message (based upon the HTTP header). A value of -1 means that there is not yet a complete header, so no
	 * length has been determined (yet).
	 */
	private int											contentLengthRemaining			= -1;

	/**
	 * Stores the first XML message from the incomingMessageBuffer, or parts of it (if it is being read over several
	 * invocations).
	 */
	private final StringBuilder					firstMessageBuffer				= new StringBuilder();

	/** Stores the key-value pairings from a parsed HTTP-like header on an incoming message. */
	protected final HashMap<String, String>	headerMap							= new HashMap<String, String>();

	protected SocketChannel							thisSocket							= null;

	protected PreppedRequestPool					pRequestPool;

	public NIOClient(String serverAddress, int portNumber, TranslationSpace messageSpace,
			ObjectRegistry<?> objectRegistry) throws IOException
	{
		super("NIOClient", portNumber, messageSpace, objectRegistry);

		this.serverAddress = serverAddress;

		this.pRequestPool = new PreppedRequestPool(2, 4, MAX_PACKET_SIZE_CHARACTERS);
	}

	/**
	 * If this client is not already connected, connects to the specified serverAddress on the specified portNumber, then
	 * calls start() to begin listening for server responses and processing them, then sends handshake data and
	 * establishes the session id.
	 * 
	 * @see ecologylab.services.distributed.legacy.ServicesClientBase#connect()
	 */
	public boolean connect()
	{
		debug("initializing connection...");
		if (this.connectImpl())
		{

			debug("starting listener thread...");
			this.start();

			// now send first handshake message
			ResponseMessage initResponse = this.sendMessage(new InitConnectionRequest(this.sessionId));

			if (initResponse instanceof InitConnectionResponse)
			{
				if (this.sessionId == null)
				{
					debug("new session...");
					this.sessionId = ((InitConnectionResponse) initResponse).getSessionId();
					debug(this.sessionId);
				}
				else if (this.sessionId == ((InitConnectionResponse) initResponse).getSessionId())
				{
					debug("reconnected and restored previous connection: " + this.sessionId);
				}
				else
				{
					String newId = ((InitConnectionResponse) initResponse).getSessionId();
					debug("unable to restore previous session, " + this.sessionId + "; new session: " + newId);
					this.unableToRestorePreviousConnection(this.sessionId, newId);
					this.sessionId = newId;
				}
			}
		}

		debug("connected? " + this.connected());
		return connected();
	}

	/**
	 * Connect to the server (if not already connected). Return connection status.
	 * 
	 * @return True if connected, false if not.
	 */
	private boolean connectImpl()
	{
		return connected() ? true : createConnection();
	}

	/**
	 * Sets the UID for request (if necessary), enqueues it then registers write interest for the NIOClient's selection
	 * key and calls wakeup() on the selector.
	 * 
	 * @param request
	 * @throws XMLTranslationException
	 */
	protected PreppedRequest prepareAndEnqueueRequestForSending(RequestMessage request) throws XMLTranslationException
	{
		long uid = this.generateUid();
		request.setUid(uid);

		// fill requestBuffer
		request.translateToXML(requestBuffer);

		PreppedRequest pReq = this.pRequestPool.acquire();
		pReq.setRequest(requestBuffer);
		pReq.setUid(uid);
		pReq.setDisposable(request.isDisposable());

		requestBuffer.setLength(0);

		enqueueRequestForSending(pReq);

		return pReq;
	}

	protected void enqueueRequestForSending(PreppedRequest request)
	{

		synchronized (requestsQueue)
		{
			requestsQueue.add(request);
		}

		this.queueForWrite(this.thisSocket.keyFor(selector));

		selector.wakeup();
	}

	public void disconnect(boolean waitForResponses)
	{
		while (this.requestsRemaining() > 0 && this.connected() && waitForResponses)
		{
			debug("*******************Request queue not empty, finishing " + requestsRemaining()
					+ " messages before disconnecting...");
			synchronized (this)
			{
				try
				{
					wait(100);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}

		debug("*******************disconnecting...");

		try
		{
			if (connected())
			{
				debug("*******************client is connected...");

				while (waitForResponses && connected() && !this.shutdownOK())
				{
					debug("*******************" + this.unfulfilledRequests.size()
							+ " requests still pending response from server.");
					debug("*******************connected: " + connected());

					synchronized (this)
					{
						try
						{
							wait(100);
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
					}
				}

				if (thisSocket != null)
				{
					synchronized (thisSocket)
					{
						debug("*******************shutting down output.");
						// shut down output
						thisSocket.socket().shutdownOutput();

						debug("*******************closing down output.");
						// now that there's nothing coming back, shut down input
						thisSocket.socket().shutdownInput();

						debug("*******************close down all.");
						// close it all out
						thisSocket.close();
						thisSocket.keyFor(selector).cancel();
					}
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			nullOut();

			stop();
		}
	}

	/**
	 * @return
	 */
	protected boolean shutdownOK()
	{
		return !(this.unfulfilledRequests.size() > 0);
	}

	protected void nullOut()
	{
		if (thisSocket != null)
		{
			synchronized (thisSocket)
			{
				debug("null out");

				thisSocket = null;
			}
		}
	}

	public boolean connected()
	{
		return (thisSocket != null) && !thisSocket.isConnectionPending() && thisSocket.isConnected();
	}

	/**
	 * Side effect of calling start().
	 */
	protected boolean createConnection()
	{
		try
		{
			// create the channel and connect it to the server
			thisSocket = SocketChannel.open(new InetSocketAddress(serverAddress, portNumber));

			// disable blocking
			thisSocket.configureBlocking(false);

			if (connected())
			{
				// register the channel for read operations, now that it is
				// connected
				thisSocket.register(selector, SelectionKey.OP_READ);
			}
		}
		catch (BindException e)
		{
			debug("Couldnt create socket connection to server '" + serverAddress + "': " + e);

			nullOut();
		}
		catch (PortUnreachableException e)
		{
			debug("Server is alive, but has no daemon on portNumber " + portNumber + ": " + e);

			nullOut();
		}
		catch (SocketException e)
		{
			debug("Server '" + serverAddress + "' unreachable: " + e);

			nullOut();
		}
		catch (IOException e)
		{
			debug("Bad response from server: " + e);

			nullOut();
		}

		return connected();
	}

	/**
	 * Hook method to allow subclasses to deal with a failed restore after disconnect. This should be a rare occurance,
	 * but some sublcasses may need to deal with this case specifically.
	 * 
	 * @param oldId -
	 *           the previous session id.
	 * @param newId -
	 *           the new session id given by the server after reconnect.
	 */
	protected void unableToRestorePreviousConnection(String oldId, String newId)
	{
	}

	/**
	 * Sends request, but does not wait for the response. The response gets processed later in a non-stateful way by the
	 * run method.
	 * 
	 * @param request
	 *           the request to send to the server.
	 * 
	 * @return the UID of request.
	 */
	public PreppedRequest nonBlockingSendMessage(RequestMessage request) throws IOException
	{
		if (connected())
		{
			try
			{
				return this.prepareAndEnqueueRequestForSending(request);
			}
			catch (XMLTranslationException e)
			{
				error("error translating message; returning null");
				e.printStackTrace();

				return null;
			}
		}
		else
		{
			throw new IOException("Not connected to server.");
		}
	}

	/**
	 * Blocking send. Sends the request and waits infinitely for the response, which it returns.
	 * 
	 * @see ecologylab.services.distributed.legacy.ServicesClientBase#sendMessage(ecologylab.services.messages.RequestMessage)
	 */
	public synchronized ResponseMessage sendMessage(RequestMessage request)
	{
		return this.sendMessage(request, -1);
	}

	/**
	 * Blocking send with timeout. Sends the request and waits timeOutMillis milliseconds for the response, which it
	 * returns. sendMessage(RequestMessage, int) will return null if no message was recieved in time.
	 * 
	 * @param request
	 * @param timeOutMillis
	 * @return
	 */
	public synchronized ResponseMessage sendMessage(RequestMessage request, int timeOutMillis)
	{
		ResponseMessage returnValue = null;

		// notify the connection thread that we are waiting on a response
		blockingRequestPending = true;

		long currentMessageUid;

		boolean blockingRequestFailed = false;
		long startTime = System.currentTimeMillis();
		int timeCounter = 0;

		try
		{
			currentMessageUid = this.prepareAndEnqueueRequestForSending(request).getUid();
		}
		catch (XMLTranslationException e1)
		{
			error("error translating to XML; returning null");
			e1.printStackTrace();

			return null;
		}

		// if (request instanceof InitConnectionRequest)
		// {
		// debug("init request: " + ((InitConnectionRequest) request).getSessionId());
		// }

		// wait to be notified that the response has arrived
		while (blockingRequestPending && !blockingRequestFailed)
		{
			if (timeOutMillis <= -1)
			{
				debug("waiting on blocking request");
			}

			try
			{
				if (timeOutMillis > -1)
				{
					wait(timeOutMillis);
				}
				else
				{
					wait();
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				Thread.interrupted();
			}

			debug("waking");

			timeCounter += System.currentTimeMillis() - startTime;
			startTime = System.currentTimeMillis();

			while ((blockingRequestPending) && (!blockingResponsesQueue.isEmpty()))
			{
				returnValue = blockingResponsesQueue.poll();

				if (returnValue.getUid() == currentMessageUid)
				{
					debug("got the right response");

					blockingRequestPending = false;

					blockingResponsesQueue.clear();

					return returnValue;
				}
				else
				{
					returnValue = null;
				}
			}

			if ((timeOutMillis > -1) && (timeCounter >= timeOutMillis) && (blockingRequestPending))
			{
				blockingRequestFailed = true;
			}
		}

		if (blockingRequestFailed)
		{
			debug("Request failed due to timeout!");
		}

		return returnValue;
	}

	@Override public void start()
	{
		if (connected())
		{
			super.start();
		}
	}

	@Override public void stop()
	{
		System.err.println("shutting down client listening thread.");

		super.stop();
	}

	/**
	 * Returns the next request in the request queue and removes it from that queue. Sublcasses that override the queue
	 * functionality will need to override this method.
	 * 
	 * @return the next request in the request queue.
	 */
	protected PreppedRequest dequeueRequest()
	{
		return this.requestsQueue.poll();
	}

	/**
	 * Returns the number of requests remaining in the requests queue. Subclasses that override the queue functionality
	 * will need to change this method accordingly.
	 * 
	 * @return the size of the request queue.
	 */
	protected int requestsRemaining()
	{
		return this.requestsQueue.size();
	}

	/**
	 * Attempts to reconnect this client if it has been disconnected. After reconnecting, re-queues all requests still in
	 * the unfulfilledRequests map.
	 * 
	 * If the attempt to reconnect fails, reconnect() will attempt a number of times equal to reconnectAttempts, waiting
	 * waitBetweenReconnectAttempts milliseconds between attempts. If all such attempts fail, calls stop() on this to
	 * shut down the client. The client will then need to be re-started manually.
	 * 
	 */
	protected void reconnect()
	{
		debug("attempting to reconnect...");
		int reconnectsRemaining = this.reconnectAttempts;
		if (reconnectsRemaining < 0)
		{
			reconnectsRemaining = 1;
		}

		while (!connected() && reconnectsRemaining > 0)
		{
			this.nullOut();

			// attempt to connect, if failed, wait
			if (!this.connect() && --reconnectsRemaining > 0)
			{
				try
				{
					this.wait(this.waitBetweenReconnectAttempts);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}

		if (connected())
		{
			synchronized (unfulfilledRequests)
			{
				List<PreppedRequest> rerequests = new LinkedList<PreppedRequest>(this.unfulfilledRequests.values());

				Collections.sort(rerequests);

				for (PreppedRequest req : rerequests)
				{
					this.enqueueRequestForSending(req);
				}
			}
		}
		else
		{
			this.stop();
		}
	}

	/**
	 * Hook method to allow subclasses to deal with unfulfilled requests in their own way.
	 * 
	 * Adds req to the unfulfilled requests map.
	 * 
	 * @param req
	 */
	protected void addUnfulfilledRequest(PreppedRequest req)
	{
		synchronized (unfulfilledRequests)
		{
			this.unfulfilledRequests.put(req.getUid(), req);
		}
	}

	/**
	 * Stores the request in the unfulfilledRequests map according to its UID, converts it to XML, prepends the HTTP-like
	 * header, then writes it out to the channel. Then re-registers key for reading.
	 * 
	 * @param pReq
	 */
	private void createPacketFromMessageAndSend(PreppedRequest pReq, SelectionKey incomingKey)
	{
		StringBuilder outgoingReq = pReq.getRequest();

		this.addUnfulfilledRequest(pReq);

		try
		{
			StringBuilder message = new StringBuilder(CONTENT_LENGTH_STRING);
			message.append(':');
			message.append(outgoingReq.length());
			message.append(HTTP_HEADER_TERMINATOR);
			message.append(outgoingReq);

			outgoingChars.clear();

			int capacity;

			while (message.length() > 0)
			{
				outgoingChars.clear();
				capacity = outgoingChars.capacity();

				if (message.length() > capacity)
				{
					outgoingChars.put(message.toString(), 0, capacity);
					message.delete(0, capacity);
				}
				else
				{
					outgoingChars.put(message.toString());
					message.delete(0, message.length());
				}

				outgoingChars.flip();

				synchronized (ENCODER)
				{
					thisSocket.write(ENCODER.encode(outgoingChars));
				}
			}
		}
		catch (ClosedChannelException e)
		{
			debug("connection severed; disconnecting and storing requests...");
			this.disconnect(false);

			this.reconnect();
		}
		catch (BufferOverflowException e)
		{
			debug("buffer overflow.");
			e.printStackTrace();
			System.out.println("capacity: " + outgoingChars.capacity());
			System.out.println("outgoing request: " + outgoingReq);
		}
		catch (NullPointerException e)
		{
			e.printStackTrace();
			System.out.println("recovering.");
		}
		catch (CharacterCodingException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			debug("connection severed; disconnecting...");
			this.disconnect(false);

			this.reconnect();
		}

		// incomingKey.interestOps(incomingKey.interestOps() & (~SelectionKey.OP_WRITE));
	}

	/**
	 * Converts incomingMessage to a ResponseMessage, then processes the response and removes its UID from the
	 * unfulfilledRequests map.
	 * 
	 * @param incomingMessage
	 * @return
	 */
	private ResponseMessage processString(String incomingMessage)
	{

		if (show(5))
			debug("incoming message: " + incomingMessage);

		try
		{
			responseMessage = translateXMLStringToResponseMessage(incomingMessage);
		}
		catch (XMLTranslationException e)
		{
			e.printStackTrace();
		}

		if (responseMessage == null)
		{
			debug("ERROR: translation failed: ");
		}
		else
		{
			// perform the service being requested
			processResponse(responseMessage);

			synchronized (unfulfilledRequests)
			{
				PreppedRequest finishedReq = unfulfilledRequests.remove(responseMessage.getUid());

				finishedReq = this.pRequestPool.release(finishedReq);
			}
		}

		// debug("just translated response: "+responseMessage.getUid());

		return responseMessage;
	}

	public void disconnect()
	{
		disconnect(true);
	}

	/**
	 * @param reconnectAttempts
	 *           the reconnectAttempts to set
	 */
	public void setReconnectAttempts(int reconnectAttempts)
	{
		this.reconnectAttempts = reconnectAttempts;
	}

	/**
	 * @param waitBetweenReconnectAttempts
	 *           the waitBetweenReconnectAttempts to set
	 */
	public void setWaitBetweenReconnectAttempts(int waitBetweenReconnectAttempts)
	{
		this.waitBetweenReconnectAttempts = waitBetweenReconnectAttempts;
	}

	protected void clearSessionId()
	{
		this.sessionId = null;
	}

	/**
	 * This method does nothing, as NIOClients do not accept incoming connections.
	 * 
	 * @see ecologylab.services.distributed.impl.NIONetworking#acceptKey(java.nio.channels.SelectionKey)
	 */
	@Override protected void acceptKey(SelectionKey key)
	{
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#checkAndDropIdleKeys()
	 */
	@Override protected void checkAndDropIdleKeys()
	{
		// TODO Auto-generated method stub

	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#invalidateKey(java.nio.channels.SelectionKey, boolean)
	 */
	@Override protected void invalidateKey(SelectionKey key, boolean permanent)
	{
		debug("server disconnected...");
		this.disconnect(false);
		this.reconnect();
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#processReadData(java.lang.Object,
	 *      java.nio.channels.SocketChannel, byte[], int)
	 */
	@Override protected void processReadData(Object readSessionId, SocketChannel sc, ByteBuffer bytes, int bytesRead)
			throws BadClientException
	{
		synchronized (incomingMessageBuffer)
		{
			try
			{
				incomingMessageBuffer.append(DECODER.decode(bytes));

				// look for HTTP header
				while (incomingMessageBuffer.length() > 0)
				{
					if (endOfFirstHeader == -1)
					{
						endOfFirstHeader = this.parseHeader(startReadIndex, incomingMessageBuffer);
					}

					if (endOfFirstHeader == -1)
					{ /*
						 * no header yet; if it's too large, bad client; if it's not too large yet, just exit, it'll get
						 * checked again when more data comes down the pipe
						 */
						if (incomingMessageBuffer.length() > ServerConstants.MAX_HTTP_HEADER_LENGTH)
						{
							// clear the buffer
							BadClientException e = new BadClientException(sc.socket().getInetAddress().getHostAddress(),
									"Maximum HTTP header length exceeded. Read " + incomingMessageBuffer.length() + "/"
											+ MAX_HTTP_HEADER_LENGTH);

							incomingMessageBuffer.setLength(0);

							throw e;
						}

						// next time around, start reading from where we left off this time
						startReadIndex = incomingMessageBuffer.length();

						break;
					}
					else
					{ // we've read all of the header, and have it loaded into the map; now we can use it
						if (contentLengthRemaining == -1)
						{
							try
							{
								// handle all header information here; delete it when done here
								contentLengthRemaining = Integer.parseInt(this.headerMap.get(CONTENT_LENGTH_STRING));

								// done with the header; delete it
								incomingMessageBuffer.delete(0, endOfFirstHeader);
								this.headerMap.clear();
							}
							catch (NumberFormatException e)
							{
								e.printStackTrace();
								contentLengthRemaining = -1;
							}
							// next time we read the header (the next message), we need to start from the beginning
							startReadIndex = 0;
						}
					}

					/*
					 * we have the end of the first header (otherwise we would have broken out earlier). If we don't have the
					 * content length, something bad happened, because it should have been read.
					 */
					if (contentLengthRemaining == -1)
					{
						/*
						 * if we still don't have the remaining length, then there was a problem
						 */
						break;
					}
					else if (contentLengthRemaining > MAX_PACKET_SIZE_CHARACTERS)
					{
						throw new BadClientException(sc.socket().getInetAddress().getHostAddress(),
								"Specified content length too large: " + contentLengthRemaining);
					}

					try
					{
						// see if the incoming buffer has enough characters to
						// include the specified content length
						if (incomingMessageBuffer.length() >= contentLengthRemaining)
						{
							firstMessageBuffer.append(incomingMessageBuffer.substring(0, contentLengthRemaining));

							incomingMessageBuffer.delete(0, contentLengthRemaining);

							// reset to do a new read on the next invocation
							contentLengthRemaining = -1;
							endOfFirstHeader = -1;
						}
						else
						{
							firstMessageBuffer.append(incomingMessageBuffer);

							// indicate that we need to get more from the buffer in
							// the next invocation
							contentLengthRemaining -= incomingMessageBuffer.length();

							incomingMessageBuffer.setLength(0);
						}
					}
					catch (NullPointerException e)
					{
						e.printStackTrace();
					}

					if ((firstMessageBuffer.length() > 0) && (contentLengthRemaining == -1))
					{ /*
						 * if we've read a complete message, then contentLengthRemaining will be reset to -1
						 */
						// we got a response
						if (!this.blockingRequestPending)
						{
							processString(firstMessageBuffer.toString());
						}
						else
						{
							blockingResponsesQueue.add(processString(firstMessageBuffer.toString()));
							synchronized (this)
							{
								notify();
							}
						}

						firstMessageBuffer.setLength(0);
					}
				}
			}
			catch (CharacterCodingException e1)
			{
				e1.printStackTrace();
			}

		}
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#removeBadConnections(java.nio.channels.SelectionKey)
	 */
	@Override protected void removeBadConnections(SelectionKey key)
	{
		// TODO Auto-generated method stub

	}

	/**
	 * Increments the internal tracker of the next UID, and returns the current one.
	 * 
	 * @return the current uidIndex.
	 */
	public synchronized long generateUid()
	{
		// return the current value of uidIndex, then increment.
		return uidIndex++;
	}

	/**
	 * Use the ServicesClient and its NameSpace to do the translation. Can be overridden to provide special
	 * functionalities
	 * 
	 * @param messageString
	 * @return
	 * @throws XMLTranslationException
	 */
	protected ResponseMessage translateXMLStringToResponseMessage(String messageString) throws XMLTranslationException
	{
		return translateXMLStringToResponseMessage(messageString, true);
	}

	/**
	 * Translate a decoded String of characters from the server into a ResponseMessage.
	 * 
	 * @param messageString
	 * @param doRecursiveDescent
	 * @return
	 * @throws XMLTranslationException
	 */
	public ResponseMessage translateXMLStringToResponseMessage(String messageString, boolean doRecursiveDescent)
			throws XMLTranslationException
	{
		return (ResponseMessage) ElementState.translateFromXMLCharSequence(messageString, translationSpace);
	}

	/**
	 * Process a ResponseMessage received from the server in response to a previously-sent RequestMessage.
	 * 
	 * @param responseMessageToProcess
	 */
	protected void processResponse(ResponseMessage responseMessageToProcess)
	{
		responseMessageToProcess.processResponse(objectRegistry);
	}

	public String getServer()
	{
		return this.serverAddress;
	}

	public void setServer(String serverAddress)
	{
		this.serverAddress = serverAddress;
	}

	/**
	 * Returns the most recently used UID.
	 * 
	 * @return the current uidIndex.
	 */
	public long getUidNoIncrement()
	{
		// return the current value of uidIndex
		return uidIndex;
	}

	/**
	 * Check to see if the server is running.
	 * 
	 * @return true if the server is running, false otherwise.
	 */
	public boolean isServerRunning()
	{
		debug("checking availability of server");
		boolean serverIsRunning = createConnection();

		// we're just checking, don't keep the connection
		if (connected())
		{
			debug("server is running; disconnecting");
			disconnect();
		}

		return serverIsRunning;
	}

	/**
	 * Try and connect to the server. If we fail, wait CONNECTION_RETRY_SLEEP_INTERVAL and try again. Repeat ad nauseum.
	 */
	public void waitForConnect()
	{
		System.out.println("Waiting for a server on port " + portNumber);
		while (!connect())
		{
			// try again soon
			Generic.sleep(WAIT_BEWTEEN_RECONNECT_ATTEMPTS);
		}
	}

	/**
	 * Parses the header of an incoming set of characters (i.e. a message from a client to a server), loading all of the
	 * HTTP-like headers into the given headerMap.
	 * 
	 * If headerMap is null, this method will throw a null pointer exception.
	 * 
	 * @param allIncomingChars -
	 *           the characters read from an incoming stream.
	 * @param headerMap -
	 *           the map into which all of the parsed headers will be placed.
	 * @return the length of the parsed header, or -1 if it was not yet found.
	 */
	protected int parseHeader(int startChar, StringBuilder allIncomingChars)
	{
		// indicates that we might be at the end of the header
		boolean maybeEndSequence = false;
		char currentChar;

		synchronized (currentHeaderSequence)
		{
			StringTools.clear(currentHeaderSequence);
			StringTools.clear(currentKeyHeaderSequence);

			int length = allIncomingChars.length();

			for (int i = 0; i < length; i++)
			{
				currentChar = allIncomingChars.charAt(i);

				switch (currentChar)
				{
				case (':'):
					/*
					 * we have the end of a key; move the currentHeaderSequence into the currentKeyHeaderSequence and clear
					 * it
					 */
					currentKeyHeaderSequence.append(currentHeaderSequence);

					StringTools.clear(currentHeaderSequence);

					break;
				case ('\r'):
					/*
					 * we have the end of a line; if there's a CRLF, then we have the end of the value sequence or the end of
					 * the header.
					 */
					if (allIncomingChars.charAt(i + 1) == '\n')
					{
						if (!maybeEndSequence)
						{// load the key/value pair
							headerMap.put(currentKeyHeaderSequence.toString().toLowerCase(), currentHeaderSequence.toString());

							StringTools.clear(currentKeyHeaderSequence);
							StringTools.clear(currentHeaderSequence);

							i++; // so we don't re-read that last character
						}
						else
						{ // end of the header
							return i + 2;
						}

						maybeEndSequence = true;
					}
					break;
				default:
					currentHeaderSequence.append(currentChar);
					maybeEndSequence = false;
					break;
				}
			}

			// if we got here, we didn't finish the header
			return -1;
		}
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#writeKey(java.nio.channels.SelectionKey)
	 */
	@Override protected void writeKey(SelectionKey key) throws IOException
	{
		// lock outgoing requests queue, send data from it, then switch out of write mode
		synchronized (requestsQueue)
		{
			while (this.requestsRemaining() > 0)
			{
				this.createPacketFromMessageAndSend(this.dequeueRequest(), key);
			}
		}
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIOCore#acceptReady(java.nio.channels.SelectionKey)
	 */
	@Override protected void acceptReady(SelectionKey key)
	{
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIOCore#connectReady(java.nio.channels.SelectionKey)
	 */
	@Override protected void connectReady(SelectionKey key)
	{
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIOCore#readFinished(java.nio.channels.SelectionKey)
	 */
	@Override protected void readFinished(SelectionKey key)
	{
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIOCore#acceptFinished(java.nio.channels.SelectionKey)
	 */
	@Override public void acceptFinished(SelectionKey key)
	{
	}
}
