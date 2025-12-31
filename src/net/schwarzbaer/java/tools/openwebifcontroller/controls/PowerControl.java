package net.schwarzbaer.java.tools.openwebifcontroller.controls;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;

import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.openwebif.Power;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;

public class PowerControl extends AbstractControlPanel<Power.Values> {
	private static final long serialVersionUID = 3842993673551313089L;
	
	private final boolean isSmall;
	private final JButton btnPower;
	private final JComboBox<Power.Commands> cmbbxSetOtherState;
	private final JButton btnUpdate;
	private final Vector<UpdateTask> updateTasks;
	
	public PowerControl(ExternCommands externCommands, boolean withBorder, boolean isStretchable, boolean isSmall) {
		super(new GridBagLayout(),externCommands,"PowerControl",withBorder?"Power":null,Power::getState);
		this.isSmall = isSmall;
		updateTasks = new Vector<>();
		
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 0;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		
		if (this.isSmall && isStretchable) c.weightx = 1;
		add2Panel(btnPower = OWCTools.createButton(isSmall ? null : "Toggle StandBy", GrayCommandIcons.IconGroup.Power_IsOn, true, e->{
			callCommand(null, "ToggleStandBy", true, (baseURL, setTaskTitle)->{
				Power.Values state = Power.setState(baseURL, Power.Commands.ToggleStandBy, setTaskTitle);
				for (UpdateTask ut : updateTasks) ut.update(baseURL);
				return state;
			});
		}), c);
		if (this.isSmall && isStretchable) c.weightx = 0;
		
		if (!this.isSmall) {
			if (isStretchable) c.weightx = 1;
			Vector<Power.Commands> items = new Vector<>(Arrays.asList(Power.Commands.values()));
			items.remove(Power.Commands.ToggleStandBy);
			add2Panel(cmbbxSetOtherState = OWCTools.createComboBox(items, Power.Commands.Wakeup, cmd->{
				callCommand(null, cmd.name(), true, (baseURL, setTaskTitle)->{
					Power.Values state = Power.setState(baseURL, cmd, setTaskTitle);
					for (UpdateTask ut : updateTasks) ut.update(baseURL);
					return state;
				});
			}), c);
			if (isStretchable) c.weightx = 0;
			
			add2Panel(btnUpdate = createUpdateButton("Update", GrayCommandIcons.IconGroup.Reload, true), c);
		} else {
			cmbbxSetOtherState = null;
			btnUpdate = null;
		}
	}
	
	public interface UpdateTask {
		void update(String baseURL);
	}
	
	public void addUpdateTask(UpdateTask updateTask) {
		updateTasks.add(updateTask);
	}

	public static void setState(String baseURL, Power.Commands cmd, PrintStream out) {
		Power.Values values = Power.setState(baseURL, cmd, str->out.printf("PowerControl: %s%n", str));
		if (values==null) out.printf("PowerControl: No Answer%n");
		else              out.printf("PowerControl: In Standby = %s%n", values.instandby);
	}

	@Override protected void updatePanel(Power.Values values) {
		if (values==null) return;
		if (!isSmall) {
			if (values.instandby) btnPower.setText("Switch On");
			else btnPower.setText("Switch to Standby");
		}
		if (values.instandby)
			OWCTools.setIcon(btnPower, GrayCommandIcons.Power_IsOff.getIcon(), GrayCommandIcons.Power_IsOff_Dis.getIcon());
		else
			OWCTools.setIcon(btnPower, GrayCommandIcons.Power_IsOn.getIcon(), GrayCommandIcons.Power_IsOn_Dis.getIcon());
	}

	@Override protected void setPanelEnable(boolean enabled) {
		if (btnPower          !=null) btnPower          .setEnabled(enabled);
		if (cmbbxSetOtherState!=null) cmbbxSetOtherState.setEnabled(enabled);
		if (btnUpdate         !=null) btnUpdate         .setEnabled(enabled);
	}
}