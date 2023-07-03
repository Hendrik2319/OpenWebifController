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
				main.toggleTimer(clickedTimer);
			}));
			
			JMenuItem miDeleteTimer = add(OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->{
				if (clickedTimer==null) return;
				main.deleteTimer(clickedTimer);
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
/*
	private static class TimersTableModel extends Tables.SimpleGetValueTableModel<Timer, TimersTableModel.ColumnID>
	{
		enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<Timer>
		{
 */
	private static class TimersTableModel extends Tables.SimpleGetValueTableModel<Timer, TimersTableModel.ColumnID>
	{
		
		// [70, 190, 180, 110, 220, 120, 350, 100, 100, 170, 170, 60, 170, 170, 50, 50, 70, 50, 60, 65, 90, 45, 70, 50, 60, 90, 50, 60, 45, 85, 100, 110, 110, 90]
		enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<Timer> {
			isAutoTimer        ("isAutoTimer"        , Long       .class,  70, SwingConstants.CENTER, timer -> timer.isAutoTimer        ),
			type               ("Type"               , Timer.Type .class,  90, null                 , timer -> timer.type               ),
			state_             ("State"              , Timer.State.class,  70, null                 , timer -> timer.state2             ),
			tags               ("tags"               , String     .class, 190, null                 , timer -> timer.tags               ),
			serviceref         ("serviceref"         , String     .class, 180, null                 , timer -> timer.serviceref         ),
			servicename        ("servicename"        , String     .class, 110, null                 , timer -> timer.servicename        ),
			name               ("name"               , String     .class, 220, null                 , timer -> timer.name               ),
			dirname            ("dirname"            , String     .class, 120, null                 , timer -> timer.dirname            ),
			filename           ("filename"           , String     .class, 350, null                 , timer -> timer.filename           ),
			realbegin          ("realbegin"          , String     .class, 100, null                 , timer -> timer.realbegin          ),
			realend            ("realend"            , String     .class, 100, null                 , timer -> timer.realend            ),
			begin              ("begin"              , Long       .class, 170, null                 , timer -> timer.begin              ),
			end                ("end"                , Long       .class, 170, null                 , timer -> timer.end                ),
			duration           ("duration"           , Long       .class,  60, null                 , timer -> timer.duration           ),
			startprepare       ("startprepare"       , Double     .class, 170, null                 , timer -> timer.startprepare       ),
			nextactivation     ("nextactivation"     , Long       .class, 170, null                 , timer -> timer.nextactivation     ),
			eit                ("eit"                , Long       .class,  50, null                 , timer -> timer.eit                ),
			state              ("state"              , Long       .class,  45, null                 , timer -> timer.state              ),
			justplay           ("justplay"           , Long       .class,  50, null                 , timer -> timer.justplay           ),
			always_zap         ("always_zap"         , Long       .class,  70, null                 , timer -> timer.always_zap         ),
			disabled           ("disabled"           , Long       .class,  50, null                 , timer -> timer.disabled           ),
			cancelled          ("cancelled"          , Boolean    .class,  60, null                 , timer -> timer.cancelled          ),
			toggledisabled     ("toggledisabled"     , Long       .class,  85, null                 , timer -> timer.toggledisabled     ),
			toggledisabledimg  ("toggledisabledimg"  , String     .class, 100, SwingConstants.RIGHT , timer -> timer.toggledisabledimg  ),
			                   
			afterevent         ("afterevent"         , Long       .class,  65, null                 , timer -> timer.afterevent         ),
			allow_duplicate    ("allow_duplicate"    , Long       .class,  90, null                 , timer -> timer.allow_duplicate    ),
			asrefs             ("asrefs"             , String     .class,  45, null                 , timer -> timer.asrefs             ),
			autoadjust         ("autoadjust"         , Long       .class,  70, null                 , timer -> timer.autoadjust         ),
			backoff            ("backoff"            , Long       .class,  50, null                 , timer -> timer.backoff            ),
			dontsave           ("dontsave"           , Long       .class,  60, null                 , timer -> timer.dontsave           ),
			firsttryprepare    ("firsttryprepare"    , Long       .class,  90, null                 , timer -> timer.firsttryprepare    ),
			pipzap             ("pipzap"             , Long       .class,  50, null                 , timer -> timer.pipzap             ),
			repeated           ("repeated"           , Long       .class,  60, null                 , timer -> timer.repeated           ),
			vpsplugin_enabled  ("vpsplugin_enabled"  , Boolean    .class, 110, null                 , timer -> timer.vpsplugin_enabled  ),
			vpsplugin_overwrite("vpsplugin_overwrite", Boolean    .class, 110, null                 , timer -> timer.vpsplugin_overwrite),
			vpsplugin_time     ("vpsplugin_time"     , Long       .class,  90, null                 , timer -> timer.vpsplugin_time     ),
			;
			private final SimplifiedColumnConfig config;
			private final int horizontalAlignment;
			private final Function<Timer, ?> getValue;
			<T> ColumnID(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment, Function<Timer, T> getValue) {
				this.horizontalAlignment = horizontalAlignment==null ? getDefaultHorizontalAlignment(columnClass) : horizontalAlignment;
				config = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
				this.getValue = getValue;
			}
			private static int getDefaultHorizontalAlignment(Class<?> columnClass) {
				if (columnClass == null) return SwingConstants.LEFT;
				if (columnClass == String.class) return SwingConstants.LEFT;
				if (Number.class.isAssignableFrom(columnClass)) return SwingConstants.RIGHT;
				return SwingConstants.LEFT;
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return this.config; }
			@Override public Function<Timer, ?> getGetValue() { return getValue; }
		}

		public TimersTableModel() {
			this(new Vector<>());
		}
		public TimersTableModel(Vector<Timer> timers) {
			super(ColumnID.values(), timers);
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
	}

	public static String generateDetailsOutput(Timer timer) {
		ValueListOutput output = new ValueListOutput();
		generateDetailsOutput(output, 0, timer);
		return output.generateOutput();
	}
	
	public static void generateDetailsOutput(ValueListOutput out, int indentLevel, Timer timer)
	{
		out.add(indentLevel, "isAutoTimer"        , "%s (%d)", timer.isAutoTimer==1, timer.isAutoTimer);
		out.add(indentLevel, "tags"               , timer.tags               );
		out.add(indentLevel, "serviceref"         , timer.serviceref         );
		out.add(indentLevel, "servicename"        , timer.servicename        );
		out.add(indentLevel, "name"               , timer.name               );
		out.add(indentLevel, "dirname"            , timer.dirname            );
		out.add(indentLevel, "filename"           , timer.filename           );
		out.add(indentLevel, "realbegin"          , timer.realbegin          );
		out.add(indentLevel, "realend"            , timer.realend            );
		out.add(indentLevel, "begin"              , timer.begin              );
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.begin       *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "end"                , timer.end                );
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.end         *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "duration"           , timer.duration);
		out.add(indentLevel, ""                   , "%s", DateTimeFormatter.getDurationStr(timer.duration));
		out.add(indentLevel, "startprepare"       , timer.startprepare       );
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(Math.round(timer.startprepare*1000), Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "nextactivation"     , timer.nextactivation     );
		if (timer.nextactivation!=null)
			out.add(indentLevel, ""               , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(timer.nextactivation.longValue()*1000, Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "eit"                , timer.eit                );
		out.add(indentLevel, "justplay"           , "%s (%d)", timer.justplay  ==1, timer.justplay  );
		out.add(indentLevel, "always_zap"         , "%s (%d)", timer.always_zap==1, timer.always_zap);
		out.add(indentLevel, "disabled"           , "%s (%d)", timer.disabled  ==1, timer.disabled  );
		out.add(indentLevel, "cancelled"          , timer.cancelled          );
		out.add(indentLevel, "afterevent"         , timer.afterevent         );
		out.add(indentLevel, "allow_duplicate"    , "%s (%d)", timer.allow_duplicate==1, timer.allow_duplicate);
		out.add(indentLevel, "asrefs"             , timer.asrefs             );
		out.add(indentLevel, "autoadjust"         , timer.autoadjust         );
		out.add(indentLevel, "backoff"            , timer.backoff            );
		out.add(indentLevel, "dontsave"           , "%s (%d)", timer.dontsave==1, timer.dontsave);
		out.add(indentLevel, "firsttryprepare"    , timer.firsttryprepare    );
		out.add(indentLevel, "pipzap"             , timer.pipzap             );
		out.add(indentLevel, "repeated"           , timer.repeated           );
		out.add(indentLevel, "state"              , timer.state              );
		out.add(indentLevel, "toggledisabled"     , "%s (%d)", timer.toggledisabled==1, timer.toggledisabled);
		out.add(indentLevel, "toggledisabledimg"  , timer.toggledisabledimg  );
		out.add(indentLevel, "vpsplugin_enabled"  , timer.vpsplugin_enabled  );
		out.add(indentLevel, "vpsplugin_overwrite", timer.vpsplugin_overwrite);
		out.add(indentLevel, "vpsplugin_time"     , timer.vpsplugin_time     );
	}

	public static String generateShortInfo(Timer timer)
	{
		return generateShortInfo("", timer);
	}

	public static String generateShortInfo(String indent, Timer timer)
	{
		StringBuilder sb = new StringBuilder();
		if (timer!=null) {
			sb.append(String.format("%sDescription:%n%s%n%n", indent, timer.description));
			sb.append(String.format("%sExtended Description:%n%s%n%n", indent, timer.descriptionextended));
			sb.append(String.format("%sLog Entries: %d%n", indent, timer.logentries.size()));
			for (int i=0; i<timer.logentries.size(); i++) {
				LogEntry entry = timer.logentries.get(i);
				String timeStr = OpenWebifController.dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
				sb.append(String.format("%s    [%d] %s <Type:%d> \"%s\"%n", indent, i+1, timeStr, entry.type, entry.text));
			}
		}
		return sb.toString();
	}

}
