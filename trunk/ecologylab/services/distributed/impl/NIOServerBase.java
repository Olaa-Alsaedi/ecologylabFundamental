/**
 * 
 */
package ecologylab.services.distributed.impl;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;

import ecologylab.appframework.ObjectRegistry;
import ecologylab.generic.Debug;
import ecologylab.generic.StartAndStoppable;
import ecologylab.net.NetTools;
import ecologylab.services.distributed.common.SessionObjects;
import ecologylab.services.distributed.server.NIOServerFrontend;
import ecologylab.services.distributed.server.contextmanager.AbstractContextManager;
import ecologylab.services.messages.InitConnectionRequest;
import ecologylab.xml.TranslationSpace;

/**
 * Combines an NIOServerBackend and NIOServerFrontend
 * 
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 * 
 */
public abstract class NIOServerBase extends Debug implements NIOServerFrontend, Runnable, StartAndStoppable,
		SessionObjects, Shutdownable
{
	private NIOServerBackend	backend;

	protected TranslationSpace	translationSpace;

	protected ObjectRegistry	registry;

	/**
	 * @return the backend
	 */
	public NIOServerBackend getBackend()
	{
		return backend;
	}

	/**
	 * @return the registry
	 */
	public ObjectRegistry getRegistry()
	{
		return registry;
	}

	/**
	 * @return the translationSpace
	 */
	public TranslationSpace getTranslationSpace()
	{
		return translationSpace;
	}

	/**
	 * Creates an instance of an NIOServer of some flavor. Creates the backend using the information in the arguments.
	 * 
	 * Registers itself as the MAIN_START_AND_STOPPABLE in the object registry.
	 * 
	 * @param portNumber
	 * @param inetAddress
	 * @param translationSpace
	 * @param objectRegistry
	 * @throws IOException
	 * @throws BindException
	 */
	protected NIOServerBase(int portNumber, InetAddress[] inetAddress, TranslationSpace requestTranslationSpace,
			ObjectRegistry objectRegistry, int idleConnectionTimeout) throws IOException, BindException
	{
		backend = this.generateBackend(portNumber, inetAddress,
				composeTranslations(portNumber, inetAddress[0], requestTranslationSpace), objectRegistry,
				idleConnectionTimeout);
		
		debug("setting up NIO Server...");

		// we get these from the backend, because it ensures that they are configured if they are passed in null
		this.translationSpace = backend.translationSpace;
		this.registry = backend.objectRegistry;

		this.registry.registerObject(MAIN_START_AND_STOPPABLE, this);
		this.registry.registerObject(MAIN_SHUTDOWNABLE, this);
	}

	static final Class[]	OUR_TRANSLATIONS	=
														{ InitConnectionRequest.class, };
	
	public static TranslationSpace composeTranslations(int portNumber, InetAddress inetAddress,
			TranslationSpace requestTranslationSpace)
	{
		return composeTranslations(OUR_TRANSLATIONS, "nio_server_base: ", portNumber, inetAddress.toString(),
				requestTranslationSpace);
	}

	public static TranslationSpace composeTranslations(Class[] newTranslations, String prefix, int portNumber,
			String inetAddress, TranslationSpace requestTranslationSpace)
	{
		return TranslationSpace.get(prefix + inetAddress.toString() + ":" + portNumber, newTranslations,
				requestTranslationSpace);
	}

	/**
	 * Creates an instance of an NIOServer of some flavor. Creates the backend using the information in the arguments.
	 * 
	 * Registers itself as the MAIN_START_AND_STOPPABLE in the object registry.
	 * 
	 * @param portNumber
	 * @param inetAddress
	 * @param translationSpace
	 * @param objectRegistry
	 * @throws IOException
	 * @throws BindException
	 */
	protected NIOServerBase(int portNumber, InetAddress inetAddress, TranslationSpace requestTranslationSpace,
			ObjectRegistry objectRegistry, int idleConnectionTimeout) throws IOException, BindException
	{
		this(portNumber, NetTools.wrapSingleAddress(inetAddress), requestTranslationSpace, objectRegistry, idleConnectionTimeout);
	}

	protected NIOServerBackend generateBackend(int portNumber, InetAddress[] inetAddresses,
			TranslationSpace requestTranslationSpace, ObjectRegistry objectRegistry, int idleConnectionTimeout)
			throws BindException, IOException
	{
		return NIOServerBackend.getInstance(portNumber, inetAddresses, this, requestTranslationSpace, objectRegistry,
				idleConnectionTimeout);
	}

	protected abstract AbstractContextManager generateContextManager(Object token, SocketChannel sc,
			TranslationSpace translationSpace, ObjectRegistry registry);

	/**
	 * @see ecologylab.generic.StartAndStoppable#start()
	 */
	public void start()
	{
		backend.start();
	}

	/**
	 * @see ecologylab.generic.StartAndStoppable#stop()
	 */
	public void stop()
	{
		backend.stop();
	}
}
