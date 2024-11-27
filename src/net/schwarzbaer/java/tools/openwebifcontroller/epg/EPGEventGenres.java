package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

class EPGEventGenres
{
	private static final Comparator<EPGEventGenre> COMPARATOR = Comparator.<EPGEventGenre,String>comparing(name -> name.name);

	record EPGEventGenre( long id, String name, boolean isNew ) {
		EPGEventGenre(EPGevent event) { this(event.genreid, event.genre, true); }

		@Override
		public int hashCode()
		{
			return Objects.hash(id, name);
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			EPGEventGenre other = (EPGEventGenre) obj;
			return id == other.id && Objects.equals(name, other.name);
		}
		
	}
	
	private static final EPGEventGenres instance = new EPGEventGenres();
	static EPGEventGenres getInstance() { return instance; }
	
	private final HashMap<Long,HashSet<EPGEventGenre>> genres;
	
	EPGEventGenres()
	{
		genres = new HashMap<>();
		readFromFile();
	}
	
	List<EPGEventGenre> getEPGEventGenresSorted()
	{
		ArrayList<Long> ids = new ArrayList<>( genres.keySet() );
		ids.sort(null);
		
		ArrayList<EPGEventGenre> list = new ArrayList<>();
		ids.forEach( id -> {
			HashSet<EPGEventGenre> names = genres.get(id);
			ArrayList<EPGEventGenre> sortedNames = new ArrayList<>( names );
			sortedNames.sort(COMPARATOR);
			
			sortedNames.forEach( list::add );
		});
		
		return list;
	}

	EPGEventGenres scanGenres(Vector<EPGevent> events)
	{
		if (events!=null)
			for (EPGevent event : events)
				getOrCreateNamesList(event.genreid).add( new EPGEventGenre(event) );
		return this;
	}

	private HashSet<EPGEventGenre> getOrCreateNamesList(long genreID)
	{
		HashSet<EPGEventGenre> genreNames = genres.get(genreID);
		if (genreNames==null) genres.put(genreID, genreNames = new HashSet<>());
		return genreNames;
	}

	void readFromFile()
	{
		File file = new File(OpenWebifController.FILE__EPG_EVENT_GENRES);
		System.out.printf("Read EPGEvent Genres from file \"%s\" ...%n", file.getAbsolutePath());
		
		genres.clear();
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String value, line;
			Long genreID = null;
			HashSet<EPGEventGenre> genreNames = null;
			while ( (line=in.readLine())!=null )
			{
				if (line.isBlank())
				{
					genreID = null;
					genreNames = null;
					continue;
				}
				
				if ( (value=getValue(line, "Genre: "))!=null )
				{
					genreNames = null;
					try { genreID = Long.parseLong(value); }
					catch (NumberFormatException ex) {
						System.err.printf("NumberFormatException while parsing line \"%s\": %s%n", line, ex.getMessage());
						// ex.printStackTrace();
						genreID = null;
						continue;
					}
					genreNames = getOrCreateNamesList(genreID);
				}
				
				if ( (value=getValue(line, "Name = "))!=null && genreNames!=null && genreID!=null)
					genreNames.add( new EPGEventGenre(genreID, value, false) );
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
		File file = new File(OpenWebifController.FILE__EPG_EVENT_GENRES);
		System.out.printf("Write EPGEvent Genres to file \"%s\" ...%n", file.getAbsolutePath());
		
		try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			Vector<Long> genreIDs = new Vector<>( genres.keySet() );
			genreIDs.sort(null);
			
			for (Long genreID : genreIDs)
				if (genreID!=null)
				{
					out.printf("Genre: %d%n", genreID);
					
					HashSet<EPGEventGenre> names = genres.get(genreID);
					Vector<EPGEventGenre> namesVec = new Vector<>( names );
					namesVec.sort(COMPARATOR);
					
					for (EPGEventGenre name : namesVec)
						out.printf("Name = %s%n", name.name);
					
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
