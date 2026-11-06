package pe.edu.ucsm.gdi;

import java.sql.SQLException;

/** Traduce errores sin mostrar SQL, datos personales o credenciales. */
public final class UserMessages {
    private UserMessages() { }
    public static String error(Throwable error) {
        if (error instanceof SQLException sql) {
            String state=sql.getSQLState();
            if (state == null) return "No se pudo completar la operación de base de datos.";
            if (state.startsWith("08")) return "Sin conexión. Revise servidor, puerto y disponibilidad de PostgreSQL.";
            if (state.startsWith("28")) return "Usuario o contraseña rechazados por PostgreSQL.";
            return switch(state) {
                case "23505" -> "Ya existe un registro con ese identificador, incluso si tiene baja lógica.";
                case "23503" -> "El registro está relacionado con otros datos o la referencia no existe.";
                case "23514", "23502", "22001", "22003" -> "Datos inválidos. Revise obligatoriedad, longitud y formato de los campos.";
                case "P0002" -> "El registro ya no está disponible. Actualice la lista.";
                case "42883", "3F000", "42P01", "42703" -> "La base necesita los scripts de instalación o actualización del aplicativo.";
                case "42501" -> "El usuario de base de datos no tiene los permisos necesarios.";
                case "57014", "55P03" -> "La operación excedió el tiempo de espera. Intente nuevamente.";
                default -> "La operación no se completó (código " + state + "). No se guardaron cambios parciales.";
            };
        }
        if (error instanceof IllegalArgumentException) return error.getMessage();
        return "No se pudo completar la operación. Revise la configuración local.";
    }
}
