package ecologylab.generic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A class that indexes inserted objects by a set of multiple criteria. implementors provide the critera in the form of 
 * ItemIndexer predicates, marshalling happens via .by();
 * @author tom
 *
 */
public abstract class MultiIndexer<IndexedObject> {

	public final class InnerIndexer<IndexedObject>{
		
		Map<String, IndexedObject> ourMap;
		
		public InnerIndexer(Map<String, IndexedObject> theMap)
		{
			this.ourMap = theMap;
		}
		
		public IndexedObject get(String indexString)
		{
			return this.ourMap.get(indexString);
		}
		
	}
	
	private HashMap<String, HashMap<String, IndexedObject>> allmaps;
	private List<IndexedObject> allitems;
	
	public MultiIndexer()
	{
		this.allitems = new ArrayList<IndexedObject>(); // Not sure about this one... may revise. 
		// This is probably better as a set, but I can't decide at the moment. 
		this.allmaps  = new HashMap<String, HashMap<String, IndexedObject>>();
		
		// Initialize all of the maps in the allmaps.
		for(ItemIndexPredicate<IndexedObject> index : this.getIndexPredicates())
		{
			String indexID = index.GetIndexIdentifier();
			
			if(this.allmaps.containsKey(indexID))
			{
				throw new RuntimeException("Should not have multiple indexers of the same identifer, please check your indexers and try again.");
			}else{
				// Put a new map at the approrpiate index. 
				this.allmaps.put(indexID, new HashMap<String, IndexedObject>());
			}
		}
	}
	
	/**
	 * Obtains the list of index predicates that implementors of the MultiIndexer desire.
	 * Guides the indexing process..
	 * This part of code is particularly tricky b/c it automagically grabs the list of relevant indexers from whatever
	 * classes are defined within the given class... thus no need to write this method manually for each class.
	 * Any verbosity that can be eliminated is a godsend in this god-forsaken language of lies. (Java) 
	 * @return 
	 */
	public List<ItemIndexPredicate<IndexedObject>> getIndexPredicates()
	{
		List<ItemIndexPredicate<IndexedObject>> ourList = new LinkedList<>();
		for(Class c : this.getClass().getDeclaredClasses())
		{
			if(c.isAssignableFrom(ItemIndexPredicate.class))
			{
				try {
					ourList.add((ItemIndexPredicate<IndexedObject>) c.newInstance());
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		return ourList;
	}
	
	/**
	 * Inserts an item into the MultiIndexer
	 * @param object
	 */
	public void Insert(IndexedObject object)
	{
		this.allitems.add(object);
		
		for(ItemIndexPredicate<IndexedObject> index : this.getIndexPredicates())
		{
			String indexID = index.GetIndexIdentifier();
			
			String indexValue = index.ObtainIndex(object);
			
			this.allmaps.get(indexID).put(indexValue, object);
		}
	}
	
	/**
	 * Removes a given item from the Multiindexer
	 * @param object
	 */
	public void Remove(IndexedObject object)
	{
		this.allitems.remove(object);
		
		for(ItemIndexPredicate<IndexedObject> index : this.getIndexPredicates())
		{
			String indexID = index.GetIndexIdentifier();
			
			String indexValue = index.ObtainIndex(object);
			
			this.allmaps.get(indexID).remove(indexValue);
		}
	}
	
	public InnerIndexer<IndexedObject> by(String indexID)
	{
		return new InnerIndexer<IndexedObject>(this.allmaps.get(indexID));
	}
}
