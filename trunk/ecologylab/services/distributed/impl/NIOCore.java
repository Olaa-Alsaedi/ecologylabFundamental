/**
 * 
 */
package ecologylab.services.distributed.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import ecologylab.generic.Debug;
import ecologylab.generic.ObjectOrHashMap;
import ecologylab.generic.StartAndStoppable;
import ecologylab.services.distributed.common.NetworkingConstants;
import ecologylab.services.exceptions.BadClientException;
import ecologylab.services.exceptions.ClientOfflineException;

/**
 * Provides core functionality for NIO-based servers or clients. This class is Runnable and StartAndStoppable; it's run
 * method automatically handles interest-switching on a selector's keys, as well as calling appropriate abstract methods
 * whenever interest ops are selected.
 * 
 * Subclasses are required to configure their own selector.
 * 
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 * 
 */
public abstract class NIOCore extends Debug implements StartAndStoppable, NetworkingConstants
{
	/**
	 * 
	 * @author James Greenfield
	 * 
	 */
	class SocketModeChangeRequest
	{
		public static final int	REGISTER						= 1;

		public static final int	CHANGEOPS					= 2;

		public static final int	INVALIDATE_PERMANENTLY	= 3;

		public static final int	INVALIDATE_TEMPORARILY	= 4;

		public SelectionKey		key;

		public int					type;

		public int					ops;

		public SocketModeChangeRequest(SelectionKey key, int type, int ops)
		{
			this.key = key;
			this.type = type;
			this.ops = ops;
		}
	}

	private Queue<SocketModeChangeRequest>	pendingSelectionOpChanges	= new ConcurrentLinkedQueue<SocketModeChangeRequest>();

	public Selector								selector;

	public String									networkingIdentifier			= "NIOCore";

	public boolean									running;

	public Thread									thread;

	public int										portNumber;

	/**
	 * Instantiates a new NIOCore object.
	 * 
	 * @param networkingIdentifier
	 *           the name to identify this object when its thread is created.
	 * @param portNumber
	 *           the port number that this object will use for network communications.
	 * @throws IOException
	 *            if an I/O error occurs while trying to open a Selector from the system.
	 */
	protected NIOCore(String networkingIdentifier, int portNumber) throws IOException
	{
		this.selector = Selector.open();

		this.networkingIdentifier = networkingIdentifier;
		this.portNumber = portNumber;
	}

	/**
	 * THIS METHOD SHOULD NOT BE CALLED DIRECTLY!
	 * 
	 * Proper use of this method is through the start / stop methods.
	 * 
	 * Main run method. Performs a loop of changing the mode (read/write) for each socket, if requested, then checks for
	 * and performs appropriate I/O for each socket that is ready. Ends when running is set to false (through the stop
	 * method).
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public final void run()
	{
		while (running)
		{
			// update pending selection operation changes
			synchronized (this.pendingSelectionOpChanges)
			{
				for (SocketModeChangeRequest changeReq : pendingSelectionOpChanges)
				{
					if (changeReq.key.channel().isRegistered())
					{
						/*
						 * Perform any changes to the interest ops on the keys, before selecting.
						 */
						switch (changeReq.type)
						{
						case SocketModeChangeRequest.CHANGEOPS:
							try
							{
								changeReq.key.interestOps(changeReq.ops);
							}
							catch (CancelledKeyException e)
							{
								debug("tried to change ops after key was cancelled.");
							}
							catch (IllegalArgumentException e1)
							{
								debug("illegal argument for interestOps: " + changeReq.ops);
							}
							break;
						case SocketModeChangeRequest.INVALIDATE_PERMANENTLY:
							invalidateKey(changeReq.key, true);
							break;
						case SocketModeChangeRequest.INVALIDATE_TEMPORARILY:
							invalidateKey(changeReq.key, false);
							break;
						}
					}
				}

				this.pendingSelectionOpChanges.clear();
			}

			// check selection operations
			try
			{
				if (selector.select() > 0)
				{
					/*
					 * get an iterator of the keys that have something to do we have to do it this way, because we have to be
					 * able to call remove() which will not work in a foreach loop
					 */
					Iterator<SelectionKey> selectedKeyIter = selector.selectedKeys().iterator();

					while (selectedKeyIter.hasNext())
					{
						/*
						 * get the key corresponding to the event and process it appropriately, then remove it
						 */
						SelectionKey key = selectedKeyIter.next();

						selectedKeyIter.remove();

						if (!key.isValid())
						{
							setPendingInvalidate(key, false);
						}
						else if (key.isReadable())
						{
							/*
							 * incoming readable, valid key; have to double-check validity here, because accept key may have
							 * rejected an incoming connection
							 */
							if (key.channel().isOpen() && key.isValid())
							{
								try
								{
									readReady(key);
									readFinished(key);
								}
								catch (ClientOfflineException e)
								{
									error(e.getMessage());
									setPendingInvalidate(key, false);
								}
								catch (BadClientException e)
								{
									// close down this evil connection!
									error(e.getMessage());
									this.removeBadConnections(key);
								}
							}
							else
							{
								debug("Channel closed on " + key.attachment() + ", removing.");
								invalidateKey(key, false);
							}
						}
						else if (key.isWritable())
						{
							try
							{
								writeReady(key);
								writeFinished(key);
							}
							catch (IOException e)
							{
								debug("IO error when attempting to write to socket; stack trace follows.");

								e.printStackTrace();
							}

						}
						else if (key.isAcceptable())
						{ // incoming connection; accept
							this.acceptReady(key);
							this.acceptFinished(key);
						}
						else if (key.isConnectable())
						{
							this.connectReady(key);
							this.connectFinished(key);
						}
					}
				}
			}
			catch (IOException e)
			{
				this.stop();

				debug("attempted to access selector after it was closed! shutting down");

				e.printStackTrace();
			}

			// remove any that were idle for too long
			this.checkAndDropIdleKeys();
		}

		this.close();
	}

	/**
	 * @param key
	 */
	protected abstract void acceptReady(SelectionKey key);

	/**
	 * @param key
	 */
	protected abstract void connectReady(SelectionKey key);

	/**
	 * @param key
	 */
	protected abstract void readFinished(SelectionKey key);

	/**
	 * @param key
	 */
	protected abstract void readReady(SelectionKey key) throws ClientOfflineException, BadClientException;

	/**
	 * Queues a request to change key's interest operations back to READ.
	 * 
	 * This method is automatically called after acceptReady(SelectionKey) in the main operating loop.
	 * 
	 * @param key
	 */
	public abstract void acceptFinished(SelectionKey key);

	/**
	 * Queues a request to change key's interest operations back to READ.
	 * 
	 * This method is automatically called after connectReady(SelectionKey) in the main operating loop.
	 * 
	 * @param key
	 */
	public void connectFinished(SelectionKey key)
	{
		this.queueForRead(key);

		selector.wakeup();
	}

	/**
	 * Queues a request to change key's interest operations back to READ.
	 * 
	 * This method is automatically called after writeReady(SelectionKey) in the main operating loop.
	 * 
	 * Perform any actions necessary after all data has been written from the outgoing queue to the client for this key.
	 * This is a hook method so that subclasses can provide specific functionality (such as, for example, invalidating
	 * the connection once the data has been sent.
	 * 
	 * @param key -
	 *           the SelectionKey that is finished writing.
	 */
	protected void writeFinished(SelectionKey key)
	{
		this.queueForRead(key);

		selector.wakeup();
	}

	protected abstract void removeBadConnections(SelectionKey key);

	/**
	 * Sets up a pending invalidate command for the given input.
	 * 
	 * @param chan -
	 *           the SocketChannel to invalidate.
	 */
	public void setPendingInvalidate(SelectionKey key, boolean permanent)
	{
		if (permanent)
		{
			synchronized (pendingSelectionOpChanges)
			{
				this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key,
						SocketModeChangeRequest.INVALIDATE_PERMANENTLY, 0));
			}
		}
		else
		{
			synchronized (pendingSelectionOpChanges)
			{
				this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key,
						SocketModeChangeRequest.INVALIDATE_TEMPORARILY, 0));
			}
		}
		selector.wakeup();
	}

	/**
	 * Shut down the connection associated with this SelectionKey. Subclasses should override to do your own
	 * housekeeping, then call super.invalidateKey(SelectionKey) to utilize the functionality here.
	 * 
	 * @param chan
	 *           The SocketChannel that needs to be shut down.
	 */
	protected void invalidateKey(SocketChannel chan)
	{
		try
		{
			chan.close();
		}
		catch (IOException e)
		{
			debug(e.getMessage());
		}
		catch (NullPointerException e)
		{
			debug(e.getMessage());
		}

		if (chan.keyFor(selector) != null)
		{ // it's possible that they key
			// was somehow disposed of
			// already,
			// perhaps it was already invalidated once
			chan.keyFor(selector).cancel();
		}
	}

	/**
	 * @see ecologylab.services.distributed.impl.NIONetworking#invalidateKey(java.nio.channels.SocketChannel, boolean)
	 */
	protected abstract void invalidateKey(SelectionKey key, boolean permanent);

	public void start()
	{
		// start the server running
		running = true;

		if (thread == null)
		{
			thread = new Thread(this, networkingIdentifier + " running on port " + portNumber);
		}

		synchronized (thread)
		{
			thread.start();
		}
	}

	public synchronized void stop()
	{
		running = false;

		this.close();

		if (thread != null)
		{
			synchronized (thread)
			{ // we cannot re-use the Thread object.
				thread = null;
			}
		}
	}

	protected void close()
	{
	}

	/**
	 * Check for timeout on all allocated keys; deallocate those that are hanging around, but no longer in use.
	 */
	protected abstract void checkAndDropIdleKeys();

	/**
	 * @param key
	 */
	protected abstract void writeReady(SelectionKey key) throws IOException;

	protected void queueForAccept(SelectionKey key)
	{
		synchronized (this.pendingSelectionOpChanges)
		{
			// queue the socket channel for writing
			this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key, SocketModeChangeRequest.CHANGEOPS,
					SelectionKey.OP_ACCEPT));
		}
	}

	protected void queueForConnect(SelectionKey key)
	{
		synchronized (this.pendingSelectionOpChanges)
		{
			// queue the socket channel for writing
			this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key, SocketModeChangeRequest.CHANGEOPS,
					SelectionKey.OP_CONNECT));
		}
	}

	protected void queueForRead(SelectionKey key)
	{
		synchronized (this.pendingSelectionOpChanges)
		{
			// queue the socket channel for writing
			this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key, SocketModeChangeRequest.CHANGEOPS,
					SelectionKey.OP_READ));
		}
	}

	protected void queueForWrite(SelectionKey key)
	{
		synchronized (this.pendingSelectionOpChanges)
		{
			// queue the socket channel for writing
			this.pendingSelectionOpChanges.offer(new SocketModeChangeRequest(key, SocketModeChangeRequest.CHANGEOPS,
					SelectionKey.OP_WRITE));
		}
	}

	/**
	 * @return the port number the server is listening on.
	 */
	public int getPortNumber()
	{
		return portNumber;
	}
}
