package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.HSColorChooser;
import net.schwarzbaer.java.lib.gui.StandardDialog;

public enum UserDefColors
{
	// to be defined
	;
	private static final String NULL_AS_STRING = "<NULL>";
	
	public final String label;
	public final boolean isForText; // false -> for background
	public final boolean isNullable;
	public final Integer defaultValue;
	private      Color color;

	UserDefColors(String label, boolean isForText, boolean isNullable, Integer defaultValue)
	{
		this.label = label;
		this.isForText = isForText;
		this.isNullable = isNullable;
		this.defaultValue = defaultValue;
		setValue(defaultValue);
	}
	
	
	public Color getColor()
	{
		return color;
	}
	private void setColor(Color color)
	{
		if (color==null && !isNullable)
			throw new UnsupportedOperationException();
		this.color = color;
		writeToSettings();
	}
	private void setValue(Integer value)
	{
		this.color = value==null ? null : new Color(value);
	}
	
	
	private void resetToDefault(boolean updateSettings)
	{
		setValue(defaultValue);
		if (updateSettings)
			writeToSettings();
	}
	private static void resetAllToDefault(boolean updateSettings)
	{
		for (UserDefColors udc : values())
			udc.resetToDefault(false);
		if (updateSettings)
			clearInSettings();
	}
	
	
	private static void readFromString(String str)
	{
		if (str!=null)
			str.lines().forEach(line -> {
				if (line.length() > 6)
				{
					String name = line.substring(6);
					String valueStr = line.substring(0, 6);
					
					UserDefColors udc;
					try { udc = valueOf( name ); }
					catch (Exception e) { return; }
					
					Integer value;
					try { value = valueStr.equals(NULL_AS_STRING) ? null : Integer.parseInt( valueStr, 16 ); }
					catch (NumberFormatException e) { return; }
					
					if (value==null && !udc.isNullable)
						return; // don't change current value (-> old value or default)
					
					udc.setValue(value);
				}
			});
	}
	private static String writeToString()
	{
		return Arrays
			.stream(values())
			.map(udc -> udc.color==null ? "%s%s%n".formatted(NULL_AS_STRING, udc.name()) : "%06X%s%n".formatted(udc.color.getRGB(), udc.name()))
			.collect(Collectors.joining());
	}
	
	
	public static void readFromSettings()
	{
		resetAllToDefault(false);
		readFromString( OpenWebifController.settings.getString(OpenWebifController.AppSettings.ValueKey.UserDefColors, null) );
		
	}
	private static void writeToSettings()
	{
		OpenWebifController.settings.putString(OpenWebifController.AppSettings.ValueKey.UserDefColors, writeToString());
	}
	private static void clearInSettings()
	{
		OpenWebifController.settings.remove(OpenWebifController.AppSettings.ValueKey.UserDefColors);
	}
	
	
	private static void readFromFile(File file)
	{
		if (file==null || !file.isFile())
			return;
		
		String str = null;
		try
		{
			str = Files.readString(file.toPath(), StandardCharsets.UTF_8);
		}
		catch (IOException ex)
		{
			System.err.printf("IOException while reading UserDefColors from file \"%s\": %s%n", file.getAbsolutePath(), ex.getMessage());
		}
		
		resetAllToDefault(false);
		readFromString(str); 
		writeToSettings();
	}
	private static void writeToFile(File file)
	{
		if (file==null)
			return;
		
		try
		{
			Files.writeString(file.toPath(), writeToString(), StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		}
		catch (IOException ex)
		{
			System.err.printf("IOException while writing UserDefColors to file \"%s\": %s%n", file.getAbsolutePath(), ex.getMessage());
		}
	}
	
	public static class EditDialog extends StandardDialog
	{
		private static final long serialVersionUID = 8058788690519662214L;
		
		public static void showDialog(Window parent)
		{
			EditDialog dlg = new EditDialog(parent, "Edit User Defined Colors", ModalityType.APPLICATION_MODAL, false);
			dlg.showDialog();
		}

		private final FileChooser fileChooser;
		private final Map<UserDefColors, HSColorChooser.ColorButton> buttonMap;

		public EditDialog(Window parent, String title, ModalityType modality, boolean repeatedUseOfDialogObject)
		{
			super(parent, title, modality, repeatedUseOfDialogObject);
			
			fileChooser = new FileChooser("Stored User Defined Colors", "colors");
			buttonMap = new EnumMap<>(UserDefColors.class);
			
			JPanel contentPane = new JPanel(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			
			c.gridy = -1;
			c.weighty = 0;
			c.gridwidth = 1;
			c.gridheight = 1;
			c.fill = GridBagConstraints.BOTH;
			
			for (UserDefColors udc : values())
			{
				HSColorChooser.ColorButton button = HSColorChooser.createColorbutton(
						udc.getColor(), this,
						"Set %s".formatted(udc.label),
						HSColorChooser.Position.PARENT_CENTER,
						udc::setColor
				);
				buttonMap.put(udc, button);
				
				c.gridx = -1;
				c.gridy++;
				
				c.weightx = 0;
				c.gridx++; contentPane.add(new JLabel(udc.label+"  "),c);
				c.weightx = 1;
				c.gridx++; contentPane.add(button,c);
				c.weightx = 0;
				c.gridx++; contentPane.add(OpenWebifController.createButton("Reset" , GrayCommandIcons.IconGroup.Reload, true, e->udc.resetToDefault(true)),c);
				if (udc.isNullable) {
					c.gridx++; contentPane.add(OpenWebifController.createButton("Remove", GrayCommandIcons.IconGroup.Delete, true, e->udc.setColor(null)),c);
				}
			}
			
			createGUI(
					contentPane,
					OpenWebifController.createButton("Reset all to Defaults", true, e -> this.resetAllToDefault()),
					OpenWebifController.createButton("Read from File", GrayCommandIcons.IconGroup.Folder, true, e -> this.readFromFile()),
					OpenWebifController.createButton("Write to File" , GrayCommandIcons.IconGroup.Save  , true, e -> this.writeToFile()),
					OpenWebifController.createButton("Close", true, e -> closeDialog())
					);
		}

		private void updateButtons()
		{
			for (UserDefColors udc : values())
				buttonMap.get(udc).setColor(udc.getColor());
		}

		private void resetAllToDefault()
		{
			UserDefColors.resetAllToDefault(true);
			updateButtons();
		}

		private void readFromFile()
		{
			if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
			{
				UserDefColors.readFromFile( fileChooser.getSelectedFile() );
				updateButtons();
			}
		}

		private void writeToFile()
		{
			if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
				UserDefColors.writeToFile( fileChooser.getSelectedFile() );
		}
	}
}
