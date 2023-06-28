package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Comparator;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedTableModel;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.Timers.LogEntry;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.ExtendedTextArea;

public class TimersPanel extends JSplitPane {
	private static final long serialVersionUID = -2563250955373710618L;
	
	public interface DataUpdateListener {
		void timersHasUpdated(Timers timers);
	}

	private final OpenWebifController main;
	private final JTable table;
	private final TimersTableRowSorter tableRowSorter;
	private final JScrollPane tableScrollPane;
	private final ExtendedTextArea textArea;
	private final Vector<DataUpdateListener> dataUpdateListeners;
	
	private Timers timers;
	private TimersTableModel tableModel;

	public TimersPanel(OpenWebifController main) {
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		this.main = main;
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		dataUpdateListeners = new Vector<>();
		
		timers = null;
		tableModel = new TimersTableModel();
		
		textArea = new ExtendedTextArea(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		
		table = new JTable(tableModel);
		table.setRowSorter(tableRowSorter = new TimersTableRowSorter(tableModel));
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		tableModel.setAllDefaultRenderers();
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e->{
			int rowV = table.getSelectedRow();
			int rowM = table.convertRowIndexToModel(rowV);
			textArea.setText(generateShortInfo(tableModel.getRow(rowM)));
		});
		tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(1000,500));
		
		new TableContextMenu();
		
		JScrollPane textScrollPane = textArea.createScrollPane(500,500);
		
		setLeftComponent(tableScrollPane);
		setRightComponent(textScrollPane);
	}
	
	private class TableContextMenu extends ContextMenu {
		private static final long serialVersionUID = -1274113566143850520L;
		
		private Timer clickedTimer;

		TableContextMenu() {
			clickedTimer = null;
			
			addTo(table);
			addTo(tableScrollPane);
			
			add(OpenWebifController.createMenuItem("Reload Timer Data", GrayCommandIcons.IconGroup.Reload, e->{
				main.getBaseURLAndRunWithProgressDialog("Reload Timer Data", TimersPanel.this::readData);
			}));
			
			addSeparator();
			
			JMenuItem miToggleTimer = add(OpenWebifController.createMenuItem("Toggle Timer", e->{
				if (clickedTimer==null) return;
				OpenWebifController.toggleTimer(clickedTimer, main.mainWindow);
			}));
			
			JMenuItem miDeleteTimer = add(OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->{
				if (clickedTimer==null) return;
				OpenWebifController.deleteTimer(clickedTimer, main.mainWindow);
			}));
			
			JMenuItem miShowTimerDetails = add(OpenWebifController.createMenuItem("Show Details of Timer", e->{
				if (clickedTimer==null) return;
				String text = generateDetailsOutput(clickedTimer);
				TextAreaDialog.showText(main.mainWindow, "Details of Timer", 800, 800, true, text);
			}));
			
			addSeparator();
			
			add(OpenWebifController.createMenuItem("Show Column Widths", e->{
				System.out.printf("Column Widths: %s%n", TimersTableModel.getColumnWidthsAsString(table));
			}));
			
			add(OpenWebifController.createMenuItem("Reset Row Order", e->{
				tableRowSorter.resetSortOrder();
				table.repaint();
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = comp!=table ? -1 : table.rowAtPoint(new Point(x,y));
				int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
				clickedTimer = tableModel.getRow(rowM);
				
				miToggleTimer     .setEnabled(clickedTimer!=null);
				miDeleteTimer     .setEnabled(clickedTimer!=null);
				miShowTimerDetails.setEnabled(clickedTimer!=null);
				
				String timerLabel = clickedTimer==null ? "" : String.format(" \"%s: %s\"", clickedTimer.servicename, clickedTimer.name);
				miToggleTimer     .setText("Toggle Timer"+timerLabel);
				miDeleteTimer     .setText("Delete Timer"+timerLabel);
				miShowTimerDetails.setText("Show Details of Timer"+timerLabel);
			});
		}
	}
	
	public void    addListener(DataUpdateListener listener) { dataUpdateListeners.   add(listener); }
	public void removeListener(DataUpdateListener listener) { dataUpdateListeners.remove(listener); }

	public Timers getData() {
		return timers;
	}
	public boolean hasData() {
		return timers!=null;
	}

	public void readData(String baseURL, ProgressView pd) {
		if (baseURL==null) return;
		timers = OpenWebifTools.readTimers(baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Timers: "+taskTitle));
		tableModel = timers==null ? new TimersTableModel() : new TimersTableModel(timers.timers);
		table.setModel(tableModel);
		tableRowSorter.setModel(tableModel);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tableModel.setAllDefaultRenderers();
		table.repaint();
		for (DataUpdateListener listener:dataUpdateListeners)
			listener.timersHasUpdated(timers);
	}
	
	public static class TimersTableRowSorter extends Tables.SimplifiedRowSorter
	{

		public TimersTableRowSorter(SimplifiedTableModel<?> tableModel)
		{
			super(tableModel);
		}

		@Override
		protected boolean isNewClass(Class<?> columnClass)
		{
			return
				(columnClass == Timer.Type .class) ||
				(columnClass == Timer.State.class);
		}
		
		@Override
		protected Comparator<Integer> addComparatorForNewClass(Comparator<Integer> comparator, SortOrder sortOrder, Class<?> columnClass, Function<Integer,Object> getValueAtRow)
		{
			if      (columnClass == Timer.Type .class) comparator = addComparator(comparator, sortOrder, row->(Timer.Type )getValueAtRow.apply(row));
			else if (columnClass == Timer.State.class) comparator = addComparator(comparator, sortOrder, row->(Timer.State)getValueAtRow.apply(row));
			return comparator;
		}
		
	}
	
	static class TimersTableCellRenderer implements TableCellRenderer {
		
		private static final Color COLOR_Type_Record        = new Color(0xD9D9FF);
		private static final Color COLOR_Type_RecordNSwitch = new Color(0xD9FFFF);
		private static final Color COLOR_Type_Switch        = new Color(0xFFEDD9);
		private static final Color COLOR_State_Running      = new Color(0xFFFFD9);
		private static final Color COLOR_State_Waiting      = new Color(0xD9FFD9);
		private static final Color COLOR_State_Finished     = new Color(0xFFD9D9);
		private static final Color COLOR_State_Deactivated  = new Color(0xD9D9D9);
		private static final Color COLOR_Unknown            = new Color(0xFF5CFF);
		
		private final TimersTableModel tableModel;
		private final Tables.LabelRendererComponent labRenderer;
		private final Tables.CheckBoxRendererComponent chkbxRenderer;

		private TimersTableCellRenderer(TimersTableModel tableModel) {
			labRenderer = new Tables.LabelRendererComponent();
			chkbxRenderer = new Tables.CheckBoxRendererComponent();
			this.tableModel = tableModel;
		}
		
		static Color getBgColor(Timer.Type type) {
			switch (type) {
			case Record       : return COLOR_Type_Record;
			case RecordNSwitch: return COLOR_Type_RecordNSwitch;
			case Switch       : return COLOR_Type_Switch;
			case Unknown      : return COLOR_Unknown;
			}
			return null;
		}
		
		static Color getBgColor(Timer.State state) {
			switch (state) {
			case Running    : return COLOR_State_Running;
			case Waiting    : return COLOR_State_Waiting;
			case Finished   : return COLOR_State_Finished;
			case Deactivated: return COLOR_State_Deactivated;
			case Unknown    : return COLOR_Unknown;
			}
			return null;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
			//Component rendererComp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowV, columnV);
			
			int    rowM = table.convertRowIndexToModel   (   rowV);
			int columnM = table.convertColumnIndexToModel(columnV);
			TimersTableModel.ColumnID columnID = tableModel.getColumnID(columnM);
			Timer timer = tableModel.getRow(rowM);
			
			Component rendererComp = labRenderer;
			
			if (columnID!=null) {
				Supplier<Color> bgCol = ()->timer==null ? null : getBgColor(timer.state2);
				Supplier<Color> fgCol = null;
				if (columnID.config.columnClass==Boolean.class) {
					if (value instanceof Boolean) {
						rendererComp = chkbxRenderer;
						chkbxRenderer.configureAsTableCellRendererComponent(table, ((Boolean) value).booleanValue(), null, isSelected, hasFocus, fgCol, bgCol);
						chkbxRenderer.setHorizontalAlignment(SwingConstants.CENTER);
					} else
						labRenderer.configureAsTableCellRendererComponent(table, null, "", isSelected, hasFocus, bgCol, fgCol);
					
				} else {
					if (columnID.config.columnClass==Timer.Type.class && value instanceof Timer.Type)
						bgCol = ()->getBgColor((Timer.Type) value);
					
					String valueStr = null;
					switch (columnID) {
					
					case duration    : valueStr = generateText(Long  .class, value, DateTimeFormatter::getDurationStr); break;
					case startprepare: valueStr = generateText(Double.class, value, d->formatSec(Math.round(d*1000))); break;
					
					case begin: case end: case nextactivation:
						valueStr = generateText(Long.class, value, t->formatSec(t*1000));
						break;
						
					default:
						valueStr = value==null ? null : value.toString();
						break;
					}
					labRenderer.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, bgCol, fgCol);
					labRenderer.setHorizontalAlignment(columnID.horizontalAlignment);
				}
			}
			
			return rendererComp;
		}

		private String formatSec(long millis) {
			return OpenWebifController.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  true,  true, false, true, false);
		}
		
		private <V> String generateText(Class<V> class_, Object value, Function<V,String> toString) {
			if (!class_.isInstance(value)) return null;
			return toString.apply(class_.cast(value));
		}
		
	}

	private static class TimersTableModel extends Tables.SimplifiedTableModel<TimersTableModel.ColumnID> {
		
		// [70, 190, 180, 110, 220, 120, 350, 100, 100, 170, 170, 60, 170, 170, 50, 50, 70, 50, 60, 65, 90, 45, 70, 50, 60, 90, 50, 60, 45, 85, 100, 110, 110, 90]
		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			isAutoTimer        ("isAutoTimer"        , Long       .class,  70, SwingConstants.CENTER),
			type               ("Type"               , Timer.Type .class,  90),
			state_             ("State"              , Timer.State.class,  70),
			tags               ("tags"               , String     .class, 190),
			serviceref         ("serviceref"         , String     .class, 180),
			servicename        ("servicename"        , String     .class, 110),
			name               ("name"               , String     .class, 220),
			dirname            ("dirname"            , String     .class, 120),
			filename           ("filename"           , String     .class, 350),
			realbegin          ("realbegin"          , String     .class, 100),
			realend            ("realend"            , String     .class, 100),
			begin              ("begin"              , Long       .class, 170),
			end                ("end"                , Long       .class, 170),
			duration           ("duration"           , Long       .class,  60),
			startprepare       ("startprepare"       , Double     .class, 170),
			nextactivation     ("nextactivation"     , Long       .class, 170),
			eit                ("eit"                , Long       .class,  50),
			state              ("state"              , Long       .class,  45),
			justplay           ("justplay"           , Long       .class,  50),
			always_zap         ("always_zap"         , Long       .class,  70),
			disabled           ("disabled"           , Long       .class,  50),
			cancelled          ("cancelled"          , Boolean    .class,  60),
			toggledisabled     ("toggledisabled"     , Long       .class,  85),
			toggledisabledimg  ("toggledisabledimg"  , String     .class, 100, SwingConstants.RIGHT),
			
			afterevent         ("afterevent"         , Long       .class,  65),
			allow_duplicate    ("allow_duplicate"    , Long       .class,  90),
			asrefs             ("asrefs"             , String     .class,  45),
			autoadjust         ("autoadjust"         , Long       .class,  70),
			backoff            ("backoff"            , Long       .class,  50),
			dontsave           ("dontsave"           , Long       .class,  60),
			firsttryprepare    ("firsttryprepare"    , Long       .class,  90),
			pipzap             ("pipzap"             , Long       .class,  50),
			repeated           ("repeated"           , Long       .class,  60),
			vpsplugin_enabled  ("vpsplugin_enabled"  , Boolean    .class, 110),
			vpsplugin_overwrite("vpsplugin_overwrite", Boolean    .class, 110),
			vpsplugin_time     ("vpsplugin_time"     , Long       .class,  90),
			;
			private final SimplifiedColumnConfig config;
			private final int horizontalAlignment;
			ColumnID(String name, Class<?> columnClass, int prefWidth) {
				this(name, columnClass, prefWidth, getDefaultHorizontalAlignment(columnClass));
			}
			ColumnID(String name, Class<?> columnClass, int prefWidth, int horizontalAlignment) {
				this.horizontalAlignment = horizontalAlignment;
				config = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
			}
			private static int getDefaultHorizontalAlignment(Class<?> columnClass) {
				if (columnClass == null) return SwingConstants.LEFT;
				if (columnClass == String.class) return SwingConstants.LEFT;
				if (Number.class.isAssignableFrom(columnClass)) return SwingConstants.RIGHT;
				return SwingConstants.LEFT;
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return this.config; }
		}

		private final Vector<Timer> timers;

		public TimersTableModel() {
			this(new Vector<>());
		}
		public TimersTableModel(Vector<Timer> timers) {
			super(ColumnID.values());
			this.timers = timers;
		}

		public void setAllDefaultRenderers() {
			TimersTableCellRenderer renderer = new TimersTableCellRenderer(this);
			table.setDefaultRenderer(Timer.Type .class, renderer);
			table.setDefaultRenderer(Timer.State.class, renderer);
			table.setDefaultRenderer(Boolean    .class, renderer);
			table.setDefaultRenderer(String     .class, renderer);
			table.setDefaultRenderer(Double     .class, renderer);
			table.setDefaultRenderer(Long       .class, renderer);
		}

		@Override public int getRowCount() {
			return timers.size();
		}

		public Timer getRow(int rowIndex) {
			if (rowIndex<0 || rowIndex>=timers.size()) return null;
			return timers.get(rowIndex);
		}

		@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			Timer timer = getRow(rowIndex);
			if (timer==null) return null;
			
			switch (columnID) {
			case name               : return timer.name               ;
			case dirname            : return timer.dirname            ;
			case filename           : return timer.filename           ;
			case servicename        : return timer.servicename        ;
			case serviceref         : return timer.serviceref         ;
			case realbegin          : return timer.realbegin          ;
			case realend            : return timer.realend            ;
			case begin              : return timer.begin              ;
			case end                : return timer.end                ;
			case duration           : return timer.duration           ;
			case startprepare       : return timer.startprepare       ;
			case nextactivation     : return timer.nextactivation     ;
			case eit                : return timer.eit                ;
			case isAutoTimer        : return timer.isAutoTimer        ;
			case tags               : return timer.tags               ;
			case afterevent         : return timer.afterevent         ;
			case allow_duplicate    : return timer.allow_duplicate    ;
			case always_zap         : return timer.always_zap         ;
			case asrefs             : return timer.asrefs             ;
			case autoadjust         : return timer.autoadjust         ;
			case backoff            : return timer.backoff            ;
			case cancelled          : return timer.cancelled          ;
			case disabled           : return timer.disabled           ;
			case dontsave           : return timer.dontsave           ;
			case firsttryprepare    : return timer.firsttryprepare    ;
			case justplay           : return timer.justplay           ;
			case pipzap             : return timer.pipzap             ;
			case repeated           : return timer.repeated           ;
			case state              : return timer.state              ;
			case toggledisabled     : return timer.toggledisabled     ;
			case toggledisabledimg  : return timer.toggledisabledimg  ;
			case vpsplugin_enabled  : return timer.vpsplugin_enabled  ;
			case vpsplugin_overwrite: return timer.vpsplugin_overwrite;
			case vpsplugin_time     : return timer.vpsplugin_time     ;
			case type               : return timer.type;
			case state_             : return timer.state2;
			}
			return null;
		}
	
	}

	private static String generateDetailsOutput(Timer timer) {
		ValueListOutput output = new ValueListOutput();
		output.add(0, "isAutoTimer"        , "%s (%d)", timer.isAutoTimer==1, timer.isAutoTimer);
		output.add(0, "tags"               , timer.tags               );
		output.add(0, "serviceref"         , timer.serviceref         );
		output.add(0, "servicename"        , timer.servicename        );
		output.add(0, "name"               , timer.name               );
		output.add(0, "dirname"            , timer.dirname            );
		output.add(0, "filename"           , timer.filename           );
		output.add(0, "realbegin"          , timer.realbegin          );
		output.add(0, "realend"            , timer.realend            );
		output.add(0, "begin"              , timer.begin              );
		output.add(0, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.begin       *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		output.add(0, "end"                , timer.end                );
		output.add(0, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.end         *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		output.add(0, "duration"           , timer.duration);
		output.add(0, ""                   , "%s", DateTimeFormatter.getDurationStr(timer.duration));
		output.add(0, "startprepare"       , timer.startprepare       );
		output.add(0, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(Math.round(timer.startprepare*1000), Locale.GERMANY,  true,  true, false, true, false) );
		output.add(0, "nextactivation"     , timer.nextactivation     );
		if (timer.nextactivation!=null)
			output.add(0, ""               , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(timer.nextactivation.longValue()*1000, Locale.GERMANY,  true,  true, false, true, false) );
		output.add(0, "eit"                , timer.eit                );
		output.add(0, "justplay"           , "%s (%d)", timer.justplay  ==1, timer.justplay  );
		output.add(0, "always_zap"         , "%s (%d)", timer.always_zap==1, timer.always_zap);
		output.add(0, "disabled"           , "%s (%d)", timer.disabled  ==1, timer.disabled  );
		output.add(0, "cancelled"          , timer.cancelled          );
		output.add(0, "afterevent"         , timer.afterevent         );
		output.add(0, "allow_duplicate"    , "%s (%d)", timer.allow_duplicate==1, timer.allow_duplicate);
		output.add(0, "asrefs"             , timer.asrefs             );
		output.add(0, "autoadjust"         , timer.autoadjust         );
		output.add(0, "backoff"            , timer.backoff            );
		output.add(0, "dontsave"           , "%s (%d)", timer.dontsave==1, timer.dontsave);
		output.add(0, "firsttryprepare"    , timer.firsttryprepare    );
		output.add(0, "pipzap"             , timer.pipzap             );
		output.add(0, "repeated"           , timer.repeated           );
		output.add(0, "state"              , timer.state              );
		output.add(0, "toggledisabled"     , "%s (%d)", timer.toggledisabled==1, timer.toggledisabled);
		output.add(0, "toggledisabledimg"  , timer.toggledisabledimg  );
		output.add(0, "vpsplugin_enabled"  , timer.vpsplugin_enabled  );
		output.add(0, "vpsplugin_overwrite", timer.vpsplugin_overwrite);
		output.add(0, "vpsplugin_time"     , timer.vpsplugin_time     );
		return output.generateOutput();
	}

	public static String generateShortInfo(Timer timer)
	{
		StringBuilder sb = new StringBuilder();
		if (timer!=null) {
			sb.append("Description:\r\n").append(timer.description).append("\r\n\r\n");
			sb.append("Extended Description:\r\n").append(timer.descriptionextended).append("\r\n\r\n");
			sb.append(String.format("Log Entries: %d%n", timer.logentries.size()));
			for (int i=0; i<timer.logentries.size(); i++) {
				LogEntry entry = timer.logentries.get(i);
				String timeStr = OpenWebifController.dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
				sb.append(String.format("    [%d] %s <Type:%d> \"%s\"%n", i+1, timeStr, entry.type, entry.text));
			}
		}
		String string = sb.toString();
		return string;
	}

}
