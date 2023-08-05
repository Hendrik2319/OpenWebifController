package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import net.schwarzbaer.java.lib.openwebif.EPGevent;

class EPGEventGenres
{
	private static final String FILE__EPG_EVENT_GENRES = "OpenWebifController - EPGEventGenres.data";
	private static final EPGEventGenres instance = new EPGEventGenres();
	static EPGEventGenres getInstance() { return instance; }
	
	private final HashMap<Long,HashSet<String>> genres;
	
	EPGEventGenres()
	{
		genres = new HashMap<>();
		readFromFile();
	}
	
	EPGEventGenres scanGenres(Vector<EPGevent> events)
	{
		if (events!=null)
			for (EPGevent event : events)
				getOrCreateNamesList(event.genreid).add(event.genre);
		return this;
	}

	private HashSet<String> getOrCreateNamesList(long genreID)
	{
		HashSet<String> genreNames = genres.get(genreID);
		if (genreNames==null) genres.put(genreID, genreNames = new HashSet<>());
		return genreNames;
	}

	void readFromFile()
	{
		File file = new File(FILE__EPG_EVENT_GENRES);
		System.out.printf("Read EPGEvent Genres from file \"%s\" ...%n", file.getAbsolutePath());
		
		genres.clear();
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String value, line;
			HashSet<String> genreNames = null;
			while ( (line=in.readLine())!=null )
			{
				if (line.isBlank())
				{
					genreNames = null;
					continue;
				}
				
				if ( (value=getValue(line, "Genre: "))!=null )
				{
					genreNames = null;
					long genreID;
					try { genreID = Long.parseLong(value); }
					catch (NumberFormatException ex) {
						System.err.printf("NumberFormatException while parsing line \"%s\": %s%n", line, ex.getMessage());
						// ex.printStackTrace();
						continue;
					}
					genreNames = getOrCreateNamesList(genreID);
				}
				
				if ( (value=getValue(line, "Name = "))!=null && genreNames!=null)
					genreNames.add(value);
			}
		}
		catch (FileNotFoundException ex) {}
		catch (IOException ex)
		{
			System.err.printf("IOException while reading file \"%s\": %s%n", file.getAbsolutePath(), ex.getMessage());
			// ex.printStackTrace();
		}
		
		System.out.printf("Done%n");
	}
	
	private static String getValue(String line, String prefix)
	{
		if (line.startsWith(prefix))
			return line.substring(prefix.length());
		return null;
	}
	
	void writeToFile()
	{
		File file = new File(FILE__EPG_EVENT_GENRES);
		System.out.printf("Write EPGEvent Genres to file \"%s\" ...%n", file.getAbsolutePath());
		
		try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			Vector<Long> genreIDs = new Vector<>( genres.keySet() );
			genreIDs.sort(null);
			
			for (Long genreID : genreIDs)
				if (genreID!=null)
				{
					out.printf("Genre: %d%n", genreID);
					
					HashSet<String> names = genres.get(genreID);
					Vector<String> namesVec = new Vector<>( names );
					namesVec.sort(null);
					
					for (String name : namesVec)
						out.printf("Name = %s%n", name);
					
					out.printf("%n");
				}
		}
		catch (IOException ex)
		{
			System.err.printf("IOException while writing file \"%s\": %s%n", file.getAbsolutePath(), ex.getMessage());
			// ex.printStackTrace();
		}
		
		System.out.printf("Done%n");
	}
}
