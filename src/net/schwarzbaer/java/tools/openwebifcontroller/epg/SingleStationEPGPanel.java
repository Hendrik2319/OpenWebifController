package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Dimension;
import java.awt.Point;
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

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
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
				epgTableModel, epg, getBaseURL, setStatusOutput,
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
		
		DataAcquisition(EPGTableModel epgTableModel, EPG epg, Supplier<String> getBaseURL, Consumer<String> setStatusOutput, Consumer<String> setTextboxOutput)
		{
			this.epgTableModel = Objects.requireNonNull(epgTableModel);
			this.epg = Objects.requireNonNull(epg);
			this.getBaseURL = Objects.requireNonNull(getBaseURL);
			this.setStatusOutput = Objects.requireNonNull(setStatusOutput);
			this.setTextboxOutput = Objects.requireNonNull(setTextboxOutput);
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
			SubService nextStation = station;
			
			boolean loopActive = true;
			while (loopActive) {
				setValues(new Vector<>(), String.format("Loading EPG of Station \"%s\" ...", nextStation.name), nextStation);
				
				final SubService station_ = nextStation;
				Vector<EPGevent> events = epg.readEPGforService(baseURL, station_.service.stationID, null, null, taskTitle->{
					setStatusOutput.accept(String.format("EPG of Station \"%s\": %s", station_.name, taskTitle));
				});
				EPGEventGenres.getInstance().scanGenres(events).writeToFile();
				
				synchronized (this)
				{
					if (currentStation==null)
					{
						thread = null;
						loopActive = false;
					}
					else if (currentStation.servicereference.toUpperCase().equals(nextStation.servicereference.toUpperCase()))
					{
						setValues(events, "", null);
						thread = null;
						loopActive = false;
					}
					else
						nextStation = currentStation;
				}
			}
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
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
			ID   ("ID"  ,   Long.class,  45, null, ev -> ev.id   ),
			Name ("Name", String.class, 200, null, ev -> ev.title),
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
			// TODO Auto-generated method stub
		}
		
	}
}
