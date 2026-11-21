package sermed.comprobantes.modelo;

/**
 * Serie de comprobantes (tabla {@code serie_comprobante}).
 *
 * @param serie           código de cuatro caracteres, como F001
 * @param tipoComprobante boleta de venta electrónica o factura electrónica
 */
public record Serie(String serie, String tipoComprobante) {
    @Override
    public String toString() {
        return serie + " — " + tipoComprobante;
    }
}
