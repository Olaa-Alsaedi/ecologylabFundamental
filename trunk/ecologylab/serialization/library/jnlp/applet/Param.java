/**
 * 
 */
package ecologylab.serialization.library.jnlp.applet;

import ecologylab.serialization.ElementState;

/**
 * @author Zachary O. Toups (toupsz@cs.tamu.edu)
 *
 */
public class Param extends ElementState
{
    @simpl_scalar private String name;
    @simpl_scalar private String value;

    /**
     * 
     */
    public Param()
    {
        super();
    }

}
