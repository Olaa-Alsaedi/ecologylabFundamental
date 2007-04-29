/**
 * The Assets class is used to manage cachable assets.
 */
package ecologylab.io;

import java.io.File;

import ecologylab.appframework.ApplicationProperties;
import ecologylab.appframework.PropertiesAndDirectories;
import ecologylab.appframework.StatusReporter;
import ecologylab.appframework.types.AssetsState;
import ecologylab.generic.Debug;
import ecologylab.generic.Generic;
import ecologylab.net.ParsedURL;

/**
 * Used to manage cachable assets
 * 
 * @author blake
 */
public class Assets
extends Debug
implements ApplicationProperties
{
	/**
	 * current known assets (although arbitrary ones can exist)
	 */
	public static final String INTERFACE		= "interface/";
	public static final String SEMANTICS		= "semantics/";
	public static final String PREFERENCES		= "preferences/";
	
	/**
	 * Source URL root of the tree of assets for this application.
	 * Default is the configDir(), which in turn is the config subdir of codebase.

	 * 
	 * The source location of any asset is specified relative to here.
	 */
	static ParsedURL	assetsRoot;
	
	/**
	 * Source URL root of the tree of interface assets for this application.
	 * This should always be set to the INTERFACE subdir of the assetsRoot.
	 * 
	 * The source location of any interface asset is specified relative to here.
	 */
	static ParsedURL	interfaceAssetsRoot;
	
	/**
	 * Source URL root of the tree of semantics assets for this application.
	 * This should always be set to the SEMANTICS subdir of the assetsRoot.
	 * 
	 * The source location of any semantics asset is specified relative to here.
	 */
	static ParsedURL	semanticsAssetsRoot;
	
	/**
	 * Source URL root of the tree of preferences assets for this application.
	 * This should always be set to the PREFERENCES subdir of the assetsRoot.
	 * 
	 * The source location of any preferences asset is specified relative to here.
	 */
	static ParsedURL	preferencesAssetsRoot;
	
/**
 * The root directory on the local machine where assets will be stored (cached).
 * 
 * The cache destination  of any asset is applied relative to here.
 */
	static File			cacheRoot;
	
	/**
	 * The root directory on the local machine where interface assets will be stored
	 * (cached). 
	 * This should always be set to the INTERFACE subdir of the cacheRoot.
	 * 
	 * The location of any interface asset is specified relative to here.
	 */
	static File			interfaceCacheRoot;
	
	/**
	 * The root directory on the local machine where semantics assets will be stored
	 * (cached). 
	 * This should always be set to the SEMANTICS subdir of the cacheRoot.
	 * 
	 * The location of any semantics asset is specified relative to here.
	 */
	static File			semanticsCacheRoot;
	
	static File			preferencesCacheRoot;

	static
	{
		setAssetsRoot(Generic.configDir());
		setCacheRoot(PropertiesAndDirectories.thisApplicationDir());
	}
	//////////////////////////////////////////////////////////////
	
	/**
	 * No instances possible, static references only.
	 */
	private Assets() {}
	
	/**
	 * Given a relative path, return a file reference to this path
	 * from the cache root.
	 * 
	 * @param relativePath	A string representing the relative file path.
	 * @return	A file reference to the requested path
	 */
	public static File getAsset(String relativePath)
	{
		if (cacheRoot == null)
			return null;
		
		return new File(cacheRoot.getAbsolutePath() + File.separatorChar + relativePath);
	}
	
	/**
	 *  Same as getAsset(String), but allows additional relative file/directory 
	 *  to be specified against the relativePath
	 *  
	 * @param relativePath A string representing the relative file path
	 * @param additionalContext A string representing an additional relative path.
	 * This path is relative to the relativePath parameter (rather than the cache root).
	 * @return	A file reference to the requested path
	 */
	public static File getAsset(String relativePath, String additionalContext)
	{
		if (cacheRoot == null)
			return null;
		
		return new File(getAsset(relativePath), additionalContext);
	}
	
	/**
	 * Same as getAsset(String), but creates the Asset location if it
	 * doesn't exist
	 * 
	 * @param relativePath	A string representing the relative file path. 
	 * @return	A file reference tot he requested path
	 */
	public static File getAndPerhapsCreateAsset(String relativePath)
	{
		File theAsset = getAsset(relativePath);
		
		if (!theAsset.exists())
			theAsset.mkdirs();
		
		return theAsset;
	}
	
	/**
	 * Same as getAndPerhapsCreateAsset(String, String), but creates the Asset location if it
	 * doesn't exist
	 * 
	 * @param relativePath	A string representing the relative file path. 
	 * @param additionalContext A string representing an additional relative path.
	 * This path is relative to the relativePath parameter (rather than the cache root).
	 * 
	 * @return	A file reference to the requested path
	 * @see #getAsset(String, String)
	 */
	public static File getAndPerhapsCreateAsset(String relativePath, String additionalContext)
	{
		File theAsset = getAsset(relativePath, additionalContext);
		
		if (!theAsset.exists())
			theAsset.mkdirs();
		
		return theAsset;
	}
	/**
	 * 
	 * @param assetRelativePath
	 * @return
	 */
	public static File getInterfaceFile(String assetRelativePath)
	{
		//FIXME need to make sure zip has been downloaded here
		// if not, initiate download & wait for it!
		return getCachedInterfaceFile(assetRelativePath);		
	}
	/**
	 * Use the interfaceCacheRoot to produce a File object using the specified relative path.
	 * 
	 * @param assetRelativePath
	 * @return
	 */
	public static File getCachedInterfaceFile(String assetRelativePath)
	{
		return Files.newFile(interfaceCacheRoot, assetRelativePath);
	}
	/**
	 * 
	 * @param assetRelativePath
	 * @return
	 */
	public static File getSemanticsFile(String assetRelativePath)
	{
		//FIXME need to make sure zip has been downloaded here
		// if not, initiate download & wait for it!
		return getCachedSemanticsFile(assetRelativePath);		
	}
	/**
	 * Use the interfaceCacheRoot to produce a File object using the specified relative path.
	 * 
	 * @param assetRelativePath
	 * @return
	 */
	protected static File getCachedSemanticsFile(String assetRelativePath)
	{
		return Files.newFile(semanticsCacheRoot, assetRelativePath);
	}
	
	public static File getPreferencesFile(String assetRelativePath)
	{
		return getCachedPreferencesFile(assetRelativePath);
	}
	/**
	 * Use the interfaceCacheRoot to produce a File object using the specified relative path.
	 * 
	 * @param assetRelativePath
	 * @return
	 */
	protected static File getCachedPreferencesFile(String assetRelativePath)
	{
		return Files.newFile(preferencesCacheRoot, assetRelativePath);
	}
	
	/**
	 * Download an interface assets zip file from the interfaceAssetsRoot.
	 * Unzip it into the cacheRoot.
	 * 
	 * @param assetRelativePath -- This is the name of the interface. It does not end in .zip!
	 * @param status	Provide feedback to the user at the bottom of a window, or such.
	 * 
	 * @return	false if the assetRelativePath is null; otherwise true.
	 */
	public static boolean downloadInterfaceZip(String assetRelativePath, StatusReporter status,
											   boolean forceDownload)
	{
		return downloadInterfaceZip(assetRelativePath, status, forceDownload, AssetsState.IGNORE_VERSION);
	}
	public static boolean downloadInterfaceZip(String assetRelativePath, StatusReporter status,
											   boolean forceDownload, float version)
	{
		if (assetRelativePath == null)
			return false;
		
		downloadZip(interfaceAssetsRoot.getRelative(assetRelativePath + ".zip", "forming zip location"), 
				interfaceCacheRoot, status, forceDownload, version);
		
		return true;
	}
	
	/**
	 * Download an semantics assets zip file from the semanticsAssetsRoot.
	 * Unzip it into the cacheRoot.
	 * 
	 * @param assetRelativePath
	 * @param status	Provide feedback to the user at the bottom of a window, or such.
	 */
	public static void downloadSemanticsZip(String assetRelativePath, StatusReporter status,
										    boolean forceDownload)
	{
		downloadSemanticsZip(assetRelativePath, status, forceDownload, AssetsState.IGNORE_VERSION);
	}
	public static void downloadSemanticsZip(String assetRelativePath, StatusReporter status,
											boolean forceDownload, float version)
	{
		downloadZip(semanticsAssetsRoot.getRelative(assetRelativePath + ".zip", "forming zip location"), 
					semanticsCacheRoot, status, forceDownload, version);
	}
	
	public static void downloadPreferencesZip(String assetRelativePath, StatusReporter status,
											  boolean forceDownload)
	{
		downloadZip(preferencesAssetsRoot.getRelative(assetRelativePath + ".zip", "forming zip location"),
					preferencesCacheRoot, status, forceDownload, AssetsState.IGNORE_VERSION);
	}
	/**
	 * Download the assets zip file from the assetsRoot.
	 * Unzip it into the cacheRoot.
	 * 
	 * @param assetRelativePath
	 * @param status	Provide feedback to the user at the bottom of a window, or such.
	 */
	public static void downloadZip(String assetRelativePath, StatusReporter status,
								   boolean forceDownload, float version)
	{
		downloadZip(assetsRoot.getRelative(assetRelativePath, "forming zip location"), 
					Files.newFile(cacheRoot, assetRelativePath), status, forceDownload, version);
	}
	/**
	 * Download the assets zip file from the assetsRoot.
	 * Unzip it into the cacheRoot.
	 * 
	 * Don't bother providing feedback to the user.
	 * 
	 * @param assetRelativePath
	 */
	public static void downloadZip(String assetRelativePath,
								   boolean forceDownload, float version)
	{
		downloadZip(assetsRoot.getRelative(assetRelativePath, "forming zip location"), 
					Files.newFile(cacheRoot, assetRelativePath), null, forceDownload, version);
	}
	
	public static void downloadZip(ParsedURL sourceZip, File targetFile,
								   boolean forceDownload, float version)
	{
		downloadZip(sourceZip, targetFile, null, forceDownload, version);
	}
	/**
	 * Download and uncompress a zip file from a source to a target location with minimal effort,
	 * unless the zip file already exists at the target location, in which case, 
	 * do nothing.
	 * 
	 * @param status		The Status object that provides a source of state change visiblity;
	 * 						can be null.
	 * @param forceDownload
	 * @param version
	 */
	public static void downloadZip(ParsedURL sourceZip, File targetDir, StatusReporter status, boolean forceDownload, float version)
	{
		String zipFileName	= sourceZip.url().getFile();
		int lastSlash		= zipFileName.lastIndexOf('\\');
		if (lastSlash == -1)
			lastSlash		= zipFileName.lastIndexOf('/');
		
		zipFileName			= zipFileName.substring(lastSlash+1);
		File zipFileDestination	= Files.newFile(targetDir, zipFileName);
		if (forceDownload || !zipFileDestination.canRead() || !AssetsState.localVersionIsUpToDate(zipFileName, version))
		{
			ZipDownload downloadingZip	= ZipDownload.downloadAndPerhapsUncompress(sourceZip, targetDir, status, true);
			if (downloadingZip != null) // null if already available locally or error
			{
				downloadingZip.waitForDownload();
			}
		}
		else
			println("Using cached " + zipFileDestination);
	}	

	/**
	 * Set the source URL root of the tree of assets for this application.
	 * Default is the configDir(), which in turn is the config subdir of codebase.
	 * 
	 * The source location of any asset is specified relative to here.
	 */
	public static void setAssetsRoot(ParsedURL assetsRoot)
	{
		Assets.assetsRoot 		= assetsRoot;
		interfaceAssetsRoot		= assetsRoot.getRelative(INTERFACE, "forming interface assets root");
		semanticsAssetsRoot		= assetsRoot.getRelative(SEMANTICS, "forming semantics assets root");
		preferencesAssetsRoot	= assetsRoot.getRelative(PREFERENCES, "forming preferences assets root");
	}

	/**
	 * Sets the root file path for caching. Assets are specified relative to this path.
	 * @param cacheRoot The root file path for caching assets.
	 */
	public static void setCacheRoot(File cacheRoot)
	{
		Assets.cacheRoot		= cacheRoot;
		interfaceCacheRoot		= Files.newFile(cacheRoot, INTERFACE);
		semanticsCacheRoot		= Files.newFile(cacheRoot, SEMANTICS);
		preferencesCacheRoot	= Files.newFile(cacheRoot, PREFERENCES);
	}

	/**
	 * Get the source URL root of the tree of assets for this application.
	 * Default is the configDir(), which in turn is the config subdir of codebase.
	 * 
	 * @return	ParsedURL referring to the root of the remote place we download assets from.
	 */
	public static ParsedURL assetsRoot() 
	{
		return assetsRoot;
	}

	/**
	 * Get the root file path for caching. Assets are specified relative to this path.
	 * @return
	 */
	public static File cacheRoot() 
	{
		return cacheRoot;
	}

	/**
	 * Download XML from the sourcePath, within the assetsRoot (the application's config dir),
	 * to the target path within the applicationDir.
	 * 
	 * @param sourcePath
	 * @param targetPath
	 * @param status
	 */
	public static void downloadXML(String sourcePath, String targetPath, StatusReporter status)
	{
		File targetDir	= cacheRoot();
		if ((targetPath != null) && (targetPath.length() > 0))
			targetDir	= Files.newFile(targetDir, targetPath);
		
		downloadXML(assetsRoot().getRelative(sourcePath, "forming Asset path location"), 
					targetDir, status);

	}	
	/**
	 * Download an XML file from a source to a target location with minimal effort,
	 * unless the XML file already exists at the target location, in which case, 
	 * do nothing.
	 * @param status The Status object that provides a source of state change visiblity;
	 * can be null.
	 * @param sourceXML The location of the zip file to download and uncompress.
	 * @param targetDir The location where the zip file should be uncompressed. This
	 * directory structure will be created if it doesn't exist.
	 */
	public static void downloadXML(ParsedURL sourceXML, File targetDir, StatusReporter status)
	{
		String xmlFileName	= sourceXML.url().getFile();
		int lastSlash		= xmlFileName.lastIndexOf('\\');
		if (lastSlash == -1)
			lastSlash		= xmlFileName.lastIndexOf('/');
	
		xmlFileName			= xmlFileName.substring(lastSlash+1);
		File xmlFileDestination	= Files.newFile(targetDir, xmlFileName);
	
		if (!xmlFileDestination.canRead())
		{
			//we just want to download it, not uncompress it... (using code from zip downloading stuff)
			ZipDownload downloadingZip	= ZipDownload.downloadAndPerhapsUncompress(sourceXML, targetDir, status, false);
			if (downloadingZip != null) // null if already available locally or error
			{
				downloadingZip.waitForDownload();
			}
		}
		else
			println("Using cached " + xmlFileDestination);
	}
}
