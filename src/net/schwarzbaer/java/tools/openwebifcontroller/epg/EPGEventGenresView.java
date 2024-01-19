package net.schwarzbaer.java.tools.openwebifcontroller.epg;

import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Vector;
import java.util.function.Function;

import javax.swing.JScrollPane;
import javax.swing.JTable;

import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.openwebifcontroller.OpenWebifController;
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
//		tableModel.setAllDefaultRenderers( clazz -> new Renderer());;
		
		JScrollPane contentPane = new JScrollPane(table);
		Dimension preferredTableSize = table.getPreferredSize();
		contentPane.setPreferredSize(new Dimension( preferredTableSize.width + 30, 500 ));
		createGUI(contentPane, OpenWebifController.createButton("Close", true, e -> closeDialog()));
	}

	@Override
	public void showDialog()
	{
		tableModel.setData(EPGEventGenresTableModel.getData());
		super.showDialog();
	}
	
	private static class EPGEventGenresTableModel extends Tables.SimpleGetValueTableModel<EPGEventGenresTableModel.Row, EPGEventGenresTableModel.ColumnID>
	{

		enum ColumnID implements Tables.SimpleGetValueTableModel.ColumnIDTypeInt<Row>
		{
			isNew( "Is New ?", String.class,  55, row -> row.isNew ),
			id   ( "ID"      , String.class,  30, row -> row.id    ),
			name1( "Name 1"  , String.class, 200, row -> row.name1 ),
			name2( "Name 2"  , String.class, 300, row -> row.name2 ),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<Row, ?> getValueFcn;
			
			private <V> ColumnID(String name, Class<V> columnClass, int width, Function<Row, V> getValueFcn) {
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
				String isNew
		) {
			Row()        { this( "...", null, null, null ); }
			Row(long id) { this( String.valueOf(id), null, null, null ); }
			
			static Row create(EPGEventGenre eeg)
			{
				String name = eeg.name();
				int pos = name.indexOf(": ");
				String name1 = pos<0 ? name : name.substring(0, pos);
				String name2 = pos<0 ? null : name.substring(pos+2);
				return new Row(
						String.valueOf(eeg.id()),
						name1, name2,
						eeg.isNew() ? "New" : null
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
