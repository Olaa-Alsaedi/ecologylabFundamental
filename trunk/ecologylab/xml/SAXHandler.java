/**
 * 
 */
package ecologylab.xml;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import ecologylab.generic.Debug;
import ecologylab.net.ConnectionAdapter;
import ecologylab.net.PURLConnection;
import ecologylab.net.ParsedURL;
import ecologylab.xml.types.element.Mappable;

/**
 * Use SAX to translate XML into a typed tree of ElementState objects.
 *
 * @author andruid
 */
public class SAXHandler 
extends Debug 
implements ContentHandler, OptimizationTypes
{
	final TranslationSpace	translationSpace;
	
	ElementState					root;
	
	private XMLReader 				parser;
	
	/**
	 * Current "DOM" frame state.
	 */
	ElementState					currentElementState;
	
	/**
	 * Optimizations for current field.
	 */
	NodeToJavaOptimizations			currentN2JO;
	
	XmlTranslationException			xmlTranslationException;
	
	ArrayList<NodeToJavaOptimizations>	n2joStack	= new ArrayList<NodeToJavaOptimizations>();
	
	/**
	 * 
	 */
	public SAXHandler(TranslationSpace translationSpace)
	{
		this.translationSpace		= translationSpace;

		try 
		{
			parser 					= XMLReaderFactory.createXMLReader();
			parser.setContentHandler(this);
		} catch (Exception e)
		{
			parser					= null;
		}
	}
	
	public ElementState parseString(String xmlString)
	throws XmlTranslationException
	{
		StringReader reader	= new StringReader(xmlString);
		ElementState result = parse(reader);
		reader.close();

		return result;
	}
	static final ConnectionAdapter connectionAdapter = new ConnectionAdapter();
	
	public ElementState parse(ParsedURL purl)
	throws XmlTranslationException
	{
		if (purl.isFile())
			return parse(purl.file());
		
		PURLConnection purlConnection		= purl.connect(connectionAdapter);
		ElementState result = parse(purlConnection.inputStream());
		purlConnection.recycle();
		return result;
	}	
	public ElementState parse(File file)
	throws XmlTranslationException
	{
		try
		{
//			FileReader fileReader			= new FileReader(file);
			FileInputStream fileInputStream			= new FileInputStream(file);
			BufferedInputStream bufferedStream	= new BufferedInputStream(fileInputStream);
			ElementState elementState 		= parse(bufferedStream);
			bufferedStream.close();
			return elementState;
//			return parse(fileInputStream);
//			BufferedReader bufferedReader	= new BufferedReader(fileReader);
//			BufferedReader bufferedReader	= new BufferedReader(fileReader);
//			return parse(bufferedReader);
//			return parse(fileReader);
			
		} catch (FileNotFoundException e)
		{
			throw new XmlTranslationException("Can't open file " + file.getAbsolutePath(), e);
		} catch (IOException e)
		{
			throw new XmlTranslationException("Can't close file " + file.getAbsolutePath(), e);
		}		
	}	
	public ElementState parse(String uri)
	throws XmlTranslationException
	{
		InputSource inputSource = new InputSource(uri);
		return parse(inputSource);
		//TODO -- should we close something here, like getCharacterStream?
	}
	public ElementState parse(Reader reader)
	throws XmlTranslationException
	{
		InputSource inputSource = new InputSource(reader);
		ElementState result		= parse(inputSource);
		//TODO -- put this into a finally from the parse
		try
		{
			reader.close();
		} catch (IOException e)
		{
			throw new XmlTranslationException("Can't close reader: " + reader, e);
		}
		return result;
	}
	public ElementState parse(InputStream inputStream)
	throws XmlTranslationException
	{
		ElementState result	= parse(new InputSource(inputStream));
//		try
//		{
//			inputStream.close();
//		} catch (IOException e)
//		{
//			e.printStackTrace();
//		}
		return result;
	}
	public ElementState parse(InputSource inputSource)
	throws XmlTranslationException
	{
		try
		{
			parser.parse(inputSource);
		} catch (IOException e)
		{
			xmlTranslationException	= new XmlTranslationException("IOException durng parsing", e);
		} catch (SAXException e)
		{
			xmlTranslationException	= new XmlTranslationException("SAXException durng parsing", e);
		}
		if (xmlTranslationException != null)
			throw xmlTranslationException;
		return root;
	}
	private void setRoot(ElementState root)
	{
		this.root					= root;
		this.currentElementState	= root;
	}
	
	private Optimizations currentOptimizations()
	{
		return this.currentElementState.optimizations;
	}
	
	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String uri, String localName, String tagName, Attributes attributes) 
	throws SAXException
	{
		if (xmlTranslationException != null)
			return;
		if (root == null)
		{	// form the root ElementState!
			Class rootClass= translationSpace.xmlTagToClass(tagName);
			if (rootClass != null)
			{
				ElementState root;
				try
				{
					root = (ElementState) XmlTools.getInstance(rootClass);
					if (root != null)
					{
						root.setupRoot();
						setRoot(root);
						root.translateAttributes(translationSpace, attributes);
					}
				} catch (XmlTranslationException e)
				{
					xmlTranslationException	= e;
				}
			}
			else
			{
				// else, we dont translate this element; we ignore it.
				String message = "XML Translation WARNING: Cant find class object for Root XML element <"
						+ tagName + ">: Ignored. ";
				println(message);
				xmlTranslationException		= new XmlTranslationException(message);
			}
			
			return;
		}
		
		NodeToJavaOptimizations activeN2JO	= (currentN2JO != null) && (currentN2JO.type() == IGNORED_ELEMENT) ?
			// new NodeToJavaOptimizations(tagName) : // (nice for debugging; slows us down)
			NodeToJavaOptimizations.IGNORED_ELEMENT_OPTIMIZATIONS :
			currentOptimizations().elementNodeToJavaOptimizations(translationSpace, currentElementState, tagName);
		this.currentN2JO						= activeN2JO;
		pushN2JO(activeN2JO);
//		printStack("After push");
		
		ElementState currentElementState	= this.currentElementState;
		ElementState childES				= null;
		try
		{
			switch (activeN2JO.type())
			{
			case REGULAR_NESTED_ELEMENT:
				childES							= activeN2JO.constructChildElementState(currentElementState);
				activeN2JO.setFieldToNestedObject(currentElementState, childES); // maybe we should do this on close element
				break;
			case LEAF_NODE_VALUE:
				// wait for characters to set scalar field
				// activeNJO.setScalarFieldWithLeafNode(activeES, childNode);
				break;
			case COLLECTION_ELEMENT:
				Collection collection			= activeN2JO.getCollection(currentElementState);
				if (collection != null)
				{
					childES						= activeN2JO.constructChildElementState(currentElementState);
					collection.add(childES);
				}
				//activeNJO.formElementAndAddToCollection(activeES, childNode);
				break;
			case COLLECTION_SCALAR:
				// wait for characters to create scalar reference type and add to collection
//				activeNJO.addLeafNodeToCollection(activeES, childNode);
				break;
			case MAP_ELEMENT:
				Map map							= activeN2JO.getMap(currentElementState);
				if (map != null)
				{
					childES						= activeN2JO.constructChildElementState(currentElementState);
					map.put(((Mappable) childES).key(), childES);
				}
				break;
			case OTHER_NESTED_ELEMENT:
				childES							= activeN2JO.constructChildElementState(currentElementState);
				if (childES != null)
					currentElementState.addNestedElement(childES);
				break;
			case IGNORED_ELEMENT:
				// should get a set of Optimizations for this, to represent its subfields
			case BAD_FIELD:
			default:
				break;

			}
			if (childES != null)
			{
				// fill in its attributes
				childES.translateAttributes(translationSpace, attributes);
				this.currentElementState		= childES;	// childES.parent = old currentElementState
				this.currentN2JO					= activeN2JO;
			}
		} catch (XmlTranslationException e)
		{
			this.xmlTranslationException		= e;
		}
	}

	private void pushN2JO(NodeToJavaOptimizations n2jo)
	{
		this.n2joStack.add(n2jo);
	}
	private void popAndPeekN2JO()
	{
		ArrayList<NodeToJavaOptimizations> stack = this.n2joStack;
		int last	= stack.size() - 1;
		if (last >= 0)
		{
			NodeToJavaOptimizations result	= stack.remove(last--);
			if (last >= 0)
				result	= stack.get(last);
			this.currentN2JO	= result;
//			printStack("After Pop");
		}
	}
	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String arg0, String arg1, String arg2)
			throws SAXException
	{
		if (xmlTranslationException != null)
			return;
		
		int length = currentLeafValue.length();
		if (length > 0)
		{
			switch (currentN2JO.type())
			{
			case LEAF_NODE_VALUE:
				//TODO -- unmarshall to set field with scalar type
				// copy from the StringBuilder
				String value	= new String(currentLeafValue.substring(0, length));
				currentN2JO.setFieldToScalar(currentElementState, value);
				break;
			default:
				break;
			}
			currentLeafValue.setLength(0);
		}
		switch (this.currentN2JO.type())	// every good push deserves a pop :-) (and othertimes, not!)
		{
		case REGULAR_NESTED_ELEMENT:
		case COLLECTION_ELEMENT:
		case MAP_ELEMENT:
			this.currentElementState	= currentElementState.parent;	// restore context!
			break;
		default:
			break;
		}
		
		popAndPeekN2JO();
		//if (this.startElementPushed)	// every good push deserves a pop :-) (and othertimes, not!)
	}
	void printStack(String msg)
	{
		currentElementState.debug("Stack -- " + msg);
		for (NodeToJavaOptimizations thatN2JO : n2joStack)
		{
			println(thatN2JO.tag() + " - " + thatN2JO.type());
		}
		println("");
	}
	StringBuilder currentLeafValue	= new StringBuilder(1024);
	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] chars, int startIndex, int length) 
	throws SAXException
	{
		if (xmlTranslationException != null)
			return;

		if (currentN2JO != null)
		{
			switch (currentN2JO.type())
			{
			case LEAF_NODE_VALUE:
				String leafValue = new String(chars, startIndex, length);
				//debug(currentElementState + " - hi LEAF_NODE_VALUE characters(): " + leafValue);
				currentLeafValue.append(chars, startIndex, length);
				//TODO -- unmarshall to set field with scalar type
				break;
			case COLLECTION_SCALAR:
				//TODO -- unmarshall to get scalar reference type value
				//TODO -- add value to collection
				break;
			case MAP_SCALAR:
				//TODO -- unmarshall to get scalar reference type value
				//TODO -- put value in map
				break;
			default:
				//TODO ?! can we dump characters in this case, or should we append to textNode?!
				//currentElementState.appendLeafXML(buffy, leafElementName, leafValue, needsEscaping, isCDATA)
				break;
			}
		}
	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException
	{
		if ((xmlTranslationException != null) && (root != null))
			root.postTranslationProcessingHook();
	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
			throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator)
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 *
	 * ${tags}
	 *
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri)
			throws SAXException
	{
		// TODO Auto-generated method stub

	}

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		println("YO!!!");
		//new SAXHandler(null, null);

	}

	public XMLReader getParser()
	{
		return parser;
	}
	/**
	 * @return the root
	 */
	public ElementState root()
	{
		return root;
	}

}
