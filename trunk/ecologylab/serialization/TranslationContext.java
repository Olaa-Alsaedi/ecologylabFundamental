package ecologylab.serialization;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.xml.sax.Attributes;

import ecologylab.collections.MultiMap;
import ecologylab.generic.Debug;
import ecologylab.net.ParsedURL;
import ecologylab.serialization.ElementState.FORMAT;
import ecologylab.serialization.TranslationScope.GRAPH_SWITCH;

public class TranslationContext extends Debug implements ScalarUnmarshallingContext, FieldTypes
{

	public static final String							SIMPL_NAMESPACE					= " xmlns:simpl=\"http://ecologylab.net/research/simplGuide/serialization/index.html\"";

	public static final String							SIMPL_ID								= "simpl:id";

	public static final String							SIMPL_REF								= "simpl:ref";

	public static final String							JSON_SIMPL_ID						= "simpl.id";

	public static final String							JSON_SIMPL_REF					= "simpl.ref";

	private MultiMap<Integer, ElementState>	marshalledObjects				= new MultiMap<Integer, ElementState>();

	private MultiMap<Integer, ElementState>	visitedElements					= new MultiMap<Integer, ElementState>();

	private MultiMap<Integer, ElementState>	needsAttributeHashCode	= new MultiMap<Integer, ElementState>();

	private HashMap<String, ElementState>		unmarshalledObjects			= new HashMap<String, ElementState>();

	protected ParsedURL											baseDirPurl;

	protected File													baseDirFile;

	protected String												delimiter								= ",";

	public TranslationContext()
	{

	}

	public TranslationContext(File fileDirContext)
	{
		if (fileDirContext != null)
			setBaseDirFile(fileDirContext);
	}

	public void setBaseDirFile(File fileDirContext)
	{
		this.baseDirFile = fileDirContext;
		this.baseDirPurl = new ParsedURL(fileDirContext);
	}

	public TranslationContext(ParsedURL purlContext)
	{
		this.baseDirPurl = purlContext;
		if (purlContext.isFile())
			this.baseDirFile = purlContext.file();
	}

	public boolean handleSimplIds(final String tag, final String value, ElementState elementState)
	{
		if (TranslationScope.graphSwitch == GRAPH_SWITCH.ON)
		{
			if (tag.equals(TranslationContext.SIMPL_ID))
			{
				markAsUnmarshalled(value, elementState);
				return true;
			}
			else
			{
				if (tag.equals(TranslationContext.SIMPL_REF))
				{
					return true;
				}
			}
		}

		return false;
	}
	
	public void markAsUnmarshalled(String value, ElementState elementState)
	{
		this.unmarshalledObjects.put(value, elementState);
	}

	public void resolveGraph(ElementState elementState)
	{
		if (TranslationScope.graphSwitch == GRAPH_SWITCH.ON)
		{
			// this.visitedElements.put(System.identityHashCode(elementState), elementState);
			this.visitedElements.put(elementState.hashCode(), elementState);

			ArrayList<FieldDescriptor> elementFieldDescriptors = elementState.classDescriptor()
					.elementFieldDescriptors();

			for (FieldDescriptor elementFieldDescriptor : elementFieldDescriptors)
			{
				Object thatReferenceObject = null;
				Field childField = elementFieldDescriptor.getField();
				try
				{
					thatReferenceObject = childField.get(elementState);
				}
				catch (IllegalAccessException e)
				{
					debugA("WARNING re-trying access! " + e.getStackTrace()[0]);
					childField.setAccessible(true);
					try
					{
						thatReferenceObject = childField.get(elementState);
					}
					catch (IllegalAccessException e1)
					{
						error("Can't access " + childField.getName());
						e1.printStackTrace();
					}
				}
				catch (Exception e)
				{
					System.out.println("yay");
				}
				// ignore null reference objects
				if (thatReferenceObject == null)
					continue;

				int childFdType = elementFieldDescriptor.getType();

				Collection thatCollection;
				switch (childFdType)
				{
				case COLLECTION_ELEMENT:
				case COLLECTION_SCALAR:
				case MAP_ELEMENT:
				case MAP_SCALAR:
					thatCollection = XMLTools.getCollection(thatReferenceObject);
					break;
				default:
					thatCollection = null;
					break;
				}

				if (thatCollection != null && (thatCollection.size() > 0))
				{
					for (Object next : thatCollection)
					{
						if (next instanceof ElementState)
						{
							ElementState compositeElement = (ElementState) next;

							if (this.alreadyVisited(compositeElement))
							{
								// this.needsAttributeHashCode.put(System.identityHashCode(compositeElement),
								// compositeElement);
								this.needsAttributeHashCode.put(compositeElement.hashCode(), compositeElement);
							}
							else
							{
								this.resolveGraph(compositeElement);
							}
						}
					}
				}
				else if (thatReferenceObject instanceof ElementState)
				{
					ElementState compositeElement = (ElementState) thatReferenceObject;

					if (this.alreadyVisited(compositeElement))
					{
						// this.needsAttributeHashCode.put(System.identityHashCode(compositeElement),
						// compositeElement);
						this.needsAttributeHashCode.put(compositeElement.hashCode(), compositeElement);
					}
					else
					{
						resolveGraph(compositeElement);
					}
				}
			}
		}
	}

	public boolean alreadyVisited(ElementState elementState)
	{
		// return this.visitedElements.contains(System.identityHashCode(elementState), elementState);
		return this.visitedElements.contains(elementState.hashCode(), elementState);
	}

	public void mapElementState(ElementState elementState)
	{
		if (TranslationScope.graphSwitch == GRAPH_SWITCH.ON)
		{
			// this.marshalledObjects.put(System.identityHashCode(elementState), elementState);
			this.marshalledObjects.put(elementState.hashCode(), elementState);
		}
	}

	public void appendSimplIdIfRequired(Appendable appendable, ElementState elementState,
			FORMAT format) throws IOException
	{
		if (TranslationScope.graphSwitch == GRAPH_SWITCH.ON && this.needsHashCode(elementState))
		{
			this.appendSimplIdAttribute(appendable, elementState, format);
		}
	}

	public boolean alreadyMarshalled(ElementState compositeElementState)
	{
		// return this.marshalledObjects.contains(System.identityHashCode(compositeElementState),
		// compositeElementState);
		return this.marshalledObjects.contains(compositeElementState.hashCode(), compositeElementState);
	}

	public void appendSimplNameSpace(Appendable appendable) throws IOException
	{
		appendable.append(SIMPL_NAMESPACE);
	}

	public void appendSimplRefId(Appendable appendable, ElementState elementState,
			FieldDescriptor compositeElementFD, FORMAT format, boolean withTag) throws IOException
	{
		switch (format)
		{
		case XML:
			appendXMLSimplRefId(appendable, elementState, compositeElementFD);
			break;
		case JSON:
			appendJSONSimplRefId(appendable, elementState, compositeElementFD, withTag);
			break;
		}
	}

	private void appendXMLSimplRefId(Appendable appendable, ElementState elementState,
			FieldDescriptor compositeElementFD) throws IOException
	{
		compositeElementFD.writeElementStart(appendable);
		appendXMLSimplIdAttributeWithTagName(appendable, SIMPL_REF, elementState);
		appendable.append("/>");
	}

	private void appendJSONSimplRefId(Appendable appendable, ElementState elementState,
			FieldDescriptor compositeElementFD, boolean withTag) throws IOException
	{
		
		compositeElementFD.writeJSONElementStart(appendable, withTag);
		appendJSONSimplIdAttributeWithTagName(appendable, JSON_SIMPL_REF, elementState, false);
		compositeElementFD.writeJSONCloseTag(appendable);
	}

	private void appendXMLSimplIdAttributeWithTagName(Appendable appendable, String tagName,
			ElementState elementState) throws IOException
	{
		appendable.append(' ');
		appendable.append(tagName);
		appendable.append('=');
		appendable.append('"');
		// appendable.append(((Integer) System.identityHashCode(elementState)).toString());
		appendable.append(((Integer) elementState.hashCode()).toString());
		appendable.append('"');
	}

	private void appendJSONSimplIdAttributeWithTagName(Appendable appendable, String tagName,
			ElementState elementState, boolean ifLast) throws IOException
	{
		if (ifLast)
		{
			appendable.append(',');
			appendable.append(' ');
		}
		
		appendable.append('"');
		appendable.append(tagName);
		appendable.append('"');
		appendable.append(':');
		appendable.append('"');
		appendable.append(((Integer) elementState.hashCode()).toString());
		appendable.append('"');
	}

	private void appendSimplIdAttribute(Appendable appendable, ElementState elementState,
			FORMAT format) throws IOException
	{
		switch (format)
		{
		case XML:
			appendXMLSimplIdAttributeWithTagName(appendable, SIMPL_ID, elementState);
			break;
		case JSON:
			appendJSONSimplIdAttributeWithTagName(appendable, JSON_SIMPL_ID, elementState, true);
			break;
		}
	}

	public boolean needsHashCode(ElementState elementState)
	{
		// return this.needsAttributeHashCode.contains(System.identityHashCode(elementState),
		// elementState);
		return this.needsAttributeHashCode.contains(elementState.hashCode(), elementState);
	}

	public boolean isGraph()
	{
		return this.needsAttributeHashCode.size() > 0;
	}

	public ElementState getFromMap(Attributes attributes)
	{
		ElementState unMarshalledObject = null;

		int numAttributes = attributes.getLength();
		for (int i = 0; i < numAttributes; i++)
		{
			final String tag = attributes.getQName(i);
			final String value = attributes.getValue(i);

			if (tag.equals(TranslationContext.SIMPL_REF))
			{
				unMarshalledObject = this.unmarshalledObjects.get(value);
			}
		}

		return unMarshalledObject;
	}
	
	public ElementState getFromMap(String value)
	{
		return this.unmarshalledObjects.get(value);
	}

	@Override
	public ParsedURL purlContext()
	{
		return (baseDirPurl != null) ? baseDirPurl : (baseDirFile != null) ? new ParsedURL(baseDirFile)
				: null;
	}

	@Override
	public File fileContext()
	{
		return (baseDirFile != null) ? baseDirFile : (baseDirPurl != null) ? baseDirPurl.file() : null;
	}

	public String getDelimiter()
	{
		return delimiter;
	}

}