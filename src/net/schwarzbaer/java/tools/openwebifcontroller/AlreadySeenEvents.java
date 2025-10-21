package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Window;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
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
		String  getEpisodeStr() { return hasEpisodeStr() ? episodeStr : null; }
	}
	
	enum DescriptionOperator
	{
		Equals,
		Contains,
		StartsWith("Starts with"),
		;
		final String title;
		DescriptionOperator() { this(null); }
		DescriptionOperator(String title)
		{
			this.title = title!=null ? title : name();
		}
	}
	
	static class DescriptionData extends EpisodeInfo
	{
		DescriptionOperator operator;
		
		DescriptionData()                      { this(null); }
		DescriptionData(DescriptionData other) { this(other, other==null ? null : other.operator); }
		DescriptionData(EpisodeInfo episodeInfo, DescriptionOperator operator)
		{
			super(episodeInfo);
			this.operator = operator==null ? DescriptionOperator.Equals : operator;
		}
	}
	
	record StationData (
			String name,
			Map<String, DescriptionData> descriptions
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
			Map<String, DescriptionData> descriptions
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
		Map<String, DescriptionData> descriptions = new HashMap<>();
		
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
		final Map<String, StationData> stations = new HashMap<>();
		final Map<String, DescriptionData> descriptions = new HashMap<>();
		
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
	
	interface ChangeListener
	{
		enum ChangeType { RuleSet, EpisodeText, Grouping }
		void somethingHasChanged(ChangeType changeType);
	}
	
	private final Map<String, EventCriteriaSet> alreadySeenEvents;
	private final Vector<ChangeListener> changeListeners;
	
	AlreadySeenEvents()
	{
		alreadySeenEvents = new HashMap<>();
		changeListeners = new Vector<>();
		readFromFile();
	}

	public void    addChangeListener(ChangeListener l) { changeListeners.   add(l); }
	public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }
	
	void notifyChangeListeners(ChangeListener.ChangeType changeType)
	{
		for (ChangeListener l : changeListeners)
			l.somethingHasChanged(changeType);
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
					saveRemainingData(ecs, stationData);
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
						saveRemainingStationData(ecs, stationData);
						stationData = null;
					}
					
					if ( (value=getValue(line, "station = "))!=null && stationData==null )
						stationData = new MutableStationData( decode( value ) );
					
					if ( (value=getValue(line, "desc = "))!=null )
						episodeInfo = setDescription(ecs, stationData, value, DescriptionOperator.Equals);
					
					if ( (value=getValue(line, "desc_startswith = "))!=null )
						episodeInfo = setDescription(ecs, stationData, value, DescriptionOperator.StartsWith);
					
					if ( (value=getValue(line, "desc_contains = "))!=null )
						episodeInfo = setDescription(ecs, stationData, value, DescriptionOperator.Contains);
					
					if ( (value=getValue(line, "group = "))!=null)
						ecs.group = value;
					
					if ( (value=getValue(line, "episodeT = "))!=null)
						ecs.episode.episodeStr = value;
					
					if ( (value=getValue(line, "episodeD = "))!=null && episodeInfo!=null )
						episodeInfo.episodeStr = value;
				}
			}
			
			saveRemainingData(ecs, stationData);
		}
		catch (FileNotFoundException ex) {}
		catch (IOException ex)
		{
			System.err.printf("IOException while reading file \"%s\": %s%n", file.getAbsolutePath(), ex.getMessage());
			// ex.printStackTrace();
		}
		
		System.out.printf("Done%n");
	}

	private EpisodeInfo setDescription(MutableECS ecs, MutableStationData stationData, String value, DescriptionOperator operator)
	{
		DescriptionData descriptionData = new DescriptionData(null, operator);
		Map<String, DescriptionData> descriptions = stationData != null ? stationData.descriptions : ecs.descriptions;
		descriptions.put( decode( value ), descriptionData );
		return descriptionData;
	}

	private void saveRemainingData(MutableECS ecs, MutableStationData stationData)
	{
		if (ecs!=null)
		{
			saveRemainingStationData(ecs, stationData);
			alreadySeenEvents.put(ecs.title, ecs.convertToRecord());
		}
	}

	private void saveRemainingStationData(MutableECS ecs, MutableStationData stationData)
	{
		Objects.requireNonNull(ecs);
		if (stationData!=null)
			ecs.stations.put(stationData.name, stationData.convertToRecord());
	}

	private static String getValue(String line, String prefix)
	{
		if (line.startsWith(prefix))
			return line.substring(prefix.length());
		return null;
	}
	
	private static final Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
	
	void writeToFileAndNotify(ChangeListener.ChangeType changeType)
	{
		writeToFile();
		notifyChangeListeners(changeType);
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
					
					writeDescriptionsMap(out, ecs.descriptions);
					
					if (ecs.stations != null)
					{
						Vector<String> stations = new Vector<>( ecs.stations.keySet() );
						stations.sort(stringComparator);
						
						for (String station : stations)
						{
							StationData stationData = ecs.stations.get(station);
							out.printf("%n[Station]%n");
							out.printf("station = %s%n", encode(station));
							writeDescriptionsMap(out, stationData.descriptions);
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

	private void writeDescriptionsMap(PrintWriter out, Map<String, DescriptionData> descriptionsMap)
	{
		if (descriptionsMap == null) return;
		
		Vector<String> descriptions = new Vector<>( descriptionsMap.keySet() );
		descriptions.sort(stringComparator);
		for (String desc : descriptions)
		{
			DescriptionData descriptionData = descriptionsMap.get(desc);
			if (descriptionData == null) continue;
			
			switch (descriptionData.operator)
			{
			case Equals    : out.print("desc"           ); break;
			case StartsWith: out.print("desc_startswith"); break;
			case Contains  : out.print("desc_contains"  ); break;
			}
			out.printf(" = %s%n", encode(desc)); 
			
			if (descriptionData.hasEpisodeStr())
				out.printf("episodeD = %s%n", descriptionData.episodeStr);
		}
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

	public MenuControl createMenuForTimers(JMenu parent, Window window, Supplier<Timer> getTimer, Supplier<Timer[]> getTimers, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent, window, getTimer, getTimers, GET_DATA_FROM_TIMER, updateAfterMenuAction);
	}
	public MenuControl createMenuForMovies(JMenu parent, Window window, Supplier<MovieList.Movie> getMovie, Supplier<MovieList.Movie[]> getMovies, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent, window, getMovie, getMovies, GET_DATA_FROM_MOVIE, updateAfterMenuAction);
	}
	
	private class SubMenu<V> implements MenuControl
	{
		private final JMenuItem miShowRule;
		private final Supplier<V> getSource;
		private final Supplier<V[]> getSources;
		private final GetData<V> getData;
		private V singleSource;
		private RuleOutput singleSourceAlreadySeenRule;

		SubMenu(JMenu parent, Window window, Supplier<V> getSource, Supplier<V[]> getSources, GetData<V> getData, Runnable updateAfterMenuAction)
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
			
			miShowRule = parent.add(OpenWebifController.createMenuItem("##", e->{
				TextAreaDialog.showText(window, "[Already Seen] Rule", 400, 300, true, singleSourceAlreadySeenRule.toString());
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
			
			singleSourceAlreadySeenRule = singleSource==null ? null : getRuleIfAlreadySeen(singleSource, getData);
			miShowRule.setEnabled(singleSourceAlreadySeenRule != null);
			miShowRule.setText( "Show [Already Seen] Rule" + (singleSource==null ? "" : " for \"%s\"".formatted(getData.getTitle(singleSource))) );
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
		writeToFileAndNotify(ChangeListener.ChangeType.RuleSet);
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
		writeToFileAndNotify(ChangeListener.ChangeType.RuleSet);
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
			final Map<String, DescriptionData> descriptions;
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
				
				descriptions.put(description, new DescriptionData());
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
		
		final Map<String,DescriptionData> descriptions;
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

	public boolean isMarkedAsAlreadySeen(Timer           timer) { return getRuleIfAlreadySeen(timer) != null; }
	public boolean isMarkedAsAlreadySeen(MovieList.Movie movie) { return getRuleIfAlreadySeen(movie) != null; }

	public RuleOutput getRuleIfAlreadySeen(Timer           timer) { return getRuleIfAlreadySeen(timer, GET_DATA_FROM_TIMER); }
	public RuleOutput getRuleIfAlreadySeen(MovieList.Movie movie) { return getRuleIfAlreadySeen(movie, GET_DATA_FROM_MOVIE); }

	private <V> RuleOutput getRuleIfAlreadySeen(final V source, final GetData<V> getData)
	{
		if (source == null)
			return null;
		
		final String title = getData.getTitle(source);
		if (title == null)
			return null;
		
		final EventCriteriaSet ecs = alreadySeenEvents.get(title);
		if (ecs == null)
			return null;
		
		
		if (ecs.stations == null && ecs.descriptions == null)
			return buildRule(title, null, null, null, ecs.variableData.getEpisodeStr());
		
		
		Map<String,DescriptionData> descriptions = null;
		String station = null;
		if (ecs.stations != null)
		{
			station = getData.getStation(source);
			if (station != null)
			{
				StationData stationData = ecs.stations.get(station);
				if (stationData != null)
				{
					if (stationData.descriptions == null)
						return buildRule(title, station, null, null, null);
					
					descriptions = stationData.descriptions;
				}
			}
		}
		
		if (descriptions == null)
			descriptions = ecs.descriptions;
		
		if (descriptions == null) // ECS has no [descriptions] but [stations] and no station was found
			return null;
		
		final String description = getData.getDescription(source);
		if (description == null)
			return null; // no description defined in source
		
		
		for (String descStr : descriptions.keySet())
		{
			DescriptionData descriptionData = descriptions.get(descStr);
			if (descMeetsCriteria(description, descStr, descriptionData.operator))
				return buildRule(title, station, descStr, descriptionData.operator, descriptionData.getEpisodeStr());
		}
		return null;
	}
	
	private boolean descMeetsCriteria(String sourceDesc, String ruleDesc, DescriptionOperator operator)
	{
		switch (operator)
		{
		case Equals    : if (sourceDesc.equals    (ruleDesc)) return true; break;
		case StartsWith: if (sourceDesc.startsWith(ruleDesc)) return true; break;
		case Contains  : if (sourceDesc.contains  (ruleDesc)) return true; break;
		}
		return false;
	}

	private RuleOutput buildRule(String title, String station, String descStr, DescriptionOperator operator, String episodeStr)
	{
		Objects.requireNonNull(title);
		RuleOutput ruleOutput = new RuleOutput();
		ruleOutput.add("Title equals", title);
		
		if (station!=null)
			ruleOutput.add("Station is", station);
		
		if (descStr!=null && operator!=null)
			ruleOutput.add("Description %s".formatted(operator.title), descStr);
		
		if (episodeStr!=null)
			ruleOutput.add("Is Episode", episodeStr);
		
		return ruleOutput;
	}
	
	static class RuleOutput
	{
		record Pair(String label, String value) {}
		
		private final List<Pair> pairs = new ArrayList<>();
		
		void add(String label, String value)
		{
			pairs.add(new Pair(
					Objects.requireNonNull(label),
					Objects.requireNonNull(value)
			));
		}

		@Override
		public String toString()
		{
			return toString("");
		}

		public String toString(String indent)
		{
			
			return pairs
					.stream()
					.map(pair -> "%s%s:%n%s    \"%s\"".formatted(indent, pair.label, indent, pair.value))
					.collect(Collectors.joining("%n".formatted()));
		}

		public void writeIntoOutput(ValueListOutput out, int indentLevel)
		{
			for (Pair pair : pairs)
				out.add(indentLevel, pair.label, pair.value);
		}
	}

	AlreadySeenEventsViewer.RootTreeNode createTreeRoot(AlreadySeenEventsViewer viewer)
	{
		return viewer.createTreeRoot(alreadySeenEvents);
	}
}
