package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Comparator;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.KeyShortCut;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EpisodeState;
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
	
	static enum TreeIcon {
		Title  , TitleEp  , Station  , Desc  , DescEp  , DescContains  , DescContainsEp  , DescStart  , DescStartEp  ,
		Title_L, TitleEp_L, Station_L, Desc_L, DescEp_L, DescContains_L, DescContainsEp_L, DescStart_L, DescStartEp_L,
		Title_S, TitleEp_S, Station_S, Desc_S, DescEp_S, DescContains_S, DescContainsEp_S, DescStart_S, DescStartEp_S,
		;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<TreeIcon> IS = IconSource.createCachedIcons(16, 16, 9, "/images/AlreadySeenEventsViewerTreeIcons.png", TreeIcon.values());
	}
	
	static enum TreeIconsGroup {
		Title(
				TreeIcon.Title  , TreeIcon.TitleEp  ,
				TreeIcon.Title_L, TreeIcon.TitleEp_L,
				TreeIcon.Title_S, TreeIcon.TitleEp_S
		),
		Desc(
				TreeIcon.Desc  , TreeIcon.DescEp  ,
				TreeIcon.Desc_L, TreeIcon.DescEp_L,
				TreeIcon.Desc_S, TreeIcon.DescEp_S
		),
		DescContains(
				TreeIcon.DescContains  , TreeIcon.DescContainsEp  ,
				TreeIcon.DescContains_L, TreeIcon.DescContainsEp_L,
				TreeIcon.DescContains_S, TreeIcon.DescContainsEp_S
		),
		DescStart(
				TreeIcon.DescStart  , TreeIcon.DescStartEp  ,
				TreeIcon.DescStart_L, TreeIcon.DescStartEp_L,
				TreeIcon.DescStart_S, TreeIcon.DescStartEp_S
		),
		;
		private final TreeIcon base;
		private final TreeIcon baseEp;
		private final TreeIcon base_L;
		private final TreeIcon baseEp_L;
		private final TreeIcon base_S;
		private final TreeIcon baseEp_S;

		TreeIconsGroup(
				TreeIcon base  , TreeIcon baseEp  ,
				TreeIcon base_L, TreeIcon baseEp_L,
				TreeIcon base_S, TreeIcon baseEp_S
		) {
			this.base = base;
			this.baseEp = baseEp;
			this.base_L = base_L;
			this.baseEp_L = baseEp_L;
			this.base_S = base_S;
			this.baseEp_S = baseEp_S;
		}

		TreeIcon getIcon(EpisodeInfo episode)
		{
			if (episode!=null)
			{
				if (episode.state==null)
					return episode.hasEpisodeStr() ? baseEp : base;
				
				switch (episode.state)
				{
				case Seen  : return episode.hasEpisodeStr() ? baseEp   : base  ;
				case Listed: return episode.hasEpisodeStr() ? baseEp_L : base_L;
				case Stored: return episode.hasEpisodeStr() ? baseEp_S : base_S;
				}
			}
			return base;
		}
	}
	
	private final JTree tree;
	private ViewerTreeModel treeModel;
	private final TreeNodeFactory factory;

	private AlreadySeenEventsViewer(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		factory = new TreeNodeFactory();
		
		tree = new JTree();
		tree.setModel(treeModel = new ViewerTreeModel(tree, factory));
		tree.setCellRenderer(new TCR());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		TreeKeyListener treeKeyListener = new TreeKeyListener();
		tree.addKeyListener(treeKeyListener);
		tree.addTreeSelectionListener(ev -> treeKeyListener.selected = new SelectionInfo(tree.getSelectionPath()));
		
		new TreeContextMenu(
				this,
				factory,
				tree,
				() -> treeModel
		).addTo(tree, () -> TreeContextMenu.computeSurrogateMousePos(tree,treeScrollPane));
		
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
				OWCTools.createButton("Close", true, e->{
					closeDialog();
				})
		);
	}

	void rebuildTree()
	{
		tree.setModel(treeModel = new ViewerTreeModel(tree, factory));
	}
	
	public static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}
	
	void editEpisodeStr(SelectionInfo selected)
	{
		if (selected.episodeInfo==null)
			return;
		
		String episodeStr = !selected.episodeInfo.hasEpisodeStr() ? "" : selected.episodeInfo.episodeStr;
		String result = JOptionPane.showInputDialog(this, "Episode Text", episodeStr);
		if (result!=null)
		{
			selected.episodeInfo.episodeStr = result;
			selected.treeNode.updateTitle();
			treeModel.fireTreeNodeUpdate(selected.treeNode);
			tree.repaint();
			AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.EpisodeText);
		}
	}

	void setEpisodeState(SelectionInfo selected, EpisodeState state)
	{
		if (selected.episodeInfo==null)
			return;
		
		selected.episodeInfo.state = state;
		selected.treeNode.updateTitle();
		treeModel.fireTreeNodeUpdate(selected.treeNode);
		tree.repaint();
		AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.EpisodeState);
	}

	void editDescriptionText(SelectionInfo selected)
	{
		if (selected.descriptionTreeNode==null) return;
		String newDesc = TextAreaDialog.editText(this, "Edit Description Text", 400, 200, true, selected.descriptionTreeNode.description.getText());
		if (newDesc!=null)
		{
			DescriptionChanger.Response response = selected.descriptionTreeNode.description.setText(newDesc);
			if (response.success())
			{
				selected.descriptionTreeNode.updateTitle();
				treeModel.fireTreeNodeUpdate(selected.descriptionTreeNode);
				tree.repaint();
				AlreadySeenEvents.getInstance().writeToFileAndNotify(AlreadySeenEvents.ChangeListener.ChangeType.RuleSet);
			}
			else
			{
				String[] msg = { "Can't change description text:", response.reasonWhyNot() };
				String title = "Can't change";
				JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE);
			}
		}
	}

	void reorderSiblings(SelectionInfo selected)
	{
		treeModel.reorderSiblings(selected.treeNode);
		tree.repaint();
	}

	void copyNodeTitle(SelectionInfo selectionInfo)
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

	static class SelectionInfo
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
	
	enum KeyFunction implements KeyShortCut.Container
	{
		EditEpisodeStr (new KeyShortCut(KeyEvent.VK_F4)),
		EditDescText   (new KeyShortCut(KeyEvent.VK_F2)),
		ReorderSiblings(new KeyShortCut(KeyEvent.VK_F5)),
		CopyNodeTitle  (new KeyShortCut(KeyEvent.VK_C, false, true, false, false)),
		;
		final KeyShortCut keyShortCut;
		KeyFunction(KeyShortCut keyShortCut)
		{
			this.keyShortCut = keyShortCut;
		}
		@Override public KeyShortCut getKeyShortCut()
		{
			return keyShortCut;
		}
		static KeyFunction getFrom(KeyEvent e)
		{
			return KeyShortCut.getFrom(e, values());
		}
	}

	private class TreeKeyListener implements KeyListener
	{
		SelectionInfo selected = new SelectionInfo(null);

		@Override public void keyTyped   (KeyEvent e) {}
		@Override public void keyReleased(KeyEvent e) {}

		@Override public void keyPressed(KeyEvent e)
		{
			KeyFunction keyFunction = KeyFunction.getFrom(e);
			if (keyFunction==null) return;
			
			switch (keyFunction)
			{
			case EditEpisodeStr:
				editEpisodeStr(selected);
				break;
				
			case EditDescText:
				editDescriptionText(selected);
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

	private static class TCR extends DefaultTreeCellRenderer
	{
		private static final long serialVersionUID = 849724659340192701L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int rowIndex, boolean hasFocus)
		{
			Component rendcomp = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, rowIndex, hasFocus);
			
			if (value instanceof AbstractTreeNode treeNode)
			{
				TreeIcon icon = treeNode.getIcon();
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
