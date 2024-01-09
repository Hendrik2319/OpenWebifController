package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Vector;

import javax.swing.JScrollBar;

import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGDialog.EventContextMenu;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGDialog.StationContextMenu;

class EPGViewMouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener{

	private final Window parent;
	private final EPGView epgView;
	private final JScrollBar epgViewHorizScrollBar;
	private final JScrollBar epgViewVertScrollBar;
	private final EventContextMenu eventContextMenu;
	private final StationContextMenu stationContextMenu;
	private final Vector<SubService> stations;
	
	private Integer hoveredRowIndex;
	private EPGView.EPGViewEvent hoveredEvent;
	private boolean isStationHovered;

	public EPGViewMouseHandler(Window parent, EPGView epgView, JScrollBar epgViewHorizScrollBar, JScrollBar epgViewVertScrollBar, EventContextMenu eventContextMenu, StationContextMenu stationContextMenu, Vector<SubService> stations) {
		this.parent = parent;
		this.epgView = epgView;
		this.epgViewHorizScrollBar = epgViewHorizScrollBar;
		this.epgViewVertScrollBar = epgViewVertScrollBar;
		this.eventContextMenu = eventContextMenu;
		this.stationContextMenu = stationContextMenu;
		this.stations = stations;
		this.epgView.addMouseListener(this);
		this.epgView.addMouseMotionListener(this);
		this.epgView.addMouseWheelListener(this);
		hoveredRowIndex = null;
		hoveredEvent = null;
		isStationHovered = false;
	}
	
	private void clearHoveredItem() {
		boolean repaintEPGView = false;
		hoveredRowIndex = null;
		repaintEPGView |= clearHoveredStation();
		repaintEPGView |= clearHoveredEvent();
		if (repaintEPGView)
			epgView.repaint();
	}

	private boolean clearHoveredStation() {
		if (isStationHovered) {
			epgView.setHoveredStation(null);
			isStationHovered = false;
			return true;
		}
		return false;
	}

	private boolean clearHoveredEvent() {
		if (hoveredEvent!=null) {
			hoveredEvent = null;
			//System.out.printf("HoveredEvent: [%d] %s%n", hoveredRowIndex, hoveredEvent);
			epgView.setHoveredEvent(hoveredEvent);
			epgView.toolTip.hide();
			return true;
		}
		return false;
	}

	private void updateHoveredItem(Point point) {
		EPGView.DataPos dataPos = epgView.getDataPos(point);
		if (dataPos==null || dataPos.rowIndex==null || (dataPos.time_s_based==null && !dataPos.isStationSelected)) {
			clearHoveredItem();
			
		} else {
			boolean repaintEPGView = false;
			
			if (hoveredRowIndex==null || !hoveredRowIndex.equals(dataPos.rowIndex)) {
				//System.out.printf("updateHoveredItem(...) HoveredRow changed: %d -> %d%n", hoveredRowIndex, dataPos.rowIndex);
				hoveredRowIndex = dataPos.rowIndex;
				
				if (!dataPos.isStationSelected)
					repaintEPGView |= clearHoveredStation();
				else
					repaintEPGView |= setHoveredStation(dataPos);
				
				if (dataPos.time_s_based==null)
					repaintEPGView |= clearHoveredEvent();
				else
					repaintEPGView |= setHoveredEvent(dataPos.time_s_based, point);
				
			} else {
				
				if (!dataPos.isStationSelected)
					repaintEPGView |= clearHoveredStation();
				else if (isStationHovered)
					repaintEPGView |= epgView.toolTip.updatePosition(point);
				else
					repaintEPGView |= setHoveredStation(dataPos);
				
				if (dataPos.time_s_based==null)
					repaintEPGView |= clearHoveredEvent();
				else if (hoveredEvent!=null && hoveredEvent.covers(dataPos.time_s_based))
					repaintEPGView |= epgView.toolTip.updatePosition(point);
				else
					repaintEPGView |= setHoveredEvent(dataPos.time_s_based, point);
			}
			
			if (repaintEPGView)
				epgView.repaint();
		}
	}

	private void showEventContextMenu(MouseEvent e) {
		if (eventContextMenu == null) return;
		if (hoveredEvent == null) return;
		eventContextMenu.setEvent(hoveredEvent);
		eventContextMenu.show(epgView, e.getX(), e.getY());
	}

	private void showStationContextMenu(MouseEvent e) {
		if (stationContextMenu == null) return;
		if (hoveredRowIndex == null) return;
		SubService station = stations.get(hoveredRowIndex);
		if (!station.isMarker()) {
			stationContextMenu.setStationID(station.name, station.service.stationID);
			stationContextMenu.show(epgView, e.getX(), e.getY());
		}
	}

	private boolean setHoveredStation(EPGView.DataPos dataPos) {
		//System.out.printf("setHoveredStation(%d) [isStationHovered:%s, dataPos:%s]%n", hoveredRowIndex, isStationHovered, dataPos);
		epgView.setHoveredStation(hoveredRowIndex);
		isStationHovered = true;
		return true;
	}

	private boolean setHoveredEvent(Integer time_s_based, Point point) {
		//System.out.printf("setHoveredEvent(%d,%d)%n", hoveredRowIndex,time_s_based);
		EPGView.EPGViewEvent newHoveredEvent = epgView.getEvent(hoveredRowIndex,time_s_based);
		if (hoveredEvent!=null || newHoveredEvent!=null) {
			hoveredEvent = newHoveredEvent;
			//System.out.printf("HoveredEvent: [%d] %s%n", hoveredRowIndex, hoveredEvent);
			epgView.setHoveredEvent(hoveredEvent);
			epgView.toolTip.updateContent(point, hoveredEvent);
			return true;
		}
		return false;
	}

	@Override public void mouseReleased(MouseEvent e) {}
	@Override public void mousePressed (MouseEvent e) {}
	@Override public void mouseClicked (MouseEvent e) {
		switch (e.getButton()) {
		case MouseEvent.BUTTON1:
			if (hoveredEvent!=null) {
				String text;
				EPGView.Timer timer = epgView.getTimer(hoveredEvent.event.sref, hoveredEvent.event.id);
				if (timer!=null)
				{
					ValueListOutput out = new ValueListOutput();
					out.add(0, "EPG Event");
					OpenWebifController.generateOutput(out, 1, hoveredEvent.event);
					out.add(0, "Timer");
					TimersPanel.generateDetailsOutput(out, 1, timer.timer);
					text = out.generateOutput();
					text += TimersPanel.generateShortInfo(ValueListOutput.DEFAULT_INDENT, timer.timer, false);
				}
				else
				{
					ValueListOutput out = new ValueListOutput();
					OpenWebifController.generateOutput(out, 0, hoveredEvent.event);
					text = out.generateOutput();
				}
				TextAreaDialog.showText(parent, hoveredEvent.title, 700, 500, true, text);
			}
			break;
			
		case MouseEvent.BUTTON3:
			if (hoveredEvent!=null) {
				showEventContextMenu(e);
			}
			if (isStationHovered) {
				showStationContextMenu(e);
			}
			break;
		
		}
	}

	@Override public void mouseExited (MouseEvent e) { /*System.out.printf("mouseExited : %d, %d%n", e.getX(), e.getY());*/ clearHoveredItem(); epgView.toolTip.deactivate(); }
	@Override public void mouseEntered(MouseEvent e) { /*System.out.printf("mouseEntered: %d, %d%n", e.getX(), e.getY());*/ updateHoveredItem(e.getPoint()); epgView.toolTip.activate(); }
	
	@Override public void mouseDragged(MouseEvent e) { updateHoveredItem(e.getPoint()); }
	@Override public void mouseMoved  (MouseEvent e) { updateHoveredItem(e.getPoint()); }

	@Override public void mouseWheelMoved(MouseWheelEvent e) {
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
		
		clearHoveredStation();
		clearHoveredEvent();
		
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
		
		updateHoveredItem(e.getPoint());
	}
}