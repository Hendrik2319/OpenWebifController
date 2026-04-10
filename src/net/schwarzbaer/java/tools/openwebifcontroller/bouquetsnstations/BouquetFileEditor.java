package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
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
import net.schwarzbaer.java.lib.gui.KeyShortCut;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.ScrollPosition;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
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
	// TODO: add optional display of picons to both tables 
	
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
		
		mainWindow = OpenWebifController.createMainWindow("Bouquet File Editor", !startedStandAlone);
		mainWindow.startGUI(contentPane, menuBar);
		
		OpenWebifController.settings.registerExtraWindow(
				mainWindow,
				AppSettings.ValueKey.BouquetFileEditor_WindowX,
				AppSettings.ValueKey.BouquetFileEditor_WindowY,
				AppSettings.ValueKey.BouquetFileEditor_WindowWidth,
				AppSettings.ValueKey.BouquetFileEditor_WindowHeight
		);
		OpenWebifController.settings.registerSplitPaneDividers(
				new SplitPaneDividersDefinition<>(mainWindow, AppSettings.ValueKey.class)
				.add(contentPane, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_ContentPane)
				.add(contentPane.bouquetDataPanel.tableTextAreaPanel, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_BouquetDataPanel)
				.add(contentPane.bouquetFilePanel.tableTextAreaPanel, AppSettings.ValueKey.SplitPaneDivider_BouquetFileEditor_BouquetFilePanel)
		);
		
		setData(this.bouquetData);
		contentPane.bouquetFilePanel.setData(this.bouquetFileData);
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
			bouquetDataPanel.setOtherPanel(bouquetFilePanel);
			bouquetFilePanel.setOtherPanel(bouquetDataPanel);
		}
	}
	
	private static abstract class AbstractPanel<
			RowType,
			TableModelType extends Tables.SimpleGetValueTableModel<RowType, ColumnID> & ExtraData.Receiver & AbstractPanel.TableContextMenu.StationIDExtractor<RowType>,
			ColumnID extends Tables.AbstractGetValueTableModel.ColumnIDTypeInt<RowType>,
			ThisType extends AbstractPanel<RowType, TableModelType, ColumnID, ThisType>
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
		OpenWebifController main;
		int[] selectedRowIndexes;
		RowType[] selectedRows;
		
		AbstractPanel(
				OpenWebifController main,
				String borderTitle,
				SelectionMode tableSelectionMode,
				Supplier<TableModelType> tableModelConstructor,
				TableContextMenu.Constructor<RowType, ThisType, TableModelType> tableContextMenuConstructor,
				ColumnID contextMenuSurrogateColumn,
				IntFunction<RowType[]> createRowArray
		)
		{
			super(new BorderLayout(3, 3));
			this.main = main;
			setBorder(BorderFactory.createTitledBorder(borderTitle));
			
			Objects.requireNonNull( tableSelectionMode );
			Objects.requireNonNull( tableModelConstructor );
			Objects.requireNonNull( tableContextMenuConstructor );
			Objects.requireNonNull( createRowArray );
			selectedRowIndexes = new int[0];
			selectedRows = createRowArray.apply(0);
			
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
				selectedRowIndexes = Arrays
						.stream(this.table.getSelectedRows())
						.map(rowV -> rowV<0 ? -1 : this.table.convertRowIndexToModel(rowV))
						.filter(rowM -> rowM>=0)
						.toArray();
				selectedRows = Arrays
						.stream(selectedRowIndexes)
						.mapToObj(this.tableModel::getRow)
						.filter(row->row!=null)
						.toArray(createRowArray);
				
				String text = selectedRows.length==1 ? generateRowInfo(selectedRows[0]) : "";
				ScrollPosition.keepScrollPos(textareaScrollPane, ScrollPosition.ScrollBarType.Vertical, ()->textArea.setText(text));
			});
			
			northPanel = new JPanel();
			tableTextAreaPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,true);
			tableTextAreaPanel.setTopComponent(tableScrollPane);
			tableTextAreaPanel.setBottomComponent(textareaScrollPane);
			tableTextAreaPanel.setResizeWeight(1);
			
			add(northPanel, BorderLayout.NORTH);
			add(tableTextAreaPanel, BorderLayout.CENTER);
			
			TableContextMenu<?,?,?> contextMenu = tableContextMenuConstructor.create(this.main, getThis(), tableModel);
			contextMenu.populateMenu();
			contextMenu.addTo(table, () -> ContextMenu.computeSurrogateMousePos(table, tableScrollPane, tableModel.getColumn(contextMenuSurrogateColumn)));
			contextMenu.addTo(tableScrollPane);
		}
		
		protected abstract ThisType getThis();
		protected abstract String generateRowInfo(RowType row);

		protected static class TableContextMenu<
				RowType,
				TablePanelType extends AbstractPanel<RowType,?,?,TablePanelType>,
				TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableContextMenu.StationIDExtractor<RowType>
		> extends ContextMenu
		{
			private static final long serialVersionUID = 5456765972894773723L;
			
			interface Constructor<
					RowType,
					TablePanelType extends AbstractPanel<RowType,?,?,TablePanelType>,
					TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableContextMenu.StationIDExtractor<RowType>
			> {
				TableContextMenu<RowType, TablePanelType, TableModelType> create(OpenWebifController main, TablePanelType tablePanel, TableModelType tableModel);
			}
			
			interface StationIDExtractor<RowType>
			{
				StationID getStationIDFromRow(RowType row);
			}
			
			protected final JTable table;
			protected final TablePanelType tablePanel;
			protected final TableModelType tableModel;
			private   final JMenuItem miSwitchToStation;
			private   final JMenuItem miStreamStation;
			private   final JMenuItem miShowColumnWidths;
			private   final JMenuItem miResetRowOrder;
			protected int clickedRowIndex;
			protected RowType clickedRow;
			protected StationID clickedRowStationID;

			TableContextMenu(OpenWebifController main, TablePanelType tablePanel, TableModelType tableModel)
			{
				this.tablePanel = tablePanel;
				this.table      = tablePanel.table;
				this.tableModel = tableModel;
				clickedRowIndex = -1;
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
					clickedRowIndex = clickedRowV<0 ? -1 : this.table.convertRowIndexToModel(clickedRowV);
					clickedRow      = this.tableModel.getRow(clickedRowIndex);
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
				BouquetDataPanel.BouquetTableModel.ColumnID,
				BouquetDataPanel
		>
	{
		private static final long serialVersionUID = 8680831963757715709L;
		private final JComboBox<Bouquet> bouquetSelector;
		private final JLabel labBouquetSelector;
		private BouquetFilePanel bouquetFilePanel;
		
		BouquetDataPanel(OpenWebifController main)
		{
			super(main,"Global Bouquet Data", SelectionMode.SINGLE_SELECTION, BouquetTableModel::new, TableContextMenu::new, BouquetTableModel.ColumnID.name, Bouquet.SubService[]::new);
			bouquetFilePanel = null;
			
			labBouquetSelector = new JLabel("Bouquet: ");
			bouquetSelector = new JComboBox<>();
			bouquetSelector.addActionListener(ev -> {
				Bouquet bouquet = bouquetSelector.getItemAt(bouquetSelector.getSelectedIndex());
				tableModel.setData(bouquet==null ? null : bouquet.subservices);
			});
			
			northPanel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			
			c.weightx = 0; northPanel.add(labBouquetSelector,c);
			c.weightx = 1; northPanel.add(bouquetSelector,c);
			
			labBouquetSelector.setEnabled(false);
			bouquetSelector.setEnabled(false);
		}

		@Override protected BouquetDataPanel getThis() { return this; }

		void setOtherPanel(BouquetFilePanel bouquetFilePanel)
		{
			this.bouquetFilePanel = bouquetFilePanel;
			tableModel.setOtherPanel(bouquetFilePanel);
		}
		
		void notifyBouquetFileChange(BouquetFileChangeEvent ev)
		{
			switch (ev.eventType)
			{
			case FileChanged:
			case StationsAdded:
			case StationsRemoved:
				tableModel.fireTableColumnUpdate(BouquetTableModel.ColumnID.isInFile);
				break;
			}
		}

		void setData(BouquetData bouquetData)
		{
			tableModel.setData((Tables.DataSource<Bouquet.SubService>) null);
			bouquetSelector.setModel(bouquetData==null ? new DefaultComboBoxModel<>() : new DefaultComboBoxModel<>(bouquetData.bouquets));
			bouquetSelector.setSelectedItem(null);
			labBouquetSelector.setEnabled(bouquetData!=null);
			bouquetSelector   .setEnabled(bouquetData!=null);
		}

		@Override
		protected String generateRowInfo(Bouquet.SubService row)
		{
			return "Bouquet.SubService";
		}
		
		static class TableContextMenu extends AbstractPanel.TableContextMenu<Bouquet.SubService, BouquetDataPanel, BouquetTableModel>
		{
			private static final long serialVersionUID = -5073780433391354857L;
			
			private final JMenuItem miCopyToFileBefore;
			private final JMenuItem miCopyToFileAfter;
			private final JMenuItem miCopyToFileEnd;

			TableContextMenu(OpenWebifController main, BouquetDataPanel tablePanel, BouquetTableModel tableModel)
			{
				super(main, tablePanel, tableModel);
				
				miCopyToFileBefore = OWCTools.createMenuItem("Copy to File (before selected)", ev -> copyToFile(i -> i));
				miCopyToFileAfter  = OWCTools.createMenuItem("Copy to File (after selected)" , ev -> copyToFile(i -> i+1));
				miCopyToFileEnd    = OWCTools.createMenuItem("Copy to File (after selected)" , ev -> copyToFileAtEnd());
				
				addContextMenuInvokeListener((comp,x,y) -> {
					boolean bfpHasFileData       = this.tablePanel.bouquetFilePanel!=null && this.tablePanel.bouquetFilePanel.bouquetFileData!=null;
					boolean thisHasSelectedRows  = this.tablePanel.selectedRows.length>0;
					boolean canAdd    = bfpHasFileData && thisHasSelectedRows;
					boolean canInsert = canAdd && this.tablePanel.bouquetFilePanel.selectedRowIndexes.length==1;
					miCopyToFileBefore.setEnabled(canInsert);
					miCopyToFileAfter .setEnabled(canInsert);
					miCopyToFileEnd   .setEnabled(canAdd);
				});
			}

			private void copyToFileAtEnd()
			{
				if (this.tablePanel.bouquetFilePanel!=null && this.tablePanel.bouquetFilePanel.bouquetFileData!=null && this.tablePanel.selectedRows.length>0)
				{
					String[] srefArr = Arrays
							.stream(this.tablePanel.selectedRows)
							.map(row->row.servicereference)
							.filter(sref->sref!=null)
							.distinct()
							.toArray(String[]::new);
					
					this.tablePanel.bouquetFilePanel.addStations(srefArr);
				}
			}

			private void copyToFile(IntUnaryOperator getTargetIndexFromSelectedIndex)
			{
				if (this.tablePanel.bouquetFilePanel!=null && this.tablePanel.bouquetFilePanel.bouquetFileData!=null && this.tablePanel.bouquetFilePanel.selectedRowIndexes.length==1 && this.tablePanel.selectedRows.length>0)
				{
					int selectedIndex = this.tablePanel.bouquetFilePanel.selectedRowIndexes[0];
					int targetIndex = getTargetIndexFromSelectedIndex.applyAsInt(selectedIndex);
					
					String[] srefArr = Arrays
							.stream(this.tablePanel.selectedRows)
							.map(row->row.servicereference)
							.filter(sref->sref!=null)
							.distinct()
							.toArray(String[]::new);
					
					this.tablePanel.bouquetFilePanel.insertStations(srefArr, targetIndex);
				}
			}

			@Override
			void populateMenu()
			{
				add(miCopyToFileBefore);
				add(miCopyToFileAfter);
				addSeparator();
				super.populateMenu();
			}
		}

		static class BouquetTableModel
				extends Tables.SimpleGetValueTableModel2<BouquetTableModel, Bouquet.SubService, BouquetTableModel.ColumnID>
				implements ExtraData.Receiver, TableContextMenu.StationIDExtractor<Bouquet.SubService>
		{
			enum ColumnID implements Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<BouquetTableModel, Bouquet.SubService>, SwingConstants
			{
				// Column Widths: [30, 55, 55, 140, 230, 230, 50] in ModelOrder
				pos               (config("Pos"              , Long   .class,  30,   null).setValFunc(s->s.pos)),
				program           (config("Program"          , Long   .class,  55,   null).setValFunc(s->s.program)),
				service_isMarker  (config("is Marker"        , Boolean.class,  55,   null).setValFunc(s->s.service, s->s.isMarker())),
			//	service_label     (config("Label"            , String .class, 140,   null).setValFunc(s->s.service, s->s.label)),
				name              (config("Name"             , String .class, 140,   null).setValFunc(s->s.name)),
				servicereference  (config("Service Reference", String .class, 230,   null).setValFunc(s->s.servicereference)),
			//	service_stationID (config("Station ID"       , String .class, 230,   null).setValFunc(s->s.service, s->s.stationID, stID->stID.toString())),
				stationOccurences (config("Occurences"       , String .class, 230,   null).setValFunc((m,s)->getOccurences(m,s))),
				isInFile          (config("is in File"       , Boolean.class,  50,   null).setValFunc((m,s)->isInFile(m,s))),
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
				
				static Boolean isInFile(BouquetTableModel model, Bouquet.SubService subService)
				{
					if (model.bouquetFilePanel==null) return null;
					return model.bouquetFilePanel.tableModel.isSrefInFile(subService.servicereference);
				}
			}

			private ExtraData extraData;
			private BouquetFilePanel bouquetFilePanel;

			BouquetTableModel()
			{
				super(ColumnID.values());
				extraData = null;
				bouquetFilePanel = null;
			}

			void setOtherPanel(BouquetFilePanel bouquetFilePanel)
			{
				this.bouquetFilePanel = bouquetFilePanel;
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
				BouquetFilePanel.BouquetFileTableModel.ColumnID,
				BouquetFilePanel
		>
	{
		private static final long serialVersionUID = -8228511386678957142L;
		
		private BouquetFileData bouquetFileData;
		private final JTextField fldFile;
		private final JTextField fldName;
		private final JButton btnSetName;
		private final JLabel labFile;
		private final JLabel labName;
		private BouquetDataPanel bouquetDataPanel;

		BouquetFilePanel(OpenWebifController main)
		{
			super(main, "Bouquet File", SelectionMode.SINGLE_SELECTION, BouquetFileTableModel::new, TableContextMenu::new, BouquetFileTableModel.ColumnID.label, BouquetFileData.Entry[]::new);
			bouquetFileData = null;
			bouquetDataPanel = null;
			
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
			
			this.table.addKeyListener(new TableKeyListener());
			
			updateFileField();
			updateNameField();
		}
		
		boolean addStations(String[] srefArr)
		{
			if (bouquetFileData==null)
				return false;
			
			int startIndex = bouquetFileData.entries.size();
			for (int i=0; i<srefArr.length; i++)
				bouquetFileData.entries.add(BouquetFileData.Entry.createStation(srefArr[i]));
			
			if (srefArr.length>0)
			{
				this.tableModel.fireTableRowsAdded(startIndex, startIndex+srefArr.length-1);
				if (bouquetDataPanel!=null)
					bouquetDataPanel.notifyBouquetFileChange(BouquetFileChangeEvent.Type.StationsAdded.createEvent());
			}
			
			return true;
		}

		boolean insertStations(String[] srefArr, int index)
		{
			if (bouquetFileData==null)
				return false;
			
			if (index<0 || index>bouquetFileData.entries.size())
				return false;
			
			for (int i=0; i<srefArr.length; i++)
				bouquetFileData.entries.insertElementAt(BouquetFileData.Entry.createStation(srefArr[i]), index+i);
			
			if (srefArr.length>0)
			{
				this.tableModel.fireTableRowsAdded(index, index+srefArr.length-1);
				if (bouquetDataPanel!=null)
					bouquetDataPanel.notifyBouquetFileChange(BouquetFileChangeEvent.Type.StationsAdded.createEvent());
			}
			
			return true;
		}

		@Override protected BouquetFilePanel getThis() { return this; }

		void setOtherPanel(BouquetDataPanel bouquetDataPanel)
		{
			this.bouquetDataPanel = bouquetDataPanel;
			tableModel.setOtherPanel(bouquetDataPanel);
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
			boolean hasFile = bouquetFileData!=null && bouquetFileData.file!=null;
			labFile.setEnabled(hasFile);
			fldFile.setEnabled(hasFile);
			fldFile.setText(!hasFile ? null : "%s (%s)".formatted(bouquetFileData.file.getName(), bouquetFileData.file.getParent()) );
		}

		private void updateNameField()
		{
			boolean hasData = bouquetFileData!=null;
			labName.setEnabled(hasData);
			fldName.setEnabled(hasData);
			fldName.setText(!hasData ? "" : bouquetFileData.name);
			updateBtnSetName();
		}

		private void updateBtnSetName()
		{
			btnSetName.setEnabled(bouquetFileData==null ? false : !fldName.getText().equals(bouquetFileData.name));
		}

		void setData(BouquetFileData bouquetFileData)
		{
			this.bouquetFileData = bouquetFileData;
			tableModel.setData(this.bouquetFileData==null ? null : this.bouquetFileData.entries);
			updateFileField();
			updateNameField();
			if (bouquetDataPanel!=null) bouquetDataPanel.notifyBouquetFileChange(new BouquetFileChangeEvent(BouquetFileChangeEvent.Type.FileChanged));
		}

		@Override
		protected String generateRowInfo(BouquetFileData.Entry row)
		{
			return "BouquetFileData.Entry";
		}
		
		void moveRow(int inc)
		{
			if (selectedRowIndexes.length==1)
			{
				int selectedIndex = selectedRowIndexes[0];
				boolean success = tableModel.swapRows(selectedIndex,selectedIndex+inc);
				if (success)
					table.setRowSelectionInterval(selectedIndex+inc, selectedIndex+inc);
			}
		}
		
		enum KeyFunction implements KeyShortCut.Container
		{
			MoveUp  (new KeyShortCut(KeyEvent.VK_UP  , false, false, true, false)),
			MoveDown(new KeyShortCut(KeyEvent.VK_DOWN, false, false, true, false)),
			;
			final KeyShortCut keyShortCut;
			KeyFunction(KeyShortCut keyShortCut)
			{
				this.keyShortCut = keyShortCut;
			}
			@Override public KeyShortCut getKeyShortCut()
			{
				return keyShortCut;
			}
			static KeyFunction getFrom(KeyEvent e)
			{
				return KeyShortCut.getFrom(e, values());
			}
		}
		
		class TableKeyListener implements KeyListener
		{
			@Override public void keyTyped   (KeyEvent e) {}
			@Override public void keyReleased(KeyEvent e) {}

			@Override public void keyPressed(KeyEvent e)
			{
				KeyFunction keyFunction = KeyFunction.getFrom(e);
				if (keyFunction==null) return;
				
				switch (keyFunction)
				{
				case MoveUp  : moveRow(-1); break;
				case MoveDown: moveRow(+1); break;
				}
			}
		}
		
		static class TableContextMenu extends AbstractPanel.TableContextMenu<BouquetFileData.Entry, BouquetFilePanel, BouquetFileTableModel>
		{
			private static final long serialVersionUID = 288857167551760006L;
			private JMenuItem miMoveUp;
			private JMenuItem miMoveDown;

			TableContextMenu(OpenWebifController main, BouquetFilePanel tablePanel, BouquetFileTableModel tableModel)
			{
				super(main, tablePanel, tableModel);
				
				miMoveUp   = OWCTools.createMenuItem(KeyFunction.MoveUp  .addKeyLabel("Move Up"  ), GrayCommandIcons.IconGroup.Up  , ev -> this.tablePanel.moveRow(-1));
				miMoveDown = OWCTools.createMenuItem(KeyFunction.MoveDown.addKeyLabel("Move Down"), GrayCommandIcons.IconGroup.Down, ev -> this.tablePanel.moveRow(+1));
				
				addContextMenuInvokeListener((comp,x,y) -> {
					miMoveUp  .setEnabled(this.tablePanel.selectedRowIndexes.length==1 && this.tablePanel.selectedRowIndexes[0]>0);
					miMoveDown.setEnabled(this.tablePanel.selectedRowIndexes.length==1 && this.tablePanel.selectedRowIndexes[0]+1<this.tableModel.getRowCount());
				});
			}

			@Override
			void populateMenu()
			{
				add(miMoveUp  );
				add(miMoveDown);
				addSeparator();
				super.populateMenu();
			}
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
			@SuppressWarnings("unused")
			private BouquetDataPanel bouquetDataPanel;

			BouquetFileTableModel()
			{
				super(ColumnID.values());
				stationNames = null;
				extraData = null;
				bouquetDataPanel = null;
			}

			boolean swapRows(int index1, int index2)
			{
				boolean swapped = dataSource.swapRows(index1, index2);
				if (swapped)
				{
					fireTableRowUpdate(index1);
					fireTableRowUpdate(index2);
				}
				return swapped;
			}

			boolean isSrefInFile(String sref)
			{
				if (sref!=null)
					for (BouquetFileData.Entry entry : dataSource)
						if (sref.equals(entry.stationSref))
							return true;
				return false;
			}

			void setOtherPanel(BouquetDataPanel bouquetDataPanel)
			{
				this.bouquetDataPanel = bouquetDataPanel;
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

			@Override
			protected void fireTableRowsAdded(int firstRowIndex, int lastRowIndex)
			{
				super.fireTableRowsAdded(firstRowIndex, lastRowIndex);
			}
		}
	}
	
	private static class BouquetFileChangeEvent
	{
		enum Type
		{
			FileChanged, StationsRemoved, StationsAdded;
			BouquetFileChangeEvent createEvent()
			{
				return new BouquetFileChangeEvent(this);
			}
		}

		final Type eventType;

		BouquetFileChangeEvent(Type eventType)
		{
			this.eventType = eventType;
		}
	}
	
	private static class BouquetFileData
	{
		File file;
		String name;
		final Vector<Entry> entries;
		
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
			
			private Entry(String markerText, String stationSref)
			{
				this.markerText = markerText;
				this.stationSref = stationSref;
				isMarker = this.markerText!=null;
				stationID = StationID.parseIDStr(this.stationSref);
			}

			static Entry createStation(String stationSref)
			{
				return new Entry(null, stationSref);
			}

			@SuppressWarnings("unused")
			static Entry createMarker(String markerText)
			{
				return new Entry(markerText, null);
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
