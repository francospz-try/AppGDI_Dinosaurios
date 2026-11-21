package sermed.comprobantes.datos;

import java.util.List;

/**
 * Resultado tabular genérico de una consulta: los nombres de las columnas,
 * tal como los devuelve la función de la base de datos, y las filas.
 *
 * @param columnas nombres de las columnas
 * @param filas    valores de cada fila, en el orden de las columnas
 */
public record Tabla(List<String> columnas, List<List<Object>> filas) { }
