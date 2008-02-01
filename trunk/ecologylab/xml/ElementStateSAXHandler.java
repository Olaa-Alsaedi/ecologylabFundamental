/**
 * 
 */
package ecologylab.xml;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import ecologylab.generic.Debug;
import ecologylab.generic.ReflectionTools;
import ecologylab.generic.StringInputStream;
import ecologylab.net.ConnectionAdapter;
import ecologylab.net.PURLConnection;
import ecologylab.net.ParsedURL;
import ecologylab.xml.types.element.Mappable;
import ecologylab.xml.NodeToJavaOptimizations;

/**
 * Use SAX to translate XML into a typed tree of ElementState objects.
 *
 * @author andruid
 */
public class ElementStateSAXHandler 
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
	
	XMLTranslationException			xmlTranslationException;
	
	ArrayList<NodeToJavaOptimizations>	n2joStack	= new ArrayList<NodeToJavaOptimizations>();
	
	/**
	 * 
	 */
	public ElementStateSAXHandler(TranslationSpace translationSpace)
	{
		this.translationSpace		= translationSpace;

		try 
		{
			parser 					= createXMLReader();
			parser.setContentHandler(this);
		} catch (Exception e)
		{
			parser					= null;
		}
	}
	
	static final String SUN_XERCES_PARSER_NAME		= "com.sun.org.apache.xerces.internal.parsers.SAXParser";
	
	static Class<? extends XMLReader> 		parserClass;
	
	static boolean		triedToFindParserClass;
	
	public static XMLReader createXMLReader() 
	throws SAXException
	{
		if (parserClass != null)
			return ReflectionTools.getInstance(parserClass);
		// else
	
		if (!triedToFindParserClass)
		{
			triedToFindParserClass	= true;
			try
			{
				parserClass				= (Class<? extends XMLReader>) Class.forName(SUN_XERCES_PARSER_NAME);
				return createXMLReader();
			} catch (Exception e)
			{
				
			}
		}
		// stuck doin it the slow way :-(
		return XMLReaderFactory.createXMLReader();
	}
/**
 * Parse the CharSequence of XML, using UTF-8 encoding.
 * 
 * @param charSequence
 * @return
 * @throws XMLTranslationException
 */
	public ElementState parse(CharSequence charSequence)
	throws XMLTranslationException
	{
		return parse(charSequence, StringInputStream.UTF8);
	}
	/**
	 * Parse the CharSequence of XML, given the charsetType encoding info.
	 * @param charSequence
	 * @param charsetType
	 * @return
	 * @throws XMLTranslationException
	 */
	public ElementState parse(CharSequence charSequence, int charsetType)
	throws XMLTranslationException
	{
		InputStream xmlStream		= new StringInputStream(charSequence, charsetType);
		ElementState result 		= parse(xmlStream);
		try
		{
			xmlStream.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return result;
	}
	
	public ElementState parseString(String xmlString)
	throws XMLTranslationException
	{
		StringReader reader	= new StringReader(xmlString);
		ElementState result = parse(reader);
		reader.close();

		return result;
	}
	static final ConnectionAdapter connectionAdapter = new ConnectionAdapter();
	

	/**
	 * Translate an XML document read from a URL to a strongly typed tree of XML objects.
	 * 
	 * Use SAX or DOM parsing depending on the value of useDOMForTranslateTo.
	 * 
	 * @param purl					XML source material.
	 * @param translationSpace		Specifies mapping from XML nodes (elements and attributes) to Java types.
	 * 
	 * @return						Strongly typed tree of ElementState objects.
	 * @throws XMLTranslationException
	 */
	public ElementState parse(URL url)
	throws XMLTranslationException
	{
		return parse(new ParsedURL(url));
	}	

	/**
	 * Translate an XML document read from a ParsedURL to a strongly typed tree of XML objects.
	 * 
	 * Use SAX or DOM parsing depending on the value of useDOMForTranslateTo.
	 * 
	 * @param purl					XML source material.
	 * @param translationSpace		Specifies mapping from XML nodes (elements and attributes) to Java types.
	 * 
	 * @return						Strongly typed tree of ElementState objects.
	 * @throws XMLTranslationException
	 */
	public ElementState parse(ParsedURL purl)
	throws XMLTranslationException
	{
		if (purl.isFile())
			return parse(purl.file());
		
		PURLConnection purlConnection		= purl.connect(connectionAdapter);
		ElementState result = parse(purlConnection.inputStream());
		purlConnection.recycle();
		return result;
	}	
	/**
	 * Translate a file from XML to a strongly typed tree of XML objects.
	 * 
	 * Use SAX or DOM parsing depending on the value of useDOMForTranslateTo.
	 * 
	 * @param file					XML source material.
	 * @param translationSpace		Specifies mapping from XML nodes (elements and attributes) to Java types.
	 * 
	 * @return						Strongly typed tree of ElementState objects.
	 * @throws XMLTranslationException
	 */
	
	public ElementState parse(File file)
	throws XMLTranslationException
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
			throw new XMLTranslationException("Can't open file " + file.getAbsolutePath(), e);
		} catch (IOException e)
		{
			throw new XMLTranslationException("Can't close file " + file.getAbsolutePath(), e);
		}		
	}	

	public ElementState parse(Reader reader)
	throws XMLTranslationException
	{
		InputSource inputSource = new InputSource(reader);
		ElementState result		= parse(inputSource);
		//TODO -- put this into a finally from the parse
		try
		{
			reader.close();
		} catch (IOException e)
		{
			throw new XMLTranslationException("Can't close reader: " + reader, e);
		}
		return result;
	}
	public ElementState parse(InputStream inputStream)
	throws XMLTranslationException
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
	throws XMLTranslationException
	{
		try
		{
			parser.parse(inputSource);
		} catch (IOException e)
		{
			xmlTranslationException	= new XMLTranslationException("IOException durng parsing", e);
		} catch (SAXException e)
		{
			xmlTranslationException	= new XMLTranslationException("SAXException durng parsing", e);
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

		NodeToJavaOptimizations activeN2JO		= null;
		final boolean isRoot 					= (root == null);
		if (isRoot)
		{	// form the root ElementState!
			Class<ElementState> rootClass	= translationSpace.xmlTagToClass(tagName);
			if (rootClass != null)
			{
				ElementState root;
				try
				{
					root					= XMLTools.getInstance(rootClass);
					if (root != null)
					{
						root.setupRoot();
						setRoot(root);
						root.translateAttributes(translationSpace, attributes);
						activeN2JO				= NodeToJavaOptimizations.ROOT_ELEMENT_OPTIMIZATIONS;
					}
					else
					{
						this.xmlTranslationException	= 
							new XMLTranslationException("Can't form root element for " + tagName);
						return;
					}
				} catch (XMLTranslationException e)
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
				xmlTranslationException		= new XMLTranslationException(message);
				return;
			}
		}
		else
		{
			activeN2JO	= (currentN2JO != null) && (currentN2JO.type() == IGNORED_ELEMENT) ?
				// new NodeToJavaOptimizations(tagName) : // (nice for debugging; slows us down)
				NodeToJavaOptimizations.IGNORED_ELEMENT_OPTIMIZATIONS :
				currentOptimizations().nodeToJavaOptimizations(translationSpace, currentElementState, tagName, false);
		}
		this.currentN2JO						= activeN2JO;
		registerXMLNS();
		//TODO? -- do we need to avoid this if null from an exception in translating root?
		pushN2JO(activeN2JO);
//		printStack("After push");
		
		if (isRoot)
			return;
		
		
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
			case NAME_SPACE_NESTED_ELEMENT:
				ElementState nsContext			= currentElementState.getNestedNameSpace(activeN2JO.nameSpaceID());
				childES							= activeN2JO.constructChildElementState(nsContext);
				activeN2JO.setFieldToNestedObject(nsContext, childES);
				break;
			case NAME_SPACE_LEAF_NODE:
				childES							= currentElementState.getNestedNameSpace(activeN2JO.nameSpaceID());
				
				break;
			case LEAF_NODE_VALUE:
				// wait for characters to set scalar field
				//activeN2JO.setScalarFieldWithLeafNode(activeES, childNode);
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
				//activeN2JO.addLeafNodeToCollection(activeES, childNode);
				break;
			case MAP_ELEMENT:
				Map map							= activeN2JO.getMap(currentElementState);
				if (map != null)
				{
					childES						= activeN2JO.constructChildElementState(currentElementState);
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
		} catch (XMLTranslationException e)
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
		ElementState currentES		= this.currentElementState;
		try
		{
			if (length > 0)
			{
				switch (currentN2JO.type())
				{
				case NAME_SPACE_LEAF_NODE:
				case LEAF_NODE_VALUE:
					//TODO -- unmarshall to set field with scalar type
					// copy from the StringBuilder
					String value	= new String(currentLeafValue.substring(0, length));
					currentN2JO.setFieldToScalar(currentES, value);
					break;
				case COLLECTION_SCALAR:
					value			= new String(currentLeafValue.substring(0, length));
					currentN2JO.addLeafNodeToCollection(currentES, value);
					break;
				default:
					break;
				}
				currentLeafValue.setLength(0);
			}
		} catch (XMLTranslationException e)
		{
			this.xmlTranslationException	= e;
		}
		final ElementState parentES					= currentES.parent;
		final NodeToJavaOptimizations currentN2JO	= this.currentN2JO;
		final int currentN2JOtype = currentN2JO.type();
		switch (currentN2JOtype)	// every good push deserves a pop :-) (and othertimes, not!)
		{
		case MAP_ELEMENT:
			final Object key 				= ((Mappable) currentES).key();
			Map map							= currentN2JO.getMap(parentES);
			map.put(key, currentES);
		case REGULAR_NESTED_ELEMENT:
		case COLLECTION_ELEMENT:
		case NAME_SPACE_NESTED_ELEMENT:
			if (parentES != null)
				parentES.createChildHook(currentES);
			else
				debug("cool - post ns element");
			currentES.postTranslationProcessingHook();
		case NAME_SPACE_LEAF_NODE:
		case OTHER_NESTED_ELEMENT:
			this.currentElementState		= parentES;	// restore context!
			break;
		default:
			break;
		}
		// end of the Namespace object, so we gotta pop it off, too.
		if (currentN2JOtype == NAME_SPACE_NESTED_ELEMENT)
			this.currentElementState		= this.currentElementState.parent;
		popAndPeekN2JO();
		//if (this.startElementPushed)	// every good push deserves a pop :-) (and othertimes, not!)
	}
	void printStack(String msg)
	{
		currentElementState.debug("Stack -- " + msg + "\t[" + this.currentElementState + "]");
		for (NodeToJavaOptimizations thatN2JO : n2joStack)
		{
			println(thatN2JO.tag() + " - 0x" + Integer.toHexString(thatN2JO.type()));
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
			case COLLECTION_SCALAR:
			case NAME_SPACE_LEAF_NODE:
				String leafValue = new String(chars, startIndex, length);
				//debug(currentElementState + " - hi LEAF_NODE_VALUE characters(): " + leafValue);
				currentLeafValue.append(chars, startIndex, length);
				//TODO -- unmarshall to set field with scalar type
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
	public void startPrefixMapping(String nsID, String urn)
			throws SAXException
	{
//		debug("Hi: startPrefixMapping(" + nsID +" := " + urn);
//		this.nameSpacePrefix	= prefix;
//		this.nameSpaceURN		= urn;
		if (nsID.length() > 0)	// these days, ignore ns decls without an id (default ones)
		{
			// push the urn in first; pop it off 2nd
			xmlnsStack.add(urn);
			// push the nsID in 2nd; pop it off 1st
			xmlnsStack.add(nsID);
		}
	}
	
//	String	nameSpacePrefix;
//	
//	String	nameSpaceURN; //FIXME -- this should be a stack!
	
	ArrayList<String>	xmlnsStack	= new ArrayList<String>(2);
	
	void registerXMLNS()
	{
		int size	= xmlnsStack.size();
		while (size >= 2)
		{
			String nameSpaceID	= xmlnsStack.remove(--size);
			String urn			= xmlnsStack.remove(--size);
			if ((nameSpaceID != null) && (urn != null))
			{
				registerXMLNS(this.currentElementState, nameSpaceID, urn);
			}
		}
//		String urn = nameSpaceURN;
//		if (urn != null)
//		{
//			registerXMLNS(this.currentElementState, nameSpacePrefix, urn);
//			nameSpaceURN	= null;
//			nameSpacePrefix	= null;
//		}
	}

	
	/**
	 * Create a name space object, nested in the context, using info saved in this.
	 * 
	 * @param context
	 * @param urn		The value of the xmlns:id attribute is the URL that is mapped to the class.
	 */
	private void registerXMLNS(ElementState context, String prefix, String urn)
	{
		if (context != null)
			context.optimizations.mapNamespaceIdToClass(translationSpace, prefix, urn);
		else
			println("ERROR: Null context. Can't register xmlns:" + prefix + "=" + urn);
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
