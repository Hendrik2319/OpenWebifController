package net.schwarzbaer.java.tools.openwebifcontroller.controls;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.MessageType;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;

public class MessageControl extends AbstractControlPanel<OpenWebifTools.MessageResponse> {
	private static final long serialVersionUID = 139846886828802469L;
	private final JButton btnSend;
	private final JButton btnGetAnswer;
	private final JTextField txtfldMessage;
	private final JTextField txtfldTimeOut;
	private final JCheckBox chkbxTimeOut;
	private final JComboBox<MessageType> cmbbxMessageType;
	private Integer timeOut;
	private OpenWebifTools.MessageType messageType;

	public MessageControl(ExternCommands externCommands) {
		super(new GridBagLayout(),externCommands,"MessageControl","Messages",null);
		
		timeOut = null;
		messageType = OpenWebifTools.MessageType.INFO;
		
		txtfldMessage = OpenWebifController.createTextField("", 10, null);
		txtfldMessage.setMinimumSize(new Dimension(85,20));
		txtfldTimeOut = OpenWebifController.createTextField("", 4, OpenWebifController::parseInt, n->n>0, n->timeOut=n);
		txtfldTimeOut.setMinimumSize(new Dimension(35,20));
		
		chkbxTimeOut = OpenWebifController.createCheckBox("Time Out", false, b->{
			txtfldTimeOut.setEditable(b);
			txtfldTimeOut.setEnabled(b);
		});
		txtfldTimeOut.setEditable(false);
		txtfldTimeOut.setEnabled(false);
		
		cmbbxMessageType = OpenWebifController.createComboBox(OpenWebifTools.MessageType.values(), messageType, type->messageType = type);
		
		btnSend = OpenWebifController.createButton("Send Message", true, e->{
			String message = txtfldMessage.getText();
			Integer timeOut_sec = chkbxTimeOut.isSelected() ? timeOut : null;
			callCommand(null, "SendMessage", (baseURL, setTaskTitle)->OpenWebifTools.sendMessage(baseURL, message, messageType, timeOut_sec, setTaskTitle));
		});
		btnGetAnswer = OpenWebifController.createButton("Get Answer", true, e->{
			callCommand(null, "GetAnswer", OpenWebifTools::getMessageAnswer);
		});
		
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 0;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		
		add2Panel(btnSend, c);
		add2Panel(txtfldMessage, c);
		add2Panel(cmbbxMessageType, c);
		add2Panel(chkbxTimeOut, c);
		add2Panel(txtfldTimeOut, c);
		add2Panel(btnGetAnswer, c);
	}

	@Override protected void updatePanel(OpenWebifTools.MessageResponse values) {
		externCommands.showMessageResponse(values, "Message Response");
	}

	@Override
	protected void setPanelEnable(boolean enabled) {
		btnSend      .setEnabled(enabled);
		btnGetAnswer .setEnabled(enabled);
		txtfldMessage.setEnabled(enabled);
		txtfldTimeOut.setEnabled(enabled);
		chkbxTimeOut .setEnabled(enabled);
	}
}