package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.util.Objects;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

abstract class LoadEPGThread {
	private final String baseURL;
	private final EPG epg;
	private final Vector<SubService> stations;
	private final Bouquet bouquet;
	private final JButton button;

	private Thread thread;
	private boolean isRunning;
	private int leadTime_s;
	private int rangeTime_s;
	private long focusTime_ms;
	private Runnable taskAtEnd;
	
	LoadEPGThread(String baseURL, EPG epg, Bouquet bouquet) {
		this(baseURL, epg, Objects.requireNonNull(bouquet), null);
	}
	LoadEPGThread(String baseURL, EPG epg, Vector<SubService> stations) {
		this(baseURL, epg, null, Objects.requireNonNull(stations));
	}
	private LoadEPGThread(String baseURL, EPG epg, Bouquet bouquet, Vector<SubService> stations) {
		this.baseURL = Objects.requireNonNull(baseURL);
		this.epg = Objects.requireNonNull(epg);
		this.bouquet = bouquet;
		this.stations = stations;
		isRunning = false;
		thread = null;
		taskAtEnd = null;
		button = OpenWebifController.createButton("Load EPG", true, e->startStopThread());
		focusTime_ms = System.currentTimeMillis(); 
	}
	
	protected abstract void setStatusOutput(String text);
	protected abstract void updateEPGView();
	protected abstract void reconfigureHorizScrollBar();
	
	void setLeadTime(int leadTime_s) {
		this.leadTime_s = leadTime_s;
	}

	void setRangeTime(int rangeTime_s) {
		this.rangeTime_s = rangeTime_s;
	}
	
	JButton getButton() {
		return button;
	}

	private void loadEPG(long focusTime_ms)
	{
		synchronized (this) {
			isRunning = true;
			button.setText("Cancel EPG Loading");
			button.setEnabled(true);
		}
		
		if (stations!=null) scanEPGbyStations  (focusTime_ms);
		if (bouquet !=null) scanEPGbyTimeBlocks(focusTime_ms);
		
		synchronized (this) {
			isRunning = false;
			button.setText("Load EPG");
			button.setEnabled(true);
		}
	}
	
	private void scanEPGbyTimeBlocks(long focusTime_ms)
	{
		int blockSize_mins = 400;
		int overlap_mins = 20;
		
		System.out.printf("Scan EPG for Bouquet \"%s\": %d min - %d min%n", bouquet.name, -leadTime_s/60, rangeTime_s/60);
		for (int blockStart_s = -leadTime_s; blockStart_s < rangeTime_s; blockStart_s += (blockSize_mins-overlap_mins)*60)
		{
			int blockStart_mins = blockStart_s/60;
			int blockEnd_mins = blockStart_mins + blockSize_mins;
			long beginTime_UnixTS = focusTime_ms/1000 + blockStart_s;
			long endTime_Minutes  = blockSize_mins;
			
			boolean isInterrupted = Thread.currentThread().isInterrupted();
			System.out.printf("EPG for Bouquet \"%s\" (%5d min - %5d min) [%s - %s] %s%n",
					bouquet.name,
					blockStart_mins, blockEnd_mins,
					OpenWebifController.dateTimeFormatter.getTimeStr(beginTime_UnixTS*1000, true, true, false, true, false),
					OpenWebifController.dateTimeFormatter.getTimeStr((beginTime_UnixTS+endTime_Minutes*60)*1000, false, false, false, true, false),
					isInterrupted ? " -> omitted" : ""
			);
			if (isInterrupted) continue;
			
			Vector<EPGevent> events = epg.readEPGforBouquet(baseURL, bouquet, beginTime_UnixTS, endTime_Minutes, taskTitle->{
				SwingUtilities.invokeLater(()->{
					setStatusOutput(String.format("EPG for Bouquet \"%s\" (%d min - %d min): %s", bouquet.name, blockStart_mins, blockEnd_mins, taskTitle));
				});
			});
			//System.out.printf("%d events found%n", events.size());
			EPGEventGenres.getInstance().scanGenres(events).writeToFile();
			updateEPGView();
			SwingUtilities.invokeLater(this::reconfigureHorizScrollBar);
		}
		System.out.println("... done");
		SwingUtilities.invokeLater(()->{
			setStatusOutput("");
		});
	}
	
	private void scanEPGbyStations(long focusTime_ms)
	{
		for (SubService subservice:stations)
			if (!subservice.isMarker()) {
				boolean isInterrupted = Thread.currentThread().isInterrupted();
				System.out.printf("EPG for Station \"%s\"%s%n", subservice.name, isInterrupted ? " -> omitted" : "");
				if (isInterrupted) continue;
				long beginTime_UnixTS = focusTime_ms/1000 - leadTime_s;
				Vector<EPGevent> events = epg.readEPGforService(baseURL, subservice.service.stationID, beginTime_UnixTS, null, taskTitle->{
					SwingUtilities.invokeLater(()->{
						setStatusOutput(String.format("EPG for Station \"%s\": %s", subservice.name, taskTitle));
					});
				});
				EPGEventGenres.getInstance().scanGenres(events).writeToFile();
				updateEPGView();
				SwingUtilities.invokeLater(this::reconfigureHorizScrollBar);
			}
		System.out.println("... done");
		SwingUtilities.invokeLater(()->{
			setStatusOutput("");
		});
	}

	synchronized boolean isRunning() {
		return isRunning;
	}
	
	private synchronized void startStopThread() {
		if (isRunning()) stop(); else start(focusTime_ms);
	}

	synchronized void start(long focusTime_ms) {
		this.focusTime_ms = focusTime_ms;
		if (!isRunning()) {
			button.setEnabled(false);
			thread = new Thread(()->{
				loadEPG(focusTime_ms);
				if (taskAtEnd!=null)
				{
					taskAtEnd.run();
					taskAtEnd = null;
				}
			});
			thread.start();
		}
	}
	synchronized void stop() {
		if (isRunning()) {
			if (thread!=null) {
				thread.interrupt();
				button.setEnabled(false);
			}
			thread = null;
		}
	}
	
	synchronized void execWhenEnded(Runnable taskAtEnd)
	{
		if (isRunning())
			this.taskAtEnd = taskAtEnd;
		else
		{
			this.taskAtEnd = null;
			taskAtEnd.run();
		}
	}
	
}