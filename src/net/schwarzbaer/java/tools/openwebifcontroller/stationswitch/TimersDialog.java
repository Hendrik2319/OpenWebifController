package net.schwarzbaer.java.tools.openwebifcontroller.stationswitch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.LogWindow;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.ExtendedTextArea;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimersTableRowSorter;

class TimersDialog extends JDialog {
	private static final long serialVersionUID = -6615053929219118162L;
	private static TimersDialog instance = null;
	
	private final Window window;
	private final LogWindow logWindow;
	private final JTable table;
	private final JScrollPane tableScrollPane;
	private final TimersTableRowSorter tableRowSorter;
	private TimersDialog.TimersTableModel tableModel;
	private ExtendedTextArea textArea;
	private Supplier<Vector<Timer>> updateData;

	TimersDialog(Window window, LogWindow logWindow) {
		super(window, "Timers", ModalityType.APPLICATION_MODAL);
		this.window = window;
		this.logWindow = logWindow;
		updateData = null;
		
		textArea = new ExtendedTextArea(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		JScrollPane textAreaScrollPane = textArea.createScrollPane(500, 500);
		
		tableModel = new TimersTableModel();
		table = new JTable(tableModel);
		table.setRowSorter(tableRowSorter = new TimersTableRowSorter(tableModel));
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		tableModel.setDefaultCellEditorsAndRenderers();
		tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(300, 600));
		
		table.getSelectionModel().addListSelectionListener(e->{
			int rowV = table.getSelectedRow();
			int rowM = table.convertRowIndexToModel(rowV);
			ScrollPosition scrollPos = ScrollPosition.getVertical(textAreaScrollPane);
			textArea.setText(TimersPanel.generateShortInfo(tableModel.getRow(rowM)));
			if (scrollPos!=null) SwingUtilities.invokeLater(()->scrollPos.setVertical(textAreaScrollPane));
		});
		
		new TimersTableContextMenu().addTo(table);
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		splitPane.setLeftComponent(tableScrollPane);
		splitPane.setRightComponent(textAreaScrollPane);
		
		setContentPane(splitPane);
	}
	
	void showDialog(Vector<Timer> data, Supplier<Vector<Timer>> updateData) {
		this.updateData = updateData;
		setData(data);
		
		Dimension size = table.getPreferredSize();
		tableScrollPane.setPreferredSize(new Dimension(size.width+25, 600));
		
		pack();
		setLocationRelativeTo(window);
		setVisible(true);
	}

	private void setData(Vector<Timer> data)
	{
		tableModel = new TimersTableModel(data);
		table.setModel(tableModel);
		tableRowSorter.setModel(tableModel);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
	//	tableModel.setAllDefaultRenderers();
		tableModel.setDefaultCellEditorsAndRenderers();
	}
	
	static void showDialog(Window window, LogWindow logWindow, Vector<Timer> data, Supplier<Vector<Timer>> updateData) {
		if (instance == null) {
			instance = new TimersDialog(window, logWindow);
			instance.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
		}
		instance.showDialog(data,updateData);
	}
	
	private class TimersTableContextMenu extends ContextMenu {
		private static final long serialVersionUID = -8581851712142869327L;
		private Timer clickedTimer;
		
		TimersTableContextMenu() {
			clickedTimer = null;
			
			JMenuItem miReloadTimers = add(OpenWebifController.createMenuItem("Reload Timer Data", GrayCommandIcons.IconGroup.Reload, e->{
				if (updateData==null) return;
				setData(updateData.get());
			}));
			
			addSeparator();
			
			JMenuItem miToggleTimer = add(OpenWebifController.createMenuItem("Toggle Timer", e->{
				if (clickedTimer==null) return;
				OpenWebifController.toggleTimer(clickedTimer, TimersDialog.this, logWindow);
			}));
			
			JMenuItem miDeleteTimer = add(OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->{
				if (clickedTimer==null) return;
				OpenWebifController.deleteTimer(clickedTimer, TimersDialog.this, logWindow);
			}));
			
			addSeparator();
			
			add(OpenWebifController.createMenuItem("Reset Row Order", e->{
				tableRowSorter.resetSortOrder();
				table.repaint();
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = table.rowAtPoint(new Point(x,y));
				int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
				clickedTimer = tableModel.getRow(rowM);
				
				miReloadTimers.setEnabled(updateData  !=null);
				miToggleTimer .setEnabled(clickedTimer!=null);
				miDeleteTimer .setEnabled(clickedTimer!=null);
				if (clickedTimer!=null) {
					miToggleTimer.setText(String.format("Toggle Timer \"%s: %s\"", clickedTimer.servicename, clickedTimer.name));
					miDeleteTimer.setText(String.format("Delete Timer \"%s: %s\"", clickedTimer.servicename, clickedTimer.name));
				} else {
					miToggleTimer.setText("Toggle Timer");
					miDeleteTimer.setText("Delete Timer");
				}
			});
		}
	}

	private static class TimersTableRenderer implements TableCellRenderer {
		
		private final Tables.LabelRendererComponent rendererComp;
		private final TimersDialog.TimersTableModel tableModel;
	
		TimersTableRenderer(TimersDialog.TimersTableModel tableModel) {
			this.tableModel = tableModel;
			rendererComp = new Tables.LabelRendererComponent();
		}
	
		@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
		{
			int columnM = table.convertColumnIndexToModel(columnV);
			TimersTableModel.ColumnID columnID = tableModel.getColumnID(columnM);
			
			Supplier<Color> bgCol = null;
			Supplier<Color> fgCol = null;
			
			String valueStr;
			if (columnID!=null && columnID.toString!=null)
				valueStr = columnID.toString.apply(value);
			else
				valueStr = value==null ? null : value.toString();
			
			if (value instanceof Timer.Type)
				bgCol = ()->TimersPanel.TimersTableCellRenderer.getBgColor((Timer.Type) value);
				
			if (value instanceof Timer.State)
				bgCol = ()->TimersPanel.TimersTableCellRenderer.getBgColor((Timer.State) value);
			
			
			rendererComp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, bgCol, fgCol);
			if (columnID.horizontalAlignment!=null)
				rendererComp.setHorizontalAlignment(columnID.horizontalAlignment);
			else if (columnID!=null && Number.class.isAssignableFrom(columnID.cfg.columnClass))
				rendererComp.setHorizontalAlignment(SwingConstants.RIGHT);
			else
				rendererComp.setHorizontalAlignment(SwingConstants.LEFT);
			
			return rendererComp;
		}
	}

	private static class TimersTableModel extends Tables.SimpleGetValueTableModel<Timer, TimersTableModel.ColumnID>
	{
		enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<Timer>, SwingConstants
		{
			type       ("Type"        , Timer.Type .class,  90, CENTER, timer -> timer.type       ),
			state      ("State"       , Timer.State.class,  70, CENTER, timer -> timer.state2     ),
			servicename("Station"     , String     .class, 110, null  , timer -> timer.servicename),
			name       ("Name"        , String     .class, 220, null  , timer -> timer.name       ),
			_date_     ("Date (Begin)", Long       .class, 115, RIGHT , timer -> timer.begin   , val -> OpenWebifController.dateTimeFormatter.getTimeStr( val*1000, Locale.GERMANY,   true,   true, false, false, false)),
			begin      ("Begin"       , Long       .class,  55, RIGHT , timer -> timer.begin   , val -> OpenWebifController.dateTimeFormatter.getTimeStr( val*1000, Locale.GERMANY,  false,  false, false,  true, false)),
			end        ("End"         , Long       .class,  55, RIGHT , timer -> timer.end     , val -> OpenWebifController.dateTimeFormatter.getTimeStr( val*1000, Locale.GERMANY,  false,  false, false,  true, false)),
			duration   ("Duration"    , Long       .class,  60, RIGHT , timer -> timer.duration, val -> DateTimeFormatter.getDurationStr(val)),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<Timer, ?> getValue;
			private final Integer horizontalAlignment;
			private final Function<Object, String> toString;
			ColumnID(String name, Class<?> columnClass, int width) {
				this(name, columnClass, width, null, null);
			}
			ColumnID(String name, Class<?> columnClass, int width, Integer horizontalAlignment) {
				this(name, columnClass, width, horizontalAlignment, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, Function<Timer, T> getValue)
			{
				this(name, columnClass, width, horizontalAlignment, getValue, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, Function<Timer, T> getValue, Function<T,String> toString)
			{
				this.horizontalAlignment = horizontalAlignment;
				this.getValue = getValue;
				this.toString = toString==null ? null : obj -> {
					if (columnClass.isInstance(obj))
						return toString.apply(columnClass.cast(obj));
					if (obj!=null)
						return obj.toString();
					return null;
				};
				this.cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
			}
			@Override public Function<Timer, ?> getGetValue() { return getValue; }
			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
		}
	
		private TimersTableModel()
		{
			this(new Vector<>());
		}
		public TimersTableModel(Vector<Timer> data)
		{
			super(ColumnID.values(), data);
		}
		
		@Override public void setDefaultCellEditorsAndRenderers()
		{
			TimersDialog.TimersTableRenderer renderer = new TimersTableRenderer(this);
			table.setDefaultRenderer(Timer.Type .class, renderer);
			table.setDefaultRenderer(Timer.State.class, renderer);
			table.setDefaultRenderer(String     .class, renderer);
			table.setDefaultRenderer(Long       .class, renderer);
		}
	}
}