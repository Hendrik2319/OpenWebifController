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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

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
	
	private enum RowHeight {
		_15px(15),
		_17px(17),
		_19px(19),
		_21px(21),
		_23px(23),
		;
		private final int value;
		RowHeight(int value) { this.value = value; }
		@Override public String toString() { return String.format("%d px", value); }
		
		public static RowHeight get(int value) {
			for (RowHeight e:values())
				if (e.value==value)
					return e;
			return null;
		}
	}
	
	private final EPG epg;
	private final Vector<Bouquet.SubService> stations;
	private final LoadEPGThread loadEPGThread;
	private final JLabel statusOutput;
	private final EPGView epgView;
	private final JScrollBar epgViewVertScrollBar;
	private final JScrollBar epgViewHorizScrollBar;

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
		
		int rowHeight = OpenWebifController.settings.getInt(ValueKey.EPGDialog_RowHeight, -1);
		if (rowHeight<0) rowHeight = epgView.getRowHeight();
		else epgView.setRowHeight(rowHeight);
		
		JComboBox<RowHeight> cmbbxRowHeight = OpenWebifController.createComboBox(RowHeight.values(), RowHeight.get(rowHeight), val->{
			epgView.setRowHeight(val.value);
			epgView.repaint();
			reconfigureEPGViewVertScrollBar();
			OpenWebifController.settings.putInt(ValueKey.EPGDialog_RowHeight, val.value);
		});
		
		//int timeScale_min = epgView.getTimeScale_s(400)/60;
		int timeScale_min = OpenWebifController.settings.getInt(ValueKey.EPGDialog_TimeScale, -1);
		if (timeScale_min<0) timeScale_min = epgView.getTimeScale_s(400)/60;
		else epgView.setTimeScale(400, timeScale_min*60);
		
		JLabel timeScaleValueLabel = new JLabel(String.format("400px ~ %02d:%02dh ", timeScale_min/60, timeScale_min%60));
		JSlider timeScaleSlider = new JSlider(JSlider.HORIZONTAL);
		timeScaleSlider.setMinimum(30);
		timeScaleSlider.setMaximum(4*60);
		timeScaleSlider.setValue(timeScale_min);
		timeScaleSlider.addChangeListener(e->{
			int timeScale_min_ = timeScaleSlider.getValue();
			epgView.setTimeScale(400, timeScale_min_*60);
			epgView.repaint();
			timeScaleValueLabel.setText(String.format("400px ~ %02d:%02dh ", timeScale_min_/60, timeScale_min_%60));
			reconfigureEPGViewHorizScrollBar();
			OpenWebifController.settings.putInt(ValueKey.EPGDialog_TimeScale, timeScale_min_);
		});
		
		epgViewHorizScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
		epgViewVertScrollBar  = new JScrollBar(JScrollBar.VERTICAL);
		
		epgViewVertScrollBar.addAdjustmentListener(e -> epgView.setRowOffsetY_px(e.getValue()));
		//epgViewVertScrollBar.setValues(epgView.getRowOffsetY(), epgView.getRowViewHeight(), 0, epgView.getContentHeight());
		
		epgViewHorizScrollBar.addAdjustmentListener(e -> epgView.setRowAnchorTime_s(e.getValue()));
		//epgViewHorizScrollBar.setValues(epgView.getRowOffsetY(), epgView.getRowViewHeight(), 0, epgView.getContentHeight());
		
		epgView.addMouseWheelListener(e -> {
			JScrollBar scrollBar;
			if ( (e.getModifiersEx() & MouseWheelEvent.SHIFT_DOWN_MASK) == 0 ) {
				scrollBar = epgViewVertScrollBar;
				if (epgView.getRowViewHeight_px()>=epgView.getContentHeight_px())
					return;
			} else {
				scrollBar = epgViewHorizScrollBar;
				if (epgView.getMaxTime_s()-epgView.getMinTime_s() < epgView.getRowViewWidth_s())
					return;
			}
			
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
			int minimum = scrollBar.getMinimum();
			int maximum = scrollBar.getMaximum();
			
			value += wheelRotation*increment;
			if      (value>=minimum && value+visible<=maximum) scrollBar.setValue(value);
			else if (value<minimum)                            scrollBar.setValue(minimum);
			else if (value+visible>maximum)                    scrollBar.setValue(maximum-visible);
		});
		
		JButton closeButton = OpenWebifController.createButton("Close", true, e->closeDialog());
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		JPanel northPanel = new JPanel(new GridBagLayout());
		set(c,0,0,0,0); northPanel.add(new JLabel("Row Height: "),c);
		set(c,1,0,0,0); northPanel.add(cmbbxRowHeight,c);
		set(c,2,0,0,0); northPanel.add(new JLabel("  Time Scale: "),c);
		set(c,3,0,0,0); northPanel.add(timeScaleValueLabel,c);
		set(c,4,0,1,0); northPanel.add(timeScaleSlider,c);
		
		JPanel centerPanel = new JPanel(new GridBagLayout());
		set(c,0,0,1,1); centerPanel.add(epgView,c);
		set(c,1,0,0,1); centerPanel.add(epgViewVertScrollBar,c);
		set(c,0,1,1,0); centerPanel.add(epgViewHorizScrollBar,c);
		
		JPanel southPanel = new JPanel(new GridBagLayout());
		set(c,0,0,1,0); southPanel.add(statusOutput,c);
		set(c,1,0,0,0); southPanel.add(loadEPGThread.getButton(),c);
		set(c,2,0,0,0); southPanel.add(closeButton,c);
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(northPanel ,BorderLayout.NORTH );
		contentPane.add(centerPanel,BorderLayout.CENTER);
		contentPane.add(southPanel ,BorderLayout.SOUTH );
		
		createGUI(contentPane);
		
		addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) {
				OpenWebifController.settings.putDimension(ValueKey.EPGDialogWidth,ValueKey.EPGDialogHeight,EPGDialog.this.getSize());
				reconfigureEPGViewVertScrollBar();
				reconfigureEPGViewHorizScrollBar();
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
		
		reconfigureEPGViewVertScrollBar();
		reconfigureEPGViewHorizScrollBar();
		loadEPGThread.start();
	}

	private void reconfigureEPGViewVertScrollBar() {
		int value   = epgView.getRowOffsetY_px();
		int visible = epgView.getRowViewHeight_px();
		int minimum = 0;
		int maximum = epgView.getContentHeight_px();
		reconfigureScrollBar(epgViewVertScrollBar, epgView::setRowOffsetY_px, value, visible, minimum, maximum);
	}

	private void reconfigureEPGViewHorizScrollBar() {
		int value   = epgView.getRowAnchorTime_s();
		int visible = epgView.getRowViewWidth_s();
		int minimum = epgView.getMinTime_s();
		int maximum = epgView.getMaxTime_s();
		reconfigureScrollBar(epgViewHorizScrollBar, epgView::setRowAnchorTime_s, value, visible, minimum, maximum);
	}

	private void reconfigureScrollBar(JScrollBar scrollBar, Consumer<Integer> setValue, int value, int visible, int minimum, int maximum) {
		if (value+visible>maximum) {
			value = maximum-visible;
			if (value<minimum) {
				value = minimum;
				visible = maximum-minimum;
				scrollBar.setEnabled(false);
			} else
				scrollBar.setEnabled(true);
			setValue.accept(value);
			
		} else if (value<minimum) {
			value = minimum;
			if (value+visible>maximum) {
				visible = maximum-minimum;
				scrollBar.setEnabled(false);
			} else
				scrollBar.setEnabled(true);
			setValue.accept(value);
			
		} else
			scrollBar.setEnabled(true);
		
		scrollBar.setValues(value, visible, minimum, maximum);
		scrollBar.setUnitIncrement (visible/4);
		scrollBar.setBlockIncrement(visible*9/10);
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
		epgView.updateMinMaxTime();
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
						SwingUtilities.invokeLater(()->{
							statusOutput.setText( String.format("EPG for Station \"%s\": %s", subservice.name, taskTitle) );
						});
					});
					updateEPGView();
					SwingUtilities.invokeLater(EPGDialog.this::reconfigureEPGViewHorizScrollBar);
				}
			System.out.println("... done");
			SwingUtilities.invokeLater(()->{
				statusOutput.setText( "" );
			});
			
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
		private final long begin_s;
		private final long end_s;
		private final EPGevent event;

		EPGViewEvent(EPGevent event) {
			this.event = event;
			title = event.title;
			begin_s = event.begin_timestamp;
			end_s   = event.begin_timestamp+event.duration_sec;
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
		private static final int STATIONWIDTH = 100;
		
		private final Calendar calendar;
		private final long baseTimeOffset_s;
		private final HashMap<String, Vector<EPGViewEvent>> events;
		private int rowHeight;
		private int rowOffsetY;
		private int rowAnchorTime_s_based;
		private int minTime_s_based;
		private int maxTime_s_based;
		private float timeScale;
		private int scaleTicksBaseHour;
		private int scaleTicksBaseTime_s_based;
		
		// TODO: repaint periodically ('Now' marker)
		// TODO: hover effects for events
		// TODO: tooltip box for events
		
		EPGView() {
			calendar = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);
			calendar.set(Calendar.HOUR_OF_DAY, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			long baseTime = calendar.getTimeInMillis();
			baseTimeOffset_s = baseTime/1000;
			
			long currentTimeMillis = System.currentTimeMillis();
			minTime_s_based = maxTime_s_based = (int) (currentTimeMillis/1000-baseTimeOffset_s);
			rowAnchorTime_s_based = (int) (currentTimeMillis/1000-baseTimeOffset_s) - 3600/2; // now - 1/2 h
			timeScale = 4*3600f/800f; // 4h ~ 800px
			updateTimeScaleTicks();
						
			rowHeight = 23;
			rowOffsetY = 0;
			events = new HashMap<>();
			setBorder(BorderFactory.createLineBorder(Color.GRAY));
		}
		
		public int  getRowHeight() { return rowHeight; }
		public void setRowHeight(int rowHeight) { this.rowHeight = rowHeight; }

		public int getTimeScale_s(int width_px) { return Math.round( width_px * timeScale ); }
		public void setTimeScale(int width_px, int time_s) { timeScale = time_s / (float)width_px; }

		@SuppressWarnings("unused")
		private void setDate(int year, int month, int date, int hourOfDay, int minute, int second) {
			calendar.clear();
			calendar.set(year, month-1, date, hourOfDay, minute, second);
		}
		
		public void setRowOffsetY_px(int rowOffsetY) { this.rowOffsetY = rowOffsetY; repaint(); }
		public int  getRowOffsetY_px() { return rowOffsetY; }

		public int getContentHeight_px() { return stations.size()*rowHeight; }
		public int getRowViewHeight_px() { return Math.max(0, height-HEADERHEIGHT); }
		
		public void setRowAnchorTime_s(int rowAnchorTime_s) { this.rowAnchorTime_s_based = rowAnchorTime_s; updateTimeScaleTicks(); repaint(); }
		public int  getRowAnchorTime_s() { return rowAnchorTime_s_based; }

		public int getRowViewWidth_s() { return Math.max(0, Math.round( (width-STATIONWIDTH)*timeScale )); }
		public int getMinTime_s() { return minTime_s_based; }
		public int getMaxTime_s() { return maxTime_s_based; }

		private void updateTimeScaleTicks() {
			int x0Time_s_based = Math.round( rowAnchorTime_s_based - STATIONWIDTH*timeScale );
			calendar.setTimeInMillis( (x0Time_s_based+baseTimeOffset_s)*1000L );
			scaleTicksBaseHour = calendar.get(Calendar.HOUR_OF_DAY);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			scaleTicksBaseTime_s_based = (int) (calendar.getTimeInMillis()/1000 - baseTimeOffset_s);
			//String timeStr1 = OpenWebifController.dateTimeFormatter.getTimeStr((scaleTicksBaseTime_s_based+baseTimeOffset_s)*1000L, false, true, false, true, false);
			//String timeStr2 = DateTimeFormatter.getTimeStr(calendar, false, true, false, true, false);
			//System.out.printf("Hour: %d | BaseTime: %d%n", scaleTicksBaseHour, scaleTicksBaseTime_s_based);
			//System.out.printf("TimeStr1: %s%n", timeStr1);
			//System.out.printf("TimeStr2: %s%n", timeStr2);
		}

		synchronized void updateEvents(StationID stationID, Vector<EPGViewEvent> viewEvents) {
			String key = stationID.toIDStr();
			if (viewEvents==null) events.remove(key);
			else                  events.put(key, viewEvents);
		}

		synchronized void updateMinMaxTime() {
			long now = System.currentTimeMillis() / 1000;
			long min = now;
			long max = now;
			for (Vector<EPGViewEvent> eventList:events.values()) {
				for (EPGViewEvent event:eventList) {
					min = Math.min(min, event.begin_s);
					max = Math.max(max, event.end_s  );
				}
			}
			minTime_s_based = (int) (min - baseTimeOffset_s);
			maxTime_s_based = (int) (max - baseTimeOffset_s);
		}
		
		synchronized Vector<EPGViewEvent> getEvents(StationID stationID) {
			return events.get(stationID.toIDStr());
		}
		

		@Override
		protected void paintCanvas(Graphics g, int x0_, int y0_, int width, int height) {
			if (!(g instanceof Graphics2D)) return;
			Graphics2D g2 = (Graphics2D) g;
			Shape oldClip = g2.getClip();
			Rectangle mainClip;
			
			g2.setClip(mainClip = new Rectangle(x0_, y0_, width, height));
			
			g2.setColor(Color.BLACK);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			
			int fontHeight = 8; // default font size: 11  -->  fontHeight == 8
			int stationTextOffsetX = 10;
			int   eventTextOffsetX = 5;
			int rowTextOffsetY = (rowHeight-1-fontHeight)/2+fontHeight; 
			
			g2.setColor(Color.GRAY);
			g2.drawLine(x0_, y0_+HEADERHEIGHT-1, x0_+width-1, y0_+HEADERHEIGHT-1);
			
			int xBase = x0_ + STATIONWIDTH + Math.round( (scaleTicksBaseTime_s_based - rowAnchorTime_s_based)/timeScale );
			int iHour = 0;
			int iQuarter = 0;
			int xTick = xBase;
			while (xTick<x0_) {
				iQuarter++;
				if (iQuarter==4) { iQuarter = 0; iHour++; }
				xTick = xBase + Math.round( (iHour*3600 + iQuarter*900)/timeScale );
			}
			while (xTick<=x0_+width) {
				switch (iQuarter) {
				case 0:
					g2.setColor(Color.GRAY);
					g2.drawLine(xTick, y0_, xTick, y0_+HEADERHEIGHT-1);
					g2.setColor(Color.BLACK);
					g2.drawString(String.format("%02d:00", (scaleTicksBaseHour+iHour)%24), xTick+4, y0_+11);
					break;
				case 1: case 3:
					g2.setColor(Color.GRAY);
					g2.drawLine(xTick, y0_+(HEADERHEIGHT*3)/4, xTick, y0_+HEADERHEIGHT-1);
					break;
				case 2:
					g2.setColor(Color.GRAY);
					g2.drawLine(xTick, y0_+HEADERHEIGHT/2, xTick, y0_+HEADERHEIGHT-1);
					break;
				}
				iQuarter++;
				if (iQuarter==4) { iQuarter = 0; iHour++; }
				xTick = xBase + Math.round( (iHour*3600 + iQuarter*900)/timeScale );
			}
			
			int y0 = y0_+HEADERHEIGHT-rowOffsetY;
			mainClip = new Rectangle(x0_, y0_+HEADERHEIGHT, width, height-HEADERHEIGHT);
			Rectangle rowViewClip = new Rectangle(x0_+STATIONWIDTH, y0_+HEADERHEIGHT, width-STATIONWIDTH, height-HEADERHEIGHT);
			
			for (int i=0; i<stations.size(); i++) {
				Bouquet.SubService station = stations.get(i);
				Vector<EPGViewEvent> events = station.isMarker() ? null : getEvents(station.service.stationID);
				
				int rowY     = y0+rowHeight*i;
				int nextRowY = y0+rowHeight*(i+1);
				
				Rectangle stationCellClip = new Rectangle(x0_, rowY, STATIONWIDTH, rowHeight).intersection(mainClip);
				if (!stationCellClip.isEmpty()) {
					g2.setClip(stationCellClip);
					g2.setColor(Color.GRAY);
					g2.drawLine(x0_               , nextRowY-1, x0_+STATIONWIDTH-1, nextRowY-1);
					g2.drawLine(x0_+STATIONWIDTH-1, rowY      , x0_+STATIONWIDTH-1, nextRowY-1);
				}
				
				Rectangle stationTextCellClip = new Rectangle(x0_, rowY, STATIONWIDTH-1, rowHeight-1).intersection(mainClip);
				if (!stationTextCellClip.isEmpty()) {
					g2.setClip(stationTextCellClip);
					
					if (!station.isMarker()) {
						g2.setColor(Color.WHITE);
						g2.fillRect(x0_-10, rowY-10, STATIONWIDTH+10, rowHeight+10);
					}
					
					g2.setColor(events!=null || station.isMarker() ? Color.BLACK : Color.GRAY);
					g2.drawString(station.name, x0_+stationTextOffsetX, rowY+rowTextOffsetY);
				}
				
				if (events!=null && !events.isEmpty())
					for (EPGViewEvent event:events) {
						long tBegin_s_based = event.begin_s - baseTimeOffset_s;
						long   tEnd_s_based = event.end_s   - baseTimeOffset_s;
						int xBegin = x0_ + STATIONWIDTH + Math.round( (tBegin_s_based - rowAnchorTime_s_based)/timeScale );
						int xEnd   = x0_ + STATIONWIDTH + Math.round( (tEnd_s_based   - rowAnchorTime_s_based)/timeScale );
						
						Rectangle eventCellClip = new Rectangle(xBegin, rowY, xEnd-xBegin-1, rowHeight-1).intersection(rowViewClip);
						if (!eventCellClip.isEmpty()) {
							g2.setClip(eventCellClip);
							g2.setColor(Color.BLACK);
							g2.drawRect(xBegin, rowY, xEnd-xBegin-2, rowHeight-2);
						}
						
						Rectangle eventTextClip = new Rectangle(xBegin+1, rowY+1, xEnd-xBegin-3, rowHeight-1-2).intersection(rowViewClip);
						if (!eventTextClip.isEmpty()) {
							g2.setClip(eventTextClip);
							g2.setColor(Color.BLACK);
							int textX = xBegin+1+eventTextOffsetX;
							if (textX < x0_+STATIONWIDTH+2) textX = x0_+STATIONWIDTH+2;
							g2.drawString(event.title, textX, rowY+rowTextOffsetY);
						}
					}
			}
			
			g2.setClip(oldClip);
			
			//g2.setClip(rowViewClip);
			g2.setColor(Color.RED);
			long tNow_ms = System.currentTimeMillis();
			long tNow_s_based = tNow_ms/1000 - baseTimeOffset_s;
			if (tNow_s_based > rowAnchorTime_s_based) {
				int xNow = x0_ + STATIONWIDTH + Math.round( (tNow_s_based - rowAnchorTime_s_based)/timeScale );
				g2.drawLine(xNow, y0_+HEADERHEIGHT, xNow, y0_+height-1);
			}
		}
		
	}
}
