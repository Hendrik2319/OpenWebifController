package net.schwarzbaer.java.tools.openwebifcontroller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Vector;

import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;

class MovieList {
	
	public interface MovieListReadInterface {
		void setIndeterminateProgressTask(String taskTitle);
	}

	public static MovieList readMovieList(String baseURL, String dir, MovieListReadInterface movieListReadInterface) {
		movieListReadInterface.setIndeterminateProgressTask("Build URL");
		String urlStr = String.format("%s/api/movielist", baseURL);
		String dir_ = dir;
		if (dir_!=null) {
			try { dir_ = URLEncoder.encode(dir_, "UTF-8");
			} catch (UnsupportedEncodingException e) { System.err.printf("Exception while converting directory name: [UnsupportedEncodingException] %s%n", e.getMessage()); }
			urlStr = String.format("%s?dirname=%s", urlStr, dir_);
		}
		System.out.printf("get MovieList: \"%s\"%n", urlStr);
		
		movieListReadInterface.setIndeterminateProgressTask("Read Content from URL");
		String content = getContent(urlStr);
		
		movieListReadInterface.setIndeterminateProgressTask("Parse Content");
		Value<NV, V> result = new JSON_Parser<NV,V>(content,null).parse();
		
		movieListReadInterface.setIndeterminateProgressTask("Create MovieList");
		try {
			return new MovieList(result);
		} catch (TraverseException e) {
			System.err.printf("Exception while parsing JSON structure: %s%n", e.getMessage());
			return null;
		}
	}

	static String decodeUnicode(String str) {
		if (str==null) return null;
		int pos;
		int startPos = 0;
		while ( (pos=str.indexOf("\\u",startPos))>=0 ) {
			if (str.length()<pos+6) break;
			String prefix = str.substring(0, pos);
			String suffix = str.substring(pos+6);
			String codeStr = str.substring(pos+2,pos+6);
			int code;
			try { code = Integer.parseUnsignedInt(codeStr,16); }
			catch (NumberFormatException e) { startPos = pos+2; continue; }
			str = prefix + ((char)code) + suffix;
		}
		return str;
	}

	static String getContent(String urlStr) {
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e) { System.err.printf("MalformedURL: %s%n", e.getMessage()); return null; }
		
		URLConnection conn;
		try { conn = url.openConnection(); }
		catch (IOException e) { System.err.printf("url.openConnection -> IOException: %s%n", e.getMessage()); return null; }
		
		conn.setDoInput(true);
		try { conn.connect(); }
		catch (IOException e) { System.err.printf("conn.connect -> IOException: %s%n", e.getMessage()); return null; }
		
		ByteArrayOutputStream storage = new ByteArrayOutputStream();
		try (BufferedInputStream in = new BufferedInputStream( conn.getInputStream() )) {
			byte[] buffer = new byte[100000];
			int n;
			while ( (n=in.read(buffer))>=0 )
				if (n>0) storage.write(buffer, 0, n);
			
		} catch (IOException e) {
			System.err.printf("IOException: %s%n", e.getMessage());
		}
		
		return new String(storage.toByteArray());
	}

	static class NV extends JSON_Data.NamedValueExtra.Dummy{}
	static class V extends JSON_Data.ValueExtra.Dummy{}
	
	/*
	    Block "MovieList" [0]
	        <Base>:Object
	    Block "MovieList.<Base>" [3]
	        bookmarks:Array
	        bookmarks[]:String
	        directory:String
	        movies:Array
	        movies[]:Object
	 */

	final String directory;
	final Vector<String> bookmarks;
	final Vector<Movie> movies;
	
	public MovieList(Value<NV, V> result) throws TraverseException {
		//JSON_Helper.OptionalValues<NV, V> optionalValueScan = new JSON_Helper.OptionalValues<NV,V>();
		//optionalValueScan.scan(result, "MovieList");
		//optionalValueScan.show(System.out);
		
		String debugOutputPrefixStr = "MovieList";
		JSON_Object<NV, V> object = JSON_Data.getObjectValue(result, debugOutputPrefixStr);
		
		directory = decodeUnicode( JSON_Data.getStringValue(object, "directory", debugOutputPrefixStr) );
		JSON_Array<NV, V> bookmarks = JSON_Data.getArrayValue(object, "bookmarks", debugOutputPrefixStr);
		JSON_Array<NV, V> movies    = JSON_Data.getArrayValue(object, "movies", debugOutputPrefixStr);
		
		this.bookmarks = new Vector<>();
		for (int i=0; i<bookmarks.size(); i++) {
			String str = JSON_Data.getStringValue(bookmarks.get(i), debugOutputPrefixStr+".bookmarks["+i+"]");
			this.bookmarks.add( decodeUnicode( str ) );
		}
		
		this.movies = new Vector<>();
		for (int i=0; i<movies.size(); i++)
			this.movies.add(new Movie(movies.get(i), debugOutputPrefixStr+".movies["+i+"]"));
	}
	
	static class Movie {
		final String begintime;
		final String description;
		final String descriptionExtended;
		final String eventname;
		final String filename;
		final String filename_stripped;
		final long filesize;
		final String filesize_readable;
		final String fullname;
		final long lastseen;
		final String lengthStr;
		final long recordingtime;
		final String servicename;
		final String serviceref;
		final String tags;
		final Integer length_s;

		/*		
		    Block "MovieList.<Base>.movies[]" [15]
		        begintime:String
		        description:String
		        descriptionExtended:String
		        eventname:String
		        filename:String
		        filename_stripped:String
		        filesize:Integer
		        filesize_readable:String
		        fullname:String
		        lastseen:Integer
		        length:String
		        recordingtime:Integer
		        servicename:String
		        serviceref:String
		        tags:String
		 */

		public Movie(Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
			JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
			
			begintime           = decodeUnicode( JSON_Data.getStringValue (object, "begintime"          , debugOutputPrefixStr) ); 
			description         = decodeUnicode( JSON_Data.getStringValue (object, "description"        , debugOutputPrefixStr) );
			descriptionExtended = decodeUnicode( JSON_Data.getStringValue (object, "descriptionExtended", debugOutputPrefixStr) );
			eventname           = decodeUnicode( JSON_Data.getStringValue (object, "eventname"          , debugOutputPrefixStr) );
			filename            = decodeUnicode( JSON_Data.getStringValue (object, "filename"           , debugOutputPrefixStr) );
			filename_stripped   = decodeUnicode( JSON_Data.getStringValue (object, "filename_stripped"  , debugOutputPrefixStr) );
			filesize            =                JSON_Data.getIntegerValue(object, "filesize"           , debugOutputPrefixStr)  ;
			filesize_readable   = decodeUnicode( JSON_Data.getStringValue (object, "filesize_readable"  , debugOutputPrefixStr) );
			fullname            = decodeUnicode( JSON_Data.getStringValue (object, "fullname"           , debugOutputPrefixStr) );
			lastseen            =                JSON_Data.getIntegerValue(object, "lastseen"           , debugOutputPrefixStr)  ;
			lengthStr           = decodeUnicode( JSON_Data.getStringValue (object, "length"             , debugOutputPrefixStr) );
			recordingtime       =                JSON_Data.getIntegerValue(object, "recordingtime"      , debugOutputPrefixStr)  ;
			servicename         = decodeUnicode( JSON_Data.getStringValue (object, "servicename"        , debugOutputPrefixStr) );
			serviceref          = decodeUnicode( JSON_Data.getStringValue (object, "serviceref"         , debugOutputPrefixStr) );
			tags                = decodeUnicode( JSON_Data.getStringValue (object, "tags"               , debugOutputPrefixStr) );
			
			length_s = parseLength(lengthStr);
		}

		private static Integer parseLength(String lengthStr) {
			if (lengthStr==null) return null;
			lengthStr = lengthStr.trim();
			
			int pos = lengthStr.indexOf(':');
			if (pos<0) return parseInt(lengthStr);
			
			Integer min = parseInt(lengthStr.substring(0, pos));
			Integer sec = parseInt(lengthStr.substring(pos+1));
			if (min==null || sec==null) return null;
			
			int sign = min<0 ? -1 : 1;
			return sign * (Math.abs(min)*60 + Math.abs(sec));
		}

		private static Integer parseInt(String str) {
			str = str.trim();
			try {
				int n = Integer.parseInt(str);
				String newStr = String.format("%0"+str.length()+"d", n);
				if (newStr.equals(str)) return n;
			} catch (NumberFormatException e) {}
			return null;
		}
		
	}
}