package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
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

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.TimeInput;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.Bouquet.SubService;
import net.schwarzbaer.java.lib.openwebif.EPG;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerDataUpdateNotifier;
import net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations.BouquetsNStations.BouquetsNStationsUpdateNotifier;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGView.EPGViewEvent;

public class EPGDialog extends StandardDialog implements TimerDataUpdateNotifier.DataUpdateListener, BouquetsNStationsUpdateNotifier.BouquetsNStationsListener {
	private static final long serialVersionUID = 8634962178940555542L;
	
	private enum LeadTime {
		_4_00h("-4:00h",(4*60+ 0)*60),
		_2_00h("-2:00h",(2*60+ 0)*60),
		_1_30h("-1:30h",(1*60+30)*60),
		_1_00h("-1:00h",(1*60+ 0)*60),
		_45min("-45min",(0*60+45)*60),
		_30min("-30min",(0*60+30)*60),
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
		_36h("+36h",36*3600),
		_48h("+48h",48*3600),
		all ("all" ,-1),
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
		
		static RangeTime getMax() { return _48h; }
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
	private final Bouquet bouquet;
	private final Vector<SubService> stations;
	private final LoadEPGThread loadEPGThread;
	private final EPGView epgView;
	private final JScrollBar epgViewVertScrollBar;
	private final JScrollBar epgViewHorizScrollBar;
	private final OpenWebifController.Updater epgViewRepainter;
	private int leadTime_s;
	private int rangeTime_s;
	private final TimeInputDialog epgTimeDialog;
	private ZonedDateTime epgFocusTime;
	private boolean saveSuggestedEPGViewFocus;

	public static void showDialog(
			Window parent, String baseURL, EPG epg, Bouquet bouquet,
			TimerDataUpdateNotifier timerNotifier,
			BouquetsNStationsUpdateNotifier bouquetsNStationsNotifier,
			ExternCommands externCommands,
			Component... additionalButtons)
	{
		if (parent                   ==null) throw new IllegalArgumentException();
		if (baseURL                  ==null) throw new IllegalArgumentException();
		if (epg                      ==null) throw new IllegalArgumentException();
		if (timerNotifier            ==null) throw new IllegalArgumentException();
		if (bouquet                  ==null) throw new IllegalArgumentException();
		if (bouquetsNStationsNotifier==null) throw new IllegalArgumentException();
		if (externCommands           ==null) throw new IllegalArgumentException();
		new EPGDialog(
				parent, ModalityType.APPLICATION_MODAL, false, baseURL,
				epg, timerNotifier.getTimers(), bouquet, bouquetsNStationsNotifier.getCurrentStation(),
				externCommands, additionalButtons
			).showDialog(timerNotifier, bouquetsNStationsNotifier);
	}
	
	public interface StationCommands {
		void zapToStation (String baseURL, StationID stationID);
		void streamStation(String baseURL, StationID stationID);
	}
	
	public interface TimerCommands {
		void addTimer     (String baseURL, String sRef, int eventID, Timers.Timer.Type type);
		void deleteTimer  (String baseURL, Timers.Timer timer);
		void toggleTimer  (String baseURL, Timers.Timer timer);
	}
	
	public interface ExternCommands extends StationCommands, TimerCommands {
	}

	private EPGDialog(
			Window parent, ModalityType modality, boolean repeatedUseOfDialogObject,
			String baseURL, EPG epg, Timers timerData, Bouquet bouquet, StationID currentStation,
			 ExternCommands externCommands, Component... additionalButtons
	) {
		super(parent, getTitle(bouquet), modality, repeatedUseOfDialogObject);
		this.epg = epg;
		this.bouquet = bouquet;
		this.stations = this.bouquet.subservices;
		
		epgTimeDialog = new TimeInputDialog(this,ModalityType.APPLICATION_MODAL,true,"EPG Focus", "Please enter date and time where EPG should focus on. ");
		epgFocusTime = epgTimeDialog.getNow();
		
		JLabel statusOutput = new JLabel("");
		statusOutput.setBorder(BorderFactory.createLoweredSoftBevelBorder());
		
		//loadEPGThread = new LoadEPGThread(baseURL, this.epg, this.stations) {
		loadEPGThread = new LoadEPGThread(baseURL, this.epg, this.bouquet) {
			@Override public void setStatusOutput(String text) { statusOutput.setText(text); }
			@Override public void updateEPGView            () { EPGDialog.this.updateEPGView(); }
			@Override public void reconfigureHorizScrollBar() { EPGDialog.this.reconfigureEPGViewHorizScrollBar(); }
		};
		epgView = new EPGView(this.stations, getEpgFocusTime_ms()/1000);
		epgViewRepainter = new OpenWebifController.Updater(20, epgView::repaint);
		timersWereUpdated(timerData, false);
		
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
		loadEPGThread.setLeadTime (leadTime_s);
		loadEPGThread.setRangeTime(rangeTime_s<0 ? RangeTime.getMax().time_s : rangeTime_s);
		
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
							loadEPGThread.start(getEpgFocusTime_ms());
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
			loadEPGThread.setRangeTime(rangeTime_s<0 ? RangeTime.getMax().time_s : rangeTime_s);
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
		
		epgViewVertScrollBar.addAdjustmentListener(e -> {
			epgView.setRowOffsetY_px(e.getValue());
		});
		//epgViewVertScrollBar.setValues(epgView.getRowOffsetY(), epgView.getRowViewHeight(), 0, epgView.getContentHeight());
		
		saveSuggestedEPGViewFocus = false;
		epgViewHorizScrollBar.addAdjustmentListener(e -> {
			if (!saveSuggestedEPGViewFocus) epgView.clearSuggestedFocus();
			epgView.setRowAnchorTime_s(e.getValue());
		});
		//epgViewHorizScrollBar.setValues(epgView.getRowOffsetY(), epgView.getRowViewHeight(), 0, epgView.getContentHeight());
		
		StationContextMenu stationContextMenu = externCommands==null ? null : new StationContextMenu(externCommands, baseURL);
		EventContextMenu     eventContextMenu = externCommands==null ? null : new   EventContextMenu(externCommands, baseURL, epgView);
		new EPGViewMouseHandler(this, epgView, epgViewHorizScrollBar, epgViewVertScrollBar, eventContextMenu, stationContextMenu, this.stations);
		
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
		int x=0;
		set(c,x++,0,1,0); southPanel.add(statusOutput,c);
		set(c,x++,0,0,0); southPanel.add(loadEPGThread.getButton(),c);
		/*
		set(c,x++,0,0,0); southPanel.add(OpenWebifController.createButton("Show EPG Status", true, e->{
			epg.showStatus(System.out);
			epgView.showStatus(System.out);
		}),c);
		*/
		set(c,x++,0,0,0); southPanel.add(OpenWebifController.createButton("Show EPG Event Genres", true, e->{
			new EPGEventGenresView(this, "EPG Event Genres").showDialog();
		}),c);
		set(c,x++,0,0,0); southPanel.add(OpenWebifController.createButton("Jump to time", true, e->{
			ZonedDateTime newValue = epgTimeDialog.showDialog(epgFocusTime);
			if (newValue!=null)
			{
				loadEPGThread.stop();
				epgFocusTime = newValue;
				long epgFocusTime_ms = getEpgFocusTime_ms();
				epgView.suggestFocus(epgFocusTime_ms/1000);
				loadEPGThread.execWhenEnded(()->{
					loadEPGThread.start(epgFocusTime_ms);
				});
			}
		}),c);
		
		for (int i=0; i<additionalButtons.length; i++)
			{ set(c,x+i,0,0,0); southPanel.add(additionalButtons[i],c); }
		set(c,x+additionalButtons.length,0,0,0); southPanel.add(closeButton,c);
		
		
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
		
		if (this.epg.isEmpty()) loadEPGThread.start(getEpgFocusTime_ms());
		else updateEPGView();
		reconfigureEPGViewVertScrollBar();
		reconfigureEPGViewHorizScrollBar();
		epgViewRepainter.start();
		
		setCurrentStation(currentStation);
	}

	private long getEpgFocusTime_ms()
	{
		return epgFocusTime.getLong(ChronoField.INSTANT_SECONDS)*1000;
	}

	private static String getTitle(Bouquet bouquet) {
		if (bouquet==null) return "EPG";
		return String.format("EPG for \"%s\"", bouquet.name);
	}

	private void reconfigureEPGViewVertScrollBar() {
		int value   = epgView.getRowOffsetY_px();
		int visible = epgView.getRowViewHeight_px();
		int minimum = 0;
		int maximum = epgView.getContentHeight_px();
		reconfigureScrollBar(epgViewVertScrollBar, epgView::setRowOffsetY_px, value, null, visible, minimum, maximum);
	}

	private void reconfigureEPGViewHorizScrollBar() {
		Integer suggestedValue = epgView.getRowAnchorTime_s_based_suggested();
		int value   = epgView.getRowAnchorTime_s();
		int visible = epgView.getRowViewWidth_s();
		int minimum = epgView.getMinTime_s();
		int maximum = epgView.getMaxTime_s();
		saveSuggestedEPGViewFocus = true;
		reconfigureScrollBar(epgViewHorizScrollBar, epgView::setRowAnchorTime_s, value, suggestedValue, visible, minimum, maximum);
		saveSuggestedEPGViewFocus = false;
	}

	private void reconfigureScrollBar(JScrollBar scrollBar, Consumer<Integer> setValue, int currentValue, Integer suggestedValue, int visible, int minimum, int maximum) {
		int value = suggestedValue!=null ? suggestedValue : currentValue;
		if (value+visible>maximum) {
			value = maximum-visible;
			if (value<minimum) {
				value = minimum;
				visible = maximum-minimum;
				scrollBar.setEnabled(false);
			} else
				scrollBar.setEnabled(true);
			
		} else if (value<minimum) {
			value = minimum;
			if (value+visible>maximum) {
				visible = maximum-minimum;
				scrollBar.setEnabled(false);
			} else
				scrollBar.setEnabled(true);
			
		} else
			scrollBar.setEnabled(true);
		
		if (value != currentValue)
			setValue.accept(value);
		
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

	private void showDialog(TimerDataUpdateNotifier timersNotifier, BouquetsNStationsUpdateNotifier bouquetsNStationsNotifier) {
		if (OpenWebifController.settings.contains(ValueKey.EPGDialogWidth) &&
			OpenWebifController.settings.contains(ValueKey.EPGDialogHeight))
		{
			Dimension size = OpenWebifController.settings.getDimension(ValueKey.EPGDialogWidth,ValueKey.EPGDialogHeight);
			setPositionAndSize(null, size);
		}
		
		if (timersNotifier           !=null) timersNotifier           .addListener(this);
		if (bouquetsNStationsNotifier!=null) bouquetsNStationsNotifier.addListener(this);
		
		super.showDialog();
		
		if (timersNotifier           !=null) timersNotifier           .removeListener(this);
		if (bouquetsNStationsNotifier!=null) bouquetsNStationsNotifier.removeListener(this);
	}

	@Override
	public void setCurrentStation(StationID stationID) {
		epgView.setCurrentStation(stationID);
	}

	private void updateEPGView() {
		long epgFocusTime_ms = getEpgFocusTime_ms();
		long beginTime_UnixTS = epgFocusTime_ms/1000 - leadTime_s;
		Long endTime_UnixTS   = rangeTime_s<0 ? null : (beginTime_UnixTS + rangeTime_s);
		for (SubService station:stations) {
			if (station.isMarker()) continue;
			StationID stationID = station.service.stationID;
			Vector<EPGevent> events = epg.getEvents(stationID, beginTime_UnixTS, endTime_UnixTS, true);
			Vector<EPGViewEvent> viewEvents = epgView.convertEvents(events);
			epgView.updateEvents(stationID,viewEvents/*,dataStatus*/);
		}
		epgView.updateMinMaxTime();
		epgView.repaint();
	}

	@Override
	public void timersWereUpdated(Timers timers) {
		timersWereUpdated(timers, true);
	}
	
	private void timersWereUpdated(Timers timers, boolean repaintEPGView) {
		Vector<EPGView.Timer> convertedTimers = epgView.convertTimers(timers.timers);
		epgView.setTimers(convertedTimers);
		if (repaintEPGView) epgView.repaint();
	}

	static class EventContextMenu extends ContextMenu {
		private static final long serialVersionUID = -8844749957308284885L;
		
		private final TimerCommands externCommands;
		private final String baseURL;
		private final EPGView epgView;
		
		private final JMenuItem miAddRecordTimer;
		private final JMenuItem miAddSwitchTimer;
		private final JMenuItem miAddRecordNSwitchTimer;
		private final JMenuItem miToggleTimer;
		private final JMenuItem miDeleteTimer;
		
		private EPGViewEvent event;
		private EPGView.Timer timer;

		EventContextMenu(TimerCommands externCommands, String baseURL, EPGView epgView) {
			this.externCommands = externCommands;
			this.baseURL = baseURL;
			this.epgView = epgView;
			event = null;
			timer = null;
			add(miAddRecordTimer        = OpenWebifController.createMenuItem("Add Record Timer"         , e->addTimer(Timers.Timer.Type.Record)));
			add(miAddSwitchTimer        = OpenWebifController.createMenuItem("Add Switch Timer"         , e->addTimer(Timers.Timer.Type.Switch)));
			add(miAddRecordNSwitchTimer = OpenWebifController.createMenuItem("Add Record'N'Switch Timer", e->addTimer(Timers.Timer.Type.RecordNSwitch)));
			add(miToggleTimer        = OpenWebifController.createMenuItem("Toggle Timer"                                   , e->toggleTimer()));
			add(miDeleteTimer        = OpenWebifController.createMenuItem("Delete Timer", GrayCommandIcons.IconGroup.Delete, e->deleteTimer()));
		}
		
		private void addTimer(Timers.Timer.Type type) { externCommands.addTimer   (baseURL, event.event.sref, event.event.id.intValue(), type); }
		private void deleteTimer()                    { externCommands.deleteTimer(baseURL, timer.timer); }
		private void toggleTimer()                    { externCommands.toggleTimer(baseURL, timer.timer); }

		public void setEvent(EPGViewEvent event) {
			this.event = event;
			boolean isEventOK = this.event!=null &&
				this.event.event!=null &&
				this.event.event.sref!=null &&
				this.event.event.id!=null;
			if (isEventOK)
				timer = epgView.getTimer(this.event.event.sref, this.event.event.id);
			else
				timer = null;
			
			miAddRecordTimer       .setEnabled(isEventOK && timer==null);
			miAddSwitchTimer       .setEnabled(isEventOK && timer==null);
			miAddRecordNSwitchTimer.setEnabled(isEventOK && timer==null);
			miToggleTimer          .setEnabled(isEventOK && timer!=null);
			miDeleteTimer          .setEnabled(isEventOK && timer!=null);
			miAddRecordTimer       .setText(!isEventOK || this.event.title==null ? "Add Record Timer"          : String.format("Add "+"Record"         +" Timer for Event \"%s\"", this.event.title));
			miAddSwitchTimer       .setText(!isEventOK || this.event.title==null ? "Add Switch Timer"          : String.format("Add "+"Switch"         +" Timer for Event \"%s\"", this.event.title));
			miAddRecordNSwitchTimer.setText(!isEventOK || this.event.title==null ? "Add Record'N'Switch Timer" : String.format("Add "+"Record'N'Switch"+" Timer for Event \"%s\"", this.event.title));
			miToggleTimer          .setText(timer==null ? "Toggle Timer"              : String.format("Toggle Timer \"%s\"", timer.name));
			miDeleteTimer          .setText(timer==null ? "Delete Timer"              : String.format("Delete Timer \"%s\"", timer.name));
		}
	}

	static class StationContextMenu extends ContextMenu {
		private static final long serialVersionUID = 3048130309705457643L;
		
		private StationID stationID;
		private final JMenuItem miSwitchToStation;
		private final JMenuItem miStreamStation;
		
		StationContextMenu(StationCommands externCommands, String baseURL) {
			add(miSwitchToStation = OpenWebifController.createMenuItem("Switch to Station",                                   e->externCommands. zapToStation(baseURL,stationID)));
			add(miStreamStation   = OpenWebifController.createMenuItem("Stream Station"   , GrayCommandIcons.IconGroup.Image, e->externCommands.streamStation(baseURL,stationID)));
		}

		void setStationID(String stationName, StationID stationID) {
			this.stationID = stationID;
			miSwitchToStation.setText(String.format("Switch to \"%s\"", stationName));
			miStreamStation  .setText(String.format("Stream \"%s\""   , stationName));
		}
	}
	
	private static class TimeInputDialog extends StandardDialog
	{
		private static final long serialVersionUID = -6346723334363284791L;
		private final TimeInput timeInput;
		private Clock clock;
		private ZonedDateTime initValue;
		private boolean ignoreValue;

		TimeInputDialog(Window parent, ModalityType modality, boolean repeatedUseOfDialogObject, String title, String text)
		{
			super(parent, title, modality, repeatedUseOfDialogObject);
			
			clock = Clock.systemDefaultZone();
			initValue = getNow();
			ignoreValue = true;
			
			timeInput = new TimeInput(initValue);
			
			JPanel contentPane = new JPanel(new BorderLayout());
			contentPane.add(new JLabel(text), BorderLayout.NORTH);
			contentPane.add(timeInput, BorderLayout.CENTER);
			contentPane.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
			
			createGUI(contentPane,
					OpenWebifController.createButton("Reset"     , true, e->timeInput.setValue(initValue)),
					OpenWebifController.createButton("Set to Now", true, e->timeInput.setValue(getNow())),
					new JLabel("   "),
					OpenWebifController.createButton("Ok"    , true, e->{ ignoreValue = false; closeDialog(); }),
					OpenWebifController.createButton("Cancel", true, e->{ closeDialog(); })
			);
		}

		private ZonedDateTime getNow()
		{
			return clock.instant().atZone(clock.getZone());
		}
		
		ZonedDateTime showDialog(ZonedDateTime value)
		{
			initValue = value;
			timeInput.setValue(initValue);
			showDialog(Position.PARENT_CENTER);
			return ignoreValue ? null : timeInput.getValue();
		}
	}
}
