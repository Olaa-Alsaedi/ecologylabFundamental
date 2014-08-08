package ecologylab.collections;

import java.util.Collection;
import java.util.Iterator;

import ecologylab.generic.Debug;
import ecologylab.generic.StringBuilderBaseUtils;

/**
 * Prioritized pools are a data structure containing an array of <code>T extends WeightSet</code><br>
 * These sets are ordered in the list by priority. The main function of this data structure is to allow
 * selections of elements from the list of pools, in order of priority. External calls to the pools object
 * can call a maxSelect regardless of what internal weightSet is being called.
 * 
 * @author damaraju
 *
 * @param <T> where T extends WeightSet
 * @param <E> WeightSet is parameterized with E, where E extends SetElement.
 */
public class PrioritizedPool<E extends SetElement>
		extends Debug implements Iterable<E>, Collection<E>
{

	/**
	 * Beware of accesses outside PrioritizedPool. Use with caution.
	 * Ensure that any additions in fact belong to the right WeightSet in this array. 
	 */
	protected WeightSet<E>[] weightSets;

	public PrioritizedPool()
	{
		super();
	}
	public PrioritizedPool(WeightSet<E>[] weightSets)
	{
		this();
		this.weightSets = weightSets;
	}

	public synchronized E maxPeek(int index)
	{
		int thisSize = this.size();
		if(index >= thisSize || thisSize == 0)
			return null;

		
		for(int poolIndex = 0; poolIndex < this.numWeightSets(); poolIndex++)
		{
			WeightSet<E> weightSet = weightSets[poolIndex];
			int weightSetSize = weightSet.size();
			if(index >=  weightSetSize)
			{
				index -= weightSetSize;
				continue; //index is beyond this pool
			}
			return weightSet.maxPeek(index);
		}
		
		//Nothing found :(
		return null;
		
	}
	
	public synchronized E maxSelect(boolean prune)
	{
		if(this.size() == 0)
			return null;
		else 
		{
			//Prune all weightSets if we're in prune mode.
			if(prune)
				for(WeightSet<E> weightSet : weightSets)
						weightSet.prune();
			
			for(WeightSet<E> weightSet : weightSets)
			{
				if(weightSet.isEmpty())
					continue;
				else
					return weightSet.maxSelect();
			}
		}
		return null;
	}

	public synchronized E pruneAndMaxSelect()
	{
		return maxSelect(true);
	}

	public synchronized void clear(boolean doRecycleElements)
	{
		for(WeightSet<E> weightSet : weightSets)
			weightSet.clear(doRecycleElements);
	}

	@Override
	public int size()
	{
		int size = 0;
		for(WeightSet<E> weightSet : weightSets)
			size += weightSet.size();
		return size;
	}
	public int maxSize()
	{
		int result	= 0;
		for (WeightSet<E> weightSet : weightSets)
		{
			int size	= weightSet.size();
			if (size > result)
				result	= size;
		}
		return result;
	}
	/**
	 * Inserts element into a set of the given priority.
	 * @param element
	 * @param priority
	 */
	public synchronized boolean insert(E element, int priority)
	{
		if(priority >= weightSets.length || priority < 0)
			debug("invalid priority for Prioritized Pools (" + weightSets.length + ") : " + priority);
		WeightSet<E> weightSet = weightSets[priority];
		if(weightSet == null)
		{
			debug("no pool exists with priority: " + priority);
			return false;
		}
		return weightSet.insert(element);
	}

	/**
	 * 
	 * @return Mean of weights of the pools
	 */
	public synchronized float mean()
	{
		int poolTotalSize = 0;
		float meanSum 		= 0;
		for(WeightSet<E> weightSet : weightSets)
		{
			int poolSize = weightSet.size();
			meanSum 		+= poolSize * weightSet.mean();
			poolTotalSize += poolSize;
		}
		if (poolTotalSize == 0)
			return 0;
		return meanSum / poolTotalSize;
	}

	public void pause()
	{
		for (WeightSet<E> weightSet : weightSets)
			if(weightSet.isRunnable())
				((RunnablePool) weightSet).pause();
	}

	public void unpause()
	{
		for (WeightSet<E> weightSet : weightSets)
			if(weightSet.isRunnable())
				((RunnablePool) weightSet).unpause();
	}

	public void stop()
	{
		for (WeightSet<E> weightSet : weightSets)
			if(weightSet.isRunnable())
				((RunnablePool) weightSet).stop();
	}

	public void start()
	{
		for(WeightSet<E>  weightSet : weightSets)
			if(weightSet.isRunnable())
				((RunnablePool) weightSet).start();
	}

	public void toggleCollectingAgent()
	{
		for(WeightSet<E>  weightSet : weightSets)
			if(weightSet.isRunnable()) 
				((RunnablePool) weightSet).toggleCollectingAgent();
	}
	
	/**
	 * 
	 * @return Debug output that includes the size of each Set in this pool.
	 */
	public String countsString()
	{
		StringBuilder buffy	= StringBuilderBaseUtils.acquire();
		for(int i = 0; i < weightSets.length; i++)
		{
			buffy.append('[').append(i).append("]: ").append(weightSets[i].size()).append(' ');
		}
		String result	= buffy.toString();
		StringBuilderBaseUtils.release(buffy);
		return result;
	}

	/**
	 * Remove an element from all the Sets in this pool.
	 * @param el
	 */
	public synchronized boolean remove(E el) 
	{
		boolean result	= false;
		for(WeightSet<E>  weightSet : weightSets)
			result			 |= weightSet.remove(el);
		return result;
	}
	
	public WeightSet<E> getWeightSet(int poolNum)
	{
		if (poolNum >= weightSets.length)
			return null;
		return weightSets[poolNum];
	}
	public WeightSet<E>[] getWeightSets()
	{
		return weightSets;
	}
	public int numWeightSets()
	{
		return weightSets.length;
	}

	public boolean isEmpty()
	{
		for(WeightSet<E>  weightSet : weightSets)
			if(!weightSet.isEmpty())
				return false; //If not empty, return false and stop iteration.
		
		return true; //Reaches here only if all weightsets are empty
	}

	public Iterator<E> iterator()
	{
		// TODO Auto-generated method stub
		return new PoolIterator();
	}
	
	final class PoolIterator implements Iterator<E>
	{
		protected Iterator<E>[] weightSetIterators;
		int idx = 0;
		public PoolIterator()
		{
			weightSetIterators = new Iterator[weightSets.length];
			int i=0;
			for(WeightSet<E>  weightSet : weightSets)
				weightSetIterators[i++] = weightSet.iterator();
		}

		public boolean hasNext()
		{
			// no more pools to iterate through
			if (idx >= weightSetIterators.length)
				return false;
			// go to the next pool if this one is empy
			while (!weightSetIterators[idx].hasNext())
				if (++idx >= weightSetIterators.length)
					return false;
			// return hasNext
			return weightSetIterators[idx].hasNext();
		}

		public E next()
		{
			if (idx >= weightSetIterators.length)
				return null;

			E next = weightSetIterators[idx].next();
			if (next == null)
			{
				idx++;
				return next();
			}
			else 
				return next;
		}

		public void remove()
		{
			// does nothing
		}

	}

	@Override
	public boolean add(E e)
	{
		return insert(e, 0);
	}

	@Override
	public boolean addAll(Collection<? extends E> c)
	{
		boolean result	= false;
		for (E element : c)
			result	|= add(element);
		return result;
	}

	@Override
	public void clear()
	{
		clear(false);
	}

	@Override
	public boolean contains(Object o)
	{
		for (WeightSet<E> weightSet : weightSets)
		{
			if (weightSet.contains(o))
				return true;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c)
	{
		boolean result	= false;
		for (Object element : c)
		{
			for (WeightSet<E> weightSet : weightSets)
			{
				if (!weightSet.contains(element))
					return false;
			}
		}
		return true;
	}

	@Override
	public boolean remove(Object arg0)
	{
		return remove((E) arg0);
	}

	@Override
	public boolean removeAll(Collection<?> c)
	{
		boolean result	= false;
		for (Object o : c)
			result |= remove(o);
		return result;
	}

	@Override
	public boolean retainAll(Collection<?> c)
	{
		throw new RuntimeException("not implemented.");
	}

	@Override
	public Object[] toArray()
	{
		E[] result	= (E[]) new SetElement[size()];
		toArray(result);		
		return result;
	}

	/**
	 * @param result
	 */
	protected void toArray(E[] result)
	{
		int start		= 0;
		for (WeightSet<E> weightSet : weightSets)
		{
			weightSet.toArray(result, start);
			start		 += weightSet.size();
		}
	}

	@Override
	public <T> T[] toArray(T[] input)
	{
		E[] result	= (E[]) input;
		if (result.length < size())
			result	= (E[]) toArray();
		return (T[]) result;
	}
}