package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseWheelEvent;
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
		//System.out.printf("BlockIncrement: %d%n", epgViewVertScrollBar.getBlockIncrement());
		//System.out.printf("UnitIncrement : %d%n", epgViewVertScrollBar.getUnitIncrement());
		
		epgViewVertScrollBar.addAdjustmentListener(e -> {
			epgView.setRowOffsetY(e.getValue());
		});
		epgViewVertScrollBar.setValues(epgView.getRowOffsetY(), epgView.getRowViewHeight(), 0, epgView.getContentHeight());
		
		epgView.addMouseWheelListener(e -> {
			JScrollBar scrollBar = epgViewVertScrollBar;
			if (epgView.getRowViewHeight()>=epgView.getContentHeight())
				return;
			
			int scrollType = e.getScrollType();
			//String scrollTypeStr = "???";
			int increment = 0;
			switch (scrollType) {
			case MouseWheelEvent.WHEEL_UNIT_SCROLL : /*scrollTypeStr = "UNIT" ;*/ increment = scrollBar.getUnitIncrement();  break;
			case MouseWheelEvent.WHEEL_BLOCK_SCROLL: /*scrollTypeStr = "BLOCK";*/ increment = scrollBar.getBlockIncrement(); break;
			}
			int wheelRotation = e.getWheelRotation();
			//System.out.printf("ScrollAmount(\"%s\"): %d x %d -> %d%n", scrollTypeStr, wheelRotation, increment, wheelRotation*increment);
			
			int value   = scrollBar.getValue();
			int visible = scrollBar.getVisibleAmount();
			int maximum = scrollBar.getMaximum();
			
			value += wheelRotation*increment;
			if      (value>=0 && value+visible<=maximum) scrollBar.setValue(value);
			else if (value<0)                            scrollBar.setValue(0);
			else if (value+visible>maximum)              scrollBar.setValue(maximum-visible);
		});
		
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
			@Override public void componentResized(ComponentEvent e) {
				OpenWebifController.settings.putDimension(ValueKey.EPGDialogWidth,ValueKey.EPGDialogHeight,EPGDialog.this.getSize());
				int rowOffsetY = epgView.getRowOffsetY();
				int viewHeight = epgView.getRowViewHeight();
				int contentHeight = epgView.getContentHeight();
				if (rowOffsetY+viewHeight>contentHeight) {
					rowOffsetY = contentHeight-viewHeight;
					if (rowOffsetY<0) {
						rowOffsetY = 0;
						viewHeight = contentHeight;
						epgViewVertScrollBar.setEnabled(false);
					} else
						epgViewVertScrollBar.setEnabled(true);
					epgView.setRowOffsetY(rowOffsetY);
				} else
					epgViewVertScrollBar.setEnabled(true);
				epgViewVertScrollBar.setValues(rowOffsetY, viewHeight, 0, contentHeight);
				epgViewVertScrollBar.setUnitIncrement (viewHeight/4);
				epgViewVertScrollBar.setBlockIncrement(viewHeight*9/10);
			}
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
			statusOutput.setText( "" );
			
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
	
	@SuppressWarnings("unused")
	private static class EPGViewEvent {
	
		private final String title;
		private final long begin;
		private final long end;
		private final EPGevent event;

		EPGViewEvent(EPGevent event) {
			this.event = event;
			title = event.title;
			begin = event.begin_timestamp;
			end   = event.begin_timestamp+event.duration_sec;
		}

		static Vector<EPGViewEvent> convert(Vector<EPGevent> events) {
			if (events==null) return null;
			Vector<EPGViewEvent> result = new Vector<>(events.size());
			for (EPGevent event:events) result.add(new EPGViewEvent(event));
			return result;
		}
	
	}

	private class EPGView extends Canvas {
		private static final long serialVersionUID = 8667640106638383774L;
		private static final int HEADERHEIGHT = 20;
		private static final int ROWHEIGHT = 23;
		private static final int STATIONWIDTH = 100;
		
		private final HashMap<String, Vector<EPGViewEvent>> events;
		private int rowOffsetY;
		
		EPGView() {
			rowOffsetY = 0;
			events = new HashMap<>();
			setBorder(BorderFactory.createLineBorder(Color.GRAY));
		}
		
		public void setRowOffsetY(int rowOffsetY) {
			this.rowOffsetY = rowOffsetY;
			repaint();
		}
		public int getRowOffsetY() {
			return rowOffsetY;
		}

		public int getContentHeight() {
			return stations.size()*ROWHEIGHT;
		}
		public int getRowViewHeight() {
			return Math.max(0, height-HEADERHEIGHT);
		}

		synchronized void updateEvents(StationID stationID, Vector<EPGViewEvent> viewEvents) {
			String key = stationID.toIDStr();
			if (viewEvents==null) events.remove(key);
			else                  events.put(key, viewEvents);
		}
		
		synchronized Vector<EPGViewEvent> getEvents(StationID stationID) {
			return events.get(stationID.toIDStr());
		}
		

		@Override
		protected void paintCanvas(Graphics g, int x0, int y0_, int width, int height) {
			if (!(g instanceof Graphics2D)) return;
			Graphics2D g2 = (Graphics2D) g;
			Shape oldClip = g2.getClip();
			Rectangle mainClip;
			
			g2.setClip(mainClip = new Rectangle(x0, y0_, width, height));
			
			g2.setColor(Color.BLACK);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			
			int fontHeight = 8; // default font size: 11  -->  fontHeight == 8
			int stationTextOffsetX = 10;
			int rowTextOffsetY = (ROWHEIGHT-1-fontHeight)/2+fontHeight; 
			int headerTextOffsetY  = (HEADERHEIGHT-1-fontHeight)/2+fontHeight;
			
			g2.setColor(Color.GRAY);
			g2.drawLine(x0, y0_+HEADERHEIGHT-1, x0+width-2, y0_+HEADERHEIGHT-1);
			g2.setColor(Color.BLACK);
			g2.drawString("Zeit-Achse", x0+10, y0_+headerTextOffsetY);
			
			int y0 = y0_+HEADERHEIGHT-rowOffsetY;
			mainClip = new Rectangle(x0, y0_+HEADERHEIGHT, width, height-HEADERHEIGHT);
			
			for (int i=0; i<stations.size(); i++) {
				Bouquet.SubService station = stations.get(i);
				Vector<EPGViewEvent> events = station.isMarker() ? null : getEvents(station.service.stationID);
				
				Rectangle stationCellClip = new Rectangle(x0, y0+ROWHEIGHT*i, STATIONWIDTH, ROWHEIGHT).intersection(mainClip);
				if (!stationCellClip.isEmpty()) {
					g2.setClip(stationCellClip);
					g2.setColor(Color.GRAY);
					g2.drawLine(x0               , y0+ROWHEIGHT*(i+1)-1, x0+STATIONWIDTH-1, y0+ROWHEIGHT*(i+1)-1);
					g2.drawLine(x0+STATIONWIDTH-1, y0+ROWHEIGHT*(i+0)  , x0+STATIONWIDTH-1, y0+ROWHEIGHT*(i+1)-1);
				}
				
				Rectangle stationTextCellClip = new Rectangle(x0, y0+ROWHEIGHT*i, STATIONWIDTH-1, ROWHEIGHT-1).intersection(mainClip);
				if (!stationTextCellClip.isEmpty()) {
					g2.setClip(stationTextCellClip);
					
					if (!station.isMarker()) {
						g2.setColor(Color.WHITE);
						g2.fillRect(x0-10, y0+ROWHEIGHT*i-10, STATIONWIDTH+10, ROWHEIGHT+10);
					}
					
					g2.setColor(events!=null || station.isMarker() ? Color.BLACK : Color.GRAY);
					g2.drawString(station.name, x0+stationTextOffsetX, y0+ROWHEIGHT*i+rowTextOffsetY);
				}
			}
			
			g2.setClip(oldClip);
		}
		
	}
}
