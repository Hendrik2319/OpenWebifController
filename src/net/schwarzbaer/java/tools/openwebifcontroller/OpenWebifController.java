package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;
import java.util.function.Function;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ContextMenu;
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
import net.schwarzbaer.system.Settings;

public class OpenWebifController {
	
	private static AppSettings settings;

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		settings = new AppSettings();
		new OpenWebifController()
			.readInitialList();
	}
	
	private static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		private enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, VideoPlayer, BaseURL,
		}

		private enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			WindowPos (ValueKey.WindowX, ValueKey.WindowY),
			WindowSize(ValueKey.WindowWidth, ValueKey.WindowHeight),
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}

		AppSettings() {
			super(OpenWebifController.class);
		}
		public Point     getWindowPos (              ) { return getPoint(ValueKey.WindowX,ValueKey.WindowY); }
		public void      setWindowPos (Point location) {        putPoint(ValueKey.WindowX,ValueKey.WindowY,location); }
		public Dimension getWindowSize(              ) { return getDimension(ValueKey.WindowWidth,ValueKey.WindowHeight); }
		public void      setWindowSize(Dimension size) {        putDimension(ValueKey.WindowWidth,ValueKey.WindowHeight,size); }
		
	}

	private final StandardMainWindow mainWindow;
	private final JTree locationsTree;
	private final JTable movieTable;
	private final JTextArea movieInfo;
	private LocationTreeNode locationsRoot;
	private TreePath selectedTreePath;
	private LocationTreeNode selectedTreeNode;
	private MovieTableModel movieTableModel;
	private DefaultTreeModel locationsTreeModel;
	@SuppressWarnings("unused")
	private MovieList.Movie clickedMovie;

	OpenWebifController() {
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
		contextMenu.add(createMenuItem("Show Column Widths", e->{
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
		
		
		movieInfo = new JTextArea();
		movieInfo.setEditable(false);
		movieInfo.setLineWrap(true);
		movieInfo.setWrapStyleWord(true);
		JScrollPane movieInfoScrollPane = new JScrollPane(movieInfo);
		movieInfoScrollPane.setPreferredSize(new Dimension(400,500));
		
		JPanel rightPanel = new JPanel(new BorderLayout(3,3));
		rightPanel.add(tableScrollPane,BorderLayout.CENTER);
		rightPanel.add(movieInfoScrollPane,BorderLayout.EAST);
		
		JSplitPane contentPane = new JSplitPane();
		contentPane.setLeftComponent(treeScrollPane);
		contentPane.setRightComponent(rightPanel);
		
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = menuBar.add(createMenu("Settings"));
		settingsMenu.add(createMenuItem("Set Path to VideoPlayer", e->{
			File videoPlayer = askUserForVideoPlayer();
			if (videoPlayer!=null)
				System.out.printf("Set VideoPlayer to \"%s\"%n", videoPlayer.getAbsolutePath());
		}));
		settingsMenu.add(createMenuItem("Set Base URL", e->{
			String baseURL = askUserForBaseURL();
			if (baseURL!=null)
				System.out.printf("Set Base URL to \"%s\"%n", baseURL);
		}));
		
		mainWindow = new StandardMainWindow("OpenWebif Controller");
		mainWindow.startGUI(contentPane, menuBar);
		
		if (settings.isSet(AppSettings.ValueGroup.WindowPos )) mainWindow.setLocation(settings.getWindowPos ());
		if (settings.isSet(AppSettings.ValueGroup.WindowSize)) mainWindow.setSize    (settings.getWindowSize());
		
		mainWindow.addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) { settings.setWindowSize( mainWindow.getSize() ); }
			@Override public void componentMoved  (ComponentEvent e) { settings.setWindowPos ( mainWindow.getLocation() ); }
		});
		
		
		locationsTree.addTreeSelectionListener(e -> {
			selectedTreePath = e.getPath();
			selectedTreeNode = null;
			if (selectedTreePath!=null) {
				Object lastPathComponent = selectedTreePath.getLastPathComponent();
				if (lastPathComponent instanceof LocationTreeNode)
					selectedTreeNode = (LocationTreeNode) lastPathComponent;
			}
			if (selectedTreeNode!=null) {
				String path = selectedTreeNode.getDirPath();
				MovieList movieList = getMovieList(path);
				locationsRoot.addLocations(movieList.directory,movieList.bookmarks,locationsTreeModel);
				movieTable.setModel(movieTableModel = new MovieTableModel(movieList.movies));
				movieTableModel.setColumnWidths(movieTable);
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

	private JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	private JMenu createMenu(String title) {
		return new JMenu(title);
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
			System.out.println(toString(process));
		}
		catch (IOException e) { System.err.printf("IOException while starting movie player: %s%n", e.getMessage()); }
	}

	private String toString(Process process) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Process", process.toString());
		try { out.add(0, "Exit Value", process.exitValue()); }
		catch (Exception e) { out.add(0, "Exit Value", "%s", e.getMessage()); }
		out.add(0, "HashCode"  , "0x%08X", process.hashCode());
		out.add(0, "Is Alive"  , process.isAlive());
		out.add(0, "Class"     , "%s", process.getClass());
		return out.generateOutput();
	}

	private void showValues(MovieList.Movie movie) {
		if (movie==null) {
			movieInfo.setText("");
			return;
		}
		
		ValueListOutput out = new ValueListOutput();
		out.add(0, "eventname          ", movie.eventname          );
		out.add(0, "servicename        ", movie.servicename        );
		out.add(0, "length             ", movie.length             );
		out.add(0, "begintime          ", movie.begintime          );
		out.add(0, "recordingtime      ", movie.recordingtime      );
		out.add(0, "lastseen           ", movie.lastseen           );
		out.add(0, "filesize           ", movie.filesize           );
		out.add(0, "filesize_readable  ", movie.filesize_readable  );
		out.add(0, "tags               ", movie.tags               );
		out.add(0, "filename           ", movie.filename           );
		out.add(0, "filename_stripped  ", movie.filename_stripped  );
		out.add(0, "fullname           ", movie.fullname           );
		out.add(0, "serviceref         ", movie.serviceref         );
		out.add(0, "description        ", movie.description        );
		out.add(0, "descriptionExtended", movie.descriptionExtended);
		movieInfo.setText(out.generateOutput());
	}

	private void readInitialList() {
		MovieList movieList = getMovieList(null);
		
		if (movieList!=null) {
			locationsRoot = LocationTreeNode.create(movieList.directory,movieList.bookmarks);
			locationsTreeModel = new DefaultTreeModel(locationsRoot, true);
			locationsTree.setModel(locationsTreeModel);
			if (locationsRoot!=null) {
				TreePath treePath = locationsRoot.getTreePath(movieList.directory);
				locationsTree.expandPath(treePath);
				//locationsTree.setSelectionPath(treePath);
				movieTable.setModel(movieTableModel = new MovieTableModel(movieList.movies));
				movieTableModel.setColumnWidths(movieTable);
			}
		}
	}
	
	private MovieList getMovieList(String dir) {
		String baseURL = getBaseURL();
		
		MovieList movieList = null;
		if (baseURL!=null) movieList = MovieList.get(baseURL,dir);
		
		return movieList;
	}

	private String getBaseURL() {
		if (!settings.contains(AppSettings.ValueKey.BaseURL))
			return askUserForBaseURL();
		return settings.getString(AppSettings.ValueKey.BaseURL, null);
	}

	private String askUserForBaseURL() {
		String baseURL = JOptionPane.showInputDialog(mainWindow, "Set BaseURL:", "Set BaseURL", JOptionPane.QUESTION_MESSAGE);
		if (baseURL!=null)
			settings.putString(AppSettings.ValueKey.BaseURL, baseURL);
		return baseURL;
	}

	private File getVideoPlayer() {
		if (!settings.contains(AppSettings.ValueKey.VideoPlayer))
			return askUserForVideoPlayer();
		return settings.getFile(AppSettings.ValueKey.VideoPlayer, null);
	}

	private File askUserForVideoPlayer() {
		JFileChooser exeFileChooser = new JFileChooser("./");
		exeFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		exeFileChooser.setMultiSelectionEnabled(false);
		exeFileChooser.setFileFilter(new FileNameExtensionFilter("Executable (*.exe)","exe"));
		exeFileChooser.setDialogTitle("Select VideoPlayer");

		File videoPlayer = null;
		if (exeFileChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
			videoPlayer = exeFileChooser.getSelectedFile();
			settings.putFile(AppSettings.ValueKey.VideoPlayer, videoPlayer);
		}
		return videoPlayer;
	}

	private static String decodeUnicode(String str) {
		if (str==null) return null;
		int pos;
		int startPos = 0;
		while ( (pos=str.indexOf("\\u",startPos))>=0 ) {
			if (str.length()<pos+6) break;
			String prefix = str.substring(0, pos);
			String suffix = str.substring(pos+6);
			String codeStr = str.substring(pos+2,pos+6);
			int code;
			try { code = Integer.parseUnsignedInt(codeStr,16); }
			catch (NumberFormatException e) { startPos = pos+2; continue; }
			str = prefix + ((char)code) + suffix;
		}
		return str;
	}

	private static String getContent(String urlStr) {
		URL url;
		try { url = new URL(urlStr); }
		catch (MalformedURLException e) { System.err.printf("MalformedURL: %s%n", e.getMessage()); return null; }
		
		URLConnection conn;
		try { conn = url.openConnection(); }
		catch (IOException e) { System.err.printf("url.openConnection -> IOException: %s%n", e.getMessage()); return null; }
		
		conn.setDoInput(true);
		try { conn.connect(); }
		catch (IOException e) { System.err.printf("conn.connect -> IOException: %s%n", e.getMessage()); return null; }
		
		ByteArrayOutputStream storage = new ByteArrayOutputStream();
		try (BufferedInputStream in = new BufferedInputStream( conn.getInputStream() )) {
			byte[] buffer = new byte[100000];
			int n;
			while ( (n=in.read(buffer))>=0 )
				if (n>0) storage.write(buffer, 0, n);
			
		} catch (IOException e) {
			System.err.printf("IOException: %s%n", e.getMessage());
		}
		
		return new String(storage.toByteArray());
	}
	
	private static class LocationTreeNode implements TreeNode {

		private final LocationTreeNode parent;
		private final String name;
		private final Vector<LocationTreeNode> children;

		static LocationTreeNode create(String path, Vector<String> children) {
			// path: /media/hdd/movie-storage/_unsortiert/
			String[] names = splitToNames(path);
			if (names==null) return null;
			
			LocationTreeNode root = new LocationTreeNode(null,names[0]);
			LocationTreeNode p = root;
			for (int i=1; i<names.length; i++) {
				LocationTreeNode newNode = new LocationTreeNode(p,names[i]);
				p.addChild(newNode);
				p = newNode;
			}
			
			if (children!=null)
				for (String childName:children) {
					LocationTreeNode newNode = new LocationTreeNode(p,childName);
					p.addChild(newNode);
				}
			
			return root;
		}

		public void addLocations(String path, Vector<String> children, DefaultTreeModel treeModel) {
			String[] names = splitToNames(path);
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
			
			if (children!=null) {
				int[] newIndexes = new int[children.size()];
				int i=0;
				for (String childName:children) {
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
		
		LocationTreeNode(LocationTreeNode parent, String name) {
			this.parent = parent;
			this.name = name;
			this.children = new Vector<>();
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

		static MovieList get(String baseURL, String dir) {
			String urlStr = String.format("%s/api/movielist", baseURL);
			if (dir!=null) {
				try { dir = URLEncoder.encode(dir, "UTF-8");
				} catch (UnsupportedEncodingException e) { System.err.printf("Exception while converting directory name: [UnsupportedEncodingException] %s%n", e.getMessage()); }
				urlStr = String.format("%s?dirname=%s", urlStr, dir);
			}
			System.out.printf("get MovieList: \"%s\"%n", urlStr);
			
			String content = getContent(urlStr);
			Value<NV, V> result = new JSON_Parser<NV,V>(content,null).parse();
			
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
		final Vector<Movie> movies;
		
		public MovieList(Value<NV, V> result) throws TraverseException {
			//JSON_Helper.OptionalValues<NV, V> optionalValueScan = new JSON_Helper.OptionalValues<NV,V>();
			//optionalValueScan.scan(result, "MovieList");
			//optionalValueScan.show(System.out);
			
			String debugOutputPrefixStr = "MovieList";
			JSON_Object<NV, V> object = JSON_Data.getObjectValue(result, debugOutputPrefixStr);
			
			directory = JSON_Data.getStringValue(object, "directory", debugOutputPrefixStr);
			JSON_Array<NV, V> bookmarks = JSON_Data.getArrayValue(object, "bookmarks", debugOutputPrefixStr);
			JSON_Array<NV, V> movies    = JSON_Data.getArrayValue(object, "movies", debugOutputPrefixStr);
			
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
			final String length;
			final long recordingtime;
			final String servicename;
			final String serviceref;
			final String tags;

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

			public Movie(Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
				JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
				
				begintime           = decodeUnicode( JSON_Data.getStringValue (object, "begintime"          , debugOutputPrefixStr) ); 
				description         = decodeUnicode( JSON_Data.getStringValue (object, "description"        , debugOutputPrefixStr) );
				descriptionExtended = decodeUnicode( JSON_Data.getStringValue (object, "descriptionExtended", debugOutputPrefixStr) );
				eventname           = decodeUnicode( JSON_Data.getStringValue (object, "eventname"          , debugOutputPrefixStr) );
				filename            = decodeUnicode( JSON_Data.getStringValue (object, "filename"           , debugOutputPrefixStr) );
				filename_stripped   = decodeUnicode( JSON_Data.getStringValue (object, "filename_stripped"  , debugOutputPrefixStr) );
				filesize            =                JSON_Data.getIntegerValue(object, "filesize"           , debugOutputPrefixStr)  ;
				filesize_readable   = decodeUnicode( JSON_Data.getStringValue (object, "filesize_readable"  , debugOutputPrefixStr) );
				fullname            = decodeUnicode( JSON_Data.getStringValue (object, "fullname"           , debugOutputPrefixStr) );
				lastseen            =                JSON_Data.getIntegerValue(object, "lastseen"           , debugOutputPrefixStr)  ;
				length              = decodeUnicode( JSON_Data.getStringValue (object, "length"             , debugOutputPrefixStr) );
				recordingtime       =                JSON_Data.getIntegerValue(object, "recordingtime"      , debugOutputPrefixStr)  ;
				servicename         = decodeUnicode( JSON_Data.getStringValue (object, "servicename"        , debugOutputPrefixStr) );
				serviceref          = decodeUnicode( JSON_Data.getStringValue (object, "serviceref"         , debugOutputPrefixStr) );
				tags                = decodeUnicode( JSON_Data.getStringValue (object, "tags"               , debugOutputPrefixStr) );
			}
			
		}
	}

	static class MovieTableModel extends Tables.SimplifiedTableModel<MovieTableModel.ColumnID> {

		private enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Name   ("Name"   , String.class, 280, m->m.eventname),
			Length ("Length" , String.class,  60, m->m.length),
			Station("Station", String.class,  80, m->m.servicename),
			File   ("File"   , String.class, 450, m->m.filename_stripped),
			Size   ("Size"   , String.class,  70, m->m.filesize_readable),
			;
		
			final SimplifiedColumnConfig cfg;
			final Function<MovieList.Movie, Object> getValue;
			
			ColumnID(String name, Class<?> columnClass, int prefWidth, Function<MovieList.Movie,Object> getValue) {
				this.getValue = getValue;
				cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, prefWidth, prefWidth);
			}
			
			@Override public SimplifiedColumnConfig getColumnConfig() {
				return cfg;
			}
			
		}

		private final Vector<MovieList.Movie> movies;

		protected MovieTableModel(Vector<MovieList.Movie> movies) {
			super(ColumnID.values());
			this.movies = movies;
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
	
	}
}
