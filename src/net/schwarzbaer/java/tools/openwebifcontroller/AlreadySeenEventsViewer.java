package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;

import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.StationData;

class AlreadySeenEventsViewer extends StandardDialog
{
	private static final long serialVersionUID = 6089627717117385916L;
	private static final Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		
		JTree tree = new JTree(AlreadySeenEvents.getInstance().createTreeRoot());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(treeScrollPane, BorderLayout.CENTER);
		
		setPreferredSize(new Dimension(600,800));
		createGUI(
				contentPane,
				OpenWebifController.createButton("Close", true, e->{
					closeDialog();
				})
		);
	}
	
	static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}

	private static abstract class AbstractTreeNode implements TreeNode
	{
		protected final TreeNode parent;
		protected final String title;
		protected final boolean allowsChildren;
		protected TreeNode[] children;
		
		AbstractTreeNode(TreeNode parent, String title, boolean allowsChildren)
		{
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			children = null;
		}
		
		protected abstract void determineChildren();
		
		private void checkChildren()
		{
			if (children == null)
				determineChildren();
		}
		
		@Override public String   toString         () { return title; }
		@Override public TreeNode getParent        () { return parent; }
		@Override public boolean  getAllowsChildren() { return allowsChildren; }
		@Override public boolean  isLeaf           () { checkChildren(); return children.length == 0; }
		@Override public int      getChildCount    () { checkChildren(); return children.length; }
		
		@Override public TreeNode getChildAt(int childIndex)
		{
			checkChildren();
			if (childIndex < 0 || childIndex >= children.length)
				return null;
			return children[childIndex];
		}

		@Override
		public int getIndex(TreeNode node)
		{
			checkChildren();
			for (int i=0; i<children.length; i++)
				if (children[i] == node)
					return i;
			return -1;
		}

		@Override
		public Enumeration<? extends TreeNode> children()
		{
			checkChildren();
			return new Enumeration<>() {
				private int index = 0;
				@Override public boolean hasMoreElements() { return index < children.length; }
				@Override public TreeNode nextElement() { return children[index++]; }
			};
		}
	}
	
	static class RootTreeNode extends AbstractTreeNode
	{
		private final Map<String, EventCriteriaSet> data;

		RootTreeNode(Map<String, EventCriteriaSet> data)
		{
			super(null, "<>", true);
			this.data = data;
		}

		@Override
		protected void determineChildren()
		{
			Vector<String> titles = new Vector<>(data.keySet());
			titles.sort(stringComparator);
			
			children = titles
					.stream()
					.map(title -> new EventCriteriaSetTreeNode(this, title, data.get(title)))
					.toArray(TreeNode[]::new);
		}
	}
	
	private static class EventCriteriaSetTreeNode extends AbstractTreeNode
	{
		private final EventCriteriaSet ecs;

		EventCriteriaSetTreeNode(RootTreeNode parent, String title, EventCriteriaSet ecs)
		{
			super(parent, title, ecs.stations()!=null || ecs.descriptions()!=null);
			this.ecs = ecs;
		}

		@Override
		protected void determineChildren()
		{
			Vector<TreeNode> childrenVec = new Vector<>();
			
			if (ecs.descriptions()!=null)
			{
				Vector<String> descriptions = new Vector<>(ecs.descriptions());
				descriptions.sort(stringComparator);
				childrenVec.addAll( descriptions
						.stream()
						.map(description -> new DescriptionTreeNode(this, description))
						.toList()
				);
			}
			
			if (ecs.stations()!=null)
			{
				Vector<String> stations = new Vector<>(ecs.stations().keySet());
				stations.sort(stringComparator);
				childrenVec.addAll( stations
						.stream()
						.map(station -> new StationTreeNode(this, station, ecs.stations().get(station)))
						.toList()
				);
			}
			
			children = childrenVec.toArray(TreeNode[]::new);
		}
	}
	
	private static class DescriptionTreeNode extends AbstractTreeNode
	{
		DescriptionTreeNode(TreeNode parent, String description)
		{
			super(parent, String.format("Desc.: %s", description), false);
			children = new TreeNode[0];
		}

		@Override protected void determineChildren()
		{
			throw new IllegalStateException();
		}
	}

	private static class StationTreeNode extends AbstractTreeNode
	{
		private final StationData stationData;

		StationTreeNode(EventCriteriaSetTreeNode parent, String station, StationData stationData)
		{
			super(parent, String.format("Station: %s", station), stationData.descriptions()!=null);
			this.stationData = stationData;
		}

		@Override
		protected void determineChildren()
		{
			if (stationData.descriptions()!=null)
			{
				Vector<String> descriptions = new Vector<>(stationData.descriptions());
				descriptions.sort(stringComparator);
				children = descriptions
						.stream()
						.map(description -> new DescriptionTreeNode(this, description))
						.toArray(TreeNode[]::new);
			}
			else
				children = new TreeNode[0];
		}
	}
}
