package sermed.comprobantes.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formatos de presentación comunes a todas las pantallas. */
public final class Formato {

    /** Fecha como la imprimen los comprobantes: dd/mm/aaaa. */
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formato() { }

    /**
     * @param fecha fecha, puede ser {@code null}
     * @return fecha en dd/mm/aaaa, o cadena vacía
     */
    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : FECHA.format(fecha);
    }

    /**
     * Muestra un importe sin ceros decimales sobrantes más allá de los dos
     * primeros, de modo que 119.9999997 se ve completo y 300.0000000 como
     * 300.00. Un importe nulo (no impreso) se muestra vacío.
     *
     * @param valor importe, puede ser {@code null}
     * @return texto del importe
     */
    public static String importe(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        BigDecimal v = valor.stripTrailingZeros();
        if (v.scale() < 2) {
            v = v.setScale(2);
        }
        return v.toPlainString();
    }

    /**
     * @param texto texto, puede ser {@code null}
     * @return el texto o cadena vacía
     */
    public static String texto(String texto) {
        return texto == null ? "" : texto;
    }
}
