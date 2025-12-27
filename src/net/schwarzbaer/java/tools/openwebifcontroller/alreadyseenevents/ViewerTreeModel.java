package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;
import java.util.function.BiConsumer;

import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.VariableECSData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.AbstractTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.DescriptionTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.ECSGroupTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.EventCriteriaSetTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.NewNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.RootTreeNode;

class ViewerTreeModel implements TreeModel
{

	@SuppressWarnings("unused")
	private final JTree tree;
	final RootTreeNode treeRoot;
	private final Vector<TreeModelListener> listeners;

	ViewerTreeModel(JTree tree, RootTreeNode treeRoot)
	{
		this.tree = tree;
		this.treeRoot = treeRoot;
		listeners = new Vector<>();
	}

	void createNewECSNode(String title, ECSGroupTreeNode groupTreeNode)
	{
		if (treeRoot.data.containsKey(title))
			throw new IllegalStateException();
		
		EventCriteriaSet ecs = EventCriteriaSet.create(title, false, false);
		treeRoot.data.put(title, ecs);
		
		EventCriteriaSetTreeNode.HostNode parent;
		if (groupTreeNode==null)
		{
			ecs.variableData().group = null;
			parent = treeRoot;
		}
		else
		{
			ecs.variableData().group = groupTreeNode.groupName;
			parent = groupTreeNode;
		}
		NewNode<EventCriteriaSetTreeNode> newNode = parent.createECSNode( ecs );
		fireTreeNodeInserted(newNode.node().parent, newNode.node(), newNode.index());
	}

	void setRootSubnodeOrder(RootTreeNode.NodeOrder order)
	{
		if (treeRoot!=null)
		{
			treeRoot.setNodeOrder(order);
			storeCurrentRootSubnodeOrder(order);
			fireTreeStructureChanged(treeRoot);
		}
	}

	static RootTreeNode.NodeOrder getCurrentRootSubnodeOrder()
	{
		return OpenWebifController.settings.getEnum(
				OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_RootSubNodeOrder,
				RootTreeNode.NodeOrder.GROUPS_FIRST,
				RootTreeNode.NodeOrder.class
		);
	}

	static void storeCurrentRootSubnodeOrder(RootTreeNode.NodeOrder order)
	{
		OpenWebifController.settings.putEnum(
				OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_RootSubNodeOrder,
				order
		);
	}

	void reorderSiblings(AbstractTreeNode node)
	{
		if (node==null) return;
		if (node.parent==null) return;
		
		node.parent.reorderChildren();
		
		fireTreeStructureChanged(node.parent);
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

	boolean isExistingGroupName(String groupName)
	{
		return treeRoot.groupNodes.containsKey(groupName);
	}

	void renameGroup(ECSGroupTreeNode groupNode, String newGroupName)
	{
		if (groupNode==null) return;
		if (treeRoot.groupNodes.containsKey(newGroupName)) throw new IllegalArgumentException();
		if (treeRoot.groupNodes.get(groupNode.groupName) != groupNode) throw new IllegalStateException();
		
		treeRoot.groupNodes.remove( groupNode.groupName );
		treeRoot.groupNodes.put( newGroupName, groupNode );
		
		groupNode.groupName = newGroupName;
		groupNode.updateTitle();
		fireTreeNodeUpdate(groupNode);
		
		groupNode.forEachChildNode( node -> {
			if (!(node instanceof EventCriteriaSetTreeNode ecsNode)) return;
			if (ecsNode.ecs == null) return;
			VariableECSData variableData = ecsNode.ecs.variableData();
			if (variableData == null) return;
			variableData.group = newGroupName;
		} );
		fireAllSubNodesUpdate(groupNode);
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
			groupNode = newGroupNode.node();
			fireTreeNodeInserted(treeRoot, groupNode, newGroupNode.index());
		}
		
		node.ecs.variableData().group = groupName;
		NewNode<EventCriteriaSetTreeNode> newNode = groupNode.createECSNode( node.ecs );
		fireTreeNodeInserted(groupNode, newNode.node(), newNode.index());
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
			fireTreeNodeInserted(treeRoot, newNode.node(), newNode.index());
		}
	}

	void deleteGroup(ECSGroupTreeNode groupNode)
	{
		if (groupNode==null) return;
		
		AbstractTreeNode[] children = groupNode.getChildren();
		children = Arrays.copyOf(children, children.length);
		
		for (AbstractTreeNode node : children)
			if (node instanceof EventCriteriaSetTreeNode ecsNode)
				removeEcsTreeNodeFromGroup( ecsNode );
		
		int index = treeRoot.removeNode( groupNode );
		if (index >= 0)
			fireTreeNodeRemoved( treeRoot, groupNode, index );
	}

	boolean deleteDescNode(DescriptionTreeNode descNode)
	{
		if (descNode==null) return false;
		
		AbstractTreeNode parent = descNode.parent;
		if (parent!=null)
		{
			int index = parent.removeNode(descNode);
			if (index >= 0)
				fireTreeNodeRemoved( parent, descNode, index );
		}
		
		return descNode.description.removeFromMap();
	}

	@Override public void addTreeModelListener   (TreeModelListener l) { listeners.add   (l); }
	@Override public void removeTreeModelListener(TreeModelListener l) { listeners.remove(l); }
	
	private void fireTreeModelEvent(TreeModelEvent ev, BiConsumer<TreeModelListener, TreeModelEvent> action)
	{
		for (TreeModelListener l : listeners)
			action.accept(l, ev);
	}

	void fireTreeNodeUpdate(AbstractTreeNode treeNode)
	{
		if (treeNode == null) return;
		if (treeNode.parent == null) return;
		
		int index = treeNode.parent.getIndex(treeNode);
		if (index<0) return;
		
		//System.out.printf("TreeNodeUpdate:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(treeNode.parent), treeNode.parent, getSimpleClassName(treeNode), treeNode, index);
		
		Object[] parentPath = AbstractTreeNode.getPath(treeNode.parent);
		int[] changedIndices = { index };
		Object[] changedChildren = { treeNode };
		
		fireTreeModelEvent(
				new TreeModelEvent(this, parentPath, changedIndices, changedChildren),
				TreeModelListener::treeNodesChanged
		);
	}

	void fireAllSubNodesUpdate(AbstractTreeNode parent)
	{
		if (parent == null) return;
		
		Object[] parentPath = AbstractTreeNode.getPath( parent );
		int[] changedIndices = generateAscendingInts( 0, parent.getChildCount() );
		Object[] changedChildren = parent.getChildren();
		
		fireTreeModelEvent(
				new TreeModelEvent(this, parentPath, changedIndices, changedChildren),
				TreeModelListener::treeNodesChanged
		);
	}

	void fireTreeNodeRemoved(AbstractTreeNode parent, AbstractTreeNode treeNode, int oldNodeIndex)
	{
		if (parent == null) return;
		if (treeNode == null) return;
		if (oldNodeIndex < 0) return;
		
		//System.out.printf("TreeNodeRemoved:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(parent), parent, getSimpleClassName(treeNode), treeNode, oldNodeIndex);
		
		Object[] parentPath = AbstractTreeNode.getPath(parent);
		int[] changedIndices = { oldNodeIndex };
		Object[] changedChildren = { treeNode };
		
		fireTreeModelEvent(
				new TreeModelEvent(this, parentPath, changedIndices, changedChildren),
				TreeModelListener::treeNodesRemoved
		);
	}

	void fireTreeNodeInserted(AbstractTreeNode parent, AbstractTreeNode treeNode, int index)
	{
		if (parent == null) return;
		if (treeNode == null) return;
		if (index < 0) return;
		
		//System.out.printf("TreeNodeInserted:%n   parent: [%s] %s%n   treeNode: [%s] %s%n   index: %d%n", getSimpleClassName(parent), parent, getSimpleClassName(treeNode), treeNode, index);
		
		Object[] parentPath = AbstractTreeNode.getPath(parent);
		int[] changedIndices = { index };
		Object[] changedChildren = { treeNode };
		
		fireTreeModelEvent(
				new TreeModelEvent(this, parentPath, changedIndices, changedChildren),
				TreeModelListener::treeNodesInserted
		);
	}

	void fireTreeStructureChanged(AbstractTreeNode treeNode)
	{
		fireTreeModelEvent(
				new TreeModelEvent(this, AbstractTreeNode.getPath(treeNode), null, null),
				TreeModelListener::treeStructureChanged
		);
	}

	private int[] generateAscendingInts(int start, int end)
	{
		int n = Math.max( 0, end-start);
		
		int[] arr = new int[n];
		for (int i=start; i<end; i++)
			arr[i-start] = i;
		
		return arr;
	}

	@SuppressWarnings("unused")
	private static String getSimpleClassName(Object obj)
	{
		if (obj==null) return "null";
		return obj.getClass().getSimpleName();
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