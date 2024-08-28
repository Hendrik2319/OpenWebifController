package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Dimension;
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

import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;

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
		this.epg = epg;
		this.getBaseURL = getBaseURL;
		this.setStatusOutput = setStatusOutput;
		
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
			showValues(epgTableModel.getRow(rowM));
		});
		
		JScrollPane tableScrollPane = new JScrollPane(epgTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		epgOutput = new JTextArea();
		
		JScrollPane textAreaScrollPane = new JScrollPane(epgOutput);
		textAreaScrollPane.setPreferredSize(new Dimension(600,500));
	}
	
	public void setStation(StationID stationID)
	{
		String baseURL = getBaseURL.get();
		
		Vector<EPGevent> events = epg.readEPGforService(baseURL, stationID, null, null, taskTitle->{
			SwingUtilities.invokeLater(()->{
				setStatusOutput.accept(String.format("EPG for Station \"%s\": %s", stationID.toIDStr(), taskTitle));
			});
		});
		EPGEventGenres.getInstance().scanGenres(events).writeToFile();
		
		epgTableModel.setData(events);
		showValues(null);
	}
	
	private void showValues(EPGevent event)
	{
		// TODO Auto-generated method stub
	}

	static class EPGTableModel extends Tables.SimpleGetValueTableModel<EPGevent, EPGTableModel.ColumnID> {
		
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<EPGevent>, SwingConstants
		{
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
