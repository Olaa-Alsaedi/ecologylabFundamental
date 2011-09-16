/**
 * 
 */
package ecologylab.io;

import java.io.File;

import ecologylab.appframework.Environment;
import ecologylab.net.ParsedURL;

/**
 * @author andrew
 * 
 */
public class AssetsRoot
{
	private ParsedURL	assetRoot;

	private File			cacheRoot;

	public AssetsRoot(ParsedURL assetRoot, File cacheRoot)
	{
		this.assetRoot = assetRoot;
		this.cacheRoot = cacheRoot;
	}

	/**
	 * Create new AssetsRoot object which stores locations for remote and cached assets.
	 * 
	 * @param relativePath
	 *          Relative path to assets from root.
	 * @param cacheDir
	 *          Directory where assets are cached. If null, uses Assets.cacheRoot.
	 */
	public AssetsRoot(String relativePath, File cacheDir)
	{
		this(	Assets.assetsRoot().getRelative(relativePath, "forming assets root"),
					Files.newFile((cacheDir == null) ? Assets.cacheRoot() : cacheDir, relativePath));
	}

	/**
	 * Create new AssetsRoot object which stores locations for remote and cached assets.
	 * 
	 * @param the
	 *          Environment for which to retrieve the path.
	 * @param relativePath
	 *          Relative path to assets from root.
	 * @param cacheDir
	 *          Directory where assets are cached. If null, uses Assets.cacheRoot.
	 */
	public AssetsRoot(Environment e, String relativePath, File cacheDir)
	{
		this(	Assets.assetsRoot(e).getRelative(relativePath, "forming assets root"),
					Files.newFile((cacheDir == null) ? Assets.cacheRoot() : cacheDir, relativePath));
	}

	public ParsedURL getAssetRoot()
	{
		return assetRoot;
	}

	public File getCacheRoot()
	{
		return cacheRoot;
	}

}
