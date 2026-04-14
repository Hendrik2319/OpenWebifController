package net.schwarzbaer.java.tools.openwebifcontroller.bouquetsnstations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
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
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
import net.schwarzbaer.java.lib.gui.ImageView;
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
import net.schwarzbaer.java.tools.openwebifcontroller.ListenerController;
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
	private final ListenerController listenerController;
	private final LocalPiconCache localPiconCache;

	private BouquetFileEditor(boolean startedStandAlone, OpenWebifController main, BouquetData bouquetData, Consumer<BouquetFileEditor> updateBouquetData)
	{
		this.bouquetData = bouquetData;
		this.updateBouquetData = updateBouquetData!=null ? updateBouquetData : BouquetFileEditor::updateBouquetData;
		this.bouquetFileData = null;
		
		localPiconCache = new LocalPiconCache();
		
		contentPane = new ContentPane(main, localPiconCache);
		menuBar = new MenuBar();
		
		mainWindow = OpenWebifController.createMainWindow("Bouquet File Editor", !startedStandAlone);
		mainWindow.startGUI(contentPane, menuBar);
		listenerController = ListenerController.createFor(mainWindow);
		
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
		
		listenerController.addListener(PiconLoader.getInstance(), new PiconLoader.Listener() {
			@Override public void updatePicon(StationID stationID, BufferedImage piconImage) {
				localPiconCache.set(stationID, piconImage);
				contentPane.bouquetDataPanel.tableModel.updatePiconColumn();
				contentPane.bouquetFilePanel.tableModel.updatePiconColumn();
			}
			@Override public void showMessage(String msg, int duration_ms) {
				// TODO Auto-generated method stub
			}
		});
	}
	
	private String getBaseURL()
	{
		return OpenWebifController.getBaseURL(true, mainWindow);
	}

	private static void updateBouquetData(BouquetFileEditor editor)
	{
		String baseURL = editor.getBaseURL();
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
		BouquetDataExtracts bouquetDataExtracts = new BouquetDataExtracts(bouquetData);
		contentPane.bouquetDataPanel.setData(this.bouquetData);
		contentPane.bouquetDataPanel.setBouquetDataExtracts(bouquetDataExtracts);
		contentPane.bouquetFilePanel.setBouquetDataExtracts(bouquetDataExtracts);
		menuBar.updateMiLoadBouquetData();
	}
	
	private static <V> V[] removeFromArr(V[] values, IntFunction<V[]> createArray, boolean removeEnabled, List<V> valuesToRemove)
	{
		return !removeEnabled ? values : Arrays
			.stream(values)
			.filter(v -> !valuesToRemove.contains(v))
			.toArray(createArray);
	}

	private class MenuBar extends JMenuBar
	{
		private static final long serialVersionUID = 2454464114876630856L;
		private final JMenu dataMenu;
		private final JMenu viewMenu;
		private final JMenuItem miLoadBouquetData;
		private final JMenuItem miSaveBouquetFile;
		private final JMenuItem miSaveBouquetFileAsRadio;
		private final JMenuItem miSaveBouquetFileAsTV;
		private final FileChooser fileChooser_Radio;
		private final FileChooser fileChooser_TV;
		
		MenuBar()
		{
			fileChooser_Radio = new FileChooser("Radio Bouquet", "radio");
			fileChooser_TV    = new FileChooser("TV Bouquet", "tv");
			
			add(dataMenu = new JMenu("Data"));
			
			dataMenu.add(miLoadBouquetData = OWCTools.createMenuItem("##", ev -> {
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
			
			add(viewMenu = new JMenu("View"));
			
			viewMenu.add(OWCTools.createCheckBoxMenuItem("Show Picons", false, isChecked->{
				contentPane.bouquetDataPanel.setShowPicons(isChecked);
				contentPane.bouquetFilePanel.setShowPicons(isChecked);
				String baseURL;
				if (isChecked && (baseURL=getBaseURL())!=null)
				{
					contentPane.bouquetDataPanel.addTasksToPiconLoader(baseURL);
					contentPane.bouquetFilePanel.addTasksToPiconLoader(baseURL);
				}
			}));
			
			viewMenu.add(OWCTools.createMenuItem("Clear Picons Cache", GrayCommandIcons.IconGroup.Delete, ev -> {
				PiconLoader.getInstance().clearPiconCache();
				localPiconCache.clear();
				contentPane.repaint();
			}));
			
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
			miLoadBouquetData.setText(bouquetData==null ? "Load Bouquet Data from STB" : "Reload Bouquet Data from STB");
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
	
	private static class LocalPiconCache
	{
		// 330 x 198
		private static final int RAW_IMAGE_HEIGHT = 198;
		private static final int RAW_IMAGE_WIDTH  = 330;
		static final int PICON_HEIGHT = 30;
		static final int PICON_WIDTH  = PICON_HEIGHT * RAW_IMAGE_WIDTH / RAW_IMAGE_HEIGHT;
		
		private final Map<String,BufferedImage> cache = new HashMap<>();

		void clear()
		{
			cache.clear();
		}

		BufferedImage get(StationID stationID)
		{
			return stationID==null ? null : cache.get(stationID.toIDStr());
		}

		void set(StationID stationID, BufferedImage piconImage)
		{
			if (stationID==null) return;
			String key = stationID.toIDStr();
			
			if (piconImage==null)
				cache.remove(key);
			else
			{
				//BufferedImage scaledImg = ImageTools.scale(piconImage, PICON_WIDTH, PICON_HEIGHT);
				BufferedImage scaledImg = ImageView.computeScaledImageByAreaSampling(setBgColor(piconImage, Color.BLACK), PICON_WIDTH, PICON_HEIGHT, true);
				cache.put(key, scaledImg);
			}
		}

		private static BufferedImage setBgColor(BufferedImage image, Color bgColor)
		{
			if (image==null || bgColor==null)
				return image;
			
			BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics g = newImage.getGraphics();
			g.drawImage(image, 0, 0, bgColor, null);
			
			return newImage;
		}
	}
	
	private static class PiconRenderer<RowType, ColumnIDType> extends Tables.GraphicRendererComponent<BufferedImage> implements Tables.GeneralizedTableCellRenderer2.ExtraRenderer<RowType, ColumnIDType>
	{
		private static final long serialVersionUID = -4899151173810183590L;
		static final int TABLE_ROW_HEIGHT   = LocalPiconCache.PICON_HEIGHT + 2*2 + 1;
		static final int TABLE_COLUMN_WIDTH = LocalPiconCache.PICON_WIDTH  + 2*2 + 1;
		
		private BufferedImage picon;

		PiconRenderer()
		{
			super(BufferedImage.class);
			picon = null;
		}
		
		@Override
		public Component getTableCellRendererComponent(
				JTable table, Object value,
				RowType row, int rowM, int columnM, ColumnIDType columnID,
				boolean isSelected, boolean hasFocus, Supplier<Color> getCustomBackground, Supplier<Color> getCustomForeground)
		{
			configureAsTableCellRendererComponent(table, value, isSelected, hasFocus, getCustomBackground, getCustomForeground);
			return this;
		}

		@Override
		protected void setValue(BufferedImage picon, JTable table, JList<?> list, Integer listIndex, boolean isSelected, boolean hasFocus)
		{
			this.picon = picon;
		}

		@Override
		protected void paintContent(Graphics g, int x, int y, int width, int height)
		{
			//g.drawImage(picon, x, y, width, height, Color.BLACK, null);
			g.drawImage(picon, x, y, Color.BLACK, null);
		}
	}
	
	private static class ContentPane extends JSplitPane
	{
		private static final long serialVersionUID = -8404621947710308787L;
		final BouquetDataPanel bouquetDataPanel;
		final BouquetFilePanel bouquetFilePanel;

		ContentPane(OpenWebifController main, LocalPiconCache localPiconCache)
		{
			super(JSplitPane.HORIZONTAL_SPLIT,true);
			setLeftComponent (bouquetDataPanel = new BouquetDataPanel(main, localPiconCache));
			setRightComponent(bouquetFilePanel = new BouquetFilePanel(main, localPiconCache));
			setResizeWeight(0.5);
			bouquetDataPanel.setOtherPanel(bouquetFilePanel);
			bouquetFilePanel.setOtherPanel(bouquetDataPanel);
		}
	}
	
	private static abstract class AbstractPanel<
			RowType,
			TableModelType extends Tables.SimpleGetValueTableModel2<TableModelType, RowType, ColumnID> & AbstractPanel.TableModelMethods<ColumnID>,
			ColumnID extends Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<TableModelType, RowType>,
			ThisType extends AbstractPanel<RowType, TableModelType, ColumnID, ThisType>
	> extends JPanel
	{
		private static final long serialVersionUID = -5359258011833246051L;
		protected static final Color BGCOLOR_MARKER   = new Color(0xE0FFEB);
		protected static final Color BGCOLOR_EDITABLE = new Color(0xFFF9E0);
		
		enum SelectionMode
		{
			SINGLE_SELECTION           (ListSelectionModel.SINGLE_SELECTION           ),
			SINGLE_INTERVAL_SELECTION  (ListSelectionModel.SINGLE_INTERVAL_SELECTION  ),
			MULTIPLE_INTERVAL_SELECTION(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION),
			;
			final int value;
			SelectionMode(int value) { this.value = value;}
		}
		
		interface TableModelMethods<ColumnID>
		{
			void updateAfterChangeOfBouquetDataExtracts();
			void enablePiconColumn(boolean isEnabled);
			void fireTableStructureUpdate_();
			void updatePiconColumn();
			ColumnID[] getAllColumns();
		}
		
		interface TableModelConstructor<TableModelType, ParentPanelType>
		{
			TableModelType create(ParentPanelType parentPanel);
		}
		
		final OpenWebifController main;
		final LocalPiconCache localPiconCache;
		final JPanel northPanel;
		final JSplitPane tableTextAreaPanel;
		final JTable table;
		final TableModelType tableModel;
		final Tables.GeneralizedTableCellRenderer2<RowType, ColumnID, TableModelType> tableCellRenderer;
		final JScrollPane tableScrollPane;
		final JTextArea textArea;
		final JScrollPane textareaScrollPane;
		
		final int defaultRowHeight;
		int[] selectedRowIndexes;
		RowType[] selectedRows;
		BouquetDataExtracts bouquetDataExtracts;
		
		AbstractPanel(
				OpenWebifController main,
				LocalPiconCache localPiconCache,
				String borderTitle,
				SelectionMode tableSelectionMode,
				TableModelConstructor<TableModelType,ThisType> tableModelConstructor,
				Class<TableModelType> tableModelClass,
				TableContextMenu.Constructor<RowType, ThisType, TableModelType> tableContextMenuConstructor,
				ColumnID contextMenuSurrogateColumn,
				IntFunction<RowType[]> createRowArray,
				Predicate<RowType> isMarker
		)
		{
			super(new BorderLayout(3, 3));
			this.main = main;
			this.localPiconCache = localPiconCache;
			setBorder(BorderFactory.createTitledBorder(borderTitle));
			
			Objects.requireNonNull( tableSelectionMode );
			Objects.requireNonNull( tableModelConstructor );
			Objects.requireNonNull( tableContextMenuConstructor );
			Objects.requireNonNull( createRowArray );
			selectedRowIndexes = new int[0];
			selectedRows = createRowArray.apply(0);
			bouquetDataExtracts = null;
			
			tableModel = tableModelConstructor.create(getThis());
			table = new JTable(tableModel);
			defaultRowHeight = table.getRowHeight();
			tableScrollPane = new JScrollPane(table);
			//tableScrollPane.setPreferredSize(new Dimension(1000,500));
			table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.setColumnSelectionAllowed(false);
			table.setSelectionMode(tableSelectionMode.value);
			tableModel.setTable(table);
			tableModel.setColumnWidths(table);
			
			tableCellRenderer = new Tables.GeneralizedTableCellRenderer2<>(tableModelClass);
			Tables.SimplifiedTableModel.forEachColumClass(tableModel.getAllColumns(), clazz -> table.setDefaultRenderer(clazz,tableCellRenderer));
			//tableModel.setAllDefaultRenderers(clazz -> tableCellRenderer);
			tableCellRenderer.setBackgroundColorizer((value, rowM, columnM, columnID, row) -> {
				if (tableModel.isCellEditable(rowM, columnM))
					return BGCOLOR_EDITABLE;
				if (row!=null && isMarker.test(row))
					return BGCOLOR_MARKER;
				return null;
			});
			
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
		
		void setBouquetDataExtracts(BouquetDataExtracts bouquetDataExtracts)
		{
			this.bouquetDataExtracts = bouquetDataExtracts;
			tableModel.updateAfterChangeOfBouquetDataExtracts();
		}

		void setShowPicons(boolean showPicons)
		{
			table.setRowHeight(showPicons ? PiconRenderer.TABLE_ROW_HEIGHT : defaultRowHeight);
			tableModel.enablePiconColumn(showPicons);
			tableModel.fireTableStructureUpdate_();
			tableModel.setColumnWidths(table);
			//setCellRenderers // all renderers are connected to column classes
		}

		void addTasksToPiconLoader(String baseURL)
		{
			if (baseURL==null) return;
			PiconLoader piconLoader = PiconLoader.getInstance();
			piconLoader.setBaseURL(baseURL);
			tableModel.forEachRow(row -> {
				StationID stationID = getStationIDFromRow(row);
				if (stationID!=null)
					piconLoader.addTask(stationID);
			});
		}
		
		BufferedImage getCachedPicon(RowType row)
		{
			StationID stationID = getStationIDFromRow(row);
			if (stationID==null) return null;
			return localPiconCache.get(stationID);
		}
		
		String getStationName(StationID stationID)
		{
			return bouquetDataExtracts==null ? null : bouquetDataExtracts.getStationName(stationID);
		}
		
		protected abstract ThisType getThis();
		protected abstract String generateRowInfo(RowType row);
		protected abstract StationID getStationIDFromRow(RowType row);

		protected static class TableContextMenu<
				RowType,
				TablePanelType extends AbstractPanel<RowType,?,?,TablePanelType>,
				TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableModelMethods<?>
		> extends ContextMenu
		{
			private static final long serialVersionUID = 5456765972894773723L;
			
			interface Constructor<
					RowType,
					TablePanelType extends AbstractPanel<RowType,?,?,TablePanelType>,
					TableModelType extends Tables.SimpleGetValueTableModel<RowType, ?> & TableModelMethods<?>
			> {
				TableContextMenu<RowType, TablePanelType, TableModelType> create(OpenWebifController main, TablePanelType tablePanel, TableModelType tableModel);
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
				
				miSwitchToStation = main==null ? null : OWCTools.createMenuItem("##", e->{
					if (clickedRowStationID!=null)
						main.zapToStation(clickedRowStationID);
				});
				miStreamStation = OWCTools.createMenuItem("##", e->{
					if (clickedRowStationID==null) return;
					if (clickedRowStationID.isMarker()) return;
					
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
					clickedRowStationID = clickedRow==null ? null : this.tablePanel.getStationIDFromRow(clickedRow);
					
					boolean isStation = clickedRowStationID!=null && !clickedRowStationID.isMarker();
					if (miSwitchToStation!=null)
					{
						miSwitchToStation.setEnabled(isStation);
						miSwitchToStation.setText("Switch to %s".formatted(getStationName(clickedRowStationID)));
					}
					miStreamStation.setEnabled(isStation);
					miStreamStation.setText("Stream %s".formatted(getStationName(clickedRowStationID)));
				});
			}
			
			private String getStationName(StationID stationID)
			{
				String stationName = tablePanel.getStationName(stationID);
				return stationName==null ? "Station" : "Station \"%s\"".formatted(stationName);
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
		private Bouquet selectedBouquet;
		
		BouquetDataPanel(OpenWebifController main, LocalPiconCache localPiconCache)
		{
			super(
					main, localPiconCache,
					"Bouquet Data in STB",
					SelectionMode.SINGLE_SELECTION,
					BouquetTableModel::new,
					BouquetTableModel.class,
					TableContextMenu::new,
					BouquetTableModel.ColumnID.name,
					Bouquet.SubService[]::new,
					Bouquet.SubService::isMarker
			);
			bouquetFilePanel = null;
			selectedBouquet = null;
			
			labBouquetSelector = new JLabel("Bouquet: ");
			bouquetSelector = new JComboBox<>();
			bouquetSelector.addActionListener(ev -> {
				selectedBouquet = bouquetSelector.getItemAt(bouquetSelector.getSelectedIndex());
				tableModel.setData(selectedBouquet==null ? null : selectedBouquet.subservices);
			});
			
			northPanel.setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			
			c.weightx = 0; northPanel.add(labBouquetSelector,c);
			c.weightx = 1; northPanel.add(bouquetSelector,c);
			
			labBouquetSelector.setEnabled(false);
			bouquetSelector.setEnabled(false);
			
			tableCellRenderer.setExtraRenderer(BouquetTableModel.ColumnID.picon, new PiconRenderer<>());
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
			case MarkerAdded:
			case MarkerRemoved:
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

		@Override
		protected StationID getStationIDFromRow(Bouquet.SubService row)
		{
			return row==null || row.service==null ? null : row.service.stationID;
		}
		
		static class TableContextMenu extends AbstractPanel.TableContextMenu<Bouquet.SubService, BouquetDataPanel, BouquetTableModel>
		{
			private static final long serialVersionUID = -5073780433391354857L;
			
			private final JMenuItem miCopyToFileBefore;
			private final JMenuItem miCopyToFileAfter;
			private final JMenuItem miCopyToFileEnd;
			private final JMenuItem miLoadPicons;

			TableContextMenu(OpenWebifController main, BouquetDataPanel tablePanel, BouquetTableModel tableModel)
			{
				super(main, tablePanel, tableModel);
				
				miCopyToFileBefore = OWCTools.createMenuItem("Copy to File (before selected)", ev -> copyToFile(i -> i));
				miCopyToFileAfter  = OWCTools.createMenuItem("Copy to File (after selected)" , ev -> copyToFile(i -> i+1));
				miCopyToFileEnd    = OWCTools.createMenuItem("Copy to File (at end)"         , ev -> copyToFileAtEnd());
				
				miLoadPicons = OWCTools.createMenuItem("##", GrayCommandIcons.IconGroup.Download, ev -> {
					if (this.tablePanel.selectedBouquet!=null)
					{
						PiconLoader piconLoader = PiconLoader.getInstance();
						this.tablePanel.selectedBouquet.subservices.forEach(s -> {
							if (!s.isMarker())
								piconLoader.addTask(s.getStationID());
						});
					}
				});
				
				addContextMenuInvokeListener((comp,x,y) -> {
					boolean bfpHasFileData       = this.tablePanel.bouquetFilePanel!=null && this.tablePanel.bouquetFilePanel.bouquetFileData!=null;
					boolean thisHasSelectedRows  = this.tablePanel.selectedRows.length>0;
					boolean canAdd    = bfpHasFileData && thisHasSelectedRows;
					boolean canInsert = canAdd && this.tablePanel.bouquetFilePanel.selectedRowIndexes.length==1;
					miCopyToFileBefore.setEnabled(canInsert);
					miCopyToFileAfter .setEnabled(canInsert);
					miCopyToFileEnd   .setEnabled(canAdd);
					miLoadPicons      .setEnabled(this.tablePanel.selectedBouquet!=null);
					miLoadPicons.setText(
							this.tablePanel.selectedBouquet==null
								? "Load Picons"
								: "Load Picons of Bouquet \"%s\"".formatted(this.tablePanel.selectedBouquet.name)
					);
				});
			}

			@Override
			void populateMenu()
			{
				add(miCopyToFileBefore);
				add(miCopyToFileAfter);
				add(miCopyToFileEnd);
				addSeparator();
				add(miLoadPicons);
				addSeparator();
				super.populateMenu();
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
		}

		static class BouquetTableModel
				extends Tables.SimpleGetValueTableModel2<BouquetTableModel, Bouquet.SubService, BouquetTableModel.ColumnID>
				implements AbstractPanel.TableModelMethods<BouquetTableModel.ColumnID>
		{
			private static int PCW = PiconRenderer.TABLE_COLUMN_WIDTH;
			
			enum ColumnID implements Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<BouquetTableModel, Bouquet.SubService>, SwingConstants
			{
				// Column Widths: [30, 55, 55, 140, 230, 230, 50] in ModelOrder
				pos               (config("Pos"              , Long         .class,  30,   null).setValFunc(s->s.pos)),
				program           (config("Program"          , Long         .class,  55,   null).setValFunc(s->s.program)),
				service_isMarker  (config("is Marker"        , Boolean      .class,  55,   null).setValFunc(s->s.service, s->s.isMarker())),
			//	service_label     (config("Label"            , String       .class, 140,   null).setValFunc(s->s.service, s->s.label)),
				picon             (config("Picon"            , BufferedImage.class, PCW,   null).setValFunc((m,s)->m.parentPanel.getCachedPicon(s))),
				name              (config("Name"             , String       .class, 140,   null).setValFunc(s->s.name)),
				servicereference  (config("Service Reference", String       .class, 230,   null).setValFunc(s->s.servicereference)),
			//	service_stationID (config("Station ID"       , String       .class, 230,   null).setValFunc(s->s.service, s->s.stationID, stID->stID.toString())),
				stationOccurences (config("Occurences"       , String       .class, 230,   null).setValFunc((m,s)->getOccurences(m,s))),
				isInFile          (config("is in File"       , Boolean      .class,  50,   null).setValFunc((m,s)->isInFile(m,s))),
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
					return subService.isMarker() || model.parentPanel.bouquetDataExtracts==null ? null : model.parentPanel.bouquetDataExtracts.getOccurences(subService.servicereference);
				}
				
				static Boolean isInFile(BouquetTableModel model, Bouquet.SubService subService)
				{
					if (model.bouquetFilePanel==null || subService.isMarker()) return null;
					return model.bouquetFilePanel.tableModel.isSrefInFile(subService.servicereference);
				}
			}

			private final BouquetDataPanel parentPanel;
			private BouquetFilePanel bouquetFilePanel;

			BouquetTableModel(BouquetDataPanel parentPanel)
			{
				super(generateColumnArr(true));
				this.parentPanel = parentPanel;
				bouquetFilePanel = null;
			}
			
			@Override public ColumnID[] getAllColumns() { return ColumnID.values(); }

			void setOtherPanel(BouquetFilePanel bouquetFilePanel)
			{
				this.bouquetFilePanel = bouquetFilePanel;
			}

			@Override protected BouquetTableModel getThis() { return this; }
			@Override public void updateAfterChangeOfBouquetDataExtracts() { fireTableColumnUpdate(ColumnID.stationOccurences); }
			@Override public void fireTableStructureUpdate_() { fireTableStructureUpdate(); }

			@Override
			public void updatePiconColumn()
			{
				fireTableColumnUpdate(ColumnID.picon);
			}
			
			@Override
			public void enablePiconColumn(boolean isEnabled)
			{
				columns = generateColumnArr(!isEnabled);
			}

			private static ColumnID[] generateColumnArr(boolean removePiconColumn)
			{
				return removeFromArr(ColumnID.values(), ColumnID[]::new, removePiconColumn, Arrays.asList( ColumnID.picon ));
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

		BouquetFilePanel(OpenWebifController main, LocalPiconCache localPiconCache)
		{
			super(
					main, localPiconCache,
					"Bouquet File",
					SelectionMode.SINGLE_SELECTION,
					BouquetFileTableModel::new,
					BouquetFileTableModel.class,
					TableContextMenu::new,
					BouquetFileTableModel.ColumnID.label,
					BouquetFileData.Entry[]::new,
					row->row.isMarker
			);
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
			
			tableCellRenderer.setExtraRenderer(BouquetFileTableModel.ColumnID.picon, new PiconRenderer<>());
		}
		
		@Override protected BouquetFilePanel getThis() { return this; }

		void setOtherPanel(BouquetDataPanel bouquetDataPanel)
		{
			this.bouquetDataPanel = bouquetDataPanel;
			tableModel.setOtherPanel(bouquetDataPanel);
		}

		boolean addStations(String[] srefArr)
		{
			if (bouquetFileData==null)
				return false;
			
			int insertIndex = bouquetFileData.entries.size();
			for (int i=0; i<srefArr.length; i++)
				bouquetFileData.entries.add(BouquetFileData.Entry.createStation(srefArr[i]));
			
			notifyStationsAdded(insertIndex, srefArr.length);
			
			return true;
		}

		boolean insertStations(String[] srefArr, int insertIndex)
		{
			if (bouquetFileData==null)
				return false;
			
			if (insertIndex<0 || insertIndex>bouquetFileData.entries.size())
				return false;
			
			for (int i=0; i<srefArr.length; i++)
				bouquetFileData.entries.insertElementAt(BouquetFileData.Entry.createStation(srefArr[i]), insertIndex+i);
			
			notifyStationsAdded(insertIndex, srefArr.length);
			
			return true;
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

		boolean addMarker(IntUnaryOperator getInsertIndexFromSelectedIndex)
		{
			if (bouquetFileData==null || selectedRowIndexes.length != 1) return false;
			
			int insertIndex = getInsertIndexFromSelectedIndex.applyAsInt(selectedRowIndexes[0]);
			if (insertIndex<0 || insertIndex>bouquetFileData.entries.size()) return false;
			
			String markerText = askUserForMarkerText();
			if (markerText==null) return false;
			
			bouquetFileData.entries.insertElementAt(BouquetFileData.Entry.createMarker(markerText), insertIndex);
			
			notifyMarkerAdded(insertIndex);
			
			return true;
		}

		boolean addMarkerAtEnd()
		{
			if (bouquetFileData==null) return false;
			
			String markerText = askUserForMarkerText();
			if (markerText==null) return false;
			
			int insertIndex = bouquetFileData.entries.size();
			bouquetFileData.entries.add(BouquetFileData.Entry.createMarker(markerText));
			
			notifyMarkerAdded(insertIndex);
			
			return true;
		}

		boolean removeEntry()
		{
			if (bouquetFileData==null || selectedRowIndexes.length != 1) return false;
			
			int index = selectedRowIndexes[0];
			if (index<0 || index>=bouquetFileData.entries.size()) return false;
			
			BouquetFileData.Entry removedEntry = bouquetFileData.entries.remove(index);
			
			notifyEntryRemoved(index, removedEntry);
			
			return true;
		}

		private void notifyStationsAdded(int insertIndex, int stationCount)
		{
			if (stationCount>0)
			{
				this.tableModel.fireTableRowsAdded(insertIndex, insertIndex+stationCount-1);
				if (bouquetDataPanel!=null)
					bouquetDataPanel.notifyBouquetFileChange(BouquetFileChangeEvent.Type.StationsAdded.createEvent());
			}
		}

		private void notifyMarkerAdded(int insertIndex)
		{
			this.tableModel.fireTableRowsAdded(insertIndex, insertIndex);
			if (bouquetDataPanel!=null)
				bouquetDataPanel.notifyBouquetFileChange(BouquetFileChangeEvent.Type.MarkerAdded.createEvent());
		}

		private void notifyEntryRemoved(int index, BouquetFileData.Entry removedEntry)
		{
			this.tableModel.fireTableRowRemoved(index);
				
			if (bouquetDataPanel!=null)
			{
				BouquetFileChangeEvent.Type eventType;
				if (removedEntry.isMarker)
					eventType = BouquetFileChangeEvent.Type.MarkerRemoved;
				else
					eventType = BouquetFileChangeEvent.Type.StationsRemoved;
				bouquetDataPanel.notifyBouquetFileChange(eventType.createEvent());
			}
		}

		private String askUserForMarkerText()
		{
			String title = "Marker text";
			String msg = "Enter Marker text: ";
			String markerText = JOptionPane.showInputDialog(this, msg, title, JOptionPane.QUESTION_MESSAGE);
			return markerText;
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

		@Override
		protected StationID getStationIDFromRow(BouquetFileData.Entry row)
		{
			return row==null ? null : row.stationID;
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
			private final JMenuItem miMoveUp;
			private final JMenuItem miMoveDown;
			private final JMenuItem miAddMarkerBefore;
			private final JMenuItem miAddMarkerAfter;
			private final JMenuItem miAddMarkerAtEnd;
			private final JMenuItem miRemoveEntry;
			private final JMenuItem miLoadPicons;

			TableContextMenu(OpenWebifController main, BouquetFilePanel tablePanel, BouquetFileTableModel tableModel)
			{
				super(main, tablePanel, tableModel);
				
				miMoveUp   = OWCTools.createMenuItem("##", GrayCommandIcons.IconGroup.Up  , ev -> this.tablePanel.moveRow(-1));
				miMoveDown = OWCTools.createMenuItem("##", GrayCommandIcons.IconGroup.Down, ev -> this.tablePanel.moveRow(+1));
				
				miAddMarkerBefore = OWCTools.createMenuItem("##"               , GrayCommandIcons.IconGroup.Add, ev -> this.tablePanel.addMarker(i -> i  ));
				miAddMarkerAfter  = OWCTools.createMenuItem("##"               , GrayCommandIcons.IconGroup.Add, ev -> this.tablePanel.addMarker(i -> i+1));
				miAddMarkerAtEnd  = OWCTools.createMenuItem("Add Marker at end", GrayCommandIcons.IconGroup.Add, ev -> this.tablePanel.addMarkerAtEnd());
				
				miRemoveEntry = OWCTools.createMenuItem("##", GrayCommandIcons.IconGroup.Delete, ev -> this.tablePanel.removeEntry());
				
				miLoadPicons = OWCTools.createMenuItem("##", GrayCommandIcons.IconGroup.Download, ev -> {
					if (this.tablePanel.bouquetFileData!=null)
					{
						PiconLoader piconLoader = PiconLoader.getInstance();
						this.tablePanel.bouquetFileData.entries.forEach(e -> {
							if (!e.isMarker && e.stationID!=null)
								piconLoader.addTask(e.stationID);
						});
					}
				});
				
				addContextMenuInvokeListener((comp,x,y) -> {
					miMoveUp    .setEnabled(this.tablePanel.selectedRowIndexes.length==1 && this.tablePanel.selectedRowIndexes[0]>0);
					miMoveDown  .setEnabled(this.tablePanel.selectedRowIndexes.length==1 && this.tablePanel.selectedRowIndexes[0]+1<this.tableModel.getRowCount());
					miLoadPicons.setEnabled(this.tablePanel.bouquetFileData!=null);
					miMoveUp         .setText(KeyFunction.MoveUp  .addKeyLabel("Move %s Up"          .formatted(getNameOfSelected())));
					miMoveDown       .setText(KeyFunction.MoveDown.addKeyLabel("Move %s Down"        .formatted(getNameOfSelected())));
					miAddMarkerBefore.setText("Add Marker before %s".formatted(getNameOfSelected()));
					miAddMarkerAfter .setText("Add Marker after %s" .formatted(getNameOfSelected()));
					miRemoveEntry    .setText("Remove %s"           .formatted(getNameOfSelected()));
					miLoadPicons.setText(
							this.tablePanel.bouquetFileData==null
								? "Load Picons"
								: "Load Picons of Bouquet File \"%s\"".formatted(this.tablePanel.bouquetFileData.name)
					);
				});
			}

			private String getNameOfSelected()
			{
				if (tablePanel.selectedRows.length==1)
				{
					BouquetFileData.Entry entry = tablePanel.selectedRows[0];
					if (entry.isMarker)
						return "Marker \"%s\"".formatted(entry.markerText);
					
					String stationName = tablePanel.getStationName(entry.stationID);
					if (stationName!=null)
						return "Station \"%s\"".formatted(stationName);
					
					return "Station {%s}".formatted(entry.stationSref);
				}
				
				return "Selected";
			}

			@Override
			void populateMenu()
			{
				add(miMoveUp  );
				add(miMoveDown);
				addSeparator();
				add(miAddMarkerBefore);
				add(miAddMarkerAfter );
				add(miAddMarkerAtEnd );
				addSeparator();
				add(miRemoveEntry);
				addSeparator();
				add(miLoadPicons);
				addSeparator();
				super.populateMenu();
			}
		}
		
		static class BouquetFileTableModel
				extends Tables.SimpleGetValueTableModel2<BouquetFileTableModel, BouquetFileData.Entry, BouquetFileTableModel.ColumnID>
				implements AbstractPanel.TableModelMethods<BouquetFileTableModel.ColumnID>
		{
			private static int PCW = PiconRenderer.TABLE_COLUMN_WIDTH;
			
			enum ColumnID implements Tables.SimpleGetValueTableModel2.ColumnIDTypeInt2b<BouquetFileTableModel, BouquetFileData.Entry>, SwingConstants
			{
				isMarker          (config("is Marker"  , Boolean      .class,  55,   null).setValFunc(   s ->s.isMarker   )),
				picon             (config("Picon"      , BufferedImage.class, PCW,   null).setValFunc((m,s)->m.parentPanel.getCachedPicon(s))),
				label             (config("Label"      , String       .class, 140,   null).setValFunc((m,s)->getLabel(m,s))),
				stationSref       (config("Station"    , String       .class, 230,   null).setValFunc(   s ->s.stationSref)),
				stationOccurences (config("Occurences" , String       .class, 230,   null).setValFunc((m,s)->getOccurences(m,s))),
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
					if (model.parentPanel.bouquetDataExtracts != null)
						return model.parentPanel.bouquetDataExtracts.getStationName(entry.stationID);
					return null;
				}
				
				private static String getOccurences(BouquetFileTableModel model, BouquetFileData.Entry entry)
				{
					return entry.isMarker || model.parentPanel.bouquetDataExtracts==null ? null : model.parentPanel.bouquetDataExtracts.getOccurences(entry.stationSref);
				}
			}

			private final BouquetFilePanel parentPanel;
			@SuppressWarnings("unused")
			private BouquetDataPanel bouquetDataPanel;

			BouquetFileTableModel(BouquetFilePanel parentPanel)
			{
				super(generateColumnArr(true));
				this.parentPanel = parentPanel;
				bouquetDataPanel = null;
			}
			
			@Override public ColumnID[] getAllColumns() { return ColumnID.values(); }

			@Override
			public void updatePiconColumn()
			{
				fireTableColumnUpdate(ColumnID.picon);
			}

			@Override
			public void enablePiconColumn(boolean isEnabled)
			{
				columns = generateColumnArr(!isEnabled);
			}

			private static ColumnID[] generateColumnArr(boolean removePiconColumn)
			{
				return removeFromArr(ColumnID.values(), ColumnID[]::new, removePiconColumn, Arrays.asList( ColumnID.picon ));
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

			@Override protected BouquetFileTableModel getThis() { return this; }
			@Override public void updateAfterChangeOfBouquetDataExtracts() {
				fireTableColumnUpdate(ColumnID.label);
				fireTableColumnUpdate(ColumnID.stationOccurences);
			}

			@Override protected void fireTableRowsAdded(int firstRowIndex, int lastRowIndex) { super.fireTableRowsAdded(firstRowIndex, lastRowIndex); }
			@Override protected void fireTableRowRemoved(int rowIndex) { super.fireTableRowRemoved(rowIndex); }
			@Override public void fireTableStructureUpdate_() { fireTableStructureUpdate(); }

			@Override
			protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID)
			{
				BouquetFileData.Entry row = getRow(rowIndex);
				return row!=null && row.isMarker && columnID==ColumnID.label;
			}

			@Override
			protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID)
			{
				BouquetFileData.Entry row = getRow(rowIndex);
				if (row==null || columnID==null) return;
				
				switch (columnID)
				{
				case isMarker:
				case stationOccurences:
				case stationSref:
				case picon:
					break;
					
				case label:
					if (aValue instanceof String str)
						row.markerText = str;
					else if (aValue==null)
						row.markerText = "";
					break;
				}
			}
		}
	}
	
	private static class BouquetFileChangeEvent
	{
		enum Type
		{
			FileChanged, StationsRemoved, StationsAdded, MarkerRemoved, MarkerAdded;
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
				stationID = this.stationSref==null ? null : StationID.parseIDStr(this.stationSref);
			}

			static Entry createStation(String stationSref)
			{
				return new Entry(null, stationSref);
			}

			static Entry createMarker(String markerText)
			{
				return new Entry(markerText, null);
			}
		}
	}
	
	private static class BouquetDataExtracts
	{
		final Map<String, String> bouquetNames;
		final Map<String, String> stationNames;
		final Map<String, Set<String>> stationOccurences;
		
		BouquetDataExtracts(BouquetData bouquetData)
		{
			bouquetNames      = new HashMap<>();
			stationNames      = new HashMap<>();
			stationOccurences = new HashMap<>();
			
			if (bouquetData == null) return;
			
			stationNames.putAll(bouquetData.names);
			for (Bouquet bouquet : bouquetData.bouquets)
			{
				bouquetNames.put(bouquet.servicereference, bouquet.name);
				for (Bouquet.SubService subservice : bouquet.subservices)
					if (!subservice.isMarker())
						stationOccurences
							.computeIfAbsent(subservice.servicereference, sref -> new HashSet<>())
							.add(bouquet.servicereference);
			}
		}
		
		String getStationName(StationID stationID)
		{
			return stationID==null ? null : stationNames.get(stationID.toIDStr());
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
