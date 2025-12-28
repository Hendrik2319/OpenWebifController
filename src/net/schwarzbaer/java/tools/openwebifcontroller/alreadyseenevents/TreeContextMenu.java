package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.awt.Window;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTree;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.DescriptionData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.DescriptionMaps;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.TextOperator;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEventsViewer.KeyFunction;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEventsViewer.SelectionInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.AbstractTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.DescriptionChanger;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.DescriptionTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.ECSGroupTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.EventCriteriaSetTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.RootTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.StationTreeNode;

class TreeContextMenu extends ContextMenu
{
	private static final long serialVersionUID = -3347262887544423895L;
	
	private final AlreadySeenEventsViewer viewer;
	private final Window window;
	private final TreeNodeFactory factory;
	private final JTree tree;
	private final Supplier<ViewerTreeModel> getCurrentTreeModel;
	private final JMenu menuMoveToGroup;
	private SelectionInfo clicked;
	
	TreeContextMenu(AlreadySeenEventsViewer viewer, TreeNodeFactory factory, JTree tree, Supplier<ViewerTreeModel> getCurrentTreeModel)
	{
		this.viewer  = viewer;
		this.window  = this.viewer;
		this.factory = factory;
		this.tree    = tree;
		this.getCurrentTreeModel = getCurrentTreeModel;
		clicked = new SelectionInfo(null);
		
		JMenuItem miCopyStr = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Copy, e->{
			this.viewer.copyNodeTitle(clicked);
		} ) );
		
		JMenuItem miEditEpisodeStr = add( OpenWebifController.createMenuItem( "##", e->{
			this.viewer.editEpisodeStr(window, clicked);
		} ) );
		
		add(OpenWebifController.createCheckBoxMenuItem("Show Episode Text before Title", this.factory.isEpisodeStringFirst(), val -> {
			this.factory.setEpisodeStringFirst(val);
			this.viewer.rebuildTree();
		} ));
		
		addSeparator();
		
		JMenuItem miEditDesc = add(OpenWebifController.createMenuItem("Edit Description Text", e -> {
			if (clicked.descriptionTreeNode==null) return;
			String newDesc = TextAreaDialog.editText(window, "Edit Description Text", 400, 200, true, clicked.descriptionTreeNode.description.getText());
			if (newDesc!=null)
			{
				DescriptionChanger.Response response = clicked.descriptionTreeNode.description.setText(newDesc);
				if (response.success())
				{
					clicked.descriptionTreeNode.updateTitle();
					this.getCurrentTreeModel.get().fireTreeNodeUpdate(clicked.descriptionTreeNode);
					this.tree.repaint();
					AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
				}
				else
				{
					String[] msg = { "Can't change description text:", response.reasonWhyNot() };
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
					this.getCurrentTreeModel.get().fireTreeNodeUpdate(clicked.descriptionTreeNode);
					this.tree.repaint();
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
			this.getCurrentTreeModel.get().removeEcsTreeNodeFromGroup( clicked.ecsTreeNode );
			AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
		} ) );
		
		JMenuItem miRenameGroup = add( OpenWebifController.createMenuItem( "##", e->{
			String result = JOptionPane.showInputDialog(window, "New group name", clicked.groupTreeNode.groupName);
			if (result==null || result.isBlank() || result.equals(clicked.groupTreeNode.groupName)) return;
			if (this.getCurrentTreeModel.get().isExistingGroupName( result ))
			{
				String[] msg = {
						"A group with name \"%s\" exists already.".formatted( result ),
						"Please choose anonther name."
				};
				JOptionPane.showMessageDialog(window, msg, "Group already exists", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			this.getCurrentTreeModel.get().renameGroup( clicked.groupTreeNode, result );
			updateMenuMoveToGroup();
			AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
		} ) );
		
		addSeparator();
		
		JMenuItem miAddNode = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Add , e->{
			if (clicked.rootTreeNode!=null)
			{
				createNewECSNode(clicked.rootTreeNode);
			}
			if (clicked.groupTreeNode!=null)
			{
				createNewECSNode(clicked.groupTreeNode);
			}
			if (clicked.ecsTreeNode!=null && clicked.ecsTreeNode.ecs.descriptions()!=null)
			{
				createNewDescNode(clicked.ecsTreeNode);
			}
			if (clicked.stationTreeNode!=null && clicked.stationTreeNode.stationData.descriptions()!=null)
			{
				createNewDescNode(clicked.stationTreeNode);
			}
			//if (clicked.descriptionTreeNode!=null)
			//{
			//}
		} ) ); 
		
		JMenuItem miDeleteNode = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Delete, e->{
			if (clicked.groupTreeNode!=null)
			{
				this.getCurrentTreeModel.get().deleteGroup( clicked.groupTreeNode );
				updateMenuMoveToGroup();
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.Grouping);
			}
			if (clicked.ecsTreeNode!=null)
			{
				// TODO: delete ecsTreeNode
			}
			if (clicked.stationTreeNode!=null)
			{
				// TODO: delete stationTreeNode
			}
			if (clicked.descriptionTreeNode!=null)
			{
				boolean success = this.getCurrentTreeModel.get().deleteDescNode(clicked.descriptionTreeNode);
				if (success)
					AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
			}
		} ) );
		
		addSeparator();
		
		JMenuItem miReorderSiblings = add( OpenWebifController.createMenuItem( "##", e->{
			this.viewer.reorderSiblings(clicked);
		} ) );
		
		JMenu menuRootOrder = OpenWebifController.createMenu("Order of subnodes of root");
		add(menuRootOrder);
		EnumMap<RootTreeNode.NodeOrder, JCheckBoxMenuItem> mapMiRootOrder = new EnumMap<>(RootTreeNode.NodeOrder.class);
		for (RootTreeNode.NodeOrder order : RootTreeNode.NodeOrder.values())
		{
			JCheckBoxMenuItem checkBoxMenuItem = OpenWebifController.createCheckBoxMenuItem(order.title, false, b -> this.getCurrentTreeModel.get().setRootSubnodeOrder(order));
			menuRootOrder.add(checkBoxMenuItem);
			mapMiRootOrder.put(order, checkBoxMenuItem);
		}
		
		add(OpenWebifController.createMenuItem("Rebuild Tree", GrayCommandIcons.IconGroup.Reload, e -> {
			this.viewer.rebuildTree();
		}));
		
		addContextMenuInvokeListener((comp, x, y) -> {
			clicked = new SelectionInfo( this.tree.getPathForLocation(x, y) );
			this.tree.setSelectionPath(clicked.path);
			
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
			miCopyStr.setText( KeyFunction.CopyNodeTitle.addKeyLabel(
					clicked.groupTreeNode!=null
						? "Copy group name to clipboard"
						: clicked.ecsTreeNode!=null
							? "Copy title to clipboard"
							: clicked.stationTreeNode!=null
								? "Copy station name to clipboard"
								: clicked.descriptionTreeNode!=null
									? "Copy description to clipboard"
									: "Copy text to clipboard"
			) );
			
			AbstractTreeNode parent = clicked.treeNode!=null ? clicked.treeNode.parent : null;
			miReorderSiblings.setEnabled(parent!=null);
			miReorderSiblings.setText( KeyFunction.ReorderSiblings.addKeyLabel(
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
			) );
			
			menuMoveToGroup.setEnabled(clicked.ecsTreeNode!=null && !ViewerTreeModel.isInGroup(clicked.ecsTreeNode));
			
			miRemoveFromGroup.setEnabled(clicked.ecsTreeNode!=null && ViewerTreeModel.isInGroup(clicked.ecsTreeNode));
			miRemoveFromGroup.setText(
					clicked.ecsTreeNode!=null && ViewerTreeModel.isInGroup(clicked.ecsTreeNode)
						? "Remove from group \"%s\"".formatted( ViewerTreeModel.getGroupName(clicked.ecsTreeNode) )
						: "Remove from group"
			);
			
			miRenameGroup.setEnabled(clicked.groupTreeNode!=null);
			miRenameGroup.setText(
					clicked.groupTreeNode!=null
						? "Rename group \"%s\"".formatted( clicked.groupTreeNode.groupName )
						: "Rename group"
			);
			
			miAddNode.setEnabled(
					clicked.rootTreeNode !=null ||
					clicked.groupTreeNode!=null ||
					( clicked.ecsTreeNode    !=null && clicked.ecsTreeNode    .ecs        .descriptions()!=null ) ||
					( clicked.stationTreeNode!=null && clicked.stationTreeNode.stationData.descriptions()!=null ) //||
					//clicked.descriptionTreeNode!=null
			);
			miAddNode.setText(
					clicked.rootTreeNode!=null || clicked.groupTreeNode!=null
						? "Add title"
						: clicked.ecsTreeNode!=null && clicked.ecsTreeNode.ecs.descriptions()!=null 
							? "Add description"
							: clicked.stationTreeNode!=null && clicked.stationTreeNode.stationData.descriptions()!=null
								? "Add description"
								//: clicked.descriptionTreeNode!=null
									//? "Add ???"
									: "Add node"
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
									? "Delete description \"%s\"".formatted( clicked.descriptionTreeNode.description.getReducedText(50) )
									: "Delete"
			);
			
			RootTreeNode.NodeOrder currentOrder = ViewerTreeModel.getCurrentRootSubnodeOrder();
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

	private <ParentNode extends AbstractTreeNode & EventCriteriaSetTreeNode.HostNode>
	void createNewECSNode(ParentNode parent)
	{
		Objects.requireNonNull(parent);
		
		String title = askUserForTitleForNewECSNode();
		if (title == null)
			return;
		
		getCurrentTreeModel.get().createNewECSNode(parent, title);
		AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
	}

	private <ParentNode extends AbstractTreeNode & DescriptionTreeNode.HostNode>
	void createNewDescNode(ParentNode parent)
	{
		Objects.requireNonNull(parent);
		
		DescriptionMaps descriptions = parent.getDescriptions();
		if (descriptions==null)
			return;
		
		Boolean isExtDesc = askUserIfNewDescNodeIsExt();
		if (isExtDesc == null)
			return;
		
		Map<String, DescriptionData> descMap = isExtDesc.booleanValue() ? descriptions.extended : descriptions.standard;
		String description = askUserForTextForNewDescNode( descMap );
		if (description == null)
			return;
		
		getCurrentTreeModel.get().createNewDescNode(parent, description, isExtDesc, descMap);
		AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
	}

	private Boolean askUserIfNewDescNodeIsExt()
	{
		String title = "New description";
		String message = "Is new description an extended description or a standard description?";
		String[] options = { "Extended", "Standard", "Cancel" };
		int result = JOptionPane.showOptionDialog(window, message, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		switch (result) {
		case 0:  return true;
		case 1:  return false;
		default: return null;
		}
	}

	private String askUserForTextForNewDescNode(Map<String, DescriptionData> descriptions)
	{
		return askUser(
				"New description",
				"Please enter a text for new DescriptionNode:",
				str -> !descriptions.containsKey(str),
				"Already exists",
				"Sorry, this description already exists."
		);
	}

	private String askUserForTitleForNewECSNode()
	{
		AlreadySeenEvents data = AlreadySeenEvents.getInstance();
		return askUser(
				"New ECSNode",
				"Pleasae enter a title for new ECSNode:",
				str -> !data.containsECS(str),
				"Already exists",
				"Sorry, this title already exists."
		);
	}

	private String askUser(String inputTitle, Object inputMsg, Predicate<String> isAllowed, String notAllowedTitle, Object notAllowedMsg)
	{
		String string = "";
		boolean isStrAllowed = false;
		
		while (!isStrAllowed && string!=null)
		{
			string = JOptionPane.showInputDialog(window, inputMsg, inputTitle, JOptionPane.QUESTION_MESSAGE);
			if (string!=null)
			{
				isStrAllowed = isAllowed.test(string);
				if (!isStrAllowed)
					JOptionPane.showMessageDialog(window, notAllowedMsg, notAllowedTitle, JOptionPane.INFORMATION_MESSAGE);
			}
		}
		
		return string;
	}

	private void updateMenuMoveToGroup()
	{
		menuMoveToGroup.removeAll();
		Collection<String> groupNames = getCurrentTreeModel.get().getGroupNames();
		if (groupNames==null)
			return;
		
		Vector<String> groupNamesVec = new Vector<>(groupNames);
		groupNamesVec.sort(AlreadySeenEventsViewer.stringComparator);
		
		for (String groupName : groupNamesVec)
		{
			menuMoveToGroup.add( OpenWebifController.createMenuItem( groupName, e->{
				getCurrentTreeModel.get().moveEcsTreeNodeToGroup( groupName, clicked.ecsTreeNode );
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
			getCurrentTreeModel.get().moveEcsTreeNodeToGroup( groupName, clicked.ecsTreeNode );
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