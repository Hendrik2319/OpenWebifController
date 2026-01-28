package net.schwarzbaer.java.tools.openwebifcontroller.controls;

import java.awt.LayoutManager;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.gui.GeneralIcons;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;

public abstract class AbstractControlPanel<ValueStructType> extends SwitchablePanel {
	private static final long serialVersionUID = 1376060978837360833L;
	
	public interface ExternCommands extends OWCTools.LogWindowInterface {
		String getBaseURL();
	}
	
	protected final ExternCommands externCommands;
	private final String controlLabel;
	private final BiFunction<String, Consumer<String>, ValueStructType> updateCommand;

	protected AbstractControlPanel(LayoutManager layout, ExternCommands externCommands, String controlLabel, String borderTitle, BiFunction<String, Consumer<String>, ValueStructType> updateCommand) {
		super(layout, borderTitle);
		this.externCommands = externCommands;
		this.controlLabel = controlLabel;
		this.updateCommand = updateCommand;
	}

	public void initialize(String baseURL, ProgressView pd) {
		if (updateCommand!=null)
			callCommand(baseURL, pd, "Init"+controlLabel, updateCommand);
	}

	protected JButton createUpdateButton(String title, boolean withDelayedUpdate) {
		return createUpdateButton(title, null, null, withDelayedUpdate);
	}
	protected JButton createUpdateButton(String title, Icon icon, boolean withDelayedUpdate) {
		return createUpdateButton(title, icon, null, withDelayedUpdate);
	}
	protected JButton createUpdateButton(String title, GeneralIcons.IconGroup icons, boolean withDelayedUpdate) {
		return createUpdateButton(title, icons.getEnabledIcon(), icons.getDisabledIcon(), withDelayedUpdate);
	}
	private JButton createUpdateButton(String title, Icon icon, Icon disIcon, boolean withDelayedUpdate) {
		if (updateCommand==null)
			throw new UnsupportedOperationException("Can't create an UpdateButton for a ContolPanel without an UpdateCommand");
		return OWCTools.createButton(title, icon, disIcon, true, e->{
			callCommand(null, "Update"+controlLabel, withDelayedUpdate, updateCommand);
		});
	}
	
	protected void callCommand(ProgressView pd, String commandLabel, BiFunction<String, Consumer<String>, ValueStructType> commandFcn) {
		callCommand(pd, commandLabel, false, commandFcn);
	}
	protected void callCommand(ProgressView pd, String commandLabel, boolean withDelayedUpdate, BiFunction<String, Consumer<String>, ValueStructType> commandFcn) {
		String baseURL = externCommands.getBaseURL();
		if (baseURL==null) return;
		callCommand(baseURL, pd, commandLabel, withDelayedUpdate, commandFcn);
	}
	
	protected void callCommand(String baseURL, ProgressView pd, String commandLabel, BiFunction<String, Consumer<String>, ValueStructType> commandFcn) {
		callCommand(baseURL, pd, commandLabel, false, commandFcn);
	}
	protected void callCommand(String baseURL, ProgressView pd, String commandLabel, boolean withDelayedUpdate, BiFunction<String, Consumer<String>, ValueStructType> commandFcn) {
		if (pd != null)
			callCommand(baseURL, withDelayedUpdate, commandFcn, taskTitle->OWCTools.setIndeterminateProgressTask(pd, String.format("%s.%s: %s", controlLabel, commandLabel, taskTitle)));
		else
			new Thread(()->callCommand(baseURL, withDelayedUpdate, commandFcn, null)).start();
	}

	private void callCommand(String baseURL, boolean withDelayedUpdate, BiFunction<String, Consumer<String>, ValueStructType> commandFcn, Consumer<String> setTaskTitle) {
		SwingUtilities.invokeLater(()->{
			setPanelEnable(false);
		});
		
		ValueStructType values, valuesPre = commandFcn.apply(baseURL, setTaskTitle);
		
		if (withDelayedUpdate && updateCommand!=null) {
			synchronized (this) {
				long time_ms = System.currentTimeMillis();
				for (long dur=time_ms+500-System.currentTimeMillis(); dur>5; dur=time_ms+500-System.currentTimeMillis()) {
					try { wait(dur); }
					catch (InterruptedException e) { System.err.printf("InterruptedException while waiting for AbstractContolPanel.Update: %s%n", e.getMessage()); }
				}
			}
			values = updateCommand.apply(baseURL, setTaskTitle);
		} else
			values = valuesPre;
		
		SwingUtilities.invokeLater(()->{
			updatePanel(values);
			setPanelEnable(true);
		});
	}
	
	protected abstract void updatePanel(ValueStructType values);
	protected abstract void setPanelEnable(boolean enabled);
}