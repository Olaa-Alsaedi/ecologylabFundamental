package ecologylab.xml.geom;

import java.awt.geom.Point2D;

import ecologylab.xml.ElementState;

/**
 * Encapsulates a Point2D.Double for use in translating to/from XML.
 * 
 * ***WARNING!!!***
 * 
 * Performing transformations (such as setLocation()) on the result of getPoint() will cause this
 * object to become out of synch with its underlying Point2D. DO NOT DO THIS!
 * 
 * If other transformation methods are required, either notify me, or implement them yourself. :D
 * 
 * Accessor methods (such as contains()) on the result of getPoint() are fine.
 * 
 * @author Zach Toups (toupsz@gmail.com)
 */
public class Point2DDoubleState extends ElementState
{
    private Point2D.Double point = new Point2D.Double();

    /**
     * Location and dimensions of the point.
     */
    public double          x     = 0;

    public double          y     = 0;

    public Point2DDoubleState()
    {
        super();
    }

    public Point2DDoubleState(double x, double y)
    {
        setLocation(x, y);
    }

    public void setLocation(double x, double y)
    {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns an Ellipse2D object represented by this.
     */
    public Point2D.Double point()
    {
        if (point == null)
        {
            point = new Point2D.Double(x, y);
        }
        else if (point.x != x || point.y != y)
        {
            point.setLocation(x, y);
        }

        return point;
    }
}