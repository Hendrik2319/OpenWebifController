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
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.schwarzbaer.java.lib.openwebif.MovieList;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;

public class AlreadySeenEvents
{
	private static final AlreadySeenEvents instance = new AlreadySeenEvents();
	public static AlreadySeenEvents getInstance() { return instance; }
	
	static class EpisodeInfo
	{
		String episodeStr;
		
		EpisodeInfo()                  { this(null); }
		EpisodeInfo(EpisodeInfo other) { this.episodeStr = other==null ? null : other.episodeStr; }
		
		boolean hasEpisodeStr() { return episodeStr!=null && !episodeStr.isBlank(); }
	}
	
	record StationData (
			String name,
			Map<String, EpisodeInfo> descriptions
	) {
		static StationData create(String name, boolean createDescriptions)
		{
			return new StationData(
					Objects.requireNonNull( name ),
					createDescriptions
						? new HashMap<>()
						: null
			);
		}
	}
	
	static class VariableECSData extends EpisodeInfo
	{
		String group;

		VariableECSData(String group, EpisodeInfo episode)
		{
			super(episode);
			this.group = group;
		}
		
		boolean hasGroup() { return group!=null && !group.isBlank(); }
	}
	
	record EventCriteriaSet (
			String title,
			VariableECSData variableData,
			Map<String, StationData> stations,
			Map<String, EpisodeInfo> descriptions
	) {
		static EventCriteriaSet create(String title, boolean createStations, boolean createDescriptions)
		{
			return create(title, null, null, createStations, createDescriptions);
		}
		
		static EventCriteriaSet create(String title, String group, EpisodeInfo episode, boolean createStations, boolean createDescriptions)
		{
			return new EventCriteriaSet(
					Objects.requireNonNull( title ),
					new VariableECSData(group, episode),
					createStations
						? new HashMap<>()
						: null,
					createDescriptions
						? new HashMap<>()
						: null
			);
		}
	}
	
	private static class MutableStationData
	{
		final String name;
		Map<String, EpisodeInfo> descriptions = new HashMap<>();
		
		MutableStationData(String name)
		{
			this.name = name;
		}
		
		StationData convertToRecord()
		{
			StationData stationData = StationData.create(name, !descriptions.isEmpty());
			
			if (stationData.descriptions != null)
				stationData.descriptions.putAll( descriptions );
			
			return stationData;
		}
	}
	
	private static class MutableECS
	{
		final String title;
		String group;
		EpisodeInfo episode;
		Map<String, StationData> stations = new HashMap<>();
		Map<String, EpisodeInfo> descriptions = new HashMap<>();
		
		MutableECS(String title)
		{
			this.title = title;
			episode = new EpisodeInfo();
			group = null;
		}
		
		EventCriteriaSet convertToRecord()
		{
			EventCriteriaSet ecs = EventCriteriaSet.create(title, group, episode, !stations.isEmpty(), !descriptions.isEmpty());
			
			if (ecs.stations != null)
				ecs.stations.putAll( stations );
			
			if (ecs.descriptions != null)
				ecs.descriptions.putAll( descriptions );
			
			return ecs;
		}
	}
	
	private final Map<String, EventCriteriaSet> alreadySeenEvents;
	
	AlreadySeenEvents()
	{
		alreadySeenEvents = new HashMap<>();
		readFromFile();
	}

	void readFromFile()
	{
		File file = OpenWebifController.LocalDataFile.AlreadySeenEvents.getFileForRead();
		if (file==null)
		{
			System.err.printf("Can't read Already Seen Events from file.%n");
			return;
		}
		System.out.printf("Read Already Seen Events from file \"%s\" ...%n", file.getAbsolutePath());
		
		alreadySeenEvents.clear();
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String value, line;
			MutableECS ecs = null;
			MutableStationData stationData = null;
			EpisodeInfo episodeInfo = null;
			
			while ( (line=in.readLine())!=null )
			{
				if (line.isBlank())
					continue;
				
				if (line.equals("[EventCriteriaSet]"))
				{
					if (ecs!=null)
					{
						if (stationData!=null)
							ecs.stations.put(stationData.name, stationData.convertToRecord());
						alreadySeenEvents.put(ecs.title, ecs.convertToRecord());
					}
					
					ecs = null;
					stationData = null;
					episodeInfo = null;
				}
				
				if ( ecs == null)
				{
					if ((value=getValue(line, "title = "))!=null)
						ecs = new MutableECS( decode( value ) );
				}
				else
				{
					if (line.equals("[Station]"))
					{
						if (stationData!=null)
							ecs.stations.put(stationData.name, stationData.convertToRecord());
						
						stationData = null;
					}
					
					if ( (value=getValue(line, "station = "))!=null && stationData==null )
						stationData = new MutableStationData( decode( value ) );
					
					if ( (value=getValue(line, "desc = "))!=null )
					{
						if (stationData != null)
							stationData.descriptions.put( decode( value ), episodeInfo = new EpisodeInfo() );
						else
							ecs.descriptions.put( decode( value ), episodeInfo = new EpisodeInfo() );
					}
					
					if ( (value=getValue(line, "group = "))!=null)
						ecs.group = value;
					
					if ( (value=getValue(line, "episodeT = "))!=null)
						ecs.episode.episodeStr = value;
					
					if ( (value=getValue(line, "episodeD = "))!=null && episodeInfo!=null )
						episodeInfo.episodeStr = value;
				}
			}
			
			if (ecs!=null)
			{
				if (stationData!=null)
					ecs.stations.put(stationData.name, stationData.convertToRecord());
				alreadySeenEvents.put(ecs.title, ecs.convertToRecord());
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
		File file = OpenWebifController.LocalDataFile.AlreadySeenEvents.getFileForWrite();
		if (file==null)
		{
			System.err.printf("Can't write Already Seen Events to file.%n");
			return;
		}
		System.out.printf("Write Already Seen Events to file \"%s\" ...%n", file.getAbsolutePath());
		
		Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
		
		try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			Vector<String> titles = new Vector<>( alreadySeenEvents.keySet() );
			titles.sort(stringComparator);
			
			for (String title : titles)
				if (title!=null)
				{
					out.printf("[EventCriteriaSet]%n");
					out.printf("title = %s%n", encode(title));
					
					EventCriteriaSet ecs = alreadySeenEvents.get(title);
					
					VariableECSData variableData = ecs.variableData;
					if (variableData!=null)
					{
						if (variableData.group!=null && !variableData.group.isBlank())
							out.printf("group = %s%n", variableData.group);
						if (variableData.hasEpisodeStr())
							out.printf("episodeT = %s%n", ecs.variableData.episodeStr);
					}
					
					if (ecs.descriptions != null)
					{
						Vector<String> descriptions = new Vector<>( ecs.descriptions.keySet() );
						descriptions.sort(stringComparator);
						for (String desc : descriptions)
						{
							out.printf("desc = %s%n", encode(desc));
							EpisodeInfo episode = ecs.descriptions.get(desc);
							if (episode == null) continue;
							if (episode.hasEpisodeStr())
								out.printf("episodeD = %s%n", episode.episodeStr);
						}
					}
					
					if (ecs.stations != null)
					{
						Vector<String> stations = new Vector<>( ecs.stations.keySet() );
						stations.sort(stringComparator);
						
						for (String station : stations)
						{
							StationData stationData = ecs.stations.get(station);
							out.printf("%n[Station]%n");
							out.printf("station = %s%n", encode(station));
							
							if (stationData.descriptions != null)
							{
								Vector<String> descriptions = new Vector<>( stationData.descriptions.keySet() );
								descriptions.sort(stringComparator);
								for (String desc : descriptions)
								{
									out.printf("desc = %s%n", encode(desc));
									EpisodeInfo episode = ecs.descriptions.get(desc);
									if (episode == null) continue;
									if (episode.hasEpisodeStr())
										out.printf("episodeD = %s%n", episode.episodeStr);
								}
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

	private interface GetData<V>
	{
		String getTitle      (V value);
		String getStation    (V value);
		String getDescription(V value);
	}
	
	private final static GetData<Timer> GET_DATA_FROM_TIMER = new GetData<>()
	{
		@Override public String getTitle      (Timer t) { return t.name;        }
		@Override public String getStation    (Timer t) { return t.servicename; }
		@Override public String getDescription(Timer t) { return t.description; }
	};
	private final static GetData<MovieList.Movie> GET_DATA_FROM_MOVIE = new GetData<>()
	{
		@Override public String getTitle      (MovieList.Movie m) { return m.eventname;   }
		@Override public String getStation    (MovieList.Movie m) { return m.servicename; }
		@Override public String getDescription(MovieList.Movie m) { return m.description; }
	};
	
	public interface MenuControl {
		void updateBeforeShowingMenu();
	}

	public MenuControl createMenuForTimers(JMenu parent, Supplier<Timer> getTimer, Supplier<Timer[]> getTimers, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent, getTimer, getTimers, GET_DATA_FROM_TIMER, updateAfterMenuAction);
	}
	public MenuControl createMenuForMovies(JMenu parent, Supplier<MovieList.Movie> getMovie, Supplier<MovieList.Movie[]> getMovies, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent, getMovie, getMovies, GET_DATA_FROM_MOVIE, updateAfterMenuAction);
	}
	
	private class SubMenu<V> implements MenuControl
	{
		private final JMenuItem miShowReason;
		private final Supplier<V> getSource;
		private final Supplier<V[]> getSources;
		private final GetData<V> getData;
		private V singleSource;

		SubMenu(JMenu parent, Supplier<V> getSource, Supplier<V[]> getSources, GetData<V> getData, Runnable updateAfterMenuAction)
		{
			this.getSource = getSource;
			this.getSources = getSources;
			this.getData = getData;
			this.singleSource = null;
			
			JMenu addMenu;
			parent.add(addMenu = new JMenu("Mark as Already Seen"));
			addMenu.add(OpenWebifController.createMenuItem("Title"                      , e -> markAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, false, false)));
			addMenu.add(OpenWebifController.createMenuItem("Title, Description"         , e -> markAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, false, true )));
			addMenu.add(OpenWebifController.createMenuItem("Title, Station"             , e -> markAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, true , false)));
			addMenu.add(OpenWebifController.createMenuItem("Title, Station, Description", e -> markAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, true , true )));
			
			miShowReason = parent.add(OpenWebifController.createMenuItem("##", e->{
				// TODO
			}));
			
//			JMenu removeMenu;
//			parent.add(removeMenu = new JMenu("Remove Already Seen Marker"));
//			removeMenu.add(OpenWebifController.createMenuItem("Title"                      , e -> unmarkAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, false, false)));
//			removeMenu.add(OpenWebifController.createMenuItem("Title, Description"         , e -> unmarkAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, false, true )));
//			removeMenu.add(OpenWebifController.createMenuItem("Station, Title"             , e -> unmarkAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, true , false)));
//			removeMenu.add(OpenWebifController.createMenuItem("Station, Title, Description", e -> unmarkAsAlreadySeen(getSource, getSources, getData, updateAfterMenuAction, true , true )));
		}
		
		@Override
		public void updateBeforeShowingMenu()
		{
			singleSource = null;
			
			if (getSource!=null)
				singleSource = getSource.get();
			
			else if (getSources!=null)
			{
				V[] sources = getSources.get();
				if (sources!=null && sources.length==1)
					singleSource = sources[0];
			}
			
			boolean isMarkedAsAlreadySeen = singleSource==null ? false : isMarkedAsAlreadySeen(singleSource, getData);
			miShowReason.setEnabled(isMarkedAsAlreadySeen);
			miShowReason.setText( "Show [Already Seen] Rule" + (singleSource==null ? "" : " for \"%s\"".formatted(getData.getTitle(singleSource))) );
		}
	}

	private <V> void markAsAlreadySeen(
			Supplier<V> getSource,
			Supplier<V[]> getSources,
			GetData<V> getData,
			Runnable updateAfterMenuAction,
			boolean useStation, boolean useDescription)
	{
		doWithSources(getSource, getSources, t1 -> markAsAlreadySeen(t1, getData, useStation, useDescription) );
		updateAfterMenuAction.run();
		writeToFile();
	}

	@SuppressWarnings("unused")
	private <V> void unmarkAsAlreadySeen(
			Supplier<V> getSource,
			Supplier<V[]> getSources,
			GetData<V> getData,
			Runnable updateAfterMenuAction,
			boolean useStation, boolean useDescription)
	{
		doWithSources(getSource, getSources, t1 -> unmarkAsAlreadySeen(t1, getData, useStation, useDescription) );
		updateAfterMenuAction.run();
		writeToFile();
	}

	private <V> void doWithSources(
			Supplier<V> getSource,
			Supplier<V[]> getSources,
			Consumer<V> action
	) {
		if (getSource != null)
			action.accept(getSource.get());
		if (getSources != null)
		{
			V[] sources = getSources.get();
			if (sources!=null)
				for (V source : sources)
					action.accept(source);
		}
	}

	private <V>void markAsAlreadySeen(
			final V source,
			final GetData<V> getData,
			final boolean useStation,
			final boolean useDescription
	) {
		if (source == null)
			return;
		
		final String title = getData.getTitle(source);
		if (title == null)
			return; // no title defined in source
		
		final EventCriteriaSet ecs = alreadySeenEvents.computeIfAbsent(title, k -> EventCriteriaSet.create(title, useStation, useDescription && !useStation));
		
		if (!useStation && !useDescription) // define a general event criteria set ( based only on title )
		{
			if (ecs.stations != null || ecs.descriptions != null)
			{
				// replace station event criteria set or description based event criteria set with general event criteria set
				alreadySeenEvents.put(title, EventCriteriaSet.create(title, false, false));
			}
		}
		else
		{
			final Map<String, EpisodeInfo> descriptions;
			if (!useStation)
				descriptions = ecs.descriptions;
			else
			{
				final String station = getData.getStation(source);
				if (station == null)
					return; // no station defined in source
				if (ecs.stations == null)
					return; // event criteria set is stationless  ->  station is not needed as criteria
				
				StationData stationData = ecs.stations.computeIfAbsent(station, k -> StationData.create(station, useDescription));
				if (!useDescription && stationData.descriptions != null)
				{ // replace description based station event criteria set with descriptionless (general) station event criteria set 
					ecs.stations.put(station, stationData = StationData.create(station, false));
				}
				
				descriptions = stationData.descriptions;
			}
			
			
			if (useDescription)
			 {
				if (descriptions == null)
					return; // description is not needed as criteria (-> "descriptionless")
				
				final String description = getData.getDescription(source);
				if (description == null)
					return; // no description defined in source
				
				descriptions.put(description, new EpisodeInfo());
			}
		}
	}

	private <V> void unmarkAsAlreadySeen(
			final V source,
			final GetData<V> getData,
			boolean useStation,
			boolean useDescription
	) {
		if (source == null)
			return;
		
		final String title = getData.getTitle(source);
		if (title == null)
			return;
		
		final EventCriteriaSet ecs = alreadySeenEvents.get(title);
		if (ecs == null)
			return;
		
		
		if (!useStation && !useDescription)
		{
			alreadySeenEvents.remove(title);
			return;
		}
		
		final Map<String,EpisodeInfo> descriptions;
		if (!useStation)
			descriptions = ecs.descriptions;
		
		else
		{
			final String station = getData.getStation(source);
			if (station == null)
				return; // no station defined in source
			if (ecs.stations == null)
				return; // event criteria set is stationless  ->  station is not needed as criteria
			
			if (!useDescription)
			{
				ecs.stations.remove(station);
				return;
			}
				
			StationData stationData = ecs.stations.get(station);
			if (stationData == null)
				return; // no criteria defined for this station
				
			descriptions = stationData.descriptions;
		}
		
		
		if (useDescription)
		 {
			if (descriptions == null)
				return; // description is not needed as criteria (-> "descriptionless")
			
			final String description = getData.getDescription(source);
			if (description == null)
				return; // no description defined in source
			
			descriptions.remove(description);
		}
		
	}

	public boolean isMarkedAsAlreadySeen(Timer timer)
	{
		return isMarkedAsAlreadySeen(timer, GET_DATA_FROM_TIMER);
	}

	public boolean isMarkedAsAlreadySeen(MovieList.Movie movie)
	{
		return isMarkedAsAlreadySeen(movie, GET_DATA_FROM_MOVIE);
	}

	private <V> boolean isMarkedAsAlreadySeen(final V source, final GetData<V> getData)
	{
		if (source == null)
			return false;
		
		final String title = getData.getTitle(source);
		if (title == null)
			return false;
		
		final EventCriteriaSet ecs = alreadySeenEvents.get(title);
		if (ecs == null)
			return false;
		
		
		if (ecs.stations == null && ecs.descriptions == null)
			return true;
		
		
		Map<String,EpisodeInfo> descriptions = null;
		if (ecs.stations != null)
		{
			final String station = getData.getStation(source);
			if (station != null)
			{
				StationData stationData = ecs.stations.get(station);
				if (stationData != null)
				{
					if (stationData.descriptions == null)
						return true;
					
					descriptions = stationData.descriptions;
				}
			}
		}
		
		if (descriptions == null)
			descriptions = ecs.descriptions;
		
		if (descriptions == null)
			return false;
		
		final String description = getData.getDescription(source);
		if (description == null)
			return false; // no description defined in source
		
		return descriptions.containsKey(description);
	}

	AlreadySeenEventsViewer.RootTreeNode createTreeRoot(AlreadySeenEventsViewer viewer)
	{
		return viewer.createTreeRoot(alreadySeenEvents);
	}
}
