package sermed.comprobantes.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Comprobante completo: cabecera, cliente, líneas de detalle y referencias.
 * Los importes que el formato del comprobante no imprime son {@code null}.
 *
 * @param serie             serie
 * @param numero            correlativo
 * @param tipoComprobante   tipo según la serie
 * @param fechaEmision      fecha de emisión
 * @param fechaVencimiento  fecha de vencimiento, o {@code null}
 * @param codigoMoneda      código de la moneda
 * @param nombreMoneda      nombre de la moneda
 * @param formaPago         forma de pago, o {@code null}
 * @param medioPago         medio de pago, o {@code null}
 * @param observacion       observación, o {@code null}
 * @param importes          resumen de importes
 * @param cliente           cliente, o {@code null} si es «CLIENTES VARIOS»
 * @param lineas            líneas de detalle
 * @param referencias       documentos citados
 */
public record Comprobante(String serie, int numero, String tipoComprobante,
                          LocalDate fechaEmision, LocalDate fechaVencimiento,
                          String codigoMoneda, String nombreMoneda,
                          String formaPago, String medioPago, String observacion,
                          Importes importes, Cliente cliente,
                          List<LineaDetalle> lineas, List<Referencia> referencias) {

    /**
     * Resumen de importes del comprobante (atributo compuesto {@code importes}).
     *
     * @param opGravada     operaciones gravadas
     * @param opExonerada   operaciones exoneradas
     * @param opInafecta    operaciones inafectas
     * @param opGratuita    operaciones gratuitas, o {@code null}
     * @param isc           impuesto selectivo al consumo, o {@code null}
     * @param igv           impuesto general a las ventas
     * @param otrosCargos   otros cargos, o {@code null}
     * @param otrosTributos otros tributos, o {@code null}
     * @param montoRedondeo redondeo, o {@code null}
     * @param importeTotal  importe total
     * @param percepcion    percepción, o {@code null}
     */
    public record Importes(BigDecimal opGravada, BigDecimal opExonerada, BigDecimal opInafecta,
                           BigDecimal opGratuita, BigDecimal isc, BigDecimal igv,
                           BigDecimal otrosCargos, BigDecimal otrosTributos,
                           BigDecimal montoRedondeo, BigDecimal importeTotal,
                           BigDecimal percepcion) { }

    /**
     * Da formato a una numeración como la imprimen los comprobantes: el
     * sistema del proveedor (series B001 y F001) completa el número con
     * ceros hasta seis cifras, y el sistema de la SUNAT (series que empiezan
     * con E, como EB01) lo imprime sin ceros.
     *
     * @param serie  serie
     * @param numero correlativo
     * @return por ejemplo, F001-005206 o EB01-50
     */
    public static String numeracion(String serie, int numero) {
        return serie.startsWith("E") ? serie + "-" + numero
                : String.format("%s-%06d", serie, numero);
    }

    /** @return serie y número con el formato impreso */
    public String numeracion() {
        return numeracion(serie, numero);
    }
}
