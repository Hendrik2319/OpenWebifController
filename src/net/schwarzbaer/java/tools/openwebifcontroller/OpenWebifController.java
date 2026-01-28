package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.LookAndFeelSwitch;
import net.schwarzbaer.java.lib.gui.MultiStepProgressDialog;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.BoxSettings;
import net.schwarzbaer.java.lib.openwebif.BoxSettings.BoxSettingsValue;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.AbstractMessageResponse;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageResponse;
import net.schwarzbaer.java.lib.openwebif.Power;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.SystemInfo;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.system.Settings;
import net.schwarzbaer.java.lib.system.Settings.DefaultAppSettings.SplitPaneDividersDefinition;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools.LogWindowInterface;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEventsViewer;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.AbstractControlPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.MessageControl;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.PowerControl;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.SwitchablePanel;
import net.schwarzbaer.java.tools.openwebifcontroller.controls.VolumeControl;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGDialog;
import net.schwarzbaer.java.tools.openwebifcontroller.stationswitch.StationSwitch;

public class OpenWebifController implements EPGDialog.ExternCommands, AbstractControlPanel.ExternCommands {
	
	public static void ASSERT( boolean predicate) { ASSERT( predicate, null ); }
	public static void ASSERT( boolean predicate, String message ) {
		if (!predicate) {
			if (message==null) throw new IllegalStateException();
			else               throw new IllegalStateException(message);
		}
	}
	
	public enum TreeIcons {
		Folder, GreenFolder;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<TreeIcons> IS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
	}
	
	public enum LedIcons {
		LED_green, LED_yellow,
		;
		public Icon getIcon() { return IS.getCachedIcon(this); }
		private static IconSource.CachedIcons<LedIcons> IS = IconSource.createCachedIcons(16, 16, "/images/LedIcons.png", LedIcons.values());
	}
	
	public enum LocalDataFile {
		AlreadySeenEvents("AlreadySeenEvents.data"),
		EPGEventGenres("EPGEventGenres.data"),
		;
		private final String filename;
		private LocalDataFile(String filename) { this.filename = filename; }
		
		public File getFileForRead()
		{
			return new File("local data",filename);
		}
		
		public File getFileForWrite()
		{
			final File folder = new File("local data");
			final File file = new File(folder,filename);
			if (!folder.isDirectory())
			{
				if (folder.isFile())
				{
					System.err.printf("Can't write to file \"%s\".%nThere is a file \"%s\" that prevents the folder \"local data\" from being created.%n", file.getAbsolutePath(), folder.getAbsolutePath());
					return null;
				}
				if (folder.exists())
				{
					System.err.printf("Can't write to file \"%s\".%nThere is something that prevents the folder \"local data\" from being created.%n", file.getAbsolutePath());
					return null;
				}
				try
				{
					Files.createDirectory(folder.toPath());
				}
				catch (IOException ex)
				{
					System.err.printf("Can't create folder \"%s\": IOException: %s%n", folder.getAbsolutePath(), ex.getMessage());
					return null;
				}
			}
			return file;
		}
	}
	
	public static AppSettings settings = new AppSettings();

	public static void main(String[] args) {
		System.out.println("OpenWebifController");
		System.out.println("by Hendrik Scholtz");
		System.out.println();
		
		if (args.length==0) {
			System.out.println("Usage");
			System.out.println("  -b, --baseurl [BaseURL]    Sets base URL of STB");
			System.out.println("  -on|-off                   Turns STB ON or OFF (=StandBy)");
			System.out.println("  -mute                      Turns volume off");
			System.out.println("  --reboot                   Restarts STB (Linux & GUI)");
			System.out.println("  --stationswitch            Start StationSwitch");
			System.out.println();
			
		} else {
			if (args[0].equals("-start") && args.length>1) {
				String[] parameters = Arrays.copyOfRange(args, 1, args.length);
				System.out.printf("start something:%n");
				System.out.printf("   parameters : %s%n", Arrays.toString(parameters));
				
				try { Runtime.getRuntime().exec(parameters); }
				catch (IOException ex) { System.err.printf("IOException while starting: %s%n", ex.getMessage()); }
				
				return;
			}
				
			String baseURL = null;
			boolean turnOn = false;
			boolean turnOff = false;
			boolean reboot = false;
			boolean mute = false;
			boolean stationswitch = false;
			
			for (int i=0; i<args.length; i++) {
				String str = args[i];
				if ( (str.equalsIgnoreCase("--baseurl") || str.equalsIgnoreCase("-b")) && i+1 < args.length) {
					baseURL = args[i+1];
					i++;
					
				} else if (str.equalsIgnoreCase("-mute")) {
					mute = true;
					
				} else if (str.equalsIgnoreCase("-on")) {
					turnOn = true;
					
				} else if (str.equalsIgnoreCase("-off")) {
					turnOff = true;
					
				} else if (str.equalsIgnoreCase("--reboot")) {
					reboot = true;
					
				} else if (str.equalsIgnoreCase("--stationswitch")) {
					stationswitch = true;
				}
			}
			
			if (turnOn || turnOff || reboot || mute || stationswitch) {
				if (baseURL==null) baseURL = getBaseURL_DontAskUser();
				if (stationswitch) {
					initializeBeforeStart();
					StationSwitch.start(baseURL,false);
					return;
				}
				if (baseURL==null) {
					System.err.println("Can't execute task: No Base URL defined.");
				} else {
					if      (turnOff) PowerControl.setState(baseURL, Power.Commands.Standby, System.out);
					else if (turnOn ) PowerControl.setState(baseURL, Power.Commands.Wakeup , System.out);
					else if (reboot ) PowerControl.setState(baseURL, Power.Commands.Reboot , System.out);
					else if (mute   ) VolumeControl.setVolMute(baseURL, System.out);
				}
				return;
			}
			
		}
		
		initializeBeforeStart();
		start(false);
	}
	
	private static void initializeBeforeStart()
	{
		UserDefColors.readFromSettings();
	}
	
	public static void start(boolean asSubWindow)
	{
//		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
//		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		LookAndFeelSwitch<AppSettings.ValueKey> lookAndFeelSwitch = new LookAndFeelSwitch<>(settings, AppSettings.ValueKey.LookAndFeel);
		lookAndFeelSwitch.setInitialLookAndFeel();
		
		new OpenWebifController(lookAndFeelSwitch, asSubWindow)
			.initialize();
	}
	
	public static class AppSettings extends Settings.DefaultAppSettings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		public enum ValueKey {
			VideoPlayer, BaseURL, Browser, JavaVM,
			BouquetsNStations_UpdateEPGAlways, BouquetsNStations_TextViewLineWrap, BouquetsNStations_UpdatePlayableStates, BouquetsNStations_UpdateCurrentStation,
			EPGDialogWidth, EPGDialogHeight, EPGDialog_TimeScale, EPGDialog_RowHeight, EPGDialog_LeadTime, EPGDialog_RangeTime,
			LogWindow_WindowX, LogWindow_WindowY, LogWindow_WindowWidth, LogWindow_WindowHeight,
			MoviesPanel_ShowDescriptionInNameColumn, 
			SplitPaneDivider_TimersPanel,
			SplitPaneDivider_SystemInfoPanel,
			SplitPaneDivider_MoviesPanel,
			SplitPaneDivider_BouquetsNStations_CenterPanel,
			SplitPaneDivider_BouquetsNStations_ValuePanel,
			SplitPaneDivider_BouquetsNStations_SingleStationEPGPanel,
			AlreadySeenEventsViewer_EpisodeStringFirst,
			AlreadySeenEventsViewer_RootSubNodeOrder,
			LookAndFeel, UserDefColors,
		}

		private enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}

		AppSettings() {
			super(OpenWebifController.class, ValueKey.values());
		}
	}

	public static class Updater {
		private static boolean SHOW_PROGRESS = false;
		
		private final ScheduledExecutorService scheduler;
		private ScheduledFuture<?> taskHandle;
		private final long interval_sec;
		private final Runnable task;
	
		public Updater(long interval_sec, Runnable task) {
			this.interval_sec = interval_sec;
			this.task = !SHOW_PROGRESS ? task : ()->{
				System.out.printf("[0x%08X|%s] Updater.task.run()%n"         , Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
				task.run();
				System.out.printf("[0x%08X|%s] Updater.task.run() finished%n", Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
			};
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
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.runOnce()%n"         , Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
			scheduler.execute(!SHOW_PROGRESS ? task : ()->{
				System.out.printf("[0x%08X|%s] Updater.runOnce() -> start task%n"   , Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
				task.run();
				System.out.printf("[0x%08X|%s] Updater.runOnce() -> task finished%n", Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
			});
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.runOnce() finished%n", Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
		}
	
		public void start() {
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.start()%n"         , Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
			taskHandle = scheduler.scheduleWithFixedDelay(task, 0, interval_sec, TimeUnit.SECONDS);
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.start() finished%n", Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
		}
	
		public void stop() {
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.stop()%n"         , Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
			taskHandle.cancel(false);
			taskHandle = null;
			if (SHOW_PROGRESS) System.out.printf("[0x%08X|%s] Updater.stop() finished%n", Thread.currentThread().hashCode(), OWCTools.getCurrentTimeStr());
		}
		
	}

	public  final StandardMainWindow mainWindow;
	private final JFileChooser exeFileChooser;
	private final MoviesPanel movies;
	private final BouquetsNStations bouquetsNStations;
	private final TimersPanel timers;
	private final ScreenShot screenShot;
	private final VolumeControl volumeControl;
	private final PowerControl powerControl;
	private final MessageControl messageControl;
	public  final EPG epg;
	public        SystemInfo systemInfo;
	public        HashMap<String, BoxSettings.BoxSettingsValue> boxSettings;
	private final SystemInfoPanel systemInfoPanel;
	private final RemoteControlPanel remoteControl;
	private final LogWindow logWindow;

	OpenWebifController(LookAndFeelSwitch<AppSettings.ValueKey> lookAndFeelSwitch, boolean asSubWindow) {
		systemInfo = null;
		boxSettings = null;
		
		exeFileChooser = new JFileChooser("./");
		exeFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		exeFileChooser.setMultiSelectionEnabled(false);
		exeFileChooser.setFileFilter(new FileNameExtensionFilter("Executable (*.exe)","exe"));
		
		mainWindow = createMainWindow("OpenWebif Controller",asSubWindow);
		
		logWindow = new LogWindow(mainWindow, "Response Log");
		
		epg = new EPG(new EPG.Tools() {
			@Override public String getTimeStr(long millis) {
				return OWCTools.dateTimeFormatter.getTimeStr(millis, false, true, false, true, false);
			}
		});
		
		movies = new MoviesPanel(this);
		timers = new TimersPanel(this);
		bouquetsNStations = new BouquetsNStations(this, timers.timerDataUpdateNotifier);
		screenShot = new ScreenShot(this, remoteControl = new RemoteControlPanel(this));
		systemInfoPanel = new SystemInfoPanel();
		
		JTabbedPane tabPanel = new JTabbedPane();
		tabPanel.addTab("System", systemInfoPanel);
		tabPanel.addTab("Movies", movies);
		tabPanel.addTab("Bouquets 'n' Stations", bouquetsNStations);
		tabPanel.addTab("Timers", timers);
		tabPanel.addTab("ScreenShot", screenShot);
		tabPanel.setSelectedIndex(2);
		
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = menuBar.add(OWCTools.createMenu("Settings"));
		
		settingsMenu.add(OWCTools.createMenuItem("Set Base URL", e->{
			String baseURL = askUserForBaseURL();
			if (baseURL!=null)
				System.out.printf("Set Base URL to \"%s\"%n", baseURL);
		}));
		settingsMenu.addSeparator();
		settingsMenu.add(OWCTools.createMenuItem("Set Path to VideoPlayer", e->{
			File file = askUserForVideoPlayer();
			if (file!=null) System.out.printf("Set VideoPlayer to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(OWCTools.createMenuItem("Set Path to Java VM", e->{
			File file = askUserForJavaVM();
			if (file!=null) System.out.printf("Set Java VM to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.add(OWCTools.createMenuItem("Set Path to Browser", e->{
			File file = askUserForBrowser();
			if (file!=null) System.out.printf("Set Browser to \"%s\"%n", file.getAbsolutePath());
		}));
		settingsMenu.addSeparator();
		settingsMenu.add(OWCTools.createMenuItem("Configure User Defined Colors", e->UserDefColors.EditDialog.showDialog(mainWindow)));
		
		JMenu updatesMenu = menuBar.add(OWCTools.createMenu("Init / Updates"));
		fillUpdatesMenu(updatesMenu);
		
		JMenu extrasMenu = menuBar.add(OWCTools.createMenu("Tools"));
		if (!asSubWindow)
			extrasMenu.add(OWCTools.createMenuItem("Station Switch", e->{
				String baseURL = getBaseURL();
				if (baseURL==null) return;
				StationSwitch.start(baseURL,true);
			}));
		extrasMenu.add(OWCTools.createMenuItem("Remote Control Tool", e->{
			new RemoteControlTool(true);
		}));
		
		lookAndFeelSwitch.setUITreeRoot(mainWindow);
		extrasMenu.add(lookAndFeelSwitch.createMenu("Look & Feel"));
		
		extrasMenu.add(OWCTools.createMenuItem("Show Definitions of Already Seen Events", e->{
			AlreadySeenEventsViewer.showViewer(mainWindow, "Already Seen Events");
		}));
		
		JMenu logsMenu = menuBar.add(OWCTools.createMenu("Logs"));
		logsMenu.add(OWCTools.createMenuItem("Show Response Log", e->{
			logWindow.showDialog();
		}));
		
		//JPanel stationSwitchPanel = new JPanel(new BorderLayout());
		//stationSwitchPanel.setBorder(BorderFactory.createTitledBorder("Station Switch"));
		//stationSwitchPanel.add(createButton("Show Station Switch", true, e->{
		//	String baseURL = getBaseURL();
		//	if (baseURL==null) return;
		//	StationSwitch.start(baseURL,true);
		//}));
		
		SwitchablePanel epgControlPanel = new SwitchablePanel(new BorderLayout(), "EPG");
		epgControlPanel.add2Panel(OWCTools.createButton("Show EPG", true, e->openEPGDialog()), BorderLayout.CENTER);
		
		JPanel toolBar = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0;
		toolBar.add(powerControl   = new PowerControl  (this, true, false, false), c);
		toolBar.add(volumeControl  = new VolumeControl (this, true, false, false), c);
		toolBar.add(messageControl = new MessageControl(this), c);
		toolBar.add(epgControlPanel, c);
		//toolBar.add(stationSwitchPanel, c);
		c.weightx = 1;
		toolBar.add(new JLabel(""), c);
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(tabPanel,BorderLayout.CENTER);
		contentPane.add(toolBar,BorderLayout.NORTH);
		
		mainWindow.startGUI(contentPane, menuBar);
		settings.registerAppWindow(mainWindow);
		settings.registerExtraWindow(logWindow,
			AppSettings.ValueKey.LogWindow_WindowX,
			AppSettings.ValueKey.LogWindow_WindowY,
			AppSettings.ValueKey.LogWindow_WindowWidth,
			AppSettings.ValueKey.LogWindow_WindowHeight
		);
		settings.registerSplitPaneDividers(
				new SplitPaneDividersDefinition<>(mainWindow, AppSettings.ValueKey.class)
				.add(timers, AppSettings.ValueKey.SplitPaneDivider_TimersPanel)
				.add(movies, AppSettings.ValueKey.SplitPaneDivider_MoviesPanel)
				.add(systemInfoPanel, AppSettings.ValueKey.SplitPaneDivider_SystemInfoPanel)
		);
	}
	
	public static StandardMainWindow createMainWindow(String title, boolean asSubWindow) {
		StandardMainWindow.DefaultCloseOperation defaultCloseOperation;
		if (asSubWindow) defaultCloseOperation = StandardMainWindow.DefaultCloseOperation.DISPOSE_ON_CLOSE;
		else             defaultCloseOperation = StandardMainWindow.DefaultCloseOperation.EXIT_ON_CLOSE;
		StandardMainWindow mainWindow = new StandardMainWindow(title, defaultCloseOperation);
		mainWindow.setIconImagesFromResource("/AppIcons/AppIcon","16.png","24.png","32.png","48.png","64.png");
		return mainWindow;
	}
	
	private void initialize() {
		new InitDialog(mainWindow).start();
		
		/*
		runWithProgressDialog("Initialize", pd->{
			String baseURL = getBaseURL();
			if (baseURL==null) return;
			
			boxSettings = BoxSettings.getSettings (baseURL, createProgressTaskFcn(pd, "BoxSettings"));
			systemInfo  = SystemInfo.getSystemInfo(baseURL, createProgressTaskFcn(pd, "SystemInfo"));
			SwingUtilities.invokeLater(systemInfoPanel::update);
			
			movies.readInitialMovieList(baseURL,pd);
			bouquetsNStations.readData(baseURL,pd);
			timers           .readData(baseURL,pd);
			remoteControl .initialize(baseURL,pd,systemInfo);
			screenShot    .initialize(baseURL,pd);
			powerControl  .initialize(baseURL,pd);
			volumeControl .initialize(baseURL,pd);
			messageControl.initialize(baseURL,pd);
		});
		*/
	}
	
	private class InitDialog extends MultiStepProgressDialog {
		private static final long serialVersionUID = -4093930408841333576L;
		
		private String baseURL;
		
		InitDialog(Window parent) {
			super(parent,"Initialize", 400);
			
			baseURL = null;
			addTask("BaseURL", pd->{
				OWCTools.setIndeterminateProgressTask(pd, "Get BaseURL");
				baseURL = getBaseURL();
				return baseURL!=null;
			});
			addTask("Box Settings", pd->{
				if (baseURL==null) return false;
				boxSettings = BoxSettings.getSettings (baseURL, OWCTools.createProgressTaskFcn(pd, "BoxSettings"));
				return true;
			});
			addTask("System Info", pd->{
				if (baseURL==null) return false;
				systemInfo  = SystemInfo.getSystemInfo(baseURL, OWCTools.createProgressTaskFcn(pd, "SystemInfo"));
				SwingUtilities.invokeLater(systemInfoPanel::update);
				return true;
			});
			
			addTask("Movies"               , true, false, pd->{ if (baseURL==null) return false; movies.readInitialMovieList(baseURL,pd);            return true; });
			addTask("Bouquets 'n' Stations", true, false, pd->{ if (baseURL==null) return false; bouquetsNStations .readData(baseURL,pd);            return true; });
			addTask("Timers"               , true, false, pd->{ if (baseURL==null) return false; timers            .readData(baseURL,pd);            return true; });
			addTask("Remote Control"       , true,  true, pd->{ if (baseURL==null) return false; remoteControl   .initialize(baseURL,pd,systemInfo); return true; });
			addTask("ScreenShot"           , true, false, pd->{ if (baseURL==null) return false; screenShot      .initialize(baseURL,pd);            return true; });
			addTask("Power Control"        ,              pd->{ if (baseURL==null) return false; powerControl    .initialize(baseURL,pd);            return true; });
			addTask("Volume Control"       ,              pd->{ if (baseURL==null) return false; volumeControl   .initialize(baseURL,pd);            return true; });
			addTask("Message Control"      ,              pd->{ if (baseURL==null) return false; messageControl  .initialize(baseURL,pd);            return true; });
			
			finishGUI();
		}
	}
	
	private void fillUpdatesMenu(JMenu updatesMenu) {
		updatesMenu.add(OWCTools.createMenuItem("BoxSettings", GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update BoxSettings", (baseURL, pd)->{
			boxSettings = BoxSettings.getSettings (baseURL, OWCTools.createProgressTaskFcn(pd, "BoxSettings"));
		})));
		updatesMenu.add(OWCTools.createMenuItem("SystemInfo", GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update SystemInfo", (baseURL, pd)->{
			systemInfo  = SystemInfo.getSystemInfo(baseURL, OWCTools.createProgressTaskFcn(pd, "SystemInfo"));
			SwingUtilities.invokeLater(systemInfoPanel::update);
		})));
		updatesMenu.add(OWCTools.createMenuItem("Movies"               , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Movies"               , movies::readInitialMovieList)));
		updatesMenu.add(OWCTools.createMenuItem("Bouquets 'n' Stations", GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Bouquets 'n' Stations", bouquetsNStations ::readData)));
		updatesMenu.add(OWCTools.createMenuItem("Timers"               , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Timers"               , timers            ::readData)));
		updatesMenu.add(OWCTools.createMenuItem("Remote Control"       , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Remote Control"       , (baseURL, pd)->{ remoteControl.initialize(baseURL,pd,systemInfo); })));
		updatesMenu.add(OWCTools.createMenuItem("ScreenShot"           , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update ScreenShot"           , screenShot      ::initialize)));
	//	updatesMenu.add(OWCTools.createMenuItem("Power Control"        , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Power Control"        , powerControl    ::initialize)));
	//	updatesMenu.add(OWCTools.createMenuItem("Volume Control"       , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Volume Control"       , volumeControl   ::initialize)));
	//	updatesMenu.add(OWCTools.createMenuItem("Message Control"      , GrayCommandIcons.IconGroup.Reload, e->getBaseURLAndRunWithProgressDialog("Init/Update Message Control"      , messageControl  ::initialize)));
	}
	
	public void getBaseURLAndRunWithProgressDialog(String dlgTitle, BiConsumer<String,ProgressDialog> action) {
		String baseURL = getBaseURL();
		if (baseURL != null)
			runWithProgressDialog(dlgTitle, pd->action.accept(baseURL, pd));
	}
	
	public void runWithProgressDialog(String title, Consumer<ProgressDialog> action) {
		OWCTools.runWithProgressDialog(mainWindow, title, action);
	}

	@Override
	public void showMessageResponse(AbstractMessageResponse response, String title, String... stringsToHighlight) {
		logWindow.showMessageResponse(response, title, stringsToHighlight);
	}

	public interface UpdateTimersTask {
		void updateTimers(String baseURL, ProgressDialog pd);
	}

	@Override
	public void addTimer(String baseURL, String sRef, int eventID, Timers.Timer.Type type)
	{
		addTimer(baseURL, sRef, eventID, type, mainWindow, logWindow, timers::readData);
	}
	public static void addTimer(String baseURL, String sRef, int eventID, Timers.Timer.Type type, Window window, LogWindowInterface lwi, UpdateTimersTask updateTimersTask)
	{
		OWCTools.runWithProgressDialog(window, "Add Timer", pd->{
			String baseURL_ = baseURL;
			if (baseURL_==null) baseURL_ = getBaseURL(true, window);
			if (baseURL_==null) return;
			MessageResponse response = Timers.addTimer(baseURL, sRef, eventID, type, taskTitle->{
				OWCTools.setIndeterminateProgressTask(pd, taskTitle);
			});
			lwi.showMessageResponse(response, "Add Timer");
			if (updateTimersTask!=null)
			{
				OWCTools.setIndeterminateProgressTask(pd, "Update Data");
				updateTimersTask.updateTimers(baseURL_,pd);
			}
		});
	}
	
	@Override
	public void deleteTimer(String baseURL, Timers.Timer timer)
	{
		deleteTimer_single(baseURL, timer, mainWindow, logWindow, null, timers::readData, null);
	}
	public void deleteTimer(String baseURL, Timers.Timer timer, BiConsumer<Timers.Timer, MessageResponse> handleResponse, Runnable updateGUITask)
	{
		deleteTimer_single(baseURL, timer, mainWindow, logWindow, handleResponse, null, updateGUITask);
	}
	public void deleteTimer(String baseURL, Timers.Timer[] timers, BiConsumer<Timers.Timer, MessageResponse> handleResponse, Runnable updateGUITask)
	{
		deleteTimer_local(baseURL, timers, mainWindow, logWindow, handleResponse, null, updateGUITask);
	}
	
	public static void deleteTimer(String baseURL, Timers.Timer timer, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse)
	{
		deleteTimer_single(baseURL, timer, window, lwi, handleResponse, null, null);
	}
	public static void deleteTimer(String baseURL, Timers.Timer[] timers, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse)
	{
		deleteTimer_local(baseURL, timers, window, lwi, handleResponse, null, null);
	}
	
	private static void deleteTimer_single(String baseURL, Timers.Timer timer, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse, UpdateTimersTask updateTimersTask, Runnable updateGUITask)
	{
		deleteTimer_local(baseURL, new Timers.Timer[] { timer }, window, lwi, handleResponse, updateTimersTask, updateGUITask);
	}
	private static void deleteTimer_local(
			String baseURL,
			Timers.Timer[] timers,
			Window window,
			LogWindowInterface lwi,
			BiConsumer<Timers.Timer, MessageResponse> handleResponse,
			UpdateTimersTask updateTimersTask,
			Runnable updateGUITask
	) {
		OWCTools.runWithProgressDialog(window, "Delete Timer", pd->{
			String baseURL_ = baseURL;
			if (baseURL_==null) baseURL_ = getBaseURL(true, window);
			if (baseURL_==null) return;
			
			for (Timers.Timer timer : timers) {
				MessageResponse response = Timers.deleteTimer(baseURL_, timer.serviceref, timer.begin, timer.end, taskTitle->{
					OWCTools.setIndeterminateProgressTask(pd, taskTitle);
				});
				lwi.showMessageResponse(response, "Delete Timer");
				if (handleResponse!=null) handleResponse.accept(timer,response);
			}
			
			if (updateTimersTask!=null)
			{
				OWCTools.setIndeterminateProgressTask(pd, "Update Data");
				updateTimersTask.updateTimers(baseURL_,pd);
			}
			
			if (updateGUITask!=null)
				updateGUITask.run();
		});
	}
	
	@Override
	public void toggleTimer(String baseURL, Timers.Timer timer) {
		toggleTimer_single(baseURL, timer, mainWindow, logWindow, null, timers::readData, null);
	}
	public void toggleTimer(String baseURL, Timers.Timer timer, BiConsumer<Timers.Timer, MessageResponse> handleResponse, Runnable updateGUITask)
	{
		toggleTimer_single(baseURL, timer, mainWindow, logWindow, handleResponse, null, updateGUITask);
	}
	public void toggleTimer(String baseURL, Timers.Timer[] timers, BiConsumer<Timers.Timer, MessageResponse> handleResponse, Runnable updateGUITask)
	{
		toggleTimer_local(baseURL, timers, mainWindow, logWindow, handleResponse, null, updateGUITask);
	}

	public static void toggleTimer(String baseURL, Timers.Timer timer, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse)
	{
		toggleTimer_single(baseURL, timer, window, lwi, handleResponse, null, null);
	}
	public static void toggleTimer(String baseURL, Timers.Timer[] timers, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse)
	{
		toggleTimer_local(baseURL, timers, window, lwi, handleResponse, null, null);
	}
	
	private static void toggleTimer_single(String baseURL, Timers.Timer timer, Window window, LogWindowInterface lwi, BiConsumer<Timers.Timer, MessageResponse> handleResponse, UpdateTimersTask updateTimersTask, Runnable updateGUITask)
	{
		toggleTimer_local(baseURL, new Timers.Timer[] { timer }, window, lwi, handleResponse, updateTimersTask, updateGUITask);
	}
	private static void toggleTimer_local(
			String baseURL,
			Timers.Timer[] timers,
			Window window,
			LogWindowInterface lwi,
			BiConsumer<Timers.Timer, MessageResponse> handleResponse,
			UpdateTimersTask updateTimersTask,
			Runnable updateGUITask
	) {
		OWCTools.runWithProgressDialog(window, "Toggle Timer", pd->{
			String baseURL_ = baseURL;
			if (baseURL_==null) baseURL_ = getBaseURL(true, window);
			if (baseURL_==null) return;
			
			for (Timers.Timer timer : timers) {
				MessageResponse response = Timers.toggleTimer(baseURL_, timer.serviceref, timer.begin, timer.end, taskTitle->{
					OWCTools.setIndeterminateProgressTask(pd, taskTitle);
				});
				lwi.showMessageResponse(response, "Toggle Timer", "disabled", "enabled", "nicht aktiviert");
				if (handleResponse!=null) handleResponse.accept(timer,response);
			}
			
			if (updateTimersTask!=null)
			{
				OWCTools.setIndeterminateProgressTask(pd, "Update Data");
				updateTimersTask.updateTimers(baseURL_,pd);
			}
			
			if (updateGUITask!=null)
				updateGUITask.run();
		});
	}
	
	public void cleanUpTimers()
	{
		cleanUpTimers(null, mainWindow, logWindow, null);
	}
	public void cleanUpTimers(UpdateTimersTask updateTimersTask)
	{
		cleanUpTimers(null, mainWindow, logWindow, updateTimersTask);
	}
	public static void cleanUpTimers(Window window, LogWindowInterface lwi)
	{
		cleanUpTimers(null, window, lwi, null);
	}
	public static void cleanUpTimers(String baseURL, Window window, LogWindowInterface lwi, UpdateTimersTask updateTimersTask)
	{
		OWCTools.runWithProgressDialog(window, "CleanUp Timers", pd->{
			String baseURL_ = baseURL;
			if (baseURL_==null) baseURL_ = getBaseURL(true, window);
			if (baseURL_==null) return;
			MessageResponse response = Timers.cleanup(baseURL_, taskTitle->{
				OWCTools.setIndeterminateProgressTask(pd, taskTitle);
			});
			lwi.showMessageResponse(response, "CleanUp Timer"/* , "disabled", "enabled", "nicht aktiviert" */);
			if (updateTimersTask!=null)
			{
				OWCTools.setIndeterminateProgressTask(pd, "Update Data");
				updateTimersTask.updateTimers(baseURL_,pd);
			}
		});
	}
	
	public void zapToStation(StationID stationID) {
		if (stationID==null) return;
		zapToStation(getBaseURL(), stationID);
	}
	@Override public void zapToStation(String baseURL, StationID stationID) {
		zapToStation(stationID, baseURL, logWindow);
	}
	public static void zapToStation(StationID stationID, String baseURL, LogWindowInterface lwi) {
		if (stationID==null) return;
		if (baseURL==null) return;
		MessageResponse response = OpenWebifTools.zapToStation(baseURL, stationID);
		lwi.showMessageResponse(response, "Zap to Station");
	}

	public void streamStation(StationID stationID) {
		if (stationID==null) return;
		streamStation(getBaseURL(), stationID);
	}
	@Override public void streamStation(String baseURL, StationID stationID) {
		if (stationID==null) return;
		if (baseURL==null) return;
		String url = OpenWebifTools.getStationStreamURL(baseURL, stationID);
		openUrlInVideoPlayer(url, String.format("stream station: %s", stationID.toIDStr()));
	}
	public static void streamStation(StationID stationID, String baseURL) {
		if (stationID==null) return;
		if (baseURL==null) return;
		String url = OpenWebifTools.getStationStreamURL(baseURL, stationID);
		openUrlInVideoPlayer_(url, String.format("stream station: %s", stationID.toIDStr()));
	}
	public static boolean canStreamStation() {
		return hasVideoPlayer() && hasJavaVM();
	}

	public void openEPGDialog()
	{
		openEPGDialog(null);
	}

	public void openEPGDialog(Bouquet bouquet)
	{
		String baseURL = getBaseURL();
		if (baseURL==null) return;
		
		if (!timers.hasData() && !bouquetsNStations.hasData())
		{
			if (!askUserIfDataShouldBeInitialized(mainWindow, "Timer", "Bouquet")) return;
			getBaseURLAndRunWithProgressDialog("Init Timers", timers::readData);
			getBaseURLAndRunWithProgressDialog("Init Bouquets 'n' Stations", bouquetsNStations ::readData);
		}
		else if (!timers.hasData())
		{
			if (!askUserIfDataShouldBeInitialized(mainWindow, "Timer")) return;
			getBaseURLAndRunWithProgressDialog("Init Timers", timers::readData);
		}
		else if (!bouquetsNStations.hasData())
		{
			if (!askUserIfDataShouldBeInitialized(mainWindow, "Bouquet")) return;
			getBaseURLAndRunWithProgressDialog("Init Bouquets 'n' Stations", bouquetsNStations ::readData);
		}
		
		if (bouquet==null) bouquet = bouquetsNStations.showBouquetSelector(mainWindow);
		if (bouquet==null) return;
		
		EPGDialog.showDialog(
				mainWindow, baseURL, epg, bouquet,
				timers.timerDataUpdateNotifier, bouquetsNStations.bouquetsNStationsUpdateNotifier,
				this);
	}
	
	public static void openUrlInVideoPlayer_ (String url, String taskLabel) { openInVideoPlayer(taskLabel, "URL" , url                   , getVideoPlayer_(), getJavaVM_()); }
	public static void openFileInVideoPlayer_(File  file, String taskLabel) { openInVideoPlayer(taskLabel, "File", file.getAbsolutePath(), getVideoPlayer_(), getJavaVM_()); }
	public void openUrlInVideoPlayer (String url, String taskLabel) { openInVideoPlayer(taskLabel, "URL" , url                   ); }
	public void openFileInVideoPlayer(File  file, String taskLabel) { openInVideoPlayer(taskLabel, "File", file.getAbsolutePath()); }

	private void openInVideoPlayer(String taskLabel, String targetLabel, String target) {
		File videoPlayer = getVideoPlayer();
		if (videoPlayer==null) return;
		
		File javaVM = getJavaVM();
		if (javaVM==null) return;
		
		openInVideoPlayer(taskLabel, targetLabel, target, videoPlayer, javaVM);
	}
	private static void openInVideoPlayer(String taskLabel, String targetLabel, String target, File videoPlayer, File javaVM) {
		if (videoPlayer==null) return;
		if (javaVM==null) return;
		
		System.out.printf("%s%n", taskLabel);
		System.out.printf("   Java VM      : \"%s\"%n", javaVM.getAbsolutePath());
		System.out.printf("   Video Player : \"%s\"%n", videoPlayer.getAbsolutePath());
		System.out.printf("   %12s"     +" : \"%s\"%n", targetLabel, target);
		
		try { Runtime.getRuntime().exec(new String[] {javaVM.getAbsolutePath(), "-jar", "OpenWebifController.jar", "-start", videoPlayer.getAbsolutePath(), target }); }
		catch (IOException ex) { System.err.printf("IOException while starting video player: %s%n", ex.getMessage()); }
	}

	@Override
	public String getBaseURL() {
		return getBaseURL(true);
	}
	public String getBaseURL(boolean askUser) {
		return getBaseURL(askUser, mainWindow);
	}
	public static String getBaseURL(boolean askUser, Component parent) {
		if (!settings.contains(AppSettings.ValueKey.BaseURL) && askUser)
			return askUserForBaseURL(parent);
		return getBaseURL_DontAskUser();
	}
	public static String getBaseURL_DontAskUser() {
		return settings.getString(AppSettings.ValueKey.BaseURL, null);
	}

	private String askUserForBaseURL() {
		return askUserForBaseURL(mainWindow);
	}
	private static String askUserForBaseURL(Component parent) {
		String baseURL = JOptionPane.showInputDialog(parent, "Set BaseURL:", "Set BaseURL", JOptionPane.QUESTION_MESSAGE);
		if (baseURL!=null)
			settings.putString(AppSettings.ValueKey.BaseURL, baseURL);
		return baseURL;
	}

	public static boolean askUserIfDataShouldBeInitialized(Component parent, String... dataLabels)
	{
		if (dataLabels==null || dataLabels.length<1)
			throw new IllegalArgumentException();
		
		Iterable<String> it;
		it = ()->Arrays.stream(dataLabels).map(str->String.format("No %s Data", str)).iterator();
		String title = String.join(" & ", it);
		
		String[] msg = new String[] {
			String.format("Currently there are %s loaded.", title.toLowerCase()),
			"Do you want to initialize that?"
		};
		
		String dataLabel_ = dataLabels.length>1 ? "" : dataLabels[0]+" ";
		String[] opts = new String[] { String.format("Initialize %sData", dataLabel_), "Cancel" };
		
		return JOptionPane.showOptionDialog(
			parent, msg, title,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, opts, opts[0]
		) == JOptionPane.OK_OPTION;
	}
	
	static boolean hasJavaVM     () { return hasExecutable(AppSettings.ValueKey.JavaVM     ); }
	static boolean hasVideoPlayer() { return hasExecutable(AppSettings.ValueKey.VideoPlayer); }
	static boolean hasBrowser    () { return hasExecutable(AppSettings.ValueKey.Browser    ); }
	static File getJavaVM_     () { return getExecutable(AppSettings.ValueKey.JavaVM     ); }
	static File getVideoPlayer_() { return getExecutable(AppSettings.ValueKey.VideoPlayer); }
	static File getBrowser_    () { return getExecutable(AppSettings.ValueKey.Browser    ); }
	File getJavaVM     () { return getExecutable(AppSettings.ValueKey.JavaVM     , "Select Java VM"    ); }
	File getVideoPlayer() { return getExecutable(AppSettings.ValueKey.VideoPlayer, "Select VideoPlayer"); }
	File getBrowser    () { return getExecutable(AppSettings.ValueKey.Browser    , "Select Browser"    ); }
	private File askUserForJavaVM     () { return askUserForExecutable(AppSettings.ValueKey.JavaVM     , "Select Java VM"    ); }
	private File askUserForVideoPlayer() { return askUserForExecutable(AppSettings.ValueKey.VideoPlayer, "Select VideoPlayer"); }
	private File askUserForBrowser    () { return askUserForExecutable(AppSettings.ValueKey.Browser    , "Select Browser"    ); }

	private static boolean hasExecutable(AppSettings.ValueKey valueKey) {
		if (!settings.contains(valueKey)) return false;
		File executable = getExecutable(valueKey);
		return executable.isFile();
	}
	
	private File getExecutable(AppSettings.ValueKey valueKey, String dialogTitle) {
		if (!settings.contains(valueKey))
			return askUserForExecutable(valueKey, dialogTitle);
		return getExecutable(valueKey);
	}
	static File getExecutable(AppSettings.ValueKey valueKey) {
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
	
	private class SystemInfoPanel extends JSplitPane {
		private static final long serialVersionUID = -8703742093758822234L;
		
		private final ExtendedTextArea leftTextView;
		private final ExtendedTextArea rightTextView;

		SystemInfoPanel() {
			super(HORIZONTAL_SPLIT,true);
			
			JPanel  leftPanel = new JPanel(new BorderLayout());
			JPanel rightPanel = new JPanel(new BorderLayout());
			leftPanel .setBorder(BorderFactory.createTitledBorder("System Info"));
			rightPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
			
			leftTextView  = new ExtendedTextArea(false);
			rightTextView = new ExtendedTextArea(false);
			leftPanel .add( leftTextView.createScrollPane(500,500),BorderLayout.CENTER);
			rightPanel.add(rightTextView.createScrollPane(500,500),BorderLayout.CENTER);
			
			setLeftComponent(leftPanel);
			setRightComponent(rightPanel);
			setResizeWeight(0.5);
		}
		
		public void update() {
			fillLeftTextView();
			fillRightTextView();
		}

		private void fillLeftTextView() {
			ValueListOutput out = new ValueListOutput();
			
			if (systemInfo!=null && systemInfo.info!=null) {
				out.add(0, "Firmware");
				/* String  */ out.add(1, "Image"              , systemInfo.info.friendlyimagedistro);
				/* String  */ out.add(1, null                 , systemInfo.info.imagedistro        );
				/* String  */ out.add(1, "Image Version"      , systemInfo.info.imagever           );
				/* String  */ out.add(1, "OE Version"         , systemInfo.info.oever              );
				/* String  */ out.add(1, "Enigma Version"     , systemInfo.info.enigmaver          );
				/* String  */ out.add(1, "OpenWebif Version"  , systemInfo.info.webifver           );
				/* String  */ out.add(1, "Kernel Verion"      , systemInfo.info.kernelver          );
				/* String  */ out.add(1, "Driver Date"        , systemInfo.info.driverdate         );
				
				out.add(0, "");
				out.add(0, "Hardware");
				/* String  */ out.add(1, "Brand"        , systemInfo.info.brand       );
				/* String  */ out.add(1, "Model"        , systemInfo.info.model       );
				/* String  */ out.add(1, "Box Type"     , systemInfo.info.boxtype     );
				/* String  */ out.add(1, "Machine Build", systemInfo.info.machinebuild);
				
				/* String  */ out.add(1, "Chipset"       , systemInfo.info.friendlychipsettext       );
				/* String  */ out.add(1, null            , systemInfo.info.chipset                   );
				/* String  */ out.add(1, "Chipset Descr.", systemInfo.info.friendlychipsetdescription);
				
				/* String  */ out.add(1, "mem1", systemInfo.info.mem1);
				/* String  */ out.add(1, "mem2", systemInfo.info.mem2);
				/* String  */ out.add(1, "mem3", systemInfo.info.mem3);
				
				out.add(0, "");
				out.add(0, "Miscellaneous");
				/* String  */ out.add(1, "Uptime"         , systemInfo.info.uptime                    );
				/* long    */ out.add(1, "lcd"            , systemInfo.info.lcd                       );
				/* long    */ out.add(1, "grabpip"        , systemInfo.info.grabpip                   );
				/* boolean */ out.add(1, "timerautoadjust", systemInfo.info.timerautoadjust           );
				/* boolean */ out.add(1, "timerpipzap"    , systemInfo.info.timerpipzap               );
				/* boolean */ out.add(1, "transcoding"    , systemInfo.info.transcoding               );
				/* String  */ out.add(1, "EX"             , systemInfo.info.EX);
				
				/* Null  */ //out.add(0, "label", systemInfo.info.fp_version);
				
				if (!systemInfo.info.streams.isEmpty()) {
					out.add(0, "");
					out.add(0, "Streams");
					for (int i=0; i<systemInfo.info.streams.size(); i++) {
						SystemInfo.Stream stream = systemInfo.info.streams.get(i);
						out.add(1, "Stream "+(i+1));
						/* String */ out.add(2, "Client IP"   , stream.ip       );
						/* String */ out.add(2, "Station Name", stream.name     );
						/* String */ out.add(2, "Station Ref" , stream.ref      );
						/* String */ out.add(2, "Type"        , stream.type     );
						/* String */ out.add(2, "Event Name"  , stream.eventname);
					}
				}
				
				if (!systemInfo.info.hdd.isEmpty()) {
					out.add(0, "");
					out.add(0, "HDDs");
					for (int i=0; i<systemInfo.info.hdd.size(); i++) {
						SystemInfo.Hdd hdd = systemInfo.info.hdd.get(i);
						out.add(1, "HDD "+(i+1));
						/* String */ out.add(2, "Mount"              , hdd.mount            );
						/* String */ out.add(2, "Model"              , hdd.model            );
						/* String */ out.add(2, "Capacity"           , hdd.friendlycapacity );
						/* String */ out.add(3, "[capacity]"         , hdd.capacity         );
						/* String */ out.add(3, "[labelled capacity]", hdd.labelled_capacity);
						/* String */ out.add(3, "[free]"             , hdd.free             );
					}
				}
				
				if (!systemInfo.info.tuners.isEmpty()) {
					out.add(0, "");
					out.add(0, "Tuners");
					for (int i=0; i<systemInfo.info.tuners.size(); i++) {
						SystemInfo.Tuner tuner = systemInfo.info.tuners.get(i);
						out.add(1, "Tuner "+(i+1));
						/* String */ out.add(2, "Name"     , tuner.name);
						/* String */ out.add(2, "Type"     , tuner.type);
						/* String */ out.add(2, "Live"     , tuner.live);
						/* String */ out.add(2, "Recording", tuner.rec );
					}
				}
				
				if (!systemInfo.info.ifaces.isEmpty()) {
					out.add(0, "");
					out.add(0, "Interfaces");
					for (int i=0; i<systemInfo.info.ifaces.size(); i++) {
						SystemInfo.Interface iface = systemInfo.info.ifaces.get(i);
						out.add(1, "Interface "+(i+1));
						/* String  */ out.add(2, "Name"       , iface.name       );
						/* String  */ out.add(2, "NIC"        , iface.friendlynic); 
						/* String  */ out.add(2, "Link Speed" , iface.linkspeed  ); 
						/* String  */ out.add(2, "MAC"        , iface.mac        ); 
						/* boolean */ out.add(2, "DHCP"       , iface.dhcp       ); 
						/* String  */ out.add(2, "IP"         , iface.ip         ); 
						/* String  */ out.add(2, "Gateway"    , iface.gw         ); 
						/* String  */ out.add(2, "Mask"       , iface.mask       ); 
						/* long    */ out.add(2, "V4 Prefix"  , iface.v4prefix   );
						/* String  */ out.add(2, "IPV4 Method", iface.ipv4method ); 
						/* String  */ out.add(2, "IP Method"  , iface.ipmethod   ); 
						/* String  */ out.add(2, "IPV6"       , iface.ipv6       ); 
						/* String  */ out.add(2, "firstpublic", iface.firstpublic); 
					}
				}
			}
			
			leftTextView.setText(out.generateOutput());
		}

		private void fillRightTextView() {
			ValueListOutput out = new ValueListOutput();
			
			if (boxSettings!=null) {
				Vector<String> keys = new Vector<>(boxSettings.keySet());
				keys.sort(null);
				for (String key:keys) {
					BoxSettingsValue settingsValue = boxSettings.get(key);
					if (settingsValue==null)
						out.add(0, key, "%s", "<null>");
					else
						out.add(0, key, "%s", settingsValue.getValueStr());
				}
			}
			
			rightTextView.setText(out.generateOutput());
		}
		
	}
	
	public static class ExtendedTextArea extends JTextArea {
		private static final long serialVersionUID = 147034518545703683L;
		
		public ExtendedTextArea() {}
		public ExtendedTextArea(boolean isEditable) {
			setEditable(isEditable);
		}

		public JScrollPane createScrollPane(int width, int height) {
			JScrollPane textViewScrollPane = new JScrollPane(this);
			textViewScrollPane.setPreferredSize(new Dimension(width,height));
			return textViewScrollPane;
		}

		public ContextMenu createContextMenu(AppSettings.ValueKey linewrapValueKey) {
			boolean textViewLineWrap = settings.getBool(linewrapValueKey, false);
			return createContextMenu(textViewLineWrap, isChecked -> settings.putBool(linewrapValueKey, isChecked));
		}

		public ContextMenu createContextMenu(boolean activateLineWrap, Consumer<Boolean> setLineWrap) {
			ContextMenu contextMenu = new ContextMenu();
			contextMenu.addTo(this);
			
			setLineWrap(activateLineWrap);
			setWrapStyleWord(activateLineWrap);
			contextMenu.add(OWCTools.createCheckBoxMenuItem("Line Wrap", activateLineWrap, isChecked->{
				setLineWrap(isChecked);
				setWrapStyleWord(isChecked);
				if (setLineWrap!=null) setLineWrap.accept(isChecked);
			}) );
			
			return contextMenu;
		}
		
	}
}
