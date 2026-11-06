package pe.edu.ucsm.gdi;

import javax.swing.table.AbstractTableModel;

/** Modelo de solo lectura; mantiene ordenación tipada salvo en columnas heterogéneas. */
public final class ResultTableModel extends AbstractTableModel {
    private static final long serialVersionUID=1L;
    private final TableData data;
    private final Class<?>[] types;
    public ResultTableModel(TableData data) {
        this.data=data;types=new Class<?>[data.columns().size()];
        for(int col=0;col<types.length;col++) {
            Class<?> type=null;
            for(var row:data.rows()) {
                Object value=row.get(col);if(value==null)continue;
                if(type==null)type=value.getClass();
                else if(type!=value.getClass()){type=Object.class;break;}
            }
            types[col]=type==null?Object.class:type;
        }
    }
    @Override public int getRowCount(){return data.rows().size();}
    @Override public int getColumnCount(){return data.columns().size();}
    @Override public String getColumnName(int column){return data.columns().get(column);}
    @Override public Object getValueAt(int row,int column){return data.rows().get(row).get(column);}
    @Override public Class<?> getColumnClass(int column){return types[column];}
    @Override public boolean isCellEditable(int row,int column){return false;}
}
