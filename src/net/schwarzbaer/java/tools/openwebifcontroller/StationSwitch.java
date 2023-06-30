package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.ExtendedTextArea;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimersTableRowSorter;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.AbstractControlPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.PowerControl;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.VolumeControl;

class StationSwitch {

	static void start(String baseURL, boolean asSubWindow) {
		if (!asSubWindow)
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		new StationSwitch(asSubWindow).initialize(baseURL);
	}

	private final StandardMainWindow mainWindow;
	private final JPanel stationsPanel;
	private final PowerControl powerControl;
	private final VolumeControl volumeControl;
	private final JButton btnBouquetData;
	private final JButton btnAddStation;
	private final JLabel labBouquet;
	private final JComboBox<Bouquet> cmbbxBouquet;
	private final JLabel labStation;
	private final JComboBox<Bouquet.SubService> cmbbxStation;
	private String baseURL;
	private BouquetData bouquetData;
	private Bouquet.SubService selectedStation;
	private final JButton btnReLoadTimerData;
	private final JButton btnShowTimers;
	private Timers timerData;
	//private JLabel labActiveTimers;
	private final JTextArea txtActiveTimers;
	private final LogWindow logWindow;
	
	StationSwitch(boolean asSubWindow) {
		baseURL = null;
		bouquetData = null;
		selectedStation = null;
		timerData = null;
		
		mainWindow = OpenWebifController.createMainWindow("Station Switch",asSubWindow);
		logWindow = new LogWindow(mainWindow, "Response Log");
		
		stationsPanel = new JPanel(new GridBagLayout());
		
		AbstractControlPanel.ExternCommands controlPanelCommands = new AbstractControlPanel.ExternCommands() {
			@Override public void showMessageResponse(MessageResponse response, String title, String... stringsToHighlight) {
				logWindow.showMessageResponse(response, title, stringsToHighlight);
			}
			@Override public String getBaseURL() {
				return baseURL!=null ? baseURL : (baseURL = OpenWebifController.getBaseURL(true, mainWindow));
			}
		};
		
		powerControl  = new PowerControl (controlPanelCommands,false,true,true);
		volumeControl = new VolumeControl(controlPanelCommands,false,true,true);
		powerControl.addUpdateTask(baseURL -> volumeControl.initialize(baseURL,null));
		
		btnBouquetData = OpenWebifController.createButton(GrayCommandIcons.Download.getIcon(), GrayCommandIcons.Download_Dis.getIcon(), true, e->{
			reloadBouquetData();
			mainWindow.pack();
		});
		btnBouquetData.setToolTipText("Load Bouquet Data");
		
		btnAddStation = OpenWebifController.createButton("Add", false, e->{
			Bouquet.SubService station = selectedStation;
			if (station==null) return;
			
			JLabel label = new JLabel(station.name);
			
			JButton btnZap = OpenWebifController.createButton("Switch", true, e1->{
				if (baseURL==null) return;
				OpenWebifController.zapToStation(station.service.stationID, baseURL, logWindow);
			});
			
			JButton btnStream = OpenWebifController.createButton("Stream", true, e1->{
				if (baseURL==null) return;
				OpenWebifController.streamStation(station.service.stationID, baseURL);
			});
			btnStream.setEnabled(OpenWebifController.canStreamStation());
			
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.gridwidth = 1;
			c.weightx = 0;
			stationsPanel.add(label,c);
			c.weightx = 1;
			stationsPanel.add(btnZap,c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			stationsPanel.add(btnStream,c);
			
			mainWindow.pack();
			
			if (baseURL!=null)
				new Thread(()->{
					BufferedImage picon = OpenWebifTools.getPicon(baseURL, station.service.stationID);
					Icon icon = picon==null ? null : BouquetsNStations.getScaleIcon(picon, 20, Color.BLACK);
					if (icon!=null)
						SwingUtilities.invokeLater(()->{
							label.setIcon(icon);
							mainWindow.pack();
						});
				}).start();
		});
		
		labStation = new JLabel("Station: ");
		cmbbxStation = OpenWebifController.createComboBox((Bouquet.SubService subService)->{
			selectedStation = subService;
			btnAddStation.setEnabled(selectedStation!=null && !selectedStation.isMarker());
		});
		cmbbxStation.setRenderer(new StationListRenderer());
		labStation.setEnabled(false);
		cmbbxStation.setEnabled(false);
		
		labBouquet = new JLabel("Bouquet: ");
		cmbbxBouquet = OpenWebifController.createComboBox((Bouquet bouquet)->{
			cmbbxStation.setModel  (bouquet==null ? new DefaultComboBoxModel<>() : new DefaultComboBoxModel<>(bouquet.subservices));
			labStation  .setEnabled(bouquet!=null && !bouquet.subservices.isEmpty());
			cmbbxStation.setEnabled(bouquet!=null && !bouquet.subservices.isEmpty());
			cmbbxStation.setSelectedItem(null);
			mainWindow.pack();
		});
		labBouquet.setEnabled(false);
		cmbbxBouquet.setEnabled(false);
		
		
		
		btnReLoadTimerData = OpenWebifController.createButton(GrayCommandIcons.Download.getIcon(), GrayCommandIcons.Download_Dis.getIcon(), true, e -> reloadTimerData());
		btnReLoadTimerData.setToolTipText("Load Timer Data");
		
		txtActiveTimers = new JTextArea();
		JScrollPane scrlpActiveTimers = new JScrollPane(txtActiveTimers);
		//labActiveTimers = new JLabel("Currently no Active Timers");
		txtActiveTimers.setEditable(false);
		txtActiveTimers  .setToolTipText("Active Timers");
		scrlpActiveTimers.setToolTipText("Active Timers");
		scrlpActiveTimers.setPreferredSize(new Dimension(300,70));
		btnShowTimers = OpenWebifController.createButton("Timer", false, e-> {
			if (timerData==null) {
				//System.err.printf("timerData == null%n");
				return;
			}
			TimersDialog.showDialog(mainWindow, logWindow, timerData.timers, ()->{
				reloadTimerData();
				return timerData.timers;
			});
		});
		txtActiveTimers.setEnabled(false);
		btnShowTimers.setEnabled(false);
		
		
		GridBagConstraints c;
		JPanel controllerPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		controllerPanel.add(powerControl ,c);
		controllerPanel.add(volumeControl,c);
		c.weightx = 0;
		controllerPanel.add(OpenWebifController.createButton("Log", true, e->logWindow.showDialog(LogWindow.Position.RIGHT_OF_PARENT)),c);
		
		
		JPanel timerPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		c.gridwidth = 1;
		c.weightx = 0;
		timerPanel.add(btnReLoadTimerData,c);
		
		c.insets = new Insets(1, 1, 1, 1);
		c.weightx = 1;
		timerPanel.add(scrlpActiveTimers,c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 0;
		timerPanel.add(btnShowTimers,c);
		
		
		JPanel bouquetPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		//c.insets = new Insets(1, 1, 1, 1);
		
		c.gridwidth = 1;
		c.weightx = 0;
		c.gridheight = 2;
		bouquetPanel.add(btnBouquetData,c);
		c.gridheight = 1;
		c.insets = new Insets(1, 1, 1, 1);
		
		bouquetPanel.add(labBouquet,c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1;
		bouquetPanel.add(cmbbxBouquet,c);
		c.gridwidth = 1;
		c.weightx = 0;
		
		bouquetPanel.add(labStation,c);
		c.weightx = 1;
		bouquetPanel.add(cmbbxStation,c);
		c.weightx = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		bouquetPanel.add(btnAddStation,c);
		
		
		JPanel configPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new Insets(2, 0, 2, 0);
		
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1;
		configPanel.add(controllerPanel,c);
		configPanel.add(timerPanel,c);
		configPanel.add(bouquetPanel,c);
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		contentPane.add(configPanel,BorderLayout.NORTH);
		contentPane.add(stationsPanel,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane);
	}

	private void reloadBouquetData()
	{
		String title = (bouquetData == null ? "Load" : "Reload") +" Bouquet Data";
		OpenWebifController.runWithProgressDialog(mainWindow, title, this::initializeBouquetData);
	}

	private void reloadTimerData()
	{
		String title = (timerData == null ? "Load" : "Reload") +" Timer Data";
		OpenWebifController.runWithProgressDialog(mainWindow, title, this::initializeTimerData);
	}
	
	private void initialize(String baseURL) {
		if (baseURL==null) baseURL = OpenWebifController.getBaseURL(true, mainWindow);
		this.baseURL = baseURL;
		
		OpenWebifController.runWithProgressDialog(mainWindow, "Initialize", pd->{
			if (this.baseURL!=null) {
				powerControl .initialize(this.baseURL,pd);
				volumeControl.initialize(this.baseURL,pd);
			}
			//initializeBouquetData(pd);
		});
		
		updateActiveTimersText();
		mainWindow.pack();
	}

	private void initializeTimerData(ProgressDialog pd) {
		timerData = null;
		
		if (baseURL!=null)
			timerData = OpenWebifTools.readTimers(baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Timer Data: "+taskTitle));
		
		SwingUtilities.invokeLater(()->{
			btnReLoadTimerData.setToolTipText("Reload Timer Data");
			OpenWebifController.setIcon(btnReLoadTimerData, GrayCommandIcons.Reload.getIcon(), GrayCommandIcons.Reload_Dis.getIcon());
			txtActiveTimers.setEnabled(timerData != null);
			btnShowTimers.setEnabled(timerData != null);
			updateActiveTimersText();
		});
	}
	
	private void updateActiveTimersText() {
		if (timerData == null) {
			txtActiveTimers.setText("<No Data>");
			return;
		}
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (Timer timer : timerData.timers) {
			if (timer.state2!=Timer.State.Running) continue;
			if (!isFirst) sb.append("\r\n");
			sb.append(String.format("[%s] %s: %s", timer.type, timer.servicename, timer.name));
			isFirst = false;
		}
		
		String str = sb.toString();
		if (str.isBlank())
			str = "No Active Timers";
		txtActiveTimers.setText(str);
	}

	private void initializeBouquetData(ProgressDialog pd) {
		bouquetData = null;
		
		if (baseURL!=null)
			bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Bouquet Data: "+taskTitle));
		
		SwingUtilities.invokeLater(()->{
			btnBouquetData.setToolTipText("Reload Bouquet Data");
			OpenWebifController.setIcon(btnBouquetData, GrayCommandIcons.Reload.getIcon(), GrayCommandIcons.Reload_Dis.getIcon());
			cmbbxBouquet.setModel  (bouquetData==null ? new DefaultComboBoxModel<Bouquet>() : new DefaultComboBoxModel<Bouquet>(bouquetData.bouquets));
			cmbbxBouquet.setEnabled(bouquetData!=null && !bouquetData.bouquets.isEmpty());
			labBouquet  .setEnabled(bouquetData!=null && !bouquetData.bouquets.isEmpty());
			cmbbxBouquet.setSelectedItem(null);
			stationsPanel.removeAll();
		});
	}

	private static class StationListRenderer implements ListCellRenderer<Bouquet.SubService> {
		
		Tables.LabelRendererComponent rendererComp = new Tables.LabelRendererComponent();
		
		@Override
		public Component getListCellRendererComponent(JList<? extends Bouquet.SubService> list, Bouquet.SubService value, int index, boolean isSelected, boolean hasFocus) {
			String valueStr = value==null ? "" : value.toString();
			if (value!=null && value.isMarker()) valueStr = String.format("--  %s  --", valueStr);
			rendererComp.configureAsListCellRendererComponent(list, null, valueStr, index, isSelected, hasFocus, null, ()->value!=null && value.isMarker() ? Color.GRAY : list.getForeground());
			return rendererComp;
		}
	
	}
	
	private static class TimersDialog extends JDialog {
		private static final long serialVersionUID = -6615053929219118162L;
		private static TimersDialog instance = null;
		
		private final Window window;
		private final LogWindow logWindow;
		private final JTable table;
		private final JScrollPane tableScrollPane;
		private final TimersTableRowSorter tableRowSorter;
		private TimersTableModel tableModel;
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
			
			tableModel = new TimersTableModel();
			table = new JTable(tableModel);
			table.setRowSorter(tableRowSorter = new TimersTableRowSorter(tableModel));
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.setColumnSelectionAllowed(false);
			table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			table.getSelectionModel().addListSelectionListener(e->{
				int rowV = table.getSelectedRow();
				int rowM = table.convertRowIndexToModel(rowV);
				textArea.setText(TimersPanel.generateShortInfo(tableModel.getRow(rowM)));
			});
			tableModel.setTable(table);
			tableModel.setColumnWidths(table);
			tableModel.setAllDefaultRenderers();
			
			new TimersTableContextMenu().addTo(table);
			
			tableScrollPane = new JScrollPane(table);
			tableScrollPane.setPreferredSize(new Dimension(300, 600));
			
			JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
			splitPane.setLeftComponent(tableScrollPane);
			splitPane.setRightComponent(textArea.createScrollPane(500, 500));
			
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
			tableModel.setAllDefaultRenderers();
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
	}
	
	private static class TimersTableRenderer implements TableCellRenderer {
		
		private final Tables.LabelRendererComponent rendererComp;
		private final TimersTableModel tableModel;

		TimersTableRenderer(TimersTableModel tableModel) {
			this.tableModel = tableModel;
			rendererComp = new Tables.LabelRendererComponent();
		}

		@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
			
			Supplier<Color> bgCol = null;
			Supplier<Color> fgCol = null;
			String valueStr = value==null ? null : value.toString();
			
			int columnM = table.convertColumnIndexToModel(columnV);
			TimersTableModel.ColumnID columnID = tableModel.getColumnID(columnM);
			
			if (value instanceof Timer.Type)
				bgCol = ()->TimersPanel.TimersTableCellRenderer.getBgColor((Timer.Type) value);
				
			if (value instanceof Timer.State)
				bgCol = ()->TimersPanel.TimersTableCellRenderer.getBgColor((Timer.State) value);
				
			rendererComp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, bgCol, fgCol);
			rendererComp.setHorizontalAlignment(columnID.horizontalAlignment);
			
			return rendererComp;
		}
	}
	
	private static class TimersTableModel extends Tables.SimplifiedTableModel<TimersTableModel.ColumnID> {

		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			type               ("Type"               , Timer.Type .class,  90, SwingConstants.CENTER),
			state              ("State"              , Timer.State.class,  70, SwingConstants.CENTER),
			servicename        ("Station"            , String     .class, 110),
			name               ("Name"               , String     .class, 220),
			_date_             ("Date"               , String     .class, 115, SwingConstants.RIGHT),
			begin              ("Begin"              , String     .class,  55, SwingConstants.RIGHT),
			end                ("End"                , String     .class,  55, SwingConstants.RIGHT),
			duration           ("Duration"           , String     .class,  60, SwingConstants.RIGHT),
			;
			private final SimplifiedColumnConfig config;
			private final int horizontalAlignment;
			ColumnID(String name, Class<?> columnClass, int width) {
				this(name, columnClass, width, SwingConstants.LEFT);
			}
			ColumnID(String name, Class<?> columnClass, int width, int horizontalAlignment) {
				this.horizontalAlignment = horizontalAlignment;
				config = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return config; }
		}

		private final Vector<Timer> data;

		private TimersTableModel() {
			this(new Vector<>());
		}
		private TimersTableModel(Vector<Timer> data) {
			super(ColumnID.values());
			if (data==null) throw new IllegalArgumentException();
			this.data = data;
		}

		private void setAllDefaultRenderers() {
			TimersTableRenderer renderer = new TimersTableRenderer(this);
			table.setDefaultRenderer(Timer.Type .class, renderer);
			table.setDefaultRenderer(Timer.State.class, renderer);
			table.setDefaultRenderer(String     .class, renderer);
		}

		@Override public int getRowCount() {
			return data.size();
		}

		private Timer getRow(int rowIndex) {
			if (rowIndex < 0 || data.size() <= rowIndex) return null;
			return data.get(rowIndex);
		}

		@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			Timer timer = getRow(rowIndex);
			
			if (timer!=null)
				switch (columnID) {
				case state      : return timer.state2;
				case type       : return timer.type;
				case servicename: return timer.servicename;
				case name       : return timer.name;
				case _date_     : return OpenWebifController.dateTimeFormatter.getTimeStr( timer.begin*1000, Locale.GERMANY,   true,   true, false, false, false);
				case begin      : return OpenWebifController.dateTimeFormatter.getTimeStr( timer.begin*1000, Locale.GERMANY,  false,  false, false,  true, false);
				case end        : return OpenWebifController.dateTimeFormatter.getTimeStr( timer.end  *1000, Locale.GERMANY,  false,  false, false,  true, false);
				case duration   : return DateTimeFormatter.getDurationStr(timer.duration);
				}
			return null;
		}
	}
}
