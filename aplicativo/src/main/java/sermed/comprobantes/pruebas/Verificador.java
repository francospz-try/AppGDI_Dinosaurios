package sermed.comprobantes.pruebas;

/**
 * Registro mínimo de resultados de pruebas, sin bibliotecas externas.
 * Cada comprobación imprime PASA o FALLA y al final un resumen.
 */
final class Verificador {

    /** Comprobación que puede lanzar cualquier excepción. */
    @FunctionalInterface
    interface Caso {
        /**
         * @return detalle que se imprime si pasa
         * @throws Exception si la comprobación falla
         */
        String ejecutar() throws Exception;
    }

    private int pasan;
    private int fallan;

    /**
     * Ejecuta un caso; cualquier excepción o {@link AssertionError} lo hace fallar.
     *
     * @param nombre descripción del caso
     * @param caso   comprobación
     */
    void caso(String nombre, Caso caso) {
        try {
            String detalle = caso.ejecutar();
            pasan++;
            System.out.println("[PASA]  " + nombre + (detalle.isEmpty() ? "" : "\n        " + detalle));
        } catch (Throwable t) {
            fallan++;
            System.out.println("[FALLA] " + nombre + "\n        " + t);
        }
    }

    /**
     * @param condicion condición esperada
     * @param mensaje   explicación si no se cumple
     */
    static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    /**
     * Imprime el resumen y termina el proceso con código 0 si todo pasó.
     */
    void terminar() {
        System.out.println();
        System.out.println("Resultado: " + pasan + " pasan, " + fallan + " fallan");
        System.exit(fallan == 0 ? 0 : 1);
    }
}
