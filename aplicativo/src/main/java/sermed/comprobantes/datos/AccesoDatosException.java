package sermed.comprobantes.datos;

/**
 * Error de acceso a la base de datos, con un mensaje en español apto para
 * mostrarse al usuario y una categoría que permite reaccionar según la
 * causa. Conserva la excepción original como causa.
 */
public class AccesoDatosException extends Exception {

    /** Categoría del error, según el código SQLSTATE de PostgreSQL. */
    public enum Tipo {
        /** No se pudo conectar (servidor, credenciales o base inexistente). */
        CONEXION,
        /** Clave primaria o única repetida (23505). */
        CLAVE_DUPLICADA,
        /** Clave foránea inexistente o en uso (23503). */
        REFERENCIA,
        /** Restricción de verificación, de no nulidad o regla de negocio (23514, 23502). */
        VERIFICACION,
        /** Dato demasiado largo o fuera de rango (clase 22). */
        DATO_INVALIDO,
        /** El registro indicado no existe o no está vigente (P0002). */
        NO_ENCONTRADO,
        /** El rol de la conexión no tiene permiso para la operación (42501). */
        PERMISO,
        /** Cualquier otro error. */
        OTRO
    }

    private final Tipo tipo;

    /**
     * @param mensaje explicación para el usuario
     * @param causa   excepción original del controlador JDBC
     */
    public AccesoDatosException(String mensaje, Throwable causa) {
        this(Tipo.OTRO, mensaje, causa);
    }

    /**
     * @param tipo    categoría del error
     * @param mensaje explicación para el usuario
     * @param causa   excepción original del controlador JDBC
     */
    public AccesoDatosException(Tipo tipo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
    }

    /** @return categoría del error */
    public Tipo tipo() {
        return tipo;
    }
}
