package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.CurrentStation;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.system.Settings.DefaultAppSettings.SplitPaneDividersDefinition;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.SingleStationEPGPanel;

public class BouquetsNStations extends JPanel {
	private static final long serialVersionUID = 1873358104402086477L;
	static final PiconLoader PICON_LOADER = new PiconLoader();
	
	private final OpenWebifController main;
	private final JTree bsTree;
	private final StatusOut statusLine;
	private final ValuePanel valuePanel;
	private final SingleStationEPGPanel singleStationEPGPanel;
	private final FileChooser m3uFileChooser;
	private final FileChooser txtFileChooser;
	private final OpenWebifController.Updater periodicUpdater10s;
	public  final BouquetsNStationsUpdateNotifier bouquetsNStationsUpdateNotifier;
	
	private BSTreeNode.RootNode bsTreeRoot;
	private DefaultTreeModel bsTreeModel;
	private boolean updatePlayableStatesPeriodically;
	private boolean updateCurrentStationPeriodically;
	private CurrentStation currentStationData;
	private final Vector<BSTreeNode.StationNode> selectedStationNodes;

	public BouquetsNStations(OpenWebifController main) {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		this.main = main;
		
		bouquetsNStationsUpdateNotifier = new BouquetsNStationsUpdateNotifier() {
			@Override public StationID getCurrentStation() {
				return currentStationData==null || currentStationData.stationInfo==null ? null : currentStationData.stationInfo.stationID;
			}
		};
		bsTreeRoot = null;
		bsTreeModel = null;
		PICON_LOADER.clear();
		selectedStationNodes = new Vector<>();
		
		m3uFileChooser = new FileChooser("Playlist", "m3u");
		txtFileChooser = new FileChooser("Text-File", "txt");
		
		bsTree = new JTree(bsTreeModel);
		bsTree.setCellRenderer(new BSTreeCellRenderer());
		bsTree.setRowHeight(BSTreeNode.ROW_HEIGHT);
		
		JScrollPane treeScrollPane = new JScrollPane(bsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		statusLine = new StatusOut();
		PICON_LOADER.setStatusOutput(statusLine);
		
		valuePanel = new ValuePanel(this.main::getBaseURL);
		singleStationEPGPanel = new SingleStationEPGPanel(main.epg, this.main::getBaseURL, statusLine::showMessage);
		
		JTabbedPane rightPanel = new JTabbedPane();
		rightPanel.addTab("Station", valuePanel.panel);
		rightPanel.addTab("EPG", singleStationEPGPanel);
		
		JSplitPane centerPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, treeScrollPane, rightPanel);
		add(centerPanel,BorderLayout.CENTER);
		add(statusLine.comp,BorderLayout.SOUTH);
		
		updatePlayableStatesPeriodically = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdatePlayableStates, false);
		updateCurrentStationPeriodically = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateCurrentStation, false);
		
		periodicUpdater10s = new OpenWebifController.Updater(10, () -> {
			if (updatePlayableStatesPeriodically) {
				statusLine.showMessage("Update 'Playable' States");
				updatePlayableStates();
			}
			if (updateCurrentStationPeriodically) {
				statusLine.showMessage("Update 'Current Station'");
				updateCurrentStation();
			}
			statusLine.clear();
		});
		
		new TreeContextMenu().addTo(bsTree);
		
		bsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		bsTree.addTreeSelectionListener(e->{
			TreePath[] selectedTreePaths = bsTree.getSelectionPaths();
			
			if (selectedTreePaths.length==1)
				valuePanel.showValues(selectedTreePaths[0]);
			
			selectedStationNodes.clear();
			for (TreePath path:selectedTreePaths) {
				Object obj = path.getLastPathComponent();
				if (obj instanceof BSTreeNode.StationNode)
					selectedStationNodes.add((BSTreeNode.StationNode) obj);
			}
		});
		
		OpenWebifController.settings.registerSplitPaneDividers(
				new SplitPaneDividersDefinition<>(this.main.mainWindow, AppSettings.ValueKey.class)
				.add(centerPanel          , AppSettings.ValueKey.SplitPaneDivider_BouquetsNStations_CenterPanel          )
				.add(valuePanel.panel     , AppSettings.ValueKey.SplitPaneDivider_BouquetsNStations_ValuePanel           )
				.add(singleStationEPGPanel, AppSettings.ValueKey.SplitPaneDivider_BouquetsNStations_SingleStationEPGPanel)
		);
		
		if (shouldPeriodicUpdaterRun())
			periodicUpdater10s.start();
	}
	
	private class TreeContextMenu extends ContextMenu {
		private static final long serialVersionUID = -5273880699058313222L;
		
		@SuppressWarnings("unused")
		private BSTreeNode.RootNode    clickedRootNode;
		private BSTreeNode.BouquetNode clickedBouquetNode;
		private BSTreeNode.StationNode clickedStationNode;

		TreeContextMenu() {
			clickedRootNode    = null;
			clickedBouquetNode = null;
			clickedStationNode = null;
			
			add(OpenWebifController.createMenuItem("Reload Bouquets", GrayCommandIcons.IconGroup.Reload, e->{
				main.getBaseURLAndRunWithProgressDialog("Reload Bouquets", BouquetsNStations.this::readData);
			}));
			
			add(OpenWebifController.createMenuItem("Save All Stations to TabSeparated-File", GrayCommandIcons.IconGroup.Save, e->{
				if (bsTreeRoot==null) return;
				
				main.runWithProgressDialog("Save Stations to TabSeparated-File", pd -> {
					OpenWebifController.setIndeterminateProgressTask(pd, "Choose Output File");
					
					if (txtFileChooser.showSaveDialog(main.mainWindow)!=FileChooser.APPROVE_OPTION) return;
					File outFile = txtFileChooser.getSelectedFile();
					
					OpenWebifController.setIndeterminateProgressTask(pd, "Write to Output File");
					try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
						
						for (Bouquet bouquet:bsTreeRoot.bouquetData.bouquets)
							for (Bouquet.SubService subservice:bouquet.subservices)
								out.printf("%s\t%s\t%s%n", bouquet.name, subservice.name, subservice.service.stationID.toJoinedStrings("\t","%d"));
						
					} catch (FileNotFoundException ex) {
						ex.printStackTrace();
					}
				});
			}));
			
			
			add(OpenWebifController.createCheckBoxMenuItem("Update 'Is Playable' States Periodically", updatePlayableStatesPeriodically, isChecked->{
				startStopPeriodicUpdater10s( ()->updatePlayableStatesPeriodically=isChecked );
				OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdatePlayableStates, isChecked);
			}));
			JMenuItem miUpdatePlayableStatesNow = add(OpenWebifController.createMenuItem("Update 'Is Playable' States Now", GrayCommandIcons.IconGroup.Reload, e->{
				periodicUpdater10s.runOnce( () -> updatePlayableStates() );
			}));
			
			add(OpenWebifController.createCheckBoxMenuItem("Update 'Current Station' Periodically", updateCurrentStationPeriodically, isChecked->{
				startStopPeriodicUpdater10s( ()->updateCurrentStationPeriodically=isChecked );
				OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateCurrentStation, isChecked);
			}));
			JMenuItem miUpdateCurrentStationNow = add(OpenWebifController.createMenuItem("Update 'Current Station' Now", GrayCommandIcons.IconGroup.Reload, e->{
				periodicUpdater10s.runOnce( () -> updateCurrentStation() );
			}));
			add(OpenWebifController.createMenuItem("Show 'Current Station' Data", e->{
				String msg = BouquetsNStations.toString( currentStationData );
				TextAreaDialog.showText(main.mainWindow, "Current Station", 800, 500, true, msg);
				//JOptionPane.showMessageDialog(mainWindow, msg, "Current Station", JOptionPane.INFORMATION_MESSAGE);
			}));
			
			JMenuItem miWriteSelectedStreamsToM3U = add(OpenWebifController.createMenuItem("Write Streams of Selected Stations to M3U-File", GrayCommandIcons.IconGroup.Save, e->{
				if (selectedStationNodes.isEmpty()) return;
				
				String baseURL = main.getBaseURL();
				if (baseURL==null) return;
				
				if (m3uFileChooser.showSaveDialog(main.mainWindow)!=FileChooser.APPROVE_OPTION) return;
				File m3uFile = m3uFileChooser.getSelectedFile();
				
				writeStreamsToM3U(selectedStationNodes,baseURL,m3uFile);
				main.openFileInVideoPlayer(m3uFile, String.format("Open Playlist of Selected Stations"));
			}));
			
			addSeparator();
			
			JMenuItem miUpdatePlayableStatesBouquet = add(OpenWebifController.createMenuItem("Update 'Is Playable' States of Bouquet", GrayCommandIcons.IconGroup.Reload, e->{
				String baseURL = main.getBaseURL();
				if (baseURL==null) return;
				periodicUpdater10s.runOnce( () -> updatePlayableStates(baseURL, clickedBouquetNode) );
			}));
			
			JMenuItem miWriteBouquetStreamsToM3U = add(OpenWebifController.createMenuItem("Write Streams of Bouquet to M3U-File", GrayCommandIcons.IconGroup.Save, e->{
				if (clickedBouquetNode==null) return;
				
				String baseURL = main.getBaseURL();
				if (baseURL==null) return;
				
				if (m3uFileChooser.showSaveDialog(main.mainWindow)!=FileChooser.APPROVE_OPTION) return;
				File m3uFile = m3uFileChooser.getSelectedFile();
				
				writeStreamsToM3U(clickedBouquetNode.bouquet,baseURL,m3uFile);
				main.openFileInVideoPlayer(m3uFile, String.format("Open Playlist of Bouquet: %s", clickedBouquetNode.bouquet.name));
			}));
			
			JMenuItem miShowEPGforBouquet = add(OpenWebifController.createMenuItem("Show EPG for Bouquet", e->{
				if (clickedBouquetNode==null) return;
				main.openEPGDialog(clickedBouquetNode.bouquet);
			}));
			
			addSeparator();
			
			JMenuItem miLoadPicons = add(OpenWebifController.createMenuItem("Load Picons", GrayCommandIcons.IconGroup.Image, e->{
				if (clickedBouquetNode!=null) {
					String baseURL = main.getBaseURL();
					if (baseURL==null) return;
					PICON_LOADER.setBaseURL(baseURL);
					for (BSTreeNode.StationNode stationNode:clickedBouquetNode.children)
						PICON_LOADER.addTask(stationNode.getStationID());
				} else if (clickedStationNode!=null) {
					String baseURL = main.getBaseURL();
					if (baseURL==null) return;
					PICON_LOADER.setBaseURL(baseURL);
					PICON_LOADER.addTask(clickedStationNode.getStationID());
				}
			}));
			JMenuItem miSwitchToStation = add(OpenWebifController.createMenuItem("Switch To Station",                                   e->main. zapToStation(clickedStationNode.getStationID())));
			JMenuItem miStreamStation   = add(OpenWebifController.createMenuItem("Stream Station"   , GrayCommandIcons.IconGroup.Image, e->main.streamStation(clickedStationNode.getStationID())));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				TreePath clickedTreePath = bsTree.getPathForLocation(x,y);
				clickedRootNode    = null;
				clickedBouquetNode = null;
				clickedStationNode = null;
				if (clickedTreePath!=null) {
					Object obj = clickedTreePath.getLastPathComponent();
					if (obj instanceof BSTreeNode.RootNode   ) clickedRootNode    = (BSTreeNode.RootNode   ) obj;
					if (obj instanceof BSTreeNode.BouquetNode) clickedBouquetNode = (BSTreeNode.BouquetNode) obj;
					if (obj instanceof BSTreeNode.StationNode) clickedStationNode = (BSTreeNode.StationNode) obj;
				}
				
				miLoadPicons     .setEnabled(clickedBouquetNode!=null || (clickedStationNode!=null && !clickedStationNode.subservice.isMarker()));
				miSwitchToStation.setEnabled(clickedStationNode!=null && !clickedStationNode.subservice.isMarker());
				miStreamStation  .setEnabled(clickedStationNode!=null && !clickedStationNode.subservice.isMarker());
				miLoadPicons.setText(
						clickedBouquetNode!=null ?
								String.format("Load Picons of Bouquet \"%s\"", clickedBouquetNode.bouquet.name) :
								(clickedStationNode!=null && !clickedStationNode.subservice.isMarker()) ?
										String.format("Load Picon of \"%s\"", clickedStationNode.subservice.name) :
										"Load Picons"
				);
				miSwitchToStation.setText(clickedStationNode!=null && !clickedStationNode.subservice.isMarker() ? String.format("Switch To \"%s\"", clickedStationNode.subservice.name) : "Switch To Station");
				miStreamStation  .setText(clickedStationNode!=null && !clickedStationNode.subservice.isMarker() ? String.format("Stream \"%s\""   , clickedStationNode.subservice.name) : "Stream Station"   );
				
				miShowEPGforBouquet.setEnabled(clickedBouquetNode!=null);
				miShowEPGforBouquet.setText(
						clickedBouquetNode!=null ?
								String.format("Show EPG for Bouquet \"%s\"", clickedBouquetNode.bouquet.name) :
								"Show EPG for Bouquet"
				);
				miUpdatePlayableStatesBouquet.setEnabled(clickedBouquetNode!=null && !updatePlayableStatesPeriodically);
				miUpdatePlayableStatesBouquet.setText(
						clickedBouquetNode!=null ?
								String.format("Update 'Is Playable' States of Bouquet \"%s\"", clickedBouquetNode.bouquet.name) :
								"Update 'Is Playable' States of Bouquet"
				);
				miWriteBouquetStreamsToM3U.setEnabled(clickedBouquetNode!=null);
				miWriteBouquetStreamsToM3U.setText(
						clickedBouquetNode!=null ?
								String.format("Write Streams of Bouquet \"%s\" to M3U-File", clickedBouquetNode.bouquet.name) :
								"Write Streams of Bouquet to M3U-File"
				);
				miWriteSelectedStreamsToM3U.setEnabled(!selectedStationNodes.isEmpty());
				
				miUpdateCurrentStationNow.setEnabled(!updateCurrentStationPeriodically);
				miUpdatePlayableStatesNow.setEnabled(!updatePlayableStatesPeriodically);
			});
		}
	}

	public Bouquet showBouquetSelector(Component parent) {
		if (bsTreeRoot==null) return null;
		return showBouquetSelector(parent, bsTreeRoot.bouquetData);
	}

	public static Bouquet showBouquetSelector(Component parent, BouquetData bouquetData)
	{
		Vector<Bouquet> bouquets = bouquetData.bouquets;
		Object result = JOptionPane.showInputDialog(
				parent,
				"Select a Bouquet:",
				"Select a Bouquet",
				JOptionPane.QUESTION_MESSAGE,
				null,
				bouquets.toArray(new Bouquet[bouquets.size()]),
				null
		);
		if (result instanceof Bouquet)
			return (Bouquet) result;
		return null;
	}

	private boolean shouldPeriodicUpdaterRun() {
		return updatePlayableStatesPeriodically ||
				updateCurrentStationPeriodically;
	}

	private void startStopPeriodicUpdater10s(Runnable changeValues) {
		boolean currentState = shouldPeriodicUpdaterRun();
		changeValues.run();
		boolean newState = shouldPeriodicUpdaterRun();
		
		if (currentState != newState) {
			if (newState)
				periodicUpdater10s.start();
			else
				periodicUpdater10s.stop();
		}
	}

	private void updateCurrentStation() {
		String baseURL = main.getBaseURL(false);
		if (baseURL==null) return;
		updateCurrentStation(baseURL, null);
	}

	private void updateCurrentStation(String baseURL, ProgressDialog pd) {
		Consumer<String> setIndeterminateProgressTask = pd==null ? null : taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "CurrentStation: "+taskTitle);
		
		updateCurrentlyPlayedStationNodes(false);
		currentStationData = OpenWebifTools.getCurrentStation(baseURL, setIndeterminateProgressTask);
		updateCurrentlyPlayedStationNodes(true);
		
		bouquetsNStationsUpdateNotifier.setCurrentStation(
				currentStationData==null || currentStationData.stationInfo==null
					? null
					: currentStationData.stationInfo.stationID
		);
	}
	
	

	private void updateCurrentlyPlayedStationNodes(boolean isCurrentlyPlayed) {
		if (bsTreeRoot == null) return;
		if (bsTreeModel == null) return;
		if (currentStationData == null) return;
		if (currentStationData.stationInfo == null) return;
		StationID stationID = currentStationData.stationInfo.stationID;
		if (stationID == null) return;
		
		bsTreeRoot.updateStationNodes(stationID, bsTreeModel, stations->{
			stations.forEach(station->{
				station.isCurrentlyPlayed=isCurrentlyPlayed;
			});
		});
	}

	private void updatePlayableStates() {
		if (bsTreeRoot==null) return;
		String baseURL = main.getBaseURL(false);
		if (baseURL==null) return;
		
		bsTreeRoot.forEachChild((bouquetNode,treepath)->{
			if (isExpanded(treepath))
				updatePlayableStates(baseURL, bouquetNode);
		});
	}

	private void updatePlayableStates(String baseURL, BSTreeNode.BouquetNode bouquetNode) {
		int[] indices = new int[bouquetNode.children.size()];
		for (int i=0; i<bouquetNode.children.size(); i++) {
			indices[i] = i;
			BSTreeNode.StationNode stationNode = bouquetNode.children.get(i);
			stationNode.updatePlayableState(baseURL);
			SwingUtilities.invokeLater(()->{ if (bsTreeModel!=null) bsTreeModel.nodeChanged(stationNode); });
		}
	}

	private static class ValueContainer<ValueType> {
		ValueType value;
		boolean isUnset;
		ValueContainer() { isUnset = true; }
		void set(ValueType value) {
			this.value = value;
			isUnset = false;
		}
	}

	private boolean isExpanded(TreePath treepath) {
		ValueContainer<Boolean> isExpanded = new ValueContainer<>();
		invokeAndWait(()->isExpanded.set(bsTree.isExpanded(treepath)));
		if (isExpanded.isUnset) return false;
		return isExpanded.value.booleanValue();
	}
	
	private void invokeAndWait(Runnable task) {
		try { SwingUtilities.invokeAndWait(task); }
		catch (InvocationTargetException | InterruptedException e) {
			System.err.printf("%s while executing SwingUtilities.invokeAndWait: %s%n", e.getClass().getName(), e);
		}
	}

	public boolean hasData() {
		return bsTreeRoot!=null && bsTreeModel!=null;
	}

	public void readData(String baseURL, ProgressView pd) {
		if (baseURL==null) return;
		
		BouquetData bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Bouquets 'n' Stations: "+taskTitle));
		
		if (bouquetData!=null) {
			PICON_LOADER.clear();
			PICON_LOADER.setBaseURL(baseURL);
			bsTreeRoot = new BSTreeNode.RootNode(bouquetData);
			bsTreeModel = new DefaultTreeModel(bsTreeRoot, true);
			bsTree.setModel(bsTreeModel);
			PICON_LOADER.setTreeModel(bsTreeModel,bsTreeRoot);
		}
	}
	
	void clearPiconCache() {
		PICON_LOADER.clearPiconCache();
	}

	private void writeStreamsToM3U(Vector<BSTreeNode.StationNode> stationNodes, String baseURL, File m3uFile) {
		Iterator<Bouquet.SubService> iterator = stationNodes.stream().filter(node->!node.isMarker()).map(node->node.subservice).iterator();
		writeStreamsToM3U(()->iterator, baseURL, m3uFile);
	}

	private void writeStreamsToM3U(Bouquet bouquet, String baseURL, File m3uFile) {
		writeStreamsToM3U(bouquet.subservices, baseURL, m3uFile);
	}

	private void writeStreamsToM3U(Iterable<Bouquet.SubService> subservices, String baseURL, File m3uFile) {
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(m3uFile), StandardCharsets.UTF_8))) {
			
			out.println("#EXTM3U");
			for (Bouquet.SubService subservice:subservices) {
				if (subservice.isMarker()) continue;
				// #EXTINF:0,SPORT1
				// http://et7x00:8001/1:0:1:384:21:85:C00000:0:0:0:
				out.printf("#EXTINF:0,%s%n", subservice.name);
				out.println(OpenWebifTools.getStationStreamURL(baseURL, subservice.service.stationID));
			}
			
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private static String toString(CurrentStation currentStationData) {
		ValueListOutput out = new ValueListOutput();
		if (currentStationData==null)
			out.add(0, "No Current Station Data");
		else
			OpenWebifController.generateOutput(out, 0, currentStationData);
		return out.generateOutput();
	}
	
	public static abstract class BouquetsNStationsUpdateNotifier
	{
		public interface BouquetsNStationsListener {
			void setCurrentStation(StationID stationID);
		}
		
		private final Vector<BouquetsNStationsListener> listeners = new Vector<>();
		
		public void    addListener(BouquetsNStationsListener listener) { listeners.   add(listener); }
		public void removeListener(BouquetsNStationsListener listener) { listeners.remove(listener); }
		
		public void setCurrentStation(StationID stationID)
		{
			for (BouquetsNStationsListener listener : listeners)
				listener.setCurrentStation(stationID);
		}
		public abstract StationID getCurrentStation();
	}

	public static Icon getScaleIcon(BufferedImage img, int newHeight, Color bgColor) {
		BufferedImage scaledImage = scaleImage(img, newHeight, bgColor);
		return scaledImage==null ? null : new ImageIcon(scaledImage);
	}

	public static BufferedImage scaleImage(BufferedImage img, int newHeight, Color bgColor) {
		if (img==null) return null;
		int h = img.getHeight();
		int w = img.getWidth();
		int newWidth = (int) Math.round(w*newHeight / (double)h);
		BufferedImage newImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = newImg.createGraphics();
		//g2.setRenderingHint(RenderingHints., hintValue);
		g2.drawImage(img, 0,0, newWidth,newHeight, bgColor, null);
		return newImg;
	}
	
	static class StatusOut {
		
		private final JLabel comp;
		
		StatusOut() {
			comp = new JLabel(); 
			comp.setBorder(BorderFactory.createEtchedBorder());
		}
		
		public void showMessage(String msg) {
			SwingUtilities.invokeLater(()->comp.setText(msg));
		}
		public void clear() {
			SwingUtilities.invokeLater(()->comp.setText(" "));
		}
	}

	static class BSTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 8843157059053309466L;
		static final Color TEXTCOLOR_DEFAULT          = Color.BLACK;
		static final Color TEXTCOLOR_CURRENTLY_PLAYED = new Color(0x0080ff);
		static final Color TEXTCOLOR_STATE_UNDEFINED  = new Color(0x800080);
		static final Color TEXTCOLOR_IS_PLAYABLE      = new Color(0x008000);
		static final Color TEXTCOLOR_IS_NOT_PLAYABLE  = Color.BLACK;

		@Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
			Component comp = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
			
			if (value instanceof BSTreeNode) {
				BSTreeNode<?,?> treeNode = (BSTreeNode<?,?>) value;
				setIcon(treeNode.icon);
			}
			if (value instanceof BSTreeNode.StationNode) {
				BSTreeNode.StationNode stationNode = (BSTreeNode.StationNode) value;
				if (stationNode.isMarker()) {
					if (!isSelected) setForeground(TEXTCOLOR_DEFAULT);
					
				} else if (stationNode.isCurrentlyPlayed) {
					if (!isSelected) setForeground(TEXTCOLOR_CURRENTLY_PLAYED);
					
				} else if (stationNode.isServicePlayable==null) {
					if (!isSelected) setForeground(TEXTCOLOR_STATE_UNDEFINED);
					
				} else {
					if (!isSelected) setForeground(stationNode.isServicePlayable ? TEXTCOLOR_IS_PLAYABLE : TEXTCOLOR_IS_NOT_PLAYABLE);
				}
			} else
				if (!isSelected) setForeground(TEXTCOLOR_DEFAULT);
			
			return comp;
		}
		
	}
	
}
