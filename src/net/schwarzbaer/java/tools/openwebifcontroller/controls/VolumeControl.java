package net.schwarzbaer.java.tools.openwebifcontroller.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.PrintStream;

import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.JTextField;

import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressView;
import net.schwarzbaer.java.lib.openwebif.Volume;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

public class VolumeControl extends AbstractControlPanel<Volume.Values> {
	private static final long serialVersionUID = 6164405483744214580L;
	
	private final boolean isSmall;
	private final JTextField txtVolume;
	private final JSlider sldrVolume;
	private final Color defaultTextColor;
	private final JButton btnVolUp;
	private final JButton btnVolDown;
	private final JButton btnVolMute;
	private final JButton btnUpdate;
	private boolean ignoreSldrVolumeEvents;
	
	public VolumeControl(ExternCommands externCommands, boolean withBorder, boolean isStretchable, boolean isSmall) {
		super(new GridBagLayout(),externCommands,"VolumeControl",withBorder?"Volume":null,Volume::getState);
		this.isSmall = isSmall;
		
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 0;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		
		txtVolume = new JTextField("mute",8);
		if (!this.isSmall) {
			add(txtVolume, c);
			if (isStretchable) c.weightx = 1;
			add(sldrVolume = new JSlider(JSlider.HORIZONTAL,0,100,75), c);
			if (isStretchable) c.weightx = 0;
		} else {
			sldrVolume = null;
		}
		
		if (this.isSmall && isStretchable) c.weightx = 1;
		add(btnVolDown = OpenWebifController.createButton("-", true, e->setVolDown(null)), c);
		add(btnVolUp   = OpenWebifController.createButton("+", true, e->setVolUp  (null)), c);
		add(btnVolMute = OpenWebifController.createButton(isSmall ? null : "Mute", GrayCommandIcons.IconGroup.Muted, true, e->setVolMute(null)), c);
		if (this.isSmall) add(txtVolume, c);
		if (this.isSmall && isStretchable) c.weightx = 0;
		
		if (!this.isSmall)
			add(btnUpdate = createUpdateButton("Update", GrayCommandIcons.IconGroup.Reload, false), c);
		else
			btnUpdate = null;
		
		if (txtVolume!=null) {
			txtVolume.setEditable(false);
			txtVolume.setMinimumSize(new Dimension(65,10));
			txtVolume.setHorizontalAlignment(JTextField.CENTER);
			defaultTextColor = txtVolume.getForeground();
		} else
			defaultTextColor = null;
		
		ignoreSldrVolumeEvents = false;
		if (sldrVolume!=null) {
			sldrVolume.setMinimumSize(new Dimension(65,10));
			sldrVolume.addChangeListener(e -> {
				if (ignoreSldrVolumeEvents) return;
				
				int value = sldrVolume.getValue();
				if (txtVolume!=null) txtVolume.setText(Integer.toString(value));
				
				if (sldrVolume.getValueIsAdjusting()) {
					if (txtVolume!=null) txtVolume.setForeground(Color.GRAY);
					
				} else {
					if (txtVolume!=null) txtVolume.setForeground(defaultTextColor);
					setVol(value,null);
				}
			});
		}
	}

	public static void setVolMute(String baseURL, PrintStream out) {
		Volume.setVolMute(baseURL,str->out.printf("VolumeControl: %s%n", str));
	}
	
	public void setVolUp  (     ProgressView pd) { callCommand(pd, "VolUp"  , Volume::setVolUp  ); }
	public void setVolDown(     ProgressView pd) { callCommand(pd, "VolDown", Volume::setVolDown); }
	public void setVolMute(     ProgressView pd) { callCommand(pd, "VolMute", Volume::setVolMute); }
	public void setVol(int vol, ProgressView pd) { callCommand(pd, "SetVol", (baseURL,setTaskTitle)->Volume.setVol(baseURL, vol, setTaskTitle)); }

	@Override protected void updatePanel(Volume.Values values) {
		if (values==null) {
			if (txtVolume!=null) txtVolume.setText("???");
		} else {
			String format;
			if (values.ismute) {
				format = "mute (%d)";
				btnVolMute.setIcon(GrayCommandIcons.UnMuted.getIcon());
				btnVolMute.setDisabledIcon(GrayCommandIcons.UnMuted_Dis.getIcon());
				if (!isSmall) btnVolMute.setText("UnMute");
			} else {
				format = "%d";
				btnVolMute.setIcon(GrayCommandIcons.Muted.getIcon());
				btnVolMute.setDisabledIcon(GrayCommandIcons.Muted_Dis.getIcon());
				if (!isSmall) btnVolMute.setText("Mute");
			}
			
			if (txtVolume!=null)
				txtVolume.setText(String.format(format, values.current));
			
			if (sldrVolume!=null) {
				ignoreSldrVolumeEvents = true;
				sldrVolume.setValue((int) values.current);
				ignoreSldrVolumeEvents = false;
			}
		}
	}

	@Override protected void setPanelEnable(boolean enabled) {
		if (txtVolume !=null) txtVolume .setEnabled(enabled);
		if (sldrVolume!=null) sldrVolume.setEnabled(enabled);
		if (btnVolUp  !=null) btnVolUp  .setEnabled(enabled);
		if (btnVolDown!=null) btnVolDown.setEnabled(enabled);
		if (btnVolMute!=null) btnVolMute.setEnabled(enabled);
		if (btnUpdate !=null) btnUpdate .setEnabled(enabled);
	}
}