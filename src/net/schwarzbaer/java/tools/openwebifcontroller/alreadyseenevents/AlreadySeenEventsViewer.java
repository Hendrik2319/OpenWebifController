package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.DescriptionData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.DescriptionMaps;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.TextOperator;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.AbstractTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.DescriptionChanger;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.DescriptionTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.ECSGroupTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.EventCriteriaSetTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.RootTreeNode;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.StationTreeNode;

public class AlreadySeenEventsViewer extends StandardDialog
{
	private static final long serialVersionUID = 6089627717117385916L;
	static final Comparator<String> stringComparator = Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder());
	
	static enum TreeIcons {
		Title, TitleEp, Station, Desc, DescEp, DescContains, DescContainsEp, DescStart, DescStartEp
		;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<TreeIcons> IS = IconSource.createCachedIcons(16, 16, "/images/AlreadySeenEventsViewerTreeIcons.png", TreeIcons.values());
	}
	
	private final JTree tree;
	private ViewerTreeModel treeModel;
	private final TreeNodeFactory factory;

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		factory = new TreeNodeFactory();
		
		tree = new JTree();
		tree.setModel(treeModel = new ViewerTreeModel(tree, AlreadySeenEvents.getInstance().createTreeRoot(this)));
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
		tree.setModel(treeModel = new ViewerTreeModel(tree, AlreadySeenEvents.getInstance().createTreeRoot(this)));
	}
	
	public static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}

	RootTreeNode createTreeRoot(Map<String, EventCriteriaSet> data)
	{
		return factory.createRootTreeNode( data, ViewerTreeModel.getCurrentRootSubnodeOrder() );
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

	private void reorderSiblings(SelectionInfo selected)
	{
		treeModel.reorderSiblings(selected.treeNode);
		tree.repaint();
	}

	private void copyNodeTitle(SelectionInfo selectionInfo)
	{
		if (selectionInfo.groupTreeNode!=null)
			ClipboardTools.copyStringSelectionToClipBoard(selectionInfo.groupTreeNode.groupName);
		
		if (selectionInfo.ecsTreeNode!=null)
			ClipboardTools.copyStringSelectionToClipBoard(selectionInfo.ecsTreeNode.ecs.title());
		
		if (selectionInfo.stationTreeNode!=null)
			ClipboardTools.copyStringSelectionToClipBoard(selectionInfo.stationTreeNode.station);
		
		if (selectionInfo.descriptionTreeNode!=null)
			ClipboardTools.copyStringSelectionToClipBoard(selectionInfo.descriptionTreeNode.description.getText());
	}

	private static class SelectionInfo
	{
		final TreePath                 path;
		final AbstractTreeNode         treeNode;
		final RootTreeNode             rootTreeNode;
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
			rootTreeNode        = treeNode     instanceof RootTreeNode             treeNode ? treeNode : null;
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
		EditEpisodeStr (KeyEvent.VK_F4),
		ReorderSiblings(KeyEvent.VK_F5),
		CopyNodeTitle(KeyEvent.VK_C, false, true, false, false, "Ctrl+C"),
		;
		private final int keyCode;
		private final boolean withShift;
		private final boolean withCtrl;
		private final boolean withAlt;
		private final boolean withAltGr;
		private final String keyLabel;
		
		KeyFunction(int keyCode) { this(keyCode, false, false, false, false, getKeyText(keyCode, false, false, false, false)); }
		KeyFunction(
				int keyCode,
				boolean withShift,
				boolean withCtrl ,
				boolean withAlt  ,
				boolean withAltGr,
				String keyLabel
		) {
			this.keyCode   = keyCode;
			this.withShift = withShift;
			this.withCtrl  = withCtrl;
			this.withAlt   = withAlt;
			this.withAltGr = withAltGr;
			this.keyLabel  = Objects.requireNonNull(keyLabel);
		}
		
		static String getKeyText(
				int keyCode,
				boolean withShift,
				boolean withCtrl ,
				boolean withAlt  ,
				boolean withAltGr
		) {
			StringBuilder sb = new StringBuilder();
			if (withCtrl ) sb.append("Ctrl+");
			if (withAlt  ) sb.append("Alt+");
			if (withShift) sb.append("Shift+");
			if (withAltGr) sb.append("AltGr+");
			sb.append(KeyEvent.getKeyText(keyCode));
			return sb.toString();
		}
		static KeyFunction getFrom(int keyCode, int modifiersEx)
		{
			boolean withShift = (modifiersEx & KeyEvent.SHIFT_DOWN_MASK    ) != 0;
			boolean withCtrl  = (modifiersEx & KeyEvent.CTRL_DOWN_MASK     ) != 0;
			boolean withAlt   = (modifiersEx & KeyEvent.ALT_DOWN_MASK      ) != 0;
			boolean withAltGr = (modifiersEx & KeyEvent.ALT_GRAPH_DOWN_MASK) != 0;
			for (KeyFunction val : values())
				if ( (val.keyCode   == keyCode  ) &&
					 (val.withShift == withShift) &&
					 (val.withCtrl  == withCtrl ) &&
					 (val.withAlt   == withAlt  ) &&
					 (val.withAltGr == withAltGr) )
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
			KeyFunction keyFunction = KeyFunction.getFrom(e.getKeyCode(), e.getModifiersEx());
			if (keyFunction==null) return;
			
			switch (keyFunction)
			{
			case EditEpisodeStr:
				editEpisodeStr(AlreadySeenEventsViewer.this, selected);
				break;
				
			case ReorderSiblings:
				reorderSiblings(selected);
				break;
				
			case CopyNodeTitle:
				copyNodeTitle(selected);
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
				copyNodeTitle(clicked);
			} ) );
			
			JMenuItem miEditEpisodeStr = add( OpenWebifController.createMenuItem( "##", e->{
				editEpisodeStr(this.window, clicked);
			} ) );
			
			add(OpenWebifController.createCheckBoxMenuItem("Show Episode Text before Title", factory.isEpisodeStringFirst(), val -> {
				factory.setEpisodeStringFirst(val);
				rebuildTree();
			} ));
			
			addSeparator();
			
			JMenuItem miEditDesc = add(OpenWebifController.createMenuItem("Edit Description Text", e -> {
				if (clicked.descriptionTreeNode==null) return;
				String newDesc = TextAreaDialog.editText(this.window, "Edit Description Text", 400, 200, true, clicked.descriptionTreeNode.description.getText());
				if (newDesc!=null)
				{
					DescriptionChanger.Response response = clicked.descriptionTreeNode.description.setText(newDesc);
					if (response.success())
					{
						clicked.descriptionTreeNode.updateTitle();
						treeModel.fireTreeNodeUpdate(clicked.descriptionTreeNode);
						tree.repaint();
						AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
					}
					else
					{
						String[] msg = { "Can't change description text:", response.reasonWhyNot() };
						String title = "Can't change";
						JOptionPane.showMessageDialog(this.window, msg, title, JOptionPane.WARNING_MESSAGE);
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
			
			JMenuItem miAddNode = add( OpenWebifController.createMenuItem( "##", GrayCommandIcons.IconGroup.Add , e->{
				if (clicked.rootTreeNode!=null)
				{
					createNewECSNode(null);
				}
				if (clicked.groupTreeNode!=null)
				{
					createNewECSNode(clicked.groupTreeNode);
				}
				if (clicked.ecsTreeNode!=null)
				{
					//createNewDescNode(clicked.ecsTreeNode, clicked.ecsTreeNode.ecs.descriptions());
					// TODO: add descriptionTreeNode
				}
				if (clicked.stationTreeNode!=null)
				{
					// TODO: add descriptionTreeNode
				}
				//if (clicked.descriptionTreeNode!=null)
				//{
				//}
			} ) ); 
			
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
				reorderSiblings(clicked);
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

		private void createNewECSNode(ECSGroupTreeNode groupTreeNode)
		{
			String title = askUserForTitleForNewECSNode();
			if (title!=null)
			{
				treeModel.createNewECSNode(title, groupTreeNode);
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
			}
		}

		@SuppressWarnings("unused")
		private void createNewDescNode(AbstractTreeNode parent, DescriptionMaps descriptions)
		{
			Boolean isExtDesc = askUserIfNewDescNodeIsExt();
			if (isExtDesc!=null)
			{
				Map<String, DescriptionData> descriptionsMap = isExtDesc.booleanValue() ? descriptions.extended : descriptions.standard;
				String title = askUserForTitleForNewDescNode( descriptionsMap );
				
			}
			// TODO: add descriptionTreeNode
			//parent.insertNode(parent)
			// TODO Auto-generated method stub
			
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

		private String askUserForTitleForNewDescNode(Map<String, DescriptionData> descriptions)
		{
			return askUser(
					"New description",
					"Pleasae enter a description for new DescriptionNode:",
					str -> !descriptions.containsKey(str),
					"Already exists",
					"Sorry, this description already exists."
			);
		}

		private String askUserForTitleForNewECSNode()
		{
			return askUser(
					"New ECSNode",
					"Pleasae enter a title for new ECSNode:",
					str -> !treeModel.treeRoot.data.containsKey(str),
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
}
