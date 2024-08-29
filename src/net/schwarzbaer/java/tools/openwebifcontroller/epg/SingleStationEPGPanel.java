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
	
	private final EPG epg;
	private final Supplier<String> getBaseURL;
	private final Consumer<String> setStatusOutput;
	
	private final JTable epgTable;
	private final EPGTableModel epgTableModel;
	private final JTextArea epgOutput;

	public SingleStationEPGPanel(EPG epg, Supplier<String> getBaseURL, Consumer<String> setStatusOutput)
	{
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		this.epg = Objects.requireNonNull(epg);
		this.getBaseURL = Objects.requireNonNull(getBaseURL);
		this.setStatusOutput = Objects.requireNonNull(setStatusOutput);
		
		epgTable = new JTable(epgTableModel = new EPGTableModel());
		epgTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		epgTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		epgTable.setRowSorter( new Tables.SimplifiedRowSorter(epgTableModel) );
		epgTableModel.setTable(epgTable);
		epgTableModel.setColumnWidths(epgTable);
		epgTableModel.setCellRenderers();
		
		epgTable.getSelectionModel().addListSelectionListener(e -> {
			int rowV = epgTable.getSelectedRow();
			int rowM = epgTable.convertRowIndexToModel(rowV);
			showValues(epgTableModel.getRow(rowM), null);
		});
		
		new EPGTableContextMenu();
		
		JScrollPane tableScrollPane = new JScrollPane(epgTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		epgOutput = new JTextArea();
		
		JScrollPane textAreaScrollPane = new JScrollPane(epgOutput);
		textAreaScrollPane.setPreferredSize(new Dimension(600,500));
		
		setLeftComponent(tableScrollPane);
		setRightComponent(textAreaScrollPane);
	}
	
	public void setStation(SubService station)
	{
		Vector<EPGevent> events;
		if (station==null || station.isMarker())
		{
			events = new Vector<>();
		}
		else
		{
			String baseURL = getBaseURL.get();
			
			epgTableModel.setData(new Vector<>());
			showValues(null, String.format("Loading EPG of Station \"%s\" ...", station.name));
			
			// TODO: move to separate thread (Skippable Task)
			events = epg.readEPGforService(baseURL, station.service.stationID, null, null, taskTitle->{
				SwingUtilities.invokeLater(()->{
					setStatusOutput.accept(String.format("EPG of Station \"%s\": %s", station.name, taskTitle));
				});
			});
			EPGEventGenres.getInstance().scanGenres(events).writeToFile();
		}
		epgTableModel.setData(events);
		showValues(null,null);
	}
	
	private void showValues(EPGevent event, String plainText)
	{
		if (event!=null)
		{
			ValueListOutput out = new ValueListOutput();
			OpenWebifController.generateOutput(out, 0, event);
			epgOutput.setText(out.generateOutput());
		}
		else
			epgOutput.setText(plainText);
	}
	
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
