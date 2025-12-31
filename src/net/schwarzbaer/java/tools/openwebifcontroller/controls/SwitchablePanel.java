package net.schwarzbaer.java.tools.openwebifcontroller.controls;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.awt.LayoutManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;

public class SwitchablePanel extends JPanel
{
	private static final long serialVersionUID = 7159067649667513502L;
	
	private final JButton viewButton;
	private final JPanel contentPanel;
	private boolean isOpen;
	
	public SwitchablePanel(LayoutManager layout, String borderTitle)
	{
		super(new BorderLayout());
		contentPanel = new JPanel(layout);
		if (borderTitle!=null)
			setBorder(BorderFactory.createTitledBorder(borderTitle));
		
		isOpen = true;
		viewButton = OWCTools.createButton("<", true, e->changePanel(!isOpen));
		viewButton.setMargin(new Insets(0,0,0,0));
		super.add(viewButton, BorderLayout.WEST);
		super.add(contentPanel, BorderLayout.CENTER);
		
	}
	
	private void changePanel(boolean newValue)
	{
		isOpen = newValue;
		if (!isOpen) remove(contentPanel);
		else super.add(contentPanel, BorderLayout.CENTER);
		viewButton.setText(isOpen ? "<" : ">");
		revalidate();
	}
	
	public void add2Panel(Component comp, Object constraints) {
		contentPanel.add(comp, constraints);
	}
	
	@Override public Component add(Component comp) { throw new UnsupportedOperationException(); }
	@Override public Component add(String name, Component comp) { throw new UnsupportedOperationException(); }
	@Override public Component add(Component comp, int index) { throw new UnsupportedOperationException(); }
	@Override public void add(Component comp, Object constraints) { throw new UnsupportedOperationException(); }
	@Override public void add(Component comp, Object constraints, int index) { throw new UnsupportedOperationException(); }
}
