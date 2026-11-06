package pe.edu.ucsm.gdi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado desconectado: conserva NULL y BigDecimal sin alterar precisión. */
public record TableData(List<String> columns, List<List<Object>> rows) {
    public TableData {
        columns = List.copyOf(columns);
        rows = rows.stream().map(r -> Collections.unmodifiableList(new ArrayList<>(r))).toList();
    }
    public Object value(int row, String column) {
        int index = columns.indexOf(column);
        if (index < 0) throw new IllegalArgumentException("Columna inexistente: " + column);
        return rows.get(row).get(index);
    }
}
