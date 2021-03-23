package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
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
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings;
import net.schwarzbaer.system.DateTimeFormatter;

class Movies extends JSplitPane {
	private static final long serialVersionUID = 3435419463730240276L;
	
	private static final DateTimeFormatter dtFormatter = new DateTimeFormatter();
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	enum TreeIcons {
		Folder, KnownFolder;
		Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}
	
	private final JTree locationsTree;
	private final JTable movieTable;
	private final JTextArea movieInfo1;
	private final JTextArea movieInfo2;
	private Movies.LocationTreeNode locationsRoot;
	private TreePath selectedTreePath;
	private Movies.LocationTreeNode selectedTreeNode;
	private Movies.MovieTableModel movieTableModel;
	private DefaultTreeModel locationsTreeModel;
	@SuppressWarnings("unused")
	private MovieList.Movie clickedMovie;

	private StandardMainWindow mainWindow;
	
	Movies(StandardMainWindow mainWindow) {
		
		this.mainWindow = mainWindow;
		locationsRoot = null;
		locationsTreeModel = null;
		selectedTreePath = null;
		selectedTreeNode = null;
		movieTableModel = null;
		clickedMovie = null;
		
		locationsTree = new JTree(locationsTreeModel);
		locationsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		
		JScrollPane treeScrollPane = new JScrollPane(locationsTree);
		treeScrollPane.setPreferredSize(new Dimension(300,500));
		
		movieTable = new JTable(movieTableModel);
		movieTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane tableScrollPane = new JScrollPane(movieTable);
		tableScrollPane.setPreferredSize(new Dimension(600,500));
		
		ContextMenu contextMenu = new ContextMenu();
		contextMenu.add(OpenWebifController.createMenuItem("Show Column Widths", e->{
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
		
		contextMenu.addTo(movieTable);
		contextMenu.addContextMenuInvokeListener((comp, x, y) -> {
			clickedMovie = null;
			int rowV = movieTable.rowAtPoint(new Point(x,y));
			int rowM = movieTable.convertRowIndexToModel(rowV);
			if (movieTableModel!=null)
				clickedMovie = movieTableModel.getValue(rowM);
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
				if (lastPathComponent instanceof Movies.LocationTreeNode)
					selectedTreeNode = (Movies.LocationTreeNode) lastPathComponent;
			}
			if (selectedTreeNode!=null) {
				String path = selectedTreeNode.getDirPath();
				Movies.MovieList movieList = getMovieList(path);
				locationsRoot.addLocations(movieList.directory,movieList.bookmarks,locationsTreeModel);
				movieTable.setModel(movieTableModel = new MovieTableModel(movieList.movies));
				movieTableModel.initializeWith(movieTable);
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

	private void showMovie(MovieList.Movie movie) {
		if (movie==null) return;
		
		String baseURL = getBaseURL();
		if (baseURL==null) return;
		
		File videoPlayer = getVideoPlayer();
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

	private Movies.MovieList getMovieList(String dir) {
		String baseURL = getBaseURL();
		
		Movies.MovieList movieList = null;
		if (baseURL!=null) movieList = MovieList.get(baseURL,dir);
		
		return movieList;
	}

	void readInitialList() {
		Movies.MovieList movieList = getMovieList(null);
		
		if (movieList!=null) {
			locationsRoot = LocationTreeNode.create(movieList.directory,movieList.bookmarks);
			locationsTreeModel = new DefaultTreeModel(locationsRoot, true);
			locationsTree.setModel(locationsTreeModel);
			if (locationsRoot!=null) {
				TreePath treePath = locationsRoot.getTreePath(movieList.directory);
				locationsTree.expandPath(treePath);
				//locationsTree.setSelectionPath(treePath);
				movieTable.setModel(movieTableModel = new MovieTableModel(movieList.movies));
				movieTableModel.initializeWith(movieTable);
			}
		}
	}

	private String getBaseURL() {
		if (!OpenWebifController.settings.contains(AppSettings.ValueKey.BaseURL))
			return askUserForBaseURL();
		return OpenWebifController.settings.getString(AppSettings.ValueKey.BaseURL, null);
	}

	String askUserForBaseURL() {
		String baseURL = JOptionPane.showInputDialog(mainWindow, "Set BaseURL:", "Set BaseURL", JOptionPane.QUESTION_MESSAGE);
		if (baseURL!=null)
			OpenWebifController.settings.putString(AppSettings.ValueKey.BaseURL, baseURL);
		return baseURL;
	}

	private File getVideoPlayer() {
		if (!OpenWebifController.settings.contains(AppSettings.ValueKey.VideoPlayer))
			return askUserForVideoPlayer();
		return OpenWebifController.settings.getFile(AppSettings.ValueKey.VideoPlayer, null);
	}

	File askUserForVideoPlayer() {
		JFileChooser exeFileChooser = new JFileChooser("./");
		exeFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		exeFileChooser.setMultiSelectionEnabled(false);
		exeFileChooser.setFileFilter(new FileNameExtensionFilter("Executable (*.exe)","exe"));
		exeFileChooser.setDialogTitle("Select VideoPlayer");
	
		File videoPlayer = null;
		if (exeFileChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
			videoPlayer = exeFileChooser.getSelectedFile();
			OpenWebifController.settings.putFile(AppSettings.ValueKey.VideoPlayer, videoPlayer);
		}
		return videoPlayer;
	}

	private static class LocationTreeNode implements TreeNode {
	
		private final Movies.LocationTreeNode parent;
		private final String name;
		private final Vector<Movies.LocationTreeNode> children;
	
		static Movies.LocationTreeNode create(String path, Vector<String> children) {
			// path: /media/hdd/movie-storage/_unsortiert/
			String[] names = splitToNames(path);
			if (names==null) return null;
			
			Movies.LocationTreeNode root = new LocationTreeNode(null,names[0]);
			Movies.LocationTreeNode p = root;
			for (int i=1; i<names.length; i++) {
				Movies.LocationTreeNode newNode = new LocationTreeNode(p,names[i]);
				p.addChild(newNode);
				p = newNode;
			}
			
			if (children!=null)
				for (String childName:children) {
					Movies.LocationTreeNode newNode = new LocationTreeNode(p,childName);
					p.addChild(newNode);
				}
			
			return root;
		}
	
		public void addLocations(String path, Vector<String> children, DefaultTreeModel treeModel) {
			String[] names = splitToNames(path);
			if (names==null) return;
			
			if (!name.equals(names[0])) return;
			Movies.LocationTreeNode node = this;
			
			for (int i=1; i<names.length; i++) {
				String name = names[i];
				Movies.LocationTreeNode childNode = node.getChild(name);
				if (childNode==null) {
					childNode = new LocationTreeNode(node, name);
					int index = node.addChild(childNode);
					treeModel.nodesWereInserted(node, new int[]{ index });
				}
				node = childNode;
			}
			
			if (children!=null) {
				int[] newIndexes = new int[children.size()];
				int i=0;
				for (String childName:children) {
					Movies.LocationTreeNode childNode = node.getChild(childName);
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
		
		LocationTreeNode(Movies.LocationTreeNode parent, String name) {
			this.parent = parent;
			this.name = name;
			this.children = new Vector<>();
		}
	
		@Override public String toString() { return name; }
		
		public TreePath getTreePath(String path) {
			String[] names = splitToNames(path);
			if (names==null) return null;
			
			Movies.LocationTreeNode node = this;
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
	
		private int addChild(Movies.LocationTreeNode child) {
			int index = children.size();
			children.add(child);
			return index;
		}
	
		private Movies.LocationTreeNode getChild(String name) {
			for (Movies.LocationTreeNode child:children)
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
	
		static Movies.MovieList get(String baseURL, String dir) {
			String urlStr = String.format("%s/api/movielist", baseURL);
			if (dir!=null) {
				try { dir = URLEncoder.encode(dir, "UTF-8");
				} catch (UnsupportedEncodingException e) { System.err.printf("Exception while converting directory name: [UnsupportedEncodingException] %s%n", e.getMessage()); }
				urlStr = String.format("%s?dirname=%s", urlStr, dir);
			}
			System.out.printf("get MovieList: \"%s\"%n", urlStr);
			
			String content = OpenWebifController.getContent(urlStr);
			Value<MovieList.NV, MovieList.V> result = new JSON_Parser<MovieList.NV,MovieList.V>(content,null).parse();
			
			try {
				return new MovieList(result);
			} catch (TraverseException e) {
				System.err.printf("Exception while parsing JSON structure: %s%n", e.getMessage());
				return null;
			}
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
			
			directory = JSON_Data.getStringValue(object, "directory", debugOutputPrefixStr);
			JSON_Array<MovieList.NV, MovieList.V> bookmarks = JSON_Data.getArrayValue(object, "bookmarks", debugOutputPrefixStr);
			JSON_Array<MovieList.NV, MovieList.V> movies    = JSON_Data.getArrayValue(object, "movies", debugOutputPrefixStr);
			
			this.bookmarks = new Vector<>();
			for (int i=0; i<bookmarks.size(); i++)
				this.bookmarks.add(JSON_Data.getStringValue(bookmarks.get(i), debugOutputPrefixStr+".bookmarks["+i+"]"));
			
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
	
		@Override public Object getValueAt(int rowIndex, int columnIndex, MovieTableModel.ColumnID columnID) {
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