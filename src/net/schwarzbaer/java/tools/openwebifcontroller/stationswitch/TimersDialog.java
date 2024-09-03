package net.schwarzbaer.java.tools.openwebifcontroller.stationswitch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.util.Arrays;
import java.util.Locale;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.LogWindow;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.ExtendedTextArea;
import net.schwarzbaer.java.tools.openwebifcontroller.TimerTools;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerStateGuesser;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimersTableRowSorter;

class TimersDialog extends JDialog {
	private static final long serialVersionUID = -6615053929219118162L;
	private static TimersDialog instance = null;
	
	private final Window window;
	private final LogWindow logWindow;
	private final JTable table;
	private final JScrollPane tableScrollPane;
	private final TimersTableRowSorter tableRowSorter;
	private final TimerStateGuesser timerStateGuesser;
	private TimersTableModel tableModel;
	private ExtendedTextArea textArea;
	private TimersUpdater updateData;

	TimersDialog(Window window, LogWindow logWindow) {
		super(window, "Timers", ModalityType.APPLICATION_MODAL);
		this.window = window;
		this.logWindow = logWindow;
		updateData = null;
		timerStateGuesser = new TimerStateGuesser();
		
		textArea = new ExtendedTextArea(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		JScrollPane textAreaScrollPane = textArea.createScrollPane(500, 500);
		
		tableModel = new TimersTableModel();
		table = new JTable(tableModel);
		table.setRowSorter(tableRowSorter = new TimersTableRowSorter(tableModel));
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		tableModel.setDefaultCellEditorsAndRenderers();
		tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(300, 600));
		
		table.getSelectionModel().addListSelectionListener(e->{
			TimersPanel.showSelectedTimers(table, tableModel, textArea, textAreaScrollPane);
		});
		
		new TimersTableContextMenu().addTo(table, () -> ContextMenu.computeSurrogateMousePos(table, tableScrollPane, tableModel.getColumn(TimersTableModel.ColumnID.name)));
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		splitPane.setLeftComponent(tableScrollPane);
		splitPane.setRightComponent(textAreaScrollPane);
		
		setContentPane(splitPane);
	}
	
	void showDialog(Vector<Timer> data, TimersUpdater updateData) {
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
		timerStateGuesser.clearGuessedStates();
		tableModel = new TimersTableModel(data, timerStateGuesser);
		table.setModel(tableModel);
		tableRowSorter.setModel(tableModel);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
	//	tableModel.setAllDefaultRenderers();
		tableModel.setDefaultCellEditorsAndRenderers();
	}
	
	static void showDialog(Window window, LogWindow logWindow, Vector<Timer> data, TimersUpdater updateData) {
		if (instance == null) {
			instance = new TimersDialog(window, logWindow);
			instance.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
		}
		instance.showDialog(data,updateData);
	}
	
	interface TimersUpdater {
		Vector<Timer> get();
		Vector<Timer> get(String baseURL, ProgressDialog pd);
	}
	
	private class TimersTableContextMenu extends ContextMenu {
		private static final long serialVersionUID = -8581851712142869327L;
		private Timer clickedTimer;
		private Timer[] selectedTimers;
		
		TimersTableContextMenu() {
			clickedTimer = null;
			
			JMenuItem miReloadTimers = add(OpenWebifController.createMenuItem("Reload Timers", GrayCommandIcons.IconGroup.Reload, e->{
				if (updateData==null) return;
				setData(updateData.get());
			}));
			
			add(OpenWebifController.createMenuItem("CleanUp Timers", GrayCommandIcons.IconGroup.Delete, e->{
				OpenWebifController.cleanUpTimers(null, TimersDialog.this, logWindow, (baseURL, pd) -> {
					if (updateData!=null)
						setData(updateData.get(baseURL, pd));
				});
			}));
			
			addSeparator();
			
			JMenu menuClickedTimer;
			add(menuClickedTimer = new JMenu("Clicked Timer"));
			
			menuClickedTimer.add(OpenWebifController.createMenuItem("Toggle", e->{
				if (clickedTimer==null) return;
				OpenWebifController.toggleTimer(null, clickedTimer, TimersDialog.this, logWindow, response -> handleToggleResponse(clickedTimer, response));
			}));
			
			menuClickedTimer.add(OpenWebifController.createMenuItem("Delete", GrayCommandIcons.IconGroup.Delete, e->{
				if (clickedTimer==null) return;
				OpenWebifController.deleteTimer(null, clickedTimer, TimersDialog.this, logWindow, response -> handleDeleteResponse(clickedTimer, response));
			}));
			
			JMenu menuSelectedTimers;
			add(menuSelectedTimers = new JMenu("Selected Timers"));
			
			menuSelectedTimers.add(OpenWebifController.createMenuItem("Activate", GrayCommandIcons.IconGroup.Play, e->{
				Timer[] filteredTimers = filterSelectedTimers(TimerStateGuesser.ExtTimerState.Deactivated);
				if (filteredTimers.length<1) return;
				OpenWebifController.toggleTimer(null, filteredTimers, TimersDialog.this, logWindow, this::handleToggleResponse);
			}));
			
			menuSelectedTimers.add(OpenWebifController.createMenuItem("Deactivate", GrayCommandIcons.IconGroup.Stop, e->{
				Timer[] filteredTimers = filterSelectedTimers(TimerStateGuesser.ExtTimerState.Waiting);
				if (filteredTimers.length<1) return;
				OpenWebifController.toggleTimer(null, filteredTimers, TimersDialog.this, logWindow, this::handleToggleResponse);
			}));
			
			menuSelectedTimers.add(OpenWebifController.createMenuItem("Toggle", e->{
				if (selectedTimers.length<1) return;
				OpenWebifController.toggleTimer(null, selectedTimers, TimersDialog.this, logWindow, this::handleToggleResponse);
			}));
			
			menuSelectedTimers.add(OpenWebifController.createMenuItem("Delete", GrayCommandIcons.IconGroup.Delete, e->{
				if (selectedTimers.length<1) return;
				OpenWebifController.deleteTimer(null, selectedTimers, TimersDialog.this, logWindow, this::handleDeleteResponse);
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
				
				int[] rowsV = table.getSelectedRows();
				int[] rowsM = Tables.convertRowIndexesToModel(table,rowsV);
				selectedTimers = Arrays.stream(rowsM).mapToObj(tableModel::getRow).filter(t->t!=null).toArray(Timer[]::new);
				
				menuClickedTimer  .setEnabled(clickedTimer!=null);
				menuSelectedTimers.setEnabled(selectedTimers.length>0);
				
				miReloadTimers.setEnabled(updateData  !=null);
				String timerLabel = clickedTimer==null ? "" : String.format(" \"%s: %s\"", clickedTimer.servicename, clickedTimer.name);
				menuClickedTimer  .setText("Clicked Timer"+timerLabel);
				menuSelectedTimers.setText("Selected Timers (%d)".formatted(selectedTimers.length));
			});
		}

		private Timer[] filterSelectedTimers(TimerStateGuesser.ExtTimerState timerState)
		{
			return Arrays
				.stream(selectedTimers)
				.filter(t -> timerStateGuesser.getState(t) == timerState)
				.toArray(Timer[]::new);
		}

		private void handleToggleResponse(Timer timer, MessageResponse response)
		{
			timerStateGuesser.updateStateAfterToggle(timer, response);
			tableModel.fireTableCellUpdate(timer, TimersTableModel.ColumnID.state);
		}

		private void handleDeleteResponse(Timer timer, MessageResponse response)
		{
			timerStateGuesser.updateStateAfterDelete(timer, response);
			tableModel.fireTableCellUpdate(timer, TimersTableModel.ColumnID.state);
		}
	}

	private static class TimersTableRenderer implements TableCellRenderer {
		
		private final Tables.LabelRendererComponent rendererComp;
		private final TimersTableModel tableModel;
	
		TimersTableRenderer(TimersTableModel tableModel) {
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
			
			if (value instanceof Timer.Type type)
				bgCol = ()->TimerTools.getBgColor(type);
				
			if (value instanceof TimerStateGuesser.ExtTimerState state)
				bgCol = ()->TimerTools.getBgColor(state);
			
			
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
			state      ("State"       , TimerStateGuesser.ExtTimerState.class, 70, CENTER, (model, timer) -> model.timerStateGuesser.getState(timer)),
			servicename("Station"     , String     .class, 110, null  , timer -> timer.servicename),
			name       ("Name"        , String     .class, 220, null  , timer -> timer.name       ),
			begin_date ("Begin"       , Long       .class, 170, RIGHT , timer -> timer.begin   , val -> OpenWebifController.dateTimeFormatter.getTimeStr( val*1000, Locale.GERMANY,   true,   true, false,  true, false)),
			end        ("End"         , Long       .class,  55, RIGHT , timer -> timer.end     , val -> OpenWebifController.dateTimeFormatter.getTimeStr( val*1000, Locale.GERMANY,  false,  false, false,  true, false)),
			duration   ("Duration"    , Double     .class,  60, RIGHT , timer -> timer.duration, val -> DateTimeFormatter.getDurationStr(val)),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<Timer, ?> getValue;
			private final BiFunction<TimersTableModel, Timer, ?> getValueM;
			private final Integer horizontalAlignment;
			private final Function<Object, String> toString;
			/*
			ColumnID(String name, Class<?> columnClass, int width) {
				this(name, columnClass, width, null, null);
			}
			ColumnID(String name, Class<?> columnClass, int width, Integer horizontalAlignment) {
				this(name, columnClass, width, horizontalAlignment, null);
			}
			*/
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, Function<Timer, T> getValue) {
				this(name, columnClass, width, horizontalAlignment, getValue, null, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, BiFunction<TimersTableModel, Timer, T> getValue) {
				this(name, columnClass, width, horizontalAlignment, null, getValue, null);
			}
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, Function<Timer, T> getValue, Function<T,String> toString) {
				this(name, columnClass, width, horizontalAlignment, getValue, null, toString);
			}
			<T> ColumnID(String name, Class<T> columnClass, int width, Integer horizontalAlignment, Function<Timer, T> getValue, BiFunction<TimersTableModel, Timer, T> getValueM, Function<T,String> toString) {
				this.horizontalAlignment = horizontalAlignment;
				this.getValue = getValue;
				this.getValueM = getValueM;
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
	
		private final TimerStateGuesser timerStateGuesser;

		private TimersTableModel()
		{
			this(new Vector<>(), null);
		}
		public TimersTableModel(Vector<Timer> data, TimerStateGuesser timerStateGuesser)
		{
			super(ColumnID.values(), data);
			this.timerStateGuesser = timerStateGuesser;
		}
		
		void fireTableCellUpdate(Timer row, ColumnID columnID)
		{	
			int rowIndex = getRowIndex(row);
			if (rowIndex>=0)
				fireTableCellUpdate(rowIndex, columnID);
		}
		
		@Override public void setDefaultCellEditorsAndRenderers()
		{
			TimersTableRenderer renderer = new TimersTableRenderer(this);
			setDefaultRenderers(cls -> renderer);
		}
		
		@Override protected Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID, Timer row)
		{
			if (columnID!=null && columnID.getValueM!=null)
				return columnID.getValueM.apply(this, row);
			
			return super.getValueAt(rowIndex, columnIndex, columnID, row);
		}
	}
}