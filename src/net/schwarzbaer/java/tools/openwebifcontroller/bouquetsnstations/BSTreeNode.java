package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	
	public TreePath getPath() {
		return getPath(this);
	}
	
	public static TreePath getPath(TreeNode treeNode) {
		if (treeNode == null)
			return null;
		if (treeNode.getParent() == null)
			return new TreePath(treeNode);
		return getPath(treeNode.getParent()).pathByAddingChild(treeNode);
	}
	
	public void forEachChild(Consumer<ChildType> action) {
		for (ChildType child:children) action.accept(child);
	}
	public void forEachChild(BiConsumer<ChildType,TreePath> action) {
		TreePath thisPath = getPath();
		forEachChild(child->action.accept(child, thisPath.pathByAddingChild(child))); 
	}

	@Override public String toString() { return name; }

	@Override public ParentType getParent() { return parent; }
	@Override public int getChildCount() { return children.size(); }
	@Override public ChildType getChildAt(int childIndex) { return children.get(childIndex); }
	@Override public int getIndex(TreeNode node) { return children.indexOf(node); }
	@Override public boolean getAllowsChildren() { return true; }
	@Override public boolean isLeaf() { return children.isEmpty(); }
	@Override public Enumeration<ChildType> children() { return children.elements(); }
	
	static class StationList {
		private final HashMap<String,Vector<StationNode>> stations;
		StationList() { stations = new HashMap<>(); }

		StationNode add(StationNode stationNode) {
			StationID stationID = stationNode.getStationID();
			Vector<StationNode> stationsWithID = get(stationID);
			if (stationsWithID==null)
				stations.put(stationID.toIDStr(),stationsWithID = new Vector<>());
			stationsWithID.add(stationNode);
			return stationNode;
		}
		
		public boolean remove(StationNode stationNode)
		{
			StationID stationID = stationNode.getStationID();
			Vector<StationNode> stationsWithID = get(stationID);
			if (stationsWithID == null) return false;
			return stationsWithID.remove(stationNode);
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
	
	static class BouquetNode extends BSTreeNode<RootNode, TreeNode>
	{
		enum SubNodeStructureType { PlainStationList, GroupedByTransponder }
		
		final Bouquet bouquet;
		private final StationList stations;
		SubNodeStructureType subNodeStructureType;

		private BouquetNode(RootNode parent, Bouquet bouquet, StationList stations) {
			super(parent,bouquet.name);
			this.bouquet = bouquet;
			this.stations = stations;
			icon = TreeIcons.Folder.getIcon();
			subNodeStructureType = SubNodeStructureType.PlainStationList;
			createChildren();
		}

		private void createChildren()
		{
			children.clear();
			switch (subNodeStructureType)
			{
			case GroupedByTransponder:
				List<String> transponderIDStrs = new ArrayList<>();
				Map<String,List<Bouquet.SubService>> groupedServices = new HashMap<>();
				
				for (Bouquet.SubService subservice : bouquet.subservices)
				{
					if (subservice.isMarker()) continue;
					Integer transponderID = StationID.getTransponderID(subservice.getStationID());
					String transponderIDStr =
							transponderID==null
								? "<null>"
								: "%X".formatted(transponderID);
					
					groupedServices
						.computeIfAbsent(transponderIDStr, str -> {
							transponderIDStrs.add(str);
							return new ArrayList<>();
						})
						.add(subservice);
				}
				for (String transponderIDStr : transponderIDStrs)
				{
					List<Bouquet.SubService> transponderSubServices = groupedServices.get(transponderIDStr);
					TransponderNode transponderNode = new TransponderNode(this, "Transponder %s   (%d stations)".formatted(transponderIDStr, transponderSubServices.size()));
					transponderNode.children.addAll(
							transponderSubServices
								.stream()
								.map(subservice -> new StationNode(transponderNode, subservice))
								.toList()
					);
					children.add(transponderNode);
				}
				break;
				
			case PlainStationList:
				for (Bouquet.SubService subservice : bouquet.subservices)
					children.add( stations.add( new StationNode(this, subservice) ) );
				break;
			}
		}
		
		void forEachStation(Consumer<StationNode> action)
		{
			switch (subNodeStructureType)
			{
			case GroupedByTransponder:
				for (TreeNode childNode : children)
					if (childNode instanceof TransponderNode transponderNode)
						transponderNode.children.forEach(action);
				break;
				
			case PlainStationList:
				for (TreeNode childNode : children)
					if (childNode instanceof StationNode stationNode)
						action.accept(stationNode);
				break;
			}
		}

		boolean isSubNodeStructureType(SubNodeStructureType subNodeStructureType)
		{
			return this.subNodeStructureType == subNodeStructureType;
		}

		void changeSubNodeStructure(SubNodeStructureType subNodeStructureType)
		{
			this.subNodeStructureType = subNodeStructureType;
			
			for (TreeNode childNode : children)
			{
				if (childNode instanceof TransponderNode transponderNode)
					transponderNode.children.forEach(stations::remove);
				
				if (childNode instanceof StationNode stationNode)
					stations.remove(stationNode);
			}
			
			createChildren();
		}
	}
	
	static class TransponderNode extends BSTreeNode<BouquetNode, StationNode>
	{
		private TransponderNode(BouquetNode parent, String name)
		{
			super(parent, name);
			icon = TreeIcons.Folder.getIcon();
		}
	}
	
	static class StationNode extends BSTreeNode<TreeNode, StationNode> {
		
		final Bouquet.SubService subservice;
		final BouquetNode bouquetNode;
		Vector<EPGevent> epgEvents;
		BufferedImage piconImage;
		Boolean isServicePlayable;
		boolean isCurrentlyPlayed;
	
		private StationNode(TransponderNode parent, Bouquet.SubService subservice) {
			this(parent, parent.parent, subservice);
		}
		private StationNode(BouquetNode parent, Bouquet.SubService subservice) {
			this(parent, parent, subservice);
		}
		private StationNode(TreeNode parent, BouquetNode bouquetNode, Bouquet.SubService subservice) {
			super(parent, !subservice.isMarker() ? String.format("[%d] %s", subservice.pos, subservice.name) : subservice.name);
			this.bouquetNode = bouquetNode;
			this.subservice = subservice;
			this.epgEvents = null;
			this.isServicePlayable = null;
			this.isCurrentlyPlayed = false;
			//aquirePicon(); // only on demand
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
			return subservice.getStationID();
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