package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.DescriptionData;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.StationData;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.TextOperator;
import net.schwarzbaer.java.tools.openwebifcontroller.AlreadySeenEvents.VariableECSData;

class AlreadySeenEventsViewer extends StandardDialog
{
	private static final long serialVersionUID = 6089627717117385916L;
	private static final Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
	
	private static enum TreeIcons {
		Title, TitleEp, Station, Desc, DescEp, DescContains, DescContainsEp, DescStart, DescStartEp
		;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<TreeIcons> IS = IconSource.createCachedIcons(16, 16, "/images/AlreadySeenEventsViewerTreeIcons.png", TreeIcons.values());
	}
	
	private boolean episodeStringFirst;
	private final JTree tree;
	private CustomTreeModel treeModel;

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		
		episodeStringFirst = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_EpisodeStringFirst, false);
		
		tree = new JTree();
		tree.setModel(treeModel = new CustomTreeModel(tree, AlreadySeenEvents.getInstance().createTreeRoot(this)));
		tree.setCellRenderer(new TCR());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		TreeKeyListener treeKeyListener = new TreeKeyListener();
		tree.addKeyListener(treeKeyListener);
		tree.addTreeSelectionListener(ev -> treeKeyListener.selected = new SelectionInfo(tree.getSelectionPath()));
		
		new TreeContextMenu(parent).addTo(tree);
		
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		
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
		tree.setModel(treeModel = new CustomTreeModel(tree, AlreadySeenEvents.getInstance().createTreeRoot(this)));
	}
	
	static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}

	RootTreeNode createTreeRoot(Map<String, EventCriteriaSet> data)
	{
		return new RootTreeNode( data, CustomTreeModel.getCurrentRootSubnodeOrder() );
	}
	
	String generateTitle(DescriptionChanger description)
	{
		if (description==null)
			return "<null>";
		
		return generateTitle(
				description.getText(),
				description.getData()
		);
	}
	
	String generateTitle(String description, DescriptionData data)
	{
		if (data==null)
			return generateTitle(description, (EpisodeInfo) data);
		
		String title = description;
		switch (data.operator)
		{
		case Equals: break;
		case StartsWith: title = description+"..."; break;
		case Contains: title = "..."+description+"..."; break;
		}
		
		return generateTitle(title, (EpisodeInfo) data);
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
	
	private void editEpisodeStr(Window window, SelectionInfo selected)
	{
		if (selected.episodeInfo==null)
			return;
		
		String episodeStr = !selected.episodeInfo.hasEpisodeStr() ? "" : selected.episodeInfo.episodeStr;
		String result = JOptionPane.showInputDialog(window, "Episode Text", episodeStr);
		if (result!=null)
		{
			selected.episodeInfo.episodeStr = result;
			selected.treeNode.updateTitle();
			treeModel.fireTreeNodeUpdate(selected.treeNode);
			tree.repaint();
			AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.EpisodeText);
		}
	}

	private static class SelectionInfo
	{
		final TreePath                 path;
		final AbstractTreeNode         treeNode;
		final ECSGroupTreeNode         groupTreeNode;
		final EventCriteriaSetTreeNode ecsTreeNode;
		final StationTreeNode          stationTreeNode;
		final DescriptionTreeNode      descriptionTreeNode;
		final EpisodeInfo              episodeInfo;
		
		SelectionInfo(TreePath path)
		{
			this.path = path;
			
			Object lastPathComp = this.path==null ? null : this.path.getLastPathComponent();
			treeNode            = lastPathComp instanceof AbstractTreeNode         treeNode ? treeNode : null;
			groupTreeNode       = treeNode     instanceof ECSGroupTreeNode         treeNode ? treeNode : null;
			ecsTreeNode         = treeNode     instanceof EventCriteriaSetTreeNode treeNode ? treeNode : null;
			stationTreeNode     = treeNode     instanceof StationTreeNode          treeNode ? treeNode : null;
			descriptionTreeNode = treeNode     instanceof DescriptionTreeNode      treeNode ? treeNode : null;
			episodeInfo =
					descriptionTreeNode!=null
						? descriptionTreeNode.description.getData()
						: ecsTreeNode!=null && ecsTreeNode.ecs!=null
							? ecsTreeNode.ecs.variableData()
							: null;
		}
	}
	
	private enum KeyFunction
	{
		EditEpisodeStr(KeyEvent.VK_F4),
		;
		private final int keyCode;
		private final String keyLabel;
		
		KeyFunction(int keyCode) { this(keyCode, KeyEvent.getKeyText(keyCode)); }
		KeyFunction(int keyCode, String keyLabel)
		{
			this.keyCode = keyCode;
			this.keyLabel = Objects.requireNonNull(keyLabel);
		}
		
		static KeyFunction getFromKeyCode(int keyCode)
		{
			for (KeyFunction val : values())
				if (val.keyCode == keyCode)
					return val;
			return null;
		}
		
		String addKeyLabel(String baseStr)
		{
			return "%s (%s)".formatted(baseStr, keyLabel);
		}
	}

	private class TreeKeyListener implements KeyListener
	{
		SelectionInfo selected = new SelectionInfo(null);

		@Override public void keyTyped   (KeyEvent e) {}
		@Override public void keyReleased(KeyEvent e) {}

		@Override public void keyPressed(KeyEvent e)
		{
			KeyFunction keyFunction = KeyFunction.getFromKeyCode(e.getKeyCode());
			if (keyFunction==null) return;
			
			switch (keyFunction)
			{
			case EditEpisodeStr:
				editEpisodeStr(AlreadySeenEventsViewer.this, selected);
				break;
			}
		}
	}

	private class TreeContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = -3347262887544423895L;
		
		private final Window window;
		private final JMenu menuMoveToGroup;
		private SelectionInfo clicked;
		
		TreeContextMenu(Window window)
		{
			this.window = window;
			clicked = new SelectionInfo(null);
			
			JMenuItem miCopyStr = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Copy, e->{
				if (clicked.groupTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clicked.groupTreeNode.groupName);
				
				if (clicked.ecsTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clicked.ecsTreeNode.ecs.title());
				
				if (clicked.stationTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clicked.stationTreeNode.station);
				
				if (clicked.descriptionTreeNode!=null)
					ClipboardTools.copyStringSelectionToClipBoard(clicked.descriptionTreeNode.description.getText());
			} ) );
			
			JMenuItem miEditEpisodeStr = add( OpenWebifController.createMenuItem( "##", e->{
				editEpisodeStr(this.window, clicked);
			} ) );
			
			add(OpenWebifController.createCheckBoxMenuItem("Show Episode Text before Title", episodeStringFirst, val -> {
				episodeStringFirst = val;
				OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_EpisodeStringFirst, episodeStringFirst);
				rebuildTree();
			} ));
			
			addSeparator();
			
			JMenuItem miEditDesc = add(OpenWebifController.createMenuItem("Edit Description Text", e -> {
				if (clicked.descriptionTreeNode==null) return;
				String newDesc = TextAreaDialog.editText(window, "Edit Description Text", 400, 200, true, clicked.descriptionTreeNode.description.getText());
				if (newDesc!=null)
				{
					DescriptionChanger.Response response = clicked.descriptionTreeNode.description.setText(newDesc);
					if (response.success)
					{
						clicked.descriptionTreeNode.updateTitle();
						treeModel.fireTreeNodeUpdate(clicked.descriptionTreeNode);
						tree.repaint();
						AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
					}
					else
					{
						String[] msg = { "Can't change description text:", response.reasonWhyNot };
						String title = "Can't change";
						JOptionPane.showMessageDialog(window, msg, title, JOptionPane.WARNING_MESSAGE);
					}
				}
			}));
			
			JMenu menuDescOperator = OpenWebifController.createMenu("##");
			add(menuDescOperator);
			EnumMap<TextOperator, JCheckBoxMenuItem> menuDescOperatorItems = new EnumMap<>(TextOperator.class);
			for (TextOperator op : TextOperator.values())
			{
				JCheckBoxMenuItem cmi = OpenWebifController.createCheckBoxMenuItem(op.title, false, b -> {
					if (clicked.descriptionTreeNode!=null)
					{
						clicked.descriptionTreeNode.description.getData().operator = op;
						clicked.descriptionTreeNode.updateTitle();
						treeModel.fireTreeNodeUpdate(clicked.descriptionTreeNode);
						tree.repaint();
						AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
					}
				});
				menuDescOperator.add( cmi );
				menuDescOperatorItems.put(op, cmi);
			}
			
			addSeparator();
			
			add(menuMoveToGroup = OpenWebifController.createMenu("Move to group"));
			updateMenuMoveToGroup();
			
			JMenuItem miRemoveFromGroup = add( OpenWebifController.createMenuItem( "##", e->{
				treeModel.removeEcsTreeNodeFromGroup( clicked.ecsTreeNode );
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
			} ) );
			
			JMenuItem miRenameGroup = add( OpenWebifController.createMenuItem( "##", e->{
				String result = JOptionPane.showInputDialog(this.window, "New group name", clicked.groupTreeNode.groupName);
				if (result==null || result.isBlank() || result.equals(clicked.groupTreeNode.groupName)) return;
				if (treeModel.isExistingGroupName( result ))
				{
					String[] msg = {
							"A group with name \"%s\" exists already.".formatted( result ),
							"Please choose anonther name."
					};
					JOptionPane.showMessageDialog(this.window, msg, "Group already exists", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				treeModel.renameGroup( clicked.groupTreeNode, result );
				updateMenuMoveToGroup();
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
			} ) );
			
			addSeparator();
			
			JMenuItem miDeleteNode = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Delete, e->{
				if (clicked.groupTreeNode!=null)
				{
					treeModel.deleteGroup( clicked.groupTreeNode );
					updateMenuMoveToGroup();
					AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
				}
				if (clicked.ecsTreeNode!=null)
				{
					// TODO: delete ecsTreeNode
				}
				if (clicked.stationTreeNode!=null)
				{
					// TODO: stationTreeNode
				}
				if (clicked.descriptionTreeNode!=null)
				{
					boolean success = treeModel.deleteDescNode(clicked.descriptionTreeNode);
					if (success)
						AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
				}
			} ) );
			
			addSeparator();
			
			JMenuItem miReorderSiblings = add( OpenWebifController.createMenuItem( "##", e->{
				treeModel.reorderSiblings(clicked.treeNode);
				tree.repaint();
			} ) );
			
			JMenu menuRootOrder = OpenWebifController.createMenu("Order of subnodes of root");
			add(menuRootOrder);
			EnumMap<RootTreeNode.NodeOrder, JCheckBoxMenuItem> mapMiRootOrder = new EnumMap<>(RootTreeNode.NodeOrder.class);
			for (RootTreeNode.NodeOrder order : RootTreeNode.NodeOrder.values())
			{
				JCheckBoxMenuItem checkBoxMenuItem = OpenWebifController.createCheckBoxMenuItem(order.title, false, b -> treeModel.setRootSubnodeOrder(order));
				menuRootOrder.add(checkBoxMenuItem);
				mapMiRootOrder.put(order, checkBoxMenuItem);
			}
			
			add(OpenWebifController.createMenuItem("Rebuild Tree", GrayCommandIcons.IconGroup.Reload, e -> {
				rebuildTree();
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				clicked = new SelectionInfo( tree.getPathForLocation(x, y) );
				tree.setSelectionPath(clicked.path);
				
				miEditEpisodeStr.setEnabled( clicked.episodeInfo!=null );
				miEditEpisodeStr.setText( KeyFunction.EditEpisodeStr.addKeyLabel(
						clicked.episodeInfo==null || !clicked.episodeInfo.hasEpisodeStr()
							?    "Add Episode Text"
							: "Change Episode Text"
				) );
				
				miCopyStr.setEnabled(
						clicked.groupTreeNode!=null ||
						clicked.ecsTreeNode!=null ||
						clicked.stationTreeNode!=null ||
						clicked.descriptionTreeNode!=null
				);
				miCopyStr.setText(
						clicked.groupTreeNode!=null
							? "Copy group name to clipboard"
							: clicked.ecsTreeNode!=null
								? "Copy title to clipboard"
								: clicked.stationTreeNode!=null
									? "Copy station name to clipboard"
									: clicked.descriptionTreeNode!=null
										? "Copy description to clipboard"
										: "Copy text to clipboard"
				);
				
				AbstractTreeNode parent = clicked.treeNode!=null ? clicked.treeNode.parent : null;
				miReorderSiblings.setEnabled(parent!=null);
				miReorderSiblings.setText(
						parent instanceof RootTreeNode
							? "Reorder subnodes of root"
							: parent instanceof ECSGroupTreeNode
								? "Reorder subnodes of group \"%s\"".formatted( parent.title )
								: parent instanceof EventCriteriaSetTreeNode
									? "Reorder subnodes of title \"%s\"".formatted( parent.title )
									: parent instanceof StationTreeNode
										? "Reorder subnodes of station \"%s\"".formatted( parent.title )
										: parent instanceof DescriptionTreeNode
											? "Reorder subnodes of description \"%s\"".formatted( parent.title )
											: "Reorder nodes"
				);
				
				menuMoveToGroup.setEnabled(clicked.ecsTreeNode!=null && !CustomTreeModel.isInGroup(clicked.ecsTreeNode));
				
				miRemoveFromGroup.setEnabled(clicked.ecsTreeNode!=null && CustomTreeModel.isInGroup(clicked.ecsTreeNode));
				miRemoveFromGroup.setText(
						clicked.ecsTreeNode!=null && CustomTreeModel.isInGroup(clicked.ecsTreeNode)
							? "Remove from group \"%s\"".formatted( CustomTreeModel.getGroupName(clicked.ecsTreeNode) )
							: "Remove from group"
				);
				
				miRenameGroup.setEnabled(clicked.groupTreeNode!=null);
				miRenameGroup.setText(
						clicked.groupTreeNode!=null
							? "Rename group \"%s\"".formatted( clicked.groupTreeNode.groupName )
							: "Rename group"
				);
				
				
				miDeleteNode.setEnabled(
						clicked.groupTreeNode!=null ||
						//clicked.ecsTreeNode!=null ||
						//clicked.stationTreeNode!=null ||
						clicked.descriptionTreeNode!=null
				);
				miDeleteNode.setText(
						clicked.groupTreeNode!=null
							? "Delete group \"%s\"".formatted( clicked.groupTreeNode.groupName )
							: clicked.ecsTreeNode!=null
								? "Delete title \"%s\"".formatted( (String)clicked.ecsTreeNode.title )
								: clicked.stationTreeNode!=null
									? "Delete station \"%s\"".formatted( clicked.stationTreeNode.station )
									: clicked.descriptionTreeNode!=null
										? "Delete description \"%s\"".formatted( clicked.descriptionTreeNode.description.getText() )
										: "Delete"
				);
				
				RootTreeNode.NodeOrder currentOrder = CustomTreeModel.getCurrentRootSubnodeOrder();
				for (RootTreeNode.NodeOrder order : RootTreeNode.NodeOrder.values())
				{
					JCheckBoxMenuItem menuItem = mapMiRootOrder.get(order);
					if (menuItem!=null)
						menuItem.setSelected(order == currentOrder);
				}
				
				miEditDesc      .setEnabled(clicked.descriptionTreeNode!=null);
				menuDescOperator.setEnabled(clicked.descriptionTreeNode!=null);
				
				TextOperator operatorOfClicked = clicked.descriptionTreeNode==null ? null : clicked.descriptionTreeNode.description.getData().operator;
				menuDescOperator.setText(
						operatorOfClicked==null
							? "[Description Text Operator]"
							: "Description %s ...".formatted(operatorOfClicked.title)
				);
				menuDescOperatorItems.forEach((op,cmi) -> cmi.setSelected(op == operatorOfClicked));
			});
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
					treeModel.moveEcsTreeNodeToGroup( groupName, clicked.ecsTreeNode );
					AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
				} ) );
			}
			
			if (!groupNamesVec.isEmpty())
				menuMoveToGroup.addSeparator();
			
			menuMoveToGroup.add( OpenWebifController.createMenuItem( "New group ...", e->{
				String suggestedName = clicked.ecsTreeNode==null || clicked.ecsTreeNode.ecs==null ? "" : clicked.ecsTreeNode.ecs.title();
				String groupName = JOptionPane.showInputDialog(window, "Group name", suggestedName);
				if (groupName==null) return;
				
				boolean groupExists = groupNames.contains(groupName);
				treeModel.moveEcsTreeNodeToGroup( groupName, clicked.ecsTreeNode );
				if (groupExists)
				{
					String title = "Group exists";
					String[] msg = {
							"A group with name \"%s\" exists already.".formatted(groupName),
							"Node was moved in this existing group."
					};
					JOptionPane.showMessageDialog(window, msg, title, JOptionPane.INFORMATION_MESSAGE);
				}
				else
					updateMenuMoveToGroup();
				
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
			} ) );
		}
	}

	private static class TCR extends DefaultTreeCellRenderer
	{
		private static final long serialVersionUID = 849724659340192701L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int rowIndex, boolean hasFocus)
		{
			Component rendcomp = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, rowIndex, hasFocus);
			
			if (value instanceof AbstractTreeNode treeNode)
			{
				TreeIcons icon = treeNode.getIcon();
				if (icon!=null)
					setIcon(icon.getIcon());
				
				Color textColor = treeNode.getTextColor();
				textColor = isSelected ? getTextSelectionColor() : textColor==null ? getTextNonSelectionColor() : textColor;
				setForeground(textColor);
			}
			
			return rendcomp;
		}
		
	}
	
	private static class CustomTreeModel implements TreeModel
	{
	
		@SuppressWarnings("unused")
		private final JTree tree;
		private final RootTreeNode treeRoot;
		private final Vector<TreeModelListener> listeners;

		CustomTreeModel(JTree tree, RootTreeNode treeRoot)
		{
			this.tree = tree;
			this.treeRoot = treeRoot;
			listeners = new Vector<>();
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
		protected       AbstractTreeNode[] children;
		protected       Comparator<AbstractTreeNode> childrenOrder;
		
		AbstractTreeNode(AbstractTreeNode parent, String title, boolean allowsChildren, Comparator<AbstractTreeNode> childrenOrder)
		{
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			this.childrenOrder = childrenOrder;
			children = null;
		}

		AbstractTreeNode[] getChildren()
		{
			checkChildren();
			return children;
		}

		void forEachChildNode(Consumer<AbstractTreeNode> action)
		{
			checkChildren();
			for (AbstractTreeNode childNode : children)
				action.accept(childNode);
		}
		
		static Object[] getPath(AbstractTreeNode treeNode)
		{
			Vector<AbstractTreeNode> path = new Vector<>();
			
			for (AbstractTreeNode node = treeNode; node!=null; node = node.parent)
				path.add(node);
			Object[] pathArr = path.reversed().toArray();
			
			return pathArr;
		}

		TreePath[] getChildPaths()
		{
			checkChildren();
			
			TreePath path = new TreePath( getPath(this) );
			
			return Arrays
					.stream(children)
					.map(path::pathByAddingChild)
					.toArray(TreePath[]::new);
		}

		int removeNode(AbstractTreeNode node)
		{
			if (node==null) return -1;
			
			checkChildren();
			
			Vector<AbstractTreeNode> childrenVec = new Vector<>(Arrays.asList(children));
			
			int index = childrenVec.indexOf(node);
			if (index<0) return -1;
			
			childrenVec.remove(index);
			
			children = childrenVec.toArray(AbstractTreeNode[]::new);
			
			return index;
		}

		int insertNode(AbstractTreeNode node)
		{
			checkChildren();
			
			int insertIndex = -1;
			for (int i=0; i<children.length; i++)
				if (childrenOrder.compare(node, children[i]) <= 0)
				{
					insertIndex = i;
					break;
				}
			
			Vector<AbstractTreeNode> childrenVec = new Vector<>(Arrays.asList(children));
			
			if (0 <= insertIndex)
				childrenVec.insertElementAt(node, insertIndex);
			else
			{
				insertIndex = childrenVec.size();
				childrenVec.add(node);
			}
			
			children = childrenVec.toArray(AbstractTreeNode[]::new);
			
			return insertIndex;
		}

		void setChildrenOrder(Comparator<AbstractTreeNode> childrenOrder)
		{
			checkChildren();
			
			this.childrenOrder = childrenOrder;
			Arrays.sort(children, childrenOrder);
		}

		void reorderChildren()
		{
			checkChildren();
			
			Arrays.sort(children, childrenOrder);
		}

		protected abstract void determineChildren();
		protected abstract TreeIcons getIcon();
		protected Color getTextColor() { return null; }
		protected void updateTitle() {}
		
		protected void checkChildren()
		{
			if (children == null)
				determineChildren();
		}
		
		@Override public String           toString         () { return title; }
		@Override public AbstractTreeNode getParent        () { return parent; }
		@Override public boolean          getAllowsChildren() { return allowsChildren; }
		@Override public boolean          isLeaf           () { checkChildren(); return children.length == 0; }
		@Override public int              getChildCount    () { checkChildren(); return children.length; }
		
		@Override public AbstractTreeNode getChildAt(int childIndex)
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
		public Enumeration<AbstractTreeNode> children()
		{
			checkChildren();
			return new Enumeration<>() {
				private int index = 0;
				@Override public boolean hasMoreElements() { return index < children.length; }
				@Override public AbstractTreeNode nextElement() { return children[index++]; }
			};
		}
	}
	
	class RootTreeNode extends AbstractTreeNode
	{
		private static final Comparator<AbstractTreeNode> ORDER_GROUPS_FIRST = Comparator
				.<AbstractTreeNode,Integer>comparing( node -> {
					if (node instanceof ECSGroupTreeNode) return 0;
					if (node instanceof EventCriteriaSetTreeNode) return 1;
					return 2;
				} )
				.thenComparing(SORT_BY_TITLE);
		
		enum NodeOrder
		{
			GROUPS_FIRST ("Groups first",ORDER_GROUPS_FIRST),
			SORT_BY_TITLE("All sorted by title",AbstractTreeNode.SORT_BY_TITLE),
			;
			private final String title;
			private final Comparator<AbstractTreeNode> order;
			NodeOrder(String title, Comparator<AbstractTreeNode> order)
			{
				this.title = title;
				this.order = order;
			}
		}
		
		private final Map<String, EventCriteriaSet> data;
		private final Map<String, ECSGroupTreeNode> groupNodes;
		
		RootTreeNode(Map<String, EventCriteriaSet> data, NodeOrder subnodeOrder)
		{
			super(null, "Already Seen Events", true, Objects.requireNonNull(subnodeOrder).order);
			this.data = data;
			groupNodes = new HashMap<>();
		}
		
		void setNodeOrder(NodeOrder subnodeOrder)
		{
			setChildrenOrder(Objects.requireNonNull(subnodeOrder).order);
		}
		
		@Override
		int removeNode(AbstractTreeNode node)
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
			
			int insertIndex = insertNode(groupNode);
			
			return new NewNode<>( insertIndex, groupNode );
		}
		
		NewNode<EventCriteriaSetTreeNode> createECSNode(EventCriteriaSet ecs)
		{
			EventCriteriaSetTreeNode ecsNode = new EventCriteriaSetTreeNode(this, ecs);
			
			int insertIndex = insertNode(ecsNode);
			
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
			childrenVec.addAll(ungroupedECSNodes);
			childrenVec.sort(childrenOrder);
			children = childrenVec.toArray(AbstractTreeNode[]::new);
		}
	}
	
	private class ECSGroupTreeNode extends AbstractTreeNode
	{
		private final Vector<EventCriteriaSet> ecsList;
		private       String groupName;

		ECSGroupTreeNode(RootTreeNode parent, String groupName)
		{
			super(parent, groupName, true, SORT_BY_TITLE);
			this.groupName = groupName;
			ecsList = new Vector<>();
		}

		@Override
		protected void updateTitle()
		{
			title = groupName;
		}

		@Override
		int removeNode(AbstractTreeNode node)
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
			
			int insertIndex = insertNode(ecsNode);
			
			return new NewNode<>(insertIndex, ecsNode);
		}

		@Override
		protected void determineChildren()
		{
			children = ecsList
					.stream()
					.map(ecs -> new EventCriteriaSetTreeNode(this, ecs))
					.sorted(childrenOrder)
					.toArray(AbstractTreeNode[]::new);
		}
	}

	private class EventCriteriaSetTreeNode extends AbstractTreeNode
	{
		private static final Comparator<AbstractTreeNode> ORDER = Comparator
				.<AbstractTreeNode,Integer>comparing( node -> {
					if (node instanceof DescriptionTreeNode) return 0;
					if (node instanceof StationTreeNode) return 1;
					return 2;
				} )
				.thenComparing(SORT_BY_TITLE);
		
		private final EventCriteriaSet ecs;

		EventCriteriaSetTreeNode(AbstractTreeNode parent, EventCriteriaSet ecs)
		{
			super(parent, generateTitle(ecs.title(), ecs.variableData()), ecs.stations()!=null || ecs.descriptions()!=null, ORDER);
			this.ecs = ecs;
		}

		@Override int removeNode(AbstractTreeNode node)
		{
			if (node instanceof DescriptionTreeNode)
				return super.removeNode(node);
			throw new UnsupportedOperationException();
		}

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
				return TreeIcons.TitleEp;
			return TreeIcons.Title;
		}

		@Override
		protected void determineChildren()
		{
			Vector<AbstractTreeNode> childrenVec = new Vector<>();
			
			if (ecs.descriptions()!=null)
			{
				Map<String, DescriptionData> descriptions = ecs.descriptions();
				childrenVec.addAll( descriptions.keySet()
						.stream()
						.map(description -> new DescriptionTreeNode(this, new DescriptionChanger(description, descriptions)))
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
			
			children = childrenVec.toArray(AbstractTreeNode[]::new);
		}
	}
	
	private class DescriptionTreeNode extends AbstractTreeNode
	{
		private final DescriptionChanger description;

		DescriptionTreeNode(AbstractTreeNode parent, DescriptionChanger description)
		{
			super(parent, generateTitle(description), false, null);
			this.description = Objects.requireNonNull(description);
			children = new AbstractTreeNode[0];
		}

		@Override
		protected void updateTitle()
		{
			title = generateTitle(description);
		}

		@Override
		protected TreeIcons getIcon()
		{
			DescriptionData data = description.getData();
			if (data != null)
			{
				TextOperator operator = data.operator!=null ? data.operator : TextOperator.Equals;
				switch (operator)
				{
				case Equals    : return data.hasEpisodeStr() ? TreeIcons.DescEp         : TreeIcons.Desc;
				case StartsWith: return data.hasEpisodeStr() ? TreeIcons.DescStartEp    : TreeIcons.DescStart;
				case Contains  : return data.hasEpisodeStr() ? TreeIcons.DescContainsEp : TreeIcons.DescContains;
				}
			}
			return TreeIcons.Desc;
		}

		@Override
		protected Color getTextColor()
		{
			DescriptionData data = description.getData();
			if (data != null)
			{
				TextOperator operator = data.operator!=null ? data.operator : TextOperator.Equals;
				switch (operator)
				{
				case Equals    : break;
				case StartsWith: return UserDefColors.TXTCOLOR_DescStartsWith.getColor();
				case Contains  : return UserDefColors.TXTCOLOR_DescContains  .getColor();
				}
			}
			return null;
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
			super(parent, station, stationData.descriptions()!=null, SORT_BY_TITLE);
			this.station = station;
			this.stationData = stationData;
		}
		
		@Override int removeNode(AbstractTreeNode node)
		{
			if (node instanceof DescriptionTreeNode)
				return super.removeNode(node);
			throw new UnsupportedOperationException();
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
				Map<String, DescriptionData> descriptions = stationData.descriptions();
				children = descriptions.keySet()
						.stream()
						.map(description -> new DescriptionTreeNode(this, new DescriptionChanger(description, descriptions)))
						.sorted(childrenOrder)
						.toArray(AbstractTreeNode[]::new);
			}
			else
				children = new AbstractTreeNode[0];
		}
	}
	
	private static class DescriptionChanger
	{
		private String descText;
		private Map<String, DescriptionData> descMap;
		private final DescriptionData descData;

		DescriptionChanger(String initialDesc, Map<String, DescriptionData> descMap)
		{
			this.descText = Objects.requireNonNull(initialDesc);
			this.descMap  = Objects.requireNonNull(descMap);
			this.descData = Objects.requireNonNull(this.descMap.get(initialDesc),"Given map (descMap) doesn't contains an entry for given key (initialDesc).");
		}
		
		boolean removeFromMap()
		{
			return descMap.remove(descText)!=null;
		}

		DescriptionData getData()
		{
			return descData;
		}
		
		String getText()
		{
			return descText;
		}
		
		record Response(boolean success, String reasonWhyNot)
		{
			Response(boolean success) { this(success, null); }
		}
		
		Response setText(String descText)
		{
			if (descText==null)
				return new Response(false, "<Null> is not allowed as description text.");
			
			if (descText.isEmpty())
				return new Response(false, "An empty string (\"\") is not allowed as description text.");
			
			if (descText.isBlank())
				return new Response(false, "A blank string (\"%s\") is not allowed as description text.".formatted(descText));
			
			if (descMap.containsKey(descText))
				return new Response(false, "Anbother rule with same description text (\"%s\") already exists.".formatted(descText));
			
			descMap.remove( this.descText );
			this.descText = descText;
			descMap.put( this.descText, descData );
			
			return new Response(true);
		}
	}
}
