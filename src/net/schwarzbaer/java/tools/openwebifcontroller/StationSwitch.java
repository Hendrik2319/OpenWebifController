package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.CommandIcons;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;

class StationSwitch {

	static void start(String baseURL, boolean setLookAndFeel) {
		if (setLookAndFeel)
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		new StationSwitch().initialize(baseURL);
	}

	private final StandardMainWindow mainWindow;
	private final JButton btnReload;
	private final JButton btnAddStation;
	private final JLabel labBouquet;
	private final JComboBox<Bouquet> cmbbxBouquet;
	private final JLabel labStation;
	private final JComboBox<Bouquet.SubService> cmbbxStation;
	private String baseURL;
	private BouquetData bouquetData;
	private Bouquet.SubService selectedStation;
	private JPanel stationPanel;
	
	StationSwitch() {
		baseURL = null;
		bouquetData = null;
		selectedStation = null;
		
		mainWindow = OpenWebifController.createMainWindow("Station Switch");
		stationPanel = new JPanel(new GridLayout(0,1));
		
		btnReload = OpenWebifController.createButton(null, CommandIcons.Reload.getIcon(), CommandIcons.Reload_Dis.getIcon(), true, e->{
			initialize(baseURL);
		});
		
		btnAddStation = OpenWebifController.createButton("Add", false, e->{
			Bouquet.SubService station = selectedStation;
			if (station==null) return;
			
			JButton button = OpenWebifController.createButton(station.name, true, e1->{
				if (baseURL==null) return;
				OpenWebifController.zapToStation(station.service.stationID, baseURL);
			});
			button.setHorizontalAlignment(JButton.LEFT);
			
			stationPanel.add(button);
			mainWindow.pack();
			
			if (baseURL!=null)
				new Thread(()->{
					BufferedImage picon = OpenWebifTools.getPicon(baseURL, station.service.stationID);
					Icon icon = picon==null ? null : BouquetsNStations.getScaleIcon(picon, 20, Color.BLACK);
					if (icon!=null)
						SwingUtilities.invokeLater(()->{
							button.setIcon(icon);
							mainWindow.pack();
						});
				}).start();
		});
		
		labStation = new JLabel("Station: ");
		cmbbxStation = OpenWebifController.createComboBox((Bouquet.SubService subService)->{
			selectedStation = subService;
			btnAddStation.setEnabled(selectedStation!=null);
		});
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
		
		
		JPanel configPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new Insets(1, 1, 1, 1);
		
		c.gridheight = 2;
		configPanel.add(btnReload,c);
		
		c.gridheight = 1;
		configPanel.add(labBouquet,c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1;
		configPanel.add(cmbbxBouquet,c);
		c.weightx = 0;
		
		c.gridwidth = 1;
		configPanel.add(labStation,c);
		c.weightx = 1;
		configPanel.add(cmbbxStation,c);
		c.weightx = 0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		configPanel.add(btnAddStation,c);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		contentPane.add(configPanel,BorderLayout.NORTH);
		contentPane.add(stationPanel,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane);
	}
	
	private void initialize(String baseURL) {
		if (baseURL==null) baseURL = OpenWebifController.getBaseURL(true, mainWindow);
		this.baseURL = baseURL;
		this.bouquetData = null;
		stationPanel.removeAll();
		
		if (this.baseURL!=null)
			OpenWebifController.runWithProgressDialog(mainWindow, "Initialize", pd->{
				bouquetData = OpenWebifTools.readBouquets(this.baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Station Switch: "+taskTitle));
			});
		
		cmbbxBouquet.setModel  (bouquetData==null ? new DefaultComboBoxModel<Bouquet>() : new DefaultComboBoxModel<Bouquet>(bouquetData.bouquets));
		cmbbxBouquet.setEnabled(bouquetData!=null && !bouquetData.bouquets.isEmpty());
		labBouquet  .setEnabled(bouquetData!=null && !bouquetData.bouquets.isEmpty());
		cmbbxBouquet.setSelectedItem(null);
		
		mainWindow.pack();
	}

}
