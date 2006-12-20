package ecologylab.xml.subelements;

import java.util.Collection;
import java.util.Vector;

import ecologylab.xml.ElementState;

/**
 * An ElementState XML tree node for collecting a set of nested elements, using a Vector
 * (synchronized).
 * <p/> In general, one should use {@link ArrayListState ArrayListState}
 * for this kind of functionality, but, in some cases, there may be concurrency
 * issues, in which case, this more expensive class will be required.
 * 
 * @author andruid
 */
public class VectorState extends ElementState
{
    public Vector set = new Vector();

    public VectorState()
    {
        super();
    }

    public void add(ElementState elementState)
    {
    	set.add(elementState);
    }

    /**
     * Return the collection object associated with this
     * 
     * @return	The ArrayList we collect in.
     */
	protected Collection getCollection(Class thatClass)
	{
		return set;
	}
	
    /**
     * Remove all elements from our Collection.
     * 
     */
    public void clear()
    {
        set.clear();
    }

    /**
     * Get the number of elements in the set.
     * 
     * @return
     */
    public int size()
    {
        return set.size();
    }
}
