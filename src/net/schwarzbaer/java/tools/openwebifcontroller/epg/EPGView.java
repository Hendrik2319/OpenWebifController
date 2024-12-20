package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

import net.schwarzbaer.java.lib.gui.Canvas;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

class EPGView extends Canvas {
	private static final long serialVersionUID = 8667640106638383774L;
	
	private static final Color COLOR_STATION_BG           = Color.WHITE;
	private static final Color COLOR_STATION_BG_HOVERED   = new Color(0xE5EDFF);
	private static final Color COLOR_STATION_FRAME        = Color.GRAY;
	private static final Color COLOR_STATION_TEXT         = Color.BLACK;
	private static final Color COLOR_STATION_TEXT_ISEMPTY = Color.GRAY;
	private static final Color COLOR_STATION_TEXT_CURRENTLY_PLAYED = new Color(0x0080ff);

	private static final Color COLOR_EVENT_HOVERED_BG = Color.WHITE;
	private static final Color COLOR_EVENT_FRAME      = Color.BLACK;
	private static final Color COLOR_EVENT_TEXT       = Color.BLACK;
	
	private static final Color COLOR_TIMESCALE_LINES = Color.GRAY;
	private static final Color COLOR_TIMESCALE_TEXT  = Color.BLACK;
	
	private static final Color COLOR_ITMER_HOVERED   = new Color(0x66B0FF);
	private static final Color COLOR_ITMER_RECORDING = Color.RED;
	private static final Color COLOR_ITMER_JUST_ZAP  = new Color(0x00B000);
	private static final Color COLOR_ITMER_DISABLED  = Color.GRAY;
	
	private static final Color COLOR_NOWMARKER = Color.RED;
	
	private static final int HEADERHEIGHT = 20;
	private static final int STATIONWIDTH = 100;
	
	private final Calendar calendar;
	private final long baseTimeOffset_s;
	private final HashMap<String, Vector<EPGViewEvent>> events;
	private final HashMap<String, HashMap<Long,Timer>> timers;
	private final Vector<SubService> stations;
	private int rowHeight;
	private int rowOffsetY;
	private int rowAnchorTime_s_based;
	private Integer rowAnchorTime_s_based_suggested;
	private int minTime_s_based;
	private int maxTime_s_based;
	private float timeScale;
	private int scaleTicksBaseHour;
	private int scaleTicksBaseTime_s_based;
	private EPGViewEvent hoveredEvent;
	private int repaintCounter;
	private StationID currentStation;
	private Integer hoveredStationIndex;
	public final ToolTip toolTip;

	EPGView(Vector<SubService> stations, long focusTime_s) {
		this.stations = stations;
		repaintCounter = 0;
		
		calendar = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		long baseTime = calendar.getTimeInMillis();
		baseTimeOffset_s = baseTime/1000;
		
		int focusTime_s_based = (int) (focusTime_s - baseTimeOffset_s);
		minTime_s_based = maxTime_s_based = focusTime_s_based;
		rowAnchorTime_s_based = focusTime_s_based - 3600/2; // now - 1/2 h
		rowAnchorTime_s_based_suggested = rowAnchorTime_s_based;
		timeScale = 4*3600f/800f; // 4h ~ 800px
		updateTimeScaleTicks();
					
		rowHeight = 23;
		rowOffsetY = 0;
		events = new HashMap<>();
		timers = new HashMap<>();
		hoveredEvent = null;
		currentStation = null;
		hoveredStationIndex = null;
		toolTip = new ToolTip() { @Override protected void repaintView() { repaint(); } };
		
		setBorder(BorderFactory.createLineBorder(Color.GRAY));
	}

	public void clearSuggestedFocus()
	{
		rowAnchorTime_s_based_suggested = null;
	}

	public void suggestFocus(long focusTime_s)
	{
		int focusTime_s_based = (int) (focusTime_s - baseTimeOffset_s);
		rowAnchorTime_s_based_suggested = focusTime_s_based - 3600/2; // now - 1/2 h
	}

	public void showStatus(PrintStream out)
	{
		out.printf("EPG View:%n");
		out.printf("    baseTimeOffset_s      : %12d [%s]%n", baseTimeOffset_s     , OpenWebifController.dateTimeFormatter.getTimeStr(baseTimeOffset_s*1000, true, true, false, true, true));
		out.printf("    minTime_s_based       : %12d [%s]%n", minTime_s_based      , DateTimeFormatter.getDurationStr(minTime_s_based      ));
		out.printf("    maxTime_s_based       : %12d [%s]%n", maxTime_s_based      , DateTimeFormatter.getDurationStr(maxTime_s_based      ));
		out.printf("    rowAnchorTime_s_based : %12d [%s]%n", rowAnchorTime_s_based, DateTimeFormatter.getDurationStr(rowAnchorTime_s_based));
	}

	public void setHoveredStation(Integer hoveredStationIndex) {
		this.hoveredStationIndex = hoveredStationIndex;
	}

	public void setCurrentStation(StationID stationID) {
		currentStation = stationID;
		repaint();
	}

	public void setHoveredEvent(EPGViewEvent hoveredEvent) {
		this.hoveredEvent = hoveredEvent;
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

	public Integer getRowAnchorTime_s_based_suggested() { return rowAnchorTime_s_based_suggested; }
	public int getRowViewWidth_s() { return Math.max(0, Math.round( (width-STATIONWIDTH)*timeScale )); }

	public int getMinTime_s() { return minTime_s_based; }
	public int getMaxTime_s() { return maxTime_s_based; }
	
	private String getTimeScaleDateStr(Graphics2D g2, long time_ms, float maxWidth) {
		String[] formatStrs = new String[] {
				"%1$tR %1$tA, %1$td.%1$tm.%1$ty",
				"%1$tR %1$tA",
				"%1$tR %1$ta",
				"%1$tR",
		};
		
		Font font = g2.getFont();
		FontRenderContext frc = g2.getFontRenderContext();
		for (String formatStr:formatStrs) {
			String str = OpenWebifController.dateTimeFormatter.getTimeStr(time_ms, Locale.GERMANY, formatStr);
			Rectangle2D bounds2 = font.getStringBounds(str, frc);
			if (bounds2.getWidth()+10 < maxWidth)
				return str;
		}
		return null;
	}

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

	static class Timer {
		final String name;
		final String serviceref;
		final long eventID;
		final int begin_s_based;
		final int end_s_based;
		final Timers.Timer timer;

		public Timer(String name, String serviceref, long eventID, int begin_s_based, int end_s_based, Timers.Timer timer) {
			this.name = name;
			this.serviceref = serviceref;
			this.eventID = eventID;
			this.begin_s_based = begin_s_based;
			this.end_s_based = end_s_based;
			this.timer = timer;
		}
	}

	Vector<Timer> convertTimers(Vector<Timers.Timer> timers) {
		if (timers==null || timers.isEmpty()) return null;
		Vector<Timer> result = new Vector<>(timers.size());
		for (Timers.Timer timer:timers) {
			int begin_s_based = (int) (timer.begin - baseTimeOffset_s);
			int   end_s_based = (int) (timer.end   - baseTimeOffset_s);
			if (timer.serviceref!=null)
				result.add(new Timer(timer.name, timer.serviceref, timer.eit, begin_s_based, end_s_based, timer));
		}
		return result;
	}

	synchronized void setTimers(Vector<Timer> timers) {
		this.timers.clear();
		for (Timer timer:timers) {
			HashMap<Long, Timer> stationTimerEvents = this.timers.get(timer.serviceref);
			if (stationTimerEvents==null)
				this.timers.put(timer.serviceref, stationTimerEvents = new HashMap<>());
			stationTimerEvents.put(timer.eventID, timer);
		}
	}
	
	synchronized HashMap<Long, Timer> getTimers(StationID stationID) {
		return timers.get(stationID.toIDStr(true));
	}
	
	synchronized Timer getTimer(String sRef, long eventID) {
		HashMap<Long, Timer> stationTimers = timers.get(sRef);
		if (stationTimers==null) return null;
		return stationTimers.get(eventID);
	}

	static class EPGViewEvent {
	
		final String title;
		final int begin_s_based;
		final int   end_s_based;
		final EPGevent event;
	
		EPGViewEvent(String title, int begin_s_based, int end_s_based, EPGevent event) {
			this.title = title;
			this.begin_s_based = begin_s_based;
			this.  end_s_based =   end_s_based;
			this.event = event;
		}
	
		boolean covers(int time_s_based) {
			return begin_s_based<=time_s_based && time_s_based<=end_s_based;
		}
	
		@Override
		public String toString() {
			return String.format("%s, %d-%d", title, begin_s_based, end_s_based);
		}
		
	}
	Vector<EPGViewEvent> convertEvents(Vector<EPGevent> events) {
		if (events==null || events.isEmpty()) return null;
		Vector<EPGViewEvent> result = new Vector<>(events.size());
		for (EPGevent event:events) {
			if (event.begin_timestamp==null) continue;
			long duration_sec = event.duration_sec==null ? 0 : event.duration_sec;
			int begin_s_based = (int) (event.begin_timestamp - baseTimeOffset_s);
			int   end_s_based = (int) (begin_s_based + duration_sec);
			result.add(new EPGViewEvent(event.title, begin_s_based, end_s_based, event));
		}
		return result;
	}

	synchronized void updateEvents(StationID stationID, Vector<EPGViewEvent> viewEvents/*, DataStatus dataStatus*/) {
		String key = stationID.toIDStr();
		//dataStatus.determineDataChange(events.get(key),viewEvents);
		if (viewEvents==null) events.remove(key);
		else                  events.put(key, viewEvents);
	}

	synchronized void updateMinMaxTime() {
		Integer min = null;
		Integer max = null;
		for (Vector<EPGViewEvent> eventList:events.values()) {
			for (EPGViewEvent event:eventList) {
				min = min == null ? event.begin_s_based : Math.min(min, event.begin_s_based);
				max = max == null ? event.  end_s_based : Math.max(max, event.  end_s_based);
			}
		}
		int now = (int) (System.currentTimeMillis() / 1000 - baseTimeOffset_s);
		minTime_s_based = min == null ? now : min;
		maxTime_s_based = max == null ? now : max;
	}
	
	synchronized Vector<EPGViewEvent> getEvents(StationID stationID) {
		return events.get(stationID.toIDStr());
	}
	

	public EPGViewEvent getEvent(int rowIndex, int time_s_based) {
		if (rowIndex<0 || rowIndex>=stations.size()) return null;
		
		SubService subService = stations.get(rowIndex);
		if (subService.isMarker()) return null;
		
		Vector<EPGViewEvent> stationEvents = getEvents(subService.service.stationID);
		if (stationEvents==null) return null;
		
		Vector<EPGViewEvent> coveringEvents = new Vector<>();
		for (EPGViewEvent event:stationEvents)
			if (event.covers(time_s_based))
				//return event;
				coveringEvents.add(event);
		
		if (coveringEvents.isEmpty())
			return null;
		
		if (coveringEvents.size()>1)
			coveringEvents.sort(Comparator.<EPGViewEvent,Long>comparing(ev->ev.event.duration_sec).thenComparing(ev->ev.event.begin_timestamp));
		
		return coveringEvents.firstElement();
	}


	class DataPos {
		final Integer time_s_based;
		final Integer rowIndex;
		final boolean isStationSelected;
		DataPos(Integer time_s_based, Integer rowIndex, boolean isStationSelected) {
			this.time_s_based = time_s_based;
			this.rowIndex = rowIndex;
			this.isStationSelected = isStationSelected;
		}
		@Override
		public String toString() {
			return String.format("DataPos [rowIndex=%s, time_s_based=%s, isStationSelected=%s]", rowIndex, time_s_based, isStationSelected);
		}
		
	}

	public DataPos getDataPos(Point point) {
		Border border = getBorder();
		Insets borderInsets = border==null ? null : border.getBorderInsets(this);
		int borderTop    = borderInsets==null ? 0 : borderInsets.top   ;
		int borderBottom = borderInsets==null ? 0 : borderInsets.bottom;
		int borderLeft   = borderInsets==null ? 0 : borderInsets.left  ;
		int borderRight  = borderInsets==null ? 0 : borderInsets.right ;
		
		boolean isStationSelected = point.x < borderLeft+STATIONWIDTH;
		
		Integer time_s_based = null;
		if (borderLeft+STATIONWIDTH < point.x && point.x < width-borderRight) {
			// int xBegin = x0_ + STATIONWIDTH + Math.round( (event.begin_s_based - rowAnchorTime_s_based)/timeScale );
			time_s_based = Math.round((point.x-STATIONWIDTH-borderLeft)*timeScale + rowAnchorTime_s_based);
		}
		Integer rowIndex = null;
		if (borderTop+HEADERHEIGHT<point.y && point.y<height-borderBottom) {
			//int y0 = y0_+HEADERHEIGHT-rowOffsetY;
			//int rowY = y0+rowHeight*i;
			rowIndex = (point.y-HEADERHEIGHT-borderTop+rowOffsetY)/rowHeight;
		}
		return time_s_based==null && rowIndex==null && !isStationSelected ? null : new DataPos(time_s_based,rowIndex,isStationSelected);
	}

	@Override
	protected void paintCanvas(Graphics g, final int x0_, final int y0_, final int width, final int height) {
		if (!(g instanceof Graphics2D)) return;
		final Graphics2D g2 = (Graphics2D) g;
		
		Shape oldClip = g2.getClip();
		
		g2.setClip(new Rectangle(x0_, y0_, width, height));
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		
		paintRepaintCounter(g2, x0_, y0_);
		paintTimeScale     (g2, x0_, y0_, width);
		paintMainView      (g2, x0_, y0_, width, height);
		
		g2.setClip(oldClip);
		
		paintNowMarker(g2, x0_, y0_,        height);
		toolTip.paint (g2, x0_, y0_, width, height);
	}

	private void paintMainView(final Graphics2D g2, final int x0_, final int y0_, final int width, final int height) {
		int fontHeight = 8; // default font size: 11  -->  fontHeight == 8
		int rowTextOffsetY = (rowHeight-1-fontHeight)/2+fontHeight; 
		
		int y0 = y0_+HEADERHEIGHT-rowOffsetY;
		Rectangle mainClip      = new Rectangle(x0_,              y0_+HEADERHEIGHT, width,              height-HEADERHEIGHT);
		Rectangle eventViewClip = new Rectangle(x0_+STATIONWIDTH, y0_+HEADERHEIGHT, width-STATIONWIDTH, height-HEADERHEIGHT);
		
		for (int i=0; i<stations.size(); i++) {
			SubService station = stations.get(i);
			Vector<EPGViewEvent> events = station.isMarker() ? null : getEvents(station.service.stationID);
			HashMap<Long,Timer>  timers = station.isMarker() ? null : getTimers(station.service.stationID);
			
			int rowY = y0+rowHeight*i;
			paintStation(g2, x0_, rowY, rowTextOffsetY, mainClip, station, i, events!=null);
			if (station.isMarker()) paintMarkerBar(g2, x0_, rowY, width, eventViewClip);
			paintTimers (g2, x0_, rowY, rowTextOffsetY, eventViewClip, timers);
			paintEvents (g2, x0_, rowY, rowTextOffsetY, eventViewClip, events, timers);
		}
	}

	private void paintMarkerBar(final Graphics2D g2, final int x0_, final int rowY, final int width, final Rectangle eventViewClip) {
		int xBase = x0_ + STATIONWIDTH + Math.round( (scaleTicksBaseTime_s_based - rowAnchorTime_s_based)/timeScale );
		int iQuarter = 0;
		int xTick = xBase;
		int lastXTick = xTick;
		g2.setClip(eventViewClip);
		g2.setColor(COLOR_STATION_FRAME);
		while (lastXTick<=x0_+width) {
			if (xTick-1>x0_+STATIONWIDTH)
				g2.drawLine(xTick-1, rowY, lastXTick, rowY+rowHeight-2);
			iQuarter++;
			lastXTick = xTick;
			xTick = xBase + Math.round( iQuarter*900/timeScale );
		}
	}

	private void paintStation(final Graphics2D g2, final int x0_, final int rowY, final int rowTextOffsetY, final Rectangle mainClip, final SubService station, int stationIndex, final boolean hasEvents) {
		int stationTextOffsetX = 10;
		int nextRowY = rowY+rowHeight;
		
		Rectangle stationCellClip = new Rectangle(x0_, rowY, STATIONWIDTH, rowHeight).intersection(mainClip);
		if (!stationCellClip.isEmpty()) {
			g2.setClip(stationCellClip);
			g2.setColor(COLOR_STATION_FRAME);
			g2.drawLine(x0_               , nextRowY-1, x0_+STATIONWIDTH-1, nextRowY-1);
			g2.drawLine(x0_+STATIONWIDTH-1, rowY      , x0_+STATIONWIDTH-1, nextRowY-1);
		}
		
		Rectangle stationTextCellClip = new Rectangle(x0_, rowY, STATIONWIDTH-1, rowHeight-1).intersection(mainClip);
		if (!stationTextCellClip.isEmpty()) {
			g2.setClip(stationTextCellClip);
			
			if (!station.isMarker()) {
				g2.setColor(hoveredStationIndex!=null && hoveredStationIndex.intValue()==stationIndex ? COLOR_STATION_BG_HOVERED : COLOR_STATION_BG);
				g2.fillRect(x0_, rowY, STATIONWIDTH-1, rowHeight-1);
			}
			
			if (currentStation!=null && currentStation.toIDStr().equals(station.service.stationID.toIDStr()))
				g2.setColor(COLOR_STATION_TEXT_CURRENTLY_PLAYED);
			else
				g2.setColor(hasEvents || station.isMarker() ? COLOR_STATION_TEXT : COLOR_STATION_TEXT_ISEMPTY);
			g2.drawString(station.name, x0_+stationTextOffsetX, rowY+rowTextOffsetY);
		}
	}

	private void paintTimers(final Graphics2D g2, final int x0_, final int rowY, final int rowTextOffsetY, final Rectangle eventViewClip, final HashMap<Long, Timer> timers) {
		if (timers == null || timers.isEmpty()) return;
		for (Timer timer:timers.values()) {
			boolean isHovered = hoveredEvent!=null && hoveredEvent.event.id!=null && hoveredEvent.event.id.longValue()==timer.eventID;
			if (!isHovered)
				paintTimer(g2, x0_, rowY, eventViewClip, timer, isHovered);
		}
		
	}

	private void paintTimer(final Graphics2D g2, final int x0_, final int rowY, final Rectangle eventViewClip, Timer timer, boolean isHovered) {
		int yBegin = rowY+1;
		int yEnd   = rowY+rowHeight-3;
		int xBegin = x0_ + STATIONWIDTH + Math.round( (timer.begin_s_based - rowAnchorTime_s_based)/timeScale );
		int xEnd   = x0_ + STATIONWIDTH + Math.round( (timer.  end_s_based - rowAnchorTime_s_based)/timeScale );
		Integer center = null;
		if (xBegin+3>xEnd) {
			center = (xBegin+xEnd)/2;
			xBegin = center - rowHeight/2;
			xEnd   = center + rowHeight/2;
		}
		
		Rectangle clip = new Rectangle(xBegin, yBegin, xEnd-xBegin, rowHeight-1-2).intersection(eventViewClip);
		if (!clip.isEmpty()) {
			g2.setClip(clip);
			boolean isDisabled  = timer.timer.state2 == Timers.Timer.State.Deactivated;
			boolean isRecording = timer.timer.type   == Timers.Timer.Type.Record || timer.timer.type == Timers.Timer.Type.RecordNSwitch;
			g2.setColor(isHovered ? COLOR_ITMER_HOVERED : isDisabled ? COLOR_ITMER_DISABLED : isRecording ? COLOR_ITMER_RECORDING : COLOR_ITMER_JUST_ZAP);
			if (center==null) {
				g2.drawLine(xBegin, yBegin, xEnd-1, yBegin);
				g2.drawLine(xBegin, yBegin, xBegin, yEnd);
				g2.drawLine(xBegin, yEnd  , xEnd-1, yEnd);
				g2.drawLine(xEnd-1, yBegin, xEnd-1, yEnd);
				
				int yMid = (yEnd+yBegin)/2;
				g2.drawLine(xBegin, yBegin, xBegin+rowHeight/2, yMid);
				g2.drawLine(xBegin, yEnd  , xBegin+rowHeight/2, yMid);
				g2.drawLine(xEnd-1, yBegin, xEnd-1-rowHeight/2, yMid);
				g2.drawLine(xEnd-1, yEnd  , xEnd-1-rowHeight/2, yMid);
				
			} else {
				g2.drawLine(center, yBegin, center, yEnd);
				g2.drawLine(xBegin, yBegin, xEnd-1, yEnd);
				g2.drawLine(xEnd-1, yBegin, xBegin, yEnd);
			}
			
		}
	}

	private void paintEvents(
			final Graphics2D g2, final int x0_, final int rowY, final int rowTextOffsetY, final Rectangle eventViewClip,
			final Vector<EPGViewEvent> events, HashMap<Long, Timer> timers) {
		if (events == null || events.isEmpty()) return;
		int eventTextOffsetX = 5;
		boolean isHoveredEventInThisRow = false;
		for (EPGViewEvent event:events) {
			boolean isHovered = hoveredEvent!=null && event.event==hoveredEvent.event;
			if (!isHovered)
				paintEvent(g2, x0_, rowY, rowTextOffsetY, eventViewClip, timers, eventTextOffsetX, event, isHovered);
			else
				isHoveredEventInThisRow = true;
		}
		if (isHoveredEventInThisRow && hoveredEvent!=null)
			paintEvent(g2, x0_, rowY, rowTextOffsetY, eventViewClip, timers, eventTextOffsetX, hoveredEvent, true);
	}

	private void paintEvent(
			final Graphics2D g2, final int x0_, final int rowY, final int rowTextOffsetY, final Rectangle eventViewClip,
			final HashMap<Long, Timer> timers, final int eventTextOffsetX, final EPGViewEvent event, final boolean isHovered) {
		int xBegin = x0_ + STATIONWIDTH + Math.round( (event.begin_s_based - rowAnchorTime_s_based)/timeScale );
		int xEnd   = x0_ + STATIONWIDTH + Math.round( (event.  end_s_based - rowAnchorTime_s_based)/timeScale );
		
		Rectangle borderRectClip = new Rectangle(xBegin, rowY, xEnd-xBegin-1, rowHeight-1).intersection(eventViewClip);
		if (!borderRectClip.isEmpty()) {
			g2.setClip(borderRectClip);
			g2.setColor(COLOR_EVENT_FRAME);
			g2.drawRect(xBegin, rowY, xEnd-xBegin-2, rowHeight-2);
		}
		
		Rectangle textClip = new Rectangle(xBegin+1, rowY+1, xEnd-xBegin-3, rowHeight-1-2).intersection(eventViewClip);
		if (!textClip.isEmpty()) {
			g2.setClip(textClip);
			if (isHovered) {
				g2.setColor(COLOR_EVENT_HOVERED_BG);
				g2.fillRect(xBegin+1, rowY+1, xEnd-xBegin-3, rowHeight-3);
				
				if (timers!=null) {
					Timer timer = timers.get(event.event.id);
					if (timer!=null) {
						paintTimer(g2, x0_, rowY, eventViewClip, timer, true);
						g2.setClip(textClip);
					}
				}
			}
			
			if (event.title!=null)
			{
				g2.setColor(COLOR_EVENT_TEXT);
				int textX = xBegin+1+eventTextOffsetX;
				if (textX < x0_+STATIONWIDTH+2) textX = x0_+STATIONWIDTH+2;
				g2.drawString(event.title, textX, rowY+rowTextOffsetY);
			}
		}
	}

	private void paintRepaintCounter(final Graphics2D g2, final int x0_, final int y0_) {
		repaintCounter++;
		int pos = repaintCounter & 0x1f;
		g2.setColor(Color.RED);
		g2.drawLine(x0_+pos,y0_+0, x0_+pos,y0_+1);
		
		int value = repaintCounter;
		for (int i=0; i<16; i++) {
			if ( (value&1)!=0 ) g2.drawLine(x0_+i,y0_+2, x0_+i,y0_+3);
			value >>= 1;
			if (value==0) break;
		}
	}

	private void paintTimeScale(final Graphics2D g2, final int x0_, final int y0_, final int width) {
		g2.setColor(COLOR_TIMESCALE_LINES);
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
				g2.setColor(COLOR_TIMESCALE_LINES);
				g2.drawLine(xTick, y0_, xTick, y0_+HEADERHEIGHT-1);
				g2.setColor(COLOR_TIMESCALE_TEXT);
				String str;
				if ( (scaleTicksBaseHour+iHour)%6 != 0)
					str = String.format("%02d:00", (scaleTicksBaseHour+iHour)%24);
				else {
					long millis = (scaleTicksBaseTime_s_based + iHour*3600 + baseTimeOffset_s)*1000L;
					str = getTimeScaleDateStr(g2, millis, 3600 / timeScale);
					if (str==null)
						str = String.format("%02d:00 ??", (scaleTicksBaseHour+iHour)%24);
				}
				g2.drawString(str, xTick+4, y0_+11);
				break;
			case 1: case 3:
				g2.setColor(COLOR_TIMESCALE_LINES);
				g2.drawLine(xTick, y0_+(HEADERHEIGHT*3)/4, xTick, y0_+HEADERHEIGHT-1);
				break;
			case 2:
				g2.setColor(COLOR_TIMESCALE_LINES);
				g2.drawLine(xTick, y0_+HEADERHEIGHT/2, xTick, y0_+HEADERHEIGHT-1);
				break;
			}
			iQuarter++;
			if (iQuarter==4) { iQuarter = 0; iHour++; }
			xTick = xBase + Math.round( (iHour*3600 + iQuarter*900)/timeScale );
		}
	}

	private void paintNowMarker(final Graphics2D g2, final int x0_, final int y0_, final int height) {
		g2.setColor(COLOR_NOWMARKER);
		long tNow_ms = System.currentTimeMillis();
		long tNow_s_based = tNow_ms/1000 - baseTimeOffset_s;
		if (tNow_s_based > rowAnchorTime_s_based) {
			int xNow = x0_ + STATIONWIDTH + Math.round( (tNow_s_based - rowAnchorTime_s_based)/timeScale );
			g2.drawLine(xNow, y0_+HEADERHEIGHT, xNow, y0_+height-1);
		}
	}
}