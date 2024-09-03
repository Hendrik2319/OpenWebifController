package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Locale;
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
import javax.swing.table.DefaultTableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

public class SingleStationEPGPanel extends JSplitPane
{
	private static final long serialVersionUID = 4349270717898712489L;
	
	private final JTable epgTable;
	private final EPGTableModel epgTableModel;
	private final JTextArea epgOutput;
	private final DataAcquisition dataAcquisition;

	public SingleStationEPGPanel(EPG epg, Supplier<String> getBaseURL, Consumer<String> setStatusOutput, EPGDialog.TimerCommands timerCommands)
	{
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		
		epgTable = new JTable(epgTableModel = new EPGTableModel());
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
		
		JScrollPane textAreaScrollPane = new JScrollPane(epgOutput);
		textAreaScrollPane.setPreferredSize(new Dimension(600,500));
		
		setLeftComponent(tableScrollPane);
		setRightComponent(textAreaScrollPane);
		
		epgTable.getSelectionModel().addListSelectionListener(e -> {
			int rowV = epgTable.getSelectedRow();
			int rowM = rowV<0 ? -1 : epgTable.convertRowIndexToModel(rowV);
			EPGevent event = rowM<0 ? null : epgTableModel.getRow(rowM);
			
			if (event == null)
			{
				epgOutput.setText(null);
				return;
			}
			
			ValueListOutput out = new ValueListOutput();
			OpenWebifController.generateOutput(out, 0, event);
			epgOutput.setText(out.generateOutput());
		});
		
		dataAcquisition = new DataAcquisition(
				epgTableModel, epg, getBaseURL, setStatusOutput, 4*60*60,
				str -> SwingUtilities.invokeLater(()->epgOutput.setText(str))
		);
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
				setValues(new Vector<>(), "", null);
			
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
				setValues(new Vector<>(), String.format("Loading EPG of Station \"%s\" ...", station.name), station);
				
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
						setValues(events, "", null);
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

		private synchronized void setValues(Vector<EPGevent> data, String text, SubService station)
		{
			epgTableModel.setData(data);
			setTextboxOutput.accept(text);
			currentStation = station;
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
	//	private final JMenuItem miToggleTimer;
	//	private final JMenuItem miDeleteTimer;
		
		private EPGevent clickedEvent;
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
		//	add(miToggleTimer           = OpenWebifController.createMenuItem("Toggle Timer"                                   , e->toggleTimer()));
		//	add(miDeleteTimer           = OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->deleteTimer()));
			
			add(OpenWebifController.createMenuItem("Show Column Widths", e->{
				System.out.printf("Column Widths: %s%n", EPGTableModel.getColumnWidthsAsString(epgTable));
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = epgTable.rowAtPoint(new Point(x,y));
				int rowM = rowV<0 ? -1 : epgTable.convertRowIndexToModel(rowV);
				clickedEvent = epgTableModel.getRow(rowM);
				
			//	int selRowV = epgTable.getSelectedRow();
			//	int selRowM = selRowV<0 ? -1 : epgTable.convertRowIndexToModel(selRowV);
			//	selectedEvent = epgTableModel.getRow(selRowM);
				
				boolean isEventOK =
						clickedEvent!=null &&
						clickedEvent.sref!=null &&
						clickedEvent.id!=null;
				
				miAddRecordTimer       .setEnabled(isEventOK /*&& timer==null*/);
				miAddSwitchTimer       .setEnabled(isEventOK /*&& timer==null*/);
				miAddRecordNSwitchTimer.setEnabled(isEventOK /*&& timer==null*/);
			//	miToggleTimer          .setEnabled(isEventOK /*&& timer!=null*/);
			//	miDeleteTimer          .setEnabled(isEventOK /*&& timer!=null*/);
				miAddRecordTimer       .setText(!isEventOK  ? "Add Record Timer"          : String.format("Add "+"Record"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddSwitchTimer       .setText(!isEventOK  ? "Add Switch Timer"          : String.format("Add "+"Switch"         +" Timer for Event \"%s\"", clickedEvent.title));
				miAddRecordNSwitchTimer.setText(!isEventOK  ? "Add Record'N'Switch Timer" : String.format("Add "+"Record'N'Switch"+" Timer for Event \"%s\"", clickedEvent.title));
			//	miToggleTimer          .setText(timer==null ? "Toggle Timer"              : String.format("Toggle Timer \"%s\"", timer.name));
			//	miDeleteTimer          .setText(timer==null ? "Delete Timer"              : String.format("Delete Timer \"%s\"", timer.name));
			});
			
			addTo(epgTable);
		}
		
		private void addTimer(Timers.Timer.Type type) { timerCommands.addTimer   (getBaseURL.get(), clickedEvent.sref, clickedEvent.id.intValue(), type); }
	//	private void deleteTimer()                    { externCommands.deleteTimer(baseURL, timer.timer); }
	//	private void toggleTimer()                    { externCommands.toggleTimer(baseURL, timer.timer); }
	}

	static class EPGTableModel extends Tables.SimpleGetValueTableModel<EPGevent, EPGTableModel.ColumnID> {
		
		private static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
			return OpenWebifController.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
		}
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
			// Column Widths: [45, 200, 170, 60, 60, 350, 250, 250, 60, 53, 48, 110, 85, 89, 74, 72, 53, 105, 71, 300, 230] in ModelOrder
			ID           ("ID"               , Long   .class,  45, CENTER, ev -> ev.id           ),
			Name         ("Name"             , String .class, 200,   null, ev -> ev.title        ),
			Begin        ("Begin"            , Long   .class, 170,   null, ev -> ev.begin_timestamp                , ev -> formatDate((ev.begin_timestamp                )*1000,  true,  true, false,  true, false)),
			End          ("End"              , Long   .class,  60,   null, ev -> ev.begin_timestamp+ev.duration_sec, ev -> formatDate((ev.begin_timestamp+ev.duration_sec)*1000, false, false, false,  true, false)),
			Duration     ("Duration"         , Long   .class,  60,   null, ev -> ev.duration_sec                   , ev -> DateTimeFormatter.getDurationStr(ev.duration_sec)),
			Genre        ("Genre"            , Long   .class, 350,   LEFT, ev -> ev.genreid                        , ev -> " [%03d] %s".formatted(ev.genreid, ev.genre)),
			ShortDesc    ("Short Description", String .class, 250,   null, ev -> ev.shortdesc    ),
			LongDesc     ("Long Description" , String .class, 250,   null, ev -> ev.longdesc     ),
			Str_Date     ("<date>"           , String .class,  60,   null, ev -> ev.date         ),
			Str_Begin    ("<begin>"          , String .class,  60,   null, ev -> ev.begin        ),
			Str_End      ("<end>"            , String .class,  60,   null, ev -> ev.end          ),
			Now          ("<now_timestamp>"  , Long   .class, 110,   null, ev -> ev.now_timestamp, ev -> formatDate((ev.now_timestamp)*1000, false, true, false, true, false)),
			IsUpToDate   ("<isUpToDate>"     , Boolean.class,  85,   null, ev -> ev.isUpToDate   ),
			Duration_min ("<duration_min>"   , Long   .class,  90,   null, ev -> ev.duration_min ),
			Remaining    ("<remaining>"      , Long   .class,  75,   null, ev -> ev.remaining    ),
			Progress     ("<progress>"       , Long   .class,  75,   null, ev -> ev.progress     ),
			TLeft        ("<tleft>"          , Long   .class,  55,   null, ev -> ev.tleft        ),
			Station      ("<station_name>"   , String .class, 105,   null, ev -> ev.station_name ),
			Provider     ("<provider>"       , String .class,  70,   null, ev -> ev.provider     ),
			Picon        ("<picon>"          , String .class, 300,   null, ev -> ev.picon        ),
			SRef         ("<sref>"           , String .class, 230,   null, ev -> ev.sref         ),
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
		}
		
		EPGTableModel()
		{
			super( ColumnID.values() );
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
		
		private class CustomCellRenderer extends DefaultTableCellRenderer
		{
			private static final long serialVersionUID = 8998686057581622646L;

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowV, columnV);
				
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				int rowM = table.convertRowIndexToModel(rowV);
				EPGevent event = getRow(rowM);
				
				if (columnID!=null)
				{
					if (columnID.getDisplayStr!=null && event!=null)
						setText(columnID.getDisplayStr.apply(event));
					setHorizontalAlignment(columnID.horizontalAlignment);
				}
				
				return comp;
			}
		}
	}
}
