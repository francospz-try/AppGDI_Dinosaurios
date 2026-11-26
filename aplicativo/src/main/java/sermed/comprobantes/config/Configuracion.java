package sermed.comprobantes.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Datos de conexión del aplicativo, leídos de un archivo de propiedades.
 *
 * <p>El archivo se busca, en este orden, en la ruta indicada por la
 * propiedad del sistema {@code comprobantes.config} y en
 * {@code config/conexion.properties} respecto de la carpeta de trabajo.
 * Las credenciales nunca se escriben en el código.</p>
 */
public final class Configuracion {

    /** Propiedad del sistema que permite indicar otra ruta del archivo. */
    public static final String PROPIEDAD_RUTA = "comprobantes.config";

    /** Ruta por defecto del archivo, relativa a la carpeta de trabajo. */
    public static final Path RUTA_POR_DEFECTO = Path.of("config", "conexion.properties");

    private final String host;
    private final int puerto;
    private final String base;
    private final String usuario;
    private final String clave;
    private final int tiempoEspera;

    /**
     * Crea una configuración con valores explícitos.
     *
     * @param host         servidor de PostgreSQL
     * @param puerto       puerto del servidor
     * @param base         nombre de la base de datos
     * @param usuario      usuario de PostgreSQL
     * @param clave        contraseña del usuario
     * @param tiempoEspera segundos de espera al conectar
     */
    public Configuracion(String host, int puerto, String base, String usuario,
                         String clave, int tiempoEspera) {
        this.host = host;
        this.puerto = puerto;
        this.base = base;
        this.usuario = usuario;
        this.clave = clave;
        this.tiempoEspera = tiempoEspera;
    }

    /**
     * Valores por defecto: PostgreSQL en el mismo equipo, con el puerto
     * estándar, la base del proyecto y el rol del aplicativo, sin
     * contraseña (se pide en la pantalla de conexión).
     *
     * @return configuración por defecto
     */
    public static Configuracion porDefecto() {
        return new Configuracion("localhost", 5432, "registro_comprobantes", "gdi_app", "", 5);
    }

    /**
     * Lee la configuración del archivo por defecto o del indicado en la
     * propiedad del sistema {@value #PROPIEDAD_RUTA}.
     *
     * @return la configuración leída
     * @throws ConfiguracionException si el archivo no existe o tiene un número no válido
     */
    public static Configuracion cargar() throws ConfiguracionException {
        String indicada = System.getProperty(PROPIEDAD_RUTA);
        return cargar(indicada != null ? Path.of(indicada) : RUTA_POR_DEFECTO);
    }

    /**
     * Lee la configuración de un archivo de propiedades en UTF-8.
     *
     * @param ruta ruta del archivo
     * @return la configuración leída
     * @throws ConfiguracionException si el archivo no existe o tiene un número no válido
     */
    public static Configuracion cargar(Path ruta) throws ConfiguracionException {
        if (!Files.isRegularFile(ruta)) {
            throw new ConfiguracionException("No se encontró el archivo de configuración "
                    + ruta.toAbsolutePath() + ". Copie config/conexion.example.properties como "
                    + "config/conexion.properties y complete los datos de conexión.");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(ruta);
             InputStreamReader lector = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(lector);
        } catch (IOException e) {
            throw new ConfiguracionException("No se pudo leer " + ruta.toAbsolutePath()
                    + ": " + e.getMessage());
        }
        // Cada dato se lee una sola vez; si falta o está en blanco, se usa el
        // valor por defecto (el de porDefecto()), salvo la contraseña, que
        // queda vacía y se pide en pantalla
        Configuracion d = porDefecto();
        return new Configuracion(
                texto(p, "db.host", d.host()),
                entero(p, "db.puerto", d.puerto(), 1, 65535),
                texto(p, "db.base", d.base()),
                texto(p, "db.usuario", d.usuario()),
                p.getProperty("db.clave", ""),
                entero(p, "db.tiempoEspera", d.tiempoEspera(), 1, 600));
    }

    /** Texto recortado de una propiedad, o el valor por defecto si falta o está en blanco. */
    private static String texto(Properties p, String clave, String porDefecto) {
        String valor = p.getProperty(clave);
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    /** Entero de una propiedad dentro de un rango, o el valor por defecto si falta. */
    private static int entero(Properties p, String clave, int porDefecto, int minimo, int maximo)
            throws ConfiguracionException {
        String valor = texto(p, clave, null);
        if (valor == null) {
            return porDefecto;
        }
        try {
            int n = Integer.parseInt(valor);
            if (n < minimo || n > maximo) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ConfiguracionException("El dato «" + clave + "» debe ser un número entero"
                    + " entre " + minimo + " y " + maximo + ": " + valor);
        }
    }

    /**
     * Devuelve una copia con otra contraseña (usada por la prueba de conexión).
     *
     * @param otraClave contraseña nueva
     * @return copia de la configuración
     */
    public Configuracion conClave(String otraClave) {
        return new Configuracion(host, puerto, base, usuario, otraClave, tiempoEspera);
    }

    /**
     * Devuelve una copia con otro puerto (usada por la prueba de conexión).
     *
     * @param otroPuerto puerto nuevo
     * @return copia de la configuración
     */
    public Configuracion conPuerto(int otroPuerto) {
        return new Configuracion(host, otroPuerto, base, usuario, clave, tiempoEspera);
    }

    /**
     * Devuelve una copia con otra base de datos (usada por la prueba de conexión).
     *
     * @param otraBase nombre de la base nueva
     * @return copia de la configuración
     */
    public Configuracion conBase(String otraBase) {
        return new Configuracion(host, puerto, otraBase, usuario, clave, tiempoEspera);
    }

    /** @return URL JDBC de la base de datos */
    public String urlJdbc() {
        return "jdbc:postgresql://" + host + ":" + puerto + "/" + base;
    }

    /** @return servidor */
    public String host() { return host; }

    /** @return puerto */
    public int puerto() { return puerto; }

    /** @return nombre de la base de datos */
    public String base() { return base; }

    /** @return usuario */
    public String usuario() { return usuario; }

    /** @return contraseña */
    public String clave() { return clave; }

    /** @return segundos de espera al conectar */
    public int tiempoEspera() { return tiempoEspera; }

    /** Descripción sin la contraseña, apta para mostrarse al usuario. */
    @Override
    public String toString() {
        return usuario + "@" + host + ":" + puerto + "/" + base;
    }
}
