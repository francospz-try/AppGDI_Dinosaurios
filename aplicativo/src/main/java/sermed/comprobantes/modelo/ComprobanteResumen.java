package sermed.comprobantes.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de la lista de comprobantes de un periodo.
 *
 * @param serie           serie del comprobante
 * @param numero          correlativo dentro de la serie
 * @param tipoComprobante tipo según la serie
 * @param fechaEmision    fecha de emisión
 * @param cliente         nombre del cliente o «CLIENTES VARIOS»
 * @param importeTotal    importe total impreso
 */
public record ComprobanteResumen(String serie, int numero, String tipoComprobante,
                                 LocalDate fechaEmision, String cliente,
                                 BigDecimal importeTotal) {

    /** @return serie y número con el formato impreso, como F001-005206 */
    public String numeracion() {
        return Comprobante.numeracion(serie, numero);
    }
}
