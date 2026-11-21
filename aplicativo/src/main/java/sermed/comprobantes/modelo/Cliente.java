package sermed.comprobantes.modelo;

/**
 * Cliente identificado de un comprobante (tabla {@code cliente}).
 *
 * @param tipoDocumento     DNI o RUC
 * @param numeroDocumento   8 dígitos si es DNI, 11 si es RUC
 * @param nombreRazonSocial nombre de la persona o razón social
 * @param direccion         dirección, o {@code null} si no se consignó
 * @param comprobantes      número de comprobantes emitidos al cliente
 */
public record Cliente(String tipoDocumento, String numeroDocumento, String nombreRazonSocial,
                      String direccion, long comprobantes) { }
