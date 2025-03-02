package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.StationData;

class AlreadySeenEventsViewer extends StandardDialog
{
	private static final long serialVersionUID = 6089627717117385916L;
	private static final Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
	
	private static enum TreeIcons {
		Title, Description, Station, TitleWithEpisode, DescriptionWithEpisode,
		;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<TreeIcons> IS = IconSource.createCachedIcons(16, 16, "/images/AlreadySeenEventsViewerTreeIcons.png", TreeIcons.values());
	}
	
	private boolean episodeStringFirst = false;
	private final JTree tree;

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		
		tree = new JTree(AlreadySeenEvents.getInstance().createTreeRoot(this));
		tree.setCellRenderer(new TCR());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		
		new TreeContextMenu(parent, tree);
		
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		
		toolBar.add(OpenWebifController.createButton("Rebuild Tree", true, e -> {
			rebuildTree();
		}));
		
		toolBar.add(OpenWebifController.createCheckBox("Episode Text before Title", episodeStringFirst, val -> {
			episodeStringFirst = val;
			rebuildTree();
		} ));
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(toolBar, BorderLayout.PAGE_END);
		contentPane.add(treeScrollPane, BorderLayout.CENTER);
		
		setPreferredSize(new Dimension(600,800));
		createGUI(
				contentPane,
				//OpenWebifController.createButton("Save Data", true, e->{
				//	AlreadySeenEvents.getInstance().writeToFile();
				//}),
				OpenWebifController.createButton("Close", true, e->{
					closeDialog();
				})
		);
	}

	private void rebuildTree()
	{
		tree.setModel(new DefaultTreeModel(AlreadySeenEvents.getInstance().createTreeRoot(this)));
	}
	
	static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}

	RootTreeNode createTreeRoot(Map<String, EventCriteriaSet> data)
	{
		return new RootTreeNode(data);
	}
	
	String generateTitle(String title, EpisodeInfo episode)
	{	
		if (episode == null || !episode.hasEpisodeStr())
			return title;
		if (episodeStringFirst)
			return String.format("(%s) %s", episode.episodeStr, title);
		else
			return String.format("%s (%s)", title, episode.episodeStr);
	}
	
	private static class TreeContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = -3347262887544423895L;
		
		private TreePath clickedPath;
		private AbstractTreeNode clickedTreeNode;
		private DescriptionTreeNode clickedDescriptionTreeNode;
		private EventCriteriaSetTreeNode clickedEcsTreeNode;
		private EpisodeInfo clickedEpisodeInfo;
		
		TreeContextMenu(Window parent, JTree tree)
		{
			clickedPath = null;
			clickedTreeNode = null;
			clickedDescriptionTreeNode = null;
			clickedEcsTreeNode = null;
			clickedEpisodeInfo = null;
			
			JMenuItem miEditEpisodeStr = add( OpenWebifController.createMenuItem("##", e->{
				if (clickedEpisodeInfo==null)
					return;
				
				String episodeStr = !clickedEpisodeInfo.hasEpisodeStr() ? "" : clickedEpisodeInfo.episodeStr;
				String result = JOptionPane.showInputDialog(parent, "Episode Text", episodeStr);
				if (result!=null)
				{
					clickedEpisodeInfo.episodeStr = result;
					clickedTreeNode.updateTitle();
					tree.repaint();
					AlreadySeenEvents.getInstance().writeToFile();
				}
			}) );
			
			addContextMenuInvokeListener((comp, x, y) -> {
				clickedPath = tree.getPathForLocation(x, y);
				Object lastPathComp = clickedPath.getLastPathComponent();
				
				clickedTreeNode = null;
				clickedDescriptionTreeNode = null;
				clickedEcsTreeNode = null;
				clickedEpisodeInfo = null;
				
				if (lastPathComp instanceof AbstractTreeNode treeNode)
					clickedTreeNode = treeNode;
				
				if (clickedTreeNode instanceof DescriptionTreeNode treeNode)
					clickedDescriptionTreeNode = treeNode;
				
				if (clickedTreeNode instanceof EventCriteriaSetTreeNode treeNode)
					clickedEcsTreeNode = treeNode;
				
				if (clickedDescriptionTreeNode!=null)
					clickedEpisodeInfo = clickedDescriptionTreeNode.episode;
				if (clickedEcsTreeNode!=null && clickedEcsTreeNode.ecs!=null)
					clickedEpisodeInfo = clickedEcsTreeNode.ecs.episode();
				
				miEditEpisodeStr.setEnabled( clickedEpisodeInfo!=null );
				miEditEpisodeStr.setText(
						clickedEpisodeInfo==null || !clickedEpisodeInfo.hasEpisodeStr()
							?    "Add Episode Text"
							: "Change Episode Text"
				);
			});
			
			addTo(tree);
		}
	}

	private static class TCR extends DefaultTreeCellRenderer
	{
		private static final long serialVersionUID = 849724659340192701L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			Component rendcomp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (value instanceof AbstractTreeNode treeNode)
			{
				TreeIcons icon = treeNode.getIcon();
				if (icon!=null)
					setIcon(icon.getIcon());
			}
			
			return rendcomp;
		}
		
	}
	
	private abstract class AbstractTreeNode implements TreeNode
	{
		protected final TreeNode parent;
		protected       String title;
		protected final boolean allowsChildren;
		protected       TreeNode[] children;
		
		AbstractTreeNode(TreeNode parent, String title, boolean allowsChildren)
		{
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			children = null;
		}
		
		protected abstract void determineChildren();
		protected abstract TreeIcons getIcon();
		protected void updateTitle() {}
		
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
	
	class RootTreeNode extends AbstractTreeNode
	{
		private final Map<String, EventCriteriaSet> data;

		RootTreeNode(Map<String, EventCriteriaSet> data)
		{
			super(null, "<>", true);
			this.data = data;
		}
		
		@Override
		protected TreeIcons getIcon()
		{
			return null;
		}

		@Override
		protected void determineChildren()
		{
			children = data.keySet()
					.stream()
					.map(title -> new EventCriteriaSetTreeNode(this, title, data.get(title)))
					.sorted(Comparator.comparing(node -> node.title, stringComparator))
					.toArray(TreeNode[]::new);
		}
	}
	
	private class EventCriteriaSetTreeNode extends AbstractTreeNode
	{
		private final EventCriteriaSet ecs;
		private String ecsTitle;

		EventCriteriaSetTreeNode(RootTreeNode parent, String title, EventCriteriaSet ecs)
		{
			super(parent, generateTitle(title, ecs.episode()), ecs.stations()!=null || ecs.descriptions()!=null);
			ecsTitle = title;
			this.ecs = ecs;
		}

		@Override
		protected void updateTitle()
		{
			title = generateTitle(ecsTitle, ecs.episode());
		}

		@Override
		protected TreeIcons getIcon()
		{
			EpisodeInfo episode = ecs.episode();
			if (episode!=null && episode.hasEpisodeStr())
				return TreeIcons.TitleWithEpisode;
			return TreeIcons.Title;
		}

		@Override
		protected void determineChildren()
		{
			Vector<TreeNode> childrenVec = new Vector<>();
			
			if (ecs.descriptions()!=null)
			{
				Map<String, EpisodeInfo> descriptions = ecs.descriptions();
				childrenVec.addAll( descriptions.keySet()
						.stream()
						.map(description -> new DescriptionTreeNode(this, description, descriptions.get(description)))
						.sorted(Comparator.comparing(node -> node.title, stringComparator))
						.toList()
				);
			}
			
			if (ecs.stations()!=null)
			{
				Map<String, StationData> stations = ecs.stations();
				childrenVec.addAll( stations.keySet()
						.stream()
						.map(station -> new StationTreeNode(this, station, stations.get(station)))
						.sorted(Comparator.comparing(node -> node.title, stringComparator))
						.toList()
				);
			}
			
			children = childrenVec.toArray(TreeNode[]::new);
		}
	}
	
	private class DescriptionTreeNode extends AbstractTreeNode
	{
		private final EpisodeInfo episode;
		private final String description;

		DescriptionTreeNode(TreeNode parent, String description, EpisodeInfo episode)
		{
			super(parent, generateTitle(description, episode), false);
			this.description = description;
			this.episode = episode;
			children = new TreeNode[0];
		}

		@Override
		protected void updateTitle()
		{
			title = generateTitle(description, episode);
		}

		@Override
		protected TreeIcons getIcon()
		{
			if (episode!=null && episode.hasEpisodeStr())
				return TreeIcons.DescriptionWithEpisode;
			return TreeIcons.Description;
		}

		@Override protected void determineChildren()
		{
			throw new IllegalStateException();
		}
	}

	private class StationTreeNode extends AbstractTreeNode
	{
		private final StationData stationData;

		StationTreeNode(EventCriteriaSetTreeNode parent, String station, StationData stationData)
		{
			super(parent, station, stationData.descriptions()!=null);
			this.stationData = stationData;
		}

		@Override
		protected TreeIcons getIcon()
		{
			return TreeIcons.Station;
		}

		@Override
		protected void determineChildren()
		{
			if (stationData.descriptions()!=null)
			{
				Map<String, EpisodeInfo> descriptions = stationData.descriptions();
				children = descriptions.keySet()
						.stream()
						.map(description -> new DescriptionTreeNode(this, description, descriptions.get(description)))
						.sorted(Comparator.comparing(node -> node.title, stringComparator))
						.toArray(TreeNode[]::new);
			}
			else
				children = new TreeNode[0];
		}
	}
}
