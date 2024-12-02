package net.schwarzbaer.java.tools.openwebifcontroller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JMenu;

import net.schwarzbaer.java.lib.openwebif.Timers.Timer;

public class AlreadySeenTimers
{
	private static final AlreadySeenTimers instance = new AlreadySeenTimers();
	public static AlreadySeenTimers getInstance() { return instance; }
	
	private record StationData (
			String name,
			Set<String> descriptions
	) {
		static StationData create(String name, boolean createDescriptions)
		{
			return new StationData(
					Objects.requireNonNull( name ),
					createDescriptions
						? new HashSet<>()
						: null
			);
		}
	}
	
	private record TimerCriteriaSet (
			String name,
			Map<String, StationData> stations,
			Set<String> descriptions
	) {
		static TimerCriteriaSet create(String name, boolean createStations, boolean createDescriptions)
		{
			return new TimerCriteriaSet(
					Objects.requireNonNull( name ),
					createStations
						? new HashMap<>()
						: null,
					createDescriptions
						? new HashSet<>()
						: null
			);
		}
	}
	
	private static class MutableStationData
	{
		final String name;
		Set<String> descriptions = new HashSet<>();
		
		MutableStationData(String name)
		{
			this.name = name;
		}
		
		StationData convertToRecord()
		{
			StationData stationData = StationData.create(name, !descriptions.isEmpty());
			
			if (stationData.descriptions != null)
				stationData.descriptions.addAll( descriptions );
			
			return stationData;
		}
	}
	
	private static class MutableTCS
	{
		final String name;
		Map<String, StationData> stations = new HashMap<>();
		Set<String> descriptions = new HashSet<>();
		
		MutableTCS(String name)
		{
			this.name = name;
		}
		
		TimerCriteriaSet convertToRecord()
		{
			TimerCriteriaSet tcs = TimerCriteriaSet.create(name, !stations.isEmpty(), !descriptions.isEmpty());
			
			if (tcs.stations != null)
				tcs.stations.putAll(stations);
			
			if (tcs.descriptions != null)
				tcs.descriptions.addAll( descriptions );
			
			return tcs;
		}
	}
	
	private final Map<String, TimerCriteriaSet> alreadySeenTimers;
	
	AlreadySeenTimers()
	{
		alreadySeenTimers = new HashMap<>();
		readFromFile();
	}

	void readFromFile()
	{
		File file = OpenWebifController.LocalDataFile.AlreadySeenTimers.getFileForRead();
		if (file==null)
		{
			System.err.printf("Can't read Already Seen Timers from file.%n");
			return;
		}
		System.out.printf("Read Already Seen Timers from file \"%s\" ...%n", file.getAbsolutePath());
		
		alreadySeenTimers.clear();
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String value, line;
			MutableTCS tcs = null;
			MutableStationData stationData = null;
			
			while ( (line=in.readLine())!=null )
			{
				if (line.isBlank())
					continue;
				
				if (line.equals("[TimerCriteriaSet]"))
				{
					if (tcs!=null)
					{
						if (stationData!=null)
							tcs.stations.put(stationData.name, stationData.convertToRecord());
						alreadySeenTimers.put(tcs.name, tcs.convertToRecord());
					}
					
					tcs = null;
					stationData = null;
				}
				
				if ( tcs == null)
				{
					if ((value=getValue(line, "title = "))!=null)
						tcs = new MutableTCS( value );
				}
				else
				{
					if (line.equals("[Station]"))
					{
						if (stationData!=null)
							tcs.stations.put(stationData.name, stationData.convertToRecord());
						
						stationData = null;
					}
					
					if ( (value=getValue(line, "station = "))!=null && stationData == null)
						stationData = new MutableStationData( value );
					
					if ( (value=getValue(line, "desc = "))!=null)
					{
						if (stationData != null)
							stationData.descriptions.add( decode( value ) );
						else
							tcs.descriptions.add( decode( value ) );
					}
				}
			}
			
			if (tcs!=null)
			{
				if (stationData!=null)
					tcs.stations.put(stationData.name, stationData.convertToRecord());
				alreadySeenTimers.put(tcs.name, tcs.convertToRecord());
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
		File file = OpenWebifController.LocalDataFile.AlreadySeenTimers.getFileForWrite();
		if (file==null)
		{
			System.err.printf("Can't write Already Seen Timers to file.%n");
			return;
		}
		System.out.printf("Write Already Seen Timers to file \"%s\" ...%n", file.getAbsolutePath());
		
		Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
		
		try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			Vector<String> titles = new Vector<>( alreadySeenTimers.keySet() );
			titles.sort(stringComparator);
			
			for (String title : titles)
				if (title!=null)
				{
					out.printf("[TimerCriteriaSet]%n");
					out.printf("title = %s%n", title);
					
					TimerCriteriaSet tcs = alreadySeenTimers.get(title);
					if (tcs.descriptions != null)
					{
						Vector<String> descriptions = new Vector<>( tcs.descriptions );
						descriptions.sort(stringComparator);
						for (String desc : descriptions)
							out.printf("desc = %s%n", encode(desc));
					}
					
					if (tcs.stations != null)
					{
						Vector<String> stations = new Vector<>( tcs.stations.keySet() );
						stations.sort(stringComparator);
						
						for (String station : stations)
						{
							StationData stationData = tcs.stations.get(station);
							out.printf("%n[Station]%n");
							out.printf("station = %s%n", station);
							
							if (stationData.descriptions != null)
							{
								Vector<String> descriptions = new Vector<>( stationData.descriptions );
								descriptions.sort(stringComparator);
								for (String desc : descriptions)
									out.printf("desc = %s%n", encode(desc));
							}
						}
					}
					
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
	
	
	
	private static String encode(String str)
	{
		return URLEncoder.encode(str, StandardCharsets.UTF_8);
	}

	private static String decode(String str)
	{
		return URLDecoder.decode(str, StandardCharsets.UTF_8);
	}

	public interface MenuControl {
		void updateBeforeShowingMenu();
	}
	
	public MenuControl createMenu(JMenu parent, Supplier<Timer> getTimer, Supplier<Timer[]> getTimers, Runnable updateAfterMenuAction)
	{
		JMenu addMenu;
		parent.add(addMenu = new JMenu("Mark as Already Seen"));
		addMenu.add(OpenWebifController.createMenuItem("Title"                      , e -> markAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, false, false)));
		addMenu.add(OpenWebifController.createMenuItem("Title, Description"         , e -> markAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, false, true )));
		addMenu.add(OpenWebifController.createMenuItem("Title, Station"             , e -> markAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, true , false)));
		addMenu.add(OpenWebifController.createMenuItem("Title, Station, Description", e -> markAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, true , true )));
		
		JMenu removeMenu;
		parent.add(removeMenu = new JMenu("Remove Already Seen Marker"));
		removeMenu.add(OpenWebifController.createMenuItem("Title"                      , e -> unmarkAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, false, false)));
		removeMenu.add(OpenWebifController.createMenuItem("Title, Description"         , e -> unmarkAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, false, true )));
		removeMenu.add(OpenWebifController.createMenuItem("Station, Title"             , e -> unmarkAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, true , false)));
		removeMenu.add(OpenWebifController.createMenuItem("Station, Title, Description", e -> unmarkAsAlreadySeen(getTimer, getTimers, updateAfterMenuAction, true , true )));
		
		return () -> {
			// t.b.d.
		};
	}

	private void markAsAlreadySeen(
			Supplier<Timer> getTimer,
			Supplier<Timer[]> getTimers,
			Runnable updateAfterMenuAction,
			boolean useStation, boolean useDescription)
	{
		doWithTimers(getTimer, getTimers, t1 -> markAsAlreadySeen(t1, useStation, useDescription) );
		updateAfterMenuAction.run();
		writeToFile();
	}

	private void unmarkAsAlreadySeen(
			Supplier<Timer> getTimer,
			Supplier<Timer[]> getTimers,
			Runnable updateAfterMenuAction,
			boolean useStation, boolean useDescription)
	{
		doWithTimers(getTimer, getTimers, t1 -> unmarkAsAlreadySeen(t1, useStation, useDescription) );
		updateAfterMenuAction.run();
		writeToFile();
	}

	private String getTimerTitle      (Timer t) { return t.name;        }
	private String getTimerStation    (Timer t) { return t.servicename; }
	private String getTimerDescription(Timer t) { return t.description; }

	private void doWithTimers(
			Supplier<Timer> getTimer,
			Supplier<Timer[]> getTimers,
			Consumer<Timer> action
	) {
		if (getTimer != null)
			action.accept(getTimer.get());
		if (getTimers != null)
		{
			Timer[] timers = getTimers.get();
			if (timers!=null)
				for (Timer t : timers)
					action.accept(t);
		}
	}

	private void markAsAlreadySeen(
			final Timer timer,
			final boolean useStation,
			final boolean useDescription
	) {
		if (timer == null)
			return;
		
		final String title = getTimerTitle(timer);
		if (title == null)
			return; // no title defined in timer
		
		final TimerCriteriaSet tcs = alreadySeenTimers.computeIfAbsent(title, k -> TimerCriteriaSet.create(title, useStation, useDescription && !useStation));
		
		if (!useStation && !useDescription) // define a general timer criteria set ( based only on title )
		{
			if (tcs.stations != null || tcs.descriptions != null)
			{
				// replace station timer criteria set or description based timer criteria set with general timer
				alreadySeenTimers.put(title, TimerCriteriaSet.create(title, false, false));
			}
		}
		else
		{
			final Set<String> descriptions;
			if (!useStation)
				descriptions = tcs.descriptions;
			else
			{
				final String station = getTimerStation(timer);
				if (station == null)
					return; // no station defined in timer
				if (tcs.stations == null)
					return; // timer criteria set is stationless  ->  station is not needed as criteria
				
				StationData stationData = tcs.stations.computeIfAbsent(station, k -> StationData.create(station, useDescription));
				if (!useDescription && stationData.descriptions != null)
				{ // replace description based station timer criteria set with descriptionless (general) station timer criteria set 
					tcs.stations.put(station, stationData = StationData.create(station, false));
				}
				
				descriptions = stationData.descriptions;
			}
			
			
			if (useDescription)
			 {
				if (descriptions == null)
					return; // description is not needed as criteria (-> "descriptionless")
				
				final String description = getTimerDescription(timer);
				if (description == null)
					return; // no description defined in timer
				
				descriptions.add(description);
			}
		}
	}

	private void unmarkAsAlreadySeen(
			Timer timer,
			boolean useStation,
			boolean useDescription
	) {
		if (timer == null)
			return;
		
		final String title = getTimerTitle(timer);
		if (title == null)
			return;
		
		final TimerCriteriaSet tcs = alreadySeenTimers.get(title);
		if (tcs == null)
			return;
		
		
		if (!useStation && !useDescription)
		{
			alreadySeenTimers.remove(title);
			return;
		}
		
		final Set<String> descriptions;
		if (!useStation)
			descriptions = tcs.descriptions;
		
		else
		{
			final String station = getTimerStation(timer);
			if (station == null)
				return; // no station defined in timer
			if (tcs.stations == null)
				return; // timer criteria set is stationless  ->  station is not needed as criteria
			
			if (!useDescription)
			{
				tcs.stations.remove(station);
				return;
			}
				
			StationData stationData = tcs.stations.get(station);
			if (stationData == null)
				return; // no criteria defined for this station
				
			descriptions = stationData.descriptions;
		}
		
		
		if (useDescription)
		 {
			if (descriptions == null)
				return; // description is not needed as criteria (-> "descriptionless")
			
			final String description = getTimerDescription(timer);
			if (description == null)
				return; // no description defined in timer
			
			descriptions.remove(description);
		}
		
	}

	public boolean isMarkedAsAlreadySeen(Timer timer)
	{
		if (timer == null)
			return false;
		
		final String title = getTimerTitle(timer);
		if (title == null)
			return false;
		
		final TimerCriteriaSet tcs = alreadySeenTimers.get(title);
		if (tcs == null)
			return false;
		
		
		if (tcs.stations == null && tcs.descriptions == null)
			return true;
		
		
		Set<String> descriptions = null;
		if (tcs.stations != null)
		{
			final String station = getTimerStation(timer);
			if (station != null)
			{
				StationData stationData = tcs.stations.get(station);
				if (stationData != null)
				{
					if (stationData.descriptions == null)
						return true;
					
					descriptions = stationData.descriptions;
				}
			}
		}
		
		if (descriptions == null)
			descriptions = tcs.descriptions;
		
		if (descriptions == null)
			return false;
		
		final String description = getTimerDescription(timer);
		if (description == null)
			return false; // no description defined in timer
		
		return descriptions.contains(description);
	}
	
	
}
