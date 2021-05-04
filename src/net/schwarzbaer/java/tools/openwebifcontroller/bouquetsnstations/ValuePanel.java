package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Supplier;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.system.DateTimeFormatter;

class ValuePanel {
	private final Supplier<String> getBaseURL;
	private final ImageView imageView;
	private final JTextArea textView;
	final JSplitPane panel;
	private TextUpdateTask runningTask;
	private boolean updateEPGAlways;
	private BSTreeNode.RootNode shownRootNode;
	private BSTreeNode.BouquetNode shownBouquetNode;
	private BSTreeNode.StationNode shownStationNode;
	
	ValuePanel(Supplier<String> getBaseURL) {
		this.getBaseURL = getBaseURL;
		
		runningTask = null;
		shownRootNode = null;
		shownBouquetNode = null;
		shownStationNode = null;
		
		imageView = new ImageView(400,500);
		imageView.setBgColor(Color.BLACK);
		imageView.reset();
		
		textView = new JTextArea();
		textView.setEditable(false);
		
		JScrollPane textViewScrollPane = new JScrollPane(textView);
		textViewScrollPane.setPreferredSize(new Dimension(400,500));
		
		panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,imageView,textViewScrollPane);
		panel.setPreferredSize(new Dimension(300,500));
		
		ContextMenu textViewContextMenu = new ContextMenu();
		textViewContextMenu.addTo(textView);
		
		JMenuItem miUpdateEPG;
		JCheckBoxMenuItem miUpdateEPGAlways;
		
		updateEPGAlways = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateEPGAlways, false);
		textViewContextMenu.add(miUpdateEPGAlways = OpenWebifController.createCheckBoxMenuItem("Update EPG everytime on Select", updateEPGAlways, isChecked->{
			OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_UpdateEPGAlways, updateEPGAlways = isChecked);
		}) );
		
		textViewContextMenu.add(miUpdateEPG = OpenWebifController.createMenuItem("Update EPG Now", e->{
			if (shownStationNode != null)
				startEpgUpdate(shownStationNode, this.getBaseURL.get());
		}) );
		
		textViewContextMenu.addSeparator();
		
		boolean textViewLineWrap = OpenWebifController.settings.getBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_TextViewLineWrap, false);
		textView.setLineWrap(textViewLineWrap);
		textView.setWrapStyleWord(textViewLineWrap);
		textViewContextMenu.add(OpenWebifController.createCheckBoxMenuItem("Line Wrap", textViewLineWrap, isChecked->{
			textView.setLineWrap(isChecked);
			textView.setWrapStyleWord(isChecked);
			OpenWebifController.settings.putBool(OpenWebifController.AppSettings.ValueKey.BouquetsNStations_TextViewLineWrap, isChecked);
		}) );
		
		textViewContextMenu.addContextMenuInvokeListener((comp, x, y) -> {
			miUpdateEPG      .setEnabled(shownStationNode != null);
			miUpdateEPGAlways.setEnabled(shownStationNode != null);
		});

	}
	
	void showValues(TreePath selectedTreePath) {
		clearTextUpdateTask();
		shownRootNode = null;
		shownBouquetNode = null;
		shownStationNode = null;
		
		if (selectedTreePath != null) {
			Object obj = selectedTreePath.getLastPathComponent();
			
			if (obj instanceof BSTreeNode.RootNode) {
				shownRootNode = (BSTreeNode.RootNode) obj;
				
				imageView.setImage(null);
				imageView.reset();
				
				textView.setText(generateOutput(shownRootNode));
				return;
			}
			
			if (obj instanceof BSTreeNode.BouquetNode) {
				shownBouquetNode = (BSTreeNode.BouquetNode) obj;
				
				imageView.setImage(null);
				imageView.reset();
				
				textView.setText(generateOutput(shownBouquetNode));
				return;
			}
			
			if (obj instanceof BSTreeNode.StationNode) {
				shownStationNode = (BSTreeNode.StationNode) obj;
				
				imageView.setImage(shownStationNode.piconImage);
				imageView.reset();
				
				textView.setText(generateOutput(shownStationNode));
				if (shownStationNode.epgEvents==null || updateEPGAlways)
					startEpgUpdate(shownStationNode, getBaseURL.get());
				
				return;
			}
		}
		
		imageView.setImage(null);
		imageView.reset();
		textView.setText("");
	}

	private String generateOutput(BSTreeNode.RootNode rootNode) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Bouquets", rootNode.bouquetData.bouquets.size());
		
		String output = out.generateOutput();
		return output;
	}

	private String generateOutput(BSTreeNode.BouquetNode bouquetNode) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Name"             , bouquetNode.bouquet.name);
		out.add(0, "Service Reference", bouquetNode.bouquet.servicereference);
		out.add(0, "SubServices"      , bouquetNode.bouquet.subservices.size());
		
		String output = out.generateOutput();
		return output;
	}

	private String generateOutput(BSTreeNode.StationNode stationNode) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Name"             , stationNode.subservice.name);
		out.add(0, "Position"         , stationNode.subservice.pos);
		out.add(0, "Program"          , stationNode.subservice.program);
		out.add(0, "Service Reference", stationNode.subservice.servicereference);
		
		if (stationNode.piconImage==null)
			out.add(0, "Picon Image", "%s", "none");
		else
			out.add(0, "Picon Image", "%d x %d", stationNode.piconImage.getWidth(), stationNode.piconImage.getHeight());
		
		if (stationNode.icon==null)
			out.add(0, "Icon", "%s", "none");
		else
			out.add(0, "Icon", "%d x %d", stationNode.icon.getIconWidth(), stationNode.icon.getIconHeight());
		
		if (stationNode.isServicePlayable==null)
			out.add(0, "Is Playable", "%s", "undefined");
		else
			out.add(0, "Is Playable", stationNode.isServicePlayable);
		
		String output = out.generateOutput();
		
		if (stationNode.epgEvents!=null) {
			output += "\r\n";
			out.clear();
			out.add(0, "Current EGP Event");
			if (stationNode.epgEvents.isEmpty())
				out.add(1, "No Events");
			else {
				int level = stationNode.epgEvents.size()==1 ? 1 : 2;
				for (int i=0; i<stationNode.epgEvents.size(); i++) {
					OpenWebifTools.EPGevent event = stationNode.epgEvents.get(i);
					if (level==2) out.add(1, String.format("Event[%d]", i+1));
					out.add(level, "Station"   , event.station_name);
					out.add(level, "SRef"      , event.sref);
					out.add(level, "Title"     , event.title);
					out.add(level, "Genre"     , "[%d] \"%s\"", event.genreid, event.genre);
					out.add(level, "ID"        , event.id);
					out.add(level, "Begin"     , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(event.begin_timestamp*1000, true, true, false, true, false) );
					out.add(level, "Now"       , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(event.now_timestamp  *1000, true, true, false, true, false) );
					out.add(level, "Duration"  , "%s", DateTimeFormatter.getDurationStr(event.duration_sec));
					out.add(level, "Remaining" , "%s", DateTimeFormatter.getDurationStr(event.remaining));
					out.add(level, "Description");
					out.add(level+1, "", event.shortdesc);
					out.add(level+1, "", event.longdesc );
				}
			}
			
			output += out.generateOutput();
		}
		
		return output;
	}
	
	private synchronized void clearTextUpdateTask() {
		runningTask = null;
	}
	
	private synchronized void startEpgUpdate(BSTreeNode.StationNode stationNode, String baseURL) {
		runningTask = new TextUpdateTask(()->{
			stationNode.updateEPG(baseURL);
			return generateOutput(stationNode);
		});
		runningTask.start();
	}

	private synchronized void setUpdateResult(String str, TextUpdateTask textUpdateTask) {
		if (runningTask==textUpdateTask) {
			textView.setText(str);
			clearTextUpdateTask();
		}
	}

	private class TextUpdateTask {
		
		private final Supplier<String> generateText;

		TextUpdateTask(Supplier<String> generateText) {
			this.generateText = generateText;
		}
		
		void start() {
			new Thread(()->{
				String str = generateText.get();
				setUpdateResult(str, this);
			}).start();
		}
	}
}