package net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.UserDefColors;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.DescriptionData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EpisodeInfo;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.EventCriteriaSet;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.StationData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.TextOperator;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents.VariableECSData;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEventsViewer.TreeIcons;

class TreeNodeFactory
{
	private boolean episodeStringFirst;
	
	TreeNodeFactory()
	{
		episodeStringFirst = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_EpisodeStringFirst, false);
	}
	
	boolean isEpisodeStringFirst()
	{
		return episodeStringFirst;
	}

	void setEpisodeStringFirst(Boolean val)
	{
		episodeStringFirst = val;
		OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.AlreadySeenEventsViewer_EpisodeStringFirst, isEpisodeStringFirst());
	}
	
	RootTreeNode createRootTreeNode(RootTreeNode.NodeOrder currentRootSubnodeOrder)
	{
		return new RootTreeNode(currentRootSubnodeOrder);
	}
	
	private String generateTitle(DescriptionChanger description)
	{
		if (description==null)
			return "<null>";
		
		return generateTitle(
				description.getText(),
				description.getData()
		);
	}
	
	private String generateTitle(String description, DescriptionData data)
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
	
	private String generateTitle(String title, EpisodeInfo episode)
	{
		if (episode == null || !episode.hasEpisodeStr())
			return title;
		if (isEpisodeStringFirst())
			return String.format("(%s) %s", episode.episodeStr, title);
		else
			return String.format("%s (%s)", title, episode.episodeStr);
	}
	
	private List<DescriptionTreeNode> createDescriptionTreeNodes(AbstractTreeNode parent, Map<String, DescriptionData> descriptions, boolean isExtDesc)
	{
		return descriptions.keySet()
				.stream()
				.map(description -> new DescriptionTreeNode(parent, new DescriptionChanger(description, descriptions), isExtDesc))
				.sorted(AbstractTreeNode.SORT_BY_TITLE)
				.toList();
	}
	
	record NewNode<NodeType extends AbstractTreeNode> (
			int index,
			NodeType node
	) {}
	
	abstract class AbstractTreeNode implements TreeNode
	{
		static final Comparator<AbstractTreeNode> SORT_BY_TITLE = Comparator.comparing(node -> node.title, AlreadySeenEventsViewer.stringComparator);
		
		protected final AbstractTreeNode parent;
		protected       String title;
		protected final boolean allowsChildren;
		protected       AbstractTreeNode[] children;
		protected       Comparator<AbstractTreeNode> childrenOrder;
		
		protected AbstractTreeNode(AbstractTreeNode parent, String title, boolean allowsChildren, Comparator<AbstractTreeNode> childrenOrder)
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
	
	class RootTreeNode extends AbstractTreeNode implements EventCriteriaSetTreeNode.HostNode
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
			final String title;
			private final Comparator<AbstractTreeNode> order;
			NodeOrder(String title, Comparator<AbstractTreeNode> order)
			{
				this.title = title;
				this.order = order;
			}
		}
		
		final Map<String, ECSGroupTreeNode> groupNodes;
		
		protected RootTreeNode(NodeOrder subnodeOrder)
		{
			super(null, "Already Seen Events", true, Objects.requireNonNull(subnodeOrder).order);
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
		
		@Override
		public NewNode<EventCriteriaSetTreeNode> createECSNode(EventCriteriaSet ecs)
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
			
			AlreadySeenEvents.getInstance().forEachECS((title, ecs) -> {
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
	
	class ECSGroupTreeNode extends AbstractTreeNode implements EventCriteriaSetTreeNode.HostNode
	{
		private final Vector<EventCriteriaSet> ecsList;
		String groupName;
		
		protected ECSGroupTreeNode(RootTreeNode parent, String groupName)
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
		
		@Override
		public NewNode<EventCriteriaSetTreeNode> createECSNode(EventCriteriaSet ecs)
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
	
	class EventCriteriaSetTreeNode extends AbstractTreeNode
	{
		private static final Comparator<AbstractTreeNode> ORDER = Comparator
				.<AbstractTreeNode,Integer>comparing( node -> {
					if (node instanceof DescriptionTreeNode) return 0;
					if (node instanceof StationTreeNode) return 1;
					return 2;
				} )
				.thenComparing(SORT_BY_TITLE);
		
		interface HostNode
		{
			NewNode<EventCriteriaSetTreeNode> createECSNode(EventCriteriaSet ecs);
		}
		
		final EventCriteriaSet ecs;
		
		protected EventCriteriaSetTreeNode(AbstractTreeNode parent, EventCriteriaSet ecs)
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
				childrenVec.addAll( createDescriptionTreeNodes(this, ecs.descriptions().standard, false) );
				childrenVec.addAll( createDescriptionTreeNodes(this, ecs.descriptions().extended, true ) );
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
	
	class DescriptionTreeNode extends AbstractTreeNode
	{
		final DescriptionChanger description;
		private final boolean isExtDesc;
		
		protected DescriptionTreeNode(AbstractTreeNode parent, DescriptionChanger description, boolean isExtDesc)
		{
			super(parent, "###", false, null);
			this.description = Objects.requireNonNull(description);
			this.isExtDesc = isExtDesc;
			children = new AbstractTreeNode[0];
			updateTitle();
		}
		
		@Override
		protected void updateTitle()
		{
			String prefix = isExtDesc ? "[E] " : "";
			title = prefix + generateTitle(description);
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
	
	class StationTreeNode extends AbstractTreeNode
	{
		final StationData stationData;
		final String station;
		
		protected StationTreeNode(EventCriteriaSetTreeNode parent, String station, StationData stationData)
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
				List<DescriptionTreeNode> list = new ArrayList<>();
				list.addAll(createDescriptionTreeNodes(this, stationData.descriptions().standard, false) );
				list.addAll(createDescriptionTreeNodes(this, stationData.descriptions().extended, true ) );
				children = list.toArray(AbstractTreeNode[]::new);
			}
			else
				children = new AbstractTreeNode[0];
		}
	}
	
	static class DescriptionChanger
	{
		private String descText;
		private final Map<String, DescriptionData> descMap;
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
		
		String getReducedText(int maxLength)
		{
			if (descText.length() <= maxLength)
				return descText;
			
			int cutLength = Math.min( Math.max( maxLength-3, 4 ), maxLength );
			return "%s...".formatted( descText.substring(0, cutLength) );
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
