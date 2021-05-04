package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
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
import net.schwarzbaer.system.DateTimeFormatter;
import net.schwarzbaer.system.Settings;

public class OpenWebifController {
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	enum TreeIcons {
		Folder, GreenFolder;
		Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}
	
	static AppSettings settings;
	static DateTimeFormatter dateTimeFormatter = new DateTimeFormatter();

	public static void main(String[] args) {
		if (args.length>0) {
			if (args[0].equals("-start") && args.length>1) {
				String[] parameters = Arrays.copyOfRange(args, 1, args.length);
				System.out.printf("start something:%n");
				System.out.printf("   parameters : %s%n", Arrays.toString(parameters));
				
				try { Runtime.getRuntime().exec(parameters); }
				catch (IOException ex) { System.err.printf("IOException while starting: %s%n", ex.getMessage()); }
				
				return;
			}
		}
		
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		settings = new AppSettings();
		new OpenWebifController()
			.initialize();
	}
	
	static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, VideoPlayer, BaseURL, Browser, JavaVM,
			BouquetsNStations_UpdateEPGAlways, BouquetsNStations_TextViewLineWrap, BouquetsNStations_UpdatePlayableStates,
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

	static class Updater {
		private final ScheduledExecutorService scheduler;
		private ScheduledFuture<?> taskHandle;
		private final long interval_sec;
		private final Runnable task;
	
		@SuppressWarnings("unused")
		Updater(long interval_sec, Runnable task) {
			this.interval_sec = interval_sec;
			this.task = task;
			
			int prio;
			if      (Thread.MIN_PRIORITY<Thread.NORM_PRIORITY-2) prio = Thread.NORM_PRIORITY-2;
			else if (Thread.MIN_PRIORITY<Thread.NORM_PRIORITY-1) prio = Thread.NORM_PRIORITY-1;
			else prio = Thread.NORM_PRIORITY;
			
			scheduler = Executors.newSingleThreadScheduledExecutor(run->{
				Thread thread = new Thread(run);
				thread.setPriority(prio);
				return thread;
			});
		}
	
		public void runOnce() {
			scheduler.execute(task);
		}
	
		public void runOnce(Runnable task) {
			scheduler.execute(task);
		}
	
		public void start() {
			taskHandle = scheduler.scheduleWithFixedDelay(task, 0, interval_sec, TimeUnit.SECONDS);
		}
	
		public void stop() {
			taskHandle.cancel(false);
			taskHandle = null;
		}
		
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
		
		settingsMenu.add(createMenuItem("Set Base URL", e->{
			String baseURL = askUserForBaseURL();
			if (baseURL!=null)
				System.out.printf("Set Base URL to \"%s\"%n", baseURL);
		}));
		settingsMenu.addSeparator();
		settingsMenu.add(createMenuItem("Set Path to VideoPlayer", e->{
			File file = askUserForVideoPlayer();
			if (file!=null) System.out.printf("Set VideoPlayer to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(createMenuItem("Set Path to Java VM", e->{
			File file = askUserForJavaVM();
			if (file!=null) System.out.printf("Set Java VM to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(createMenuItem("Set Path to Browser", e->{
			File file = askUserForBrowser();
			if (file!=null) System.out.printf("Set Browser to \"%s\"%n", file.getAbsolutePath());
		}));
		
		mainWindow.setIconImagesFromResource("/AppIcons/AppIcon","16.png","24.png","32.png","48.png","64.png");
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

	static JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isChecked, Consumer<Boolean> setValue) {
		JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isChecked);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
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

	void openUrlInVideoPlayer(String url, String taskLabel) { openInVideoPlayer(taskLabel, "URL", url); }
	void openFileInVideoPlayer(File file, String taskLabel) { openInVideoPlayer(taskLabel, "File", file.getAbsolutePath()); }

	private void openInVideoPlayer(String taskLabel, String targetLabel, String target) {
		File videoPlayer = getVideoPlayer();
		if (videoPlayer==null) return;
		
		File javaVM = getJavaVM();
		if (javaVM==null) return;
		
		System.out.printf("%s%n", taskLabel);
		System.out.printf("   Java VM      : \"%s\"%n", javaVM.getAbsolutePath());
		System.out.printf("   Video Player : \"%s\"%n", videoPlayer.getAbsolutePath());
		System.out.printf("   %12s"     +" : \"%s\"%n", targetLabel, target);
		
		try { Runtime.getRuntime().exec(new String[] {javaVM.getAbsolutePath(), "-jar", "OpenWebifController.jar", "-start", videoPlayer.getAbsolutePath(), target }); }
		catch (IOException ex) { System.err.printf("IOException while starting video player: %s%n", ex.getMessage()); }
	}

	String getBaseURL() {
		return getBaseURL(true);
	}
	String getBaseURL(boolean askUser) {
		if (!settings.contains(AppSettings.ValueKey.BaseURL) && askUser)
			return askUserForBaseURL();
		return settings.getString(AppSettings.ValueKey.BaseURL, null);
	}

	private String askUserForBaseURL() {
		String baseURL = JOptionPane.showInputDialog(mainWindow, "Set BaseURL:", "Set BaseURL", JOptionPane.QUESTION_MESSAGE);
		if (baseURL!=null)
			settings.putString(AppSettings.ValueKey.BaseURL, baseURL);
		return baseURL;
	}

	File getJavaVM     () { return getExecutable(AppSettings.ValueKey.JavaVM     , "Select Java VM"); }
	File getVideoPlayer() { return getExecutable(AppSettings.ValueKey.VideoPlayer, "Select VideoPlayer"); }
	File getBrowser    () { return getExecutable(AppSettings.ValueKey.Browser    , "Select Browser"    ); }
	private File askUserForJavaVM     () { return askUserForExecutable(AppSettings.ValueKey.JavaVM     , "Select Java VM"    ); }
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
