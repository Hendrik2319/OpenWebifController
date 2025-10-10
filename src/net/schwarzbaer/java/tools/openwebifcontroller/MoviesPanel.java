package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.GetValueTableModelOutputter.OutputType;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.MovieList;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.ExtendedTextArea;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.TreeIcons;

class MoviesPanel extends JSplitPane {
	private static final long serialVersionUID = 3435419463730240276L;
	
	private static final DateTimeFormatter dtFormatter = new DateTimeFormatter();

	private final OpenWebifController main;
	
	private final JTree locationsTree;
	private final JTable movieTable;
	private final ExtendedTextArea movieInfo1;
	private final JScrollPane      movieInfo1ScrollPane;
	private final ExtendedTextArea movieInfo2;
	private final JScrollPane      movieInfo2ScrollPane;

	private LocationTreeNode locationsRoot;
	private DefaultTreeModel locationsTreeModel;
	private MovieTableModel movieTableModel;

	private TreePath selectedTreePath;
	private LocationTreeNode selectedTreeNode;

	MoviesPanel(OpenWebifController main) {
		super(HORIZONTAL_SPLIT, true);
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		this.main = main;
		locationsRoot = null;
		locationsTreeModel = null;
		selectedTreePath = null;
		selectedTreeNode = null;
		movieTableModel = null;
		
		locationsTree = new JTree(locationsTreeModel);
		locationsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		locationsTree.setCellRenderer(new LocationTreeCellRenderer());
		
		JScrollPane treeScrollPane = new JScrollPane(locationsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		movieTable = new JTable(movieTableModel);
		movieTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		movieTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		JScrollPane tableScrollPane = new JScrollPane(movieTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		new TableContextMenu().addTo(movieTable);
		new TreeContextMenu().addTo(locationsTree);
		
		movieInfo1 = new ExtendedTextArea(false);
		//movieInfo1.setLineWrap(true);
		//movieInfo1.setWrapStyleWord(true);
		movieInfo1ScrollPane = movieInfo1.createScrollPane(400,330);
		
		movieInfo2 = new ExtendedTextArea(false);
		movieInfo2.setLineWrap(true);
		movieInfo2.setWrapStyleWord(true);
		movieInfo2ScrollPane = movieInfo2.createScrollPane(400,300);
		
		JPanel movieInfoPanel = new JPanel(new BorderLayout(3,3));
		movieInfoPanel.add(movieInfo1ScrollPane,BorderLayout.NORTH);
		movieInfoPanel.add(movieInfo2ScrollPane,BorderLayout.CENTER);
		
		JPanel rightPanel = new JPanel(new BorderLayout(3,3));
		rightPanel.add(tableScrollPane,BorderLayout.CENTER);
		rightPanel.add(movieInfoPanel,BorderLayout.EAST);
		
		setLeftComponent(treeScrollPane);
		setRightComponent(rightPanel);
		
		
		locationsTree.addTreeSelectionListener(e -> {
			selectedTreePath = e.getPath();
			selectedTreeNode = null;
			if (selectedTreePath!=null) {
				Object lastPathComponent = selectedTreePath.getLastPathComponent();
				if (lastPathComponent instanceof LocationTreeNode)
					selectedTreeNode = (LocationTreeNode) lastPathComponent;
			}
			if (selectedTreeNode!=null) {
				Vector<MovieList.Movie> movies;
				if (selectedTreeNode.movies != null)
					movies = selectedTreeNode.movies;
				else {
					String path = selectedTreeNode.getDirPath();
					MovieList movieList = getMovieList(path);
					locationsRoot.addLocations(movieList,locationsTreeModel);
					movies = movieList.movies;
				}
				updateMovieTableModel(movies);
			}
		});
		
		movieTable.getSelectionModel().addListSelectionListener(e -> {
			int[] rowsM = getSelectedRowsM();
			showValues(rowsM.length == 1 ? movieTableModel.getRow(rowsM[0]) : null);
		});
		
		movieTable.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON1) {
					if (e.getClickCount()<2) return;
					int rowV = movieTable.rowAtPoint(e.getPoint());
					int rowM = movieTable.convertRowIndexToModel(rowV);
					showMovie(movieTableModel.getRow(rowM));
				}
				if (e.getButton()==MouseEvent.BUTTON3) {
					
				}
			}
		});
		
	}

	private int[] getSelectedRowsM()
	{
		int[] rowsV = movieTable.getSelectedRows();
		int rowCount = movieTable.getRowCount();	
		int[] rowsM = Arrays
				.stream(rowsV)
				.filter(i -> i>=0 && i<rowCount)
				.map(movieTable::convertRowIndexToModel)
				.toArray();
		return rowsM;
	}

	private class TreeContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = -7833563667506242088L;
		
		private TreePath clickedTreePath;
		private LocationTreeNode clickedTreeNode;

		TreeContextMenu()
		{
			clickedTreePath = null;
			clickedTreeNode = null;
			
			add(OpenWebifController.createMenuItem("Reload Movies", GrayCommandIcons.IconGroup.Reload, e->main.getBaseURLAndRunWithProgressDialog("Reload Movies", MoviesPanel.this::readInitialMovieList)));
			JMenuItem miReloadTreeNode = add(OpenWebifController.createMenuItem("Reload Folder", GrayCommandIcons.IconGroup.Reload, e->reloadTreeNode(clickedTreeNode)));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				clickedTreePath = locationsTree.getPathForLocation(x,y);
				clickedTreeNode = null;
				if (clickedTreePath!=null) {
					Object obj = clickedTreePath.getLastPathComponent();
					if (obj instanceof LocationTreeNode)
						clickedTreeNode = (LocationTreeNode) obj;
				}
				miReloadTreeNode.setEnabled(clickedTreeNode!=null);
				miReloadTreeNode.setText(String.format("%s Folder", clickedTreeNode!=null && clickedTreeNode.movies!=null ? "Reload" : "Load"));
			});
		}
	}

	private class TableContextMenu extends ContextMenu
	{
		private static final long serialVersionUID = -1424675027095694095L;
		
		private MovieList.Movie clickedMovie;
		private int clickedMovieRowM;

		private MovieList.Movie[] selectedMovies;
		private int[] selectedMovieRowsM;

		TableContextMenu()
		{
			clickedMovie = null;
			clickedMovieRowM = -1;
			selectedMovies = null;
			selectedMovieRowsM = null;
			
			JMenuItem miOpenVideoPlayer = add(OpenWebifController.createMenuItem("Show in VideoPlayer" , GrayCommandIcons.IconGroup.Image , e->showMovie(clickedMovie)));
			JMenuItem miOpenBrowser     = add(OpenWebifController.createMenuItem("Show in Browser"     , GrayCommandIcons.IconGroup.Image , e->showMovieInBrowser(clickedMovie)));
			JMenuItem miZapToMovie      = add(OpenWebifController.createMenuItem("Start Playing in STB", GrayCommandIcons.IconGroup.Play  , e->zapToMovie(clickedMovie)));
			JMenuItem miDeleteMovie     = add(OpenWebifController.createMenuItem("Delete"              , GrayCommandIcons.IconGroup.Delete, e->{
				deleteMovie(clickedMovie, ()->{
					movieTableModel.deletedMovies.add(clickedMovie);
					movieTableModel.fireTableRowUpdate(clickedMovieRowM);
				});
			}));
			
			JMenu clickedMovieMenu = new JMenu("Clicked Movie");
			add(clickedMovieMenu);
			AlreadySeenEvents.MenuControl aseMenuControlClicked = AlreadySeenEvents.getInstance().createMenuForMovies(clickedMovieMenu, ()->clickedMovie, null, ()->{
				movieTableModel.fireTableColumnUpdate(MovieTableModel.ColumnID.Seen);
			});
			
			addSeparator();
			
			JMenu selectedMovieMenu = new JMenu("Selected Movie");
			add(selectedMovieMenu);
			AlreadySeenEvents.MenuControl aseMenuControlSelected = AlreadySeenEvents.getInstance().createMenuForMovies(selectedMovieMenu, null, ()->selectedMovies, ()->{
				movieTableModel.fireTableColumnUpdate(MovieTableModel.ColumnID.Seen);
			});
			
			addSeparator();
			
			boolean showDescriptionInNameColumn = MovieTableModel.getShowDescriptionInNameColumn();
			add(OpenWebifController.createCheckBoxMenuItem("Show description in name column", showDescriptionInNameColumn, b->{
				if (movieTableModel!=null)
					movieTableModel.setShowDescriptionInNameColumn(b);
			}));
			
			JMenuItem miReloadTable = add(OpenWebifController.createMenuItem("Reload Table", GrayCommandIcons.IconGroup.Reload, e->reloadTreeNode(selectedTreeNode)));
			
			add( OpenWebifController.createMenuItem("Copy table content to clipboard (tab separated)", GrayCommandIcons.IconGroup.Copy, e->{
				ClipboardTools.copyToClipBoard(movieTableModel.getTableContentAsString(OutputType.TabSeparated, true, true));
			}) );
			
			add(OpenWebifController.createMenuItem("Show Column Widths", e->{
				TableColumnModel columnModel = movieTable.getColumnModel();
				if (columnModel==null) return;
				int[] widths = new int[columnModel.getColumnCount()];
				for (int i=0; i<widths.length; i++) {
					TableColumn column = columnModel.getColumn(i);
					if (column==null) widths[i] = -1;
					else widths[i] = column.getWidth();
				}
				System.out.printf("Column Widths: %s%n", Arrays.toString(widths));
			}));
			
			addContextMenuInvokeListener((comp, x, y) -> {
				int rowV = movieTable.rowAtPoint(new Point(x,y));
				int rowM = movieTable.convertRowIndexToModel(rowV);
				clickedMovieRowM = rowM;
				selectedMovieRowsM = getSelectedRowsM();
				
				clickedMovie = null;
				selectedMovies = null;
				if (movieTableModel!=null)
				{
					clickedMovie = movieTableModel.getRow(rowM);
					selectedMovies = Arrays
							.stream(selectedMovieRowsM)
							.mapToObj(movieTableModel::getRow)
							.toArray(MovieList.Movie[]::new);
				}
				
				String rowName = MovieTableModel.getRowName(movieTableModel, clickedMovie);
				if (rowName!=null && rowName.length() > 60)
					rowName = rowName.substring(0,58) + "...";
				
				miReloadTable.setEnabled(selectedTreeNode!=null);
				miOpenVideoPlayer.setEnabled(clickedMovie!=null);
				miOpenBrowser    .setEnabled(clickedMovie!=null);
				miZapToMovie     .setEnabled(clickedMovie!=null);
				miDeleteMovie    .setEnabled(clickedMovie!=null);
				miOpenVideoPlayer.setText(clickedMovie==null ? "Show in VideoPlayer"  : String.format("Show \"%s\" in VideoPlayer" , rowName));
				miOpenBrowser    .setText(clickedMovie==null ? "Show in Browser"      : String.format("Show \"%s\" in Browser"     , rowName));
				miZapToMovie     .setText(clickedMovie==null ? "Start Playing in STB" : String.format("Start Playing \"%s\" in STB", rowName));
				miDeleteMovie    .setText(clickedMovie==null ? "Delete"               : String.format("Delete \"%s\""              , rowName));
				
				clickedMovieMenu .setText(clickedMovie==null ? "Clicked Movie"        : String.format("Clicked Movie \"%s\""       , rowName));
				
				int n = selectedMovies==null ? 0 : selectedMovies.length;
				selectedMovieMenu.setText(n == 1 ? "Selected Movie (1)" : "Selected Movies (%d)".formatted(n));
				
				aseMenuControlClicked .updateBeforeShowingMenu();
				aseMenuControlSelected.updateBeforeShowingMenu();
			});
		}
	}

	private void updateMovieTableModel(Vector<MovieList.Movie> movies) {
		movieTable.setModel(movieTableModel = new MovieTableModel(movies));
		movieTable.setRowSorter( new Tables.SimplifiedRowSorter(movieTableModel) );
		movieTableModel.setTable(movieTable);
		movieTableModel.setColumnWidths(movieTable);
		movieTableModel.setCellRenderers();
	}

	private void reloadTreeNode(LocationTreeNode treeNode) {
		String path = treeNode.getDirPath();
		MovieList movieList = getMovieList(path);
		locationsRoot.addLocations(movieList,locationsTreeModel);
		locationsTreeModel.nodeChanged(treeNode);
		if (treeNode==selectedTreeNode)
			updateMovieTableModel(movieList.movies);
	}
	
	private void zapToMovie(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		main.runWithProgressDialog("Zap to Movie", pd->{
			OpenWebifTools.MessageResponse response = OpenWebifTools.zapToMovie(baseURL, movie, taskTitle->{
				OpenWebifController.setIndeterminateProgressTask(pd, taskTitle);
			});
			main.showMessageResponse(response, "Zap to Movie");
		});
	}
	
	private void deleteMovie(MovieList.Movie movie, Runnable wasDeleted) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		main.runWithProgressDialog("Delete Movie", pd->{
			OpenWebifTools.MessageResponse response = OpenWebifTools.deleteMovie(baseURL, movie, taskTitle->{
				OpenWebifController.setIndeterminateProgressTask(pd, taskTitle);
			});
			main.showMessageResponse(response, "Delete Movie");
			if (response.result && wasDeleted!=null)
				wasDeleted.run();
		});
	}

	private void showMovie(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		File videoPlayer = main.getVideoPlayer();
		if (videoPlayer==null) return;
		
		File javaVM = main.getJavaVM();
		if (javaVM==null) return;
		
		String url = OpenWebifTools.getMovieURL(baseURL, movie);
		
		System.out.printf("show movie:%n");
		System.out.printf("   java VM      : \"%s\"%n", javaVM.getAbsolutePath());
		System.out.printf("   video player : \"%s\"%n", videoPlayer.getAbsolutePath());
		System.out.printf("   url          : \"%s\"%n", url);
		
		try { Runtime.getRuntime().exec(new String[] {javaVM.getAbsolutePath(), "-jar", "OpenWebifController.jar", "-start", videoPlayer.getAbsolutePath(), url }); }
		catch (IOException ex) { System.err.printf("IOException while starting video player: %s%n", ex.getMessage()); }
		
		//try {
		//	Process process = Runtime.getRuntime().exec(new String[] {"c:\\Program Files (x86)\\Java\\jre\\bin\\java.exe", "StartSomething", videoPlayer.getAbsolutePath(), url });
		//	//Process process = Runtime.getRuntime().exec(new String[] { videoPlayer.getAbsolutePath(), url });
		//	System.out.println(OpenWebifController.toString(process));
		//}
		//catch (IOException e) { System.err.printf("IOException while starting movie player: %s%n", e.getMessage()); }
	}

	private void showMovieInBrowser(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		File browser = main.getBrowser();
		if (browser==null) return;
		
		String url = OpenWebifTools.getMovieURL(baseURL, movie);
		
		System.out.printf("show movie:%n");
		System.out.printf("   browser : \"%s\"%n", browser.getAbsolutePath());
		System.out.printf("   url     : \"%s\"%n", url);
		
		try {
			Process process = Runtime.getRuntime().exec(new String[] { browser.getAbsolutePath(), url });
			System.out.println(OpenWebifController.toString(process));
		}
		catch (IOException e) { System.err.printf("IOException while starting movie player: %s%n", e.getMessage()); }
	}

	private void showValues(MovieList.Movie movie) {
		if (movie==null) {
			movieInfo1.setText("");
			movieInfo2.setText("");
			return;
		}
		
		ScrollPosition movieInfo1ScrollPos = ScrollPosition.getVertical(movieInfo1ScrollPane);
		ScrollPosition movieInfo2ScrollPos = ScrollPosition.getVertical(movieInfo2ScrollPane);
		
		ValueListOutput out = new ValueListOutput();
		out.add(0, "eventname          ", movie.eventname          );
		out.add(0, "servicename        ", movie.servicename        );
		out.add(0, "length             ", movie.lengthStr          );
		out.add(0, null                 , movie.length_s           );
		out.add(0, "begintime          ", movie.begintime          );
		out.add(0, "recordingtime      ", movie.recordingtime      );
		out.add(0, null                 , "%s", dtFormatter.getTimeStr(movie.recordingtime*1000, true, true, false, true, false) );
		out.add(0, "lastseen           ", movie.lastseen           );
		out.add(0, "filesize           ", movie.filesize           );
		out.add(0, "filesize_readable  ", movie.filesize_readable  );
		out.add(0, "tags               ", movie.tags               );
		out.add(0, "filename           ", movie.filename           );
		out.add(0, "filename_stripped  ", movie.filename_stripped  );
		out.add(0, "fullname           ", movie.fullname           );
		out.add(0, "serviceref         ", movie.serviceref         );
		movieInfo1.setText(out.generateOutput());
		if (movieInfo1ScrollPos!=null)
			SwingUtilities.invokeLater(()->movieInfo1ScrollPos.setVertical(movieInfo1ScrollPane));
		
		StringBuilder sb = new StringBuilder();
		sb.append("description:\r\n").append(movie.description.replace(""+((char)0x8a), "\r\n")).append("\r\n");
		sb.append("\r\nextended description:\r\n").append(movie.descriptionExtended.replace(""+((char)0x8a), "\r\n")).append("\r\n");
		movieInfo2.setText(sb.toString());
		if (movieInfo2ScrollPos!=null)
			SwingUtilities.invokeLater(()->movieInfo2ScrollPos.setVertical(movieInfo2ScrollPane));
	}

	private MovieList getMovieList(String dir) {
		return ProgressDialog.runWithProgressDialogRV(main.mainWindow, "Load MovieList", 400, pd->{
			String baseURL = main.getBaseURL();
			return getMovieList(baseURL, dir, pd);
		});
	}

	private MovieList getMovieList(String baseURL, String dir, ProgressView pd) {
		if (baseURL==null) return null;
		
		MovieList movieList = OpenWebifTools.readMovieList(baseURL, dir, taskTitle -> OpenWebifController.setIndeterminateProgressTask(pd, "Movies: "+taskTitle));
		//movieList.printTo(System.out);
		return movieList;
	}

	void readInitialMovieList(String baseURL, ProgressView pd) {
		MovieList movieList = getMovieList(baseURL, null, pd);
		
		if (movieList!=null) {
			locationsRoot = LocationTreeNode.create(movieList);
			locationsTreeModel = new DefaultTreeModel(locationsRoot, true);
			locationsTree.setModel(locationsTreeModel);
			if (locationsRoot!=null) {
				selectedTreePath = locationsRoot.getTreePath(movieList.directory);
				selectedTreeNode = null;
				locationsTree.expandPath(selectedTreePath);
				if (selectedTreePath!=null) {
					Object obj = selectedTreePath.getLastPathComponent();
					if (obj instanceof LocationTreeNode)
						selectedTreeNode = (LocationTreeNode) obj;
				}
				//locationsTree.setSelectionPath(treePath);
				updateMovieTableModel(movieList.movies);
			}
		}
	}
	
	private static class LocationTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 5786063818778166574L;

		@Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (value instanceof LocationTreeNode) {
				LocationTreeNode treeNode = (LocationTreeNode) value;
				if (treeNode.movies==null) setIcon(TreeIcons.Folder     .getIcon());
				else                       setIcon(TreeIcons.GreenFolder.getIcon());
			}
			
			return comp;
		}
		
	}

	private static class LocationTreeNode implements TreeNode {
	
		private final LocationTreeNode parent;
		private final String name;
		private final Vector<LocationTreeNode> children;
		private Vector<MovieList.Movie> movies;
	
		LocationTreeNode(LocationTreeNode parent, String name) {
			this.parent = parent;
			this.name = name;
			this.children = new Vector<>();
			this.movies = null;
		}

		public static LocationTreeNode create(MovieList movieList) {
			if (movieList==null) return null;
			
			// path: /media/hdd/movie-storage/_unsortiert/
			String[] names = splitToNames(movieList.directory);
			if (names==null) return null;
			
			LocationTreeNode root = new LocationTreeNode(null,names[0]);
			LocationTreeNode p = root;
			for (int i=1; i<names.length; i++) {
				LocationTreeNode newNode = new LocationTreeNode(p,names[i]);
				p.addChild(newNode);
				p = newNode;
			}
			
			p.movies = movieList.movies;
			if (movieList.bookmarks!=null)
				for (String childName:movieList.bookmarks) {
					LocationTreeNode newNode = new LocationTreeNode(p,childName);
					p.addChild(newNode);
				}
			
			return root;
		}
	
		public void addLocations(MovieList movieList, DefaultTreeModel treeModel) {
			if (movieList==null) return;
			
			String[] names = splitToNames(movieList.directory);
			if (names==null) return;
			
			if (!name.equals(names[0])) return;
			LocationTreeNode node = this;
			
			for (int i=1; i<names.length; i++) {
				String name = names[i];
				LocationTreeNode childNode = node.getChild(name);
				if (childNode==null) {
					childNode = new LocationTreeNode(node, name);
					int index = node.addChild(childNode);
					treeModel.nodesWereInserted(node, new int[]{ index });
				}
				node = childNode;
			}
			
			node.movies = movieList.movies;
			
			if (movieList.bookmarks!=null) {
				int[] newIndexes = new int[movieList.bookmarks.size()];
				int i=0;
				for (String childName:movieList.bookmarks) {
					LocationTreeNode childNode = node.getChild(childName);
					if (childNode==null) {
						childNode = new LocationTreeNode(node, childName);
						newIndexes[i] = node.addChild(childNode);
						i++;
					}
				}
				if (i>0)
					treeModel.nodesWereInserted(node, Arrays.copyOfRange(newIndexes,0,i));
			}
		}
		public String getDirPath() {
			if (parent == null) return "/"+name;
			return parent.getDirPath()+"/"+name;
		}
	
		private static String[] splitToNames(String path) {
			if (path==null) return null;
			
			while (path.startsWith("/")) path = path.substring(1);
			while (path.  endsWith("/")) path = path.substring(0,path.length()-1);
			
			return path.split("/");
		}
		
		@Override public String toString() { return name; }
		
		public TreePath getTreePath(String path) {
			String[] names = splitToNames(path);
			if (names==null) return null;
			
			LocationTreeNode node = this;
			if (!name.equals(names[0])) return null;
			
			TreePath treePath = new TreePath(this);
			for (int i=1; i<names.length; i++) {
				String name = names[i];
				node = node.getChild(name);
				if (node==null) return null;
				treePath = treePath.pathByAddingChild(node);
			}
			
			return treePath;
		}
	
		private int addChild(LocationTreeNode child) {
			int index = children.size();
			children.add(child);
			return index;
		}
	
		private LocationTreeNode getChild(String name) {
			for (LocationTreeNode child:children)
				if (child.name.equals(name))
					return child;
			return null;
		}
	
		@Override public TreeNode getParent() { return parent; }
		@Override public int getChildCount() { return children.size(); }
		@Override public TreeNode getChildAt(int childIndex) { return children.get(childIndex); }
		@Override public int getIndex(TreeNode node) { return children.indexOf(node); }
		@Override public boolean getAllowsChildren() { return true; }
		@Override public boolean isLeaf() { return children.isEmpty(); }
		@Override public Enumeration<LocationTreeNode> children() { return children.elements(); }
	}

	static class MovieTableModel extends Tables.SimpleGetValueTableModel<MovieList.Movie, MovieTableModel.ColumnID> {
		
		private static final Color COLOR_DELETED = new Color(0xC0C0C0);
		
		// Column Widths: [280, 50, 109, 450, 59, 108]
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface, Tables.AbstractGetValueTableModel.ColumnIDTypeInt<MovieList.Movie>, SwingConstants {
			Name    (config("Name"    ,  String.class, 280, null).setValFunc(MovieTableModel::getRowName)),
			Progress(config("Progress",    Long.class,  60, null).setValFunc(m->m.lastseen         )),
			Length  (config("Length"  , Integer.class,  50, null).setValFunc(m->m.length_s         ).setToStringR(m->m.lengthStr        )),
			Size    (config("Size"    ,    Long.class,  60, null).setValFunc(m->m.filesize         ).setToStringR(m->m.filesize_readable)),
			Time    (config("Time"    ,    Long.class, 110, null).setValFunc(m->m.recordingtime    ).setToStringR(m->dtFormatter.getTimeStr(m.recordingtime*1000, false, true, false, true, false))),
			Seen    (config("Seen"    , Boolean.class,  50, null).setValFunc(AlreadySeenEvents.getInstance()::isMarkedAsAlreadySeen)),
			Station (config("Station" ,  String.class, 110, null).setValFunc(m->m.servicename      )),
			File    (config("File"    ,  String.class, 450, null).setValFunc(m->m.filename_stripped)),
			;
			
			private final Tables.SimplifiedColumnConfig2<MovieTableModel, MovieList.Movie, ?> cfg;
			ColumnID(Tables.SimplifiedColumnConfig2<MovieTableModel, MovieList.Movie, ?> cfg) { this.cfg = cfg; }
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return this.cfg; }
			@Override public Function<MovieList.Movie, ?> getGetValue() { return cfg.getValue; }
			
			private static <T> Tables.SimplifiedColumnConfig2<MovieTableModel, MovieList.Movie, T> config(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment)
			{
				return new Tables.SimplifiedColumnConfig2<>(name, columnClass, 20, -1, prefWidth, prefWidth, horizontalAlignment);
			}
		}
	
		private boolean showDescriptionInNameColumn;
		private final Set<MovieList.Movie> deletedMovies;

		private MovieTableModel(Vector<MovieList.Movie> movies)
		{
			super(ColumnID.values(), movies);
			showDescriptionInNameColumn = getShowDescriptionInNameColumn();
			deletedMovies = new HashSet<>();
		}
	
		private static String getRowName(MovieTableModel model, MovieList.Movie movie)
		{
			if (movie==null) return null;
			if (model==null) return movie.eventname;
			if (!model.showDescriptionInNameColumn) return movie.eventname;
			if (movie.description == null  ) return movie.eventname;
			if (movie.description.isBlank()) return movie.eventname;
			return String.format("%s | %s", movie.eventname, movie.description);
		}

		public static boolean getShowDescriptionInNameColumn()
		{
			return OpenWebifController.settings.getBool(
					OpenWebifController.AppSettings.ValueKey.MoviesPanel_ShowDescriptionInNameColumn,
					false
			);
		}

		public void setShowDescriptionInNameColumn(boolean showDescriptionInNameColumn)
		{
			this.showDescriptionInNameColumn = showDescriptionInNameColumn;
			OpenWebifController.settings.putBool(
					OpenWebifController.AppSettings.ValueKey.MoviesPanel_ShowDescriptionInNameColumn,
					showDescriptionInNameColumn
			);
			fireTableColumnUpdate(MovieTableModel.ColumnID.Name);
		}
		
		

		@Override
		protected Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID, MovieList.Movie row)
		{
			if (columnID.cfg.getValueM!=null)
				return columnID.cfg.getValueM.apply(this, row);
			return super.getValueAt(rowIndex, columnIndex, columnID, row);
		}

		@Override
		protected void fireTableRowUpdate(int rowIndex) {
			super.fireTableRowUpdate(rowIndex);
		}

		private void setCellRenderers()
		{
			CustomCellRenderer customCellRenderer = new CustomCellRenderer();
			ScaleCellRenderer scaleCellRenderer = new ScaleCellRenderer();
			forEachColum((ColumnID columnID, TableColumn column) -> {
				if (column!=null && columnID!=null)
				{
					if (columnID==ColumnID.Progress)
						column.setCellRenderer(scaleCellRenderer);
					else //if (columnID.getDisplayStr!=null)
						column.setCellRenderer(customCellRenderer);
				}
			});
		}
		
		private class ScaleCellRenderer implements TableCellRenderer {
			
			private final ScaleRC rendComp;

			ScaleCellRenderer() {
				rendComp = new ScaleRC();
				rendComp.setDefaultPreferredSize();
			}

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int rowM = table.convertRowIndexToModel(rowV);
				MovieList.Movie movie = getRow(rowM);
				rendComp.wasDeleted = deletedMovies.contains(movie);
				
				rendComp.configureAsTableCellRendererComponent(table, value, isSelected, hasFocus);
				return rendComp;
			}
			
			private static class ScaleRC extends Tables.GraphicRendererComponent<Long>
			{
				private static final Color COLOR_COMPLETED = new Color(0x00CD00);
				private static final Color COLOR_PLAYING   = new Color(0xFFCD00);

				private static final long serialVersionUID = 5308892166500614939L;
				
				private Long value = null;
				private boolean isSelected = false;
				private JTable table = null;
				private JList<?> list = null;
				private boolean wasDeleted = false;
				
				ScaleRC() { super(Long.class); }
				
				@Override protected void setValue(Long value, JTable table, JList<?> list, Integer listIndex, boolean isSelected, boolean hasFocus)
				{
					this.value = value;
					this.table = table;
					this.list = list;
					this.isSelected = isSelected;
				}

				@Override protected void paintContent(Graphics g, int x, int y, int width, int height)
				{
					if (value==null)
						return;
					
					Color color = null;
					if (value < 30)
						color = isSelected ? getTableOrListSelectionForeground() : wasDeleted ? COLOR_DELETED : getTableOrListForeground();
					else if (value < 90)
						color = !wasDeleted ? COLOR_PLAYING   : isSelected ? getTableOrListSelectionForeground() : COLOR_DELETED;
					else
						color = !wasDeleted ? COLOR_COMPLETED : isSelected ? getTableOrListSelectionForeground() : COLOR_DELETED;
					
					int scaleWidth = (width-2)*Math.min(value.intValue(), 100) / 100;
					g.setColor(color);
					g.drawRect(x  , y  , width-1   , height-1);
					g.fillRect(x+1, y+1, scaleWidth, height-2);
				}

				private Color getTableOrListSelectionForeground() {
					if (table!=null) return table.getSelectionForeground();
					if (list !=null) return list .getSelectionForeground();
					return Color.BLACK;
				}

				private Color getTableOrListForeground() {
					if (table!=null) return table.getForeground();
					if (list !=null) return list .getForeground();
					return Color.BLACK;
				}
			}
		}
		
		private class CustomCellRenderer implements TableCellRenderer {
			
			private Tables.LabelRendererComponent label;
			private Tables.CheckBoxRendererComponent checkbox;

			CustomCellRenderer()
			{
				label = new Tables.LabelRendererComponent();
				checkbox = new Tables.CheckBoxRendererComponent();
				checkbox.setHorizontalAlignment(SwingConstants.CENTER);
			}

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				int rowM = table.convertRowIndexToModel(rowV);
				MovieList.Movie movie = getRow(rowM);
				
				Supplier<Color> getCustomBackground = null;
				Supplier<Color> getCustomForeground = null;
				
				if (!isSelected && deletedMovies.contains(movie))
					getCustomForeground = ()->COLOR_DELETED;
				
				Component rendComp = label;
				String valueStr = value == null ? "" : value.toString();
				
				if (columnID!=null)
				{
					if (columnID.getColumnConfig().columnClass == Boolean.class && value instanceof Boolean boolVal) {
						rendComp = checkbox;
						checkbox.configureAsTableCellRendererComponent(table, boolVal, null, isSelected, hasFocus, getCustomForeground, getCustomBackground);
					}
					else
					{
						if (columnID.cfg.toStringR!=null && movie!=null)
							valueStr = columnID.cfg.toStringR.apply(movie);
						if (columnID.cfg.toString!=null)
							valueStr = columnID.cfg.toString.apply(value);
						label.setHorizontalAlignment(columnID.cfg.horizontalAlignment);
					}
				}
				label.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, getCustomForeground);
				
				return rendComp;
			}
		}
		
	}
	
}