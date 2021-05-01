package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.TreeIcons;

class BouquetsNStations extends JPanel {
	private static final long serialVersionUID = 1873358104402086477L;
	private static final PiconLoader PICON_LOADER = new PiconLoader();
	
	private final OpenWebifController main;
	private final JTree bsTree;
	private final JLabel statusLine;
	
	private BSTreeNode.RootNode bsTreeRoot;
	private DefaultTreeModel bsTreeModel;
	private TreePath clickedTreePath;
	@SuppressWarnings("unused")
	private BSTreeNode.RootNode    clickedRootNode;
	private BSTreeNode.BouquetNode clickedBouquetNode;
	private BSTreeNode.StationNode clickedStationNode;

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
		
		bsTree = new JTree(bsTreeModel);
		bsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		bsTree.setCellRenderer(new BSTreeCellRenderer());
		
		JScrollPane treeScrollPane = new JScrollPane(bsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		JPanel rightPanel = new JPanel(new BorderLayout(3,3));
		rightPanel.setPreferredSize(new Dimension(300,500));
		
		statusLine = new JLabel();
		statusLine.setBorder(BorderFactory.createEtchedBorder());
		PICON_LOADER.setStatusOutput(statusLine);
		
		JSplitPane centerPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,treeScrollPane,rightPanel);
		add(centerPanel,BorderLayout.CENTER);
		add(statusLine,BorderLayout.SOUTH);
		
		JMenuItem miLoadPicons,miSwitchToStation,miStreamStation;
		ContextMenu treeContextMenu = new ContextMenu();
		treeContextMenu.add(miLoadPicons = OpenWebifController.createMenuItem("Load Picons", e->{
			if (clickedBouquetNode!=null) {
				PICON_LOADER.setBaseURL(main.getBaseURL());
				clickedBouquetNode.children.forEach(BSTreeNode.StationNode::aquirePicon);
			} else if (clickedStationNode!=null) {
				PICON_LOADER.setBaseURL(main.getBaseURL());
				clickedStationNode.aquirePicon();
			}
		}));
		treeContextMenu.add(miSwitchToStation = OpenWebifController.createMenuItem("Switch To Station", e->{
			// TODO
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
			miLoadPicons.setText(clickedBouquetNode!=null ? "Load Picons of Stations" : clickedStationNode!=null ? String.format("Load Picon of \"%s\"", clickedStationNode.subservice.name) : "Load Picons");
			
			miSwitchToStation.setEnabled(clickedStationNode!=null);
			miStreamStation  .setEnabled(clickedStationNode!=null);
			miSwitchToStation.setText(clickedStationNode!=null ? String.format("Switch To \"%s\"", clickedStationNode.subservice.name) : "Switch To Station");
			miStreamStation  .setText(clickedStationNode!=null ? String.format("Stream \"%s\""   , clickedStationNode.subservice.name) : "Stream Station"   );
		});
	}
	
	void readData(String baseURL, ProgressDialog pd) {
		if (baseURL==null) return;
		
		BouquetData bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle->{
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle(taskTitle);
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

	private void streamStation(StationID stationID) {
		if (stationID==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		File videoPlayer = main.getVideoPlayer();
		if (videoPlayer==null) return;
		
		File javaVM = main.getJavaVM();
		if (javaVM==null) return;
		
		String url = OpenWebifTools.getStationStreamURL(baseURL, stationID);
		
		System.out.printf("stream station: %s%n", stationID.toIDStr());
		System.out.printf("   java VM      : \"%s\"%n", javaVM.getAbsolutePath());
		System.out.printf("   video player : \"%s\"%n", videoPlayer.getAbsolutePath());
		System.out.printf("   url          : \"%s\"%n", url);
		
		try { Runtime.getRuntime().exec(new String[] {javaVM.getAbsolutePath(), "-jar", "OpenWebifController.jar", "-start", videoPlayer.getAbsolutePath(), url }); }
		catch (IOException ex) { System.err.printf("IOException while starting video player: %s%n", ex.getMessage()); }
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

		@Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (value instanceof BSTreeNode) {
				BSTreeNode<?,?> treeNode = (BSTreeNode<?,?>) value;
				setIcon(treeNode.icon);
			}
			
			return comp;
		}
		
	}
	
	private static class BSTreeNode<ParentType extends TreeNode, ChildType extends TreeNode> implements TreeNode {
		
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

		@Override public String toString() { return name; }

		@Override public TreeNode getParent() { return parent; }
		@Override public int getChildCount() { return children.size(); }
		@Override public TreeNode getChildAt(int childIndex) { return children.get(childIndex); }
		@Override public int getIndex(TreeNode node) { return children.indexOf(node); }
		@Override public boolean getAllowsChildren() { return true; }
		@Override public boolean isLeaf() { return children.isEmpty(); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children() { return children.elements(); }
		
		private static class RootNode extends BSTreeNode<RootNode, BouquetNode> {
			
			@SuppressWarnings("unused")
			final BouquetData bouquetData;

			RootNode(BouquetData bouquetData) {
				super(null, "Bouquets");
				this.bouquetData = bouquetData;
				for (Bouquet bouquet:bouquetData.bouquets)
					children.add(new BouquetNode(this,bouquet));
				icon = TreeIcons.Folder.getIcon();
			}
		}
		
		private static class BouquetNode extends BSTreeNode<RootNode, StationNode> {
		
			final Bouquet bouquet;

			private BouquetNode(RootNode parent, Bouquet bouquet) {
				super(parent,bouquet.name);
				this.bouquet = bouquet;
				for (Bouquet.SubService subservice:this.bouquet.subservices)
					children.add(new StationNode(this,subservice));
				icon = TreeIcons.Folder.getIcon();
			}
		}
		
		private static class StationNode extends BSTreeNode<BouquetNode, StationNode> {
			
			final Bouquet.SubService subservice;
			BufferedImage piconImage;
		
			private StationNode(BouquetNode parent, Bouquet.SubService subservice) {
				super(parent, !subservice.isMarker() ? String.format("[%d] %s", subservice.pos, subservice.name) : subservice.name);
				this.subservice = subservice;
				//aquirePicon(); // only on demand
			}

			@Override public boolean getAllowsChildren() { return subservice==null; }
		
			private void aquirePicon() {
				PICON_LOADER.addTask(this,getStationID());
			}

			StationID getStationID() {
				return subservice.service.stationID;
			}
		
			private void setPicon(BufferedImage piconImage) {
				BufferedImage picon16 = scaleImage(piconImage,16,Color.BLACK);
				ImageIcon icon = picon16==null ? null : new ImageIcon(picon16);
				setPicon(piconImage, icon);
			}
		
			private void setPicon(BufferedImage piconImage, Icon icon) {
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
