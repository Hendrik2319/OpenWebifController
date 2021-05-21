package net.schwarzbaer.java.tools.openwebifcontroller;

import javax.swing.JPanel;

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;

class Timers extends JPanel {
	private static final long serialVersionUID = -2563250955373710618L;
	
	private final OpenWebifController main;
	private net.schwarzbaer.java.lib.openwebif.Timers timers;

	public Timers(OpenWebifController main) {
		this.main = main;
	}

	public void readData(String baseURL, ProgressDialog pd) {
		if (baseURL==null) return;
		
		timers = OpenWebifTools.readTimers(baseURL, taskTitle -> main.setIndeterminateProgressTask(pd, "Timers: "+taskTitle));
		
		// TODO Auto-generated method stub
		
	}

}
