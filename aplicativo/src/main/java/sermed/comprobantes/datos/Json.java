package sermed.comprobantes.datos;

import java.math.BigDecimal;
import java.util.List;

import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.Referencia;

/**
 * Convierte las líneas y referencias de un comprobante al JSON que reciben
 * los procedimientos {@code sp_insertar_comprobante} y
 * {@code sp_modificar_comprobante}. Es un escritor mínimo, sin bibliotecas.
 */
final class Json {

    private Json() { }

    /**
     * @param lineas líneas de detalle
     * @return arreglo JSON de líneas
     */
    static String lineas(List<LineaDetalle> lineas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lineas.size(); i++) {
            LineaDetalle l = lineas.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            campo(sb, "codigo", l.codigo()).append(',');
            campo(sb, "descripcion", l.descripcion()).append(',');
            campo(sb, "unidad_medida", l.unidadMedida()).append(',');
            numero(sb, "cantidad", l.cantidad()).append(',');
            numero(sb, "valor_unitario", l.valorUnitario()).append(',');
            numero(sb, "precio_unitario", l.precioUnitario()).append(',');
            numero(sb, "descuento", l.descuento()).append(',');
            numero(sb, "importe_venta", l.importeVenta());
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    /**
     * @param referencias documentos citados
     * @return arreglo JSON de referencias
     */
    static String referencias(List<Referencia> referencias) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < referencias.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            campo(sb, "tipo_relacionado", referencias.get(i).tipoRelacionado()).append(',');
            campo(sb, "numero_relacionado", referencias.get(i).numeroRelacionado());
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static StringBuilder campo(StringBuilder sb, String nombre, String valor) {
        sb.append('"').append(nombre).append("\":");
        if (valor == null) {
            return sb.append("null");
        }
        sb.append('"');
        for (char ch : valor.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"');
    }

    private static StringBuilder numero(StringBuilder sb, String nombre, BigDecimal valor) {
        sb.append('"').append(nombre).append("\":");
        return sb.append(valor == null ? "null" : valor.toPlainString());
    }
}
