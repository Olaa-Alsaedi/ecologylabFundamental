package ecologylab.serialization.library.html;

import java.util.ArrayList;

import ecologylab.serialization.annotations.simpl_collection;
import ecologylab.serialization.annotations.simpl_inherit;
import ecologylab.serialization.annotations.simpl_nowrap;

@simpl_inherit
public class Table extends HtmlElement
{
	@simpl_nowrap
	@simpl_collection("tr")	
	public ArrayList<Tr>	rows;

	public Table()
	{
		rows = new ArrayList<Tr>();
	}
	
	
	static int count=0;
	boolean printOnceLock = false;
	public String toString()
	{
		count+=1;
		int mycount = count;
		if(printOnceLock)
		   return mycount+"table-lockreached";
		printOnceLock = true;
		String returnString = "table"+mycount+"\n================================\n";
		for(Tr row : rows)
		{
			returnString += " "+row.toString()+"\n";
		}
		returnString += "\n"+mycount+"================================\n";
		printOnceLock = false;
		return returnString;
	}
}
