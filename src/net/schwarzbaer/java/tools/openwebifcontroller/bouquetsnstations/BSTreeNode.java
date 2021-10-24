package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.TreeIcons;

class BSTreeNode<ParentType extends TreeNode, ChildType extends TreeNode> implements TreeNode {
	
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
	@Override public Enumeration<ChildType> children() { return children.elements(); }
	
	static class StationList {
		private final HashMap<String,Vector<StationNode>> stations;
		StationList() { stations = new HashMap<>(); }

		public void add(StationID stationID, StationNode stationNode) {
			Vector<StationNode> stationsWithID = get(stationID);
			if (stationsWithID==null)
				stations.put(stationID.toIDStr(),stationsWithID = new Vector<>());
			stationsWithID.add(stationNode);
		}

		public Vector<StationNode> get(StationID stationID) {
			return stations.get(stationID.toIDStr());
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

		void updateStationNodes(StationID stationID, DefaultTreeModel treeModel, Consumer<Vector<BSTreeNode.StationNode>> doWithStationNodes) {
			Vector<StationNode> stations = this.stations.get(stationID);
			if (stations==null || stations.isEmpty())
				return;
			
			if (doWithStationNodes!=null)
				doWithStationNodes.accept(stations);
			
			if (treeModel!=null)
				SwingUtilities.invokeLater(()->{
					for (BSTreeNode.StationNode treeNode:stations)
						treeModel.nodeChanged(treeNode);
				});
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
		boolean isCurrentlyPlayed;
	
		private StationNode(BouquetNode parent, Bouquet.SubService subservice, StationList stations) {
			super(parent, !subservice.isMarker() ? String.format("[%d] %s", subservice.pos, subservice.name) : subservice.name);
			this.subservice = subservice;
			this.epgEvents = null;
			this.isServicePlayable = null;
			this.isCurrentlyPlayed = false;
			//aquirePicon(); // only on demand
			stations.add(getStationID(),this);
		}

		void updateEPG(String baseURL) {
			epgEvents = OpenWebifTools.getCurrentEPGevent(baseURL, getStationID(), null);
		}
		void updatePlayableState(String baseURL) {
			if (isMarker()) return;
			isServicePlayable = OpenWebifTools.getIsServicePlayable(baseURL, getStationID(), null);
		}

		boolean isMarker() {
			return subservice.isMarker();
		}

		@Override public boolean getAllowsChildren() { return subservice==null; }

		StationID getStationID() {
			return subservice.service.stationID;
		}
	
		void setPicon(BufferedImage piconImage) {
			setPicon(piconImage, getIcon(piconImage));
		}

		void setPicon(BufferedImage piconImage, Icon icon) {
			this.piconImage = piconImage;
			this.icon = icon;
		}
	
		static Icon getIcon(BufferedImage piconImage) {
			return BouquetsNStations.getScaleIcon(piconImage, ROW_HEIGHT, Color.BLACK);
		}
	}
}