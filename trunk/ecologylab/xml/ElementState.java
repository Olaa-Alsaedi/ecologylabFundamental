package ecologylab.xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import ecologylab.generic.Debug;
import ecologylab.generic.ReflectionTools;
import ecologylab.generic.StringInputStream;
import ecologylab.net.PURLConnection;
import ecologylab.net.ParsedURL;
import ecologylab.xml.types.element.Mappable;
import ecologylab.xml.types.scalar.ScalarType;
import ecologylab.xml.types.scalar.TypeRegistry;

/**
 * This class is the heart of the <code>ecologylab.xml</code>
 * translation framework.
 * 
 * <p/>
 * To use the framework, the programmer must define a tree of objects derived
 * from this class. The public fields in each of these derived objects 
 * correspond to the XML DOM. The declarations of attribute fields  must 
 * preceed thos for nested XML elements. Attributes are built directly from
 * Strings, using classes derived from
 * @link ecologylab.types.Type ecologylab.types.Type}.
 *
 * <p/>
 * The framework proceeds automatically through the application of rules.
 * In the standard case, the rules are based on the automatic mapping of
 * XML element names (aka tags), to ElementState class names.
 * An mechanism for supplying additional translations may also be provided.
 * 
 * <p/>
 * <code>ElementState</code> is based on 2 methods, each of which employs 
 * Java reflection and recursive descent.
 * 
 * <li><code>translateToXML(...)</code> translates a tree of these 
 * <code>ElementState</code> objects into XML.</li>
 *
 * <li><code>translateFromXML(...)</code> translates an XML DOM into a tree of these
 * <code>ElementState</code> objects</li>
 *  
 * @author      Andruid Kerne
 * @author      Madhur Khandelwal
 * @version     2.9
 */
public class ElementState extends Debug
implements OptimizationTypes, XmlTranslationExceptionTypes
{
	/**
	 * Link for a DOM tree.
	 */
	ElementState				parent;
/**
 * Enables storage of a single text node child.
 * This facility is meager and rarely used, since
 * the leaf nodes facility does the same thing but better.
 * <p/>
 * We might want to implement the ability to store multiple text nodes
 * here some time in the future.
 */	
	private StringBuilder		textNodeBuffy;
	
	/**
	 * Just-in time look-up tables to make translation be efficient.
	 * Allocated on a per class basis.
	 */
	Optimizations				optimizations;
	
	/**
	 * Use for resolving getElementById()
	 */
	HashMap<String, ElementState>						elementByIdMap;
	
	HashMap<String, ElementState>						nestedNameSpaces;

    static final HashMap		fieldsForClassMap	= new HashMap();

	
	public static final int 	UTF16_LE	= 0;
	public static final int 	UTF16		= 1;
	public static final int 	UTF8		= 2;
	
	/**
	 * These are the styles for declaring fields as translated to XML.
	 *
	 * @author andruid
	 */
	public enum DeclarationStyle { ANNOTATION, TRANSIENT, PUBLIC};
	
	private static DeclarationStyle	declarationStyle	= DeclarationStyle.ANNOTATION;
	
	/**
	 * xml header
	 */
	static protected final String XML_FILE_HEADER = "<?xml version=" + "\"1.0\"" + " encoding=" + "\"UTF-8\"" + "?>\n";
//	static protected final String XML_FILE_HEADER = "<?xml version=" + "\"1.0\"" + " encoding=" + "\"US-ASCII\"" + "?>";
	
	static protected final int	ESTIMATE_CHARS_PER_FIELD	= 80;

	static final int 			TOP_LEVEL_NODE		= 1;
	
	private static final TranslationSpace globalTranslationSpace	= TranslationSpace.get("global");
	
/**
 * Used for argument marshalling with reflection to access 
 * a set method that takes a String as an argument.
 */
	protected static Class[] 	MARSHALLING_PARAMS	= {String.class};

    /**
     * Constant indicating that floating precision cutoff is disabled. If floatingPrecision is set
     * to this value, then all available decimal places will be emitted.
     */
    public static final short             FLOATING_PRECISION_OFF   = -1;

    /**
     * Indicates how many digits after the decimal will be emitted on all floating values (floats
     * and doubles). If set to FLOATING_PRECISION_OFF (the default value), nothing will be done.
     */
    private short                         floatingPrecision = FLOATING_PRECISION_OFF;
    
    /**
     * Construct. Create a link to a root optimizations object.
     */
	public ElementState()
	{
		optimizations			= Optimizations.lookupRootOptimizations(this);		
	}
/**
 * Emit XML header, then the object's XML.
 */
	public String translateToXMLWithHeader(boolean compression) throws XmlTranslationException
	{
	   return XML_FILE_HEADER + translateToXML(compression);
	}

	/**
	 * Translates a tree of ElementState objects into an equivalent XML string.
	 * 
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * Note: in the declaration of <code>this</code>, all nested elements 
	 * must be after all attributes.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @return 							the generated xml string
	 * 
	 * @throws XmlTranslationException if there is a problem with the 
	 * structure. Specifically, in each ElementState object, fields for 
	 * attributes must be declared
	 * before all fields for nested elements (those derived from ElementState).
	 * If there is any public field which is not derived from ElementState
	 * declared after the declaration for 1 or more ElementState instance
	 * variables, this exception will be thrown.
	 */
	public String translateToXML() throws XmlTranslationException
	{
		StringBuilder buffy	= translateToXML((StringBuilder)null);
		return (buffy == null) ? "" : buffy.toString();
	}
	
	/**
	 * Translates a tree of ElementState objects into an equivalent XML string.
	 * 
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * Note: in the declaration of <code>this</code>, all nested elements 
	 * must be after all attributes.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param compression				if the emitted xml needs to be compressed
	 * 
	 * @return 							the generated xml string
	 * 
	 * @throws XmlTranslationException if there is a problem with the 
	 * structure. Specifically, in each ElementState object, fields for 
	 * attributes must be declared
	 * before all fields for nested elements (those derived from ElementState).
	 * If there is any public field which is not derived from ElementState
	 * declared after the declaration for 1 or more ElementState instance
	 * variables, this exception will be thrown.
	 */
	public String translateToXML(boolean compression) throws XmlTranslationException
	{
		//nodeNumber is just to indicate which node number(#1 is the root node of the DOM)
		//is being processed. compression attr is emitted only for node number 1
		return translateToXML(compression, true);
	}
	
	/**
	 * Translates a tree of ElementState objects into an equivalent XML string.
	 * 
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements
	 * -- if doRecursiveDescent is true).
	 * <p/>
	 * Note: in the declaration of <code>this</code>, all nested elements 
	 * must be after all attributes.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param compression				if the emitted xml needs to be compressed
	 * @param doRecursiveDescent		true for recursive descent parsing.
	 * 									false to parse just one level of attributes.
	 * 										In this case, only the open tag w attributes is generated.
	 * 										There is no close.
	 * 
	 * @return 							the generated xml string
	 * 
	 * @throws XmlTranslationException if there is a problem with the 
	 * structure. Specifically, in each ElementState object, fields for 
	 * attributes must be declared
	 * before all fields for nested elements (those derived from ElementState).
	 * If there is any public field which is not derived from ElementState
	 * declared after the declaration for 1 or more ElementState instance
	 * variables, this exception will be thrown.
	 */
	public String translateToXML(boolean compression, boolean doRecursiveDescent) throws XmlTranslationException
	{
		StringBuilder buffy	= translateToXML((StringBuilder)null);
		return (buffy == null) ? "" : buffy.toString();
	}

/**
 * Allocated a StringBuilder for translateToXML(), based on a rough guess of how many fields there are to translate.
 * @return
 */
	private StringBuilder allocStringBuilder()
	{
		ArrayList<Field> attributeFields	= optimizations.attributeFields();
		int numAttributes 					= attributeFields.size();
		int	numFields						= numAttributes + optimizations.quickNumElements();

		return new StringBuilder(numFields * ESTIMATE_CHARS_PER_FIELD);
	}
	/**
	 * Translates a tree of ElementState objects into equivalent XML in a StringBuilder.
	 * 
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements
	 * -- if doRecursiveDescent is true).
	 * <p/>
	 * Note: in the declaration of <code>this</code>, all nested elements 
	 * must be after all attributes.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param buffy 				StringBuilder to translate into, or null if you want one created for you.
	 * 
	 * @return 						the generated xml string
	 * 
	 * @throws XmlTranslationException if a problem arises during translation.
	 * Problems with Field access are possible, but very unlikely.
	 */	
	public StringBuilder translateToXML(StringBuilder buffy) 
	throws XmlTranslationException
	{
		if (buffy == null)
	        buffy = allocStringBuilder();
		Class rootClass	= optimizations.thatClass;

		translateToXMLBuilder(rootClass, optimizations.rootFieldToXMLOptimizations(rootClass), buffy);
		
		return buffy;
	}
	
	/**
	 * Translates a tree of ElementState objects, and writes the output to the File passed in.
	 * <p/>
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * <p/>
	 * Makes directories if necessary.
	 * 
	 * @param outputFile		File to write the XML to.
	 * 
	 * @throws XmlTranslationException if a problem arises during translation. 
	 * Problems with Field access are possible, but very unlikely.
	 * @throws IOException		If there are problems with the file.
	 */
	public void translateToXML(File outputFile)
	throws XmlTranslationException, IOException
	{
		outputFile.mkdirs();
		
		BufferedWriter bufferedWriter	= new BufferedWriter(new FileWriter(outputFile));
		translateToXML(bufferedWriter);
		bufferedWriter.close();
	}

	/**
	 * Translates a tree of ElementState objects, and writes the output to the Appendable passed in.
	 * <p/>
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param appendable		Appendable to translate into. Must be non-null. Can be a Writer, OutputStream, ...
	 * 
	 * @throws XmlTranslationException if a problem arises during translation.
	 * The most likely cause is an IOException.

	 * <p/>
	 * Problems with Field access are possible, but very unlikely.
	 */
	public void translateToXML(Appendable appendable) 
	throws XmlTranslationException
	{
		if (appendable == null)
	        throw new XmlTranslationException("Appendable is null");
	
		try
		{
			Class rootClass = optimizations.thatClass;
			translateToXMLAppendable(rootClass, optimizations.rootFieldToXMLOptimizations(rootClass), appendable);
		} catch (IOException e)
		{
			throw new XmlTranslationException("IO", e);
		}
	}

	/**
	 * Translates a tree of ElementState objects into an equivalent XML string.
	 * 
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements
	 * -- if doRecursiveDescent is true).
	 * <p/>
	 * Note: in the declaration of <code>this</code>, all nested elements 
	 * must be after all attributes.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @return 						the generated xml string
	 * 
	 * @throws XmlTranslationException if a problem arises during translation.
	 * Problems with Field access are possible, but very unlikely.
	 */
	private void translateToXMLBuilder(Class thatClass, FieldToXMLOptimizations fieldToXMLOptimizations, StringBuilder buffy)
	throws XmlTranslationException
	{
        this.preTranslationProcessingHook();
		
        ArrayList<FieldToXMLOptimizations> attributeF2XOs	= optimizations.attributeFieldOptimizations();

		buffy.append(fieldToXMLOptimizations.startOpenTag());

		int numAttributes 			= attributeF2XOs.size();
		if (numAttributes > 0)
		{
			try
			{
				for (int i=0; i<numAttributes; i++)
				{
					// iterate through fields
					FieldToXMLOptimizations childF2Xo	= attributeF2XOs.get(i);
					childF2Xo.appendValueAsAttribute(buffy, this);
				}
			} catch (Exception e)
			{
				// IllegalArgumentException, IllegalAccessException
				throw new XmlTranslationException("TranslateToXML for attribute " + this, e);
			}
		}
		//ArrayList<Field> elementFields		= optimizations.elementFields();
		ArrayList<FieldToXMLOptimizations> elementF2XOs	= optimizations.elementFieldOptimizations();
		int numElements						= elementF2XOs.size();

		StringBuilder textNode = this.textNodeBuffy;
		//TODO -- fix textNode == null -- should be size() == 0 or some such
		if ((numElements == 0) && (textNode == null))
		{
			buffy.append('/').append('>');	// done! completely close element behind attributes				
		}
		else
		{
			buffy.append('>');	// close open tag behind attributes
			if (textNode != null) 
			{	
				//TODO -- might need to trim the buffy here!
				//if (textNode.length() > 0 -- not needed with current impl, which doesnt do append to text node if trim -> empty string
				//if (textNode.length() > 0)
				XmlTools.escapeXML(buffy, textNode);
			}
			for (int i=0; i<numElements; i++)
			{
//				NodeToJavaOptimizations pte		= optimizations.getPTEByFieldName(thatFieldName);
				FieldToXMLOptimizations childF2Xo	= elementF2XOs.get(i);
				//if (XmlTools.representAsLeafNode(thatField))
				final int childOptimizationsType 	= childF2Xo.type();
				if (childOptimizationsType == LEAF_NODE_VALUE)
				{
					try
					{
						childF2Xo.appendLeaf(buffy, this);
					} catch (Exception e)
					{
						throw new XmlTranslationException("TranslateToXML for leaf node " + this, e);
					}				}
				else
				{
					Object thatReferenceObject	= null;
					Field childField			= childF2Xo.field();
					try
					{
						thatReferenceObject		= childField.get(this);
					}
					catch (IllegalAccessException e)
					{
						debugA("WARNING re-trying access! " + e.getStackTrace()[0]);
						childField.setAccessible(true);
						try
						{
							thatReferenceObject	= childField.get(this);
						} catch (IllegalAccessException e1)
						{
							error("Can't access " + childField.getName());
							e1.printStackTrace();
						}
					}
					// ignore null reference objects
					if (thatReferenceObject == null)
						continue;

					final boolean isScalar		= (childOptimizationsType == COLLECTION_SCALAR) || (childOptimizationsType == MAP_SCALAR);
					// gets Collection object directly or through Map.values()
					Collection thatCollection;
					switch (childOptimizationsType)
					{
					case COLLECTION_ELEMENT:
					case COLLECTION_SCALAR:
					case MAP_ELEMENT:
					case MAP_SCALAR:
						thatCollection			= XmlTools.getCollection(thatReferenceObject);
						break;
					default:
						thatCollection			= null;
					break;
					}

					if (thatCollection != null)
					{
						//if the object is a collection, 
						//basically iterate thru the collection and emit XML from each element
						final Iterator iterator			= thatCollection.iterator();
//						Class childClass				= iterator.hasNext() ? iterator.

						while (iterator.hasNext())
						{
							Object next = iterator.next();
							if (isScalar)	// leaf node!
							{
								try
								{
									childF2Xo.appendLeaf(buffy, this);
								} catch (IllegalArgumentException e)
								{
									throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
								} catch (IllegalAccessException e)
								{
									throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
								}
							}
							else if (next instanceof ElementState)
							{
								ElementState collectionSubElementState = (ElementState) next;
								//collectionSubElementState.translateToXML(collectionSubElementState.getClass(), true, nodeNumber, buffy, REGULAR_NESTED_ELEMENT);
								final Class<? extends ElementState> collectionElementClass = collectionSubElementState.getClass();
								FieldToXMLOptimizations collectionElementEntry		= optimizations.fieldToJavaOptimizations(childF2Xo, collectionElementClass);
								collectionSubElementState.translateToXMLBuilder(collectionElementClass, collectionElementEntry, buffy);
							}
							// this is a special hack for working with pre-translated XML Strings (LogOp!)
							//TODO -- get rid of this crap!!!!!! 
							// Make sure LogOp uses CDATA!
							//FIXME -- support scalar leaf type collections here!!!
							else if (next instanceof String)
								buffy.append((String) next);
							else
								throw new XmlTranslationException("Collections MUST contain " +
										"objects of class derived from ElementState or XML Strings, but " +
										thatReferenceObject +" contains some that aren't.");

						}
					}
					else if (thatReferenceObject instanceof ElementState)
					{	// one of our nested elements, so recurse
						ElementState thatElementState	= (ElementState) thatReferenceObject;
						// if the field type is the same type of the instance (that is, if no subclassing),
						// then use the field name to determine the XML tag name.
						// if the field object is an instance of a subclass that extends the declared type of the
						// field, use the instance's type to determine the XML tag name.
						Class thatNewClass			= thatElementState.getClass();
						// debug("checking: " + thatReferenceObject+" w " + thatNewClass+", " + thatField.getType());
						FieldToXMLOptimizations nestedTagMapEntry = thatNewClass.equals(childField.getType()) ?
								childF2Xo : fieldToXMLOptimizations(childField, thatNewClass);

						thatElementState.translateToXMLBuilder(thatNewClass, nestedTagMapEntry, buffy);
						//buffy.append('\n');						
					}
				}
			} //end of for each element child

			// end the element
			buffy.append(fieldToXMLOptimizations.closeTag())/* .append('\n') */;

		} // end if no nested elements or text node
	}
    
	/**
	/**
	 * Translates a tree of ElementState objects, and writes the output to the Appendable passed in
	 * <p/>
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param thatClass
	 * @param fieldToXMLOptimizations
	 * @param appendable		Appendable to translate into. Must be non-null. Can be a Writer, OutputStream, ...
	 * 
	 * @throws XmlTranslationException if a problem arises during translation.
	 * Problems with Field access are possible, but very unlikely.
	 * @throws IOException
	 */
	private void translateToXMLAppendable(Class thatClass, FieldToXMLOptimizations fieldToXMLOptimizations, Appendable appendable)
	throws XmlTranslationException, IOException
	{
		this.preTranslationProcessingHook();

		ArrayList<FieldToXMLOptimizations> attributeF2XOs	= optimizations.attributeFieldOptimizations();
		int numAttributes 			= attributeF2XOs.size();

		appendable.append(fieldToXMLOptimizations.startOpenTag());

		if (numAttributes > 0)
		{
			try
			{
				for (int i=0; i<numAttributes; i++)
				{
					// iterate through fields
					FieldToXMLOptimizations childF2Xo	= attributeF2XOs.get(i);
					childF2Xo.appendValueAsAttribute(appendable, this);
				}
			} catch (Exception e)
			{
				// IllegalArgumentException, IllegalAccessException
				throw new XmlTranslationException("TranslateToXML for attribute " + this, e);
			}
		}
		//ArrayList<Field> elementFields		= optimizations.elementFields();
		ArrayList<FieldToXMLOptimizations> elementF2XOs	= optimizations.elementFieldOptimizations();
		int numElements						= elementF2XOs.size();

		StringBuilder textNode = this.textNodeBuffy;
		//TODO -- fix textNode == null -- should be size() == 0 or some such
		if ((numElements == 0) && (textNode == null))
		{
			appendable.append('/').append('>');	// done! completely close element behind attributes				
		}
		else
		{
			appendable.append('>');	// close open tag behind attributes
			if (textNode != null) 
			{	
				//TODO -- might need to trim the buffy here!
				//if (textNode.length() > 0 -- not needed with current impl, which doesnt do append to text node if trim -> empty string
				//if (textNode.length() > 0)
				XmlTools.escapeXML(appendable, textNode);
			}
			for (int i=0; i<numElements; i++)
			{
//				NodeToJavaOptimizations pte		= optimizations.getPTEByFieldName(thatFieldName);
				FieldToXMLOptimizations childF2Xo	= elementF2XOs.get(i);
				//if (XmlTools.representAsLeafNode(thatField))
				final int childOptimizationsType 	= childF2Xo.type();
				if (childOptimizationsType == LEAF_NODE_VALUE)
				{
					try
					{
						childF2Xo.appendLeaf(appendable, this);
					} catch (Exception e)
					{
						throw new XmlTranslationException("TranslateToXML for leaf node " + this, e);
					}				}
				else
				{
					Object thatReferenceObject	= null;
					Field childField			= childF2Xo.field();
					try
					{
						thatReferenceObject		= childField.get(this);
					}
					catch (IllegalAccessException e)
					{
						debugA("WARNING re-trying access! " + e.getStackTrace()[0]);
						childField.setAccessible(true);
						try
						{
							thatReferenceObject	= childField.get(this);
						} catch (IllegalAccessException e1)
						{
							error("Can't access " + childField.getName());
							e1.printStackTrace();
						}
					}
					// ignore null reference objects
					if (thatReferenceObject == null)
						continue;

					final boolean isScalar		= (childOptimizationsType == COLLECTION_SCALAR) || (childOptimizationsType == MAP_SCALAR);
					// gets Collection object directly or through Map.values()
					Collection thatCollection;
					switch (childOptimizationsType)
					{
					case COLLECTION_ELEMENT:
					case COLLECTION_SCALAR:
					case MAP_ELEMENT:
					case MAP_SCALAR:
						thatCollection			= XmlTools.getCollection(thatReferenceObject);
						break;
					default:
						thatCollection			= null;
					break;
					}

					if (thatCollection != null)
					{
						//if the object is a collection, 
						//basically iterate thru the collection and emit XML from each element
						final Iterator iterator			= thatCollection.iterator();
//						Class childClass				= iterator.hasNext() ? iterator.

						while (iterator.hasNext())
						{
							Object next = iterator.next();
							if (isScalar)	// leaf node!
							{
								try
								{
									childF2Xo.appendLeaf(appendable, this);
								} catch (IllegalArgumentException e)
								{
									throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
								} catch (IllegalAccessException e)
								{
									throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
								}
							}
							else if (next instanceof ElementState)
							{
								ElementState collectionSubElementState = (ElementState) next;
								//collectionSubElementState.translateToXML(collectionSubElementState.getClass(), true, nodeNumber, buffy, REGULAR_NESTED_ELEMENT);
								final Class<? extends ElementState> collectionElementClass = collectionSubElementState.getClass();
								FieldToXMLOptimizations collectionElementEntry		= optimizations.fieldToJavaOptimizations(childF2Xo, collectionElementClass);
								collectionSubElementState.translateToXMLAppendable(collectionElementClass, collectionElementEntry, appendable);
							}
							// this is a special hack for working with pre-translated XML Strings (LogOp!)
							//TODO -- get rid of this crap!!!!!! 
							// Make sure LogOp uses CDATA!
							//FIXME -- support scalar leaf type collections here!!!
							else if (next instanceof String)
								appendable.append((String) next);
							else
								throw new XmlTranslationException("Collections MUST contain " +
										"objects of class derived from ElementState or XML Strings, but " +
										thatReferenceObject +" contains some that aren't.");

						}
					}
					else if (thatReferenceObject instanceof ElementState)
					{	// one of our nested elements, so recurse
						ElementState thatElementState	= (ElementState) thatReferenceObject;
						// if the field type is the same type of the instance (that is, if no subclassing),
						// then use the field name to determine the XML tag name.
						// if the field object is an instance of a subclass that extends the declared type of the
						// field, use the instance's type to determine the XML tag name.
						Class thatNewClass			= thatElementState.getClass();
						// debug("checking: " + thatReferenceObject+" w " + thatNewClass+", " + thatField.getType());
						FieldToXMLOptimizations nestedTagMapEntry = thatNewClass.equals(childField.getType()) ?
								childF2Xo : fieldToXMLOptimizations(childField, thatNewClass);

						thatElementState.translateToXMLAppendable(thatNewClass, nestedTagMapEntry, appendable);
					}
				}
			} //end of for each element child

			// end the element
			appendable.append(fieldToXMLOptimizations.closeTag())/* .append('\n') */;

		} // end if no nested elements or text node
	}
	
	/**
	 * Create a W3C Document object from this. 
	 * That is, go back, from our nice, strongly typed tree, to an untyped one.
	 * 
	 * @return
	 * @throws XmlTranslationException
	 */
	public Document translateToDOM() 
	throws XmlTranslationException
	{
		DocumentBuilderFactory factory	= DocumentBuilderFactory.newInstance();
		try
		{
			DocumentBuilder docBuilder 	= factory.newDocumentBuilder();
			Document dom				= docBuilder.newDocument();

			Class rootClass				= optimizations.thatClass;

			translateToDOM(rootClass, optimizations.rootFieldToXMLOptimizations(rootClass), dom, dom);
		
			return dom;
		} catch (ParserConfigurationException e)
		{
			throw new XmlTranslationException("Couldn't acquire empty Document.", e);
		}
	}

	/**
	/**
	 * Translates a tree of ElementState objects, and writes the output to the Appendable passed in
	 * <p/>
	 * Uses Java reflection to iterate through the public fields of the object.
	 * When primitive types are found, they are translated into attributes.
	 * When objects derived from ElementState are found, 
	 * they are recursively translated into nested elements.
	 * <p/>
	 * The result is a hierarchichal XML structure.
	 * <p/>
	 * Note: to keep XML files from growing unduly large, there is a default 
	 * value for each type.
	 * Attributes which are set to the default value (for that type), 
	 * are not emitted.
	 * 
	 * @param thatClass
	 * @param fieldToXMLOptimizations
	 * @param dom TODO
	 * @param appendable		Appendable to translate into. Must be non-null. Can be a Writer, OutputStream, ...
	 * @throws XmlTranslationException if a problem arises during translation.
	 * Problems with Field access are possible, but very unlikely.
	 * @throws IOException
	 */
	private void translateToDOM(Class thatClass, FieldToXMLOptimizations fieldToXMLOptimizations, Node parentNode, Document dom)
	throws XmlTranslationException
	{
		this.preTranslationProcessingHook();
		
//		Document dom				= parentNode.getOwnerDocument();
		
		Element elementNode			= dom.createElement(fieldToXMLOptimizations.tagName());
		
		parentNode.appendChild(elementNode);

		ArrayList<FieldToXMLOptimizations> attributeF2XOs	= optimizations.attributeFieldOptimizations();
		int numAttributes 			= attributeF2XOs.size();
		if (numAttributes > 0)
		{
			try
			{
				for (int i=0; i<numAttributes; i++)
				{
					// iterate through fields
					FieldToXMLOptimizations childF2Xo	= attributeF2XOs.get(i);
					childF2Xo.setAttribute(elementNode, this);
				}
			} catch (Exception e)
			{
				// IllegalArgumentException, IllegalAccessException
				throw new XmlTranslationException("TranslateToXML for attribute " + this, e);
			}
		}
		
		//TODO -- deal with text node child
		
		ArrayList<FieldToXMLOptimizations> elementF2XOs	= optimizations.elementFieldOptimizations();
		int numElements						= elementF2XOs.size();

		for (int i=0; i<numElements; i++)
		{
			FieldToXMLOptimizations childF2Xo	= elementF2XOs.get(i);

			final int childOptimizationsType 	= childF2Xo.type();
			if (childOptimizationsType == LEAF_NODE_VALUE)
			{
				try
				{
					childF2Xo.appendLeaf(elementNode, this);
				} catch (Exception e)
				{
					throw new XmlTranslationException("TranslateToXML for leaf node " + this, e);
				}				
			}
			else
			{
				Object thatReferenceObject	= null;
				Field childField			= childF2Xo.field();
				try
				{
					thatReferenceObject		= childField.get(this);
				}
				catch (IllegalAccessException e)
				{
					throw new XmlTranslationException("Couldn't access " + childF2Xo.tagName());
				}
				// ignore null reference objects
				if (thatReferenceObject == null)
					continue;

				final boolean isScalar		= (childOptimizationsType == COLLECTION_SCALAR) || (childOptimizationsType == MAP_SCALAR);
				// gets Collection object directly or through Map.values()
				Collection thatCollection;
				switch (childOptimizationsType)
				{
				case COLLECTION_ELEMENT:
				case COLLECTION_SCALAR:
				case MAP_ELEMENT:
				case MAP_SCALAR:
					thatCollection			= XmlTools.getCollection(thatReferenceObject);
					break;
				default:
					thatCollection			= null;
				break;
				}

				if (thatCollection != null)
				{
					//if the object is a collection, 
					//basically iterate thru the collection and emit XML from each element
					final Iterator iterator			= thatCollection.iterator();
//					Class childClass				= iterator.hasNext() ? iterator.

					while (iterator.hasNext())
					{
						Object next = iterator.next();
						if (isScalar)	// leaf node!
						{
							try
							{
								childF2Xo.appendLeaf(elementNode, this);
							} catch (IllegalArgumentException e)
							{
								throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
							} catch (IllegalAccessException e)
							{
								throw new XmlTranslationException("TranslateToXML for collection leaf " + this, e);
							}
						}
						else if (next instanceof ElementState)
						{
							ElementState collectionSubElementState = (ElementState) next;
							//collectionSubElementState.translateToXML(collectionSubElementState.getClass(), true, nodeNumber, buffy, REGULAR_NESTED_ELEMENT);
							final Class<? extends ElementState> collectionElementClass = collectionSubElementState.getClass();
							FieldToXMLOptimizations collectionElementF2XO		= optimizations.fieldToJavaOptimizations(childF2Xo, collectionElementClass);
							collectionSubElementState.translateToDOM(collectionElementClass, collectionElementF2XO, elementNode, dom);
						}
						else
							throw new XmlTranslationException("Collections MUST contain " +
									"objects of class derived from ElementState or XML Strings, but " +
									thatReferenceObject +" contains some that aren't.");

					}
				}
				else if (thatReferenceObject instanceof ElementState)
				{	// one of our nested elements, so recurse
					ElementState thatElementState	= (ElementState) thatReferenceObject;
					// if the field type is the same type of the instance (that is, if no subclassing),
					// then use the field name to determine the XML tag name.
					// if the field object is an instance of a subclass that extends the declared type of the
					// field, use the instance's type to determine the XML tag name.
					Class thatNewClass			= thatElementState.getClass();
					// debug("checking: " + thatReferenceObject+" w " + thatNewClass+", " + thatField.getType());
					FieldToXMLOptimizations nestedTagMapEntry = thatNewClass.equals(childField.getType()) ?
							childF2Xo : fieldToXMLOptimizations(childField, thatNewClass);

					thatElementState.translateToDOM(thatNewClass, nestedTagMapEntry, elementNode, dom);
				}
			}
		} //end of for each element child
	}

    /**
     * Returns the precision of floating point numbers associated with this
     * instance of ElementState.
     * 
     * Subclasses may override this method, which is particularly useful if a
     * class should have a certain floating point precision associated with it.
     * 
     * @return the floating point precision to be used when translating this to
     *         XML.
     */
    protected short floatingPrecision()
    {
        return this.floatingPrecision;
    }
    
    /**
	 * Translate our representation of a leaf node to XML.
	 * 
	 * @param buffy
	 * @param leafElementName
	 * @param leafValue
	 * @param type
	 * @param isCDATA
	 */
	void appendLeafXML(StringBuilder buffy, FieldToXMLOptimizations fieldToXMLOptimizations, String leafValue)
	{
		appendLeafXML(buffy, fieldToXMLOptimizations.tagName(), leafValue, fieldToXMLOptimizations.isNeedsEscaping(), fieldToXMLOptimizations.isCDATA());
	}
	/**
	 * Translate our representation of a leaf node to XML.
	 * 
	 * @param buffy
	 * @param leafElementName
	 * @param leafValue
	 * @param type
	 * @param isCDATA
	 */
	void appendLeafXML(StringBuilder buffy, String leafElementName, String leafValue, boolean needsEscaping, boolean isCDATA)
	{
		if (!ecologylab.xml.types.scalar.ScalarType.DEFAULT_VALUE_STRING.equals(leafValue))
		{
			buffy.append('<').append(leafElementName).append('>');
			
			if (isCDATA)
			{
				buffy.append("<![CDATA[");
				buffy.append(leafValue);
				buffy.append("]]>");
			}
			else
			{
				if (needsEscaping)
					XmlTools.escapeXML(buffy, leafValue);
				else
					buffy.append(leafValue);
			}
			buffy.append("</").append(leafElementName).append('>').append('\n');
		}
	}
	
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of
	 * classes derived from ElementState, which corresponds to the structure
	 * of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * <p/>
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlDocumentPURL	ParsedURL for the XML document that needs to be translated.
	 * @return 	   Parent ElementState object of the corresponding Java tree.
	 */

	public static ElementState translateFromXML(ParsedURL xmlDocumentPURL)
	throws XmlTranslationException
	{
		return translateFromXML(xmlDocumentPURL, globalTranslationSpace);
	}
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of
	 * classes derived from ElementState, which corresponds to the structure
	 * of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * <p/>
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param purl	ParsedURL for the XML document that needs to be translated.
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * 
	 * @return 	   Parent ElementState object of the corresponding Java tree.
	 */

	public static ElementState translateFromXML(ParsedURL purl,
												TranslationSpace translationSpace)
	throws XmlTranslationException
	{
		if (purl == null)
			throw new XmlTranslationException("Null PURL", NULL_PURL);
		
		if (!purl.isNotFileOrExists())
			throw new XmlTranslationException("Can't find " + purl.toString(), FILE_NOT_FOUND);
		
		return translateFromXMLDOM(buildDOM(purl), translationSpace);
	}
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of
	 * classes derived from ElementState, which corresponds to the structure
	 * of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them 
	 * into a tree of state-objects.
	 * <p/>
	 * Uses the default globalNameSpace as the basis for translation.
	 * <p/>
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlDocumentURL	URL for the XML document that needs to be translated.
	 * @return 		 Parent ElementState object of the corresponding Java tree.
	 */

	public static ElementState translateFromXML(URL xmlDocumentURL)
	throws XmlTranslationException
	{
	   return translateFromXML(xmlDocumentURL, globalTranslationSpace);
	}
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of
	 * classes derived from ElementState, which corresponds to the structure
	 * of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * <p/>
	 * Uses the default globalNameSpace as the basis for translation.
	 * <p/>
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlDocumentURL	URL for the XML document that needs to be translated.
	 * 
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * @return 		 Parent ElementState object of the corresponding Java tree.
	 */

	public static ElementState translateFromXML(URL xmlDocumentURL,
												TranslationSpace translationSpace)
	throws XmlTranslationException
	{
	   Document document	= buildDOM(xmlDocumentURL);
	   return (document == null) ? 
		  null : translateFromXMLDOM(document, translationSpace);
	}
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlFile		the path to the XML document that needs to be translated.
	 * @return 					the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(File xmlFile, 
												TranslationSpace translationSpace)
	throws XmlTranslationException
	{
	   Document document	= buildDOM(xmlFile);
	   ElementState result	= null;
	   if (document != null)
		  result			= translateFromXMLDOM(document, translationSpace);
	   return result;
	}
	/**
	 * Given the URL of a valid XML document,
	 * reads the document and builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * <p/>
	 * Uses the default globalNameSpace as the basis for translation.
	 * 
	 * @param xmlFile		the path to the XML document that needs to be translated.
	 * @return 					the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(File xmlFile)
	throws XmlTranslationException
	{
	   return translateFromXML(xmlFile, globalTranslationSpace);
	}
	/**
	 * Given the name of a valid XML file,
	 * reads the file and builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param fileName	the name of the XML file that needs to be translated.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(String fileName,
												TranslationSpace translationSpace)
		throws XmlTranslationException
	{
		Document document	= buildDOM(fileName);
		return (document == null) ? null : translateFromXMLDOM(document, translationSpace);
	}
	/**
	 * Given the name of a valid XML file,
	 * reads the file and builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param fileName	the name of the XML file that needs to be translated.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(String fileName)
		throws XmlTranslationException
	{
		return translateFromXML(fileName, globalTranslationSpace);
	}
	
	/**
	 * Given an XML-formatted String, 
	 * builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlStream	An InputStream to the XML that needs to be translated.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(InputStream xmlStream,
												TranslationSpace nameSpace)
		throws XmlTranslationException
	{
		Document document	= buildDOM(xmlStream);
		return (document == null) ? null : translateFromXMLDOM(document, nameSpace);
	}	
	/**
	 * Given an XML-formatted String, 
	 * builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlStream	An InputStream to the XML that needs to be translated.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(InputStream xmlStream)
		throws XmlTranslationException
	{
		return translateFromXML(xmlStream, globalTranslationSpace);
	}	
	
	/**
	 * Given an XML-formatted String, 
	 * builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * @param charsetType	A constant from ecologylab.generic.StringInputStream.
	 * 						0 for UTF16_LE. 1 for UTF16. 2 for UTF8.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString, 
													  int charsetType,
													  TranslationSpace translationSpace)
		throws XmlTranslationException
	{
	   return translateFromXMLString(xmlString, charsetType, translationSpace, true);
	}
	/**
	 * Given an XML-formatted String, 
	 * builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * @param charsetType	A constant from ecologylab.generic.StringInputStream.
	 * 						0 for UTF16_LE. 1 for UTF16. 2 for UTF8.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString, 
													  int charsetType,
													  TranslationSpace translationSpace,
													boolean doRecursiveDescent)
		throws XmlTranslationException
	{
	   Document document	= buildDOMFromXMLString(xmlString, charsetType);
	   return (document == null) ? null : 
		  translateFromXMLDOM(document,translationSpace, doRecursiveDescent);
	}
	/**
	 * Given an XML-formatted String, 
	 * builds a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which is 
	 * an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of classes derived
	 * from ElementState, which corresponds to the structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * @param charsetType	A constant from ecologylab.generic.StringInputStream.
	 * 						0 for UTF16_LE. 1 for UTF16. 2 for UTF8.
	 * @return 			the parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString, 
													  int charsetType)
		throws XmlTranslationException
	{
	   return translateFromXMLString(xmlString, charsetType, globalTranslationSpace);
	}
	
	/**
	 * Given an XML-formatted String, uses charset type UTF-8 to create
	 * a stream, and build a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree 
	 * of classes derived from ElementState, which corresponds to the
	 * structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into
	 * a tree of state-objects. Uses the default UTF8 charset.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * @return 		 Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString,
													  TranslationSpace translationSpace)
		throws XmlTranslationException
	{

	   return translateFromXMLString(xmlString, translationSpace, true);
	}
	/**
	 * Given an XML-formatted String, uses charset type UTF-8 to create
	 * a stream, and build a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree 
	 * of classes derived from ElementState, which corresponds to the
	 * structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into
	 * a tree of state-objects. Uses the default UTF8 charset.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * @return 		 Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString,
													  TranslationSpace translationSpace,
													boolean doRecursiveDescent)
		throws XmlTranslationException
	{

	   xmlString = XML_FILE_HEADER + xmlString;
	   return translateFromXMLString(xmlString, StringInputStream.UTF8,
									 translationSpace, doRecursiveDescent);
	}
	/**
	 * Given an XML-formatted String, uses charset type UTF-8 to create
	 * a stream, and build a tree of equivalent ElementState objects.
	 * 
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree 
	 * of classes derived from ElementState, which corresponds to the
	 * structure of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into
	 * a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlString	the actual XML that needs to be translated.
	 * 
	 * @return 		 Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLString(String xmlString)
		throws XmlTranslationException
	{
	   return translateFromXMLString(xmlString, globalTranslationSpace);
	}
	
	/**
	 * Given the Document object for an XML DOM, builds a tree of equivalent
	 * ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of 
	 * classes derived from ElementState, which corresponds to the structure
	 * of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them
	 * into a tree of state-objects.
	 * <p/>
	 * Uses the default globalNameSpace as the basis for translation.
	 * <p/>
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param doc	Document object for DOM tree that needs to be translated.
	 * @return 	  Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(Document doc)
	throws XmlTranslationException
	{
	   return translateFromXMLDOM(doc, globalTranslationSpace);
	}
	
	/**
	 * Given the Document object for an XML DOM, builds a tree of equivalent
	 * ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree 
	 * of classes derived from ElementState, which corresponds to the 
	 * structure of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param doc	Document object for DOM tree that needs to be translated.
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * 
	 * @return 		Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLDOM(Document doc, 
												TranslationSpace translationSpace)
	throws XmlTranslationException
	{
		return translateFromXMLDOM(doc, translationSpace, true);
	}
	
	/**
	 * Given the Document object for an XML DOM, builds a tree of equivalent
	 * ElementState objects.
	 * <p/>
	 * That is, translates the XML into a tree of Java objects, each of which
	 * is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree 
	 * of classes derived from ElementState, which corresponds to the 
	 * structure of the XML DOM that needs to be parsed.
	 * <p/>
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file.
	 * S/he passes it to this method to create a Java hierarchy equivalent to 
	 * the DOM.
	 * <p/>
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param dom	Document object for DOM tree that needs to be translated.
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * 
	 * @return 		Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLDOM(Document dom, 
												TranslationSpace translationSpace,
												boolean doRecursiveDescent)
	throws XmlTranslationException
	{
		Node rootNode				= dom.getDocumentElement();
		return translateFromXMLRootNode(rootNode, translationSpace, doRecursiveDescent);
	}
	

	/**
	 * A recursive method.
	 * Typically, this method is initially passed the root Node of an XML DOM,
	 * from which it builds a tree of equivalent ElementState objects.
	 * It does this by recursively calling itself for each node/subtree of 
	 * ElementState objects.
	 * 
	 * The method translates any tree of DOM into a tree of Java objects, each
	 * of which is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of 
	 * classes derived from ElementState, which corresponds to the structure 
	 * of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file, and access the 
	 * root Node. S/he passes it to this method to create a Java hierarchy 
	 * equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param document	Root node of the DOM tree that needs to be translated.
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * 
	 * @return 			Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXML(Document document,
			TranslationSpace translationSpace)
	throws XmlTranslationException
	{
		return translateFromXMLRootNode(document.getDocumentElement(), translationSpace, true);
	}
	/**
	 * A recursive method.
	 * Typically, this method is initially passed the root Node of an XML DOM,
	 * from which it builds a tree of equivalent ElementState objects.
	 * It does this by recursively calling itself for each node/subtree of 
	 * ElementState objects.
	 * 
	 * The method translates any tree of DOM into a tree of Java objects, each
	 * of which is an instance of a subclass of ElementState.
	 * The operation of the method is predicated on the existence of a tree of 
	 * classes derived from ElementState, which corresponds to the structure 
	 * of the XML DOM that needs to be parsed.
	 * 
	 * Before calling the version of this method with this signature,
	 * the programmer needs to create a DOM from the XML file, and access the 
	 * root Node. S/he passes it to this method to create a Java hierarchy 
	 * equivalent to the DOM.
	 * 
	 * Recursively parses the XML nodes in DFS order and translates them into 
	 * a tree of state-objects.
	 * 
	 * This method used to be called builtStateObject(...).
	 * 
	 * @param xmlRootNode		Root node of the DOM tree that needs to be translated.
	 * @param translationSpace		NameSpace that provides basis for translation.
	 * 
	 * @return 				Parent ElementState object of the corresponding Java tree.
	 */
	public static ElementState translateFromXMLRootNode(Node xmlRootNode,
												TranslationSpace translationSpace,
												boolean doRecursiveDescent)
	   throws XmlTranslationException
	{
	   // find the class for the new object derived from ElementState
		Class stateClass			= null;
		String tagName				= xmlRootNode.getNodeName();
		int colonIndex				= tagName.indexOf(':');
		if (colonIndex > 1)
		{   // we are dealing with an XML Namespace
			//TODO -- do something more substantial than throwing away the prefix
			tagName					= tagName.substring(colonIndex + 1);
		}
		try
		{
			//TODO -- use class-level @xml_tag if it was declared?!
			stateClass= translationSpace.xmlTagToClass(tagName);
			if (stateClass != null)
			{
				ElementState rootState= (ElementState) XmlTools.getInstance(stateClass);
				if (rootState != null)
				{
					rootState.setupRoot();
					rootState.translateFromXMLNode(xmlRootNode, translationSpace, doRecursiveDescent);

					rootState.postTranslationProcessingHook();

					return rootState;
				}
			}
			else
			{
				// else, we don't translate this element; we ignore it.
				println("XML Translation WARNING: Cant find class object for Root XML element <"
						+ tagName + ">: Ignored. ");
			}
		}
		catch (Exception e)
		{
		   StackTraceElement stackTrace[] = e.getStackTrace();
		   println("XML Translation WARNING: Exception while trying to translate XML element <" 
				   + tagName+ "> class="+stateClass + ". Ignored.\nThe exception was " 
				   + e.getMessage() + " from " +stackTrace[0] +" " + stackTrace[1]);
		   //e.printStackTrace();
//		   throw new XmlTranslationException("All ElementState subclasses"
//							       + "MUST contain an empty constructor, but "+
//								   stateClass+" doesn't seem to.");
		}
		return null;
	 }	
	
	/**
	 * Link new born root element to its Optimizations and create an elementByIdMap for it.
	 */
	void setupRoot()
	{
		elementByIdMap		= new HashMap<String, ElementState>();
		optimizations		= Optimizations.lookupRootOptimizations(this);		
	}
/**
     * A recursive method.
     * Typically, this method is initially passed the root Node of an XML DOM,
     * from which it builds a tree of equivalent ElementState objects.
     * It does this by recursively calling itself for each node/subtree of ElementState objects.
     * 
     * The method translates any tree of DOM into a tree of Java objects, each
     * of which is an instance of a subclass of ElementState.
     * The operation of the method is predicated on the existence of a tree of
     * classes derived from ElementState, which corresponds to the structure 
	 * of the XML DOM that needs to be parsed.
     * 
     * Before calling the version of this method with this signature, the
	 *  programmer needs to create a DOM from the XML file, and access the root
     * Node. S/he passes it to this method to create a Java hierarchy 
	 * equivalent to the DOM.
     * 
     * Recursively parses the XML nodes in DFS order and translates them into
	 * a tree of state-objects.
     * 
     * @param xmlNode	Root node of the DOM tree that needs to be translated.
     * @param translationSpace		NameSpace that provides basis for translation.
     * @return 			Parent ElementState object of the corresponding Java tree.
     */
	void translateFromXMLNode(Node xmlNode, TranslationSpace translationSpace, boolean doRecursiveDescent)
	throws XmlTranslationException
	{
		// translate attribtues
		if (xmlNode.hasAttributes())
		{
			NamedNodeMap xmlNodeAttributes = xmlNode.getAttributes();
			
			int numAttributes = xmlNodeAttributes.getLength();
			for (int i = 0; i < numAttributes; i++) 
			{
				final Node attrNode 	= xmlNodeAttributes.item(i);
				final String tag		= attrNode.getNodeName();
				final String value		= attrNode.getNodeValue();
               
				if (value != null)
				{
					NodeToJavaOptimizations njo	=
						optimizations.attributeNodeToJavaOptimizations(translationSpace, this, tag, value);
					switch (njo.type())
					{
					case REGULAR_ATTRIBUTE:
						njo.setFieldToScalar(this, value);
						// the value can become a unique id for looking up this
						if ("id".equals(njo.tag()))
							this.elementByIdMap.put(value, this);
						break;
					default:
						break;	
					}
				}
			}
		}
		if (!doRecursiveDescent)
			return;
		
		// translate nested elements (aka children):
		// loop through them, recursively build them, and add them to ourself
		NodeList childNodes	= xmlNode.getChildNodes();
		int numChilds		= childNodes.getLength();
	
		for (int i = 0; i < numChilds; i++)
		{
			Node childNode		= childNodes.item(i);
			short childNodeType	= childNode.getNodeType();
			if ((childNodeType == Node.TEXT_NODE) || (childNodeType == Node.CDATA_SECTION_NODE))
			{
				appendTextNodeString(childNode.getNodeValue());
			}
			else
			{
				NodeToJavaOptimizations njo		= optimizations.elementNodeToJavaOptimizations(translationSpace, this, childNode);
				NodeToJavaOptimizations nsNJO	= njo.nestedPTE();
				NodeToJavaOptimizations	activeNJO;
				ElementState	activeES;
				if (nsNJO != null)
				{
					activeNJO				= nsNJO;
					// get (create if necessary) the ElementState object corresponding to the XML Namespace
					activeES				= (ElementState) ReflectionTools.getFieldValue(this, njo.field());
					if (activeES == null)
					{	// first time using the Namespace element, so we gotta create it
						activeES			= (ElementState) njo.domFormChildElement(this, null, false);
						ReflectionTools.setFieldValue(this, njo.field(), activeES);
					}
				}
				else
				{
					activeNJO				= njo;
					activeES				= this;
				}
				switch (njo.type())
				{
				case REGULAR_NESTED_ELEMENT:
					activeNJO.domFormNestedElementAndSetField(activeES, childNode);
					break;
				case LEAF_NODE_VALUE:
					activeNJO.setScalarFieldWithLeafNode(activeES, childNode);
					break;
				case COLLECTION_ELEMENT:
					activeNJO.domFormElementAndAddToCollection(activeES, childNode);
					break;
				case COLLECTION_SCALAR:
					activeNJO.addLeafNodeToCollection(activeES, childNode);
					break;
				case MAP_ELEMENT:
					activeNJO.domFormElementAndToMap(activeES, childNode);
					break;
				case OTHER_NESTED_ELEMENT:
					activeES.addNestedElement(activeNJO, childNode);
					break;
				case IGNORED_ELEMENT:
				case BAD_FIELD:
				default:
					break;
				}
			}
		}
	}
	
	public static ElementState translateFromXMLSAX(ParsedURL purl, TranslationSpace translationSpace)
	throws XmlTranslationException
	{
		ElementStateSAXHandler saxHandler	= new ElementStateSAXHandler(translationSpace);
		return saxHandler.parse(purl);
	}
	public static ElementState translateFromXMLSAX(File file, TranslationSpace translationSpace)
	throws XmlTranslationException
	{
		ElementStateSAXHandler saxHandler	= new ElementStateSAXHandler(translationSpace);
		return saxHandler.parse(file);
	}
	/**
	 * Used in SAX parsing to unmarshall attributes into fields.
	 * 
	 * @param translationSpace
	 * @param attributes
	 */
	void translateAttributes(TranslationSpace translationSpace, Attributes attributes)
	{
		int numAttributes	= attributes.getLength();
		for (int i=0; i<numAttributes; i++)
		{
			//TODO -- figure out what we're doing if there's a colon and a namespace
			final String tag		= attributes.getQName(i);
			final String value	= attributes.getValue(i);
			//TODO String attrType = getType()?!
			if (value != null)
			{
				NodeToJavaOptimizations njo	= 
					optimizations.attributeNodeToJavaOptimizations(translationSpace, this, tag, value);
				switch (njo.type())
				{
				case REGULAR_ATTRIBUTE:
					njo.setFieldToScalar(this, value);
					// the value can become a unique id for looking up this
					//TODO -- could support the ID type for the node here!
					if ("id".equals(njo.tag()))
						this.elementByIdMap.put(value, this);
					break;
				default:
					break;	
				}
			}
		}
	}
	
	

	//////////////// methods to generate DOM objects ///////////////////////
	/**
	 * This method creates a DOM Document from the XML file at a given URL.
	 *
	 * @param url	the URL to the XML from which the DOM is to be created
	 * 
	 * @return			the Document object
	 */
	static public Document buildDOM(URL url)
	{
		return buildDOM(url.toString());
	}
	
	static public Document buildDOM(ParsedURL purl)
	{
		return purl.isFile() ? buildDOM(purl.file()) : buildDOM(purl.url());
	}
	/**
	 * This method creates a DOM Document from the local XML file.
	 *
	 * @param file		the XML file from which the DOM is to be created
	 * 
	 * @return			the Document object
	 */
	static public Document buildDOM(File file)
	{
		Document document	= null;
		try
		{
    	  DocumentBuilder builder = XmlTools.getDocumentBuilder();
    	  createErrorHandler(builder);
     	  document = builder.parse(file);
		} 
		
		catch (SAXParseException spe) 
		{
			// Error generated by the parser
		    reportException(spe, file.getAbsolutePath());
	  	}
	  	catch (SAXException sxe) 
	  	{
		    // Error generated during parsing
		    reportException(sxe);
	   	}
	  	catch(Exception e)
	  	{
	  		e.printStackTrace();
	  	}
		return document;
	}
	/**
	 * This method creates a DOM Document from the XML file at a given URI,
	 * which could be a local file or a URL.
	 *
	 * @param xmlFileOrURLName	the path to the XML from which the DOM is to be created
	 * 
	 * @return					the Document object
	 */
	static public Document buildDOM(String xmlFileOrURLName)
	{		       
		Document document	= null;
		try
		{
    	  DocumentBuilder builder = XmlTools.getDocumentBuilder();
    	  createErrorHandler(builder);
    	  if( !xmlFileOrURLName.contains("://") )
    		  xmlFileOrURLName = "file:///" + xmlFileOrURLName;
    	  document = builder.parse(xmlFileOrURLName);
		} 
		
		catch (SAXParseException spe) 
		{
			// Error generated by the parser
		    reportException(spe, xmlFileOrURLName);
	  	}
	  	catch (SAXException sxe) 
	  	{
		    // Error generated during parsing
		    reportException(sxe);
	   	}
        catch(IOException e)
        {
            e.printStackTrace();
        }
	  	catch(Exception e)
	  	{
	  		e.printStackTrace();
	  	}
		return document;
	}
	/**
	 * Report exception during DOM parsing.
	 * 
	 * @param sxe
	 */
	private static void reportException(SAXException sxe) 
	{
		Exception  x = sxe;
		if (sxe.getException() != null)
		  x = sxe.getException();
		x.printStackTrace();
	}
	/**
	 * Report exception during DOM parsing.
	 * 
	 * @param spe
	 * @param xmlFileOrURLName
	 */
	private static void reportException(SAXParseException spe, String xmlFileOrURLName)
	{
		println(xmlFileOrURLName + ":\n** Parsing error" + ", line " + spe.getLineNumber() + ", uri " + spe.getSystemId());
		println("   " + spe.getMessage());
  
		// Use the contained exception, if any
		Exception  x = spe;
		if (spe.getException() != null)
		   x = spe.getException();
		x.printStackTrace();
	}

	/**
	 * This method creates a DOM Document from the XML file at a given URI,
	 * which could be a local file or a URL.
	 *
	 * @param inStream	InputStream from which the DOM is to be created
	 * 
	 * @return					the Document object
	 */
	static public Document buildDOM(InputStream inStream)
	{		       
		Document document	= null;
		try
		{
    	  DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	  DocumentBuilder builder = factory.newDocumentBuilder();
    	  createErrorHandler(builder);
    	  
  		  document = builder.parse(inStream);
		} 
		catch (SAXParseException spe)
		{
			// Error generated by the parser
		    println("ERROR parsing DOM in" + inStream + ":\n\t** Parsing error on line " + spe.getLineNumber() + ", uri=" + spe.getSystemId());
		    println("   " + spe.getMessage());
	  	}
	  	catch (SAXException sxe)
	  	{   // Error generated during parsing
		    Exception  x = sxe;
		    if (sxe.getException() != null)
		      x = sxe.getException();
		    x.printStackTrace();
	   	}
	   	catch (ParserConfigurationException pce)
	   	{
		    // Parser with specified options can't be built
		    pce.printStackTrace();
	   	}
	   	catch (IOException ioe)
	   	{
		    // I/O error
		    ioe.printStackTrace();
	  	}
	  	catch(FactoryConfigurationError fce)
	  	{
	  		fce.printStackTrace();
	  	}
	  	catch(Exception e)
	  	{
	  		e.printStackTrace();
	  	}
		return document;
	}
	/**
	 * This method creates a DOM Document from an XML-formatted String.
	 *
	 * @param xmlString	the XML-formatted String from which the DOM is to be created
	 * @param charsetType	A constant from ecologylab.generic.StringInputStream.
	 * 						0 for UTF16_LE. 1 for UTF16. 2 for UTF8.
	 * 
	 * @return					the Document object
	 */
	static public Document buildDOMFromXMLString(String xmlString,
												 int charsetType)
    {
	   InputStream xmlStream =
		  new StringInputStream(xmlString, charsetType);

	   return buildDOM(xmlStream);
	}

	/**
	 * This method creates a DOM Document from an XML-formatted String,
	 * encoded as UTF8.
	 *
	 * @param xmlString	the XML-formatted String from which the DOM is to be created
	 * 
	 * @return					the Document object
	 */
	static public Document buildDOMFromXMLString(String xmlString)
    {
	   return buildDOMFromXMLString(xmlString, UTF8);
	}

  	static private void createErrorHandler(final DocumentBuilder builder){
  		
  		builder.setErrorHandler(
	  	new org.xml.sax.ErrorHandler() {
	    	// ignore fatal errors (an exception is guaranteed)
		    public void fatalError(SAXParseException exception)
		    throws SAXException {
		    }
		    // treat validation errors as fatal
		    public void error(SAXParseException e)
		    throws SAXParseException
		    {
		      throw e;
		    }
		
		     // dump warnings too
		    public void warning(SAXParseException err)
		    throws SAXParseException
		    {
		      println(builder + "** Warning"
		        + ", line " + err.getLineNumber()
		        + ", uri " + err.getSystemId());
		      println("   " + err.getMessage());
		    }
	    
	  	}  
		); 
  	}

	//////////////// methods to generate XML, and write to a file /////////////
	public void saveXmlFile(String filePath, boolean prettyXml, boolean compression)
	throws XmlTranslationException
	{
		if(!filePath.endsWith(".xml") && !filePath.endsWith(".XML"))
		{
			filePath	= filePath + ".xml";
		}
		savePrettyXML(new File(filePath));
	}
	
	/**
	 * 	Translate to XML, then write the result to a file, while formatting nicely.
	 */
	public void savePrettyXML(String xmlFileName)
	throws XmlTranslationException
	{
		savePrettyXML(new File(xmlFileName));
	}
	
/**
 * 	Translate to XML, then write the result to a file.
 * 
 * 	@param xmlFile		the file in which the xml needs to be saved
 */	
	public void savePrettyXML(File xmlFile)
		throws XmlTranslationException
	{
	 	XmlTools.writePrettyXml(translateToDOM(), xmlFile);
	}
	
	/**
	 * 	Translate to XML, then write the result to a file.
	 * 
	 * 	@param xmlFile		the file in which the xml needs to be saved
	 */	
	public void writePrettyXML(OutputStream outputStream)
	throws XmlTranslationException
	{
		XmlTools.writePrettyXml(translateToDOM(), new StreamResult(outputStream));
	}
		
	//////////////// helper methods used by translateToXML() //////////////////

/**
 * Get a tag translation object that corresponds to the fieldName,
 * with this class. If necessary, form that tag translation object,
 * and cache it.
 */
	private FieldToXMLOptimizations fieldToXMLOptimizations(Field field)
	{
		return optimizations.fieldToXMLOptimizations(field);
	}
/**
 * Get a tag translation object that corresponds to the fieldName,
 * with this class. If necessary, form that tag translation object,
 * and cache it.
 */
	protected FieldToXMLOptimizations fieldToXMLOptimizations(Field field, Class<? extends ElementState> thatClass)
	{
		return optimizations.fieldToXMLOptimizations(field, thatClass);
	}

	//////////////// helper methods used by translateFromXML() ////////////////
	/**
	 * Set a field that is an extended primitive -- a non ElementState --
	 * using the type registry.
	 * 
	 * @param field
	 * @param fieldValue
	 * @return	true if the Field is set successfully.
	 */
	protected boolean setFieldUsingTypeRegistry(Field field, String fieldValue)
	{
		boolean result		= false;
		ScalarType fieldType		= TypeRegistry.getType(field);
		if (fieldType != null)
			result			= fieldType.setField(this, field, fieldValue);
		else
			debug("Can't find type for " + field + " with value=" + fieldValue);
		return result;
	}

	static final int HAVENT_TRIED_ADDING	= 0;
	static final int DONT_NEED_WARNING		= 1;
	static final int NEED_WARNING			= -1;
	
	private int considerWarning				= HAVENT_TRIED_ADDING;
	
	/**
	 * Old-school DOM approach.
	 * 
	 * This base implementation provides a warning.
	 * @param pte
	 * @param childNode
	 * @throws XmlTranslationException
	 */
	protected void addNestedElement(NodeToJavaOptimizations pte, Node childNode)
	throws XmlTranslationException
	{
		addNestedElement((ElementState) pte.domFormChildElement(this, childNode, false));
		if (considerWarning == NEED_WARNING)
		{
			warning("Ignoring nested elements with tag <" + pte.tag() + ">");
			considerWarning					= DONT_NEED_WARNING;
		}
	}
	/**
	 * This is the hook that enables programmers to do something special
	 * when handling a nested XML element and its associate ElementState (subclass),
	 * by overriding this method and providing a custom implementation.
	 * <p/>
	 * The default implementation is a no-op.
	 * fields that get here are ignored.
	 * 
	 * @param elementState
	 * @throws XmlTranslationException 
	 */
	protected void addNestedElement(ElementState elementState)
	{
		if (considerWarning == HAVENT_TRIED_ADDING)
			considerWarning	= NEED_WARNING;
	}

	/**
	 * Called during translateFromXML().
	 * If the textNodeString is currently null, assign to.
	 * Otherwise, append to it.
	 * 
	 * @param newText	Text Node value just found parsing the XML.
	 */
	protected void appendTextNodeString(String newText)
	{
	   if ((newText != null) && (newText.length() > 0))
	   {
		   //TODO -- hopefully we can get away with this speed up
		   String trimmed	=	newText.trim();
		   if (trimmed.length() > 0)
		   {
			   String unescapedString = XmlTools.unescapeXML(newText);
			   if (this.textNodeBuffy == null)
				   textNodeBuffy	= new StringBuilder(unescapedString);
			   else
				   textNodeBuffy.append(unescapedString);
		   }
	   }
	}
	public String getTextNodeString()
	{
		return textNodeBuffy.toString();
//		return (textNodeString == null) ? null : XmlTools.unescapeXML(textNodeString);
	}
	/////////////////////////// other methods //////////////////////////

	
	/**
	 * Add a package name to className mapping to the translation table in the NameSpace.
	 * <br/><br/>Example:<br/><code>
	 * 	  addTranslation("cf.history", "KeyframeState");<br/>
	 *    addTranslation("cf.history", "KeyframeTimeStampSet");<br/></code>
	 * <br/>
	 * The class name will be translated into an xml tag name, using the usual rules.
	 * 
	 * @param packageName
	 * @param className
	 */
	public static void addTranslation(String packageName, String className)
	{
		globalTranslationSpace.addTranslation(packageName, className);
	}
   /**
	* Set the default package name for XML tag to ElementState sub-class translations,
	* for the global name space.
	* 
	* @param packageName	The new default package name.
	*/
   public static void setDefaultPackageName(String packageName)
   {
	  globalTranslationSpace.setDefaultPackageName(packageName);
   }

	/**
	 * The DOM classic accessor method.
	 * 
	 * @return element in the tree rooted from this, whose id attrribute is as in the parameter.
	 * 
	 */
	public ElementState getElementStateById(String id)
	{
		return this.elementByIdMap.get(id);
	}

	/**
	 * When translating from XML, if a tag is encountered with no matching field, perhaps
	 * it belongs in a Collection.
	 * This method tells us which collection object that would be.
	 * 
	 * @param thatClass		The class of the ElementState superclass that could be stored in a Collection.
	 * @return
	 */
	protected Collection<? extends ElementState> getCollection(Class thatClass)
	{
		return null;
	}
	
	/**
	 * When translating from XML, if a tag is encountered with no matching field, perhaps
	 * it belongs in a Collection.
	 * This method tells us which collection object that would be.
	 * 
	 * @param thatClass		The class of the ElementState superclass that could be stored in a Collection.
	 * @return
	 */
	protected <K extends Object, V extends ElementState & Mappable<K>>Map<K, V> getMap(Class thatClass)
	{
		return null;
	}
	
	/**
	 * An array of Strings with the names of the leaf elements.
	 * Must be overridden to provide leaf elements as direct, typed field values.
	 * 
	 * @return		null in the default implementation.
	 */
	protected String[] leafElementFieldNames()
	{
		return null;
	}
	
	
	/**
	 * Convenience for specifying what collection to put objects of a given
	 * type into, where there is a clear mapping based on type (class).
	 *
	 * @author andruid
	 */
	protected class ClassToCollectionMap
	extends HashMap<String, Collection>
	{
		public ClassToCollectionMap(Object[][] mappings)
		{
			int numMappings	= mappings.length;
			for (int i=0; i<numMappings; i++)
			{
				Object[] thatMapping			= mappings[i];
				try
				{
					Class thatClass				= (Class) thatMapping[0];
					Collection thatCollection	= (Collection) thatMapping[1];
//					put(thatClass.getSimpleName(), thatCollection);
					put(Debug.classSimpleName(thatClass), thatCollection);
				} catch (ClassCastException e)
				{
					debug("ERROR in ClassToCollectionMap initializer("+i+" has wrong type:\n\t"+
						  thatMapping[0] +", " + thatMapping[i]);
				}
			}
		}
		public Collection lookup(String className)
		{
			return (Collection) get(className);
		}
		public Collection lookup(Class thatClass)
		{
			return lookup(classSimpleName(thatClass));
		}
	}

	/**
	 * Specifies automatic conversion from XML style names (e.g. composition_space) to
	 * Java style class names (e.g. CompositionSpace) or instance variable names (e.g. compositionSpace).
	 * 
	 * @return	The default implementation returns true.
	 */
	protected boolean convertNameStyles()
	{
		return true;
	}

	/**
	 * @return the parent
	 */
	protected ElementState parent()
	{
		return parent;
	}
    /**
     * @param floatingPrecision the floatingPrecision to set
     */
    public void setFloatingPrecision(short floatingPrecision)
    {
        this.floatingPrecision = floatingPrecision;
    }
    
    public static void setDeclarationStyle(DeclarationStyle ds)
    {
    	declarationStyle	= ds;
    }
    
    static DeclarationStyle declarationStyle()
    {
    	return declarationStyle;
    }
    static boolean isPublicDeclarationStyle()
    {
    	return declarationStyle() == DeclarationStyle.PUBLIC;
    }
    /**
     * Annotation that tells ecologylab.xml translators that each Field it is applied to as a keyword
     * is a scalar-value,
     * which should be represented in XML as an attribute.
     *
     * @author andruid
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Inherited
    public @interface xml_attribute
    {

    }

    /**
     * Value for the leaf annotation that specifies translation to XML as CDATA.
     */
    public static final int		CDATA	= 1;
    /**
     * Value for the leaf annotation that specifies translation to XML without CDATA.
     */
    public static final int		NORMAL	= 0;
    
    static final 		String	EMPTY	= "";
    /**
     * Annotation that tells ecologylab.xml translators that each Field it is applied to as a keyword
     * is a scalar-value,
     * which should be represented in XML as a leaf node.
     *
     * @author andruid
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Inherited
    public @interface xml_leaf
    {
    	int value() default NORMAL;
    }

    /**
     * Annotation that tells ecologylab.xml translators that each Field it is applied to as a keyword
     * is a complex nested field, which requires further translation.
     *
     * @author andruid
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Inherited
    public @interface xml_nested
    {

    }
    static final String NULL_TAG	= "";
    /**
     * Annotation that tells ecologylab.xml translators that each Field it is applied to as a keyword
     * is a complex nested field, which requires further translation.
     *
     * @author andruid
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Inherited
    public @interface xml_collection
    {
       	String value() default NULL_TAG;
    }
    
    /**
     * Annotation that tells ecologylab.xml translators that each Field it is applied to as a keyword
     * is a complex nested field, which requires further translation.
     *
     * @author andruid
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Inherited
    public @interface xml_map
    {
       	String value() default NULL_TAG;
    }
    
    /**
     * Annotation that tells ecologylab.xml translators that this field has a name that cannot be dynamically generated.
     * This name is specified by the value of this annotation.
     * 
     * Note that programmers should be careful when specifying an xml_tag, to ensure that there are no collisions with
     * other names. Note that when an xml_tag is specified for a field, it will ALWAYS EMIT AND TRANSLATE FROM USING
     * THAT NAME.
     * 
     * xml_tag's should typically be something that cannot be represented using dynamically-generated names, such as
     * utilizing characters that are not normally allowed in field names, but that are allowed in XML names. This can be
     * particularly useful for building ElementState objects out of XML from the wild.
     * 
     * Remember, you cannot use XML-forbidden characters or constructs in an xml_tag!
     * 
     * @xml_tag you MUST create your translation space using a Class object instead of a pair of Strings!!!
     * 
     * TODO we can fix the above issue by translating the Strings into a Class object and checking that -- but that's
     * for another day.
     * 
     * @author Zach
     * 
     */
    @Retention(RetentionPolicy.RUNTIME) 
    @Inherited 
    public @interface xml_tag
    {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME) 
    @Inherited 
    public @interface xml_class
    {
        Class value();
    }
    
    @Retention(RetentionPolicy.RUNTIME) 
    @Inherited 
    public @interface xml_classes
    {
        Class[] value();
    }
    
	public void checkAnnotation() throws NoSuchFieldException
	{
		System.out.println(" isValidatable = " + this.getClass().isAnnotationPresent(xml_inherit.class));
		Field f		= this.getClass().getField("foo");
		System.out.println(" is leaf = " + XmlTools.representAsLeafNode(f));
	}
	/**
	 * @return Returns the optimizations.
	 */
	protected Optimizations optimizations()
	{
		return optimizations;
	}

	/**
	 * Perform custom processing on the newly created child node,
	 * just before it is added to this.
	 * <p/>
	 * This is part of depth-first traversal during translateFromXML().
	 * <p/>
	 * This, the default implementation, does nothing.
	 * Sub-classes may wish to override.
	 * 
	 * @param child
	 */
	protected void createChildHook(ElementState child)
	{
		
	}
    
    /**
     * Perform custom processing immediately before translating this to XML. 
     * <p/>
     * This, the default implementation, does nothing. Sub-classes may wish to override.
     *
     */
    protected void preTranslationProcessingHook()
    {

    }
    
    /**
     * Perform custom processing immediately after all translation from XML is
     * completed. This allows a newly-created ElementState object to perform any
     * post processing with all the data it will have from XML.
     * <p/>
     * This method is called by NodeToJavaOptimizations.createChildElement() or
     * translateToXML depending on whether the element in question is a child or
     * the top-level parent.
     * <p/>
     * This, the default implementation, does nothing. Sub-classes may wish to
     * override.
     * 
     */
    protected void postTranslationProcessingHook()
    {

    }
    
    /**
     * Clear data structures and references to enable garbage collecting of resources associated with this.
     */
    public void recycle()
    {
    	if (parent == null)
    	{	// root state!
    		if (elementByIdMap != null)
    		{
    			elementByIdMap.clear();
    			elementByIdMap	= null;
    		}
    	}
    	else
    		parent		= null;
    	
    	elementByIdMap	= null;
    	textNodeBuffy	= null;
    	optimizations	= null;  
    	if (nestedNameSpaces != null)
    	{
    		for (ElementState nns : nestedNameSpaces.values())
    		{
    			if (nns != null)
    				nns.recycle();
    		}
    		nestedNameSpaces.clear();
    		nestedNameSpaces	= null;
    	}
    }
    
    /**
     * Add a NestedNameSpace object to this.
     * 
     * @param id
     * @param nns
     */
    public void putNestedNameSpace(String id, ElementState nns)
    {
    	if (nestedNameSpaces == null)
    		nestedNameSpaces	= new HashMap<String, ElementState>(2);
    	
    	nestedNameSpaces.put(id, nns);
    }
}
