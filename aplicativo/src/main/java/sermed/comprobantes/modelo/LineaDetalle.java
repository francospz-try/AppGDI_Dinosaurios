package sermed.comprobantes.modelo;

import java.math.BigDecimal;

/**
 * Línea de detalle de un comprobante (tabla {@code incluye}).
 *
 * @param codigo         código del producto o servicio
 * @param descripcion    descripción del producto o servicio
 * @param unidadMedida   unidad de medida
 * @param cantidad       cantidad
 * @param valorUnitario  valor unitario sin impuestos
 * @param precioUnitario precio unitario con impuestos, o {@code null}
 * @param descuento      descuento de la línea
 * @param importeVenta   importe de venta con impuestos
 */
public record LineaDetalle(String codigo, String descripcion, String unidadMedida,
                           BigDecimal cantidad, BigDecimal valorUnitario,
                           BigDecimal precioUnitario, BigDecimal descuento,
                           BigDecimal importeVenta) { }
