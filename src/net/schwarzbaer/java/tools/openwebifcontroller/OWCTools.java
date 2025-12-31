package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.gui.GeneralIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.OptionalValue;
import net.schwarzbaer.java.lib.openwebif.Timers.LogEntry;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerStateGuesser;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents;

public class OWCTools
{
	public static void ASSERT( boolean predicate) { ASSERT( predicate, null ); }
	public static void ASSERT( boolean predicate, String message ) {
		if (!predicate) {
			if (message==null) throw new IllegalStateException();
			else               throw new IllegalStateException(message);
		}
	}
	public static DateTimeFormatter dateTimeFormatter = new DateTimeFormatter();
	
//	private static final Color BGCOLOR_Type_Record        = new Color(0xD9D9FF);
//	private static final Color BGCOLOR_Type_RecordNSwitch = new Color(0xD9FFFF);
//	private static final Color BGCOLOR_Type_Switch        = new Color(0xFFEDD9);
//	private static final Color BGCOLOR_Type_Unknown       = new Color(0xFF5CFF);
//	private static final Color BGCOLOR_State_Running      = new Color(0xFFFFD9);
//	private static final Color BGCOLOR_State_Waiting      = new Color(0xD9FFD9);
//	private static final Color BGCOLOR_State_Waiting_Seen = new Color(0xFAFFD9);
//	private static final Color BGCOLOR_State_Finished     = new Color(0xFFD9D9);
//	private static final Color BGCOLOR_State_Deactivated  = new Color(0xD9D9D9);
//	private static final Color BGCOLOR_State_Unknown      = new Color(0xFF5CFF);
	
	public static Color getBgColor(Timer.Type type) {
		switch (type) {
		case Record       : return UserDefColors.BGCOLOR_Type_Record       .getColor();
		case RecordNSwitch: return UserDefColors.BGCOLOR_Type_RecordNSwitch.getColor();
		case Switch       : return UserDefColors.BGCOLOR_Type_Switch       .getColor();
		case Unknown      : return UserDefColors.BGCOLOR_Type_Unknown      .getColor();
		}
		return null;
	}
	
	public static Color getBgColor(Timer.State state)
	{
		switch (state) {
			case Running    : return UserDefColors.BGCOLOR_State_Running    .getColor();
			case Waiting    : return UserDefColors.BGCOLOR_State_Waiting    .getColor();
			case Finished   : return UserDefColors.BGCOLOR_State_Finished   .getColor();
			case Deactivated: return UserDefColors.BGCOLOR_State_Deactivated.getColor();
			case Unknown    : return UserDefColors.BGCOLOR_State_Unknown    .getColor();
		}
		return null;
	}
	
	public static Color getBgColor(TimerStateGuesser.ExtTimerState state)
	{
		return getBgColor(state, false);
	}
	public static Color getBgColor(TimerStateGuesser.ExtTimerState state, boolean markedAsAlreadySeen) {
		switch (state) {
			case Running    : return getColor(UserDefColors.BGCOLOR_State_Running    , markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Running_Seen    );
			case Waiting    : return getColor(UserDefColors.BGCOLOR_State_Waiting    , markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Waiting_Seen    );
			case Finished   : return getColor(UserDefColors.BGCOLOR_State_Finished   , markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Finished_Seen   );
			case Deactivated: return getColor(UserDefColors.BGCOLOR_State_Deactivated, markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Deactivated_Seen);
			case Unknown    : return getColor(UserDefColors.BGCOLOR_State_Unknown    , markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Unknown_Seen    );
			case Deleted    : return getColor(UserDefColors.BGCOLOR_State_Deleted    , markedAsAlreadySeen, UserDefColors.BGCOLOR_State_Deleted_Seen    );
		}
		return null;
	}

	private static Color getColor(UserDefColors color, boolean markedAsAlreadySeen, UserDefColors colorSeen)
	{
		return markedAsAlreadySeen && colorSeen.getColor()!=null
								? colorSeen.getColor()
								: color    .getColor();
	}
	
	public interface ValueAccess<SourceType>
	{
		long getBegin(SourceType sourceItem);
		long getEnd(SourceType sourceItem);
		String getServiceRef(SourceType sourceItem);
		
		public static ValueAccess<Timer> TimerAccess = new ValueAccess<>()
		{
			@Override public long   getBegin     (Timer timer) { return timer.begin; }
			@Override public long   getEnd       (Timer timer) { return timer.end; }
			@Override public String getServiceRef(Timer timer) { return timer.serviceref; }
		};
		
		public static ValueAccess<EPGevent> EPGeventAccess = new ValueAccess<>() {
			@Override public String getServiceRef(EPGevent ev) { return ev.sref; }
			@Override public long getBegin(EPGevent ev)
			{
				if (ev.begin_timestamp==null)
					return 0;
				
				return ev.begin_timestamp;
			}
			@Override public long getEnd(EPGevent ev)
			{
				if (ev.begin_timestamp==null)
					return 0;
				
				if (ev.duration_sec!=null)
					return ev.begin_timestamp + ev.duration_sec;
				
				if (ev.duration_min!=null)
					return ev.begin_timestamp + ev.duration_min*60;
				
				return ev.begin_timestamp;
			}
		};
	}
	
	public static <GroupType> void showCollisions(Window window, Timer timer, Consumer<Consumer<Timer>> loopOverAllTimers, Function<Timer,GroupType> getGroupValue)
	{
		showCollisions(
				window,
				"Timer", timer, ValueAccess.TimerAccess,
				loopOverAllTimers,
				getGroupValue,
				(out, indentLevel, t) -> showTimer(out, indentLevel, t, null, null)
		);
	}
	public static <GroupType, SourceType> void showCollisions(
			Window window,
			String sourceTypeLabel, SourceType sourceItem, ValueAccess<SourceType> valueAccess,
			Consumer<Consumer<Timer>> loopOverAllTimers,
			Function<Timer,GroupType> getGroupValue,
			ShowSourceItem<SourceType> showSourceItem
	) {
		List<Timer> collidingTimers = new ArrayList<>();
		loopOverAllTimers.accept(t -> {
			if (sourceItem!=t && collidesWith( sourceItem, t, valueAccess ))
				collidingTimers.add(t);
		});
		
		String collidingInfo = generateCollidingInfo(
				sourceTypeLabel,
				sourceItem,
				valueAccess,
				collidingTimers,
				getGroupValue,
				showSourceItem
		);
		TextAreaDialog.showText(window, "Collisions with "+sourceTypeLabel, 800, 800, true, collidingInfo);
	}

	private static <SourceType> boolean collidesWith(SourceType v1, Timer v2, ValueAccess<SourceType> valueAccess)
	{
		long v1_begin = valueAccess.getBegin(v1);
		long v1_end   = valueAccess.getEnd(v1);
		if (v1_begin > v1_end) return false;
		if (v2.begin > v2.end) return false;
		if (isIn(v1_begin, v2.begin, v2.end)) return true;
		if (isIn(v1_end  , v2.begin, v2.end)) return true;
		if (isIn(v2.begin, v1_begin, v1_end)) return true;
		if (isIn(v2.end  , v1_begin, v1_end)) return true;
		return false;
	}

	private static boolean isIn(long n1, long n2a, long n2b)
	{
		return (n2a <= n1 && n1 <= n2b);
	}
	
	public interface ShowSourceItem<SourceType>
	{
		void showDetails(ValueListOutput out, int indentLevel, SourceType sourceItem);
	}
	
	private static <GroupType,SourceType> String generateCollidingInfo(
			String sourceTypeLabel,
			SourceType sourceItem,
			ValueAccess<SourceType> valueAccess,
			List<Timer> collidingTimers,
			Function<Timer,GroupType> getGroupValue,
			ShowSourceItem<SourceType> showSourceItem
	) {
		Map<GroupType,List<Timer>> collidingTimersMap = new HashMap<>();
		collidingTimers.forEach(t -> {
			collidingTimersMap
				.computeIfAbsent( getGroupValue.apply(t), st -> new ArrayList<>() )
				.add(t);
		});
		
		ValueListOutput out = new ValueListOutput();
		
		out.add(0, sourceTypeLabel);
		showSourceItem.showDetails(out, 1, sourceItem);
		
		out.addEmptyLine();
		
		out.add(0, "Colliding Timers", collidingTimers.size());
		out.addEmptyLine();
		collidingTimersMap.keySet().stream().sorted().forEachOrdered(group -> {
			out.add(1, group.toString());
			List<Timer> list = collidingTimersMap.get(group);
			list.sort( Comparator.<Timer,Long>comparing(t -> t.begin).thenComparing(t -> t.name) );
			list.forEach(t -> {
				showTimer(out, 2, t, sourceItem, valueAccess);
			});
		});
		
		return out.generateOutput();
	}

	private static <SourceType> void showTimer(ValueListOutput out, int indentLevel, Timer timer, SourceType sourceItem, ValueAccess<SourceType> valueAccess)
	{
		out.add(indentLevel, "\"%s\"".formatted( timer.name ));
		out.add(indentLevel+1, "Type"   , "%s", timer.type);
		out.add(indentLevel+1, "Station", timer.servicename);
		
		if (sourceItem!=null && Objects.equals(timer.serviceref, valueAccess.getServiceRef(sourceItem)))
			out.add(indentLevel+2, "[ SAME STATION ]");
		
		else if (isSameTransponder(timer, sourceItem, valueAccess))
			out.add(indentLevel+2, "[ SAME TRANSPONDER ]");
		
		out.add(indentLevel+1, "Begin", "%s", formatDate(timer.begin*1000, true, true, false, true, false));
		out.add(indentLevel+1, "End"  , "%s", formatDate(timer.end  *1000, true, true, false, true, false));
		out.addEmptyLine();
	}
	
	private static <SourceType> boolean isSameTransponder(Timer timer, SourceType sourceItem, ValueAccess<SourceType> valueAccess)
	{
		if (timer==null || sourceItem==null)
			return false;
		
		return isSameTransponder(timer.serviceref, valueAccess.getServiceRef(sourceItem));
	}

	public static boolean isSameTransponder(Timer t1, Timer t2)
	{
		if (t1==null || t2==null)
			return false;
		
		return isSameTransponder(t1.serviceref, t2.serviceref);
	}

	public static boolean isSameTransponder(String serviceRef1, String serviceRef2)
	{
		if (serviceRef1==null || serviceRef2==null)
			return false;
		
		StationID stationID1 = StationID.parseIDStr(serviceRef1);
		StationID stationID2 = StationID.parseIDStr(serviceRef2);
		return StationID.isSameTransponder(stationID1, stationID2);
	}
	
	public static void runWithProgressDialog(Window parent, String title, Consumer<ProgressDialog> action) {
		ProgressDialog.runWithProgressDialog(parent, title, 400, action);
	}
	
	public static Consumer<String> createProgressTaskFcn(ProgressView pd, String moduleTitle) {
		return taskTitle -> setIndeterminateProgressTask(pd, moduleTitle+": "+taskTitle);
	}
	public static void setIndeterminateProgressTask(ProgressView pd, String taskTitle) {
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle(taskTitle);
			pd.setIndeterminate(true);
		});
	}
	
	public static void callInGUIThread(Runnable task)
	{
		if (SwingUtilities.isEventDispatchThread())
			task.run();
		else
			SwingUtilities.invokeLater(task);
	}

	public static String getCurrentTimeStr() {
		return dateTimeFormatter.getTimeStr(System.currentTimeMillis(), false, false, false, true, false);
	}
	
	public static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
		return dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
	}

	public static JRadioButton createRadioButton(String text, ButtonGroup bg, boolean selected, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(text,selected);
		if (bg!=null) bg.add(comp);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	public static JCheckBox createCheckBox(String text, boolean selected, Consumer<Boolean> setValue) {
		JCheckBox comp = new JCheckBox(text, selected);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	public static JTextField createTextField(String text, int columns, Consumer<String> setValue) {
		JTextField comp = new JTextField(text, columns);
		if (setValue!=null) {
			comp.addActionListener(e->setValue.accept(comp.getText()));
			comp.addFocusListener(new FocusListener() {
				@Override public void focusLost  (FocusEvent e) { setValue.accept(comp.getText()); }
				@Override public void focusGained(FocusEvent e) {}
			});
		}
		return comp;
	}

	public static Integer parseInt(String str) {
		try { return Integer.parseInt(str); }
		catch (NumberFormatException e1) { return null; }
	}

	public static <ValueType> JTextField createTextField(String initialValue, int columns, Function<String,ValueType> convert, Predicate<ValueType> isOk, Consumer<ValueType> setValue) {
		JTextField comp = new JTextField(initialValue, columns);
		Color defaultBackground = comp.getBackground();
		ASSERT(setValue!=null);
		ASSERT(convert !=null);
		@SuppressWarnings("null")
		Runnable setValueTask = ()->{
			String str = comp.getText();
			ValueType value = convert.apply(str);
			if (value==null) { comp.setBackground(Color.RED); return; }
			if (isOk ==null) { setValue.accept(value); return; }
			if (!isOk.test(value)) { comp.setBackground(Color.RED); return; }
			comp.setBackground(defaultBackground);
			setValue.accept(value);
		};
		comp.addActionListener(e->setValueTask.run());
		comp.addFocusListener(new FocusListener() {
			@Override public void focusLost  (FocusEvent e) { setValueTask.run(); }
			@Override public void focusGained(FocusEvent e) {}
		});
		return comp;
	}

	public static <E> JComboBox<E> createComboBox(Consumer<E> setValue) {
		return confirureComboBox(new JComboBox<E>(), null, setValue);
	}

	public static <E> JComboBox<E> createComboBox(E[] items, E initialValue, Consumer<E> setValue) {
		return confirureComboBox(new JComboBox<>(items), initialValue, setValue);
	}

	public static <E> JComboBox<E> createComboBox(Vector<E> items, E initialValue, Consumer<E> setValue) {
		return confirureComboBox(new JComboBox<>(items), initialValue, setValue);
	}
	
	public static <E> JComboBox<E> confirureComboBox(JComboBox<E> comp, E initialValue, Consumer<E> setValue) {
		comp.setSelectedItem(initialValue);
		if (setValue!=null)
			comp.addActionListener(e->{
				int i = comp.getSelectedIndex();
				if (i<0) setValue.accept(null);
				else setValue.accept(comp.getItemAt(i));
			});
		return comp;
	}
	
	public static JButton createButton(String text, boolean enabled, ActionListener al) {
		return createButton(text, null, null, enabled, al);
	}
	public static JButton createButton(String text, Icon icon, boolean enabled, ActionListener al) {
		return createButton(text, icon, null, enabled, al);
	}
	public static JButton createButton(GeneralIcons.IconGroup icons, boolean enabled, ActionListener al) {
		return createButton(icons.getEnabledIcon(), icons.getDisabledIcon(), enabled, al);
	}
	public static JButton createButton(Icon icon, Icon disabledIcon, boolean enabled, ActionListener al) {
		return createButton(null, icon, disabledIcon, enabled, al);
	}
	public static JButton createButton(String text, GeneralIcons.IconGroup icons, boolean enabled, ActionListener al) {
		return createButton(text, icons.getEnabledIcon(), icons.getDisabledIcon(), enabled, al);
	}
	public static JButton createButton(String text, Icon icon, Icon disabledIcon, boolean enabled, ActionListener al) {
		JButton comp = new JButton(text);
		setIcon(comp, icon, disabledIcon);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	public static void setIcon(AbstractButton btn, GeneralIcons.IconGroup icons) {
		setIcon(btn, icons.getEnabledIcon(), icons.getDisabledIcon());
	}
	public static void setIcon(AbstractButton btn, Icon icon, Icon disabledIcon) {
		if (icon        !=null) btn.setIcon        (icon        );
		if (disabledIcon!=null) btn.setDisabledIcon(disabledIcon);
	}

	public static JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isChecked, Consumer<Boolean> setValue) {
		JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isChecked);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	public static JMenuItem createMenuItem(String title, ActionListener al) {
		return createMenuItem(title, null, null, al);
	}
	public static JMenuItem createMenuItem(String title, Icon icon, ActionListener al) {
		return createMenuItem(title, icon, null, al);
	}
	public static JMenuItem createMenuItem(String title, GeneralIcons.IconGroup iconGroup, ActionListener al) {
		return createMenuItem(title, iconGroup.getEnabledIcon(), iconGroup.getDisabledIcon(), al);
	}
	public static JMenuItem createMenuItem(String title, Icon icon, Icon disabledIcon, ActionListener al) {
		JMenuItem comp = new JMenuItem(title,icon);
		if (disabledIcon!=null) comp.setDisabledIcon(disabledIcon);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	public static JMenu createMenu(String title) {
		return new JMenu(title);
	}

	public static String toString(Process process) {
		ValueListOutput out = new ValueListOutput();
		out.add(0, "Process", process.toString());
		try { out.add(0, "Exit Value", process.exitValue()); }
		catch (Exception e) { out.add(0, "Exit Value", "%s", e.getMessage()); }
		out.add(0, "HashCode"  , "0x%08X", process.hashCode());
		out.add(0, "Is Alive"  , process.isAlive());
		out.add(0, "Class"     , "%s", process.getClass());
		return out.generateOutput();
	}

	public static void generateOutput(ValueListOutput out, int level, OpenWebifTools.CurrentStation currentStation) {
		out.add(level, "Station"          ); generateOutput(out, level+1, currentStation.stationInfo    );
		out.add(level, "Current EPG Event"); generateOutput(out, level+1, currentStation.currentEPGevent);
		out.add(level, "Next EPG Event"   ); generateOutput(out, level+1, currentStation.nextEPGevent   );
	}

	public static void generateOutput(ValueListOutput out, int level, OpenWebifTools.StationInfo stationInfo) {
		out.add(level, "Bouquet Name"      , stationInfo.bouquetName ); // String    bouquetName ;
		out.add(level, "Bouquet Reference" , stationInfo.bouquetRef  ); // String    bouquetRef  ;
		out.add(level, "Service Name"      , stationInfo.serviceName ); // String    serviceName ;
		out.add(level, "service Reference" , stationInfo.serviceRef  ); // String    serviceRef  ;
		if (stationInfo.stationID!=null)
			out.add(level, "StationID", " %s", stationInfo.stationID.toIDStr()); // StationID stationID   ;
		out.add(     level, "Provider"     ,                 stationInfo.provider); // String    provider    ;
		addLine(out, level, "Width"        ,                 stationInfo.width);
		addLine(out, level, "Height"       ,                 stationInfo.height);
		addLine(out, level, "\"Aspect\""   ,                 stationInfo.aspect);
		out.add(     level, "Is WideScreen",                 stationInfo.isWideScreen); // boolean   isWideScreen;
		addLine(out, level, "[onid]"       , "0x%1$X, %1$d", stationInfo.onid  );
		addLine(out, level, "[txtpid]"     , "0x%1$X, %1$d", stationInfo.txtpid);
		addLine(out, level, "[pmtpid]"     , "0x%1$X, %1$d", stationInfo.pmtpid);
		addLine(out, level, "[tsid]"       , "0x%1$X, %1$d", stationInfo.tsid  );
		addLine(out, level, "[pcrpid]"     , "0x%1$X, %1$d", stationInfo.pcrpid);
		out.add(     level, "[sid]"        , "0x%X, %d"    , stationInfo.sid   , stationInfo.sid   ); // long      sid         ;
		addLine(out, level, "[namespace]"  , "0x%1$X, %1$d", stationInfo.namespace);
		addLine(out, level, "[apid]"       , "0x%1$X, %1$d", stationInfo.apid);
		addLine(out, level, "[vpid]"       , "0x%1$X, %1$d", stationInfo.vpid);
		out.add(     level, "result"       ,                 stationInfo.result); // boolean   result      ;
	}

	private static void addLine(ValueListOutput out, int level, String field, OptionalValue optVal) {
		addLine(out, level, field, null, optVal);
	}
	private static void addLine(ValueListOutput out, int level, String field, String format, OptionalValue optVal)
	{
		if (optVal.value() == null) out.add(level, field,         optVal.str());
		else if (format    != null) out.add(level, field, format, optVal.value());
		else                        out.add(level, field,         optVal.value());
	}
	
	public static void generateOutput(ValueListOutput out, int level, EPGevent event) {
		out.add(level, "Station"   , event.station_name);
		out.add(level, "SRef"      , event.sref);
		if (event.picon   !=null) out.add(level, "Picon"   , event.picon);
		if (event.provider!=null) out.add(level, "Provider", event.provider);
		out.add(level, "Title"     , event.title);
		out.add(level, "Genre"     , "[%d] \"%s\"", event.genreid, event.genre);
		out.add(level, "ID"        , event.id);
		if (event.date!=null && event.begin!=null && event.end  !=null)
			out.add(level, "Time", "\"%s\", \"%s\" - \"%s\"", event.date, event.begin, event.end  );
		else {
			if (event.date !=null) out.add(level, "Date" , event.date );
			if (event.begin!=null) out.add(level, "Begin", event.begin);
			if (event.end  !=null) out.add(level, "End"  , event.end  );
		}
		out.add(level, "Begin"     , "%s", event.begin_timestamp==null ? "<null>" : dateTimeFormatter.getTimeStr(event.begin_timestamp*1000, true, true, false, true, false) );
		if (event.isUpToDate)
		{
			if (event.now_timestamp==0)
				out.add(level, "Now", "%s", event.now_timestamp);
			else
				out.add(level, "Now", "%s", dateTimeFormatter.getTimeStr(event.now_timestamp*1000, true, true, false, true, false) );
		}
		if (event.duration_sec==null)
			out.add(level, "Duration"  , "%s", "<null>");
		else
			out.add(level, "Duration"  , "%s (%d s)", DateTimeFormatter.getDurationStr(event.duration_sec), event.duration_sec);
		if (event.duration_min!=null                    ) out.add(level, "Duration" , "%s (%d min)", DateTimeFormatter.getDurationStr(event.duration_min*60), event.duration_min);
		if (event.remaining   !=null && event.isUpToDate) out.add(level, "Remaining", "%s", DateTimeFormatter.getDurationStr(event.remaining));
		if (event.tleft       !=null && event.isUpToDate) {
			if (event.tleft<0) out.add(level, "Time Left", "ended %s ago", DateTimeFormatter.getDurationStr(-event.tleft*60));
			else               out.add(level, "Time Left", "%s"          , DateTimeFormatter.getDurationStr( event.tleft*60));
		}
		if (event.progress    !=null && event.isUpToDate) out.add(level, "Progress" , event.progress);
		out.add(level, "Is Up-To-Date" , event.isUpToDate);
		out.add(level, "Description");
		out.add(level+1, "", event.shortdesc);
		out.add(level+1, "", event.longdesc );
		
		AlreadySeenEvents.RuleOutput rule = AlreadySeenEvents.getInstance().getRuleIfAlreadySeen(event);
		if (rule!=null)
		{
			out.addEmptyLine();
			out.add(level, "Is Already Seen");
			rule.writeIntoOutput(out, level+1);
		}
	}

	public static String generateOutput(EPGevent event, Timers.Timer timer)
	{
		ValueListOutput out = new ValueListOutput();
		String output;
		if (timer == null)
		{
			generateOutput(out, 0, event);
			output = out.generateOutput();
		}
		else
		{
			out.add(0, "EPG Event");
			generateOutput(out, 1, event);
			out.addEmptyLine();
			out.add(0, "Timer");
			generateDetailsOutput(out, 1, timer);
			output = out.generateOutput();
			output += "\r\n"+generateShortInfo(ValueListOutput.DEFAULT_INDENT, timer, false);
		}
		return output;
	}

	public static String generateDetailsOutput(Timer timer) {
		ValueListOutput output = new ValueListOutput();
		generateDetailsOutput(output, 0, timer);
		return output.generateOutput();
	}
	
	public static void generateDetailsOutput(ValueListOutput out, int indentLevel, Timer timer)
	{
		out.add(indentLevel, "isAutoTimer"        , "%s (%d)", timer.isAutoTimer==1, timer.isAutoTimer);
		out.add(indentLevel, "tags"               , timer.tags               );
		out.add(indentLevel, "serviceref"         , timer.serviceref         );
		out.add(indentLevel, "servicename"        , timer.servicename        );
		out.add(indentLevel, "name"               , timer.name               );
		out.add(indentLevel, "dirname"            , timer.dirname            );
		out.add(indentLevel, "filename"           , timer.filename           );
		out.add(indentLevel, "realbegin"          , timer.realbegin          );
		out.add(indentLevel, "realend"            , timer.realend            );
		out.add(indentLevel, "begin"              , timer.begin              );
		out.add(indentLevel, ""                   , "%s", dateTimeFormatter.getTimeStr(           timer.begin       *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "end"                , timer.end                );
		out.add(indentLevel, ""                   , "%s", dateTimeFormatter.getTimeStr(           timer.end         *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "duration"           , timer.duration);
		out.add(indentLevel, ""                   , "%s", DateTimeFormatter.getDurationStr(timer.duration));
		out.add(indentLevel, "startprepare"       , timer.startprepare       );
		out.add(indentLevel, ""                   , "%s", dateTimeFormatter.getTimeStr(Math.round(timer.startprepare*1000), Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "nextactivation"     , timer.nextactivation     );
		if (timer.nextactivation!=null)
			out.add(indentLevel, ""               , "%s", dateTimeFormatter.getTimeStr(timer.nextactivation.longValue()*1000, Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "eit"                , timer.eit                );
		out.add(indentLevel, "justplay"           , "%s (%d)", timer.justplay  ==1, timer.justplay  );
		out.add(indentLevel, "always_zap"         , "%s (%d)", timer.always_zap==1, timer.always_zap);
		out.add(indentLevel, "disabled"           , "%s (%d)", timer.disabled  ==1, timer.disabled  );
		out.add(indentLevel, "cancelled"          , timer.cancelled          );
		out.add(indentLevel, "afterevent"         , timer.afterevent         );
		out.add(indentLevel, "allow_duplicate"    , "%s (%d)", timer.allow_duplicate==1, timer.allow_duplicate);
		out.add(indentLevel, "asrefs"             , timer.asrefs             );
		out.add(indentLevel, "autoadjust"         , timer.autoadjust         );
		out.add(indentLevel, "backoff"            , timer.backoff            );
		out.add(indentLevel, "dontsave"           , "%s (%d)", timer.dontsave==1, timer.dontsave);
		out.add(indentLevel, "firsttryprepare"    , timer.firsttryprepare    );
		out.add(indentLevel, "pipzap"             , timer.pipzap             );
		out.add(indentLevel, "repeated"           , timer.repeated           );
		out.add(indentLevel, "state"              , timer.state              );
		out.add(indentLevel, "toggledisabled"     , "%s (%d)", timer.toggledisabled==1, timer.toggledisabled);
		out.add(indentLevel, "toggledisabledimg"  , timer.toggledisabledimg  );
		out.add(indentLevel, "vpsplugin_enabled"  , timer.vpsplugin_enabled  );
		out.add(indentLevel, "vpsplugin_overwrite", timer.vpsplugin_overwrite);
		out.add(indentLevel, "vpsplugin_time"     , timer.vpsplugin_time     );
	}

	public static String generateShortInfo(Timer timer, boolean withName)
	{
		return generateShortInfo("", timer, withName);
	}

	public static String generateShortInfo(String indent, Timer timer, boolean withName)
	{
		StringBuilder sb = new StringBuilder();
		if (timer!=null)
		{
			if (withName)
				sb.append(String.format("%sName:%n%s%n%n", indent, timer.name));
			sb.append(String.format("%sDescription:%n%s%n%n", indent, timer.description));
			sb.append(String.format("%sExtended Description:%n%s%n%n", indent, timer.descriptionextended));
			sb.append(String.format("%sLog Entries: %d%n", indent, timer.logentries.size()));
			for (int i=0; i<timer.logentries.size(); i++)
			{
				LogEntry entry = timer.logentries.get(i);
				String timeStr = dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
				sb.append(String.format("%s    [%d] %s <Type:%d> \"%s\"%n", indent, i+1, timeStr, entry.type, entry.text));
			}
			sb.append(String.format("%n"));
			AlreadySeenEvents.RuleOutput rule = AlreadySeenEvents.getInstance().getRuleIfAlreadySeen(timer);
			if (rule!=null)
				sb.append(String.format("%sIs Already Seen:%n%s%n", indent, rule.toString(indent+"    ")));
		}
		return sb.toString();
	}
}
