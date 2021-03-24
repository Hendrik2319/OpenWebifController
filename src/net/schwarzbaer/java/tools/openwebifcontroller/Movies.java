package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.system.DateTimeFormatter;

class Movies extends JSplitPane {
	private static final long serialVersionUID = 3435419463730240276L;
	
	private static final DateTimeFormatter dtFormatter = new DateTimeFormatter();
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	enum TreeIcons {
		Folder, KnownFolder;
		Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}

	private final OpenWebifController main;
	
	private final JTree locationsTree;
	private final JTable movieTable;
	private final JTextArea movieInfo1;
	private final JTextArea movieInfo2;
	
	private LocationTreeNode locationsRoot;
	private DefaultTreeModel locationsTreeModel;
	private MovieTableModel movieTableModel;

	private TreePath selectedTreePath;
	private TreePath clickedTreePath;
	private LocationTreeNode selectedTreeNode;
	private LocationTreeNode clickedTreeNode;
	private MovieList.Movie clickedMovie;

	Movies(OpenWebifController main) {
		
		this.main = main;
		locationsRoot = null;
		locationsTreeModel = null;
		selectedTreePath = null;
		selectedTreeNode = null;
		movieTableModel = null;
		clickedMovie = null;
		
		locationsTree = new JTree(locationsTreeModel);
		locationsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		locationsTree.setCellRenderer(new LocationTreeCellRenderer());
		
		JScrollPane treeScrollPane = new JScrollPane(locationsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		movieTable = new JTable(movieTableModel);
		movieTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane tableScrollPane = new JScrollPane(movieTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		
		JMenuItem miReloadTable1, miOpenVideoPlayer, miOpenBrowser;
		ContextMenu tableContextMenu = new ContextMenu();
		tableContextMenu.add(miOpenVideoPlayer = OpenWebifController.createMenuItem("Show in VideoPlayer", e->showMovie(clickedMovie)));
		tableContextMenu.add(miOpenBrowser     = OpenWebifController.createMenuItem("Show in Browser"    , e->showMovieInBrowser(clickedMovie)));
		tableContextMenu.addSeparator();
		tableContextMenu.add(miReloadTable1 = OpenWebifController.createMenuItem("Reload Table", e->reloadTreeNode(selectedTreeNode)));
		tableContextMenu.add(OpenWebifController.createMenuItem("Show Column Widths", e->{
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
		
		tableContextMenu.addTo(movieTable);
		tableContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
			clickedMovie = null;
			int rowV = movieTable.rowAtPoint(new Point(x,y));
			int rowM = movieTable.convertRowIndexToModel(rowV);
			if (movieTableModel!=null)
				clickedMovie = movieTableModel.getValue(rowM);
			
			miReloadTable1.setEnabled(selectedTreeNode!=null);
			miOpenVideoPlayer.setEnabled(clickedMovie!=null);
			miOpenVideoPlayer.setText(clickedMovie==null ? "Show in VideoPlayer" : String.format("Show \"%s\" in VideoPlayer", clickedMovie.eventname));
			miOpenBrowser.setEnabled(clickedMovie!=null);
			miOpenBrowser.setText(clickedMovie==null ? "Show in Browser" : String.format("Show \"%s\" in Browser", clickedMovie.eventname));
		});
		
		JMenuItem miReloadTable2;
		ContextMenu treeContextMenu = new ContextMenu();
		treeContextMenu.add(miReloadTable2 = OpenWebifController.createMenuItem("Reload Folder", e->reloadTreeNode(clickedTreeNode)));
		
		treeContextMenu.addTo(locationsTree);
		treeContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
			clickedTreePath = locationsTree.getPathForLocation(x,y);
			clickedTreeNode = null;
			if (clickedTreePath!=null) {
				Object obj = clickedTreePath.getLastPathComponent();
				if (obj instanceof LocationTreeNode)
					clickedTreeNode = (LocationTreeNode) obj;
			}
			miReloadTable2.setEnabled(clickedTreeNode!=null);
			miReloadTable2.setText(String.format("%s Folder", clickedTreeNode!=null && clickedTreeNode.movies!=null ? "Reload" : "Load"));
		});
		
		
		movieInfo1 = new JTextArea();
		movieInfo1.setEditable(false);
		//movieInfo1.setLineWrap(true);
		//movieInfo1.setWrapStyleWord(true);
		JScrollPane movieInfo1ScrollPane = new JScrollPane(movieInfo1);
		movieInfo1ScrollPane.setPreferredSize(new Dimension(400,330));
		
		movieInfo2 = new JTextArea();
		movieInfo2.setEditable(false);
		movieInfo2.setLineWrap(true);
		movieInfo2.setWrapStyleWord(true);
		JScrollPane movieInfo2ScrollPane = new JScrollPane(movieInfo2);
		movieInfo2ScrollPane.setPreferredSize(new Dimension(400,300));
		
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
			int rowV = movieTable.getSelectedRow();
			int rowM = movieTable.convertRowIndexToModel(rowV);
			showValues(movieTableModel.getValue(rowM));
		});
		
		movieTable.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getButton()==MouseEvent.BUTTON1) {
					if (e.getClickCount()<2) return;
					int rowV = movieTable.rowAtPoint(e.getPoint());
					int rowM = movieTable.convertRowIndexToModel(rowV);
					showMovie(movieTableModel.getValue(rowM));
				}
				if (e.getButton()==MouseEvent.BUTTON3) {
					
				}
			}
		});
		
	}

	private void updateMovieTableModel(Vector<MovieList.Movie> movies) {
		movieTable.setModel(movieTableModel = new MovieTableModel(movies));
		movieTableModel.initializeWith(movieTable);
	}

	private void reloadTreeNode(LocationTreeNode treeNode) {
		String path = treeNode.getDirPath();
		MovieList movieList = getMovieList(path);
		locationsRoot.addLocations(movieList,locationsTreeModel);
		locationsTreeModel.nodeChanged(treeNode);
		if (treeNode==selectedTreeNode)
			updateMovieTableModel(movieList.movies);
	}

	private void showMovie(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		File videoPlayer = main.getVideoPlayer();
		if (videoPlayer==null) return;
		
		String movieURL = null;
		try { movieURL = URLEncoder.encode(movie.filename, "UTF-8");
		} catch (UnsupportedEncodingException e) { System.err.printf("Exception while creating movie URL: [UnsupportedEncodingException] %s%n", e.getMessage()); }
		
		String url = baseURL+"/file?file="+movieURL;
		
		System.out.printf("show movie:%n");
		System.out.printf("   videoPlayer : \"%s\"%n", videoPlayer.getAbsolutePath());
		System.out.printf("   url         : \"%s\"%n", url);
		
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"c:\\Program Files (x86)\\Java\\jre\\bin\\java.exe", "StartSomething", videoPlayer.getAbsolutePath(), url });
			//Process process = Runtime.getRuntime().exec(new String[] { videoPlayer.getAbsolutePath(), url });
			System.out.println(OpenWebifController.toString(process));
		}
		catch (IOException e) { System.err.printf("IOException while starting movie player: %s%n", e.getMessage()); }
	}

	private void showMovieInBrowser(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = main.getBaseURL();
		if (baseURL==null) return;
		
		File browser = main.getBrowser();
		if (browser==null) return;
		
		String movieURL = null;
		try { movieURL = URLEncoder.encode(movie.filename, "UTF-8");
		} catch (UnsupportedEncodingException e) { System.err.printf("Exception while creating movie URL: [UnsupportedEncodingException] %s%n", e.getMessage()); }
		
		String url = baseURL+"/file?file="+movieURL;
		
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
		
		StringBuilder sb = new StringBuilder();
		sb.append("description:\r\n").append(movie.description.replace(""+((char)0x8a), "\r\n")).append("\r\n");
		sb.append("\r\nextended description:\r\n").append(movie.descriptionExtended.replace(""+((char)0x8a), "\r\n")).append("\r\n");
		movieInfo2.setText(sb.toString());
	}

	private MovieList getMovieList(String dir) {
		String baseURL = main.getBaseURL();
		
		MovieList movieList = null;
		if (baseURL!=null) movieList = MovieList.get(main.mainWindow,baseURL,dir);
		
		return movieList;
	}

	void readInitialList() {
		MovieList movieList = getMovieList(null);
		
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
				else                       setIcon(TreeIcons.KnownFolder.getIcon());
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
		@SuppressWarnings("rawtypes")
		@Override public Enumeration children() { return children.elements(); }
	}

	private static class MovieList {
	
		static MovieList get(Window parent, String baseURL, String dir) {
			return ProgressDialog.runWithProgressDialogRV(parent, "Load MovieList", 400, pd->{
				
				SwingUtilities.invokeLater(()->{
					pd.setTaskTitle("Build URL");
					pd.setIndeterminate(true);
				});
				String urlStr = String.format("%s/api/movielist", baseURL);
				String dir_ = dir;
				if (dir_!=null) {
					try { dir_ = URLEncoder.encode(dir_, "UTF-8");
					} catch (UnsupportedEncodingException e) { System.err.printf("Exception while converting directory name: [UnsupportedEncodingException] %s%n", e.getMessage()); }
					urlStr = String.format("%s?dirname=%s", urlStr, dir_);
				}
				System.out.printf("get MovieList: \"%s\"%n", urlStr);
				
				SwingUtilities.invokeLater(()->{
					pd.setTaskTitle("Read Content from URL");
					pd.setIndeterminate(true);
				});
				String content = OpenWebifController.getContent(urlStr);
				
				SwingUtilities.invokeLater(()->{
					pd.setTaskTitle("Parse Content");
					pd.setIndeterminate(true);
				});
				Value<MovieList.NV, MovieList.V> result = new JSON_Parser<MovieList.NV,MovieList.V>(content,null).parse();
				
				SwingUtilities.invokeLater(()->{
					pd.setTaskTitle("Create MovieList");
					pd.setIndeterminate(true);
				});
				try {
					return new MovieList(result);
				} catch (TraverseException e) {
					System.err.printf("Exception while parsing JSON structure: %s%n", e.getMessage());
					return null;
				}
			});
		}
	
		static class NV extends JSON_Data.NamedValueExtra.Dummy{}
		static class V extends JSON_Data.ValueExtra.Dummy{}
		
		/*
		    Block "MovieList" [0]
		        <Base>:Object
		    Block "MovieList.<Base>" [3]
		        bookmarks:Array
		        bookmarks[]:String
		        directory:String
		        movies:Array
		        movies[]:Object
		 */
	
		final String directory;
		final Vector<String> bookmarks;
		final Vector<MovieList.Movie> movies;
		
		public MovieList(Value<MovieList.NV, MovieList.V> result) throws TraverseException {
			//JSON_Helper.OptionalValues<NV, V> optionalValueScan = new JSON_Helper.OptionalValues<NV,V>();
			//optionalValueScan.scan(result, "MovieList");
			//optionalValueScan.show(System.out);
			
			String debugOutputPrefixStr = "MovieList";
			JSON_Object<MovieList.NV, MovieList.V> object = JSON_Data.getObjectValue(result, debugOutputPrefixStr);
			
			directory = OpenWebifController.decodeUnicode( JSON_Data.getStringValue(object, "directory", debugOutputPrefixStr) );
			JSON_Array<MovieList.NV, MovieList.V> bookmarks = JSON_Data.getArrayValue(object, "bookmarks", debugOutputPrefixStr);
			JSON_Array<MovieList.NV, MovieList.V> movies    = JSON_Data.getArrayValue(object, "movies", debugOutputPrefixStr);
			
			this.bookmarks = new Vector<>();
			for (int i=0; i<bookmarks.size(); i++) {
				String str = JSON_Data.getStringValue(bookmarks.get(i), debugOutputPrefixStr+".bookmarks["+i+"]");
				this.bookmarks.add( OpenWebifController.decodeUnicode( str ) );
			}
			
			this.movies = new Vector<>();
			for (int i=0; i<movies.size(); i++)
				this.movies.add(new Movie(movies.get(i), debugOutputPrefixStr+".movies["+i+"]"));
		}
		
		private static class Movie {
			final String begintime;
			final String description;
			final String descriptionExtended;
			final String eventname;
			final String filename;
			final String filename_stripped;
			final long filesize;
			final String filesize_readable;
			final String fullname;
			final long lastseen;
			final String lengthStr;
			final long recordingtime;
			final String servicename;
			final String serviceref;
			final String tags;
			final Integer length_s;
	
			/*		
			    Block "MovieList.<Base>.movies[]" [15]
			        begintime:String
			        description:String
			        descriptionExtended:String
			        eventname:String
			        filename:String
			        filename_stripped:String
			        filesize:Integer
			        filesize_readable:String
			        fullname:String
			        lastseen:Integer
			        length:String
			        recordingtime:Integer
			        servicename:String
			        serviceref:String
			        tags:String
			 */
	
			public Movie(Value<MovieList.NV, MovieList.V> value, String debugOutputPrefixStr) throws TraverseException {
				JSON_Object<MovieList.NV, MovieList.V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
				
				begintime           = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "begintime"          , debugOutputPrefixStr) ); 
				description         = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "description"        , debugOutputPrefixStr) );
				descriptionExtended = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "descriptionExtended", debugOutputPrefixStr) );
				eventname           = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "eventname"          , debugOutputPrefixStr) );
				filename            = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "filename"           , debugOutputPrefixStr) );
				filename_stripped   = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "filename_stripped"  , debugOutputPrefixStr) );
				filesize            =                                    JSON_Data.getIntegerValue(object, "filesize"           , debugOutputPrefixStr)  ;
				filesize_readable   = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "filesize_readable"  , debugOutputPrefixStr) );
				fullname            = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "fullname"           , debugOutputPrefixStr) );
				lastseen            =                                    JSON_Data.getIntegerValue(object, "lastseen"           , debugOutputPrefixStr)  ;
				lengthStr           = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "length"             , debugOutputPrefixStr) );
				recordingtime       =                                    JSON_Data.getIntegerValue(object, "recordingtime"      , debugOutputPrefixStr)  ;
				servicename         = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "servicename"        , debugOutputPrefixStr) );
				serviceref          = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "serviceref"         , debugOutputPrefixStr) );
				tags                = OpenWebifController.decodeUnicode( JSON_Data.getStringValue (object, "tags"               , debugOutputPrefixStr) );
				
				length_s = parseLength(lengthStr);
			}

			private static Integer parseLength(String lengthStr) {
				if (lengthStr==null) return null;
				lengthStr = lengthStr.trim();
				
				int pos = lengthStr.indexOf(':');
				if (pos<0) return parseInt(lengthStr);
				
				Integer min = parseInt(lengthStr.substring(0, pos));
				Integer sec = parseInt(lengthStr.substring(pos+1));
				if (min==null || sec==null) return null;
				
				int sign = min<0 ? -1 : 1;
				return sign * (Math.abs(min)*60 + Math.abs(sec));
			}

			private static Integer parseInt(String str) {
				str = str.trim();
				try {
					int n = Integer.parseInt(str);
					String newStr = String.format("%0"+str.length()+"d", n);
					if (newStr.equals(str)) return n;
				} catch (NumberFormatException e) {}
				return null;
			}
			
		}
	}

	static class MovieTableModel extends Tables.SimplifiedTableModel<MovieTableModel.ColumnID> {
	
		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Name   ("Name"   ,  String.class, 280, m->m.eventname          ),
			Length ("Length" , Integer.class,  60, m->m.length_s     , m->m.lengthStr),
			Station("Station",  String.class,  80, m->m.servicename        ),
			File   ("File"   ,  String.class, 450, m->m.filename_stripped  ),
			Size   ("Size"   ,    Long.class,  70, m->m.filesize     , m->m.filesize_readable),
			Time   ("Time"   ,    Long.class, 100, m->m.recordingtime, m->dtFormatter.getTimeStr(m.recordingtime*1000, false, true, false, true, false)),
			;
		
			final SimplifiedColumnConfig cfg;
			final Function<MovieList.Movie, Object> getValue;
			final Function<MovieList.Movie, String> getDisplayStr;
			
			ColumnID(String name, Class<?> columnClass, int prefWidth, Function<MovieList.Movie,Object> getValue) {
				this(name, columnClass, prefWidth, getValue, null);
			}
			ColumnID(String name, Class<?> columnClass, int prefWidth, Function<MovieList.Movie,Object> getValue, Function<MovieList.Movie,String> getDisplayStr) {
				this.getValue = getValue;
				this.getDisplayStr = getDisplayStr;
				cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
			}
			
			@Override public SimplifiedColumnConfig getColumnConfig() {
				return cfg;
			}
			
		}
	
		private final Vector<MovieList.Movie> movies;
		private final CustomCellRenderer customCellRenderer;
	
		private MovieTableModel(Vector<MovieList.Movie> movies) {
			super(ColumnID.values());
			this.movies = movies;
			customCellRenderer = new CustomCellRenderer();
		}
	
		public void initializeWith(JTable table) {
			setColumnWidths(table);
			
			TableColumnModel columnModel = table.getColumnModel();
			for (int i=0; i<columnModel.getColumnCount(); ++i) {
				TableColumn column = columnModel.getColumn(i);
				ColumnID columnID = getColumnID(table.convertColumnIndexToModel(i));
				if (columnID!=null && columnID.getDisplayStr!=null)
					column.setCellRenderer(customCellRenderer);
			}
			
			table.setRowSorter( new Tables.SimplifiedRowSorter(this) );
		}

		@Override public int getRowCount() { return movies.size(); }
	
		@Override public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			MovieList.Movie value = getValue(rowIndex);
			if (value==null) return null;
			return columnID.getValue.apply(value);
		}
	
		private MovieList.Movie getValue(int rowIndex) {
			if (rowIndex<0 || rowIndex>=movies.size()) return null;
			return movies.get(rowIndex);
		}
		
		private class CustomCellRenderer extends DefaultTableCellRenderer {
			private static final long serialVersionUID = -6595078558558524809L;

			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV) {
				Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowV, columnV);
				
				int columnM = table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				
				if (columnID.getDisplayStr!=null) {
					int rowM = table.convertRowIndexToModel(rowV);
					MovieList.Movie movie = getValue(rowM);
					if (movie!=null) setText(columnID.getDisplayStr.apply(movie));
				}
				
				return comp;
			}
		}
		
	}
	
}