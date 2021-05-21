package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.gui.ContextMenu;
import net.schwarzbaer.gui.StandardDialog;
import net.schwarzbaer.gui.TextAreaDialog;
import net.schwarzbaer.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings.ValueKey;

public class EPGDialog extends StandardDialog {
	private static final long serialVersionUID = 8634962178940555542L;
	
	private enum LeadTime {
		_2_00h("-2:00h",7200),
		_1_30h("-1:30h",5400),
		_1_00h("-1:00h",3600),
		_45min("-45min",2700),
		_30min("-30min",1800),
		;
		private final int time_s;
		private final String label;
		private LeadTime(String label, int time_s) { this.label = label; this.time_s = time_s; }
		static LeadTime get(int time_s) {
			for (LeadTime e:values())
				if (e.time_s==time_s)
					return e;
			return null;
		}
		@Override public String toString() { return label; }
	}
	private enum RangeTime {
		_4h ("+4h" , 4*3600),
		_8h ("+8h" , 8*3600),
		_12h("+12h",12*3600),
		_24h("+24h",24*3600),
		;
		private final int time_s;
		private final String label;
		private RangeTime(String label, int time_s) { this.label = label; this.time_s = time_s; }
		static RangeTime get(int time_s) {
			for (RangeTime e:values())
				if (e.time_s==time_s)
					return e;
			return null;
		}
		@Override public String toString() { return label; }
	}
	
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
	private final Vector<SubService> stations;
	private final LoadEPGThread loadEPGThread;
	private final EPGView epgView;
	private final JScrollBar epgViewVertScrollBar;
	private final JScrollBar epgViewHorizScrollBar;
	private OpenWebifController.Updater epgViewRepainter;
	private int leadTime_s;
	private int rangeTime_s;

	public static void showDialog(Window parent, String title, String baseURL, EPG epg, Vector<SubService> subservices, ExternCommands externCommands) {
		new EPGDialog(parent, title, ModalityType.APPLICATION_MODAL, false, baseURL, epg, subservices, externCommands)
			.showDialog();
	}
	
	interface ExternCommands {
		void zapToStation(String baseURL, StationID stationID);
		void streamStation(String baseURL, StationID stationID);
	}

	public EPGDialog(Window parent, String title, ModalityType modality, boolean repeatedUseOfDialogObject, String baseURL, EPG epg, Vector<SubService> stations, ExternCommands externCommands) {
		super(parent, title, modality, repeatedUseOfDialogObject);
		this.epg = epg;
		this.stations = stations;
		
		JLabel statusOutput = new JLabel("");
		statusOutput.setBorder(BorderFactory.createLoweredSoftBevelBorder());
		
		loadEPGThread = new LoadEPGThread(baseURL, this.epg, this.stations) {
			@Override public void setStatusOutput(String text) { statusOutput.setText(text); }
			@Override public void updateEPGView            () { EPGDialog.this.updateEPGView(); }
			@Override public void reconfigureHorizScrollBar() { EPGDialog.this.reconfigureEPGViewHorizScrollBar(); }
		};
		epgView = new EPGView(this.stations);
		epgViewRepainter = new OpenWebifController.Updater(20, epgView::repaint);
		
		int rowHeight = OpenWebifController.settings.getInt(ValueKey.EPGDialog_RowHeight, -1);
		if (rowHeight<0) rowHeight = epgView.getRowHeight();
		else epgView.setRowHeight(rowHeight);
		
		JComboBox<RowHeight> cmbbxRowHeight = OpenWebifController.createComboBox(RowHeight.values(), RowHeight.get(rowHeight), val->{
			epgView.setRowHeight(val.value);
			epgView.repaint();
			reconfigureEPGViewVertScrollBar();
			OpenWebifController.settings.putInt(ValueKey.EPGDialog_RowHeight, val.value);
		});
		
		leadTime_s  = OpenWebifController.settings.getInt(ValueKey.EPGDialog_LeadTime , LeadTime._1_30h.time_s);
		rangeTime_s = OpenWebifController.settings.getInt(ValueKey.EPGDialog_RangeTime, RangeTime._4h  .time_s);
		loadEPGThread.setLeadTime(leadTime_s);
		
		JComboBox<LeadTime > cmbbxLeadTime = OpenWebifController.createComboBox(LeadTime.values(), LeadTime.get(leadTime_s), e->{
			int oldLeadTime_s = leadTime_s;
			OpenWebifController.settings.putInt(ValueKey.EPGDialog_LeadTime, leadTime_s = e.time_s);
			loadEPGThread.setLeadTime(oldLeadTime_s);
			if (!loadEPGThread.isRunning()) {
				if (oldLeadTime_s < leadTime_s) {
					SwingUtilities.invokeLater(()->{
						String dlgTitle = "Restart EPG loading thread?";
						String message = "Lead Time was raised.\r\nDo you want to restart EPG loading thread?";
						int result = JOptionPane.showConfirmDialog(this, message, dlgTitle, JOptionPane.YES_NO_CANCEL_OPTION);
						if (result==JOptionPane.YES_OPTION)
							loadEPGThread.start();
						else {
							updateEPGView();
							reconfigureEPGViewHorizScrollBar();
						}
					});
				} else {
					updateEPGView();
					reconfigureEPGViewHorizScrollBar();
				}
			}
		});
		JComboBox<RangeTime> cmbbxRangeTime = OpenWebifController.createComboBox(RangeTime.values(), RangeTime.get(rangeTime_s), e->{
			OpenWebifController.settings.putInt(ValueKey.EPGDialog_RangeTime, rangeTime_s = e.time_s);
			if (!loadEPGThread.isRunning()) {
				updateEPGView();
				reconfigureEPGViewHorizScrollBar();
			}
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
		
		StationContextMenu stationContextMenu = externCommands==null ? null : new StationContextMenu(externCommands, baseURL);
		new EPGViewMouseHandler(this,epgView,epgViewHorizScrollBar,epgViewVertScrollBar,stationContextMenu,this.stations);
		
		JButton closeButton = OpenWebifController.createButton("Close", true, e->closeDialog());
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		JPanel northPanel = new JPanel(new GridBagLayout());
		set(c,0,0,0,0); northPanel.add(new JLabel("Row Height: "),c);
		set(c,1,0,0,0); northPanel.add(cmbbxRowHeight,c);
		set(c,2,0,0,0); northPanel.add(new JLabel("  Time Scale: "),c);
		set(c,3,0,0,0); northPanel.add(timeScaleValueLabel,c);
		set(c,4,0,1,0); northPanel.add(timeScaleSlider,c);
		set(c,5,0,0,0); northPanel.add(new JLabel("  Time Range: "),c);
		set(c,6,0,0,0); northPanel.add(cmbbxLeadTime,c);
		set(c,7,0,0,0); northPanel.add(cmbbxRangeTime,c);
		
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
			@Override public void windowActivated  (WindowEvent e) {}
			@Override public void windowDeactivated(WindowEvent e) {}
			@Override public void windowOpened     (WindowEvent e) {}
			@Override public void windowIconified  (WindowEvent e) {}
			@Override public void windowDeiconified(WindowEvent e) {}
			@Override public void windowClosing    (WindowEvent e) {}
			@Override public void windowClosed     (WindowEvent e) {
				loadEPGThread.stop();
				epgViewRepainter.stop();
			}
		});
		
		reconfigureEPGViewVertScrollBar();
		reconfigureEPGViewHorizScrollBar();
		if (this.epg.isEmpty()) loadEPGThread.start();
		else updateEPGView();
		epgViewRepainter.start();
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

	public void setCurrentStation(StationID stationID) {
		epgView.setCurrentStation(stationID);
	}

	private void updateEPGView() {
		long now_ms = System.currentTimeMillis();
		long beginTime_UnixTS = now_ms/1000 - leadTime_s;
		long endTime_UnixTS   = beginTime_UnixTS + rangeTime_s;
		for (SubService station:stations) {
			if (station.isMarker()) continue;
			StationID stationID = station.service.stationID;
			Vector<EPGevent> events = epg.getEvents(stationID, beginTime_UnixTS, endTime_UnixTS, true);
			Vector<EPGViewEvent> viewEvents = epgView.convert(events);
			epgView.updateEvents(stationID,viewEvents/*,dataStatus*/);
		}
		epgView.updateMinMaxTime();
		epgView.repaint();
	}

	private static abstract class LoadEPGThread {
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
	
	private static class StationContextMenu extends ContextMenu {
		private static final long serialVersionUID = 3048130309705457643L;
		
		private StationID stationID;
		private final JMenuItem miSwitchToStation;
		private final JMenuItem miStreamStation;
		
		StationContextMenu(ExternCommands externCommands, String baseURL) {
			add(miSwitchToStation = OpenWebifController.createMenuItem("Switch to Station", e->externCommands. zapToStation(baseURL,stationID)));
			add(miStreamStation   = OpenWebifController.createMenuItem("Stream Station"   , e->externCommands.streamStation(baseURL,stationID)));
		}

		void setStationID(String stationName, StationID stationID) {
			this.stationID = stationID;
			miSwitchToStation.setText(String.format("Switch to \"%s\"", stationName));
			miStreamStation  .setText(String.format("Stream \"%s\""   , stationName));
		}
	}
	
	private static class EPGViewMouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener{

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

	private static class EPGViewEvent {
	
		private final String title;
		private final int begin_s_based;
		private final int   end_s_based;
		private final EPGevent event;

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

	private static class EPGView extends Canvas {
		private static final long serialVersionUID = 8667640106638383774L;
		
		private static final Color COLOR_STATION_BG           = Color.WHITE;
		private static final Color COLOR_STATION_BG_HOVERED   = new Color(0xE5EDFF);
		private static final Color COLOR_STATION_FRAME        = Color.GRAY;
		private static final Color COLOR_STATION_TEXT         = Color.BLACK;
		private static final Color COLOR_STATION_TEXT_ISEMPTY = Color.GRAY;
		
		private static final Color COLOR_EVENT_HOVERED_BG = Color.WHITE;
		private static final Color COLOR_EVENT_FRAME      = Color.BLACK;
		private static final Color COLOR_EVENT_TEXT       = Color.BLACK;
		
		private static final Color COLOR_TOOLTIP_BG    = new Color(0xFFFFD0);
		private static final Color COLOR_TOOLTIP_FRAME = Color.BLACK;
		private static final Color COLOR_TOOLTIP_TEXT  = Color.BLACK;
		
		private static final Color COLOR_TIMESCALE_LINES = Color.GRAY;
		private static final Color COLOR_TIMESCALE_TEXT  = Color.BLACK;
		
		private static final Color COLOR_NOWMARKER = Color.RED;
		
		private static final int HEADERHEIGHT = 20;
		private static final int STATIONWIDTH = 100;




		
		
		private final Calendar calendar;
		private final long baseTimeOffset_s;
		private final HashMap<String, Vector<EPGViewEvent>> events;
		private final Vector<SubService> stations;
		private int rowHeight;
		private int rowOffsetY;
		private int rowAnchorTime_s_based;
		private int minTime_s_based;
		private int maxTime_s_based;
		private float timeScale;
		private int scaleTicksBaseHour;
		private int scaleTicksBaseTime_s_based;
		private EPGViewEvent hoveredEvent;
		private int repaintCounter;
		private BufferedImage toolTip;
		private Point toolTipPos;
		private StationID currentStation;
		private Integer hoveredStationIndex;
		
		EPGView(Vector<SubService> stations) {
			this.stations = stations;
			repaintCounter = 0;
			
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
			hoveredEvent = null;
			toolTip = null;
			toolTipPos = null;
			currentStation = null;
			hoveredStationIndex = null;
			
			setBorder(BorderFactory.createLineBorder(Color.GRAY));
		}

		public void setHoveredStation(Integer hoveredStationIndex) {
			this.hoveredStationIndex = hoveredStationIndex;
		}

		public void setCurrentStation(StationID stationID) {
			currentStation = stationID;
		}

		public void showToolTip(Point point) {
			if (hoveredEvent!=null)
				toolTip = createToolTip(hoveredEvent);
			toolTipPos = new Point(point);
		}

		public boolean updateToolTip(Point point) {
			boolean posChanged = toolTipPos==null || !toolTipPos.equals(point);
			toolTipPos = new Point(point);
			return toolTip!=null && posChanged;
		}

		public void hideToolTip() {
			toolTip=null;
		}

		private BufferedImage createToolTip(EPGViewEvent event) {
			Graphics2D g2;
			BufferedImage image;
			Font stdFont, boldFont;
			
			image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
			g2 = image.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			stdFont = g2.getFont();
			boldFont = stdFont.deriveFont(Font.BOLD);
			
			String begin = event.event.begin;
			String end   = event.event.end;
			if (begin==null) begin = OpenWebifController.dateTimeFormatter.getTimeStr( event.event.begin_timestamp                          *1000, false, false, false, true, false);
			if (end  ==null) end   = OpenWebifController.dateTimeFormatter.getTimeStr((event.event.begin_timestamp+event.event.duration_sec)*1000, false, false, false, true, false);
			String timeRange = String.format("%s - %s", begin, end);
			String title     = event.event.title;
			String shortdesc = event.event.shortdesc;
			if (shortdesc!=null && (shortdesc.isEmpty() || shortdesc.equals(title)))
				shortdesc = null;
			
			float  stdFontSize =  stdFont.getSize()*1.2f;
			float boldFontSize = boldFont.getSize()*1.2f;
			
			Rectangle2D timeRangeBounds =  stdFont.getStringBounds(timeRange, g2.getFontRenderContext());
			Rectangle2D titleBounds     = boldFont.getStringBounds(title, g2.getFontRenderContext());
			Rectangle2D shortdescBounds = shortdesc==null ? null : stdFont.getStringBounds(shortdesc, g2.getFontRenderContext());
			
			int borderSpacing = 5;
			int imgWidth  = 2*borderSpacing + (int) Math.ceil( Math.max( Math.max( timeRangeBounds.getWidth(), titleBounds.getWidth() ), shortdescBounds==null ? 0 : shortdescBounds.getWidth() ) );
			int imgHeight = 2*borderSpacing + (int) Math.ceil( stdFontSize + boldFontSize+(shortdescBounds==null ? 0 : stdFontSize) );
			int[] baselineOffset = new int[] {
				borderSpacing + Math.round(  stdFontSize*0.75f ),
				borderSpacing + Math.round( boldFontSize*0.75f + stdFontSize),
				borderSpacing + Math.round(  stdFontSize*0.75f + stdFontSize + boldFontSize),
			};
			
			image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
			g2 = image.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			stdFont = g2.getFont();
			boldFont = stdFont.deriveFont(Font.BOLD);
			
			g2.setColor(COLOR_TOOLTIP_BG);
			g2.fillRect(0, 0, imgWidth, imgHeight);
			g2.setColor(COLOR_TOOLTIP_FRAME);
			g2.drawRect(0, 0, imgWidth-1, imgHeight-1);
			//g2.drawString("ToolTip Dummy", 10, 20);
			
			g2.setColor(COLOR_TOOLTIP_TEXT);
			g2.setFont(stdFont);
			g2.drawString(timeRange, borderSpacing, baselineOffset[0]);
			g2.setFont(boldFont);
			g2.drawString(title, borderSpacing, baselineOffset[1]);
			if (shortdesc!=null) {
				g2.setFont(stdFont);
				g2.drawString(shortdesc, borderSpacing, baselineOffset[2]);
			}
			
			return image;
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

		Vector<EPGViewEvent> convert(Vector<EPGevent> events) {
			if (events==null || events.isEmpty()) return null;
			Vector<EPGViewEvent> result = new Vector<>(events.size());
			for (EPGevent event:events) {
				int begin_s_based = (int) (event.begin_timestamp - baseTimeOffset_s);
				int   end_s_based = (int) (begin_s_based + event.duration_sec);
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
			int now = (int) (System.currentTimeMillis() / 1000 - baseTimeOffset_s);
			int min = now;
			int max = now;
			for (Vector<EPGViewEvent> eventList:events.values()) {
				for (EPGViewEvent event:eventList) {
					min = Math.min(min, event.begin_s_based);
					max = Math.max(max, event.  end_s_based);
				}
			}
			minTime_s_based = min;
			maxTime_s_based = max;
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
			
			for (EPGViewEvent event:stationEvents)
				if (event.covers(time_s_based))
					return event;
			
			return null;
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
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			
			paintRepaintCounter(g2, x0_, y0_);
			paintTimeScale     (g2, x0_, y0_, width);
			paintMainView      (g2, x0_, y0_, width, height);
			
			g2.setClip(oldClip);
			
			paintNowMarker(g2, x0_, y0_,        height);
			paintToolTip  (g2, x0_, y0_, width, height);
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
					if ( (scaleTicksBaseHour+iHour)%24 != 0)
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

		private void paintMainView(final Graphics2D g2, final int x0_, final int y0_, final int width, final int height) {
			int fontHeight = 8; // default font size: 11  -->  fontHeight == 8
			int rowTextOffsetY = (rowHeight-1-fontHeight)/2+fontHeight; 
			
			int y0 = y0_+HEADERHEIGHT-rowOffsetY;
			Rectangle mainClip      = new Rectangle(x0_,              y0_+HEADERHEIGHT, width,              height-HEADERHEIGHT);
			Rectangle eventViewClip = new Rectangle(x0_+STATIONWIDTH, y0_+HEADERHEIGHT, width-STATIONWIDTH, height-HEADERHEIGHT);
			
			for (int i=0; i<stations.size(); i++) {
				SubService station = stations.get(i);
				Vector<EPGViewEvent> events = station.isMarker() ? null : getEvents(station.service.stationID);
				
				int rowY = y0+rowHeight*i;
				paintStation(g2, x0_, rowY, rowTextOffsetY, mainClip, station, i, events!=null);
				paintEvents (g2, x0_, rowY, rowTextOffsetY, eventViewClip, events);
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
					g2.setColor(BouquetsNStations.BSTreeCellRenderer.TEXTCOLOR_CURRENTLY_PLAYED);
				else
					g2.setColor(hasEvents || station.isMarker() ? COLOR_STATION_TEXT : COLOR_STATION_TEXT_ISEMPTY);
				g2.drawString(station.name, x0_+stationTextOffsetX, rowY+rowTextOffsetY);
			}
		}

		private void paintEvents(final Graphics2D g2, final int x0_, final int rowY, final int rowTextOffsetY, final Rectangle eventViewClip, final Vector<EPGViewEvent> events) {
			int eventTextOffsetX = 5;
			if (events!=null && !events.isEmpty())
				for (EPGViewEvent event:events) {
					int xBegin = x0_ + STATIONWIDTH + Math.round( (event.begin_s_based - rowAnchorTime_s_based)/timeScale );
					int xEnd   = x0_ + STATIONWIDTH + Math.round( (event.  end_s_based - rowAnchorTime_s_based)/timeScale );
					
					Rectangle eventCellClip = new Rectangle(xBegin, rowY, xEnd-xBegin-1, rowHeight-1).intersection(eventViewClip);
					if (!eventCellClip.isEmpty()) {
						g2.setClip(eventCellClip);
						g2.setColor(COLOR_EVENT_FRAME);
						g2.drawRect(xBegin, rowY, xEnd-xBegin-2, rowHeight-2);
					}
					
					Rectangle eventTextClip = new Rectangle(xBegin+1, rowY+1, xEnd-xBegin-3, rowHeight-1-2).intersection(eventViewClip);
					if (!eventTextClip.isEmpty()) {
						g2.setClip(eventTextClip);
						if (hoveredEvent!=null && event.event==hoveredEvent.event) {
							g2.setColor(COLOR_EVENT_HOVERED_BG);
							g2.fillRect(xBegin+1, rowY+1, xEnd-xBegin-3, rowHeight-3);
						}
						
						g2.setColor(COLOR_EVENT_TEXT);
						int textX = xBegin+1+eventTextOffsetX;
						if (textX < x0_+STATIONWIDTH+2) textX = x0_+STATIONWIDTH+2;
						g2.drawString(event.title, textX, rowY+rowTextOffsetY);
					}
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

		private void paintToolTip(final Graphics2D g2, final int x0_, final int y0_, final int width, final int height) {
			if (toolTip!=null && toolTipPos!=null) {
				int toolTipWidth  = toolTip.getWidth();
				int toolTipHeight = toolTip.getHeight();
				int distToPosX = 15;
				int distToPosY = 10;
				int distToBorder = 10;
				int imgX;
				int imgY;
				
				if (toolTipPos.x+distToPosX+toolTipWidth+distToBorder < x0_+width)
					imgX = toolTipPos.x+distToPosX; // right of pos
				else if (x0_ < toolTipPos.x-distToPosX-toolTipWidth-distToBorder)
					imgX = toolTipPos.x-distToPosX-toolTipWidth; // left of pos
				else if (width < toolTipWidth)
					imgX = x0_;
				else if (width < toolTipWidth+2*distToBorder)
					imgX = (width-toolTipWidth)/2; // centered
				else if (toolTipPos.x < x0_+width/2)
					imgX = x0_+width - distToBorder - toolTipWidth; // on right border
				else
					imgX = x0_+distToBorder; // on left border
				
				if (toolTipPos.y+distToPosY+toolTipHeight+distToBorder < y0_+height)
					imgY = toolTipPos.y+distToPosY; // right of pos
				else if (y0_ < toolTipPos.y-distToPosY-toolTipHeight-distToBorder)
					imgY = toolTipPos.y-distToPosY-toolTipHeight; // left of pos
				else if (height < toolTipHeight )
					imgY = y0_;
				else if (height < toolTipHeight+2*distToBorder)
					imgY = (height-toolTipHeight)/2; // centered
				else if (toolTipPos.y < y0_+height/2)
					imgY = y0_+height - distToBorder - toolTipHeight; // on right border
				else
					imgY = y0_+distToBorder; // on left border
				
				g2.drawImage(toolTip, imgX, imgY, null);
			}
		}
		
	}
}
