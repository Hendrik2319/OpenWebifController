package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.util.Objects;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.EPG;
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
		button = OpenWebifController.createButton("Load EPG", true, e->startStopThread());
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

	private void loadEPG(String baseURL) {
		
		synchronized (this) {
			isRunning = true;
			button.setText("Cancel EPG Loading");
			button.setEnabled(true);
		}
		
		long now_ms = System.currentTimeMillis();
		
		if (stations!=null) scanEPGbyStations(baseURL, now_ms);
		if (bouquet !=null) scanEPGbyTimeBlocks(baseURL, now_ms);
		
		
		synchronized (this) {
			isRunning = false;
			button.setText("Load EPG");
			button.setEnabled(true);
		}
	}
	
	private void scanEPGbyTimeBlocks(String baseURL, long now_ms)
	{
		int blockSize_mins = 400;
		int overlap_mins = 20;
		
		System.out.printf("Scan EPG for Bouquet \"%s\": %d min - %d min%n", bouquet.name, -leadTime_s/60, rangeTime_s/60);
		for (int blockStart_s = -leadTime_s; blockStart_s < rangeTime_s; blockStart_s += (blockSize_mins-overlap_mins)*60)
		{
			int blockStart_mins = blockStart_s/60;
			int blockEnd_mins = blockStart_mins + blockSize_mins;
			
			boolean isInterrupted = Thread.currentThread().isInterrupted();
			System.out.printf("EPG for Bouquet \"%s\" (%d min - %d min)%s%n", bouquet.name, blockStart_mins, blockEnd_mins, isInterrupted ? " -> omitted" : "");
			if (isInterrupted) continue;
			
			long beginTime_UnixTS = now_ms/1000 + blockStart_s;
			long endTime_Minutes  = blockSize_mins;
			epg.readEPGforBouquet(baseURL, bouquet, beginTime_UnixTS, endTime_Minutes, taskTitle->{
				SwingUtilities.invokeLater(()->{
					setStatusOutput(String.format("EPG for Bouquet \"%s\" (%d min - %d min): %s", bouquet.name, blockStart_mins, blockEnd_mins, taskTitle));
				});
			});
			updateEPGView();
			SwingUtilities.invokeLater(this::reconfigureHorizScrollBar);
		}
		System.out.println("... done");
		SwingUtilities.invokeLater(()->{
			setStatusOutput("");
		});
	}
	
	private void scanEPGbyStations(String baseURL, long now_ms)
	{
		for (SubService subservice:stations)
			if (!subservice.isMarker()) {
				boolean isInterrupted = Thread.currentThread().isInterrupted();
				System.out.printf("EPG for Station \"%s\"%s%n", subservice.name, isInterrupted ? " -> omitted" : "");
				if (isInterrupted) continue;
				long beginTime_UnixTS = now_ms/1000 - leadTime_s;
				epg.readEPGforService(baseURL, subservice.service.stationID, beginTime_UnixTS, null, taskTitle->{
					SwingUtilities.invokeLater(()->{
						setStatusOutput(String.format("EPG for Station \"%s\": %s", subservice.name, taskTitle));
					});
				});
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
		if (isRunning()) stop(); else start();
	}

	synchronized void start() {
		if (!isRunning()) {
			button.setEnabled(false);
			thread = new Thread(()->loadEPG(baseURL));
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
	
}