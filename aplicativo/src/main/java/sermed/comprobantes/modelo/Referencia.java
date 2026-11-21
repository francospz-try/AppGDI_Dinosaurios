package sermed.comprobantes.modelo;

/**
 * Documento citado por un comprobante (tablas {@code referencia} y
 * {@code documento_relacionado}).
 *
 * @param tipoRelacionado   guía de remisión o cotización
 * @param numeroRelacionado número completo del documento
 */
public record Referencia(String tipoRelacionado, String numeroRelacionado) { }
