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

import net.schwarzbaer.gui.TextAreaDialog;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGDialog.StationContextMenu;

class EPGViewMouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener{

	private final Window parent;
	private final EPGView epgView;
	private final JScrollBar epgViewHorizScrollBar;
	private final JScrollBar epgViewVertScrollBar;
	private final StationContextMenu stationContextMenu;
	private final Vector<SubService> stations;
	
	private Integer hoveredRowIndex;
	private EPGViewEvent hoveredEvent;
	private boolean isStationHovered;

	public EPGViewMouseHandler(Window parent, EPGView epgView, JScrollBar epgViewHorizScrollBar, JScrollBar epgViewVertScrollBar, StationContextMenu stationContextMenu, Vector<SubService> stations) {
		this.parent = parent;
		this.epgView = epgView;
		this.epgViewHorizScrollBar = epgViewHorizScrollBar;
		this.epgViewVertScrollBar = epgViewVertScrollBar;
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
			epgView.hideToolTip();
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
					repaintEPGView |= setHoveredEvent(dataPos.time_s_based);
				
			} else {
				
				if (!dataPos.isStationSelected)
					repaintEPGView |= clearHoveredStation();
				else if (isStationHovered)
					repaintEPGView |= epgView.updateToolTip(point);
				else
					repaintEPGView |= setHoveredStation(dataPos);
				
				if (dataPos.time_s_based==null)
					repaintEPGView |= clearHoveredEvent();
				else if (hoveredEvent!=null && hoveredEvent.covers(dataPos.time_s_based))
					repaintEPGView |= epgView.updateToolTip(point);
				else
					repaintEPGView |= setHoveredEvent(dataPos.time_s_based);
			}
			
			if (repaintEPGView)
				epgView.repaint();
		}
	}

	private boolean setHoveredStation(EPGView.DataPos dataPos) {
		//System.out.printf("setHoveredStation(%d) [isStationHovered:%s, dataPos:%s]%n", hoveredRowIndex, isStationHovered, dataPos);
		epgView.setHoveredStation(hoveredRowIndex);
		isStationHovered = true;
		if (stationContextMenu!=null) {
			SubService station = stations.get(hoveredRowIndex);
			if (!station.isMarker()) {
				stationContextMenu.setStationID(station.name, station.service.stationID);
				stationContextMenu.setVisible(false);
			}
		}
		return true;
	}

	private boolean setHoveredEvent(Integer time_s_based) {
		//System.out.printf("setHoveredEvent(%d,%d)%n", hoveredRowIndex,time_s_based);
		EPGViewEvent newHoveredEvent = epgView.getEvent(hoveredRowIndex,time_s_based);
		if (hoveredEvent!=null || newHoveredEvent!=null) {
			hoveredEvent = newHoveredEvent;
			//System.out.printf("HoveredEvent: [%d] %s%n", hoveredRowIndex, hoveredEvent);
			epgView.setHoveredEvent(hoveredEvent);
			epgView.hideToolTip();
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
				ValueListOutput out = new ValueListOutput();
				OpenWebifController.generateOutput(out, 0, hoveredEvent.event);
				String text = out.generateOutput();
				TextAreaDialog.showText(parent, hoveredEvent.title, 700, 500, true, text);
			}
			if (isStationHovered) {
				showStationContextMenu(e);
			}
			break;
			
		case MouseEvent.BUTTON3:
			if (hoveredEvent!=null) {
				epgView.showToolTip(e.getPoint());
				epgView.repaint();
			}
			if (isStationHovered) {
				showStationContextMenu(e);
			}
			break;
		
		}
	}

	private void showStationContextMenu(MouseEvent e) {
		if (stationContextMenu == null) return;
		if (hoveredRowIndex == null) return;
		SubService station = stations.get(hoveredRowIndex);
		if (!station.isMarker())
			stationContextMenu.show(epgView, e.getX(), e.getY());
	}
	
	@Override public void mouseExited (MouseEvent e) { clearHoveredItem(); }
	@Override public void mouseEntered(MouseEvent e) { updateHoveredItem(e.getPoint()); }
	
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