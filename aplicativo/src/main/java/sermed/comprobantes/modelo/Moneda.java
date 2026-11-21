package sermed.comprobantes.modelo;

/**
 * Moneda de los importes (tabla {@code moneda}).
 *
 * @param codigo código ISO 4217, como PEN
 * @param nombre nombre impreso, como SOLES
 */
public record Moneda(String codigo, String nombre) {
    @Override
    public String toString() {
        return codigo + " — " + nombre;
    }
}
