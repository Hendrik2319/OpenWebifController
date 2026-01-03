package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Window;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerDataUpdateNotifier;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents;

public class SingleStationEPGPanel extends JSplitPane
{
	private static final long serialVersionUID = 4349270717898712489L;
	
	private final JTable epgTable;
	private final EPGTableModel epgTableModel;
	private final JTextArea epgOutput;
	private final JScrollPane epgOutputScrollPane;
	private final DataAcquisition dataAcquisition;
	private final Tables.SimplifiedRowSorter epgTableRowSorter;

	private EPGevent selectedEvent;
	private Timer selectedTimer;

	public SingleStationEPGPanel(EPG epg, TimerDataUpdateNotifier timerNotifier, Supplier<String> getBaseURL, Consumer<String> setStatusOutput, EPGDialog.TimerCommands timerCommands, Window window)
	{
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		selectedEvent = null;
		selectedTimer = null;
		
		epgTable = new JTable(epgTableModel = new EPGTableModel(timerNotifier));
		epgTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		epgTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		epgTable.setRowSorter( epgTableRowSorter = new Tables.SimplifiedRowSorter(epgTableModel) );
		epgTableModel.setTable(epgTable);
		epgTableModel.setColumnWidths(epgTable);
		epgTableModel.setCellRenderers();
		
		new EPGTableContextMenu(window, getBaseURL, timerCommands, timerNotifier::getTimers);
		
		JScrollPane tableScrollPane = new JScrollPane(epgTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		epgOutput = new JTextArea();
		epgOutput.setLineWrap(true);
		epgOutput.setWrapStyleWord(true);
		
		epgOutputScrollPane = new JScrollPane(epgOutput);
		epgOutputScrollPane.setPreferredSize(new Dimension(600,500));
		
		setLeftComponent(tableScrollPane);
		setRightComponent(epgOutputScrollPane);
		
		epgTable.getSelectionModel().addListSelectionListener(e -> {
			int rowV = epgTable.getSelectedRow();
			int rowM = rowV<0 ? -1 : epgTable.convertRowIndexToModel(rowV);
			selectedEvent = rowM<0 ? null : epgTableModel.getRow(rowM);
			selectedTimer = epgTableModel.getEventTimer(selectedEvent);
			showEPGOutput();
		});
		
		dataAcquisition = new DataAcquisition(
				epgTableModel, epg, getBaseURL, setStatusOutput, 4*60*60,
				str -> SwingUtilities.invokeLater(()->showEPGOutput(str))
		);
	}

	private void showEPGOutput()
	{
		showEPGOutput(selectedEvent==null ? null : OWCTools.generateOutput(selectedEvent, selectedTimer));
	}

	private void showEPGOutput(String str)
	{
		ScrollPosition scrollPos = ScrollPosition.getVertical(epgOutputScrollPane);
		epgOutput.setText(str);
		if (scrollPos!=null) SwingUtilities.invokeLater(()->scrollPos.setVertical(epgOutputScrollPane));
	}
	
	public void readEPG(SubService station) { dataAcquisition.readEPG( station ); }
	public void readEPG(Bouquet    bouquet) { dataAcquisition.readEPG( bouquet ); }
	public void dontReadEPG()               { dataAcquisition.dontReadEPG(); }
	
	static class DataAcquisition
	{
		private final EPG epg;
		private final Supplier<String> getBaseURL;
		private final Consumer<String> setStatusOutput;
		private final Consumer<String> setTextboxOutput;
		private final EPGTableModel epgTableModel;
		private DataAcquisitionTask<?> currentTask = null;
		private Thread thread = null;
		private final long leadTime_s;
		
		DataAcquisition(EPGTableModel epgTableModel, EPG epg, Supplier<String> getBaseURL, Consumer<String> setStatusOutput, long leadTime_s, Consumer<String> setTextboxOutput)
		{
			this.epgTableModel = Objects.requireNonNull(epgTableModel);
			this.epg = Objects.requireNonNull(epg);
			this.getBaseURL = Objects.requireNonNull(getBaseURL);
			this.setStatusOutput = Objects.requireNonNull(setStatusOutput);
			this.setTextboxOutput = Objects.requireNonNull(setTextboxOutput);
			this.leadTime_s = leadTime_s;
		}

		public void readEPG(SubService station)
		{
			addTask( station==null ? null : new DataAcquisitionTask.StationTask(station, setStatusOutput, leadTime_s));
		}

		public void readEPG(Bouquet bouquet)
		{
			addTask( bouquet==null ? null : new DataAcquisitionTask.BouquetTask(bouquet, setStatusOutput));
		}

		public void dontReadEPG()
		{
			addTask( null );
		}

		public void addTask(DataAcquisitionTask<?> task)
		{
			if (task==null || !task.isOk())
				setValues(new Vector<>(), null, "", null);
			
			else
				synchronized (this)
				{
					if (thread!=null)
						currentTask = task;
					else
						(thread = new Thread(()->threadLoop(task))).start();
				}
		}

		private void threadLoop(DataAcquisitionTask<?> task)
		{
			String baseURL = getBaseURL.get();
			
			boolean loopActive = true;
			while (loopActive) {
				setValues(new Vector<>(), null, task.getLoadingText(), task);
				
				Vector<EPGevent> events = task.readEPG(epg, baseURL);
				EPGEventGenres.getInstance().scanGenres(events).writeToFile();
				
				synchronized (this)
				{
					if (currentTask==null)
					{
						thread = null;
						loopActive = false;
					}
					else if (currentTask.isEqualTask(task))
					{
						setValues(events, task, "", null);
						thread = null;
						loopActive = false;
					}
					else
						task = currentTask;
				}
			}
		}
		
		private synchronized void setValues(Vector<EPGevent> data, DataAcquisitionTask<?> dataSourceTask, String text, DataAcquisitionTask<?> nextTask)
		{
			epgTableModel.setData(data);
			if (dataSourceTask!=null)
				epgTableModel.setViewStyle( dataSourceTask.sourceDependingViewStyle );
			setTextboxOutput.accept(text);
			currentTask = nextTask;
		}
	}
	
	private static abstract class DataAcquisitionTask<SourceType>
	{
		protected final SourceType source;
		private   final Class<SourceType> sourceClass;
		private   final String sourceLabel;
		private   final EPGTableModel.ViewStyle sourceDependingViewStyle;
		protected final Consumer<String> setStatusOutput;
		private   final Function<SourceType, String> getSRef;
		
		protected DataAcquisitionTask(SourceType source, Class<SourceType> sourceClass, String sourceLabel, EPGTableModel.ViewStyle sourceDependingViewStyle, Function<SourceType,String> getSRef, Consumer<String> setStatusOutput)
		{
			this.source          = Objects.requireNonNull(source         );
			this.sourceClass     = Objects.requireNonNull(sourceClass    );
			this.sourceLabel     = Objects.requireNonNull(sourceLabel    );
			this.sourceDependingViewStyle = Objects.requireNonNull(sourceDependingViewStyle);
			this.getSRef         = Objects.requireNonNull(getSRef        );
			this.setStatusOutput = Objects.requireNonNull(setStatusOutput);
		}
		
		String getLoadingText()
		{
			return String.format("Loading EPG of %s ...", sourceLabel);
		}
		
		boolean isEqualTask(DataAcquisitionTask<?> other)
		{
			Object otherSource_ = other.source;
			if (!sourceClass.isInstance(otherSource_))
				return false;
			
			SourceType otherSource = sourceClass.cast(otherSource_);
			
			String thisSRef = getSRef.apply(source);
			String otherSRef = getSRef.apply(otherSource);
			
			return thisSRef.toUpperCase().equals(otherSRef.toUpperCase());
		}
		
		protected void setDefaultProgressOutput(String taskTitle)
		{
			setStatusOutput.accept(String.format("EPG of %s: %s", sourceLabel, taskTitle));
		}
		
		boolean isOk() { return true; }
		abstract Vector<EPGevent> readEPG(EPG epg, String baseURL);
		
		
		static class StationTask extends DataAcquisitionTask<SubService>
		{
			private final long leadTime_s;

			StationTask(SubService station, Consumer<String> setStatusOutput, long leadTime_s)
			{
				super(
						station,
						SubService.class,
						String.format("Station \"%s\"", station.name),
						EPGTableModel.ViewStyle.Station,
						st -> st.servicereference,
						setStatusOutput
				);
				this.leadTime_s = leadTime_s;
			}
			
			@Override
			boolean isOk()
			{
				return !source.isMarker();
			}

			@Override
			Vector<EPGevent> readEPG(EPG epg, String baseURL)
			{
				long beginTime_UnixTS = System.currentTimeMillis()/1000 - leadTime_s;
				return epg.readEPGforService(baseURL, source.service.stationID, beginTime_UnixTS, null, this::setDefaultProgressOutput);
			}
			
		}
		
		
		static class BouquetTask extends DataAcquisitionTask<Bouquet>
		{
			BouquetTask(Bouquet bouquet, Consumer<String> setStatusOutput)
			{
				super(
						bouquet,
						Bouquet.class,
						String.format("Bouquet \"%s\"", bouquet.name),
						EPGTableModel.ViewStyle.Bouquet,
						b -> b.servicereference,
						setStatusOutput
				);
			}

			@Override
			Vector<EPGevent> readEPG(EPG epg, String baseURL)
			{
				return epg.readEPGNowNextForBouquet(baseURL, source, this::setDefaultProgressOutput);
			}
		}
	}
	
	private class EPGTableContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = 2824217721324395677L;

		private final EPGDialog.TimerCommands timerCommands;
		private final Supplier<String> getBaseURL;
		private final JMenuItem miAddRecordTimer;
		private final JMenuItem miAddSwitchTimer;
		private final JMenuItem miAddRecordNSwitchTimer;
		private final JMenuItem miToggleTimer;
		private final JMenuItem miDeleteTimer;
		private final JMenuItem miShowCollisions;
		
		private EPGevent clickedEvent;
	//	private EPGevent selectedEvent;
		private Timer clickedEventTimer;

		EPGTableContextMenu(Window window, Supplier<String> getBaseURL, EPGDialog.TimerCommands timerCommands, Supplier<Timers> getTimers)
		{
			this.getBaseURL = Objects.requireNonNull(getBaseURL);
			this.timerCommands = Objects.requireNonNull(timerCommands);
			clickedEvent = null;
		//	selectedEvent = null;
			
			add(miAddRecordTimer        = OWCTools.createMenuItem("###", GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.Record       )));
			add(miAddSwitchTimer        = OWCTools.createMenuItem("###", GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.Switch       )));
			add(miAddRecordNSwitchTimer = OWCTools.createMenuItem("###", GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.RecordNSwitch)));
			add(miToggleTimer           = OWCTools.createMenuItem("###"                                   , e->toggleTimer()));
			add(miDeleteTimer           = OWCTools.createMenuItem("###", GrayCommandIcons.IconGroup.Delete, e->deleteTimer()));
			add(miShowCollisions        = OWCTools.createMenuItem("###", e->OWCTools.showCollisions(window, clickedEvent, getTimers)));
			
			addSeparator();
			
			AlreadySeenEvents.MenuControl aseMenuControlClicked = AlreadySeenEvents
					.getInstance()
					.createMenuForEPGevent(this, window, ()->clickedEvent, () -> {
						epgTableModel.fireTableColumnUpdate(EPGTableModel.ColumnID.Seen);
						if (clickedEvent == selectedEvent)
							showEPGOutput();
					});
			
			addSeparator();
			
			add(OWCTools.createMenuItem("Show Column Widths", e->{
				System.out.printf("Column Widths: %s%n", EPGTableModel.getColumnWidthsAsString(epgTable));
			}));
			
			add(OWCTools.createMenuItem("Reset Row Order", e->{
				epgTableRowSorter.resetSortOrder();
				epgTable.repaint();
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = epgTable.rowAtPoint(new Point(x,y));
				int rowM = rowV<0 ? -1 : epgTable.convertRowIndexToModel(rowV);
				clickedEvent = epgTableModel.getRow(rowM);
				clickedEventTimer = epgTableModel.getEventTimer(clickedEvent);
				
			//	int selRowV = epgTable.getSelectedRow();
			//	int selRowM = selRowV<0 ? -1 : epgTable.convertRowIndexToModel(selRowV);
			//	selectedEvent = epgTableModel.getRow(selRowM);
				
				boolean isEventOK =
						clickedEvent!=null &&
						clickedEvent.sref!=null &&
						clickedEvent.id!=null;
				
				miAddRecordTimer       .setEnabled(isEventOK && clickedEventTimer==null);
				miAddSwitchTimer       .setEnabled(isEventOK && clickedEventTimer==null);
				miAddRecordNSwitchTimer.setEnabled(isEventOK && clickedEventTimer==null);
				miToggleTimer          .setEnabled(isEventOK && clickedEventTimer!=null);
				miDeleteTimer          .setEnabled(isEventOK && clickedEventTimer!=null);
				miShowCollisions       .setEnabled(isEventOK);
				miAddRecordTimer       .setText(!isEventOK || clickedEvent.title==null ? "Add Record Timer"          : String.format("Add "+"Record"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddSwitchTimer       .setText(!isEventOK || clickedEvent.title==null ? "Add Switch Timer"          : String.format("Add "+"Switch"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddRecordNSwitchTimer.setText(!isEventOK || clickedEvent.title==null ? "Add Record'N'Switch Timer" : String.format("Add "+"Record'N'Switch"+" Timer for Event \"%s\"", clickedEvent.title));
				miToggleTimer          .setText(clickedEventTimer==null                ? "Toggle Timer"              : String.format("Toggle Timer \"%s\"", clickedEventTimer.name));
				miDeleteTimer          .setText(clickedEventTimer==null                ? "Delete Timer"              : String.format("Delete Timer \"%s\"", clickedEventTimer.name));
				miShowCollisions       .setText(!isEventOK || clickedEvent.title==null ? "Show Collisions"           : String.format("Show Collisions for Event \"%s\"", clickedEvent.title));
				
				aseMenuControlClicked.updateBeforeShowingMenu();
			});
			
			addTo(epgTable);
		}
		
		private void addTimer(Timers.Timer.Type type) { timerCommands.addTimer   (getBaseURL.get(), clickedEvent.sref, clickedEvent.id.intValue(), type); }
		private void deleteTimer()                    { timerCommands.deleteTimer(getBaseURL.get(), clickedEventTimer); }
		private void toggleTimer()                    { timerCommands.toggleTimer(getBaseURL.get(), clickedEventTimer); }
	}

	static class EPGTableModel extends Tables.SimpleGetValueTableModel<EPGevent, EPGTableModel.ColumnID> {
		
		private static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
			return OWCTools.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
		}
		
		private static boolean areEqual(ColumnID[] array1, ColumnID[] array2)
		{
			if (array1==null && array2==null) return true;
			if (array1==null || array2==null) return false;
			if (array1.length!=array2.length) return false;
			
			for (int i=0; i<array1.length; i++)
				if (array1[i] != array2[i])
					return false;
			
			return true;
		}

		private static String getBeginDisplayStr(EPGevent ev)
		{
			if (ev.begin_timestamp==null) return "";
			long millis = ev.begin_timestamp*1000;
			return formatDate(millis,  true,  true, false,  true, false);
		}
		
		private static String getEndDisplayStr(EPGevent ev)
		{
			if (ev.begin_timestamp==null) return "";
			long duration_sec = ev.duration_sec==null ? 0 : ev.duration_sec;
			long millis = (ev.begin_timestamp+duration_sec)*1000;
			return formatDate(millis, false, false, false,  true, false);
		}
		
		private static Long getEnd(EPGevent ev)
		{
			if (ev.begin_timestamp==null) return null;
			long duration_sec = ev.duration_sec==null ? 0 : ev.duration_sec;
			return ev.begin_timestamp+duration_sec;
		}
		
		private static boolean isInTimeSpan(EPGevent ev, long millis)
		{
			Long start = ev.begin_timestamp;
			Long end   = getEnd(ev);
			return start != null && end != null && start*1000 <= millis && millis <= end*1000;
		}

		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
			// Column Widths: [45, 200, 170, 60, 60, 90, 350, 250, 250, 60, 60, 60, 110, 85, 90, 75, 75, 55, 105, 70, 300, 230] in ModelOrder
			ID           (config("ID"               , Long      .class,  45, CENTER).setValFunc(   ev  -> ev.id              )),
			Name         (config("Name"             , String    .class, 200,   null).setValFunc(   ev  -> ev.title           )),
			Begin        (config("Begin"            , Long      .class, 170,   null).setValFunc(   ev  -> ev.begin_timestamp ).setToStringR(EPGTableModel::getBeginDisplayStr)),
			End          (config("End"              , Long      .class,  60,   null).setValFunc(   EPGTableModel::getEnd     ).setToStringR(EPGTableModel::getEndDisplayStr  )),
			Duration     (config("Duration"         , Long      .class,  60,   null).setValFunc(   ev  -> ev.duration_sec    ).setToStringR(ev -> ev.duration_sec==null ? "" : DateTimeFormatter.getDurationStr(ev.duration_sec))),
			Timer        (config("Timer"            , Timer.Type.class,  90, CENTER).setValFunc((m,ev) -> getTimerType(m,ev) )),
			Seen         (config("Seen"             , String    .class,  75, CENTER).setValFunc(   ev  -> toString(AlreadySeenEvents.getInstance().getRuleIfAlreadySeen(ev)))),
			Genre        (config("Genre"            , Long      .class, 350,   LEFT).setValFunc(   ev  -> ev.genreid         ).setToStringR(ev -> " [%03d] %s".formatted(ev.genreid, ev.genre))),
			ShortDesc    (config("Short Description", String    .class, 250,   null).setValFunc(   ev  -> ev.shortdesc       )),
			LongDesc     (config("Long Description" , String    .class, 250,   null).setValFunc(   ev  -> ev.longdesc        )),
			Str_Date     (config("<date>"           , String    .class,  60,   null).setValFunc(   ev  -> ev.date            )),
			Str_Begin    (config("<begin>"          , String    .class,  60,   null).setValFunc(   ev  -> ev.begin           )),
			Str_End      (config("<end>"            , String    .class,  60,   null).setValFunc(   ev  -> ev.end             )),
			Now          (config("<now_timestamp>"  , Long      .class, 110,   null).setValFunc(   ev  -> ev.now_timestamp   ).setToStringR(ev -> formatDate((ev.now_timestamp)*1000, false, true, false, true, false))),
			IsUpToDate   (config("<isUpToDate>"     , Boolean   .class,  85, CENTER).setValFunc(   ev  -> ev.isUpToDate      )),
			Duration_min (config("<duration_min>"   , Long      .class,  90,   null).setValFunc(   ev  -> ev.duration_min    )),
			Remaining    (config("<remaining>"      , Long      .class,  75,   null).setValFunc(   ev  -> ev.remaining       )),
			Progress     (config("<progress>"       , Long      .class,  75,   null).setValFunc(   ev  -> ev.progress        )),
			CompProgress (config("Progress"         , Double    .class,  75,   null).setValFunc(   ev  -> ev.computedProgress).setToStringR(ev -> ev.computedProgress==null ? "" : String.format(Locale.ENGLISH, "%1.1f %%", ev.computedProgress*100 ))),
			TLeft        (config("<tleft>"          , Long      .class,  55,   null).setValFunc(   ev  -> ev.tleft           )),
			Station      (config("Station"          , String    .class, 105,   null).setValFunc(   ev  -> ev.station_name    )),
			Provider     (config("<provider>"       , String    .class,  70,   null).setValFunc(   ev  -> ev.provider        )),
			Picon        (config("<picon>"          , String    .class, 300,   null).setValFunc(   ev  -> ev.picon           )),
			SRef         (config("<sref>"           , String    .class, 230,   null).setValFunc(   ev  -> ev.sref            )),
			;
			
			private final Tables.SimplifiedColumnConfig2<EPGTableModel, EPGevent, ?> cfg;
			ColumnID(Tables.SimplifiedColumnConfig2<EPGTableModel, EPGevent, ?> cfg) { this.cfg = cfg; }
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return this.cfg; }
			@Override public Function<EPGevent, ?> getGetValue() { return cfg.getValue; }
			
			private static <T> Tables.SimplifiedColumnConfig2<EPGTableModel, EPGevent, T> config(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment)
			{
				return new Tables.SimplifiedColumnConfig2<>(name, columnClass, 20, -1, prefWidth, prefWidth, horizontalAlignment);
			}
			
			private static Timer.Type getTimerType(EPGTableModel model, EPGevent event)
			{
				Timer timer = model.getEventTimer(event);
				return timer==null ? null : timer.type;
			}
			
			private static String toString(AlreadySeenEvents.RuleOutput ruleOutput)
			{
				if (ruleOutput == null)
					return null;
				if (ruleOutput.episodeStr == null)
					return "Seen";
				return "[%s]".formatted(ruleOutput.episodeStr);
			}
		}
		
		private static final ViewStyle INITIAL_VIEW_STYLE = ViewStyle.Station;
		private Timers timers;
		private final TimersMap timersMap;
		private final CustomCellRenderer renderer;
		private ViewStyle viewStyle;
		private long dataTimeStamp_ms;
		private Vector<EPGevent> dataVec;
		
		EPGTableModel(TimerDataUpdateNotifier timerNotifier)
		{
			super( INITIAL_VIEW_STYLE.columns );
			dataVec = null;
			
			viewStyle = INITIAL_VIEW_STYLE;
			timersMap = new TimersMap();
			timers = timerNotifier.getTimers();
			timerNotifier.addListener(timers -> {
				this.timers = timers;
				timersMap.updateData(this.timers, dataVec);
				fireTableUpdate();
			});
			renderer = new CustomCellRenderer();
			dataTimeStamp_ms = System.currentTimeMillis();
		}
		
		public static ColumnID[] getDefaultColumnIDArray()
		{
			return ColumnID.values();
		}

		public void setViewStyle(ViewStyle viewStyle)
		{
			if (this.viewStyle == viewStyle)
				return;
			
			this.viewStyle = Objects.requireNonNull(viewStyle);
			
			if (!areEqual(columns, viewStyle.columns))
			{
				columns = viewStyle.columns;
				fireTableStructureUpdate();
				setColumnWidths(table);
			}
		}

		public void setData(Vector<EPGevent> data)
		{
			super.setData(Tables.DataSource.createFrom(data));
			this.dataVec = data;
			dataTimeStamp_ms = System.currentTimeMillis();
			timersMap.updateData(this.timers, dataVec);
		}
		@Override public void setData(EPGevent[] data) { throw new UnsupportedOperationException(); }
		@Override public void setData(List<EPGevent> data) { throw new UnsupportedOperationException(); }
		@Override public void setData(Tables.DataSource<EPGevent> data) { throw new UnsupportedOperationException(); }

		public Timer getEventTimer(EPGevent event)
		{
			return timersMap.getEventTimer(event);
		}

		@Override protected Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID, EPGevent row)
		{
			if (columnID!=null && columnID.cfg.getValueM!=null)
				return columnID.cfg.getValueM.apply(this, row);
			
			return super.getValueAt(rowIndex, columnIndex, columnID, row);
		}

		void setCellRenderers()
		{
			setDefaultRenderers(clazz -> renderer);
		}
		
		enum ViewStyle
		{
			Station(
					EPGTableModel.getDefaultColumnIDArray()
			),
			Bouquet(
					new Tables.ArrayReorderer<>(EPGTableModel.getDefaultColumnIDArray())
					.moveToFirst(EPGTableModel.ColumnID.Station)
					.moveAfter  (EPGTableModel.ColumnID.CompProgress, EPGTableModel.ColumnID.Duration)
					.toArray    (EPGTableModel.ColumnID[]::new)
			),
			;
			private final ColumnID[] columns;

			ViewStyle(ColumnID[] columns)
			{
				this.columns = Objects.requireNonNull( columns );
			}
		}
		
		private class CustomCellRenderer implements TableCellRenderer
		{
			private static final Color COLOR_FG_INACTIVE_EVENT = new Color(0x92B4C9);
			private static final Color COLOR_FG_MARKER         = new Color(0xFF8700);
			private final Tables.LabelRendererComponent label;
			private final ProgressScaleRC progressScale;
			
			CustomCellRenderer()
			{
				label = new Tables.LabelRendererComponent();
				progressScale = new ProgressScaleRC();
			}

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				int rowM = table.convertRowIndexToModel(rowV);
				EPGevent event = getRow(rowM);
				Timer timer = getEventTimer(event);
				
				Supplier<Color> getCustomBackground = null;
				Supplier<Color> getCustomForeground = null;
				
				if (timer!=null)
				{
					Color bgColor;
					if (columnID==ColumnID.Timer) bgColor = OWCTools.getBgColor(timer.type);
					else                          bgColor = OWCTools.getBgColor(timer.state2);
					getCustomBackground = ()->bgColor;
				}
				
				if (viewStyle==ViewStyle.Bouquet)
				{
					if (event!=null)
					{
						if (StationID.isMarker(event.sref))
							getCustomForeground = ()->COLOR_FG_MARKER;
							
						else if (!EPGTableModel.isInTimeSpan( event, dataTimeStamp_ms ))
							getCustomForeground = ()->COLOR_FG_INACTIVE_EVENT;
					}
				}
				
				if (columnID==ColumnID.CompProgress)
				{
					progressScale.configureAsTableCellRendererComponent(table, value, isSelected, hasFocus, getCustomBackground, getCustomForeground);
					return progressScale;
				}
				
				int horizontalAlignment = SwingConstants.LEFT;
				String valueStr = value==null ? null : value.toString();
				
				if (columnID!=null)
				{
					if (columnID.cfg.toStringR!=null && event!=null)
						valueStr = columnID.cfg.toStringR.apply(event);
					horizontalAlignment = columnID.cfg.horizontalAlignment;
				}
				
				label.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, getCustomForeground);
				label.setHorizontalAlignment(horizontalAlignment);
				
				return label;
			}
		}
		
		private static class ProgressScaleRC extends Tables.GraphicRendererComponent<Double>
		{
			private static final long serialVersionUID = -271394399399751152L;
			
			private Double value = null;
			private boolean isSelected = false;
			private JTable table = null;
			private JList<?> list = null;
			
			ProgressScaleRC() { super(Double.class); }
			
			@Override protected void setValue(Double value, JTable table, JList<?> list, Integer listIndex, boolean isSelected, boolean hasFocus)
			{
				this.value = value;
				this.table = table;
				this.list = list;
				this.isSelected = isSelected;
			}

			@Override protected void paintContent(Graphics g, int x, int y, int width, int height)
			{
				if (value==null)
					return;
				
				int scaleWidth = (int) ((width-2) * Math.min(Math.max(0, value), 1));
				
				g.setColor(isSelected ? getSelectionForeground() : getForeground());
				g.drawRect(x  , y  , width-1   , height-1);
				g.fillRect(x+1, y+1, scaleWidth, height-2);
			}

			private Color getSelectionForeground() {
				if (table!=null) return table.getSelectionForeground();
				if (list !=null) return list .getSelectionForeground();
				return getForeground();
			}
		}
		
		private static class TimersMap
		{
			private final Map<String,Map<Long,Timer>> map;
			
			TimersMap()
			{
				map = new HashMap<>();
			}
			
			void updateData(Timers timers, Vector<EPGevent> events)
			{
				Set<Timers.EventID> eventIDs = new HashSet<>();
				if (events!=null)
					for (EPGevent ev : events)
						{
							if (ev.sref==null || ev.id==null) continue;
							eventIDs.add(new Timers.EventID(ev.sref, ev.id));
						}
				
				map.clear();
				
				if (timers!=null)
				{
					Vector<Timer> timersArr = timers.getTimers(eventIDs);
					for (Timer timer : timersArr)
						map
							.computeIfAbsent(timer.serviceref.toLowerCase(), sref->new HashMap<>())
							.put(timer.eit, timer);
				}
			}
			
			Timer getEventTimer(EPGevent event)
			{
				if (event==null || event.sref==null || event.id==null) return null;
				
				Map<Long, Timer> map2 = map.get(event.sref.toLowerCase());
				if (map2==null) return null;
				
				return map2.get(event.id);
			}
		}
	}
}
