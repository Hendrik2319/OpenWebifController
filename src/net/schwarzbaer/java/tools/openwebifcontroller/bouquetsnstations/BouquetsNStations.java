package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.FileChooser;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.ResponseMessage;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

public class BouquetsNStations extends JPanel {
	private static final long serialVersionUID = 1873358104402086477L;
	static final PiconLoader PICON_LOADER = new PiconLoader();
	
	private final OpenWebifController main;
	private final StandardMainWindow mainWindow;
	private final JTree bsTree;
	private final JLabel statusLine;
	private final ValuePanel valuePanel;
	private final FileChooser m3uFileChooser;
	private final OpenWebifController.Updater playableStatesUpdater;
	
	private BSTreeNode.RootNode bsTreeRoot;
	private DefaultTreeModel bsTreeModel;
	private TreePath clickedTreePath;
	@SuppressWarnings("unused")
	private BSTreeNode.RootNode    clickedRootNode;
	private BSTreeNode.BouquetNode clickedBouquetNode;
	private BSTreeNode.StationNode clickedStationNode;
	private TreePath selectedTreePath;
	private boolean updatePlayableStatesPeriodically;

	public BouquetsNStations(OpenWebifController main, StandardMainWindow mainWindow) {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		this.main = main;
		this.mainWindow = mainWindow;
		bsTreeRoot = null;
		bsTreeModel = null;
		PICON_LOADER.clear();
		clickedTreePath = null;
		clickedRootNode    = null;
		clickedBouquetNode = null;
		clickedStationNode = null;
		
		m3uFileChooser = new FileChooser("Playlist", "m3u");
		
		bsTree = new JTree(bsTreeModel);
		bsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		bsTree.setCellRenderer(new BSTreeCellRenderer());
		bsTree.setRowHeight(BSTreeNode.ROW_HEIGHT);
		
		JScrollPane treeScrollPane = new JScrollPane(bsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		valuePanel = new ValuePanel(()->this.main.getBaseURL());
		
		statusLine = new JLabel();
		statusLine.setBorder(BorderFactory.createEtchedBorder());
		PICON_LOADER.setStatusOutput(statusLine);
		
		JSplitPane centerPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,treeScrollPane,valuePanel.panel);
		add(centerPanel,BorderLayout.CENTER);
		add(statusLine,BorderLayout.SOUTH);
		
		JMenuItem miLoadPicons, miSwitchToStation, miStreamStation, miWriteStreamsToM3U, miUpdatePlayableStatesNow, miUpdatePlayableStatesBouquet;
		ContextMenu treeContextMenu = new ContextMenu();
		
		treeContextMenu.add(OpenWebifController.createMenuItem("Reload Bouquets", e->{
			ProgressDialog.runWithProgressDialog(this.mainWindow, "Reload Bouquets", 400, pd->{
				String baseURL = this.main.getBaseURL();
				if (baseURL==null) return;
				
				readData(baseURL,pd);
			});
		}));
		
		playableStatesUpdater = new OpenWebifController.Updater(10,()->{
			//String timeStr = getCurrentTimeStr();
			//System.out.printf("[0x%08X|%s] PlayableStatesUpdater: started%n", Thread.currentThread().hashCode(), timeStr);
			updatePlayableStates();
			//System.out.printf("[0x%08X|%s] PlayableStatesUpdater: finished (%s started)%n", Thread.currentThread().hashCode(), getCurrentTimeStr(), timeStr);
		});
		updatePlayableStatesPeriodically = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdatePlayableStates, false);
		treeContextMenu.add(OpenWebifController.createCheckBoxMenuItem("Update 'Is Playable' States Periodically", updatePlayableStatesPeriodically, isChecked->{
			//System.out.printf("[0x%08X|%s] PlayableStatesUpdater: GUI action%n", Thread.currentThread().hashCode(), getCurrentTimeStr());
			OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdatePlayableStates, updatePlayableStatesPeriodically=isChecked);
			if (updatePlayableStatesPeriodically)
				playableStatesUpdater.start();
			else
				playableStatesUpdater.stop();
		}));
		treeContextMenu.add(miUpdatePlayableStatesNow = OpenWebifController.createMenuItem("Update 'Is Playable' States Now", e->{
			//System.out.printf("[0x%08X|%s] PlayableStatesUpdater: GUI action%n", Thread.currentThread().hashCode(), getCurrentTimeStr());
			playableStatesUpdater.runOnce();
		}));
		
		treeContextMenu.addSeparator();
		
		treeContextMenu.add(miUpdatePlayableStatesBouquet = OpenWebifController.createMenuItem("Update 'Is Playable' States of Bouquet", e->{
			//System.out.printf("[0x%08X|%s] updatePlayableStates(BouquetNode): GUI action%n", Thread.currentThread().hashCode(), getCurrentTimeStr());
			String baseURL = this.main.getBaseURL();
			if (baseURL==null) return;
			playableStatesUpdater.runOnce( ()->{
				//String timeStr = getCurrentTimeStr();
				//System.out.printf("[0x%08X|%s] updatePlayableStates(BouquetNode): started%n", Thread.currentThread().hashCode(), timeStr);
				updatePlayableStates(baseURL, clickedBouquetNode);
				//System.out.printf("[0x%08X|%s] updatePlayableStates(BouquetNode): finished (%s started)%n", Thread.currentThread().hashCode(), getCurrentTimeStr(), timeStr);
			} );
		}));
		
		treeContextMenu.add(miWriteStreamsToM3U = OpenWebifController.createMenuItem("Write Streams of Bouquet to M3U-File", e->{
			if (clickedBouquetNode==null) return;
			
			String baseURL = this.main.getBaseURL();
			if (baseURL==null) return;
			
			if (m3uFileChooser.showSaveDialog(this.mainWindow)!=FileChooser.APPROVE_OPTION) return;
			File m3uFile = m3uFileChooser.getSelectedFile();
			
			writeStreamsToM3U(clickedBouquetNode.bouquet,baseURL,m3uFile);
			this.main.openFileInVideoPlayer(m3uFile, String.format("Open Playlist of Bouquet: %s", clickedBouquetNode.bouquet.name));
		}));
		
		treeContextMenu.addSeparator();
		
		treeContextMenu.add(miLoadPicons = OpenWebifController.createMenuItem("Load Picons", e->{
			if (clickedBouquetNode!=null) {
				String baseURL = this.main.getBaseURL();
				if (baseURL==null) return;
				PICON_LOADER.setBaseURL(baseURL);
				for (BSTreeNode.StationNode stationNode:clickedBouquetNode.children)
					PICON_LOADER.addTask(stationNode.getStationID());
			} else if (clickedStationNode!=null) {
				String baseURL = this.main.getBaseURL();
				if (baseURL==null) return;
				PICON_LOADER.setBaseURL(baseURL);
				PICON_LOADER.addTask(clickedStationNode.getStationID());
			}
		}));
		treeContextMenu.add(miSwitchToStation = OpenWebifController.createMenuItem("Switch To Station", e->{
			String baseURL = this.main.getBaseURL();
			if (baseURL==null) return;
			ResponseMessage response = OpenWebifTools.zapToStation(baseURL, clickedStationNode.getStationID());
			if (response!=null) response.printTo(System.out);
		}));
		treeContextMenu.add(miStreamStation = OpenWebifController.createMenuItem("Stream Station", e->streamStation(clickedStationNode.getStationID())));
		
		treeContextMenu.addTo(bsTree);
		treeContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
			clickedTreePath = bsTree.getPathForLocation(x,y);
			clickedRootNode    = null;
			clickedBouquetNode = null;
			clickedStationNode = null;
			if (clickedTreePath!=null) {
				Object obj = clickedTreePath.getLastPathComponent();
				if (obj instanceof BSTreeNode.RootNode   ) clickedRootNode    = (BSTreeNode.RootNode   ) obj;
				if (obj instanceof BSTreeNode.BouquetNode) clickedBouquetNode = (BSTreeNode.BouquetNode) obj;
				if (obj instanceof BSTreeNode.StationNode) clickedStationNode = (BSTreeNode.StationNode) obj;
			}
			miLoadPicons.setEnabled(clickedBouquetNode!=null || clickedStationNode!=null);
			miLoadPicons.setText(
					clickedBouquetNode!=null ?
							String.format("Load Picons of Bouquet \"%s\"", clickedBouquetNode.bouquet.name) :
							clickedStationNode!=null ?
									String.format("Load Picon of \"%s\"", clickedStationNode.subservice.name) :
									"Load Picons"
			);
			
			miSwitchToStation.setEnabled(clickedStationNode!=null);
			miStreamStation  .setEnabled(clickedStationNode!=null);
			miSwitchToStation.setText(clickedStationNode!=null ? String.format("Switch To \"%s\"", clickedStationNode.subservice.name) : "Switch To Station");
			miStreamStation  .setText(clickedStationNode!=null ? String.format("Stream \"%s\""   , clickedStationNode.subservice.name) : "Stream Station"   );
			
			miUpdatePlayableStatesBouquet.setEnabled(clickedBouquetNode!=null && !updatePlayableStatesPeriodically);
			miWriteStreamsToM3U          .setEnabled(clickedBouquetNode!=null);
			miUpdatePlayableStatesBouquet.setText(
					clickedBouquetNode!=null ?
							String.format("Update 'Is Playable' States of Bouquet \"%s\"", clickedBouquetNode.bouquet.name) :
							"Update 'Is Playable' States of Bouquet"
			);
			miWriteStreamsToM3U.setText(
					clickedBouquetNode!=null ?
							String.format("Write Streams of Bouquet \"%s\" to M3U-File", clickedBouquetNode.bouquet.name) :
							"Write Streams of Bouquet to M3U-File"
			);
			
			miUpdatePlayableStatesNow.setEnabled(!updatePlayableStatesPeriodically);
		});
		
		bsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		bsTree.addTreeSelectionListener(e->{
			selectedTreePath = bsTree.getSelectionPath();
			valuePanel.showValues(selectedTreePath);
			//if (selectedTreePath!=null) {
			//	Object obj = clickedTreePath.getLastPathComponent();
			//	if (obj instanceof BSTreeNode.RootNode   ) clickedRootNode    = (BSTreeNode.RootNode   ) obj;
			//	if (obj instanceof BSTreeNode.BouquetNode) clickedBouquetNode = (BSTreeNode.BouquetNode) obj;
			//	if (obj instanceof BSTreeNode.StationNode) clickedStationNode = (BSTreeNode.StationNode) obj;
			//}
		});
		
		if (updatePlayableStatesPeriodically)
			playableStatesUpdater.start();
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

	private void updatePlayableStates() {
		if (bsTreeRoot==null) return;
		String baseURL = main.getBaseURL(false);
		if (baseURL==null) return;
		
		bsTreeRoot.forEachChild((bouquetNode,treepath)->{
			if (isExpanded(treepath))
				updatePlayableStates(baseURL, bouquetNode);
		});
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

	public void readData(String baseURL, ProgressDialog pd) {
		if (baseURL==null) return;
		
		BouquetData bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle->{
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle("Bouquets 'n' Stations: "+taskTitle);
				pd.setIndeterminate(true);
			});
		});
		
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

	private void writeStreamsToM3U(Bouquet bouquet, String baseURL, File m3uFile) {
		try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(m3uFile), StandardCharsets.UTF_8))) {
			
			out.println("#EXTM3U");
			for (Bouquet.SubService subservice:bouquet.subservices) {
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

	private void streamStation(StationID stationID) {
		if (stationID==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		String url = OpenWebifTools.getStationStreamURL(baseURL, stationID);
		main.openUrlInVideoPlayer(url, String.format("stream station: %s", stationID.toIDStr()));
	}
	
	private static class BSTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 8843157059053309466L;

		@Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
			Component comp = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
			
			if (value instanceof BSTreeNode) {
				BSTreeNode<?,?> treeNode = (BSTreeNode<?,?>) value;
				setIcon(treeNode.icon);
			}
			if (value instanceof BSTreeNode.StationNode) {
				BSTreeNode.StationNode stationNode = (BSTreeNode.StationNode) value;
				if (stationNode.isMarker()) {
					if (!isSelected) setForeground(Color.BLACK);
					
				} else if (stationNode.isServicePlayable==null) {
					if (!isSelected) setForeground(Color.MAGENTA);
					
				} else {
					if (!isSelected) setForeground(stationNode.isServicePlayable ? Color.BLACK : Color.GRAY);
				}
			} else
				if (!isSelected) setForeground(Color.BLACK);
			
			return comp;
		}
		
	}
	
}
