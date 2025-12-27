package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Comparator;
import java.util.Objects;

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
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.TreeNodeFactory.AbstractTreeNode;
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
		).addTo(tree);
		
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

	void rebuildTree()
	{
		tree.setModel(treeModel = new ViewerTreeModel(tree, factory));
	}
	
	public static void showViewer(Window parent, String title)
	{
		new AlreadySeenEventsViewer(parent, title).showDialog();
	}
	
	void editEpisodeStr(Window window, SelectionInfo selected)
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
	
	enum KeyFunction
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
