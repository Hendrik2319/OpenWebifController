package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.ProgressView;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.CommandIcons;

class ScreenShot extends JPanel {
	private static final long serialVersionUID = 4685951556648091350L;
	
	private final RemoteControlPanel remoteControl;
	private final ImageView screenshotView;
	private final ScreenShotUpdater screenShotUpdater;
	private final JButton updateButton;
	
	private OpenWebifTools.ScreenShotType content;
	private OpenWebifTools.ScreenShotResolution resolution;

	public ScreenShot(OpenWebifController main, RemoteControlPanel remoteControl) {
		super(new BorderLayout(3,3));
		this.remoteControl = remoteControl;
		this.remoteControl.addKeyPressListener(this::updateScreenShot);
		setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		
		// initial values
		content = OpenWebifTools.ScreenShotType.TVnOSD;
		resolution = OpenWebifTools.ScreenShotResolution.HighRes;
		int updateInterval_s = 30;
		
		screenshotView = new ImageView(800,600,ImageView.InterpolationLevel.Level2_Better);
		screenshotView.reset();
		
		screenShotUpdater = new ScreenShotUpdater(updateInterval_s, ()->{
			String baseURL = main.getBaseURL();
			if (baseURL==null) return;
			updateScreenShot(baseURL);
		});
		
		GridBagConstraints c;
		JPanel updatePanel = new JPanel(new GridBagLayout());
		updatePanel.setBorder(BorderFactory.createTitledBorder("Update"));
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		
		c.gridwidth = GridBagConstraints.REMAINDER;
		updatePanel.add(OpenWebifController.createCheckBox("Automatic Update", false, b->{
			if (b) screenShotUpdater.start();
			else   screenShotUpdater.stop();
		}),c);
		
		c.gridwidth = 1;
		c.weightx = 0;
		updatePanel.add(new JLabel("Interval (s): "),c);
		c.weightx = 1;
		c.gridwidth = GridBagConstraints.REMAINDER;
		updatePanel.add(OpenWebifController.createTextField(Integer.toString(updateInterval_s), 6, OpenWebifController::parseInt, n->n>0, screenShotUpdater::changeInterval),c);
		
		updatePanel.add(updateButton = OpenWebifController.createButton("Update", true, e->screenShotUpdater.runOnce()),c);
		
		JPanel configPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1;
		
		ButtonGroup bgContent = new ButtonGroup();
		configPanel.add(createGridPanel("Content",
				OpenWebifController.createRadioButton("TV & OSD", bgContent, content == OpenWebifTools.ScreenShotType.TVnOSD , b->{ content = OpenWebifTools.ScreenShotType.TVnOSD;  updateScreenShot(); }),
				OpenWebifController.createRadioButton("TV only" , bgContent, content == OpenWebifTools.ScreenShotType.TVonly , b->{ content = OpenWebifTools.ScreenShotType.TVonly;  updateScreenShot(); }),
				OpenWebifController.createRadioButton("OSD only", bgContent, content == OpenWebifTools.ScreenShotType.OSDonly, b->{ content = OpenWebifTools.ScreenShotType.OSDonly; updateScreenShot(); })
		),c);
		
		ButtonGroup bgResolution = new ButtonGroup();
		configPanel.add(createGridPanel("Resolution",
				OpenWebifController.createRadioButton("720 Rows" , bgResolution, resolution == OpenWebifTools.ScreenShotResolution.R720   , b->{ resolution = OpenWebifTools.ScreenShotResolution.R720   ;  updateScreenShot(); }),
				OpenWebifController.createRadioButton("High-Res" , bgResolution, resolution == OpenWebifTools.ScreenShotResolution.HighRes, b->{ resolution = OpenWebifTools.ScreenShotResolution.HighRes;  updateScreenShot(); })
		),c);
		
		configPanel.add(updatePanel,c);
		
		JPanel remoteControlPanel = new JPanel(new BorderLayout());
		remoteControlPanel.setBorder(BorderFactory.createTitledBorder("Remote Control"));
		remoteControlPanel.add(this.remoteControl.createScrollPane(),BorderLayout.CENTER);
		
		JPanel leftPanel = new JPanel(new BorderLayout(3,3));
		leftPanel.add(configPanel,BorderLayout.NORTH);
		leftPanel.add(remoteControlPanel,BorderLayout.CENTER);
		
		add(leftPanel,BorderLayout.WEST);
		add(screenshotView,BorderLayout.CENTER);
	}

	private JPanel createGridPanel(String title, Component... comps) {
		JPanel panel = new JPanel(new GridLayout(0,1,3,3));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		for (Component comp:comps) panel.add(comp);
		return panel;
	}

	public void initialize(String baseURL, ProgressView pd) {
		OpenWebifController.setIndeterminateProgressTask(pd, "ScreenShot: Read Image");
		updateScreenShot(baseURL);
		SwingUtilities.invokeLater(this::revalidate);
	}

	private void updateScreenShot(String baseURL) {
		SwingUtilities.invokeLater(()->{
			updateButton.setIcon(CommandIcons.LED_yellow.getIcon());
		});
		
		BufferedImage image = OpenWebifTools.getScreenShot(baseURL, content, resolution);
		
		SwingUtilities.invokeLater(()->{
			screenshotView.setImage(image);
			updateButton.setIcon(CommandIcons.LED_green.getIcon());
		});
	}

	private void updateScreenShot() {
		screenShotUpdater.runOnce();
	}
	
	private static class ScreenShotUpdater {
		
		private int interval_sec;
		private ScheduledExecutorService scheduler;
		private final Runnable task;
		private ScheduledFuture<?> taskHandle;
		private boolean taskIsRunning;

		ScreenShotUpdater(int interval_sec, Runnable task) {
			this.interval_sec = interval_sec;
			this.task = ()->{
				taskIsRunning = true;
				task.run();
				taskIsRunning = false;
			};
			scheduler = Executors.newSingleThreadScheduledExecutor();
			taskIsRunning = false;
		}
		
		synchronized void runOnce() {
			if (taskHandle == null)
				scheduler.execute(task);
			
			else if (!taskIsRunning) {
				stop();
				start();
			}
		}

		synchronized void changeInterval(int interval_sec) {
			this.interval_sec = interval_sec;
			if (taskHandle != null) {
				stop();
				start();
			}
		}
		
		synchronized void start() {
			if (taskHandle != null) stop();
			taskHandle = scheduler.scheduleWithFixedDelay(task, 0, interval_sec, TimeUnit.SECONDS);
		}

		synchronized void stop() {
			if (taskHandle == null) return;
			taskHandle.cancel(false);
			taskHandle = null;
		}
	}
	
}
