package sermed.comprobantes.ui;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/** Crea tablas de solo lectura con el mismo aspecto en todo el aplicativo. */
public final class Tablas {

    private Tablas() { }

    /**
     * @param <T>    tipo de las filas
     * @param modelo modelo de la tabla
     * @param numericas índices de las columnas alineadas a la derecha
     * @return tabla configurada
     */
    public static <T> JTable crear(ModeloTabla<T> modelo, int... numericas) {
        JTable tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(22);
        for (int i = 0; i < modelo.getColumnCount(); i++) {
            TableColumn columna = tabla.getColumnModel().getColumn(i);
            columna.setPreferredWidth(modelo.getColumnas().get(i).ancho());
        }
        DefaultTableCellRenderer derecha = new DefaultTableCellRenderer();
        derecha.setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);
        for (int i : numericas) {
            tabla.getColumnModel().getColumn(i).setCellRenderer(derecha);
        }
        return tabla;
    }
}
