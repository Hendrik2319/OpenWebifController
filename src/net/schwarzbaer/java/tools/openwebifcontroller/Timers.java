package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.Timers.LogEntry;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.system.DateTimeFormatter;

public class Timers extends JSplitPane {
	private static final long serialVersionUID = -2563250955373710618L;
	
	public interface DataUpdateListener {
		void timersHasUpdated(net.schwarzbaer.java.lib.openwebif.Timers timers);
	}

	private final OpenWebifController main;
	private final JTable table;
	private final JTextArea textArea;
	private final Vector<DataUpdateListener> dataUpdateListeners;
	
	public net.schwarzbaer.java.lib.openwebif.Timers timers;
	private TimersTableModel tableModel;

	public Timers(OpenWebifController main) {
		super(JSplitPane.HORIZONTAL_SPLIT, true);
		this.main = main;
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		dataUpdateListeners = new Vector<>();
		
		timers = null;
		tableModel = null;
		
		table = new JTable();
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e->{
			if (tableModel == null) return;
			int rowV = table.getSelectedRow();
			int rowM = table.convertRowIndexToModel(rowV);
			showValues(tableModel.getRow(rowM));
		});
		table.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON3) {
					showColumnWidths();
				}
			}
		});
		
		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		
		JScrollPane tableScrollPane = new JScrollPane(table);
		JScrollPane textScrollPane = new JScrollPane(textArea);
		tableScrollPane.setPreferredSize(new Dimension(1000,500));
		textScrollPane .setPreferredSize(new Dimension(500,500));
		
		setLeftComponent(tableScrollPane);
		setRightComponent(textScrollPane);
	}
	
	public void    addListener(DataUpdateListener listener) { dataUpdateListeners.   add(listener); }
	public void removeListener(DataUpdateListener listener) { dataUpdateListeners.remove(listener); }

	private void showValues(Timer timer) {
		StringBuilder sb = new StringBuilder();
		sb.append("Description:\r\n").append(timer.description).append("\r\n\r\n");
		sb.append("Extended Description:\r\n").append(timer.descriptionextended).append("\r\n\r\n");
		sb.append(String.format("Log Entries: %d%n", timer.logentries.size()));
		for (int i=0; i<timer.logentries.size(); i++) {
			LogEntry entry = timer.logentries.get(i);
			String timeStr = OpenWebifController.dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
			sb.append(String.format("    [%d] %s <Type:%d> \"%s\"%n", i+1, timeStr, entry.type, entry.text));
		}
		textArea.setText(sb.toString());
	}

	private void showColumnWidths() {
		TableColumnModel columnModel = table.getColumnModel();
		if (columnModel==null) return;
		int[] widths = new int[columnModel.getColumnCount()];
		for (int i=0; i<widths.length; i++) {
			TableColumn column = columnModel.getColumn(i);
			if (column==null) widths[i] = -1;
			else widths[i] = column.getWidth();
		}
		System.out.printf("Column Widths: %s%n", Arrays.toString(widths));
	}

	public void readData(String baseURL, ProgressDialog pd) {
		if (baseURL==null) return;
		timers = OpenWebifTools.readTimers(baseURL, taskTitle -> main.setIndeterminateProgressTask(pd, "Timers: "+taskTitle));
		if (timers==null) {
			tableModel = null;
			table.setModel(new DefaultTableModel());
		} else {
			table.setModel(tableModel = new TimersTableModel(timers.timers));
			tableModel.setTable(table);
			tableModel.setColumnWidths(table);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			TimersTableCellRenderer renderer = new TimersTableCellRenderer(tableModel);
			tableModel.forEachColum((columnID, column) -> {
				switch (columnID) {
				case begin: case end: case duration:
				case startprepare: case nextactivation:
					column.setCellRenderer(renderer);
					break;
				default: break;
				}
			});
			tableModel.setAllDefaultRenderers();
		}
		for (DataUpdateListener listener:dataUpdateListeners)
			listener.timersHasUpdated(timers);
	}
	
	private static class TimersTableCellRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = -2154233305983187976L;
		
		private final TimersTableModel tableModel;

		public TimersTableCellRenderer(TimersTableModel tableModel) {
			this.tableModel = tableModel;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
			Component rendererComp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowV, columnV);
			
			int    rowM = table.convertRowIndexToModel   (   rowV);
			int columnM = table.convertColumnIndexToModel(columnV);
			TimersTableModel.ColumnID columnID = tableModel.getColumnID(columnM);
			Timer timer = tableModel.getRow(rowM);
			
			if (columnID!=null && timer!=null)
				switch (columnID) {
				case duration      : setText(DateTimeFormatter.getDurationStr(timer.duration)); break;
				case begin         : setText(OpenWebifController.dateTimeFormatter.getTimeStr(           timer.begin       *1000 , Locale.GERMANY,  true,  true, false, true, false)); break;
				case end           : setText(OpenWebifController.dateTimeFormatter.getTimeStr(           timer.end         *1000 , Locale.GERMANY,  true,  true, false, true, false)); break;
				case startprepare  : setText(OpenWebifController.dateTimeFormatter.getTimeStr(Math.round(timer.startprepare*1000), Locale.GERMANY,  true,  true, false, true, false)); break;
				case nextactivation:
					if (timer.nextactivation!=null)
						setText(OpenWebifController.dateTimeFormatter.getTimeStr(timer.nextactivation.longValue()*1000, Locale.GERMANY,  true,  true, false, true, false));
					break;
				default: break;
				}
			
			return rendererComp;
		}
		
	}

	private static class TimersTableModel extends Tables.SimplifiedTableModel<TimersTableModel.ColumnID> {
		
		// [70, 190, 180, 110, 220, 120, 350, 100, 100, 170, 170, 60, 170, 170, 50, 65, 90, 70, 45, 70, 50, 60, 50, 60, 90, 50, 50, 60, 45, 85, 100, 110, 110, 90]
		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			isAutoTimer        ("isAutoTimer"        , Boolean.class,  70),
			tags               ("tags"               , String .class, 190),
			serviceref         ("serviceref"         , String .class, 180),
			servicename        ("servicename"        , String .class, 110),
			name               ("name"               , String .class, 220),
			dirname            ("dirname"            , String .class, 120),
			filename           ("filename"           , String .class, 350),
			realbegin          ("realbegin"          , String .class, 100),
			realend            ("realend"            , String .class, 100),
			begin              ("begin"              , Long   .class, 170),
			end                ("end"                , Long   .class, 170),
			duration           ("duration"           , Long   .class,  60),
			startprepare       ("startprepare"       , Double .class, 170),
			nextactivation     ("nextactivation"     , Long   .class, 170),
			eit                ("eit"                , Long   .class,  50),
			afterevent         ("afterevent"         , Long   .class,  65),
			allow_duplicate    ("allow_duplicate"    , Boolean.class,  90),
			always_zap         ("always_zap"         , Boolean.class,  70),
			asrefs             ("asrefs"             , String .class,  45),
			autoadjust         ("autoadjust"         , Long   .class,  70),
			backoff            ("backoff"            , Long   .class,  50),
			cancelled          ("cancelled"          , Boolean.class,  60),
			disabled           ("disabled"           , Boolean.class,  50),
			dontsave           ("dontsave"           , Boolean.class,  60),
			firsttryprepare    ("firsttryprepare"    , Long   .class,  90),
			justplay           ("justplay"           , Boolean.class,  50),
			pipzap             ("pipzap"             , Long   .class,  50),
			repeated           ("repeated"           , Long   .class,  60),
			state              ("state"              , Long   .class,  45),
			toggledisabled     ("toggledisabled"     , Boolean.class,  85),
			toggledisabledimg  ("toggledisabledimg"  , String .class, 100),
			vpsplugin_enabled  ("vpsplugin_enabled"  , Boolean.class, 110),
			vpsplugin_overwrite("vpsplugin_overwrite", Boolean.class, 110),
			vpsplugin_time     ("vpsplugin_time"     , Long   .class,  90),
			;
			private final SimplifiedColumnConfig config;
			ColumnID(String name, Class<?> columnClass, int prefWidth) {
				config = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return this.config; }
		}

		private final Vector<Timer> timers;

		public TimersTableModel(Vector<Timer> timers) {
			super(ColumnID.values());
			this.timers = timers;
		}

		public void setAllDefaultRenderers() {
			//setAllDefaultRenderers(columnClass->{
			//	if ()
			//	return new DefaultTableCellRenderer();
			//});
			// TODO Auto-generated method stub
			
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
			case isAutoTimer        : return timer.isAutoTimer==1     ;
			case tags               : return timer.tags               ;
			case afterevent         : return timer.afterevent         ;
			case allow_duplicate    : return timer.allow_duplicate==1 ;
			case always_zap         : return timer.always_zap==1      ;
			case asrefs             : return timer.asrefs             ;
			case autoadjust         : return timer.autoadjust         ;
			case backoff            : return timer.backoff            ;
			case cancelled          : return timer.cancelled          ;
			case disabled           : return timer.disabled==1        ;
			case dontsave           : return timer.dontsave==1        ;
			case firsttryprepare    : return timer.firsttryprepare    ;
			case justplay           : return timer.justplay==1        ;
			case pipzap             : return timer.pipzap             ;
			case repeated           : return timer.repeated           ;
			case state              : return timer.state              ;
			case toggledisabled     : return timer.toggledisabled==1  ;
			case toggledisabledimg  : return timer.toggledisabledimg  ;
			case vpsplugin_enabled  : return timer.vpsplugin_enabled  ;
			case vpsplugin_overwrite: return timer.vpsplugin_overwrite;
			case vpsplugin_time     : return timer.vpsplugin_time     ;
			}
			return null;
		}
	
	}

}
