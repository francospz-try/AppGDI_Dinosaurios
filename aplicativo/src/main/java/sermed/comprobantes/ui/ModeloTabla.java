package sermed.comprobantes.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla genérico de solo lectura: cada columna obtiene su valor
 * de un objeto de la lista mediante una función.
 *
 * @param <T> tipo de las filas
 */
public class ModeloTabla<T> extends AbstractTableModel {

    /**
     * Columna de la tabla.
     *
     * @param <T>    tipo de las filas
     * @param titulo encabezado
     * @param valor  función que extrae el valor mostrado
     * @param ancho  ancho preferido en píxeles
     */
    public record Columna<T>(String titulo, Function<T, Object> valor, int ancho) { }

    private final List<Columna<T>> columnas;
    private List<T> filas = new ArrayList<>();

    /** @param columnas columnas, en orden */
    public ModeloTabla(List<Columna<T>> columnas) {
        this.columnas = columnas;
    }

    /** @param nuevas filas que reemplazan a las actuales */
    public void setFilas(List<T> nuevas) {
        this.filas = new ArrayList<>(nuevas);
        fireTableDataChanged();
    }

    /**
     * @param indice índice de la fila en el modelo
     * @return objeto de esa fila
     */
    public T getFila(int indice) {
        return filas.get(indice);
    }

    /** @return columnas del modelo */
    public List<Columna<T>> getColumnas() {
        return columnas;
    }

    @Override
    public int getRowCount() {
        return filas.size();
    }

    @Override
    public int getColumnCount() {
        return columnas.size();
    }

    @Override
    public String getColumnName(int columna) {
        return columnas.get(columna).titulo();
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        return columnas.get(columna).valor().apply(filas.get(fila));
    }
}
