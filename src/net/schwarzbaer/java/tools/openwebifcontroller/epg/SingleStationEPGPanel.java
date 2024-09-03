package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.TimerTools;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerDataUpdateNotifier;

public class SingleStationEPGPanel extends JSplitPane
{
	private static final long serialVersionUID = 4349270717898712489L;
	
	private final JTable epgTable;
	private final EPGTableModel epgTableModel;
	private final JTextArea epgOutput;
	private final JScrollPane epgOutputScrollPane;
	private final DataAcquisition dataAcquisition;

	public SingleStationEPGPanel(EPG epg, TimerDataUpdateNotifier timerNotifier, Supplier<String> getBaseURL, Consumer<String> setStatusOutput, EPGDialog.TimerCommands timerCommands)
	{
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		
		epgTable = new JTable(epgTableModel = new EPGTableModel(timerNotifier));
		epgTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		epgTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		epgTable.setRowSorter( new Tables.SimplifiedRowSorter(epgTableModel) );
		epgTableModel.setTable(epgTable);
		epgTableModel.setColumnWidths(epgTable);
		epgTableModel.setCellRenderers();
		
		new EPGTableContextMenu(getBaseURL, timerCommands);
		
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
			EPGevent event = rowM<0 ? null : epgTableModel.getRow(rowM);
			Timer timer = epgTableModel.getEventTimer(event);
			
			if (event == null)
			{
				showEPGOutput(null);
				return;
			}
			
			ValueListOutput out = new ValueListOutput();
			String output;
			if (timer==null)
			{
				OpenWebifController.generateOutput(out, 0, event);
				output = out.generateOutput();
			}
			else
			{
				out.add(0, "EPG Event");
				OpenWebifController.generateOutput(out, 1, event);
				out.add(0, "Timer");
				TimerTools.generateDetailsOutput(out, 1, timer);
				output = out.generateOutput();
				output += TimerTools.generateShortInfo(ValueListOutput.DEFAULT_INDENT, timer, false);
			}
			showEPGOutput(output);
		});
		
		dataAcquisition = new DataAcquisition(
				epgTableModel, epg, getBaseURL, setStatusOutput, 4*60*60,
				str -> SwingUtilities.invokeLater(()->showEPGOutput(str))
		);
	}

	private void showEPGOutput(String str)
	{
		ScrollPosition scrollPos = ScrollPosition.getVertical(epgOutputScrollPane);
		epgOutput.setText(str);
		if (scrollPos!=null) SwingUtilities.invokeLater(()->scrollPos.setVertical(epgOutputScrollPane));
	}
	
	public void setStation(SubService station)
	{
		dataAcquisition.readEPGforService(station);
	}
	
	static class DataAcquisition
	{
		private final EPG epg;
		private final Supplier<String> getBaseURL;
		private final Consumer<String> setStatusOutput;
		private final Consumer<String> setTextboxOutput;
		private final EPGTableModel epgTableModel;
		private SubService currentStation = null;
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

		public void readEPGforService(SubService station)
		{
			if (station==null || station.isMarker())
				setValues(new Vector<>(), null, "", null);
			
			else
				synchronized (this)
				{
					if (thread!=null)
						currentStation = station;
					else
						(thread = new Thread(()->threadLoop(station))).start();
				}
		}

		private void threadLoop(SubService station)
		{
			String baseURL = getBaseURL.get();
			
			boolean loopActive = true;
			while (loopActive) {
				setValues(new Vector<>(), null, String.format("Loading EPG of Station \"%s\" ...", station.name), station);
				
				Vector<EPGevent> events = readEPGforService(baseURL, station);
				
				synchronized (this)
				{
					if (currentStation==null)
					{
						thread = null;
						loopActive = false;
					}
					else if (currentStation.servicereference.toUpperCase().equals(station.servicereference.toUpperCase()))
					{
						setValues(events, station, "", null);
						thread = null;
						loopActive = false;
					}
					else
						station = currentStation;
				}
			}
		}

		private Vector<EPGevent> readEPGforService(String baseURL, SubService station)
		{
			long beginTime_UnixTS = System.currentTimeMillis()/1000 - leadTime_s;
			Vector<EPGevent> events = epg.readEPGforService(baseURL, station.service.stationID, beginTime_UnixTS, null, taskTitle->{
				setStatusOutput.accept(String.format("EPG of Station \"%s\": %s", station.name, taskTitle));
			});
			EPGEventGenres.getInstance().scanGenres(events).writeToFile();
			return events;
		}

		private synchronized void setValues(Vector<EPGevent> data, SubService dataStation, String text, SubService nextStation)
		{
			epgTableModel.setData(data, dataStation);
			setTextboxOutput.accept(text);
			currentStation = nextStation;
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
		
		private EPGevent clickedEvent;
		private Timer clickedEventTimer;
		
	//	private EPGevent selectedEvent;

		EPGTableContextMenu(Supplier<String> getBaseURL, EPGDialog.TimerCommands timerCommands)
		{
			this.getBaseURL = Objects.requireNonNull(getBaseURL);
			this.timerCommands = Objects.requireNonNull(timerCommands);
			clickedEvent = null;
		//	selectedEvent = null;
			
			add(miAddRecordTimer        = OpenWebifController.createMenuItem("Add Record Timer"         , GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.Record       )));
			add(miAddSwitchTimer        = OpenWebifController.createMenuItem("Add Switch Timer"         , GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.Switch       )));
			add(miAddRecordNSwitchTimer = OpenWebifController.createMenuItem("Add Record'N'Switch Timer", GrayCommandIcons.IconGroup.Add, e->addTimer(Timers.Timer.Type.RecordNSwitch)));
			add(miToggleTimer           = OpenWebifController.createMenuItem("Toggle Timer"                                   , e->toggleTimer()));
			add(miDeleteTimer           = OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->deleteTimer()));
			
			addSeparator();
			
			add(OpenWebifController.createMenuItem("Show Column Widths", e->{
				System.out.printf("Column Widths: %s%n", EPGTableModel.getColumnWidthsAsString(epgTable));
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
				miAddRecordTimer       .setText(!isEventOK              ? "Add Record Timer"          : String.format("Add "+"Record"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddSwitchTimer       .setText(!isEventOK              ? "Add Switch Timer"          : String.format("Add "+"Switch"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddRecordNSwitchTimer.setText(!isEventOK              ? "Add Record'N'Switch Timer" : String.format("Add "+"Record'N'Switch"+" Timer for Event \"%s\"", clickedEvent.title));
				miToggleTimer          .setText(clickedEventTimer==null ? "Toggle Timer"              : String.format("Toggle Timer \"%s\"", clickedEventTimer.name));
				miDeleteTimer          .setText(clickedEventTimer==null ? "Delete Timer"              : String.format("Delete Timer \"%s\"", clickedEventTimer.name));
			});
			
			addTo(epgTable);
		}
		
		private void addTimer(Timers.Timer.Type type) { timerCommands.addTimer   (getBaseURL.get(), clickedEvent.sref, clickedEvent.id.intValue(), type); }
		private void deleteTimer()                    { timerCommands.deleteTimer(getBaseURL.get(), clickedEventTimer); }
		private void toggleTimer()                    { timerCommands.toggleTimer(getBaseURL.get(), clickedEventTimer); }
	}

	static class EPGTableModel extends Tables.SimpleGetValueTableModel<EPGevent, EPGTableModel.ColumnID> {
		
		private static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
			return OpenWebifController.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
		}
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
			// Column Widths: [45, 200, 170, 60, 60, 90, 350, 250, 250, 60, 60, 60, 110, 85, 90, 75, 75, 55, 105, 70, 300, 230] in ModelOrder
			ID           ("ID"               , Long      .class,  45, CENTER,    ev  -> ev.id           ),
			Name         ("Name"             , String    .class, 200,   null,    ev  -> ev.title        ),
			Begin        ("Begin"            , Long      .class, 170,   null,    ev  -> ev.begin_timestamp                , ev -> formatDate((ev.begin_timestamp                )*1000,  true,  true, false,  true, false)),
			End          ("End"              , Long      .class,  60,   null,    ev  -> ev.begin_timestamp+ev.duration_sec, ev -> formatDate((ev.begin_timestamp+ev.duration_sec)*1000, false, false, false,  true, false)),
			Duration     ("Duration"         , Long      .class,  60,   null,    ev  -> ev.duration_sec                   , ev -> DateTimeFormatter.getDurationStr(ev.duration_sec)),
			Timer        ("Timer"            , Timer.Type.class,  90, CENTER, (m,ev) -> getTimerType(m,ev) ),
			Genre        ("Genre"            , Long      .class, 350,   LEFT,    ev  -> ev.genreid                        , ev -> " [%03d] %s".formatted(ev.genreid, ev.genre)),
			ShortDesc    ("Short Description", String    .class, 250,   null,    ev  -> ev.shortdesc    ),
			LongDesc     ("Long Description" , String    .class, 250,   null,    ev  -> ev.longdesc     ),
			Str_Date     ("<date>"           , String    .class,  60,   null,    ev  -> ev.date         ),
			Str_Begin    ("<begin>"          , String    .class,  60,   null,    ev  -> ev.begin        ),
			Str_End      ("<end>"            , String    .class,  60,   null,    ev  -> ev.end          ),
			Now          ("<now_timestamp>"  , Long      .class, 110,   null,    ev  -> ev.now_timestamp, ev -> formatDate((ev.now_timestamp)*1000, false, true, false, true, false)),
			IsUpToDate   ("<isUpToDate>"     , Boolean   .class,  85, CENTER,    ev  -> ev.isUpToDate   ),
			Duration_min ("<duration_min>"   , Long      .class,  90,   null,    ev  -> ev.duration_min ),
			Remaining    ("<remaining>"      , Long      .class,  75,   null,    ev  -> ev.remaining    ),
			Progress     ("<progress>"       , Long      .class,  75,   null,    ev  -> ev.progress     ),
			TLeft        ("<tleft>"          , Long      .class,  55,   null,    ev  -> ev.tleft        ),
			Station      ("<station_name>"   , String    .class, 105,   null,    ev  -> ev.station_name ),
			Provider     ("<provider>"       , String    .class,  70,   null,    ev  -> ev.provider     ),
			Picon        ("<picon>"          , String    .class, 300,   null,    ev  -> ev.picon        ),
			SRef         ("<sref>"           , String    .class, 230,   null,    ev  -> ev.sref         ),
			;
			
			final SimplifiedColumnConfig cfg;
			final Function<EPGevent, ?> getValue;
			final BiFunction<EPGTableModel, EPGevent, ?> getValueM;
			final Function<EPGevent, String> getDisplayStr;
			final int horizontalAlignment;
			
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue) {
				this(name, columnClass, prefWidth, horizontalAlignment, getValue, null, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, BiFunction<EPGTableModel,EPGevent,T> getValueM) {
				this(name, columnClass, prefWidth, horizontalAlignment, null, getValueM, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue, Function<EPGevent,String> getDisplayStr) {
				this(name, columnClass, prefWidth, horizontalAlignment, getValue, null, getDisplayStr);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, BiFunction<EPGTableModel,EPGevent,T> getValueM, Function<EPGevent,String> getDisplayStr) {
				this(name, columnClass, prefWidth, horizontalAlignment, null, getValueM, getDisplayStr);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue, BiFunction<EPGTableModel,EPGevent,T> getValueM, Function<EPGevent,String> getDisplayStr) {
				this.horizontalAlignment = Tables.UseFulColumnDefMethods.getHorizontalAlignment(horizontalAlignment, columnClass);
				this.getValue = getValue;
				this.getValueM = getValueM;
				this.getDisplayStr = getDisplayStr;
				cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
			}
			
			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<EPGevent,?> getGetValue() { return getValue; }
			
			private static Timer.Type getTimerType(EPGTableModel model, EPGevent event)
			{
				Timer timer = model.getEventTimer(event);
				return timer==null ? null : timer.type;
			}
		}

		private Timers timers;
		private SubService station;
		private Map<Long,Timer> stationTimers;
		
		EPGTableModel(TimerDataUpdateNotifier timerNotifier)
		{
			super( ColumnID.values() );
			
			station = null;
			stationTimers = null;
			timers = timerNotifier.getTimers();
			timerNotifier.addListener(timers -> {
				this.timers = timers;
				updateStationTimers();
				fireTableUpdate();
			});
		}
		
		public void setData(Vector<EPGevent> data, SubService station)
		{
			this.station = station;
			setData(data);
			updateStationTimers();
		}

		private void updateStationTimers()
		{
			if (timers==null || station==null || station.servicereference==null)
			{
				stationTimers = null;
				//System.out.printf("EPGTableModel.stationTimers = null%n");
			}
			else
			{
				Vector<Timer> stationTimers = timers.getStationTimers(station.servicereference);
				this.stationTimers = new HashMap<>();
				for (Timer timer : stationTimers)
					this.stationTimers.put(timer.eit, timer);
				//System.out.printf("EPGTableModel.stationTimers = %d timers%n", this.stationTimers.size());
			}
		}
		
		public Timer getEventTimer(EPGevent event)
		{
			if (stationTimers==null) return null;
			if (event==null || event.id==null) return null;
			return stationTimers.get(event.id);
		}

		@Override protected Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID, EPGevent row)
		{
			if (columnID!=null && columnID.getValueM!=null)
				return columnID.getValueM.apply(this, row);
			
			return super.getValueAt(rowIndex, columnIndex, columnID, row);
		}

		void setCellRenderers()
		{
			CustomCellRenderer renderer = new CustomCellRenderer();
			setDefaultRenderers(clazz -> renderer);
		}
		
		private class CustomCellRenderer implements TableCellRenderer
		{
			private Tables.LabelRendererComponent rendComp;
			
			CustomCellRenderer()
			{
				rendComp = new Tables.LabelRendererComponent();
			}

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				int rowM = table.convertRowIndexToModel(rowV);
				EPGevent event = getRow(rowM);
				Timer timer = getEventTimer(event);
				
				String valueStr = value==null ? null : value.toString();
				Supplier<Color> getCustomBackground = null;
				int horizontalAlignment = SwingConstants.LEFT;
				
				if (columnID!=null)
				{
					if (columnID.getDisplayStr!=null && event!=null)
						valueStr = columnID.getDisplayStr.apply(event);
					horizontalAlignment = columnID.horizontalAlignment;
				}
				
				if (timer!=null)
				{
					Color bgColor;
					if (columnID==ColumnID.Timer) bgColor = TimerTools.getBgColor(timer.type);
					else                          bgColor = TimerTools.getBgColor(timer.state2);
					getCustomBackground = ()->bgColor;
				}
				
				rendComp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
				rendComp.setHorizontalAlignment(horizontalAlignment);
				
				return rendComp;
			}
		}
	}
}
