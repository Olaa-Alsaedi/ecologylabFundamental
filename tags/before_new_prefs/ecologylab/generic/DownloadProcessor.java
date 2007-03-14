package ecologylab.generic;


/**
 * Interface to a module that performs downloads, perhaps concurrently.
 * A wrapper for DownloadMonitor, for example.
 *
 * @author andruid
 */
public interface DownloadProcessor
{
	public void stop();
	
/**
 * Download the Downloadable, perhaps concurrently.
 * If concurrently, call the dispatchTarget.delivery(Object) when done.
 * 
 * @param thatDownloadable
 * @param dispatchTarget
 */
	public void download(Downloadable thatDownloadable,
			DispatchTarget dispatchTarget);
}
