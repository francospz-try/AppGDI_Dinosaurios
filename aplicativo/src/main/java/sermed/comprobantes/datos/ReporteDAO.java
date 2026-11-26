package sermed.comprobantes.datos;

import java.sql.Types;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Ejecuta los reportes: llama a la función de la base de datos de cada uno
 * ({@code SELECT * FROM fn_reporte_...(?, ...)}) con los valores de sus
 * parámetros.
 */
public class ReporteDAO {

    private final ConexionBD bd;

    /** @param bd capa de conexión */
    public ReporteDAO(ConexionBD bd) {
        this.bd = bd;
    }

    /**
     * @param reporte reporte que se ejecuta
     * @param valores un valor por parámetro, en orden ({@link LocalDate},
     *                {@link String}, {@link Integer}, {@link Boolean} o
     *                {@code null} si el parámetro opcional se omite)
     * @return columnas y filas del reporte
     * @throws AccesoDatosException si la consulta falla
     * @throws IllegalArgumentException si no hay un valor por parámetro
     */
    public Tabla ejecutar(Reporte reporte, List<Object> valores) throws AccesoDatosException {
        List<Reporte.Parametro> parametros = reporte.parametros();
        if (valores.size() != parametros.size()) {
            throw new IllegalArgumentException(reporte + " espera " + parametros.size()
                    + " parámetros y recibió " + valores.size());
        }
        String sql = "SELECT * FROM " + reporte.funcion() + "("
                + String.join(", ", Collections.nCopies(parametros.size(), "?")) + ")";
        return bd.consultarTabla(sql, ps -> {
            for (int i = 0; i < valores.size(); i++) {
                Object v = valores.get(i);
                int n = i + 1;
                switch (parametros.get(i).tipo()) {
                    case FECHA -> {
                        if (v == null) {
                            ps.setNull(n, Types.DATE);
                        } else {
                            ps.setObject(n, (LocalDate) v);
                        }
                    }
                    case ENTERO -> {
                        if (v == null) {
                            ps.setNull(n, Types.INTEGER);
                        } else {
                            ps.setInt(n, (Integer) v);
                        }
                    }
                    case BOOLEANO -> ps.setBoolean(n, Boolean.TRUE.equals(v));
                    default -> ps.setString(n, (String) v);
                }
            }
        });
    }
}
