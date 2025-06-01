package net.schwarzbaer.java.tools.openwebifcontroller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum UserDefColors
{
	// to be defined
	;
	private static final String NULL_AS_STRING = "<NULL>";
	
	public final String label;
	public final boolean isForText; // false -> for background
	public final boolean isNullable;
	public final Integer defaultValue;
	private      Integer value;

	UserDefColors(String label, boolean isForText, boolean isNullable, Integer defaultValue)
	{
		this.label = label;
		this.isForText = isForText;
		this.isNullable = isNullable;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}
	
	
	public Integer getValue()
	{
		return value;
	}
	public void setValue(Integer value)
	{
		if (value==null && !isNullable)
			throw new UnsupportedOperationException();
		this.value = value;
		writeToSettings();
	}
	
	
	public void resetToDefault(boolean updateSettings)
	{
		this.value = defaultValue;
		if (updateSettings)
			writeToSettings();
	}
	public static void resetAllToDefault(boolean updateSettings)
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
					
					udc.value = value;
				}
			});
	}
	private static String writeToString()
	{
		return Arrays
			.stream(values())
			.map(udc -> udc.value==null ? "%s%s%n".formatted(NULL_AS_STRING, udc.name()) : "%06X%s%n".formatted(udc.value, udc.name()))
			.collect(Collectors.joining());
	}
	
	
	public static void readFromSettings()
	{
		resetAllToDefault(false);
		readFromString( OpenWebifController.settings.getString(OpenWebifController.AppSettings.ValueKey.UserDefColors, null) );
		
	}
	public static void writeToSettings()
	{
		OpenWebifController.settings.putString(OpenWebifController.AppSettings.ValueKey.UserDefColors, writeToString());
	}
	private static void clearInSettings()
	{
		OpenWebifController.settings.remove(OpenWebifController.AppSettings.ValueKey.UserDefColors);
	}
	
	
	public static void readFromFile(File file)
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
	public static void writeToFile(File file)
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
}
