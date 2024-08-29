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

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

public class SingleStationEPGPanel extends JSplitPane
{
	private static final long serialVersionUID = 4349270717898712489L;
	
	private final JTable epgTable;
	private final EPGTableModel epgTableModel;
	private final JTextArea epgOutput;
	private final DataAcquisition dataAcquisition;

	public SingleStationEPGPanel(EPG epg, Supplier<String> getBaseURL, Consumer<String> setStatusOutput)
	{
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		
		epgTable = new JTable(epgTableModel = new EPGTableModel());
		epgTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		epgTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		epgTable.setRowSorter( new Tables.SimplifiedRowSorter(epgTableModel) );
		epgTableModel.setTable(epgTable);
		epgTableModel.setColumnWidths(epgTable);
		epgTableModel.setCellRenderers();
		
		new EPGTableContextMenu();
		
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
	
	@SuppressWarnings("unused")
	private class EPGTableContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = 2824217721324395677L;
		
		private EPGevent clickedEvent;
		private EPGevent selectedEvent;

		EPGTableContextMenu()
		{
			clickedEvent = null;
			selectedEvent = null;
			
			add(OpenWebifController.createMenuItem("Show Column Widths", e->{
				System.out.printf("Column Widths: %s%n", EPGTableModel.getColumnWidthsAsString(epgTable));
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = epgTable.rowAtPoint(new Point(x,y));
				int rowM = rowV<0 ? -1 : epgTable.convertRowIndexToModel(rowV);
				clickedEvent = epgTableModel.getRow(rowM);
				
				int selRowV = epgTable.getSelectedRow();
				int selRowM = selRowV<0 ? -1 : epgTable.convertRowIndexToModel(selRowV);
				selectedEvent = epgTableModel.getRow(selRowM);
				
				// ...
			});
			
			addTo(epgTable);
		}
	}

	@SuppressWarnings("unused")
	static class EPGTableModel extends Tables.SimpleGetValueTableModel<EPGevent, EPGTableModel.ColumnID> {
		
		private static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
			return OpenWebifController.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
		}
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
			ID       ("ID"       , Long  .class,  45, CENTER, ev -> ev.id   ),
			Name     ("Name"     , String.class, 200,   null, ev -> ev.title),
			Begin    ("Begin"    , Long  .class, 170,   null, ev -> ev.begin_timestamp                , ev -> formatDate((ev.begin_timestamp                )*1000,  true,  true, false,  true, false)),
			End      ("End"      , Long  .class,  60,   null, ev -> ev.begin_timestamp+ev.duration_sec, ev -> formatDate((ev.begin_timestamp+ev.duration_sec)*1000, false, false, false,  true, false)),
			Duration ("Duration" , Long  .class,  60,   null, ev -> ev.duration_sec                   , ev -> DateTimeFormatter.getDurationStr(ev.duration_sec)),
			Duratio_ ("Genre"    , Long  .class, 350,   LEFT, ev -> ev.genreid                        , ev -> " [%03d] %s".formatted(ev.genreid, ev.genre)),
			;
			
			final SimplifiedColumnConfig cfg;
			final Function<EPGevent, ?> getValue;
			final BiFunction<EPGTableModel, EPGevent, ?> getValue2;
			final Function<EPGevent, String> getDisplayStr;
			final int horizontalAlignment;
			
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue) {
				this(name, columnClass, prefWidth, horizontalAlignment, getValue, null, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, BiFunction<EPGTableModel,EPGevent,T> getValue2) {
				this(name, columnClass, prefWidth, horizontalAlignment, null, getValue2, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue, Function<EPGevent,String> getDisplayStr) {
				this(name, columnClass, prefWidth, horizontalAlignment, getValue, null, getDisplayStr);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, BiFunction<EPGTableModel,EPGevent,T> getValue2, Function<EPGevent,String> getDisplayStr) {
				this(name, columnClass, prefWidth, horizontalAlignment, null, getValue2, getDisplayStr);
			}
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<EPGevent,T> getValue, BiFunction<EPGTableModel,EPGevent,T> getValue2, Function<EPGevent,String> getDisplayStr) {
				this.horizontalAlignment = Tables.UseFulColumnDefMethods.getHorizontalAlignment(horizontalAlignment, columnClass);
				this.getValue = getValue;
				this.getValue2 = getValue2;
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
