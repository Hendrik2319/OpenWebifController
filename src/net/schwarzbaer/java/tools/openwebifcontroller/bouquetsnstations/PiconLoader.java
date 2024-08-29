package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.tree.DefaultTreeModel;

import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations.StatusOut;

class PiconLoader {

	private final ArrayDeque<Task> solvedTasks = new ArrayDeque<>();
	private final ArrayDeque<Task> tasks = new ArrayDeque<>();
	private final PiconCache piconCache = new PiconCache();
	private DefaultTreeModel treeModel = null;
	private BSTreeNode.RootNode rootNode = null;
	private Thread taskThread = null;
	private String baseURL = null;
	private StatusOut statusOut;

	private synchronized void startTasks() {
		if (taskThread!=null)
			return;
		taskThread = new Thread(()->{
			//System.out.println("PiconLoader.start");
			while (performTask());
			//System.out.println("PiconLoader.end");
			//if (statusOut!=null) statusOut.clear();;
		});
		taskThread.start();
	}

	private synchronized void updateStatus() {
		String msg = String.format("Picon Loader : %d pending tasks, %d tree nodes to update, %d picons cached", tasks.size(), solvedTasks.size(), piconCache.size());
		if (statusOut!=null) statusOut.showMessage(msg, 2000);
	}

	private boolean performTask() {
		updateStatus();
		
		Task task = null;
		DefaultTreeModel localTreeModel = null;
		BSTreeNode.RootNode localRootNode = null;
		synchronized (this) {
			if (treeModel!=null && rootNode!=null && !solvedTasks.isEmpty()) {
				task = solvedTasks.pollFirst();
				localTreeModel = treeModel;
				localRootNode  = rootNode;
			}
		}
		if (task!=null) {
			task.updateTreeNodes(localTreeModel, localRootNode, piconCache);
			return true;
		}
		
		String localBaseURL = null;
		synchronized (this) {
			if (baseURL!=null && !tasks.isEmpty()) {
				task = tasks.pollFirst();
				localBaseURL = baseURL;
			}
			
		}
		if (task!=null) {
			task.readImage(localBaseURL, piconCache);
			synchronized (this) {
				localTreeModel = treeModel;
				localRootNode  = rootNode;
			}
			
			if (localTreeModel!=null)
				task.updateTreeNodes(localTreeModel, localRootNode, piconCache);
			else
				synchronized (this) { solvedTasks.addLast(task); }
			return true;
		}
		
		boolean isEverythingDone = false;
		synchronized (this) {
			isEverythingDone = solvedTasks.isEmpty() && tasks.isEmpty();
			if (isEverythingDone)
				taskThread = null;
		}
		
		return !isEverythingDone;
	}

	synchronized void clear() {
		treeModel = null;
		rootNode = null;
		baseURL = null;
		tasks.clear();
		solvedTasks.clear();
		clearPiconCache();
	}

	synchronized void clearPiconCache() {
		piconCache.clear();
	}

	synchronized void setStatusOutput(StatusOut statusOut) {
		this.statusOut = statusOut;
	}

	synchronized void setBaseURL(String baseURL) {
		this.baseURL = baseURL;
	}

	synchronized void addTask(StationID stationID) {
		tasks.add(new Task(stationID));
		startTasks();
	}

	synchronized void setTreeModel(DefaultTreeModel treeModel, BSTreeNode.RootNode rootNode) {
		this.treeModel = treeModel;
		this.rootNode = rootNode;
		startTasks();
	}
	
	private static class PiconCache {
		
		private final HashMap<String,CachedPicon> cache;
		
		PiconCache() {
			cache = new HashMap<>();
		}
		synchronized int size() {
			return cache.size();
		}
		synchronized void clear() {
			cache.clear();
		}
		synchronized CachedPicon get(StationID stationID) {
			return cache.get(stationID.toIDStr());
		}
		synchronized boolean contains(StationID stationID) {
			return cache.containsKey(stationID.toIDStr());
		}
		synchronized void put(StationID stationID, BufferedImage piconImage, Icon icon) {
			cache.put(stationID.toIDStr(), new CachedPicon(piconImage, icon));
		}
		
		private static class CachedPicon {
			final BufferedImage piconImage;
			final Icon icon;
			CachedPicon(BufferedImage piconImage, Icon icon) {
				this.piconImage = piconImage;
				this.icon = icon;
			}
			@SuppressWarnings("unused")
			boolean isEmpty() {
				return piconImage==null && icon==null;
			}
		}
	}
	
	private static class Task {
		private final StationID stationID;
		
		Task(StationID stationID) {
			this.stationID = stationID;
		}
		
		void readImage(String baseURL, PiconCache piconCache) {
			if (baseURL==null || stationID==null) return;
			if (!piconCache.contains(stationID)) {
				BufferedImage piconImage = OpenWebifTools.getPicon(baseURL, stationID);
				Icon icon = BSTreeNode.StationNode.getIcon(piconImage);
				piconCache.put(stationID, piconImage, icon);
			}
		}
		
		void updateTreeNodes(DefaultTreeModel treeModel, BSTreeNode.RootNode rootNode, PiconCache piconCache) {
			rootNode.updateStationNodes(stationID, treeModel, stations->{
				PiconCache.CachedPicon cachedPicon = piconCache.get(stationID);
				if (cachedPicon!=null)
					for (BSTreeNode.StationNode treeNode:stations)
						treeNode.setPicon(cachedPicon.piconImage, cachedPicon.icon);
			});
		}
	}
}