package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.StationData;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.VariableECSData;

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
	private CustomTreeModel treeModel;

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		
		treeModel = new CustomTreeModel(AlreadySeenEvents.getInstance().createTreeRoot(this));
		tree = new JTree(treeModel);
		tree.setCellRenderer(new TCR());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		
		new TreeContextMenu(parent);
		
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
		tree.setModel(treeModel = new CustomTreeModel(AlreadySeenEvents.getInstance().createTreeRoot(this)));
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
	
	private class TreeContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = -3347262887544423895L;
		
		private final Window parent;
		private final JMenu menuMoveToGroup;
		
		private TreePath                 clickedPath;
		private AbstractTreeNode         clickedTreeNode;
		private ECSGroupTreeNode         clickedGroupTreeNode;
		private EventCriteriaSetTreeNode clickedEcsTreeNode;
		private StationTreeNode          clickedStationTreeNode;
		private DescriptionTreeNode      clickedDescriptionTreeNode;
		private EpisodeInfo              clickedEpisodeInfo;
		
		TreeContextMenu(Window parent)
		{
			this.parent = parent;
			
			clickedPath                = null;
			clickedTreeNode            = null;
			clickedGroupTreeNode       = null;
			clickedEcsTreeNode         = null;
			clickedStationTreeNode     = null;
			clickedDescriptionTreeNode = null;
			clickedEpisodeInfo         = null;
			
			JMenuItem miCopyStr = add( OpenWebifController.createMenuItem( "##", e->{
				if (clickedGroupTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clickedGroupTreeNode.groupName);
				
				if (clickedEcsTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clickedEcsTreeNode.ecs.title());
				
				if (clickedStationTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clickedStationTreeNode.station);
				
				if (clickedDescriptionTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clickedDescriptionTreeNode.description);
			} ) );
			
			JMenuItem miEditEpisodeStr = add( OpenWebifController.createMenuItem( "##", e->{
				if (clickedEpisodeInfo==null)
					return;
				
				String episodeStr = !clickedEpisodeInfo.hasEpisodeStr() ? "" : clickedEpisodeInfo.episodeStr;
				String result = JOptionPane.showInputDialog(parent, "Episode Text", episodeStr);
				if (result!=null)
				{
					clickedEpisodeInfo.episodeStr = result;
					clickedTreeNode.updateTitle();
					treeModel.fireTreeNodeUpdate(clickedTreeNode);
					tree.repaint();
					AlreadySeenEvents.getInstance().writeToFile();
				}
			}) );
			
			addSeparator();
			
			add(menuMoveToGroup = OpenWebifController.createMenu("Move to group"));
			updateMenuMoveToGroup();
			
			JMenuItem miRemoveFromGroup = add( OpenWebifController.createMenuItem( "##", e->{
				treeModel.removeEcsTreeNodeFromGroup( clickedEcsTreeNode );
				AlreadySeenEvents.getInstance().writeToFile();
			} ) );
			
			JMenuItem miDeleteGroup = add( OpenWebifController.createMenuItem( "##", e->{
				treeModel.deleteGroup( clickedGroupTreeNode );
				AlreadySeenEvents.getInstance().writeToFile();
			} ) );
			
			addContextMenuInvokeListener((comp, x, y) -> {
				clickedPath = tree.getPathForLocation(x, y);
				Object lastPathComp = clickedPath.getLastPathComponent();
				tree.setSelectionPath(clickedPath);
				
				clickedTreeNode            = null;
				clickedGroupTreeNode       = null;
				clickedEcsTreeNode         = null;
				clickedStationTreeNode     = null;
				clickedDescriptionTreeNode = null;
				clickedEpisodeInfo         = null;
				
				if (lastPathComp instanceof AbstractTreeNode treeNode)
					clickedTreeNode = treeNode;
				
				if (clickedTreeNode instanceof ECSGroupTreeNode treeNode)
					clickedGroupTreeNode = treeNode;
				
				if (clickedTreeNode instanceof EventCriteriaSetTreeNode treeNode)
					clickedEcsTreeNode = treeNode;
				
				if (clickedTreeNode instanceof StationTreeNode treeNode)
					clickedStationTreeNode = treeNode;
				
				if (clickedTreeNode instanceof DescriptionTreeNode treeNode)
					clickedDescriptionTreeNode = treeNode;
				
				if (clickedDescriptionTreeNode!=null)
					clickedEpisodeInfo = clickedDescriptionTreeNode.episode;
				if (clickedEcsTreeNode!=null && clickedEcsTreeNode.ecs!=null)
					clickedEpisodeInfo = clickedEcsTreeNode.ecs.variableData();
				
				miEditEpisodeStr.setEnabled( clickedEpisodeInfo!=null );
				miEditEpisodeStr.setText(
						clickedEpisodeInfo==null || !clickedEpisodeInfo.hasEpisodeStr()
							?    "Add Episode Text"
							: "Change Episode Text"
				);
				
				miCopyStr.setEnabled(
						clickedGroupTreeNode!=null ||
						clickedEcsTreeNode!=null ||
						clickedStationTreeNode!=null ||
						clickedDescriptionTreeNode!=null
				);
				miCopyStr.setText(
						clickedGroupTreeNode!=null
							? "Copy group name to clipboard"
							: clickedEcsTreeNode!=null
								? "Copy title to clipboard"
								: clickedStationTreeNode!=null
									? "Copy station name to clipboard"
									: clickedDescriptionTreeNode!=null
										? "Copy description to clipboard"
										: "Copy text to clipboard"
				);
				
				menuMoveToGroup.setEnabled(clickedEcsTreeNode!=null && !CustomTreeModel.isInGroup(clickedEcsTreeNode));
				
				miRemoveFromGroup.setEnabled(clickedEcsTreeNode!=null && CustomTreeModel.isInGroup(clickedEcsTreeNode));
				miRemoveFromGroup.setText(
						clickedEcsTreeNode!=null && CustomTreeModel.isInGroup(clickedEcsTreeNode)
							? "Remove from group \"%s\"".formatted( CustomTreeModel.getGroupName(clickedEcsTreeNode) )
							: "Remove from group"
				);
				
				miDeleteGroup.setEnabled(clickedGroupTreeNode!=null && false); // TODO: activate deleteGroup button after implementing CustomTreeModel.deleteGroup
				miDeleteGroup.setText(
						clickedGroupTreeNode!=null
							? "Delete group \"%s\"".formatted( clickedGroupTreeNode.groupName )
							: "Delete group"
				);
			});
			
			addTo(tree);
		}

		private void updateMenuMoveToGroup()
		{
			menuMoveToGroup.removeAll();
			Collection<String> groupNames = treeModel.getGroupNames();
			if (groupNames==null)
				return;
			
			Vector<String> groupNamesVec = new Vector<>(groupNames);
			groupNamesVec.sort(stringComparator);
			
			for (String groupName : groupNamesVec)
			{
				menuMoveToGroup.add( OpenWebifController.createMenuItem( groupName, e->{
					treeModel.moveEcsTreeNodeToGroup( groupName, clickedEcsTreeNode );
					AlreadySeenEvents.getInstance().writeToFile();
				} ) );
			}
			
			if (!groupNamesVec.isEmpty())
				menuMoveToGroup.addSeparator();
			
			menuMoveToGroup.add( OpenWebifController.createMenuItem( "New group ...", e->{
				String groupName = JOptionPane.showInputDialog(parent, "Group name", "");
				if (groupName==null) return;
				
				boolean groupExists = groupNames.contains(groupName);
				treeModel.moveEcsTreeNodeToGroup( groupName, clickedEcsTreeNode );
				if (groupExists)
				{
					String title = "Group exists";
					String[] msg = {
							"A group with name \"%s\" exists already.".formatted(groupName),
							"Node was moved in this existing group."
					};
					JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.INFORMATION_MESSAGE);
				}
				else
					updateMenuMoveToGroup();
				
				AlreadySeenEvents.getInstance().writeToFile();
			} ) );
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
	
	private static class CustomTreeModel implements TreeModel
	{
	
		private final RootTreeNode treeRoot;
		private final Vector<TreeModelListener> listeners;

		CustomTreeModel(RootTreeNode treeRoot)
		{
			this.treeRoot = treeRoot;
			listeners = new Vector<>();
		}

		static boolean isInGroup(EventCriteriaSetTreeNode node)
		{
			return node!=null && node.parent instanceof ECSGroupTreeNode;
		}

		static String getGroupName(EventCriteriaSetTreeNode node)
		{
			if (node!=null && node.parent instanceof ECSGroupTreeNode groupNode)
				return groupNode.groupName;
			return null;
		}

		Collection<String> getGroupNames()
		{
			if (treeRoot != null)
				return treeRoot.getGroupNames();
			return null;
		}

		void moveEcsTreeNodeToGroup(String groupName, EventCriteriaSetTreeNode node)
		{
			if (node==null) return;
			if (node.parent instanceof ECSGroupTreeNode) return;
			if (node.parent==null) return;
			if (treeRoot==null) return;
			
			int oldNodeIndex = node.parent.removeNode(node);
			if (oldNodeIndex < 0) return;
			fireTreeNodeRemoved(node.parent, node, oldNodeIndex);
			
			ECSGroupTreeNode groupNode = treeRoot.groupNodes.get(groupName);
			if (groupNode==null)
			{
				NewNode<ECSGroupTreeNode> newGroupNode = treeRoot.createGroupNode( groupName );
				groupNode = newGroupNode.node;
				fireTreeNodeInserted(treeRoot, groupNode, newGroupNode.index);
			}
			
			node.ecs.variableData().group = groupName;
			NewNode<EventCriteriaSetTreeNode> newNode = groupNode.createChild( node.ecs );
			fireTreeNodeInserted(groupNode, newNode.node, newNode.index);
		}

		void removeEcsTreeNodeFromGroup(EventCriteriaSetTreeNode node)
		{
			if (node==null) return;
			if (node.parent instanceof ECSGroupTreeNode groupNode) 
			{
				int oldNodeIndex = groupNode.removeNode(node);
				if (oldNodeIndex < 0) return;
				fireTreeNodeRemoved(groupNode, node, oldNodeIndex);
				
				node.ecs.variableData().group = null;
				NewNode<EventCriteriaSetTreeNode> newNode = treeRoot.createECSNode( node.ecs );
				fireTreeNodeInserted(treeRoot, newNode.node, newNode.index);
			}
		}

		void deleteGroup(ECSGroupTreeNode groupNode)
		{
			// TODO: implement CustomTreeModel.deleteGroup
			// 1. remove all children from <groupNode> (-> nodes & indices)
			// 2. remove <groupNode> from <treeRoot> (-> node & index)
			// 3. create all new nodes in <treeRoot> (-> nodes & indices)
			// 4. fire all updates ( "nodes removed", "node removed", "nodes inserted" )
		}

		@Override public void addTreeModelListener   (TreeModelListener l) { listeners.add   (l); }
		@Override public void removeTreeModelListener(TreeModelListener l) { listeners.remove(l); }
		
		private void fireTreeNodesInsertedEvent   (TreeModelEvent ev) { for (TreeModelListener l : listeners) l.treeNodesInserted   (ev); }
		private void fireTreeNodesChangedEvent    (TreeModelEvent ev) { for (TreeModelListener l : listeners) l.treeNodesChanged    (ev); }
		private void fireTreeNodesRemovedEvent    (TreeModelEvent ev) { for (TreeModelListener l : listeners) l.treeNodesRemoved    (ev); }
		@SuppressWarnings("unused")
		private void fireTreeStructureChangedEvent(TreeModelEvent ev) { for (TreeModelListener l : listeners) l.treeStructureChanged(ev); }

		@SuppressWarnings("unused")
		private String getSimpleClassName(Object obj)
		{
			if (obj==null) return "null";
			return obj.getClass().getSimpleName();
		}

		void fireTreeNodeUpdate(AbstractTreeNode treeNode)
		{
			if (treeNode == null) return;
			if (treeNode.parent == null) return;
			
			int index = treeNode.parent.getIndex(treeNode);
			if (index<0) return;
			
			//System.out.printf("TreeNodeUpdate:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(treeNode.parent), treeNode.parent, getSimpleClassName(treeNode), treeNode, index);
			
			Object[] parentPath = getPath(treeNode.parent);
			int[] changedIndices = { index };
			Object[] changedChildren = { treeNode };
			
			TreeModelEvent event = new TreeModelEvent(this, parentPath, changedIndices, changedChildren);
			fireTreeNodesChangedEvent(event);
		}

		void fireTreeNodeRemoved(AbstractTreeNode parent, AbstractTreeNode treeNode, int oldNodeIndex)
		{
			if (parent == null) return;
			if (treeNode == null) return;
			if (oldNodeIndex < 0) return;
			
			//System.out.printf("TreeNodeRemoved:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(parent), parent, getSimpleClassName(treeNode), treeNode, oldNodeIndex);
			
			Object[] parentPath = getPath(parent);
			int[] changedIndices = { oldNodeIndex };
			Object[] changedChildren = { treeNode };
			
			TreeModelEvent event = new TreeModelEvent(this, parentPath, changedIndices, changedChildren);
			fireTreeNodesRemovedEvent(event);
		}

		void fireTreeNodeInserted(AbstractTreeNode parent, AbstractTreeNode treeNode, int index)
		{
			if (parent == null) return;
			if (treeNode == null) return;
			if (index < 0) return;
			
			//System.out.printf("TreeNodeInserted:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(parent), parent, getSimpleClassName(treeNode), treeNode, index);
			
			Object[] parentPath = getPath(parent);
			int[] changedIndices = { index };
			Object[] changedChildren = { treeNode };
			
			TreeModelEvent event = new TreeModelEvent(this, parentPath, changedIndices, changedChildren);
			fireTreeNodesInsertedEvent(event);
		}

		static Object[] getPath(AbstractTreeNode treeNode)
		{
			Vector<AbstractTreeNode> path = new Vector<>();
			
			for (AbstractTreeNode node = treeNode; node!=null; node = node.parent)
				path.add(node);
			Object[] pathArr = path.reversed().toArray();
			
			return pathArr;
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public RootTreeNode getRoot()
		{
			return treeRoot;
		}
	
		@Override
		public boolean isLeaf(Object node)
		{
			if (node instanceof TreeNode treeNode)
				return treeNode.isLeaf();
			
			return false;
		}

		@Override
		public int getChildCount(Object parent)
		{
			if (parent instanceof TreeNode parentTreeNode)
				return parentTreeNode.getChildCount();
			
			return 0;
		}

		@Override
		public TreeNode getChild(Object parent, int index)
		{
			if (parent instanceof TreeNode parentTreeNode)
				return parentTreeNode.getChildAt(index);
			
			return null;
		}
	
		@Override
		public int getIndexOfChild(Object parent, Object child)
		{
			if (parent instanceof TreeNode parentTreeNode && child instanceof TreeNode childTreeNode)
				return parentTreeNode.getIndex(childTreeNode);
			
			return -1;
		}
	}

	record NewNode<NodeType extends AbstractTreeNode> (
			int index,
			NodeType node
	) {}

	private abstract class AbstractTreeNode implements TreeNode
	{
		static final Comparator<AbstractTreeNode> SORT_BY_TITLE = Comparator.comparing(node -> node.title, stringComparator);
		
		protected final AbstractTreeNode parent;
		protected       String title;
		protected final boolean allowsChildren;
		protected       TreeNode[] children;
		
		AbstractTreeNode(AbstractTreeNode parent, String title, boolean allowsChildren)
		{
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			children = null;
		}
		
		int removeNode(TreeNode node)
		{
			if (node==null) return -1;
			
			Vector<TreeNode> childrenVec = new Vector<>(Arrays.asList(children));
			
			int index = childrenVec.indexOf(node);
			if (index<0) return -1;
			
			childrenVec.remove(index);
			
			children = childrenVec.toArray(TreeNode[]::new);
			
			return index;
		}

		int insertNode(TreeNode node, Predicate<TreeNode> insertBeforeNode)
		{
			int insertIndex = -1;
			for (int i=0; i<children.length; i++)
				if (insertBeforeNode.test( children[i] ))
				{
					insertIndex = i;
					break;
				}
			
			Vector<TreeNode> childrenVec = new Vector<>(Arrays.asList(children));
			
			if (0 <= insertIndex)
				childrenVec.insertElementAt(node, insertIndex);
			else
			{
				insertIndex = childrenVec.size();
				childrenVec.add(node);
			}
			
			children = childrenVec.toArray(TreeNode[]::new);
			
			return insertIndex;
		}

		protected abstract void determineChildren();
		protected abstract TreeIcons getIcon();
		protected void updateTitle() {}
		
		protected void checkChildren()
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
		private final Map<String, ECSGroupTreeNode> groupNodes;

		RootTreeNode(Map<String, EventCriteriaSet> data)
		{
			super(null, "<>", true);
			this.data = data;
			groupNodes = new HashMap<>();
		}
		
		@Override
		int removeNode(TreeNode node)
		{
			int index = super.removeNode(node);
			
			if (index>=0 && node instanceof ECSGroupTreeNode groupNode)
				groupNodes.remove(groupNode.groupName);
			
			return index;
		}

		NewNode<ECSGroupTreeNode> createGroupNode(String groupName)
		{
			if (groupNodes.containsKey(groupName))
				throw new IllegalArgumentException("A group node with name \"%s\" exists already.".formatted(groupName));
			
			ECSGroupTreeNode groupNode = new ECSGroupTreeNode(this, groupName);
			groupNodes.put(groupName, groupNode);
			
			int insertIndex = insertNode(groupNode, node -> !(node instanceof ECSGroupTreeNode existingGroupNode) || stringComparator.compare(groupNode.groupName, existingGroupNode.groupName) <= 0);
			
			return new NewNode<>( insertIndex, groupNode );
		}
		
		NewNode<EventCriteriaSetTreeNode> createECSNode(EventCriteriaSet ecs)
		{
			EventCriteriaSetTreeNode ecsNode = new EventCriteriaSetTreeNode(this, ecs);
			
			int insertIndex = insertNode(ecsNode, node -> !(node instanceof EventCriteriaSetTreeNode existingECSNode) || SORT_BY_TITLE.compare(ecsNode, existingECSNode) <= 0);
			
			return new NewNode<>( insertIndex, ecsNode );
		}

		Collection<String> getGroupNames()
		{
			checkChildren();
			return groupNodes.keySet();
		}

		@Override
		protected TreeIcons getIcon()
		{
			return null;
		}

		@Override
		protected void determineChildren()
		{
			groupNodes.clear();
			Vector<EventCriteriaSetTreeNode> ungroupedECSNodes = new Vector<>();
			
			data.forEach((title, ecs) -> {
				VariableECSData variableData = ecs.variableData();
				if (variableData!=null && variableData.hasGroup())
				{
					ECSGroupTreeNode groupNode = groupNodes.computeIfAbsent(variableData.group, group -> new ECSGroupTreeNode(this, group));
					groupNode.ecsList.add(ecs);
				}
				else
				{
					ungroupedECSNodes.add(new EventCriteriaSetTreeNode(this, ecs));
				}
			});
			
			Vector<AbstractTreeNode> childrenVec = new Vector<>( groupNodes.values() );
			childrenVec.sort(SORT_BY_TITLE);
			
			ungroupedECSNodes.sort(SORT_BY_TITLE);
			childrenVec.addAll(ungroupedECSNodes);
			
			children = childrenVec.toArray(TreeNode[]::new);
		}
	}
	
	private class ECSGroupTreeNode extends AbstractTreeNode
	{
		private final Vector<EventCriteriaSet> ecsList;
		private final String groupName;

		ECSGroupTreeNode(RootTreeNode parent, String groupName)
		{
			super(parent, groupName, true);
			this.groupName = groupName;
			ecsList = new Vector<>();
		}

		@Override
		int removeNode(TreeNode node)
		{
			int index = super.removeNode(node);
			
			if (index>=0 && node instanceof EventCriteriaSetTreeNode ecsNode)
				ecsList.remove(ecsNode.ecs);
			
			return index;
		}

		@Override
		protected TreeIcons getIcon()
		{
			return null;
		}

		NewNode<EventCriteriaSetTreeNode> createChild(EventCriteriaSet ecs)
		{
			EventCriteriaSetTreeNode ecsNode = new EventCriteriaSetTreeNode(this, ecs);
			ecsList.add(ecs);
			
			int insertIndex = insertNode(ecsNode, node -> !(node instanceof AbstractTreeNode existingNode) || SORT_BY_TITLE.compare(ecsNode, existingNode) <= 0);
			
			return new NewNode<>(insertIndex, ecsNode);
		}

		@Override
		protected void determineChildren()
		{
			children = ecsList
					.stream()
					.map(ecs -> new EventCriteriaSetTreeNode(this, ecs))
					.sorted(SORT_BY_TITLE)
					.toArray(TreeNode[]::new);
		}
	}

	private class EventCriteriaSetTreeNode extends AbstractTreeNode
	{
		private final EventCriteriaSet ecs;

		EventCriteriaSetTreeNode(AbstractTreeNode parent, EventCriteriaSet ecs)
		{
			super(parent, generateTitle(ecs.title(), ecs.variableData()), ecs.stations()!=null || ecs.descriptions()!=null);
			this.ecs = ecs;
		}

		@Override int removeNode(TreeNode node) { throw new UnsupportedOperationException(); }

		@Override
		protected void updateTitle()
		{
			title = generateTitle(ecs.title(), ecs.variableData());
		}

		@Override
		protected TreeIcons getIcon()
		{
			EpisodeInfo episode = ecs.variableData();
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
						.sorted(SORT_BY_TITLE)
						.toList()
				);
			}
			
			if (ecs.stations()!=null)
			{
				Map<String, StationData> stations = ecs.stations();
				childrenVec.addAll( stations.keySet()
						.stream()
						.map(station -> new StationTreeNode(this, station, stations.get(station)))
						.sorted(SORT_BY_TITLE)
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

		DescriptionTreeNode(AbstractTreeNode parent, String description, EpisodeInfo episode)
		{
			super(parent, generateTitle(description, episode), false);
			this.description = description;
			this.episode = episode;
			children = new TreeNode[0];
		}
		
		@Override int removeNode(TreeNode node) { throw new UnsupportedOperationException(); }

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
		private final String station;

		StationTreeNode(EventCriteriaSetTreeNode parent, String station, StationData stationData)
		{
			super(parent, station, stationData.descriptions()!=null);
			this.station = station;
			this.stationData = stationData;
		}
		
		@Override int removeNode(TreeNode node) { throw new UnsupportedOperationException(); }

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
						.sorted(SORT_BY_TITLE)
						.toArray(TreeNode[]::new);
			}
			else
				children = new TreeNode[0];
		}
	}
}
