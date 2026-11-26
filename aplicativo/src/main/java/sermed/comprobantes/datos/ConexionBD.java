package sermed.comprobantes.datos;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.postgresql.util.PSQLException;

import sermed.comprobantes.config.Configuracion;

/**
 * Capa única de acceso a la base de datos. Todo el aplicativo se comunica
 * con PostgreSQL a través de esta clase, mediante JDBC y sin frameworks.
 *
 * <p>Cada operación abre su propia conexión, la usa y la cierra con
 * {@code try-with-resources}, junto con la sentencia y el resultado, aunque
 * ocurra un error. Para un aplicativo de escritorio con un solo usuario a la
 * vez, este esquema es suficiente y evita conexiones abiertas olvidadas.
 * Los errores de JDBC se traducen a {@link AccesoDatosException} con un
 * mensaje en español.</p>
 */
public final class ConexionBD {

    /** Fija los parámetros de una sentencia preparada. */
    @FunctionalInterface
    public interface Parametros {
        /**
         * @param ps sentencia a la que se asignan los parámetros
         * @throws SQLException si un parámetro no puede asignarse
         */
        void fijar(PreparedStatement ps) throws SQLException;
    }

    /**
     * Convierte la fila actual de un resultado en un objeto.
     *
     * @param <T> tipo del objeto
     */
    @FunctionalInterface
    public interface Mapeador<T> {
        /**
         * @param rs resultado posicionado en una fila
         * @return objeto construido con la fila
         * @throws SQLException si una columna no puede leerse
         */
        T mapear(ResultSet rs) throws SQLException;
    }

    /**
     * Datos del servidor obtenidos al probar la conexión.
     *
     * @param version      versión de PostgreSQL
     * @param base         base de datos conectada
     * @param usuario      usuario de la conexión
     * @param servidor     servidor y puerto
     * @param milisegundos tiempo empleado en conectar y consultar
     */
    public record InfoConexion(String version, String base, String usuario,
                               String servidor, long milisegundos) { }

    /** Parámetros vacíos, para sentencias sin parámetros. */
    public static final Parametros SIN_PARAMETROS = ps -> { };

    private volatile Configuracion config;

    /**
     * @param config datos de conexión leídos del archivo de configuración
     *               o ingresados en la pantalla de conexión
     */
    public ConexionBD(Configuracion config) {
        this.config = config;
    }

    /**
     * Reemplaza los datos de conexión, por ejemplo después de que el
     * usuario los ingresa en la pantalla de conexión. Las operaciones
     * siguientes usan los nuevos datos.
     *
     * @param nueva datos de conexión ya probados
     */
    public void usar(Configuracion nueva) {
        this.config = nueva;
    }

    /** @return la configuración usada por esta capa */
    public Configuracion configuracion() {
        return config;
    }

    /**
     * Abre una conexión nueva. Quien la recibe debe cerrarla, de preferencia
     * con {@code try-with-resources}.
     *
     * @return conexión abierta
     * @throws AccesoDatosException si no se puede conectar
     */
    public Connection abrir() throws AccesoDatosException {
        Properties props = new Properties();
        props.setProperty("user", config.usuario());
        props.setProperty("password", config.clave());
        props.setProperty("connectTimeout", String.valueOf(config.tiempoEspera()));
        // Ninguna operación espera indefinidamente: 15 s por sentencia, 5 s
        // por un bloqueo y 30 s sin respuesta del servidor
        props.setProperty("socketTimeout", "30");
        props.setProperty("options", "-c statement_timeout=15000 -c lock_timeout=5000");
        props.setProperty("ApplicationName", "registro-comprobantes");
        try {
            return DriverManager.getConnection(config.urlJdbc(), props);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * Ejecuta una consulta y convierte cada fila en un objeto.
     *
     * @param <T>        tipo de los objetos
     * @param sql        sentencia SQL con parámetros {@code ?}
     * @param parametros asigna los parámetros
     * @param mapeador   convierte cada fila
     * @return lista de objetos, vacía si no hubo filas
     * @throws AccesoDatosException si la consulta falla
     */
    public <T> List<T> consultar(String sql, Parametros parametros, Mapeador<T> mapeador)
            throws AccesoDatosException {
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            parametros.fijar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> filas = new ArrayList<>();
                while (rs.next()) {
                    filas.add(mapeador.mapear(rs));
                }
                return filas;
            }
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * Ejecuta una consulta que devuelve a lo sumo una fila.
     *
     * @param <T>        tipo del objeto
     * @param sql        sentencia SQL con parámetros {@code ?}
     * @param parametros asigna los parámetros
     * @param mapeador   convierte la fila
     * @return el objeto, o vacío si no hubo filas
     * @throws AccesoDatosException si la consulta falla
     */
    public <T> Optional<T> consultarUno(String sql, Parametros parametros, Mapeador<T> mapeador)
            throws AccesoDatosException {
        List<T> filas = consultar(sql, parametros, mapeador);
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    /**
     * Ejecuta una consulta y devuelve sus columnas y filas sin convertirlas
     * a objetos del modelo; la usan los reportes, cuyas columnas dependen de
     * cada función.
     *
     * @param sql        sentencia SQL con parámetros {@code ?}
     * @param parametros asigna los parámetros
     * @return columnas y filas del resultado
     * @throws AccesoDatosException si la consulta falla
     */
    public Tabla consultarTabla(String sql, Parametros parametros) throws AccesoDatosException {
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            parametros.fijar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> columnas = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    columnas.add(md.getColumnLabel(i));
                }
                List<List<Object>> filas = new ArrayList<>();
                while (rs.next()) {
                    List<Object> fila = new ArrayList<>(columnas.size());
                    for (int i = 1; i <= columnas.size(); i++) {
                        Object valor = rs.getObject(i);
                        fila.add(valor instanceof Date d ? d.toLocalDate() : valor);
                    }
                    filas.add(fila);
                }
                return new Tabla(columnas, filas);
            }
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * Comprueba la conexión: conecta, consulta la versión del servidor y
     * mide el tiempo empleado.
     *
     * @return datos del servidor
     * @throws AccesoDatosException si no se puede conectar
     */
    public InfoConexion probar() throws AccesoDatosException {
        long inicio = System.nanoTime();
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT current_setting('server_version'), current_database(), current_user");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return new InfoConexion("PostgreSQL " + rs.getString(1), rs.getString(2),
                    rs.getString(3), config.host() + ":" + config.puerto(), ms);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * Traduce un error de JDBC a un mensaje en español según su código
     * SQLSTATE de PostgreSQL.
     *
     * @param e error original
     * @return excepción con mensaje para el usuario
     */
    AccesoDatosException traducir(SQLException e) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        String servidor = mensajeServidor(e);
        String restriccion = e instanceof PSQLException p && p.getServerErrorMessage() != null
                ? p.getServerErrorMessage().getConstraint() : null;
        String conocido = MensajesError.deRestriccion(restriccion);
        if (estado.equals("28P01") || estado.equals("28000")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.CONEXION,
                    "Usuario o contraseña incorrectos para «" + config.usuario()
                    + "». Revise los datos de conexión (Archivo > Conectar…).", e);
        } else if (estado.equals("3D000")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.CONEXION,
                    "La base de datos «" + config.base() + "» no existe en el servidor. Créela con"
                    + " scripts/registro_comprobantes_completo.sql o corrija su nombre.", e);
        } else if (estado.startsWith("08")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.CONEXION,
                    "No se pudo conectar con el servidor de base de datos. Verifique que "
                    + "PostgreSQL esté en ejecución y que el servidor y el puerto sean correctos.", e);
        } else if (estado.equals("42883") || estado.equals("42P01")) {
            return new AccesoDatosException("Falta un objeto en la base de datos (" + servidor
                    + "). Vuelva a crearla con scripts/registro_comprobantes_completo.sql.", e);
        } else if (estado.equals("23505")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.CLAVE_DUPLICADA,
                    conocido != null ? conocido : servidor, e);
        } else if (estado.equals("23503")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.REFERENCIA,
                    conocido != null ? conocido : servidor, e);
        } else if (estado.equals("23514")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.VERIFICACION,
                    conocido != null ? conocido : servidor, e);
        } else if (estado.equals("23502")) {
            String columna = e instanceof PSQLException p && p.getServerErrorMessage() != null
                    ? p.getServerErrorMessage().getColumn() : null;
            return new AccesoDatosException(AccesoDatosException.Tipo.VERIFICACION,
                    "Falta un dato obligatorio" + (columna == null ? "" : ": " + columna) + ".", e);
        } else if (estado.equals("22001")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.DATO_INVALIDO,
                    "Un texto supera la longitud máxima permitida.", e);
        } else if (estado.startsWith("22")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.DATO_INVALIDO,
                    "Un dato tiene un formato o un tamaño no válido: " + servidor, e);
        } else if (estado.equals("P0002")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.NO_ENCONTRADO, servidor, e);
        } else if (estado.equals("42501")) {
            return new AccesoDatosException(AccesoDatosException.Tipo.PERMISO,
                    "El usuario «" + config.usuario() + "» no tiene permiso para esta operación."
                    + " El aplicativo debe conectarse con el rol gdi_app, creado por"
                    + " scripts/08_permisos.sql.", e);
        } else if (estado.equals("57014") || estado.equals("55P03")) {
            return new AccesoDatosException("La base de datos tardó demasiado en responder o el"
                    + " registro está siendo modificado por otro usuario. Intente de nuevo.", e);
        }
        return new AccesoDatosException("Error de la base de datos: " + servidor
                + (estado.isEmpty() ? "" : " (SQLSTATE " + estado + ")"), e);
    }

    /** Primer renglón del mensaje del servidor, sin el prefijo «ERROR:». */
    private static String mensajeServidor(SQLException e) {
        if (e instanceof PSQLException p && p.getServerErrorMessage() != null
                && p.getServerErrorMessage().getMessage() != null) {
            return p.getServerErrorMessage().getMessage();
        }
        String m = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
        return m.startsWith("ERROR: ") ? m.substring(7) : m;
    }

    /**
     * Ejecuta una sentencia que no devuelve filas, como la llamada a un
     * procedimiento ({@code CALL sp_...}). Con la conexión en modo de
     * confirmación automática, la sentencia es una transacción: si el
     * procedimiento o un disparador diferido la rechazan, no queda nada
     * registrado.
     *
     * @param sql        sentencia SQL con parámetros {@code ?}
     * @param parametros asigna los parámetros
     * @throws AccesoDatosException si la base de datos rechaza la operación
     */
    public void ejecutar(String sql, Parametros parametros) throws AccesoDatosException {
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            parametros.fijar(ps);
            ps.execute();
        } catch (SQLException e) {
            throw traducir(e);
        }
    }
}
