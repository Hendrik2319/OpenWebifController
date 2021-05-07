package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Volume;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;
import net.schwarzbaer.system.DateTimeFormatter;
import net.schwarzbaer.system.Settings;

public class OpenWebifController {
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	public enum TreeIcons {
		Folder, GreenFolder;
		public Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}
	
	public static AppSettings settings;
	public static DateTimeFormatter dateTimeFormatter = new DateTimeFormatter();

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
	
	public static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		public enum ValueKey {
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

	public static class Updater {
		private final ScheduledExecutorService scheduler;
		private ScheduledFuture<?> taskHandle;
		private final long interval_sec;
		private final Runnable task;
	
		public Updater(long interval_sec, Runnable task) {
			this.interval_sec = interval_sec;
			this.task = task;
			scheduler = Executors.newSingleThreadScheduledExecutor();
			
			//int prio;
			//if      (Thread.MIN_PRIORITY<Thread.NORM_PRIORITY-2) prio = Thread.NORM_PRIORITY-2;
			//else if (Thread.MIN_PRIORITY<Thread.NORM_PRIORITY-1) prio = Thread.NORM_PRIORITY-1;
			//else prio = Thread.NORM_PRIORITY;
			//
			//scheduler = Executors.newSingleThreadScheduledExecutor(run->{
			//	Thread thread = new Thread(run);
			//	thread.setPriority(prio);
			//	return thread;
			//});
		}
	
		public void runOnce() {
			runOnce(task);
		}
	
		public void runOnce(Runnable task) {
			//System.out.printf("[0x%08X|%s] Updater.runOnce()%n"         , Thread.currentThread().hashCode(), getCurrentTimeStr());
			scheduler.execute(task);
			//System.out.printf("[0x%08X|%s] Updater.runOnce() finished%n", Thread.currentThread().hashCode(), getCurrentTimeStr());
		}
	
		public void start() {
			//System.out.printf("[0x%08X|%s] Updater.start()%n"         , Thread.currentThread().hashCode(), getCurrentTimeStr());
			taskHandle = scheduler.scheduleWithFixedDelay(task, 0, interval_sec, TimeUnit.SECONDS);
			//System.out.printf("[0x%08X|%s] Updater.start() finished%n", Thread.currentThread().hashCode(), getCurrentTimeStr());
		}
	
		public void stop() {
			taskHandle.cancel(false);
			taskHandle = null;
		}
		
	}

	private final StandardMainWindow mainWindow;
	private final Movies movies;
	private final JFileChooser exeFileChooser;
	private final BouquetsNStations bouquetsNStations;
	private final GeneralContol generalContol;

	OpenWebifController() {
		exeFileChooser = new JFileChooser("./");
		exeFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		exeFileChooser.setMultiSelectionEnabled(false);
		exeFileChooser.setFileFilter(new FileNameExtensionFilter("Executable (*.exe)","exe"));
		
		mainWindow = new StandardMainWindow("OpenWebif Controller");
		movies = new Movies(this,mainWindow);
		bouquetsNStations = new BouquetsNStations(this,mainWindow);
		
		JTabbedPane tabPanel = new JTabbedPane();
		tabPanel.addTab("Movies", movies);
		tabPanel.addTab("Bouquets 'n' Stations", bouquetsNStations);
		tabPanel.setSelectedIndex(1);
		
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
		
		generalContol = new GeneralContol(this);
		
		JPanel toolBar = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 0;
		toolBar.add(generalContol.getPowerPanel(), c);
		toolBar.add(generalContol.getVolumePanel(), c);
		c.weightx = 1;
		toolBar.add(new JLabel(""), c);
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(tabPanel,BorderLayout.CENTER);
		contentPane.add(toolBar,BorderLayout.NORTH);
		
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
			generalContol.initialize(baseURL,pd);
		});
	}

	static class GeneralContol {
		
		private final OpenWebifController main;
		private final JPanel toolBarPower;
		private final JPanel toolBarVolume;
		private final JButton btnPower;
		private final JTextField txtVolume;
		private final JSlider sldrVolume;
		private final Color defaultTextColor;
		private final JButton btnVolUp;
		private final JButton btnVolDown;
		private final JButton btnVolMute;
		private boolean ignoreSldrVolumeEvents;
		
		GeneralContol(OpenWebifController main) {
			this.main = main;
			
			toolBarPower = new JPanel(new GridBagLayout());
			GridBagConstraints c1 = new GridBagConstraints();
			c1.weightx = 0; c1.weighty = 0;
			toolBarPower.setBorder(BorderFactory.createTitledBorder("Power"));
			toolBarPower.add(btnPower = createButton("on / off", null, true, e->{}), c1);
			
			toolBarVolume = new JPanel(new GridBagLayout());
			GridBagConstraints c2 = new GridBagConstraints();
			c2.weightx = 0; c2.weighty = 0;
			toolBarVolume.setBorder(BorderFactory.createTitledBorder("Volume"));
			toolBarVolume.add(txtVolume  = new JTextField("mute",7), c2);
			toolBarVolume.add(sldrVolume = new JSlider(JSlider.HORIZONTAL,0,100,75), c2);
			toolBarVolume.add(btnVolUp   = createButton("up"  , null, true, e->setVolUp  (null)), c2);
			toolBarVolume.add(btnVolDown = createButton("down", null, true, e->setVolDown(null)), c2);
			toolBarVolume.add(btnVolMute = createButton("mute", null, true, e->setVolMute(null)), c2);
			
			txtVolume.setEditable(false);
			txtVolume.setHorizontalAlignment(JTextField.CENTER);
			defaultTextColor = txtVolume.getForeground();
			
			ignoreSldrVolumeEvents = false;
			sldrVolume.addChangeListener(e -> {
				if (ignoreSldrVolumeEvents) return;
				
				int value = sldrVolume.getValue();
				txtVolume.setText(Integer.toString(value));
				
				if (sldrVolume.getValueIsAdjusting()) {
					txtVolume.setForeground(Color.GRAY);
					
				} else {
					txtVolume.setForeground(defaultTextColor);
					setVol(value,null);
				}
			});
			
		}
	
		void initialize(String baseURL, ProgressDialog pd) {
			callVolumeCommand(baseURL, pd, "InitVolume", Volume::getState);
		}

		JPanel getPowerPanel() {
			return toolBarPower;
		}
	
		JPanel getVolumePanel() {
			return toolBarVolume;
		}

		private void setVolUp  (     ProgressDialog pd) { callVolumeCommand(pd, "VolUp"  , Volume::setVolUp  ); }
		private void setVolDown(     ProgressDialog pd) { callVolumeCommand(pd, "VolDown", Volume::setVolDown); }
		private void setVolMute(     ProgressDialog pd) { callVolumeCommand(pd, "VolMute", Volume::setVolMute); }
		private void setVol(int vol, ProgressDialog pd) { callVolumeCommand(pd, "SetVol", (baseURL,setTaskTitle)->Volume.setVol(baseURL, vol, setTaskTitle)); }
		
		private void callVolumeCommand(ProgressDialog pd, String taskLabel, BiFunction<String, Consumer<String>, Volume.Values> commandFcn) {
			String baseURL = main.getBaseURL();
			if (baseURL==null) return;
			callVolumeCommand(baseURL, pd, taskLabel, commandFcn);
		}

		private void callVolumeCommand(String baseURL, ProgressDialog pd, String taskLabel, BiFunction<String, Consumer<String>, Volume.Values> commandFcn) {
			setVolumePanelEnable(false);
			new Thread(()->{
				Consumer<String> setTaskTitle = pd==null ? null : taskTitle->{
					SwingUtilities.invokeLater(()->{
						pd.setTaskTitle("GeneralContol."+taskLabel+": "+taskTitle);
						pd.setIndeterminate(true);
					});
				};
				Volume.Values values = commandFcn.apply(baseURL, setTaskTitle);
				SwingUtilities.invokeLater(()->{
					updateVolumePanel(values);
					setVolumePanelEnable(true);
				});
			}).start();
		}

		private void updateVolumePanel(Volume.Values values) {
			if (values==null) {
				txtVolume.setText("???");
			} else {
				String format;
				if (values.ismute) format = "mute (%d)";
				else               format = "%d";
				txtVolume.setText(String.format(format, values.current));
				ignoreSldrVolumeEvents = true;
				sldrVolume.setValue((int) values.current);
				ignoreSldrVolumeEvents = false;
			}
		}

		private void setVolumePanelEnable(boolean enabled) {
			txtVolume .setEnabled(enabled);
			sldrVolume.setEnabled(enabled);
			btnVolUp  .setEnabled(enabled);
			btnVolDown.setEnabled(enabled);
			btnVolMute.setEnabled(enabled);
		}
	}

	static String getCurrentTimeStr() {
		return dateTimeFormatter.getTimeStr(System.currentTimeMillis(), false, false, false, true, false);
	}

	public static JButton createButton(String text, Icon icon, boolean enabled, ActionListener al) {
		JButton comp = new JButton(text,icon);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	public static JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isChecked, Consumer<Boolean> setValue) {
		JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isChecked);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	public static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	public static JMenu createMenu(String title) {
		return new JMenu(title);
	}

	public static String toString(Process process) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Process", process.toString());
		try { out.add(0, "Exit Value", process.exitValue()); }
		catch (Exception e) { out.add(0, "Exit Value", "%s", e.getMessage()); }
		out.add(0, "HashCode"  , "0x%08X", process.hashCode());
		out.add(0, "Is Alive"  , process.isAlive());
		out.add(0, "Class"     , "%s", process.getClass());
		return out.generateOutput();
	}

	public void openUrlInVideoPlayer(String url, String taskLabel) { openInVideoPlayer(taskLabel, "URL", url); }
	public void openFileInVideoPlayer(File file, String taskLabel) { openInVideoPlayer(taskLabel, "File", file.getAbsolutePath()); }

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

	public String getBaseURL() {
		return getBaseURL(true);
	}
	public String getBaseURL(boolean askUser) {
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
