package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.openwebifcontroller.OWCTools;
import net.schwarzbaer.java.tools.openwebifcontroller.epg.EPGEventGenres.EPGEventGenre;

class EPGEventGenresView extends StandardDialog
{
	private static final long serialVersionUID = 2578768568602544128L;
	private final JTable table;
	private final EPGEventGenresTableModel tableModel;

	EPGEventGenresView(Window parent, String title)
	{
		super(parent, title, ModalityType.APPLICATION_MODAL, false);
		
		table = new JTable(tableModel = new EPGEventGenresTableModel());
//		table.setRowSorter(tableRowSorter = new TimersTableRowSorter(tableModel));
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
//		table.setColumnSelectionAllowed(false);
//		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		tableModel.setTable(table);
		tableModel.setColumnWidths(table);
		EPGEventGenresTableModelRenderer renderer = new EPGEventGenresTableModelRenderer(tableModel);
		tableModel.setAllDefaultRenderers( clazz -> renderer);
		
		JScrollPane contentPane = new JScrollPane(table);
		Dimension preferredTableSize = table.getPreferredSize();
		contentPane.setPreferredSize(new Dimension( preferredTableSize.width + 30, 500 ));
		createGUI(contentPane, OWCTools.createButton("Close", true, e -> closeDialog()));
	}

	@Override
	public void showDialog()
	{
		tableModel.setData(EPGEventGenresTableModel.getData());
		super.showDialog();
	}
	
	private static class EPGEventGenresTableModelRenderer implements TableCellRenderer
	{
		private static final Color COLOR_BG_EVEN_ID = new Color(0xFFFFDF);
		private static final Color COLOR_BG_ODD_ID  = new Color(0xE1FFDF);
		
		private final Tables.LabelRendererComponent rendercomp;
		private final EPGEventGenresTableModel tableModel;

		EPGEventGenresTableModelRenderer( EPGEventGenresTableModel tableModel )
		{
			this.tableModel = tableModel;
			rendercomp = new Tables.LabelRendererComponent();
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
		{
			int rowM    = rowV   <0 ? -1 : table.convertRowIndexToModel(rowV); 
			int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
			EPGEventGenresTableModel.Row row = tableModel.getRow(rowM);
			EPGEventGenresTableModel.ColumnID columnID = tableModel.getColumnID(columnM);
			
			Supplier<Color> getCustomBackground = () -> row==null ? null : row.bgcolor;
			
			String valueStr = value == null ? null : value.toString();
			rendercomp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
			
			rendercomp.setHorizontalAlignment(
					columnID==null
						? SwingConstants.LEFT
						: columnID.alignment
			);
			
			return rendercomp;
		}
		
	}
	
	private static class EPGEventGenresTableModel extends Tables.SimpleGetValueTableModel<EPGEventGenresTableModel.Row, EPGEventGenresTableModel.ColumnID>
	{

		enum ColumnID implements Tables.SimpleGetValueTableModel.ColumnIDTypeInt<Row>
		{
			isNew( "Is New ?", String.class, SwingConstants.CENTER,  55, row -> row.isNew ),
			id   ( "ID"      , String.class, SwingConstants.CENTER,  30, row -> row.id    ),
			name1( "Name 1"  , String.class, SwingConstants.LEFT  , 200, row -> row.name1 ),
			name2( "Name 2"  , String.class, SwingConstants.LEFT  , 300, row -> row.name2 ),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<Row, ?> getValueFcn;
			private final int alignment;
			
			private <V> ColumnID(String name, Class<V> columnClass, int alignment, int width, Function<Row, V> getValueFcn) {
				this.alignment = alignment;
				cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
				this.getValueFcn = getValueFcn;
			}

			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<Row, ?> getGetValue() { return getValueFcn; }
		}

		public EPGEventGenresTableModel()
		{
			super(ColumnID.values());
		}

		record Row(
				String id,
				String name1,
				String name2,
				String isNew,
				Color bgcolor
		) {

			Row()        { this( "...", null, null, null, null ); }
			Row(long id) { this( String.valueOf(id), null, null, null, null ); }
			
			static Row create(EPGEventGenre eeg)
			{
				String name = eeg.name();
				int pos = name.indexOf(": ");
				String name1 = pos<0 ? name : name.substring(0, pos);
				String name2 = pos<0 ? null : name.substring(pos+2);
				return new Row(
						String.valueOf(eeg.id()),
						name1, name2,
						eeg.isNew() ? "New" : null,
						(eeg.id() & 1) == 0
							? EPGEventGenresTableModelRenderer.COLOR_BG_EVEN_ID
							: EPGEventGenresTableModelRenderer.COLOR_BG_ODD_ID
				);
			}
		}

		private static Row[] getData()
		{
			List<EPGEventGenre> rawData = EPGEventGenres.getInstance().getEPGEventGenresSorted();
			Vector<Row> data = new Vector<>();
			
			Long lastId = null;
			for (EPGEventGenre eeg : rawData)
			{
				if (lastId!=null && 1 < eeg.id()-lastId)
				{
					if (6 < eeg.id()-lastId)
					{
						data.add(new Row( lastId+1 ));
						data.add(new Row( lastId+2 ));
						data.add(new Row());
						data.add(new Row( eeg.id()-2 ));
						data.add(new Row( eeg.id()-1 ));
					}
					else
						for (long id = lastId+1; id<eeg.id(); id++)
							data.add(new Row( id ));
				}
				
				data.add(Row.create( eeg ));
				
				lastId = eeg.id();
			}
			
			return data.toArray(Row[]::new);
		}
		
	} 
}
