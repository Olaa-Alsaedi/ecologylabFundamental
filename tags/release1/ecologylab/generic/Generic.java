package cm.generic;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.net.*;
import java.io.*;

/**
 * @author andruid
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class Generic 
{
/**
 * Get a boolean parameter from the runtime environment. If the value is the
 * string <code>true</code> or <code>yes</code>, the result is 
 * <code>true</code>; else false.
 * 
 * @param	name	The name of the parameter's key.
 */
   public static boolean parameterBool(String name)
   {
      String value	= parameter(name);
      boolean result	= value != null;
      if (result)
	 result		=  value.equalsIgnoreCase("true") ||
	    value.equalsIgnoreCase("yes") || (value.equals("1"));
      return result;
   }
   public static boolean parameterBool(String name, boolean defaultValue)
   {
      String param	= parameter(name);
      boolean result;
      if (param != null)
	 result		=  param.equalsIgnoreCase("true") ||
	    param.equalsIgnoreCase("yes") || (param.equals("1"));
      else
	 result		= defaultValue;
      return result;
   }
/**
 * Get an integer parameter from the runtime environment. The default is 0.
 * 
 * @param	name	The name of the parameter's key.
 */
   public static int parameterInt(String paramName)
   { return parameterInt(paramName, 0); }
   
/**
 * Get an integer parameter from the runtime environment. 
 * 
 * @param	name		The name of the parameter's key.
 * @param	defaultValue	Default integer value, in case param is 
 *				unspecified in the runtime env.
 */
   public static int parameterInt(String paramName, int defaultValue)
   {
      String paramValue	= parameter(paramName);
      int result	= defaultValue;
      if (paramValue != null)
	 try
	 {
	    result	= Integer.parseInt(paramValue);
	 } catch (NumberFormatException e)
	 {
	    Debug.println("bad number format: "+paramName+"="+paramValue);
	 }
      return result;
   }
/**
 * Get a float parameter from the runtime environment.
 * 
 * @param	name		The name of the parameter's key.
 * @param	defaultValue	Default floating point value, in case param is 
 *				unspecified in the runtime env.
 */
   public static float parameterFloat(String paramName, float defaultValue)
   {
      String paramValue	= parameter(paramName);
      float result	= defaultValue;
      if (paramValue != null)
      {
	 float parsedValue	= Generic.parseFloat(paramValue);
	 if (!Float.isNaN(parsedValue))
	    result	= parsedValue;
      }
      return result;
   }
   public static Color parameterColor(String param)
   {	
      String s = parameter(param);
      return (s != null) ? Palette.hexToColor(s) : null;
   }

   public static final String parameter(String paramName)
   {
      return Environment.the.get().parameter(paramName);
   }


   public static void status(String msg)
   {
      Environment.the.get().status(msg);
   }

/**
 * Turn a string into a float.
 * 
 * @return	the float, if the String is cool; else Float.NaN
 */
   static public float parseFloat(String floatString)
   {
      float result;
      try
      {
	 Double fObj	= Double.valueOf(floatString);
	 result		= fObj.floatValue();
      } catch (NumberFormatException e)
      {
	 result		= Float.NaN;
      }
      return result;
   }


	/**
 * Sleep easily, ignoring (unlikely) <code>InterruptedException</code>s.
 */
   public static final void sleep(int time)
   {
      try
      {
	 Thread.sleep(time);
      }
      catch (InterruptedException e)
      {
	 Debug.println("Sleep was interrupted -- clearing if possible.\n" + e);
	 // in jdk 1.1x clears the interrupt
	 // !!! (undocumented) !!! (see Thread src)
	 Thread.interrupted();	
      }
   }

/**
 * Form a URL easily, without perhaps throwing an exception.
 */
   public static URL getURL(URL base, String path, String error)
   {
      // ??? might want to allow this default behaviour ???
      if (path == null)
	 return null;
      try 
      {
		//System.err.println("\nGENERIC - base, path, error = \n" + base + "\n" + path);
		URL newURL = new URL(base,path);
		//System.err.println("\nNEW URL = " + newURL);
	 return newURL;
      } catch (MalformedURLException e) 
      {
	 if (error != null)
	    throw new Error(e + "\n" + error + " " + base + " -> " + path);
	 return null;
      }
   }


/**
 * Find the path to system files.
 */
   public static URL systemPath(String relativePath)
   {
      return Environment.the.get().codeRelativeURL(relativePath);
   }
   public static final String SEP	= "/";
   public static URL systemPhotoPath(String relativePath)
   {
      String photoPathParam	= parameter("photo_path");
      if (photoPathParam == null)
	 throw new RuntimeException("Generic configuration ERROR! Startup parameter photo_path is not defined.");
      String sep = photoPathParam.endsWith(SEP) || relativePath.startsWith(SEP)
	 ? "" : SEP;
      return systemPath(photoPathParam + sep + relativePath);
   }

/**
 * @return	The version of Java we're using (but not the specific release),
 *		as in 1.2, 1.3, 1.4,...
 */
   public static float javaVersion()
   {
      return Environment.the.javaVersion();
   }
   public static boolean hasXML()
   {
      return Environment.the.hasXML();
   }
   public static final void propogateValues(Rectangle src, Rectangle dest)
   {
      dest.x		= src.x;
      dest.y		= src.y;
      dest.width	= src.width;
      dest.height	= src.height;
   }

/**
 * Set the priority of the current thread.
 */
   static final public void setPriority(int priority)
   {
      Thread.currentThread().setPriority(priority);
   }

   public static final boolean contains(String in, String toMatch)
   {
      return StringTools.contains(in, toMatch);
   }

   public static final HashMap buildHashMapFromStrings(String[] strings)
   {
      HashMap hashMap	= new HashMap(strings.length);
      buildMapFromStrings(hashMap, strings);
      return hashMap;
   }
   public static final HashMap buildHashMap(String[][] entries)
   {
      HashMap hashMap	= new HashMap(entries.length);
      buildMap(hashMap, entries);
      return hashMap;
   }
   public static final void buildMapFromStrings(Map map, String[] strings)
   {
      for (int i=0; i<strings.length; i++)
      {
	 String thatString	= strings[i];
	 map.put(thatString, thatString);
      }
   }
   public static final void buildMap(Map map, String[][] entries)
   {
      for (int i=0; i<entries.length; i++)
      {
	 String[] thatEntry	= entries[i];
	 String thatKey		= thatEntry[0];
	 String thatValue	= thatEntry[1];
	 map.put(thatKey, thatValue);
      }
   }
   
   // The keys come from the String[], the values are corresponding number start with 0
   public static final HashMap buildNumberHashMapFromStrings(String[] strings)
   {
      HashMap hashMap	= new HashMap(strings.length);
      buildNumberMapFromStrings(hashMap, strings);
      return hashMap;
   }
   
   // The keys come from the String[], the values are corresponding number start with 0
   public static final void buildNumberMapFromStrings(Map map, String[] strings)
   {
      for (int i=0; i<strings.length; i++)
      {
	 String thatString	= strings[i];
	 Integer integer = new Integer(i);
	 map.put(thatString, integer);
      }
   }
   
   
   
   public static final void stringIntMapEntry(Map map,
					      String string, int integer)
   {
      map.put(string, new IntSlot(integer));
   }
   //////////////////////////////////////////////////////////////
   public static String round(float f, int powerOfTen)
   {
      if (Float.isNaN(f))
	 return "NaN";
      int i = (int) f;
      if ((f - i) == 0)
      {
	 return Integer.toString(i);
      }
      String input = Float.toString(f);
//    Debug.println("input="+input+" powerOfTen="+powerOfTen);
      int end = input.length();
      int dot = input.indexOf('.');
      int exp = input.indexOf('E');
      int endFigs = (exp <= 0) ? end : exp;
      int figs = endFigs - dot;
//    Debug.println("dot="+dot+" exp="+exp+" figs="+figs+" endFigs="+endFigs);
      String result = input;
      if (figs > powerOfTen)
      {
	 result = input.substring(0, dot+powerOfTen+1);
	 if (exp > 0)
	    result += input.substring(exp);
      }
      return result ;
   }
   ///////////////////////////////////////////////////////////////////////
   public static URL getURLAbsolute(String webAddr, String errorDescriptor)
   {
      URL url	= null;
      try
      {
	 url		= new URL(webAddr);
      }
      catch (MalformedURLException e)
      {
	 Debug.println(urlErrorMsg(webAddr, errorDescriptor));
      }
      return url;
   }
   
/**
 * Creates an absolute URL, if the String parameter looks like that,
 * or one that's relative to docBase, if it looks a relative URL.
 */
   public static URL getURL(String webAddr, String errorDescriptor)
   {
      if (webAddr == null)
	 return null;
      
      URL url	= null;
      if (!webAddr.startsWith("http://") && !webAddr.startsWith("ftp://"))
      {
	 try
	 {
	    Debug.println("Forming relative URL from docBase="+
			  Generic.docBase());
	    url		= new URL(Generic.docBase(), webAddr);
	 }
	 catch (MalformedURLException e)
	 {
	    Debug.println(urlErrorMsg(webAddr, errorDescriptor));
	 }
      }      
      else
      {
	 url		= getURLAbsolute(webAddr, errorDescriptor);
      }
      return url;
   }
   static String urlErrorMsg(String webAddr, String errorDescriptor)
   {
      return "CANT open " + errorDescriptor + " " + webAddr +
	      " because it doesn't look like a web address.";
   }
/**
 * Get the IP number for the user's machine.
 * returns:	the ip number as a string, or unknown if JDK 1.0x or
 * other error (like security).
 * !!! for error cases, could create somewhat elaborate scheme to synthesize
 * some kind of id from a cookie, but current usage is just for the study --
 * doesnt need to be perfect. nb: getting ip addr on server side
 * isn't adequate cause proxy servers are so popular w mongo isps like aol!!!
 */
   public static String getLocalIp(URL remote)
   {
      String result	= null;

      try
      {
	 InetAddress server = InetAddress.getByName(remote.getHost());
      
//	 println("getByName() = " + server);
	 
	 Socket socket	= new Socket(server, 80);

	 try
	 {
	    InetAddress localHost	= socket.getLocalAddress();
	    result		= localHost.getHostAddress();
	 } catch (Exception e)
	 {
	    // no such method in JDK 1.0x: getLocalAddress()
	    if (!(e instanceof NoSuchMethodException))
	       Debug.println("UserStudy.getLocalIp() unknown error: " +
				  e);
	    result		= "unknown";
	 }
//	 println("localHost=" + result);
	 socket.close();
      } catch (UnknownHostException e)
      {
	 Debug.println("getByName() failed.\n" + e);
      } catch (IOException e)
      {
	 Debug.println("new Socket() failed.\n" + e);
      }
      return result;
   }
   public static URL docBase()
   {
      return Environment.the.get().docBase();
   }
   public static void main(String[] s)
   {
      Debug.println(round(LN_EMPTY_WEIGHT, 2));
      Debug.println(round(.334455f, 3));
      Debug.println(round(-.334455f, 3));
      Debug.println(round(22, 3));
   }
   static final float	LN_EMPTY_WEIGHT	= Float.MAX_VALUE / 1000;
}