package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.MovieList;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

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
	
	enum TextOperator
	{
		Equals    (""             ),
		Contains  (" [contains]"  ),
		StartsWith(" [startswith]", "Starts with"),
		;
		private final String paramName;
		final String title;
		TextOperator(String paramName) { this(paramName, null); }
		TextOperator(String paramName, String title)
		{
			this.paramName = Objects.requireNonNull(paramName);
			this.title = title!=null ? title : name();
		}
	}
	
	static class DescriptionData extends EpisodeInfo
	{
		TextOperator operator;
		
		DescriptionData()                      { this(null); }
		DescriptionData(DescriptionData other) { this(other, other==null ? null : other.operator); }
		DescriptionData(EpisodeInfo episodeInfo, TextOperator operator)
		{
			super(episodeInfo);
			this.operator = operator==null ? TextOperator.Equals : operator;
		}
	}
	
	static class DescriptionMaps
	{
		final Map<String, DescriptionData> standard = new HashMap<>();
		final Map<String, DescriptionData> extended = new HashMap<>();
		
		boolean isEmpty()
		{
			return standard.isEmpty() && extended.isEmpty();
		}

		void putAll(DescriptionMaps descriptions)
		{
			standard.putAll(descriptions.standard);
			extended.putAll(descriptions.extended);
		}

		int size()
		{
			return standard.size() + extended.size();
		}
	}
	
	record StationData (
			String name,
			DescriptionMaps descriptions
	) {
		static StationData create(String name, boolean createDescriptions)
		{
			return new StationData(
					Objects.requireNonNull( name ),
					createDescriptions ? new DescriptionMaps() : null
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
			DescriptionMaps descriptions
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
					createStations ? new HashMap<>() : null,
					createDescriptions ? new DescriptionMaps() : null
			);
		}
	}
	
	private static class MutableStationData
	{
		final String name;
		final DescriptionMaps descriptions = new DescriptionMaps();
		
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
		final DescriptionMaps descriptions = new DescriptionMaps();
		
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
	
	public interface ChangeListener
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

	void forEachECS(BiConsumer<String, EventCriteriaSet> action)
	{
		alreadySeenEvents.forEach(action);
	}
	
	boolean containsECS(String title)
	{
		return alreadySeenEvents.containsKey(title);
	}

	void deleteECS(String title)
	{
		alreadySeenEvents.remove(title);
	}

	EventCriteriaSet createECS(String title)
	{
		if (alreadySeenEvents.containsKey(title))
			throw new IllegalArgumentException();
		
		EventCriteriaSet ecs = EventCriteriaSet.create(title, false, false);
		alreadySeenEvents.put(title, ecs);
		return ecs;
	}

	DescriptionData createDesc(Map<String, DescriptionData> descMap, String description)
	{
		if (descMap.containsKey(description))
			throw new IllegalArgumentException();
		
		DescriptionData descData = new DescriptionData();
		descMap.put(description, descData);
		return descData;
	}

	public void    addChangeListener(ChangeListener l) { changeListeners.   add(l); }
	public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }
	
	private void notifyChangeListeners(ChangeListener.ChangeType changeType)
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
					
					for (TextOperator op : TextOperator.values())
					{
						if ( (value=getValue(line,    "desc%s = ".formatted(op.paramName)))!=null )
							episodeInfo = setDescription(ecs, stationData, value, op, false);
						if ( (value=getValue(line, "extdesc%s = ".formatted(op.paramName)))!=null )
							episodeInfo = setDescription(ecs, stationData, value, op, true);
					}
					
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

	private EpisodeInfo setDescription(MutableECS ecs, MutableStationData stationData, String value, TextOperator operator, boolean isExtDesc)
	{
		DescriptionData descriptionData = new DescriptionData(null, operator);
		DescriptionMaps descMaps = stationData != null ? stationData.descriptions : ecs.descriptions;
		Map<String, DescriptionData> descMap = !isExtDesc ? descMaps.standard : descMaps.extended;
		descMap.put( decode( value ), descriptionData );
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
					
					writeDescriptionsMaps(out, ecs.descriptions);
					
					if (ecs.stations != null)
					{
						Vector<String> stations = new Vector<>( ecs.stations.keySet() );
						stations.sort(stringComparator);
						
						for (String station : stations)
						{
							StationData stationData = ecs.stations.get(station);
							out.printf("%n[Station]%n");
							out.printf("station = %s%n", encode(station));
							writeDescriptionsMaps(out, stationData.descriptions);
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

	private void writeDescriptionsMaps(PrintWriter out, DescriptionMaps  descriptionsMaps)
	{
		if (descriptionsMaps == null) return;
		writeDescriptionsMap(out, descriptionsMaps.standard,    "desc");
		writeDescriptionsMap(out, descriptionsMaps.extended, "extdesc");
	}

	private void writeDescriptionsMap(PrintWriter out, Map<String, DescriptionData> descriptionsMap, String paramPrefix)
	{
		if (descriptionsMap == null) return;
		
		Vector<String> descriptions = new Vector<>( descriptionsMap.keySet() );
		descriptions.sort(stringComparator);
		for (String desc : descriptions)
		{
			DescriptionData descriptionData = descriptionsMap.get(desc);
			if (descriptionData == null) continue;
			
			out.printf("%s%s = %s%n", paramPrefix, descriptionData.operator.paramName, encode(desc)); 
			
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

	private static abstract class GetData<V>
	{
		final String sourceLabel;
		GetData(String sourceLabel) { this.sourceLabel = sourceLabel; }
		abstract String getTitle          (V value);
		abstract String getStation        (V value);
		abstract String getDescription    (V value);
		abstract String getExtDescription (V value);
	}
	
	private final static GetData<Timer> GET_DATA_FROM_TIMER = new GetData<>("timer")
	{
		@Override public String getTitle          (Timer t) { return t.name;        }
		@Override public String getStation        (Timer t) { return t.servicename; }
		@Override public String getDescription    (Timer t) { return t.description; }
		@Override public String getExtDescription (Timer t) { return t.descriptionextended; }
	};
	private final static GetData<MovieList.Movie> GET_DATA_FROM_MOVIE = new GetData<>("movie")
	{
		@Override public String getTitle          (MovieList.Movie m) { return m.eventname;   }
		@Override public String getStation        (MovieList.Movie m) { return m.servicename; }
		@Override public String getDescription    (MovieList.Movie m) { return m.description; }
		@Override public String getExtDescription (MovieList.Movie m) { return m.descriptionExtended; }
	};
	private final static GetData<EPGevent> GET_DATA_FROM_EPGEVENT = new GetData<>("EPG event")
	{
		@Override public String getTitle          (EPGevent m) { return m.title;        }
		@Override public String getStation        (EPGevent m) { return m.station_name; }
		@Override public String getDescription    (EPGevent m) { return m.shortdesc;    }
		@Override public String getExtDescription (EPGevent m) { return m.longdesc;     }
	};
	
	public interface MenuControl {
		void updateBeforeShowingMenu();
	}

	public MenuControl createMenuForTimer(JMenu parent, Window window, Supplier<Timer> getTimer, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent::add, window, getTimer, null, GET_DATA_FROM_TIMER, updateAfterMenuAction);
	}
	public MenuControl createMenuForTimers(JMenu parent, Window window, Supplier<Timer[]> getTimers, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent::add, window, null, getTimers, GET_DATA_FROM_TIMER, updateAfterMenuAction);
	}
	public MenuControl createMenuForMovie(JMenu parent, Window window, Supplier<MovieList.Movie> getMovie, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent::add, window, getMovie, null, GET_DATA_FROM_MOVIE, updateAfterMenuAction);
	}
	public MenuControl createMenuForMovies(JMenu parent, Window window, Supplier<MovieList.Movie[]> getMovies, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent::add, window, null, getMovies, GET_DATA_FROM_MOVIE, updateAfterMenuAction);
	}
	public MenuControl createMenuForEPGevent(JPopupMenu parent, Window window, Supplier<EPGevent> getEPGevent, Runnable updateAfterMenuAction)
	{
		return new SubMenu<>(parent::add, window, getEPGevent, null, GET_DATA_FROM_EPGEVENT, updateAfterMenuAction);
	}
	
	private interface ParentMenu
	{
		JMenuItem add(JMenuItem menuItem);
	}
	
	private class SubMenu<V> implements MenuControl, UserInteraction
	{
		private final Window window;
		private final JMenu menuAdd;
		private final JMenuItem miShowRule;
		private final Supplier<V> getSource;
		private final Supplier<V[]> getSources;
		private final GetData<V> getData;
		private RuleOutput singleSourceAlreadySeenRule;

		SubMenu(ParentMenu parent, Window window, Supplier<V> getSource, Supplier<V[]> getSources, GetData<V> getData, Runnable updateAfterMenuAction)
		{
			this.window = Objects.requireNonNull( window );
			this.getSource = getSource;
			this.getSources = getSources;
			this.getData = Objects.requireNonNull( getData );
			
			parent.add(menuAdd = new JMenu("Mark as Already Seen"));
			menuAdd.add(OWCTools.createMenuItem("Title"                      , e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, false, false, false)));
			menuAdd.add(OWCTools.createMenuItem("Title, Description"         , e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, false, true , false)));
			menuAdd.add(OWCTools.createMenuItem("Title, Extended Description", e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, false, false, true )));
			menuAdd.add(OWCTools.createMenuItem("Title, Station"             , e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, true , false, false)));
			menuAdd.add(OWCTools.createMenuItem("Title, Station, Description", e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, true , true , false)));
			menuAdd.add(OWCTools.createMenuItem("Title, Station, Ext. Desc." , e -> markAsAlreadySeen(this.getSource, this.getSources, this.getData, this, updateAfterMenuAction, true , false, true )));
			
			miShowRule = parent.add(OWCTools.createMenuItem("##", e->{
				if (singleSourceAlreadySeenRule != null)
					TextAreaDialog.showText(this.window, "[Already Seen] Rule", 400, 300, true, singleSourceAlreadySeenRule.toString());
			}));
		}
		
		@Override
		public void showMessage(String title, String... msgLines)
		{
			JOptionPane.showMessageDialog(window, msgLines, title, JOptionPane.INFORMATION_MESSAGE);
		}

		@Override
		public void updateBeforeShowingMenu()
		{
			V singleSource = null;
			V[] sources = null;
			
			if (getSource!=null)
				singleSource = getSource.get();
			
			else if (getSources!=null)
			{
				sources = getSources.get();
				if (sources!=null && sources.length==1)
					singleSource = sources[0];
			}
			
			singleSourceAlreadySeenRule = singleSource==null ? null : getRuleIfAlreadySeen(singleSource, getData);
			miShowRule.setEnabled(singleSourceAlreadySeenRule != null);
			menuAdd   .setEnabled(singleSource != null || (sources != null && sources.length > 0));
			miShowRule.setText( "Show [Already Seen] Rule%s".formatted(singleSource==null ? "" : " for \"%s\"".formatted(getData.getTitle(singleSource))) );
			menuAdd   .setText( "Mark%s as Already Seen"    .formatted(singleSource==null ? "" : " \"%s\""    .formatted(getData.getTitle(singleSource))) );
		}
	}

	private <V> void markAsAlreadySeen(
			Supplier<V> getSource,
			Supplier<V[]> getSources,
			GetData<V> getData,
			UserInteraction userInteraction,
			Runnable updateAfterMenuAction,
			boolean useStation, boolean useDescription, boolean useExtDescription
	) {
		doWithSources(getSource, getSources, t1 -> markAsAlreadySeen(t1, getData, userInteraction, useStation, useDescription, useExtDescription) );
		if (updateAfterMenuAction!=null)
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
	
	private interface UserInteraction
	{
		void showMessage(String title, String... msgLines);
	}

	private <V>void markAsAlreadySeen(
			final V source,
			final GetData<V> getData,
			final UserInteraction userInteraction,
			final boolean useStation,
			final boolean useDescription,
			final boolean useExtDescription
	) {
		if (source == null)
			return;
		
		final String title = getData.getTitle(source);
		if (title == null)
		{
			userInteraction.showMessage(
					"Can't mark as \"already seen\"",
					"Sorry, can't mark %s as \"already seen\",".formatted(getData.sourceLabel),
					"because this %s don't have a title."      .formatted(getData.sourceLabel)
			);
			return; // no title defined in source
		}
		
		final EventCriteriaSet ecs = alreadySeenEvents.computeIfAbsent(title, k -> EventCriteriaSet.create(title, useStation, (useDescription || useExtDescription) && !useStation));
		
		if (!useStation && !useDescription && !useExtDescription) // define a general event criteria set ( based only on title )
		{
			if (ecs.stations != null || ecs.descriptions != null)
			{
				boolean noStations     = ecs.stations    ==null || ecs.stations    .isEmpty();
				boolean noDescriptions = ecs.descriptions==null || ecs.descriptions.isEmpty();
				
				if ( noStations && noDescriptions )
					// replace station event criteria set or description based event criteria set with general event criteria set
					alreadySeenEvents.put(title, EventCriteriaSet.create(title, false, false));
				else
				{
					String definedItems =
							!noStations && !noDescriptions ? "stations and descriptions"
							: !noStations ? "stations"
							: !noDescriptions ? "descriptions"
							: "????";
					userInteraction.showMessage(
							"Can't mark as \"already seen\"",
							"Sorry, can't mark %s as \"already seen\",".formatted( getData.sourceLabel ),
							"because you want me to create a criteria set with only a title,",
							"but there are already some %s defined for this title.".formatted( definedItems )
					);
				}
			}
		}
		else
		{
			final DescriptionMaps descriptions;
			if (!useStation)
				descriptions = ecs.descriptions;
			else
			{
				final String station = getData.getStation(source);
				if (station == null)
				{
					userInteraction.showMessage(
							"Can't mark as \"already seen\"",
							"Sorry, can't mark %s as \"already seen\","    .formatted(getData.sourceLabel),
							"because this %s don't have a station defined.".formatted(getData.sourceLabel)
					);
					return; // no station defined in source
				}
				if (ecs.stations == null)
				{
					userInteraction.showMessage(
							"Can't mark as \"already seen\"",
							"Sorry, can't mark %s as \"already seen\","    .formatted(getData.sourceLabel),
							"because a criteria set for this title exists",
							"and don't allow stations as a criterion."
					);
					return; // event criteria set is stationless  ->  station is not needed as criteria
				}
				
				StationData stationData = ecs.stations.computeIfAbsent(station, k -> StationData.create(station, useDescription));
				if (!useDescription && !useExtDescription && stationData.descriptions != null )
				{ // replace description based station event criteria set with descriptionless (general) station event criteria set 
					ecs.stations.put(station, stationData = StationData.create(station, false));
				}
				
				descriptions = stationData.descriptions;
			}
			
			if (useDescription || useExtDescription)
			{
				// case (useDescription && useExtDescription) is not allowed -> standard description will be preferred then
				
				if (descriptions == null)
				{
					userInteraction.showMessage(
							"Can't mark as \"already seen\"",
							"Sorry, can't mark %s as \"already seen\","    .formatted(getData.sourceLabel),
							"because a criteria set for this title exists",
							"and don't allow descriptions as a criterion."
					);
					return; // ecs or station is descriptionless -> can't add description as criteria
				}
				
				final String description;
				if      (useDescription   ) description = getData.   getDescription(source);
				else if (useExtDescription) description = getData.getExtDescription(source);
				else throw new IllegalStateException();
				
				if (description == null)
				{
					String str;  
					if      (useDescription   ) str = "a description";
					else if (useExtDescription) str = "an extended description";
					else throw new IllegalStateException();
					
					userInteraction.showMessage(
							"Can't mark as \"already seen\"",
							"Sorry, can't mark %s as \"already seen\",".formatted( getData.sourceLabel ),
							"because this %s don't have %s defined."   .formatted( getData.sourceLabel, str )
					);
					return; // no description defined in source
				}
				
				Map <String, DescriptionData> descMap = useDescription ? descriptions.standard : descriptions.extended;
				descMap.put(description, new DescriptionData());
			}
		}
	}

	public boolean isMarkedAsAlreadySeen(Timer           timer) { return getRuleIfAlreadySeen(timer) != null; }
	public boolean isMarkedAsAlreadySeen(MovieList.Movie movie) { return getRuleIfAlreadySeen(movie) != null; }
	public boolean isMarkedAsAlreadySeen(EPGevent        event) { return getRuleIfAlreadySeen(event) != null; }

	public RuleOutput getRuleIfAlreadySeen(Timer           timer) { return getRuleIfAlreadySeen(timer, GET_DATA_FROM_TIMER); }
	public RuleOutput getRuleIfAlreadySeen(MovieList.Movie movie) { return getRuleIfAlreadySeen(movie, GET_DATA_FROM_MOVIE); }
	public RuleOutput getRuleIfAlreadySeen(EPGevent        event) { return getRuleIfAlreadySeen(event, GET_DATA_FROM_EPGEVENT); }

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
			return new RuleOutput(title, null, null, false, null, ecs.variableData.getEpisodeStr());
		
		
		DescriptionMaps descriptions = null;
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
						return new RuleOutput(title, station, null, false, null, null);
					
					descriptions = stationData.descriptions;
				}
			}
		}
		
		if (descriptions == null)
			descriptions = ecs.descriptions;
		
		if (descriptions == null) // ECS has no [descriptions] but [stations] exists and no station was found
			return null;
		
		RuleOutput ruleOutput = null;
		if (ruleOutput == null) ruleOutput = findDescription(title, station, getData.getDescription   (source), descriptions.standard, false);
		if (ruleOutput == null) ruleOutput = findDescription(title, station, getData.getExtDescription(source), descriptions.extended, true );
		return ruleOutput;
	}
	
	private RuleOutput findDescription(final String title, final String station, final String description, final Map<String, DescriptionData> descriptions, boolean isExtDesc)
	{
		if (description == null)
			return null; // no description defined in source
		
		for (String descStr : descriptions.keySet())
		{
			DescriptionData descriptionData = descriptions.get(descStr);
			if (descMeetsCriteria(description, descStr, descriptionData.operator))
				return new RuleOutput(title, station, descStr, isExtDesc, descriptionData.operator, descriptionData.getEpisodeStr());
		}
		return null;
	}

	private boolean descMeetsCriteria(String sourceDesc, String ruleDesc, TextOperator operator)
	{
		switch (operator)
		{
		case Equals    : if (sourceDesc.equals    (ruleDesc)) return true; break;
		case StartsWith: if (sourceDesc.startsWith(ruleDesc)) return true; break;
		case Contains  : if (sourceDesc.contains  (ruleDesc)) return true; break;
		}
		return false;
	}
	
	public static class RuleOutput
	{
		private record Pair(String label, String value) {}
		
		private final List<Pair> output = new ArrayList<>();
		public  final String title;
		public  final String station;
		public  final String descStr;
		public  final boolean isExtDesc;
		public  final TextOperator operator;
		public  final String episodeStr;
		
		private RuleOutput(String title, String station, String descStr, boolean isExtDesc, TextOperator operator, String episodeStr)
		{
			this.title = Objects.requireNonNull(title);
			this.station = station;
			this.descStr = descStr;
			this.isExtDesc = isExtDesc;
			this.operator = operator;
			this.episodeStr = episodeStr;
			
			addOutput("Title equals", title);
			
			if (station!=null)
				addOutput("Station is", station);
			
			if (descStr!=null && operator!=null)
				addOutput("%sDescription %s".formatted(isExtDesc ? "Extended " : "", operator.title), descStr);
			
			if (episodeStr!=null)
				addOutput("Is Episode", episodeStr);
		}
		
		private void addOutput(String label, String value)
		{
			output.add(new Pair(
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
			
			return output
					.stream()
					.map(pair -> "%s%s:%n%s    \"%s\"".formatted(indent, pair.label, indent, pair.value))
					.collect(Collectors.joining("%n".formatted()));
		}

		public void writeIntoOutput(ValueListOutput out, int indentLevel)
		{
			for (Pair pair : output)
				out.add(indentLevel, pair.label, pair.value);
		}
	}
}
