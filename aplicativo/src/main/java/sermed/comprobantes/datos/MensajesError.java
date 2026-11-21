package sermed.comprobantes.datos;

import java.util.Map;

/**
 * Mensajes en español para las restricciones con nombre de la base de
 * datos (diccionario del modelo físico). Se usan cuando PostgreSQL rechaza
 * una operación e informa qué restricción se violó.
 */
final class MensajesError {

    private static final Map<String, String> RESTRICCIONES = Map.ofEntries(
        Map.entry("pk_cliente", "Ya existe un cliente con ese tipo y número de documento."),
        Map.entry("pk_producto_servicio", "Ya existe un producto o servicio con ese código."),
        Map.entry("pk_comprobante", "Ese comprobante (serie y número) ya está registrado."),
        Map.entry("pk_incluye", "Un mismo producto o servicio aparece en dos líneas del comprobante."),
        Map.entry("pk_referencia", "Un mismo documento relacionado aparece dos veces en el comprobante."),
        Map.entry("pk_documento_relacionado", "Ese documento relacionado ya existe."),
        Map.entry("ck_cliente_tipo_documento", "El tipo de documento debe ser DNI o RUC."),
        Map.entry("ck_cliente_numero_documento",
                "El número de documento debe tener 8 dígitos si es DNI y 11 si es RUC."),
        Map.entry("ck_cliente_nombre", "El nombre o razón social no puede estar vacío."),
        Map.entry("ck_serie_comprobante_serie", "La serie debe tener 4 letras mayúsculas o dígitos."),
        Map.entry("ck_moneda_codigo", "El código de moneda debe tener 3 letras mayúsculas."),
        Map.entry("ck_serie_comprobante_tipo",
                "El tipo de comprobante debe ser boleta de venta electrónica o factura electrónica."),
        Map.entry("pk_serie_comprobante", "Esa serie ya está registrada."),
        Map.entry("pk_moneda", "Esa moneda ya está registrada."),
        Map.entry("uq_moneda_nombre", "Ya existe una moneda con ese nombre."),
        Map.entry("ck_comprobante_numero", "El número del comprobante debe estar entre 1 y 99999999."),
        Map.entry("ck_comprobante_vencimiento",
                "La fecha de vencimiento no puede ser anterior a la fecha de emisión."),
        Map.entry("ck_comprobante_cliente",
                "Indique el tipo y el número de documento del cliente, o ninguno de los dos."),
        Map.entry("ck_producto_servicio_codigo", "El código del producto o servicio no puede estar vacío."),
        Map.entry("ck_producto_servicio_descripcion",
                "La descripción del producto o servicio no puede estar vacía."),
        Map.entry("ck_documento_relacionado_tipo",
                "El documento relacionado debe ser una guía de remisión o una cotización."),
        Map.entry("ck_incluye_cantidad", "La cantidad de cada línea debe ser mayor que cero."),
        Map.entry("ck_dom_importe", "Los importes no pueden ser negativos."),
        Map.entry("ck_dom_importe_linea", "Los valores de las líneas no pueden ser negativos."),
        Map.entry("fk_comprobante_serie", "La serie indicada no está registrada."),
        Map.entry("fk_comprobante_moneda", "La moneda indicada no está registrada."),
        Map.entry("fk_comprobante_cliente", "El cliente indicado no está registrado."),
        Map.entry("fk_incluye_producto_servicio", "El producto o servicio indicado no está registrado."),
        Map.entry("fk_incluye_comprobante", "El comprobante de la línea no está registrado."),
        Map.entry("fk_referencia_comprobante", "El comprobante de la referencia no está registrado."),
        Map.entry("fk_referencia_documento_relacionado", "El documento relacionado no está registrado."));

    private MensajesError() { }

    /**
     * @param restriccion nombre de la restricción violada
     * @return mensaje en español, o {@code null} si no se conoce
     */
    static String deRestriccion(String restriccion) {
        return restriccion == null ? null : RESTRICCIONES.get(restriccion);
    }
}
