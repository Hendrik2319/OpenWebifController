package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.StationID;

class BouquetsNStations extends JPanel {
	private static final long serialVersionUID = 1873358104402086477L;
	private static final PiconLoader PICON_LOADER = new PiconLoader();
	
	@SuppressWarnings("unused")
	private final OpenWebifController main;
	private final JTree bsTree;
	private final JLabel statusLine;
	
	private BSTreeNode bsTreeRoot;
	private DefaultTreeModel bsTreeModel;

	BouquetsNStations(OpenWebifController main) {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		this.main = main;
		bsTreeRoot = null;
		bsTreeModel = null;
		PICON_LOADER.clear();
		
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

		private boolean performTask() {
			synchronized (this) {
				String msg = String.format("Picon Loader : %d pending tasks, %d tree nodes to update, %d picons cached", tasks.size(), solvedTasks.size(), piconCache.size());
				SwingUtilities.invokeLater(()->statusLine.setText(msg));
			}
			
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
			piconCache.clear();
		}

		synchronized void setStatusOutput(JLabel statusLine) {
			this.statusLine = statusLine;
		}

		synchronized void setBaseURL(String baseURL) {
			this.baseURL = baseURL;
		}

		synchronized void addTask(BSTreeNode bsTreeNode, StationID stationID) {
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
			void put(StationID stationID, BufferedImage piconImage, ImageIcon icon) {
				cache.put(stationID.toIDStr(), new CachedPicon(piconImage, icon));
			}
			
			private static class CachedPicon {
				final BufferedImage piconImage;
				final ImageIcon icon;
				CachedPicon(BufferedImage piconImage, ImageIcon icon) {
					this.piconImage = piconImage;
					this.icon = icon;
				}
				boolean isEmpty() {
					return piconImage==null && icon==null;
				}
			}
		}
		
		private static class Task {
			private final BSTreeNode bsTreeNode;
			private final StationID stationID;
			
			Task(BSTreeNode bsTreeNode, StationID stationID) {
				this.bsTreeNode = bsTreeNode;
				this.stationID = stationID;
			}
			
			void readImage(String baseURL, PiconCache piconCache) {
				if (baseURL==null || stationID==null || bsTreeNode==null) return;
				PiconCache.CachedPicon cachedPicon = piconCache.get(stationID);
				if (cachedPicon==null) {
					BufferedImage piconImage = OpenWebifTools.getPicon(baseURL, stationID);
					bsTreeNode.setPicon(piconImage);
					piconCache.put(stationID, bsTreeNode.piconImage, bsTreeNode.icon);
				} else {
					bsTreeNode.setPicon(cachedPicon.piconImage, cachedPicon.icon);
				}
			}
			
			void updateTreeNode(DefaultTreeModel treeModel) {
				SwingUtilities.invokeLater(()->treeModel.nodeChanged(bsTreeNode));
			}
		}
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
			bsTreeRoot = BSTreeNode.create(bouquetData);
			bsTreeModel = new DefaultTreeModel(bsTreeRoot, true);
			bsTree.setModel(bsTreeModel);
			PICON_LOADER.setTreeModel(bsTreeModel);
			if (bsTreeRoot!=null) {
//				selectedTreePath = locationsRoot.getTreePath(movieList.directory);
//				selectedTreeNode = null;
//				locationsTree.expandPath(selectedTreePath);
//				if (selectedTreePath!=null) {
//					Object obj = selectedTreePath.getLastPathComponent();
//					if (obj instanceof LocationTreeNode)
//						selectedTreeNode = (LocationTreeNode) obj;
//				}
//				//locationsTree.setSelectionPath(treePath);
//				updateMovieTableModel(movieList.movies);
			}
		}
	}
	
	private static class BSTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 8843157059053309466L;

		@Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (value instanceof BSTreeNode) {
				BSTreeNode treeNode = (BSTreeNode) value;
				setIcon(treeNode.icon);
			}
			
			return comp;
		}
		
	}
	
	private static class BSTreeNode implements TreeNode {

		public static BSTreeNode create(BouquetData bouquetData) {
			if (bouquetData==null) return null;
			return new BSTreeNode(bouquetData.bouquets);
		}
		
		private final BSTreeNode parent;
		private final String name;
		private final Vector<BSTreeNode> children;
		private final Bouquet.SubService subservice;
		private BufferedImage piconImage;
		private ImageIcon icon;
	
		private BSTreeNode(BSTreeNode parent, String name, Bouquet.SubService subservice) {
			this.parent = parent;
			this.name = name;
			this.subservice = subservice;
			this.children = new Vector<>();
			piconImage = null;
		}
		
		private BSTreeNode(Vector<Bouquet> bouquets) {
			this(null, "Bouquets", null);
			for (Bouquet bouquet:bouquets)
				children.add(new BSTreeNode(this,bouquet));
		}

		private BSTreeNode(BSTreeNode parent, Bouquet bouquet) {
			this(parent,bouquet.name, null);
			for (Bouquet.SubService subservice:bouquet.subservices)
				children.add(new BSTreeNode(this,subservice));
		}

		private BSTreeNode(BSTreeNode parent, Bouquet.SubService subservice) {
			this(parent,!subservice.isMarker() ? String.format("[%d] %s", subservice.pos, subservice.name) : subservice.name, subservice);
			//aquirePicon(); // only on demand
		}

		@SuppressWarnings("unused")
		private void aquirePicon() {
			PICON_LOADER.addTask(this,subservice.service.stationID);
		}

		private void setPicon(BufferedImage piconImage) {
			BufferedImage picon16 = scaleImage(piconImage,16,Color.BLACK);
			ImageIcon icon = picon16==null ? null : new ImageIcon(picon16);
			setPicon(piconImage, icon);
		}

		private void setPicon(BufferedImage piconImage, ImageIcon icon) {
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

		@Override public String toString() { return name; }

		@Override public TreeNode getParent() { return parent; }
		@Override public int getChildCount() { return children.size(); }
		@Override public TreeNode getChildAt(int childIndex) { return children.get(childIndex); }
		@Override public int getIndex(TreeNode node) { return children.indexOf(node); }
		@Override public boolean getAllowsChildren() { return subservice==null; }
		@Override public boolean isLeaf() { return children.isEmpty(); }
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children() { return children.elements(); }
	}
	
}
