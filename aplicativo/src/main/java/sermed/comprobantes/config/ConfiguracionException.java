package sermed.comprobantes.config;

/** Error al leer el archivo de configuración; su mensaje se muestra al usuario. */
public class ConfiguracionException extends Exception {

    /**
     * @param mensaje explicación en español del problema y de cómo resolverlo
     */
    public ConfiguracionException(String mensaje) {
        super(mensaje);
    }
}
