package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import net.schwarzbaer.java.lib.openwebif.EPGevent;

class EPGViewEvent {

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