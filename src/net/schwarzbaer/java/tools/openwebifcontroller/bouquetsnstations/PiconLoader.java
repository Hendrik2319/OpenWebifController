package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Vector;

import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.ListenerController;

public class PiconLoader implements ListenerController.ListenerUser<PiconLoader.Listener>
{
	interface Listener
	{
		void updatePicon(StationID stationID, BufferedImage piconImage);
		void showMessage(String msg, int duration_ms);
	}
	
	private static PiconLoader instance = null; 
	public static PiconLoader getInstance()
	{
		return instance != null ? instance : (instance = new PiconLoader());
	}

	private final Vector<Listener> listeners = new Vector<>(); 
	private final ArrayDeque<StationID> unsolvedIDs = new ArrayDeque<>();
	private final PiconCache piconCache = new PiconCache();
	private Thread taskThread = null;
	private String baseURL = null;
	
	private PiconLoader() {}
	
	@Override public void    addListener(Listener l) { listeners.   add(l); }
	@Override public void removeListener(Listener l) { listeners.remove(l); }

	BufferedImage getPicon(StationID stationID)
	{
		return piconCache.get(stationID);
	}

	private synchronized void startTasks() {
		if (taskThread!=null)
			return;
		taskThread = new Thread(()->{
			//System.out.println("PiconLoader.start");
			while (performTask());
			//System.out.println("PiconLoader.end");
			showMessage(" ", 100);
		});
		taskThread.start();
	}

	private synchronized void updateStatus() {
		String msg = String.format("Picon Loader : %d pending tasks, %d picons cached", unsolvedIDs.size(), piconCache.size());
		showMessage(msg, 2000);
	}

	private void showMessage(String msg, int duration_ms)
	{
		listeners.forEach(l->l.showMessage(msg, duration_ms));
	}

	private void updatePicon(StationID stationID, BufferedImage piconImage)
	{
		listeners.forEach(l->l.updatePicon(stationID, piconImage));
	}

	private boolean performTask() {
		updateStatus();
		
		StationID stationID = null;
		String localBaseURL = null;
		synchronized (this) {
			if (baseURL!=null && !unsolvedIDs.isEmpty()) {
				stationID = unsolvedIDs.pollFirst();
				localBaseURL = baseURL;
			}
		}
		
		if (stationID!=null) {
			BufferedImage piconImage;
			if (localBaseURL!=null && !piconCache.contains(stationID))
				piconCache.put(stationID, piconImage = OpenWebifTools.getPicon(localBaseURL, stationID));
			else
				piconImage = piconCache.get(stationID);
			if (piconImage!=null)
				updatePicon(stationID, piconImage);
			return true;
		}
		
		boolean isEverythingDone = false;
		synchronized (this) {
			isEverythingDone = unsolvedIDs.isEmpty();
			if (isEverythingDone)
				taskThread = null;
		}
		
		return !isEverythingDone;
	}

	synchronized void clearPiconCache() {
		piconCache.clear();
	}

	synchronized void setBaseURL(String baseURL) {
		this.baseURL = baseURL;
	}

	synchronized void addTask(StationID stationID) {
		unsolvedIDs.add(stationID);
		startTasks();
	}
	
	private static class PiconCache
	{
		private final HashMap<String,BufferedImage> cache = new HashMap<>();
		
		synchronized int size() {
			return cache.size();
		}
		synchronized void clear() {
			cache.clear();
		}
		synchronized BufferedImage get(StationID stationID) {
			return cache.get(stationID.toIDStr());
		}
		synchronized boolean contains(StationID stationID) {
			return cache.containsKey(stationID.toIDStr());
		}
		synchronized void put(StationID stationID, BufferedImage piconImage) {
			cache.put(stationID.toIDStr(), piconImage);
		}
	}
}