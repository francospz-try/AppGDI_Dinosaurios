package sermed.comprobantes.modelo;

/**
 * Producto o servicio vendido (tabla {@code producto_servicio}).
 *
 * @param codigo       código asignado por la empresa
 * @param descripcion  descripción impresa en el detalle
 * @param unidadMedida unidad tal como fue impresa (UND, SERV, UNIDAD…)
 * @param lineas       número de líneas de detalle en que aparece
 */
public record ProductoServicio(String codigo, String descripcion, String unidadMedida,
                               long lineas) { }
