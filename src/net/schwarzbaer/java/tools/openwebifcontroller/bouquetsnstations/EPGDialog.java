package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.gui.StandardDialog;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings.ValueKey;

public class EPGDialog extends StandardDialog {
	private static final long serialVersionUID = 8634962178940555542L;
	
	private final EPG epg;
	private final Vector<Bouquet.SubService> stations;
	private final LoadEPGThread loadEPGThread;
	private final JLabel statusOutput;
	private final EPGView epgView;

	public EPGDialog(String baseURL, Vector<Bouquet.SubService> stations, Window parent, String title, ModalityType modality, boolean repeatedUseOfDialogObject) {
		super(parent, title, modality, repeatedUseOfDialogObject);
		this.stations = stations;
		
		epg = new EPG(new EPG.Tools() {
			@Override public String getTimeStr(long millis) {
				return OpenWebifController.dateTimeFormatter.getTimeStr(millis, false, true, false, true, false);
			}
		});
		
		statusOutput = new JLabel("");
		statusOutput.setBorder(BorderFactory.createLoweredSoftBevelBorder());
		
		loadEPGThread = new LoadEPGThread(baseURL);
		epgView = new EPGView();
		
		JSlider widthSlider = new JSlider(JSlider.HORIZONTAL);
		JSlider heightSlider = new JSlider(JSlider.VERTICAL);
		
		JScrollBar epgViewHorizScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
		JScrollBar epgViewVertScrollBar  = new JScrollBar(JScrollBar.VERTICAL);
		
		JButton closeButton = OpenWebifController.createButton("Close", true, e->closeDialog());
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		JPanel bottomPanel = new JPanel(new GridBagLayout());
		set(c,0,0,1,0); bottomPanel.add(statusOutput,c);
		set(c,1,0,0,0); bottomPanel.add(loadEPGThread.getButton(),c);
		set(c,2,0,0,0); bottomPanel.add(closeButton,c);
		
		
		JPanel centerPaneL = new JPanel(new GridBagLayout());
		
		set(c,1,0,1,0); centerPaneL.add(widthSlider,c);
		set(c,0,1,0,1); centerPaneL.add(heightSlider,c);
		set(c,1,1,1,1); centerPaneL.add(epgView,c);
		set(c,2,1,0,1); centerPaneL.add(epgViewVertScrollBar,c);
		set(c,1,2,1,0); centerPaneL.add(epgViewHorizScrollBar,c);
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(centerPaneL,BorderLayout.CENTER);
		contentPane.add(bottomPanel,BorderLayout.SOUTH);
		
		createGUI(contentPane);
		
		addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) { OpenWebifController.settings.putDimension(ValueKey.EPGDialogWidth,ValueKey.EPGDialogHeight,EPGDialog.this.getSize()); }
			@Override public void componentMoved  (ComponentEvent e) {}
		});
		addWindowListener(new WindowListener() {
			@Override public void windowOpened     (WindowEvent e) {}
			@Override public void windowIconified  (WindowEvent e) {}
			@Override public void windowDeiconified(WindowEvent e) {}
			@Override public void windowDeactivated(WindowEvent e) {}
			@Override public void windowClosing    (WindowEvent e) {}
			@Override public void windowClosed     (WindowEvent e) { if (loadEPGThread.isRunning()) loadEPGThread.stop(); }
			@Override public void windowActivated  (WindowEvent e) {}
		});
		
		loadEPGThread.start();
	}

	private void set(GridBagConstraints c, int gridx, int gridy, double weightx, double weighty) {
		c.gridx = gridx;
		c.gridy = gridy;
		c.weightx = weightx;
		c.weighty = weighty;
	}

	public static void showDialog(String baseURL, Vector<Bouquet.SubService> subservices, Window parent, String title) {
		EPGDialog dialog = new EPGDialog(baseURL, subservices, parent, title, ModalityType.APPLICATION_MODAL, false);
		dialog.showDialog();
	}

	@Override
	public void showDialog() {
		if (OpenWebifController.settings.contains(ValueKey.EPGDialogWidth) &&
			OpenWebifController.settings.contains(ValueKey.EPGDialogHeight))
		{
			Dimension size = OpenWebifController.settings.getDimension(ValueKey.EPGDialogWidth,ValueKey.EPGDialogHeight);
			setPositionAndSize(null, size);
		}
		super.showDialog();
	}

	private void updateEPGView() {
		long beginTime_UnixTS = System.currentTimeMillis()/1000 - 3600;
		long endTime_UnixTS   = beginTime_UnixTS + 3600*4;
		for (Bouquet.SubService station:stations) {
			if (station.isMarker()) continue;
			Vector<EPGevent> events = epg.getEvents(station.service.stationID, beginTime_UnixTS, endTime_UnixTS, true);
			Vector<EPGViewEvent> viewEvents = events.isEmpty() ? null : EPGViewEvent.convert(events);
			epgView.updateEvents(station.service.stationID,viewEvents);
		}
		epgView.repaint();
	}

	private class LoadEPGThread {
		private final JButton button;
		private final String baseURL;

		private Thread thread;
		private boolean isRunning;

		
		LoadEPGThread(String baseURL) {
			this.baseURL = baseURL;
			button = OpenWebifController.createButton("Load EPG", true, null);
			
			isRunning = false;
			thread = null;
			
			button.addActionListener(e->startStopThread());
		}

		private void loadEPG(String baseURL) {
			setRunning(true);
			
			synchronized (LoadEPGThread.this) {
				button.setText("Cancel EPG Loading");
				button.setEnabled(true);
			}
			
			for (Bouquet.SubService subservice:stations)
				if (!subservice.isMarker()) {
					boolean isInterrupted = Thread.currentThread().isInterrupted();
					System.out.printf("EPG for Station \"%s\"%s%n", subservice.name, isInterrupted ? " -> omitted" : "");
					if (isInterrupted) continue;
					epg.readEPGforService(baseURL, subservice.service.stationID, null, null, taskTitle->{
						statusOutput.setText( String.format("EPG for Station \"%s\": %s", subservice.name, taskTitle) );
					});
					updateEPGView();
				}
			System.out.println("... done");
			
			setRunning(false);
			
			synchronized (LoadEPGThread.this) {
				button.setText("Load EPG");
				button.setEnabled(true);
			}
		}

		private synchronized void startStopThread() {
			if (isRunning()) {
				thread.interrupt();
				thread = null;
			} else {
				if (thread==null) thread = new Thread(()->loadEPG(baseURL));
				thread.start();
			}
			button.setEnabled(false);
		}

		synchronized boolean isRunning() {
			return isRunning;
		}

		private synchronized void setRunning(boolean isRunning) {
			this.isRunning = isRunning;
		}

		JButton getButton() {
			return button;
		}

		void start() {
			startStopThread();
		}

		void stop() {
			thread.interrupt();
		}
		
	}
	
	private static class EPGViewEvent {
	
		static Vector<EPGViewEvent> convert(Vector<EPGevent> events) {
			// TODO Auto-generated method stub
			return null;
		}
	
	}

	private class EPGView extends Canvas {
		private static final long serialVersionUID = 8667640106638383774L;

		private final HashMap<String, Vector<EPGViewEvent>> events;
		
		EPGView() {
			events = new HashMap<>();
			setBorder(BorderFactory.createLineBorder(Color.GRAY));
		}
		
		synchronized void updateEvents(StationID stationID, Vector<EPGViewEvent> viewEvents) {
			String key = stationID.toIDStr();
			if (viewEvents==null) events.remove(key);
			else                  events.put(key, viewEvents);
		}

		@Override
		protected void paintCanvas(Graphics g, int x0, int y0, int width, int height) {
			Shape oldClip = g.getClip();
			g.setClip(x0, y0, width, height);
			
			int rowHeight = 30;
			int stationWidth = 100;
			
			for (int i=0; i<stations.size(); i++) {
				Bouquet.SubService station = stations.get(i);
//				renderComp.setForeground(station.isMarker() ? Color.GRAY : Color.BLACK);
//				renderComp.setText(station.name);
//				renderComp.setSize(stationWidth,rowHeight);
//				renderComp.setLocation(x0, y0+rowHeight*i);
//				renderComp.paint(g);
			}
			
			// TODO
			
			g.setClip(oldClip);
		}
		
	}
}
