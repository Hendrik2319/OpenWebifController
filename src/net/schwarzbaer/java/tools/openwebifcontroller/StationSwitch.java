package net.schwarzbaer.java.tools.openwebifcontroller;

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

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.CommandIcons;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.PowerControl;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.VolumeControl;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;

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
	private JButton btnTimerData;
	private JButton btnShowTimers;
	private Timers timerData;
	//private JLabel labActiveTimers;
	private JTextArea txtActiveTimers;
	
	StationSwitch(boolean asSubWindow) {
		baseURL = null;
		bouquetData = null;
		selectedStation = null;
		timerData = null;
		
		mainWindow = OpenWebifController.createMainWindow("Station Switch",asSubWindow);
		stationsPanel = new JPanel(new GridBagLayout());
		
		OpenWebifController.AbstractControlPanel.ExternCommands controlPanelCommands = new OpenWebifController.AbstractControlPanel.ExternCommands() {
			@Override public void showMessageResponse(MessageResponse response, String title) {
				OpenWebifController.showMessageResponse(mainWindow, response, title);
			}
			@Override public String getBaseURL() {
				return baseURL!=null ? baseURL : (baseURL = OpenWebifController.getBaseURL(true, mainWindow));
			}
		};
		
		powerControl  = new OpenWebifController.PowerControl (controlPanelCommands,false,true,true);
		volumeControl = new OpenWebifController.VolumeControl(controlPanelCommands,false,true,true);
		powerControl.addUpdateTask(baseURL -> volumeControl.initialize(baseURL,null));
		
		btnBouquetData = OpenWebifController.createButton(CommandIcons.Download.getIcon(), CommandIcons.Download_Dis.getIcon(), true, e->{
			String title = (bouquetData == null ? "Load" : "Reload") +" Bouquet Data";
			OpenWebifController.runWithProgressDialog(mainWindow, title, this::initializeBouquetData);
			mainWindow.pack();
		});
		btnBouquetData.setToolTipText("Load Bouquet Data");
		
		btnAddStation = OpenWebifController.createButton("Add", false, e->{
			Bouquet.SubService station = selectedStation;
			if (station==null) return;
			
			JLabel label = new JLabel(station.name);
			
			JButton btnZap = OpenWebifController.createButton("Switch", true, e1->{
				if (baseURL==null) return;
				OpenWebifController.zapToStation(station.service.stationID, baseURL);
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
		cmbbxStation.setRenderer(new StationSwitch.StationListRenderer());
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
		
		
		
		btnTimerData = OpenWebifController.createButton(CommandIcons.Download.getIcon(), CommandIcons.Download_Dis.getIcon(), true, e-> {
			String title = (timerData == null ? "Load" : "Reload") +" Bouquet Data";
			OpenWebifController.runWithProgressDialog(mainWindow, title, this::initializeTimerData);
		});
		btnTimerData.setToolTipText("Load Timer Data");
		
		txtActiveTimers = new JTextArea();
		JScrollPane scrlpActiveTimers = new JScrollPane(txtActiveTimers);
		//labActiveTimers = new JLabel("Currently no Active Timers");
		txtActiveTimers.setEditable(false);
		txtActiveTimers  .setToolTipText("Active Timers");
		scrlpActiveTimers.setToolTipText("Active Timers");
		scrlpActiveTimers.setPreferredSize(new Dimension(300,70));
		btnShowTimers = OpenWebifController.createButton("Timer", false, e-> {
			// TODO
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
		
		
		JPanel timerPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		c.gridwidth = 1;
		c.weightx = 0;
		timerPanel.add(btnTimerData,c);
		
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
			btnTimerData.setToolTipText("Reload Timer Data");
			OpenWebifController.setIcon(btnTimerData, CommandIcons.Reload.getIcon(), CommandIcons.Reload_Dis.getIcon());
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
		for (Timers.Timer timer : timerData.timers) {
			if (timer.state2!=Timers.Timer.State.Running) continue;
			if (!isFirst) sb.append("\r\n");
			sb.append(String.format("[%s] %s: %s", timer.type, timer.servicename, timer.name));
			isFirst = false;
		}
		
		txtActiveTimers.setText(sb.toString());
	}

	private void initializeBouquetData(ProgressDialog pd) {
		bouquetData = null;
		
		if (baseURL!=null)
			bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Bouquet Data: "+taskTitle));
		
		SwingUtilities.invokeLater(()->{
			btnBouquetData.setToolTipText("Reload Bouquet Data");
			OpenWebifController.setIcon(btnBouquetData, CommandIcons.Reload.getIcon(), CommandIcons.Reload_Dis.getIcon());
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
