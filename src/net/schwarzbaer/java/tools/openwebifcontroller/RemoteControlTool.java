package net.schwarzbaer.java.tools.openwebifcontroller;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.openwebif.RemoteControl;
import net.schwarzbaer.java.tools.imagemapeditor.Area;
import net.schwarzbaer.java.tools.imagemapeditor.ImageMapEditor;
import net.schwarzbaer.java.tools.imagemapeditor.ImageMapEditor.MapImage;

public class RemoteControlTool {

	private final static String[] machines = new String[] {"et4x00","et6500","et6x00","et7x00","et8000","et9500","et9x00"};
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		new RemoteControlTool(false);
		// showKeyMappings();
	}

	private final StandardMainWindow mainWindow;
	
	RemoteControlTool(boolean asSubWindow) {
		mainWindow = OpenWebifController.createMainWindow("RemoteControl Tool", asSubWindow);
		Consumer<String> setIndeterminateProgressTask = taskTitle -> System.out.printf("RemoteControlTool: %s%n", taskTitle);
		
		JPanel otherCommandsPanel = new JPanel(new GridLayout(0,1,3,3));
		otherCommandsPanel.setBorder(BorderFactory.createTitledBorder("Other Commands"));
		otherCommandsPanel.add(createButton("Show Key Mappings", true, e->{
			String baseURL = OpenWebifController.getBaseURL(true, mainWindow);
			if (baseURL==null) return;
			showKeyMappings(baseURL);
		}));
		
		JPanel remoteControlMapsPanel = new JPanel(new GridLayout(0,1,3,3));
		remoteControlMapsPanel.setBorder(BorderFactory.createTitledBorder("RemoteControl Maps"));
		for (String machine:machines)
			remoteControlMapsPanel.add(createButton(String.format("Edit Map for \"%s\"",machine),true,e->{
				String baseURL = OpenWebifController.getBaseURL(true, mainWindow);
				if (baseURL==null) return;
				
				setIndeterminateProgressTask.accept("Load Image");
				String remoteControlImageURL = RemoteControl.getRemoteControlImageURL(baseURL, machine);
				MapImage mapImage = ImageMapEditor.MapImage.loadImage(remoteControlImageURL);
				if (mapImage==null) return;
				
				RemoteControl.Key[] keys = RemoteControl.getKeys(baseURL, machine, setIndeterminateProgressTask);
				if (keys==null) return;
				
				Area[] areas = Arrays.stream(keys).map(this::convert).toArray(Area[]::new);
				String title = String.format("RemoteControl Map for \"%s\"",machine);
				String suggestedHtmlOutFileName = String.format("keymapping_%s.html", machine);
				ImageMapEditor.show(title, mapImage, new Vector<>(Arrays.asList(areas)), suggestedHtmlOutFileName);
			}));
		
		FileChooser fileChooser = new FileChooser("KeyMapping File","html");
		remoteControlMapsPanel.add(createButton("Edit Custom Map ...",true,e->{
			if (fileChooser.showOpenDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
			File file = fileChooser.getSelectedFile();
			
			RemoteControl.KeyMappingFile keyMapping = RemoteControl.KeyMappingFile.load(file, setIndeterminateProgressTask);
			if (keyMapping==null) return;
			
			setIndeterminateProgressTask.accept("Load Image");
			MapImage mapImage = ImageMapEditor.MapImage.loadImage(keyMapping.imageURL);
			if (mapImage==null) return;
			
			Area[] areas = Arrays.stream(keyMapping.keys).map(this::convert).toArray(Area[]::new);
			String title = String.format("RemoteControl Map \"%s\"", file);
			String suggestedHtmlOutFileName = file.getName();
			ImageMapEditor.show(title, mapImage, new Vector<>(Arrays.asList(areas)), suggestedHtmlOutFileName);
		}));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		contentPane.add(remoteControlMapsPanel, BorderLayout.CENTER);
		contentPane.add(otherCommandsPanel, BorderLayout.SOUTH);
		
		mainWindow.startGUI(contentPane);
		mainWindow.setResizable(false);
		
	}

	private Area convert(RemoteControl.Key key) {
		String onclick = String.format("pressMenuRemote('%s');", key.keyCode);
		return new Area(convert(key.shape), key.title, onclick);
	}

	private Area.Shape convert(RemoteControl.Key.Shape shape) {
		switch (shape.type) {
		case Circle: return new Area.Shape(new Point(shape.center),shape.radius);
		case Rect  : return new Area.Shape(new Point(shape.corner1),new Point(shape.corner2));
		}
		throw new IllegalArgumentException();
	}

	private JButton createButton(String title, boolean isEnabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(isEnabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	@SuppressWarnings("unused")
	private static void showKeyMappings() {
		String baseURL = OpenWebifController.getBaseURL_DontAskUser();
		showKeyMappings(baseURL);
	}

	private static void showKeyMappings(String baseURL) {
		System.out.printf("baseURL: \"%s\"%n", baseURL);
		
		HashMap<String,HashMap<String,Vector<String>>> keyMappings = new HashMap<>();
		HashMap<String,HashMap<String,Vector<String>>> titleMappings_ = new HashMap<>();
		for (String machine:machines) {
			System.out.printf("RemoteControl.getKeys(\"%s\")%n", machine);
			RemoteControl.Key[] keys = RemoteControl.getKeys(baseURL, machine, null);
			for (RemoteControl.Key key:keys) {
				if (key.keyCode==null) continue;
				if (key.title==null) continue;
				String title = key.title.toLowerCase();
				Vector<String> machines2;
				
				HashMap<String, Vector<String>> titleKeyMappings = titleMappings_.get(title);
				if (titleKeyMappings==null) titleMappings_.put(title, titleKeyMappings = new HashMap<>());
				machines2 = titleKeyMappings.get(key.keyCode);
				if (machines2==null) titleKeyMappings.put(key.keyCode, machines2 = new Vector<>());
				machines2.add(machine);
				
				HashMap<String, Vector<String>> keyTitleMappings = keyMappings.get(key.keyCode);
				if (keyTitleMappings==null) keyMappings.put(key.keyCode, keyTitleMappings = new HashMap<>());
				machines2 = keyTitleMappings.get(title);
				if (machines2==null) keyTitleMappings.put(title, machines2 = new Vector<>());
				machines2.add(machine);
				
				
			}
		}
		
		ValueListOutput out;
		
		out = new ValueListOutput();
		Vector<String> titles_ = new Vector<>(titleMappings_.keySet());
		titles_.sort(null);
		for (String title:titles_) {
			HashMap<String, Vector<String>> titleKeyMappings = titleMappings_.get(title);
			Vector<String> keyCodes = new Vector<>(titleKeyMappings.keySet());
			keyCodes.sort(null);
			for (int i=0; i<keyCodes.size(); i++) {
				String keyCode = keyCodes.get(i);
				Vector<String> machines2 = titleKeyMappings.get(keyCode);
				String value = String.format("[%s]: %s", keyCode, getMachineList(machines2,machines));
				String label = i>0 ? null : String.format("\"%s\"",title);
				out.add(0, label, "%s", value);
			}
		}
		System.out.println();
		System.out.println("Title -> Key -> Machines");
		System.out.println(out.generateOutput());		
		
		out = new ValueListOutput();
		Vector<String> keyCodes = new Vector<>(keyMappings.keySet());
		keyCodes.sort(null);
		for (String keyCode:keyCodes) {
			HashMap<String, Vector<String>> keyTitleMappings = keyMappings.get(keyCode);
			Vector<String> titles = new Vector<>(keyTitleMappings.keySet());
			titles.sort(null);
			for (int i=0; i<titles.size(); i++) {
				String title = titles.get(i);
				Vector<String> machines2 = keyTitleMappings.get(title);
				String value = String.format("\"%s\": %s", title, getMachineList(machines2,machines));
				String label = i>0 ? null : String.format("KeyCode[%s]",keyCode);
				out.add(0, label, "%s", value);
			}
		}
		System.out.println();
		System.out.println("Key -> Title -> Machines");
		System.out.println(out.generateOutput());
	}

	private static String getMachineList(Vector<String> machines2, String[] machines) {
		if (machines2==null)
			return "<none>";
		for (String m:machines)
			if (!machines2.contains(m))
				return String.join(", ", machines2);
		return "<all>";
	}

	
	
	
}
