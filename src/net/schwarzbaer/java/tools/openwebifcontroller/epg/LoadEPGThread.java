package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.util.Vector;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

abstract class LoadEPGThread {
	private final String baseURL;
	private final EPG epg;
	private final Vector<SubService> stations;
	private final JButton button;

	private Thread thread;
	private boolean isRunning;
	private int leadTime_s;
	
	LoadEPGThread(String baseURL, EPG epg, Vector<SubService> stations) {
		this.baseURL = baseURL;
		this.epg = epg;
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
		
		
		synchronized (this) {
			isRunning = false;
			button.setText("Load EPG");
			button.setEnabled(true);
		}
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