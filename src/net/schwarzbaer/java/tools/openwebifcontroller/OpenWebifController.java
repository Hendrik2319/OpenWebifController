package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.system.Settings;

public class OpenWebifController {
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	enum TreeIcons {
		Folder, GreenFolder;
		Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}
	
	static AppSettings settings;

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		settings = new AppSettings();
		new OpenWebifController()
			.initialize();
	}
	
	static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, VideoPlayer, BaseURL, Browser,
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

	final StandardMainWindow mainWindow;
	private final Movies movies;
	private final JFileChooser exeFileChooser;
	private final BouquetsNStations bouquetsNStations;

	OpenWebifController() {
		exeFileChooser = new JFileChooser("./");
		exeFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		exeFileChooser.setMultiSelectionEnabled(false);
		exeFileChooser.setFileFilter(new FileNameExtensionFilter("Executable (*.exe)","exe"));
		
		mainWindow = new StandardMainWindow("OpenWebif Controller");
		movies = new Movies(this);
		bouquetsNStations = new BouquetsNStations(this);
		
		JTabbedPane contentPane = new JTabbedPane();
		contentPane.addTab("Movies", movies);
		contentPane.addTab("Bouquets 'n' Stations", bouquetsNStations);
		contentPane.setSelectedIndex(1);
		
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = menuBar.add(createMenu("Settings"));
		
		settingsMenu.add(createMenuItem("Set Path to VideoPlayer", e->{
			File file = askUserForVideoPlayer();
			if (file!=null) System.out.printf("Set VideoPlayer to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(createMenuItem("Set Path to Browser", e->{
			File file = askUserForBrowser();
			if (file!=null) System.out.printf("Set VideoPlayer to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(createMenuItem("Set Base URL", e->{
			String baseURL = askUserForBaseURL();
			if (baseURL!=null)
				System.out.printf("Set Base URL to \"%s\"%n", baseURL);
		}));
		
		mainWindow.startGUI(contentPane, menuBar);
		
		if (settings.isSet(AppSettings.ValueGroup.WindowPos )) mainWindow.setLocation(settings.getWindowPos ());
		if (settings.isSet(AppSettings.ValueGroup.WindowSize)) mainWindow.setSize    (settings.getWindowSize());
		
		mainWindow.addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) { settings.setWindowSize( mainWindow.getSize() ); }
			@Override public void componentMoved  (ComponentEvent e) { settings.setWindowPos ( mainWindow.getLocation() ); }
		});
	}
	
	private void initialize() {
		ProgressDialog.runWithProgressDialog(mainWindow, "Initialize", 400, pd->{
			String baseURL = getBaseURL();
			if (baseURL==null) return;
			
			movies.readInitialMovieList(baseURL,pd);
			bouquetsNStations.readData(baseURL,pd);
		});
	}

	static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JMenu createMenu(String title) {
		return new JMenu(title);
	}

	static String toString(Process process) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Process", process.toString());
		try { out.add(0, "Exit Value", process.exitValue()); }
		catch (Exception e) { out.add(0, "Exit Value", "%s", e.getMessage()); }
		out.add(0, "HashCode"  , "0x%08X", process.hashCode());
		out.add(0, "Is Alive"  , process.isAlive());
		out.add(0, "Class"     , "%s", process.getClass());
		return out.generateOutput();
	}

	String getBaseURL() {
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

	File getVideoPlayer() { return getExecutable(AppSettings.ValueKey.VideoPlayer, "Select VideoPlayer"); }
	File getBrowser    () { return getExecutable(AppSettings.ValueKey.Browser    , "Select Browser"    ); }
	private File askUserForVideoPlayer() { return askUserForExecutable(AppSettings.ValueKey.VideoPlayer, "Select VideoPlayer"); }
	private File askUserForBrowser    () { return askUserForExecutable(AppSettings.ValueKey.Browser    , "Select Browser"    ); }

	private File getExecutable(AppSettings.ValueKey valueKey, String dialogTitle) {
		if (!settings.contains(valueKey))
			return askUserForExecutable(valueKey, dialogTitle);
		return settings.getFile(valueKey, null);
	}

	private File askUserForExecutable(AppSettings.ValueKey valueKey, String dialogTitle) {
		exeFileChooser.setDialogTitle(dialogTitle);
	
		File executable = null;
		if (exeFileChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
			executable = exeFileChooser.getSelectedFile();
			settings.putFile(valueKey, executable);
		}
		return executable;
	}
}
