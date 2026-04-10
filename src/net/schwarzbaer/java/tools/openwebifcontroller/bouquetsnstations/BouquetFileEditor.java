package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.StandardMainWindow.DefaultCloseOperation;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.openwebif.Bouquet;
import net.schwarzbaer.java.lib.openwebif.BouquetFile;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools;
import net.schwarzbaer.java.lib.openwebif.OpenWebifTools.BouquetData;
import net.schwarzbaer.java.lib.openwebif.StationID;
import net.schwarzbaer.java.lib.system.Settings.DefaultAppSettings.SplitPaneDividersDefinition;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController.AppSettings;

public class BouquetFileEditor
{
	public static final String ProgressDialogTitle_ReadBouquetData = "Read Bouquet Data";

	public static void main(String[] args)
	{
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		new BouquetFileEditor(true, null, null, null);
	}
	
	public static void startAsSubWindow(OpenWebifController main, BouquetData bouquetData, Consumer<BouquetFileEditor> updateBouquetData)
	{
		new BouquetFileEditor(false, main, bouquetData, updateBouquetData);
	}
	
	private BouquetData bouquetData;
	private BouquetFileData bouquetFileData;
	private final Consumer<BouquetFileEditor> updateBouquetData;
	private final StandardMainWindow mainWindow;
	private final ContentPane contentPane;
	private final MenuBar menuBar;

	private BouquetFileEditor(boolean startedStandAlone, OpenWebifController main, BouquetData bouquetData, Consumer<BouquetFileEditor> updateBouquetData)
	{
		this.bouquetData = bouquetData;
		this.updateBouquetData = updateBouquetData!=null ? updateBouquetData : BouquetFileEditor::updateBouquetData;
		this.bouquetFileData = null;
		
		contentPane = new ContentPane(main);
		menuBar = new MenuBar();
		
		mainWindow = new StandardMainWindow("Bouquet File Editor", startedStandAlone ? DefaultCloseOperation.EXIT_ON_CLOSE : DefaultCloseOperation.DISPOSE_ON_CLOSE);
		mainWindow.startGUI(contentPane, menuBar);
		
		OpenWebifController.settings.registerExtraWindow(
				mainWindow,
				null, null,
				AppSettings.ValueKey.BouquetFileEditor_WindowWidth,
				AppSettings.ValueKey.BouquetFileEditor_WindowHeight
		);
		OpenWebifController.settings.registerSplitPaneDividers(
				new SplitPaneDividersDefinition<>(mainWindow, AppSettings.ValueKey.class)
				.add(contentPane, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_ContentPane)
				.add(contentPane.bouquetDataPanel.tableTextAreaPanel, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_BouquetDataPanel)
				.add(contentPane.bouquetFilePanel.tableTextAreaPanel, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_BouquetFilePanel)
		);
		
		contentPane.bouquetDataPanel.setData(this.bouquetData);
		contentPane.bouquetFilePanel.setData(this.bouquetFileData);
		contentPane.bouquetFilePanel.setStationNames(this.bouquetData==null ? null : this.bouquetData.names);
	}
	
	private static void updateBouquetData(BouquetFileEditor editor)
	{
		String baseURL = OpenWebifController.getBaseURL(true, editor.mainWindow);
		ProgressDialog.runWithProgressDialog(editor.mainWindow, ProgressDialogTitle_ReadBouquetData, 400, pd -> {
				BouquetData bouquetData = OpenWebifTools.readBouquets(baseURL, taskTitle ->
					OWCTools.setIndeterminateProgressTask(pd, taskTitle)
				);
				SwingUtilities.invokeLater(() -> {
					editor.setData(bouquetData);
				});
		} );
	}
	
	public void setData(BouquetData bouquetData)
	{
		this.bouquetData = bouquetData;
		ExtraData extraData = ExtraData.generate(bouquetData);
		contentPane.bouquetDataPanel.setData(this.bouquetData);
		contentPane.bouquetDataPanel.tableModel.setExtraData(extraData);
		contentPane.bouquetFilePanel.setStationNames(this.bouquetData==null ? null : this.bouquetData.names);
		contentPane.bouquetFilePanel.tableModel.setExtraData(extraData);
		menuBar.updateMiLoadBouquetData();
	}

	private class MenuBar extends JMenuBar
	{
		private static final long serialVersionUID = 2454464114876630856L;
		private final JMenu dataMenu;
		private final JMenuItem miLoadBouquetData;
		private final JMenuItem miSaveBouquetFile;
		private final JMenuItem miSaveBouquetFileAsRadio;
		private final FileChooser fileChooser_Radio;
		private final FileChooser fileChooser_TV;
		private JMenuItem miSaveBouquetFileAsTV;
		
		MenuBar()
		{
			fileChooser_Radio = new FileChooser("Radio Bouquet", "radio");
			fileChooser_TV    = new FileChooser("TV Bouquet", "tv");
			
			add(dataMenu = new JMenu("Data"));
			
			dataMenu.add(miLoadBouquetData = OWCTools.createMenuItem("", ev -> {
				updateBouquetData.accept(BouquetFileEditor.this);
			}));
			
			dataMenu.addSeparator();
			
			dataMenu.add(OWCTools.createMenuItem("New Bouquet File", ev -> {
				bouquetFileData = new BouquetFileData();
				contentPane.bouquetFilePanel.setData(bouquetFileData);
				updateMiSaveBouquetFile();
			}));
			
			dataMenu.add(OWCTools.createMenuItem("Open Radio Bouquet File", ev -> openBouquetFile(fileChooser_Radio)));
			dataMenu.add(OWCTools.createMenuItem("Open TV Bouquet File"   , ev -> openBouquetFile(fileChooser_TV   )));
			
			dataMenu.add(miSaveBouquetFile = OWCTools.createMenuItem("Save Bouquet File", GrayCommandIcons.IconGroup.Save, ev -> {
				saveBouquetFile();
			}));
			
			dataMenu.add(miSaveBouquetFileAsRadio = OWCTools.createMenuItem("Save File as Radio Bouquet File ...", GrayCommandIcons.IconGroup.Save, ev -> saveAsBouquetFile(fileChooser_Radio)));
			dataMenu.add(miSaveBouquetFileAsTV    = OWCTools.createMenuItem("Save File as TV Bouquet File ..."   , GrayCommandIcons.IconGroup.Save, ev -> saveAsBouquetFile(fileChooser_TV   )));
			
			updateMiLoadBouquetData();
			updateMiSaveBouquetFile();
		}

		private void saveBouquetFile()
		{
			if (bouquetFileData != null && bouquetFileData.file != null)
			{
				BouquetFile bouquetFile = bouquetFileData.buildBouquetFile();
				bouquetFile.writeFile(bouquetFileData.file);
			}
		}

		private void saveAsBouquetFile(FileChooser fileChooser)
		{
			if (bouquetFileData==null) return;
			if (fileChooser.showSaveDialog(mainWindow) == JFileChooser.APPROVE_OPTION)
			{
				bouquetFileData.file = fileChooser.getSelectedFile();
				BouquetFile bouquetFile = bouquetFileData.buildBouquetFile();
				bouquetFile.writeFile(bouquetFileData.file);
				contentPane.bouquetFilePanel.updateFileField();
				updateMiSaveBouquetFile();
			}
		}

		private void openBouquetFile(FileChooser fileChooser)
		{
			if (fileChooser.showOpenDialog(mainWindow) == JFileChooser.APPROVE_OPTION)
			{
				File selectedFile = fileChooser.getSelectedFile();
				BouquetFile loadedBouquetFile = BouquetFile.readFile(selectedFile);
				if (loadedBouquetFile!=null)
				{
					bouquetFileData = BouquetFileData.getFrom(loadedBouquetFile, selectedFile);
					contentPane.bouquetFilePanel.setData(bouquetFileData);
					updateMiSaveBouquetFile();
				}
			}
		}

		private void updateMiLoadBouquetData()
		{
			miLoadBouquetData.setText(bouquetData==null ? "Load Global Bouquet Data" : "Reload Global Bouquet Data");
			GrayCommandIcons.IconGroup icons = bouquetData==null ? GrayCommandIcons.IconGroup.Download : GrayCommandIcons.IconGroup.Reload;
			icons.setIcons(miLoadBouquetData);
		}

		private void updateMiSaveBouquetFile()
		{
			miSaveBouquetFile       .setEnabled(bouquetFileData!=null && bouquetFileData.file!=null);
			miSaveBouquetFileAsRadio.setEnabled(bouquetFileData!=null);
			miSaveBouquetFileAsTV   .setEnabled(bouquetFileData!=null);
		}
	}
	
	private static class ContentPane extends JSplitPane
	{
		private static final long serialVersionUID = -8404621947710308787L;
		final BouquetDataPanel bouquetDataPanel;
		final BouquetFilePanel bouquetFilePanel;

		ContentPane(OpenWebifController main)
		{
			super(JSplitPane.HORIZONTAL_SPLIT,true);
			setLeftComponent (bouquetDataPanel = new BouquetDataPanel(main));
			setRightComponent(bouquetFilePanel = new BouquetFilePanel(main));
			setResizeWeight(0.5);
		}
	}
	
	private static abstract class AbstractPanel<
			RowType,
			TableModelType extends Tables.SimpleGetValueTableModel<RowType, ColumnID> & ExtraData.Receiver & AbstractPanel.TableContextMenu.StationIDExtractor<RowType>,
			ColumnID extends Tables.AbstractGetValueTableModel.ColumnIDTypeInt<RowType>
	> extends JPanel
	{
		private static final long serialVersionUID = -5359258011833246051L;
		
		enum SelectionMode
		{
			SINGLE_SELECTION           (ListSelectionModel.SINGLE_SELECTION           ),
			SINGLE_INTERVAL_SELECTION  (ListSelectionModel.SINGLE_INTERVAL_SELECTION  ),
			MULTIPLE_INTERVAL_SELECTION(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION),
			;
			final int value;
			SelectionMode(int value) { this.value = value;}
		}
		
		final JPanel northPanel;
		final JSplitPane tableTextAreaPanel;
		final JTable table;
		final TableModelType tableModel;
		final JScrollPane tableScrollPane;
		final JTextArea textArea;
		final JScrollPane textareaScrollPane;
		protected OpenWebifController main;
		
		AbstractPanel(
				OpenWebifController main,
				String borderTitle,
				SelectionMode tableSelectionMode,
				Supplier<TableModelType> tableModelConstructor,
				TableContextMenu.Constructor<RowType, TableModelType> tableContextMenuConstructor,
				ColumnID contextMenuSurrogateColumn
		)
		{
			super(new BorderLayout(3, 3));
			this.main = main;
			setBorder(BorderFactory.createTitledBorder(borderTitle));
			
			Objects.requireNonNull( tableSelectionMode );
			Objects.requireNonNull( tableModelConstructor );
			
			tableModel = tableModelConstructor.get();
			table = new JTable(tableModel);
			tableScrollPane = new JScrollPane(table);
			//tableScrollPane.setPreferredSize(new Dimension(1000,500));
			table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.setColumnSelectionAllowed(false);
			table.setSelectionMode(tableSelectionMode.value);
			tableModel.setTable(table);
			tableModel.setColumnWidths(table);
			//tableModel.setAllDefaultRenderers();
			
			textArea = new JTextArea(5, 20);
			textareaScrollPane = new JScrollPane(textArea);
			
			table.getSelectionModel().addListSelectionListener(ev -> {
				String text;
				if (table.getSelectedRowCount()==1)
				{
					int rowV = table.getSelectedRow();
					int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
					RowType row = tableModel.getRow(rowM);
					text = generateRowInfo(row);
				}
				else
					text = "";
				ScrollPosition.keepScrollPos(textareaScrollPane, ScrollPosition.ScrollBarType.Vertical, ()->textArea.setText(text));
			});
			
			northPanel = new JPanel();
			tableTextAreaPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,true);
			tableTextAreaPanel.setTopComponent(tableScrollPane);
			tableTextAreaPanel.setBottomComponent(textareaScrollPane);
			tableTextAreaPanel.setResizeWeight(1);
			
			add(northPanel, BorderLayout.NORTH);
			add(tableTextAreaPanel, BorderLayout.CENTER);
			
			if (tableContextMenuConstructor == null)
				tableContextMenuConstructor = TableContextMenu::new;
			
			TableContextMenu<RowType,TableModelType> contextMenu = tableContextMenuConstructor.create(this.main, table, tableModel);
			contextMenu.populateMenu();
			contextMenu.addTo(table, () -> ContextMenu.computeSurrogateMousePos(table, tableScrollPane, tableModel.getColumn(contextMenuSurrogateColumn)));
			contextMenu.addTo(tableScrollPane);
		}
		
		protected abstract String generateRowInfo(RowType row);

		protected static class TableContextMenu<RowType, TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableContextMenu.StationIDExtractor<RowType>> extends ContextMenu
		{
			private static final long serialVersionUID = 5456765972894773723L;
			
			interface Constructor<RowType, TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableContextMenu.StationIDExtractor<RowType>>
			{
				TableContextMenu<RowType, TableModelType> create(OpenWebifController main, JTable table, TableModelType tableModel);
			}
			
			interface StationIDExtractor<RowType>
			{
				StationID getStationIDFromRow(RowType row);
			}
			
			protected final JTable table;
			protected final TableModelType tableModel;
			private   final JMenuItem miSwitchToStation;
			private   final JMenuItem miStreamStation;
			private   final JMenuItem miShowColumnWidths;
			private   final JMenuItem miResetRowOrder;
			protected int clickedRowM;
			protected RowType clickedRow;
			protected StationID clickedRowStationID;

			TableContextMenu(OpenWebifController main, JTable table, TableModelType tableModel)
			{
				this.table = table;
				this.tableModel = tableModel;
				clickedRowM = -1;
				clickedRow = null;
				
				miSwitchToStation = main==null ? null : OWCTools.createMenuItem("Switch To Station", e->{
					if (clickedRowStationID!=null)
						main.zapToStation(clickedRowStationID);
				});
				miStreamStation = OWCTools.createMenuItem("Stream Station", e->{
					if (clickedRowStationID==null) return;
					if (main!=null)
						main.streamStation(clickedRowStationID);
					else
					{
						String baseURL = OpenWebifController.getBaseURL(true, this.table);
						if (baseURL!=null)
							OpenWebifController.streamStation(clickedRowStationID, baseURL);
					}
				});
				
				miShowColumnWidths = OWCTools.createMenuItem("Show Column Widths", e->{
					System.out.printf("Column Widths: %s%n", Tables.SimplifiedTableModel.getColumnWidthsAsString(this.table));
				});
				
				miResetRowOrder = OWCTools.createMenuItem("Reset Row Order", e -> {
					if (this.table.getRowSorter() instanceof Tables.SimplifiedRowSorter simplifiedRowSorter)
						simplifiedRowSorter.resetSortOrder();
					this.table.repaint();
				});

				addContextMenuInvokeListener((comp,x,y)->{
					int clickedRowV = comp!=this.table ? -1 : this.table.rowAtPoint(new Point(x, y));
					clickedRowM = clickedRowV<0 ? -1 : this.table.convertRowIndexToModel(clickedRowV);
					clickedRow = this.tableModel.getRow(clickedRowM);
					clickedRowStationID = clickedRow==null ? null : this.tableModel.getStationIDFromRow(clickedRow);
					
					if (miSwitchToStation!=null)
						miSwitchToStation.setEnabled(clickedRowStationID!=null);
					miStreamStation.setEnabled(clickedRowStationID!=null);
				});
			}
			
			void populateMenu()
			{
				if (miSwitchToStation!=null)
					add(miSwitchToStation);
				add(miStreamStation);
				addSeparator();
				add(miShowColumnWidths);
				add(miResetRowOrder);
			}
		}
	}
	
	private static class BouquetDataPanel
		extends AbstractPanel<
				Bouquet.SubService,
				BouquetDataPanel.BouquetTableModel,
				BouquetDataPanel.BouquetTableModel.ColumnID
		>
	{
		private static final long serialVersionUID = 8680831963757715709L;
		private final JComboBox<Bouquet> bouquetSelector;
		
		BouquetDataPanel(OpenWebifController main)
		{
			super(main,"Global Bouquet Data", SelectionMode.SINGLE_SELECTION, BouquetTableModel::new, TableContextMenu::new, BouquetTableModel.ColumnID.name);
			
			bouquetSelector = new JComboBox<>();
			bouquetSelector.addActionListener(ev -> {
				Bouquet bouquet = bouquetSelector.getItemAt(bouquetSelector.getSelectedIndex());
				tableModel.setData(bouquet==null ? null : bouquet.subservices);
			});
			
			northPanel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			
			c.weightx = 0; northPanel.add(new JLabel("Bouquet: "),c);
			c.weightx = 1; northPanel.add(bouquetSelector,c);
		}

		public void setData(BouquetData bouquetData)
		{
			tableModel.setData((Tables.DataSource<Bouquet.SubService>) null);
			bouquetSelector.setModel(bouquetData==null ? new DefaultComboBoxModel<>() : new DefaultComboBoxModel<>(bouquetData.bouquets));
			bouquetSelector.setSelectedItem(null);
		}

		@Override
		protected String generateRowInfo(Bouquet.SubService row)
		{
			return "Bouquet.SubService";
		}
		
		static class TableContextMenu extends AbstractPanel.TableContextMenu<Bouquet.SubService, BouquetTableModel>
		{
			private static final long serialVersionUID = -5073780433391354857L;

			TableContextMenu(OpenWebifController main, JTable table, BouquetTableModel tableModel)
			{
				super(main, table, tableModel);
				// TODO Auto-generated constructor stub
			}

			@Override
			void populateMenu()
			{
				// TODO Auto-generated constructor stub
				super.populateMenu();
			}
		}

		static class BouquetTableModel
				extends Tables.SimpleGetValueTableModel2<BouquetTableModel, Bouquet.SubService, BouquetTableModel.ColumnID>
				implements ExtraData.Receiver, TableContextMenu.StationIDExtractor<Bouquet.SubService>
		{
			enum ColumnID implements Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<BouquetTableModel, Bouquet.SubService>, SwingConstants
			{
				// Column Widths: [30, 55, 55, 140, 140, 230, 230] in ModelOrder
				pos               (config("Pos"              , Long   .class,  30,   null).setValFunc(s->s.pos)),
				program           (config("Program"          , Long   .class,  55,   null).setValFunc(s->s.program)),
				service_isMarker  (config("is Marker"        , Boolean.class,  55,   null).setValFunc(s->s.service, s->s.isMarker())),
			//	service_label     (config("Label"            , String .class, 140,   null).setValFunc(s->s.service, s->s.label)),
				name              (config("Name"             , String .class, 140,   null).setValFunc(s->s.name)),
				servicereference  (config("Service Reference", String .class, 230,   null).setValFunc(s->s.servicereference)),
			//	service_stationID (config("Station ID"       , String .class, 230,   null).setValFunc(s->s.service, s->s.stationID, stID->stID.toString())),
				stationOccurences (config("Occurences"       , String .class, 230,   null).setValFunc((m,s)->getOccurences(m,s))),
				;
				private final Tables.SimplifiedColumnConfig2<BouquetTableModel, Bouquet.SubService, ?> cfg;
				ColumnID(Tables.SimplifiedColumnConfig2<BouquetTableModel, Bouquet.SubService, ?> cfg) { this.cfg = cfg; }
				@Override public Tables.SimplifiedColumnConfig2<BouquetTableModel, Bouquet.SubService, ?> getColumnConfig() { return this.cfg; }
				@Override public Function<Bouquet.SubService, ?> getGetValue() { return cfg.getValue; }
				@Override public BiFunction<BouquetTableModel, Bouquet.SubService, ?> getGetValueM() { return cfg.getValueM; }
				
				private static <T> Tables.SimplifiedColumnConfig2<BouquetTableModel, Bouquet.SubService, T> config(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment)
				{
					return new Tables.SimplifiedColumnConfig2<>(name, columnClass, 20, -1, prefWidth, prefWidth, horizontalAlignment);
				}
				
				static String getOccurences(BouquetTableModel model, Bouquet.SubService subService)
				{
					return model.extraData==null ? null : model.extraData.getOccurences(subService.servicereference);
				}
			}

			private ExtraData extraData;

			BouquetTableModel()
			{
				super(ColumnID.values());
				extraData = null;
			}

			@Override
			protected BouquetTableModel getThis() { return this; }

			@Override public void setExtraData(ExtraData extraData)
			{
				this.extraData = extraData;
				fireTableColumnUpdate(ColumnID.stationOccurences);
			}

			@Override
			public StationID getStationIDFromRow(Bouquet.SubService row)
			{
				return row==null || row.service==null ? null : row.service.stationID;
			}
		}
	}
	
	private static class BouquetFilePanel
		extends AbstractPanel<
				BouquetFileData.Entry,
				BouquetFilePanel.BouquetFileTableModel,
				BouquetFilePanel.BouquetFileTableModel.ColumnID
		>
	{
		private static final long serialVersionUID = -8228511386678957142L;
		
		private BouquetFileData bouquetFileData;
		private final JTextField fldFile;
		private final JTextField fldName;
		private final JButton btnSetName;
		private final JLabel labFile;
		private final JLabel labName;

		BouquetFilePanel(OpenWebifController main)
		{
			super(main, "Bouquet File", SelectionMode.MULTIPLE_INTERVAL_SELECTION, BouquetFileTableModel::new, null, BouquetFileTableModel.ColumnID.label);
			bouquetFileData = null;
			
			labFile = new JLabel("File: ");
			fldFile = new JTextField();
			fldFile.setEditable(false);
			
			labName = new JLabel("Bouquet Name: ");
			fldName = new JTextField();
			btnSetName = OWCTools.createButton("Set", false, ev -> setName());
			
			northPanel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.gridy = -1;
			
			c.gridx = -1; c.gridy++;
			c.gridx++; c.weightx = 0; c.gridwidth = 1; northPanel.add(labFile,c);
			c.gridx++; c.weightx = 1; c.gridwidth = 2; northPanel.add(fldFile,c);
			
			c.gridx = -1; c.gridy++; c.gridwidth = 1;
			c.gridx++; c.weightx = 0; northPanel.add(labName,c);
			c.gridx++; c.weightx = 1; northPanel.add(fldName,c);
			c.gridx++; c.weightx = 0; northPanel.add(btnSetName,c);
			
			fldName.addActionListener(ev -> setName());
			fldName.addFocusListener(new FocusListener()
			{
				@Override public void focusGained(FocusEvent e) {}
				@Override public void focusLost  (FocusEvent e) { updateBtnSetName(); }
			});
			fldName.addKeyListener(new KeyAdapter() {
				@Override public void keyReleased(KeyEvent e) { updateBtnSetName(); }
			});
			
			updateFileField();
			updateNameField();
		}
		
		void setStationNames(HashMap<String, String> names)
		{
			tableModel.setStationNames(names);
		}

		private void setName()
		{
			if (bouquetFileData!=null)
				bouquetFileData.name = fldName.getText();
			updateBtnSetName();
		}

		void updateFileField()
		{
			fldFile.setEnabled(bouquetFileData!=null && bouquetFileData.file!=null);
			fldFile.setText   (bouquetFileData==null || bouquetFileData.file==null ? null : "%s (%s)".formatted(bouquetFileData.file.getName(), bouquetFileData.file.getParent()) );
		}

		private void updateNameField()
		{
			fldName.setEnabled(bouquetFileData!=null);
			fldName.setText   (bouquetFileData==null ? "" : bouquetFileData.name);
			updateBtnSetName();
		}

		private void updateBtnSetName()
		{
			btnSetName.setEnabled(bouquetFileData==null ? false : !fldName.getText().equals(bouquetFileData.name));
		}

		public void setData(BouquetFileData bouquetFileData)
		{
			this.bouquetFileData = bouquetFileData;
			tableModel.setData(this.bouquetFileData==null ? null : this.bouquetFileData.entries);
			updateFileField();
			updateNameField();
		}

		@Override
		protected String generateRowInfo(BouquetFileData.Entry row)
		{
			return "BouquetFileData.Entry";
		}

		static class BouquetFileTableModel
				extends Tables.SimpleGetValueTableModel2<BouquetFileTableModel, BouquetFileData.Entry, BouquetFileTableModel.ColumnID>
				implements ExtraData.Receiver, AbstractPanel.TableContextMenu.StationIDExtractor<BouquetFileData.Entry>
		{
			enum ColumnID implements Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<BouquetFileTableModel, BouquetFileData.Entry>, SwingConstants
			{
				isMarker          (config("is Marker"  , Boolean.class,  55,   null).setValFunc(   s ->s.isMarker   )),
				label             (config("Label"      , String .class, 140,   null).setValFunc((m,s)->getLabel(m,s))),
				stationSref       (config("Station"    , String .class, 230,   null).setValFunc(   s ->s.stationSref)),
				stationOccurences (config("Occurences" , String .class, 230,   null).setValFunc((m,s)->getOccurences(m,s))),
				;
				private final Tables.SimplifiedColumnConfig2<BouquetFileTableModel, BouquetFileData.Entry, ?> cfg;
				ColumnID(Tables.SimplifiedColumnConfig2<BouquetFileTableModel, BouquetFileData.Entry, ?> cfg) { this.cfg = cfg; }
				@Override public Tables.SimplifiedColumnConfig2<BouquetFileTableModel, BouquetFileData.Entry, ?> getColumnConfig() { return this.cfg; }
				@Override public Function<BouquetFileData.Entry, ?> getGetValue() { return cfg.getValue; }
				@Override public BiFunction<BouquetFileTableModel, BouquetFileData.Entry, ?> getGetValueM() { return cfg.getValueM; }
				
				private static <T> Tables.SimplifiedColumnConfig2<BouquetFileTableModel, BouquetFileData.Entry, T> config(String name, Class<T> columnClass, int prefWidth, Integer horizontalAlignment)
				{
					return new Tables.SimplifiedColumnConfig2<>(name, columnClass, 20, -1, prefWidth, prefWidth, horizontalAlignment);
				}
				
				private static String getLabel(BouquetFileTableModel model, BouquetFileData.Entry entry)
				{
					if (entry.isMarker)
						return entry.markerText;
					
					String sref = entry.stationSref;
					if (model.stationNames==null || sref==null)
						return null;
					
					if (sref.endsWith(":"));
						sref = sref.substring(0, sref.length()-1);
					
					return model.stationNames.get(sref);
				}
				
				private static String getOccurences(BouquetFileTableModel model, BouquetFileData.Entry entry)
				{
					return entry.isMarker || model.extraData==null ? null : model.extraData.getOccurences(entry.stationSref);
				}
			}

			private HashMap<String, String> stationNames;
			private ExtraData extraData;

			BouquetFileTableModel()
			{
				super(ColumnID.values());
				stationNames = null;
				extraData = null;
			}

			void setStationNames(HashMap<String, String> stationNames)
			{
				this.stationNames = stationNames;
				fireTableColumnUpdate(ColumnID.label);
			}

			@Override protected BouquetFileTableModel getThis() { return this; }

			@Override
			public void setExtraData(ExtraData extraData)
			{
				this.extraData = extraData;
			}

			@Override
			public StationID getStationIDFromRow(BouquetFileData.Entry row)
			{
				return row==null ? null : row.stationID;
			}
		}
	}
	
	private static class BouquetFileData
	{
		File file;
		String name;
		final List<Entry> entries;
		
		BouquetFileData()
		{
			file = null;
			name = "";
			entries = new Vector<>();
		}
		
		BouquetFile buildBouquetFile()
		{
			return new BouquetFile(
					name,
					entries
						.stream()
						.<BouquetFile.Entry>map(e -> e==null ? null : e.isMarker ? new BouquetFile.Marker(e.markerText) : new BouquetFile.Station(e.stationSref))
						.toArray(BouquetFile.Entry[]::new)
			);
		}

		static BouquetFileData getFrom(BouquetFile bouquetFile, File file)
		{
			if (bouquetFile==null)
				return null;
			
			BouquetFileData bfd = new BouquetFileData();
			bfd.file = file;
			bfd.name = bouquetFile.name;
			bfd.entries.addAll(
					Arrays
						.stream(bouquetFile.entries)
						.map(Entry::new)
						.toList()
			);
			
			return bfd;
		}

		static class Entry
		{
			private final boolean isMarker;
			private String markerText;
			private final String stationSref;
			private final StationID stationID;

			Entry(BouquetFile.Entry entry)
			{
				if (entry instanceof BouquetFile.Marker marker)
				{
					isMarker = true;
					markerText = marker.text();
				}
				else
				{
					isMarker = false;
					markerText = null;
				}
				
				if (entry instanceof BouquetFile.Station station)
				{
					stationSref = station.sref();
					stationID = StationID.parseIDStr(stationSref);
				}
				else
				{
					stationSref = null;
					stationID = null;
				}
			}
			
		}
	}
	
	private static class ExtraData
	{
		interface Receiver
		{
			void setExtraData(ExtraData extraData);
		}
		
		final Map<String, String> bouquetNames = new HashMap<>();
		final Map<String, Set<String>> stationOccurences = new HashMap<>();
		
		static ExtraData generate(BouquetData bouquetData)
		{
			ExtraData extraData = new ExtraData();
			if (bouquetData!=null)
				for (Bouquet bouquet : bouquetData.bouquets)
				{
					extraData.bouquetNames.put(bouquet.servicereference, bouquet.name);
					for (Bouquet.SubService subservice : bouquet.subservices)
					{
						extraData.stationOccurences
							.computeIfAbsent(subservice.servicereference, sref -> new HashSet<>())
							.add(bouquet.servicereference);
					}
				}
			return extraData;
		}
		
		String getOccurences(String sref)
		{
			if (sref==null)
				return null;
			
			Set<String> bouquetSrefs = stationOccurences.get(sref);
			if (bouquetSrefs==null)
				return null;
			
			return bouquetSrefs
				.stream()
				.map(bouquetNames::get)
				.filter(str->str!=null)
				.sorted()
				.collect(Collectors.joining(", "));
		}
	}
}
