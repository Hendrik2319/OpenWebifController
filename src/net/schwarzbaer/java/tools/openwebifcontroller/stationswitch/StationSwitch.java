package net.schwarzbaer.java.tools.openwebifcontroller.stationswitch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer.Type;
import net.schwarzbaer.java.tools.openwebifcontroller.LogWindow;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.AbstractControlPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.PowerControl;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.VolumeControl;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGDialog;

public class StationSwitch {

	public static void start(String baseURL, boolean asSubWindow) {
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
	private final EPG epg;
	
	StationSwitch(boolean asSubWindow) {
		baseURL = null;
		bouquetData = null;
		selectedStation = null;
		timerData = null;
		
		mainWindow = OpenWebifController.createMainWindow("Station Switch",asSubWindow);
		logWindow = new LogWindow(mainWindow, "Response Log");
		
		epg = new EPG(new EPG.Tools() {
			@Override public String getTimeStr(long millis) {
				return OpenWebifController.dateTimeFormatter.getTimeStr(millis, false, true, false, true, false);
			}
		});
		
		EPGDialog.ExternCommands epgDialogCommands = new EPGDialog.ExternCommands() {
			@Override public void  zapToStation(String baseURL, StationID stationID) { OpenWebifController. zapToStation(stationID, baseURL, logWindow); }
			@Override public void streamStation(String baseURL, StationID stationID) { OpenWebifController.streamStation(stationID, baseURL); }
			@Override public void    addTimer(String baseURL, String sRef, int eventID, Type type) { OpenWebifController.addTimer(baseURL, sRef, eventID, type, mainWindow, logWindow, null); }
			@Override public void deleteTimer(String baseURL, Timer timer) { OpenWebifController.deleteTimer(baseURL, timer, mainWindow, logWindow, null); }
			@Override public void toggleTimer(String baseURL, Timer timer) { OpenWebifController.toggleTimer(baseURL, timer, mainWindow, logWindow, null); }
		};
		
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
		
		JButton btnShowLogWindow = OpenWebifController.createButton("Log", true, e->logWindow.showDialog(LogWindow.Position.RIGHT_OF_PARENT));
		JButton btnShowEPG = OpenWebifController.createButton("EPG", true, e->{
			String baseURL = OpenWebifController.getBaseURL(true, mainWindow);
			if (baseURL==null) return;
			
			if (timerData==null && bouquetData==null)
			{
				if (!OpenWebifController.askUserIfDataShouldBeInitialized(mainWindow, "Timer", "Bouquet")) return;
				reloadTimerData();
				reloadBouquetData();
				mainWindow.pack();
			}
			else if (timerData==null)
			{
				if (!OpenWebifController.askUserIfDataShouldBeInitialized(mainWindow, "Timer")) return;
				reloadTimerData();
			}
			else if (bouquetData==null)
			{
				if (!OpenWebifController.askUserIfDataShouldBeInitialized(mainWindow, "Bouquet")) return;
				reloadBouquetData();
				mainWindow.pack();
			}
			
			Bouquet bouquet = BouquetsNStations.showBouquetSelector(mainWindow, bouquetData);
			if (bouquet==null) return;
			
			EPGDialog.showDialog(mainWindow, baseURL, epg, timerData, bouquet, epgDialogCommands);
		});
		
		
		
		btnReLoadTimerData = OpenWebifController.createButton(GrayCommandIcons.IconGroup.Download, true, e -> reloadTimerData());
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
		
		
		
		stationsPanel = new JPanel(new GridBagLayout());
		
		btnBouquetData = OpenWebifController.createButton(GrayCommandIcons.IconGroup.Download, true, e->{
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
		
		
		
		GridBagConstraints c;
		JPanel controllerPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		controllerPanel.add(powerControl ,c);
		controllerPanel.add(volumeControl,c);
		c.weightx = 0;
		controllerPanel.add(btnShowEPG,c);
		controllerPanel.add(btnShowLogWindow,c);
		
		
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
			OpenWebifController.setIcon(btnReLoadTimerData, GrayCommandIcons.IconGroup.Reload);
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
			OpenWebifController.setIcon(btnBouquetData, GrayCommandIcons.IconGroup.Reload);
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
}
