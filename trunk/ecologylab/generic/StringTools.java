package ecologylab.generic;

import java.net.*;
import java.util.*;

import ecologylab.collections.CollectionTools;
import ecologylab.net.ParsedURL;


/**
 * A set of lovely convenience methods for doing operations on 
 * {@link java.lang.String String} s and 
 * {@link java.lang.StringBuffer StringBuffer}s.
 */
public class StringTools
extends Debug
{
   static final String[]	oneDotDomainStrings = 
   {
      "com", "edu", "gov", "org", "net", "tv",
   };
   static final HashMap	oneDotDomains	= 
      CollectionTools.buildHashMapFromStrings(oneDotDomainStrings);

   public static final String	EMPTY_STRING	= "";
   
/**
 * Changes the StringBuffer to lower case, in place, without any new storage
 * allocation.
 */
   public static final void toLowerCase(StringBuffer buffer)
   {
	   int length	= buffer.length();
	   for (int i=0; i<length; i++)
	   {
		   char c		= buffer.charAt(i);
//		   if (Character.isUpperCase(c))
		   // A = 0x41, Z = 0x5A; a = 0x61, z = 0x7A
		   if ((c >='A') && (c <= 'Z'))
		   {
			   c	       += 0x20;
			   buffer.setCharAt(i, c);
		   }
	   }
   }
   public static final boolean sameDomain(URL url1, URL url2)
   {
      return domain(url1).equals(domain(url2));
   }
   public static final String domain(URL url)
   {
	   if (url.getProtocol().equals("file"))
		   return "filesystem.local";
	   else
		   return domain(url.getHost());
   }
/**
 * Useful for finding common domains.
 */
   public static final String domain(String urlString)
   {
	   if ((urlString == null) || (urlString.length() == 0))
		   return null;
	   int end	= urlString.length() - 1;
	   int domainStartingDot	= 0;
	   boolean foundFirstDot	= false;
	   boolean foundSecondDot	= false;
	   boolean international	= false;
	   for (int i=end; i>0; i--)
	   {
		   if (urlString.charAt(i) == '.')
		   {
			   if (foundFirstDot)
			   {
				   if (international && !foundSecondDot)
				   {
					   foundSecondDot	= true;
				   }
				   else
				   {
					   domainStartingDot	= i + 1;
					   break;
				   }
			   }
			   else
			   {
				   foundFirstDot	= true;
				   String suffix	= urlString.substring(i+1);
				   international	= !oneDotDomains.containsKey(suffix);
			   }
		   }
	   }
	   return urlString.substring(domainStartingDot, end + 1);
   }
   
/**
 * Use this method to efficiently get a <code>String</code> from a
 * <code>StringBuffer</code> on those occassions when you plan to keep
 * using the <code>StringBuffer</code>, and want an efficiently made copy.
 * In those cases, <i>much</i> better than 
 * <code>new String(StringBuffer)</code>
 */
   public static final String toString(StringBuffer buffer)
   {
      return buffer.substring(0);
   }
   public static final boolean contains(String in, String toMatch)
   {
      return (in == null) ? false : in.indexOf(toMatch) != -1;
   }
   public static final boolean contains(String in, char toMatch)
   {
      return (in == null) ? false : in.indexOf(toMatch) != -1;
   }

/**
 * Very efficiently forms String representation of url (better than 
 * <code>URL.toExternalForm(), URL.toString()</code>). Doesn't include query or anchor.
 */
   public static final String noAnchorNoQueryPageString(URL u)
   {
      String protocol	= u.getProtocol();
      String authority	= u.getAuthority(); // authority is host:port
      String path	= u.getPath();	    // doesn't include query

      int pathLength	= (path == null) ? 0 : path.length();
      // pre-compute length of StringBuffer
      int length =0;
      
      try
      {
      	length	=
      		protocol.length() + 3 /* :// */ + authority.length() + pathLength;
      } catch (Exception e)
	  {
      	Debug.println("protocol="+protocol+" authority="+authority+
		     u.toExternalForm());
      	e.printStackTrace();
	  }
      
      StringBuffer result = new StringBuffer(length);
      result.append(protocol).append("://").append(authority).append(path);

      return new String(result);
   }
   
   
   public static final String noAnchorPageString(URL u)
   {
      String protocol	= u.getProtocol();
      String authority	= u.getAuthority(); // authority is host:port
      String path	= u.getPath();	    // doesn't include query
      String query  = u.getQuery();

      int pathLength	= (path == null) ? 0 : path.length();
      int queryLength	= (query == null) ? 0: query.length();
      
      // pre-compute length of StringBuffer
      int length =0;
      
      try
      {
    	  length	=
    		  protocol.length() + 3 /* :// */ + authority.length() + pathLength + 1/* ? */ + queryLength;
      } catch (Exception e)
      {
    	  Debug.println("protocol="+protocol+" authority="+authority+
    			  u.toExternalForm());
    	  e.printStackTrace();
      }
      
      StringBuffer result = new StringBuffer(length);
      result.append(protocol).append("://").append(authority).append(path);
      if(query != null)
    	  result.append("?").append(query);

      return new String(result);
   }

   public static final String pageString(URL u)
   {
      String protocol	= u.getProtocol();
      String authority	= u.getAuthority(); // authority is host:port
      String path	= u.getPath();	    // doesn't include query
      String query  = u.getQuery();
      String anchor = u.getRef();

      int pathLength	= (path == null) ? 0 : path.length();
      int queryLength	= (query == null) ? 0: query.length();
      int anchorLength	= (anchor == null) ? 0: anchor.length();
      int authorityLength	= (authority == null) ? 0: authority.length();

      // pre-compute length of StringBuffer
      int length =0;
      
      try
      {
    	  length	=
    		  protocol.length() + 3 /* :// */ + authorityLength + pathLength
    		  + 1 /* ? */ + queryLength + 1 /* # */ + anchorLength;
      } catch (Exception e)
      {
    	  Debug.println("protocol="+protocol+" authority="+authority+
    			  u.toExternalForm());
    	  e.printStackTrace();
      }
      
      StringBuffer result = new StringBuffer(length);
      result.append(protocol).append("://");
      if (authority != null)
    	  result.append(authority);
      if (path != null)
    	  result.append(path);
      if(query != null)
    	  result.append("?").append(query);
      if(anchor != null)
    	  result.append("#").append(anchor);
      
      return new String(result);
   }
   
   public static final URL urlRemoveAnchorIfNecessary(URL source)
   {
//Below operation is already in the urlNoAnchor();      
//   	String anchor			= source.getRef();
//      return (anchor == null) ? source : urlNoAnchor(source);
   		return urlNoAnchor(source);
   	
   }
   
   public static final URL urlNoAnchor(URL source)
   {
	   URL result = null;
	   
	   if (source==null)
		   return result;
	   
	   if (source.getRef() == null)
		   return source;
	   
	   try
	   {
		   result= new URL(source.getProtocol(), source.getHost(),
				   source.getPort(), source.getFile());
	   } catch (MalformedURLException e)
	   {
		   e.printStackTrace();
		   throw new RuntimeException("Cant form noHashUrl from " +
				   source.toString());
	   }
	   return result;
   }
/*      
   public static void main(String[] args)
   {
      for (int i=0; i<args.length; i++)
	 println(pageString(Generic.getURL(args[i], "oops " + i)));
	 
   }
 */
   
/**
 * Parse file name or variable name spellings, to convert to a set of words.
 * 
 * @param in	 input <code>String</code>, for example: "isFileName".
 *
 * @return An array of <code>String</code>s, for example: "is", "file", "name".
 */   
   public static String[] seperateLowerUpperCase(String in)
   {
	   int n = in.length();
	   // pass 1 -- just find out how many transitions there are?
	   int numWords = 1;
	   for (int i=0; i<n; i++)
	   {
		   char thisChar = in.charAt(i);
		   if (Character.isUpperCase(thisChar) && (i != 0))
			   numWords++;
	   }
	   // pass 2 -- create the result set and fill it in
	   String result[]	= new String[numWords];
	   int resultIndex = 0;
	   int transition  = 0;
	   char[] buffer = new char[n];
	   for (int i=0; i<n; i++)
	   {
		   char thisChar = in.charAt(i);
		   if (Character.isUpperCase(thisChar))
		   {
			   thisChar = Character.toLowerCase(thisChar);
			   if (i > 0)
			   {
				   result[resultIndex++]	= 
					   new String(buffer, transition, (i - transition));
//				   result[resultIndex++]	= in.substring(transition, i);
				   transition		= i;
			   }
		   }
		   buffer[i]	= thisChar;
	   }
	   result[resultIndex]= new String(buffer, transition, (n - transition));
	   return result;
   }
/**
 * Remove all instances of @param c from @arg string
 */   
   public static String remove(String string, char c)
   {
	   int index;
	   
	   while ((index = string.indexOf(c)) > -1)
	   {
		   int length	= string.length();
		   if (index == 0)
			   string	= string.substring(1);
		   else if (index == (length - 1))
			   string	= string.substring(0, length-1);
		   else
			   string	= string.substring(0,index) +string.substring(index+1);
	   }
	   return string;
   }
   public static final String FIND_PUNCTUATION_REGEX = 
      "(:)|(\\d)|(\\.)|(/++)|(=)|(\\?)|(\\-)|(\\+)|(_)|(%)|(\\,)";        
/**
 * Turn punctuation into space delimiters.
 */
   public static String removePunctuation(String s)
   {
	   int length		= s.length();
	   StringBuffer buffy	= new StringBuffer(length);
	   
	   boolean	wasSpace	= true;
	   
	   for (int i=0; i<length; i++)
	   {
		   char c			= s.charAt(i);
		   if (Character.isLetter(c))
		   {
			   buffy.append(c);
			   wasSpace		= false;
		   }
		   else
		   {
			   if (!wasSpace)
//				   buffy.append('-');
				   buffy.append(' ');
			   wasSpace		= true;
		   }
	   }
	   return new String(buffy);
   }
   
   public static String removePunctuation2(String s)
   {
      return s.replaceAll(FIND_PUNCTUATION_REGEX, " ");
   }

   public static void main(String[] s)
   {
   	  /* create ParsedURL from url string. */
      ParsedURL u = ParsedURL.getAbsolute("http://www.bbc.co.uk/eastenders/images/navigation/icon_bbc_one.gif", "foo");
//      println(removePunctuation("http://www.bbc.co.uk/eastenders/images/navigation/icon_bbc_one.gif"));
      println(u.removePunctuation());
   }
   public static void main2(String[] s)
   {
	   for (int i=0; i<s.length; i++)
	   {
		   String[] result = seperateLowerUpperCase(s[i]);
		   System.out.print(s[i] + " -> " );
		   for (int j=0; j<result.length; j++)
			   System.out.print(result[j] + " ");
		   System.out.println();
	   }   	
   }
/**
 * Reset the StringBuffer, so that is empty and ready for reuse.
 * Do this with a minimum of overhead, given the latest vagaries of the
 * JDK implementation.
 */
   public static final void clear(StringBuffer buffy)
   {
	   // as of JDK1-4 .setLength(0) initiates horrible re-allocation of
	   // a tiny buffer, so use this weirdness, which looks like the 
	   // most reasonable option
	   int length	= buffy.length();
	   if (length > 0)
		   buffy.delete(0, length);
   }
   /**
    * Return true iff all the characters in the argument are lower case.
    * @param s
    * @return
    */
   public static boolean isLowerCase(String s)
   {
	   int length	= s.length();
	   for (int i=0; i< length; i++)
	   {
		   if (!Character.isLowerCase(s.charAt(i)))
			   return false;
	   }
	   return true;
   }
   
	/**
	 * @param path
	 * @return true if the String ends with a forward slash, like a nice directory.
	 */
	public static boolean endsWithSlash(String path)
	{
		return path.charAt(path.length() - 1) == '/';
	}
	
	/**
	 * Find the last parenthesis given the location of the first one
	 * 
	 * @param relationFrag The relation
	 * @param startLoc	The open parenthesis location in the string
	 * @return	The location of the close parenthesis matching the open one
	 */
	public static int findMatchingParenLoc(String relationFrag, int startLoc)
	{
		final char L_PARENTHESIS = '(';
		final char R_PARENTHESIS = ')';
		
		/**
		 * Bad start location
		 */
		if (startLoc >= relationFrag.length() || 
				relationFrag.charAt(startLoc) != L_PARENTHESIS)
			return -1;
		
		char[] relation = relationFrag.toCharArray();
		int numUnmatchedParens = 0;
		for (int i=startLoc; i<relation.length; i++)
		{
			if (relation[i] == L_PARENTHESIS)
				numUnmatchedParens++;
			else if (relation[i] == R_PARENTHESIS)
				numUnmatchedParens--;
			
			if (numUnmatchedParens == 0)
				return i;
		}
		
		//didn't find a matching parenthesis
		return -1;
	}

}

