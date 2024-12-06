package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.util.Locale;

import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.Timers.LogEntry;
import net.schwarzbaer.java.lib.openwebif.Timers.Timer;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.openwebifcontroller.TimersPanel.TimerStateGuesser;

public class TimerTools
{
	private static final Color BGCOLOR_Type_Record        = new Color(0xD9D9FF);
	private static final Color BGCOLOR_Type_RecordNSwitch = new Color(0xD9FFFF);
	private static final Color BGCOLOR_Type_Switch        = new Color(0xFFEDD9);
	private static final Color BGCOLOR_Type_Unknown       = new Color(0xFF5CFF);
	private static final Color BGCOLOR_State_Running      = new Color(0xFFFFD9);
	private static final Color BGCOLOR_State_Waiting      = new Color(0xD9FFD9);
	private static final Color BGCOLOR_State_Waiting_Seen = new Color(0xFAFFD9);
	private static final Color BGCOLOR_State_Finished     = new Color(0xFFD9D9);
	private static final Color BGCOLOR_State_Deactivated  = new Color(0xD9D9D9);
	private static final Color BGCOLOR_State_Unknown      = new Color(0xFF5CFF);
	
	public static Color getBgColor(Timer.Type type) {
		switch (type) {
		case Record       : return BGCOLOR_Type_Record;
		case RecordNSwitch: return BGCOLOR_Type_RecordNSwitch;
		case Switch       : return BGCOLOR_Type_Switch;
		case Unknown      : return BGCOLOR_Type_Unknown;
		}
		return null;
	}
	
	public static Color getBgColor(Timer.State state)
	{
		switch (state) {
			case Running    : return BGCOLOR_State_Running;
			case Waiting    : return BGCOLOR_State_Waiting;
			case Finished   : return BGCOLOR_State_Finished;
			case Deactivated: return BGCOLOR_State_Deactivated;
			case Unknown    : return BGCOLOR_State_Unknown;
		}
		return null;
	}
	
	public static Color getBgColor(TimerStateGuesser.ExtTimerState state)
	{
		return getBgColor(state, false);
	}
	public static Color getBgColor(TimerStateGuesser.ExtTimerState state, boolean markedAsAlreadySeen) {
		switch (state) {
			case Running    : return BGCOLOR_State_Running;
			case Waiting    : return markedAsAlreadySeen ? BGCOLOR_State_Waiting_Seen : BGCOLOR_State_Waiting;
			case Finished   : return BGCOLOR_State_Finished;
			case Deactivated: return BGCOLOR_State_Deactivated;
			case Unknown    : return BGCOLOR_State_Unknown;
			case Deleted    : return BGCOLOR_State_Unknown;
		}
		return null;
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
		if (timer!=null) {
			if (withName)
				sb.append(String.format("%sName:%n%s%n%n", indent, timer.name));
			sb.append(String.format("%sDescription:%n%s%n%n", indent, timer.description));
			sb.append(String.format("%sExtended Description:%n%s%n%n", indent, timer.descriptionextended));
			sb.append(String.format("%sLog Entries: %d%n", indent, timer.logentries.size()));
			for (int i=0; i<timer.logentries.size(); i++) {
				LogEntry entry = timer.logentries.get(i);
				String timeStr = OpenWebifController.dateTimeFormatter.getTimeStr(entry.when*1000L, Locale.GERMANY, false, true, false, true, false);
				sb.append(String.format("%s    [%d] %s <Type:%d> \"%s\"%n", indent, i+1, timeStr, entry.type, entry.text));
			}
		}
		return sb.toString();
	}
}
