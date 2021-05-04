package net.schwarzbaer.java.tools.openwebifcontroller;

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
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.FileChooser;
import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.EPGevent;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.ResponseMessage;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.TreeIcons;
import net.schwarzbaer.system.DateTimeFormatter;

class BouquetsNStations extends JPanel {
	private static final long serialVersionUID = 1873358104402086477L;
	private static final PiconLoader PICON_LOADER = new PiconLoader();
	
	private final OpenWebifController main;
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

	BouquetsNStations(OpenWebifController main) {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		this.main = main;
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
		
		valuePanel = new ValuePanel(this.main::getBaseURL);
		
		statusLine = new JLabel();
		statusLine.setBorder(BorderFactory.createEtchedBorder());
		PICON_LOADER.setStatusOutput(statusLine);
		
		JSplitPane centerPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,treeScrollPane,valuePanel.panel);
		add(centerPanel,BorderLayout.CENTER);
		add(statusLine,BorderLayout.SOUTH);
		
		JMenuItem miLoadPicons, miSwitchToStation, miStreamStation, miWriteStreamsToM3U, miUpdatePlayableStatesNow, miUpdatePlayableStatesBouquet;
		ContextMenu treeContextMenu = new ContextMenu();
		
		treeContextMenu.add(OpenWebifController.createMenuItem("Reload Bouquets", e->{
			ProgressDialog.runWithProgressDialog(this.main.mainWindow, "Reload Bouquets", 400, pd->{
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
			
			if (m3uFileChooser.showSaveDialog(this.main.mainWindow)!=FileChooser.APPROVE_OPTION) return;
			File m3uFile = m3uFileChooser.getSelectedFile();
			
			writeStreamsToM3U(clickedBouquetNode.bouquet,baseURL,m3uFile);
			main.openFileInVideoPlayer(m3uFile, String.format("Open Playlist of Bouquet: %s", clickedBouquetNode.bouquet.name));
		}));
		
		treeContextMenu.addSeparator();
		
		treeContextMenu.add(miLoadPicons = OpenWebifController.createMenuItem("Load Picons", e->{
			if (clickedBouquetNode!=null) {
				PICON_LOADER.setBaseURL(this.main.getBaseURL());
				clickedBouquetNode.children.forEach(BSTreeNode.StationNode::aquirePicon);
			} else if (clickedStationNode!=null) {
				PICON_LOADER.setBaseURL(this.main.getBaseURL());
				clickedStationNode.aquirePicon();
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

	void readData(String baseURL, ProgressDialog pd) {
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
			PICON_LOADER.setTreeModel(bsTreeModel);
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
	
	private static class ValuePanel {
		private final Supplier<String> getBaseURL;
		private final ImageView imageView;
		private final JTextArea textView;
		private final JSplitPane panel;
		private TextUpdateTask runningTask;
		private boolean updateEPGAlways;
		private BSTreeNode.RootNode shownRootNode;
		private BSTreeNode.BouquetNode shownBouquetNode;
		private BSTreeNode.StationNode shownStationNode;
		
		ValuePanel(Supplier<String> getBaseURL) {
			this.getBaseURL = getBaseURL;
			
			runningTask = null;
			shownRootNode = null;
			shownBouquetNode = null;
			shownStationNode = null;
			
			imageView = new ImageView(400,500);
			imageView.setBgColor(Color.BLACK);
			imageView.reset();
			
			textView = new JTextArea();
			textView.setEditable(false);
			
			JScrollPane textViewScrollPane = new JScrollPane(textView);
			textViewScrollPane.setPreferredSize(new Dimension(400,500));
			
			panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,imageView,textViewScrollPane);
			panel.setPreferredSize(new Dimension(300,500));
			
			ContextMenu textViewContextMenu = new ContextMenu();
			textViewContextMenu.addTo(textView);
			
			JMenuItem miUpdateEPG;
			JCheckBoxMenuItem miUpdateEPGAlways;
			
			updateEPGAlways = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateEPGAlways, false);
			textViewContextMenu.add(miUpdateEPGAlways = OpenWebifController.createCheckBoxMenuItem("Update EPG everytime on Select", updateEPGAlways, isChecked->{
				OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateEPGAlways, updateEPGAlways = isChecked);
			}) );
			
			textViewContextMenu.add(miUpdateEPG = OpenWebifController.createMenuItem("Update EPG Now", e->{
				if (shownStationNode != null)
					startEpgUpdate(shownStationNode, this.getBaseURL.get());
			}) );
			
			textViewContextMenu.addSeparator();
			
			boolean textViewLineWrap = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_TextViewLineWrap, false);
			textView.setLineWrap(textViewLineWrap);
			textView.setWrapStyleWord(textViewLineWrap);
			textViewContextMenu.add(OpenWebifController.createCheckBoxMenuItem("Line Wrap", textViewLineWrap, isChecked->{
				textView.setLineWrap(isChecked);
				textView.setWrapStyleWord(isChecked);
				OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_TextViewLineWrap, isChecked);
			}) );
			
			textViewContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
				miUpdateEPG      .setEnabled(shownStationNode != null);
				miUpdateEPGAlways.setEnabled(shownStationNode != null);
			});

		}
		
		private void showValues(TreePath selectedTreePath) {
			clearTextUpdateTask();
			shownRootNode = null;
			shownBouquetNode = null;
			shownStationNode = null;
			
			if (selectedTreePath != null) {
				Object obj = selectedTreePath.getLastPathComponent();
				
				if (obj instanceof BSTreeNode.RootNode) {
					shownRootNode = (BSTreeNode.RootNode) obj;
					
					imageView.setImage(null);
					imageView.reset();
					
					textView.setText(generateOutput(shownRootNode));
					return;
				}
				
				if (obj instanceof BSTreeNode.BouquetNode) {
					shownBouquetNode = (BSTreeNode.BouquetNode) obj;
					
					imageView.setImage(null);
					imageView.reset();
					
					textView.setText(generateOutput(shownBouquetNode));
					return;
				}
				
				if (obj instanceof BSTreeNode.StationNode) {
					shownStationNode = (BSTreeNode.StationNode) obj;
					
					imageView.setImage(shownStationNode.piconImage);
					imageView.reset();
					
					textView.setText(generateOutput(shownStationNode));
					if (shownStationNode.epgEvents==null || updateEPGAlways)
						startEpgUpdate(shownStationNode, getBaseURL.get());
					
					return;
				}
			}
			
			imageView.setImage(null);
			imageView.reset();
			textView.setText("");
		}

		private String generateOutput(BSTreeNode.RootNode rootNode) {
			ValueListOutput out = new ValueListOutput();
			out.add(0, "Bouquets", rootNode.bouquetData.bouquets.size());
			
			String output = out.generateOutput();
			return output;
		}

		private String generateOutput(BSTreeNode.BouquetNode bouquetNode) {
			ValueListOutput out = new ValueListOutput();
			out.add(0, "Name"             , bouquetNode.bouquet.name);
			out.add(0, "Service Reference", bouquetNode.bouquet.servicereference);
			out.add(0, "SubServices"      , bouquetNode.bouquet.subservices.size());
			
			String output = out.generateOutput();
			return output;
		}

		private String generateOutput(BSTreeNode.StationNode stationNode) {
			ValueListOutput out = new ValueListOutput();
			out.add(0, "Name"             , stationNode.subservice.name);
			out.add(0, "Position"         , stationNode.subservice.pos);
			out.add(0, "Program"          , stationNode.subservice.program);
			out.add(0, "Service Reference", stationNode.subservice.servicereference);
			
			if (stationNode.piconImage==null)
				out.add(0, "Picon Image", "%s", "none");
			else
				out.add(0, "Picon Image", "%d x %d", stationNode.piconImage.getWidth(), stationNode.piconImage.getHeight());
			
			if (stationNode.icon==null)
				out.add(0, "Icon", "%s", "none");
			else
				out.add(0, "Icon", "%d x %d", stationNode.icon.getIconWidth(), stationNode.icon.getIconHeight());
			
			if (stationNode.isServicePlayable==null)
				out.add(0, "Is Playable", "%s", "undefined");
			else
				out.add(0, "Is Playable", stationNode.isServicePlayable);
			
			String output = out.generateOutput();
			
			if (stationNode.epgEvents!=null) {
				output += "\r\n";
				out.clear();
				out.add(0, "Current EGP Event");
				if (stationNode.epgEvents.isEmpty())
					out.add(1, "No Events");
				else {
					int level = stationNode.epgEvents.size()==1 ? 1 : 2;
					for (int i=0; i<stationNode.epgEvents.size(); i++) {
						OpenWebifTools.EPGevent event = stationNode.epgEvents.get(i);
						if (level==2) out.add(1, String.format("Event[%d]", i+1));
						out.add(level, "Station"   , event.station_name);
						out.add(level, "SRef"      , event.sref);
						out.add(level, "Title"     , event.title);
						out.add(level, "Genre"     , "[%d] \"%s\"", event.genreid, event.genre);
						out.add(level, "ID"        , event.id);
						out.add(level, "Begin"     , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(event.begin_timestamp*1000, true, true, false, true, false) );
						out.add(level, "Now"       , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(event.now_timestamp  *1000, true, true, false, true, false) );
						out.add(level, "Duration"  , "%s", DateTimeFormatter.getDurationStr(event.duration_sec));
						out.add(level, "Remaining" , "%s", DateTimeFormatter.getDurationStr(event.remaining));
						out.add(level, "Description");
						out.add(level+1, "", event.shortdesc);
						out.add(level+1, "", event.longdesc );
					}
				}
				
				output += out.generateOutput();
			}
			
			return output;
		}
		
		private synchronized void clearTextUpdateTask() {
			runningTask = null;
		}
		
		private synchronized void startEpgUpdate(BSTreeNode.StationNode stationNode, String baseURL) {
			runningTask = new TextUpdateTask(()->{
				stationNode.updateEPG(baseURL);
				return generateOutput(stationNode);
			});
			runningTask.start();
		}

		private synchronized void setUpdateResult(String str, TextUpdateTask textUpdateTask) {
			if (runningTask==textUpdateTask) {
				textView.setText(str);
				clearTextUpdateTask();
			}
		}

		private class TextUpdateTask {
			
			private final Supplier<String> generateText;

			TextUpdateTask(Supplier<String> generateText) {
				this.generateText = generateText;
			}
			
			void start() {
				new Thread(()->{
					String str = generateText.get();
					setUpdateResult(str, this);
				}).start();
			}
		}
	}

	private static class PiconLoader {

		private final ArrayDeque<Task> solvedTasks = new ArrayDeque<>();
		private final ArrayDeque<Task> tasks = new ArrayDeque<>();
		private DefaultTreeModel treeModel = null;
		private Thread taskThread = null;
		private String baseURL = null;
		private final PiconCache piconCache = new PiconCache();
		private JLabel statusLine = null;

		private synchronized void startTasks() {
			if (taskThread!=null)
				return;
			taskThread = new Thread(()->{
				System.out.println("PiconLoader.start");
				while (performTask());
				System.out.println("PiconLoader.end");
				SwingUtilities.invokeLater(()->statusLine.setText(" "));
			});
			taskThread.start();
		}

		private synchronized void updateStatus() {
			String msg = String.format("Picon Loader : %d pending tasks, %d tree nodes to update, %d picons cached", tasks.size(), solvedTasks.size(), piconCache.size());
			SwingUtilities.invokeLater(()->statusLine.setText(msg));
		}

		private boolean performTask() {
			updateStatus();
			
			Task task = null;
			DefaultTreeModel localTreeModel = null;
			synchronized (this) {
				if (treeModel!=null && !solvedTasks.isEmpty()) {
					task = solvedTasks.pollFirst();
					localTreeModel = treeModel;
				}
			}
			if (task!=null) {
				task.updateTreeNode(localTreeModel);
				return true;
			}
			
			String localBaseURL = null;
			synchronized (this) {
				if (baseURL!=null && !tasks.isEmpty()) {
					task = tasks.pollFirst();
					localBaseURL = baseURL;
				}
				
			}
			if (task!=null) {
				task.readImage(localBaseURL, piconCache);
				synchronized (this) { if (treeModel!=null) localTreeModel = treeModel; }
				
				if (localTreeModel!=null)
					task.updateTreeNode(treeModel);
				else
					synchronized (this) { solvedTasks.addLast(task); }
				return true;
			}
			
			boolean isEverythingDone = false;
			synchronized (this) {
				isEverythingDone = solvedTasks.isEmpty() && tasks.isEmpty();
				if (isEverythingDone)
					taskThread = null;
			}
			
			return !isEverythingDone;
		}

		synchronized void clear() {
			treeModel = null;
			baseURL = null;
			tasks.clear();
			solvedTasks.clear();
			clearPiconCache();
		}

		synchronized void clearPiconCache() {
			piconCache.clear();
		}

		synchronized void setStatusOutput(JLabel statusLine) {
			this.statusLine = statusLine;
		}

		synchronized void setBaseURL(String baseURL) {
			this.baseURL = baseURL;
		}

		synchronized void addTask(BSTreeNode.StationNode bsTreeNode, StationID stationID) {
			tasks.add(new Task(bsTreeNode, stationID));
			startTasks();
		}

		synchronized void setTreeModel(DefaultTreeModel treeModel) {
			this.treeModel = treeModel;
			startTasks();
		}
		
		@SuppressWarnings("unused")
		private static class PiconCache {
			
			private final HashMap<String,CachedPicon> cache;
			
			PiconCache() {
				cache = new HashMap<>();
			}
			int size() {
				return cache.size();
			}
			void clear() {
				cache.clear();
			}
			CachedPicon get(StationID stationID) {
				return cache.get(stationID.toIDStr());
			}
			void put(StationID stationID, BufferedImage piconImage, Icon icon) {
				cache.put(stationID.toIDStr(), new CachedPicon(piconImage, icon));
			}
			
			private static class CachedPicon {
				final BufferedImage piconImage;
				final Icon icon;
				CachedPicon(BufferedImage piconImage, Icon icon) {
					this.piconImage = piconImage;
					this.icon = icon;
				}
				boolean isEmpty() {
					return piconImage==null && icon==null;
				}
			}
		}
		
		private static class Task {
			private final BSTreeNode.StationNode treeNode;
			private final StationID stationID;
			
			Task(BSTreeNode.StationNode treeNode, StationID stationID) {
				this.treeNode = treeNode;
				this.stationID = stationID;
			}
			
			void readImage(String baseURL, PiconCache piconCache) {
				if (baseURL==null || stationID==null || treeNode==null) return;
				PiconCache.CachedPicon cachedPicon = piconCache.get(stationID);
				if (cachedPicon==null) {
					BufferedImage piconImage = OpenWebifTools.getPicon(baseURL, stationID);
					treeNode.setPicon(piconImage);
					piconCache.put(stationID, treeNode.piconImage, treeNode.icon);
				} else {
					treeNode.setPicon(cachedPicon.piconImage, cachedPicon.icon);
				}
			}
			
			void updateTreeNode(DefaultTreeModel treeModel) {
				SwingUtilities.invokeLater(()->treeModel.nodeChanged(treeNode));
			}
		}
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
	
	private static class BSTreeNode<ParentType extends TreeNode, ChildType extends TreeNode> implements TreeNode {
		
		static final int ROW_HEIGHT = 20;
		final ParentType parent;
		final String name;
		final Vector<ChildType> children;
		Icon icon;
	
		private BSTreeNode(ParentType parent, String name) {
			this.parent = parent;
			this.name = name;
			this.children = new Vector<>();
			icon = null;
		}
		
		public void forEachChild(Consumer<ChildType> action) { for (ChildType child:children) action.accept(child); }
		public void forEachChild(BiConsumer<ChildType,TreePath> action) { forEachChild(null, action); }
		public void forEachChild(TreePath myPath, BiConsumer<ChildType,TreePath> action) {
			TreePath myPath_;
			if (myPath==null) myPath_ = new TreePath(this);
			else              myPath_ = myPath;
			forEachChild(child->action.accept(child, myPath_.pathByAddingChild(child))); 
		}

		@Override public String toString() { return name; }

		@Override public TreeNode getParent() { return parent; }
		@Override public int getChildCount() { return children.size(); }
		@Override public TreeNode getChildAt(int childIndex) { return children.get(childIndex); }
		@Override public int getIndex(TreeNode node) { return children.indexOf(node); }
		@Override public boolean getAllowsChildren() { return true; }
		@Override public boolean isLeaf() { return children.isEmpty(); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children() { return children.elements(); }
		
		static class StationList {
			private final HashMap<String,Vector<StationNode>> stations;
			StationList() { stations = new HashMap<>(); }

			public void add(String idStr, StationNode stationNode) {
				Vector<StationNode> stationsWithID = stations.get(idStr);
				if (stationsWithID==null)
					stations.put(idStr,stationsWithID = new Vector<>());
				stationsWithID.add(stationNode);
			}
		}
		
		static class RootNode extends BSTreeNode<RootNode, BouquetNode> {
			
			final BouquetData bouquetData;
			final StationList stations;

			RootNode(BouquetData bouquetData) {
				super(null, "Bouquets");
				this.bouquetData = bouquetData;
				this.stations = new StationList();
				
				for (Bouquet bouquet:bouquetData.bouquets)
					children.add(new BouquetNode(this,bouquet,stations));
				icon = TreeIcons.Folder.getIcon();
			}
		}
		
		static class BouquetNode extends BSTreeNode<RootNode, StationNode> {
		
			final Bouquet bouquet;

			private BouquetNode(RootNode parent, Bouquet bouquet, StationList stations) {
				super(parent,bouquet.name);
				this.bouquet = bouquet;
				for (Bouquet.SubService subservice:this.bouquet.subservices)
					children.add(new StationNode(this,subservice,stations));
				icon = TreeIcons.Folder.getIcon();
			}
		}
		
		static class StationNode extends BSTreeNode<BouquetNode, StationNode> {
			
			final Bouquet.SubService subservice;
			Vector<EPGevent> epgEvents;
			BufferedImage piconImage;
			Boolean isServicePlayable;
		
			private StationNode(BouquetNode parent, Bouquet.SubService subservice, StationList stations) {
				super(parent, !subservice.isMarker() ? String.format("[%d] %s", subservice.pos, subservice.name) : subservice.name);
				this.subservice = subservice;
				this.epgEvents = null;
				this.isServicePlayable = null;
				//aquirePicon(); // only on demand
				stations.add(getStationID().toIDStr(),this);
			}

			void updateEPG(String baseURL) {
				epgEvents = OpenWebifTools.getCurrentEPGevent(baseURL, getStationID());
			}
			void updatePlayableState(String baseURL) {
				if (isMarker()) return;
				isServicePlayable = OpenWebifTools.getIsServicePlayable(baseURL, getStationID());
			}

			boolean isMarker() {
				return subservice.isMarker();
			}

			@Override public boolean getAllowsChildren() { return subservice==null; }
		
			void aquirePicon() {
				PICON_LOADER.addTask(this,getStationID());
			}

			StationID getStationID() {
				return subservice.service.stationID;
			}
		
			void setPicon(BufferedImage piconImage) {
				BufferedImage scaledImage = scaleImage(piconImage,ROW_HEIGHT,Color.BLACK);
				ImageIcon icon = scaledImage==null ? null : new ImageIcon(scaledImage);
				setPicon(piconImage, icon);
			}
		
			void setPicon(BufferedImage piconImage, Icon icon) {
				this.piconImage = piconImage;
				this.icon = icon;
			}
		
			private BufferedImage scaleImage(BufferedImage img, int newHeight, Color bgColor) {
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
		}
	}
	
}
