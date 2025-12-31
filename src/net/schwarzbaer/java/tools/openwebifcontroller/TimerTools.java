package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import net.schwarzbaer.java.lib.gui.TextAreaDialog;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.EPGevent;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.openwebif.Timers.LogEntry;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerStateGuesser;
import net.schwarzbaer.java.tools.openwebifcontroller.alreadyseenevents.AlreadySeenEvents;

public class TimerTools
{
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
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.begin       *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "end"                , timer.end                );
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(           timer.end         *1000 , Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "duration"           , timer.duration);
		out.add(indentLevel, ""                   , "%s", DateTimeFormatter.getDurationStr(timer.duration));
		out.add(indentLevel, "startprepare"       , timer.startprepare       );
		out.add(indentLevel, ""                   , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(Math.round(timer.startprepare*1000), Locale.GERMANY,  true,  true, false, true, false) );
		out.add(indentLevel, "nextactivation"     , timer.nextactivation     );
		if (timer.nextactivation!=null)
			out.add(indentLevel, ""               , "%s", OpenWebifController.dateTimeFormatter.getTimeStr(timer.nextactivation.longValue()*1000, Locale.GERMANY,  true,  true, false, true, false) );
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
				String timeStr = OpenWebifController.dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
				sb.append(String.format("%s    [%d] %s <Type:%d> \"%s\"%n", indent, i+1, timeStr, entry.type, entry.text));
			}
			sb.append(String.format("%n"));
			AlreadySeenEvents.RuleOutput rule = AlreadySeenEvents.getInstance().getRuleIfAlreadySeen(timer);
			if (rule!=null)
				sb.append(String.format("%sIs Already Seen:%n%s%n", indent, rule.toString(indent+"    ")));
		}
		return sb.toString();
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
	
	private static String formatDate(long millis, boolean withTextDay, boolean withDate, boolean dateIsLong, boolean withTime, boolean withTimeZone) {
		return OpenWebifController.dateTimeFormatter.getTimeStr(millis, Locale.GERMANY,  withTextDay,  withDate, dateIsLong, withTime, withTimeZone);
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
}
