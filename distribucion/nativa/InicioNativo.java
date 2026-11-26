package sermed.comprobantes.distribucion;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import org.postgresql.core.Parser;
import sermed.comprobantes.App;
import sermed.comprobantes.config.Configuracion;

public final class InicioNativo {
    private static final String BASE = "registro_comprobantes";
    private static final String USUARIO = "gdi_app";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String EXE = WINDOWS ? ".exe" : "";

    private final Path programa;
    private final Path carpeta;
    private final Path datos;
    private final Path config;
    private final Path log;
    private final boolean verificar;
    private final boolean embebido;

    private InicioNativo(Path programa, Path carpeta, boolean verificar, boolean embebido) {
        this.programa = programa;
        this.carpeta = carpeta;
        this.verificar = verificar;
        this.embebido = embebido;
        datos = carpeta.resolve("postgresql");
        config = carpeta.resolve("config.ini");
        log = carpeta.resolve("arranque.log");
    }

    public static void main(String[] args) {
        boolean verificar = args.length == 3 && args[1].equals("--verificar");
        boolean detener = args.length == 2 && args[1].equals("--detener");
        Path programa = Path.of(args[0]).toAbsolutePath();
        Path carpeta = programa;
        try {
            String modo = Files.readString(programa.resolve("modo.txt")).strip();
            boolean embebido = modo.equals("embebido");
            if (!embebido && !modo.equals("headless")) {
                throw new IOException("Variante de instalación desconocida.");
            }
            carpeta = verificar ? Files.createTempDirectory(Path.of(args[2]), "appgdi-prueba-")
                    : carpetaUsuario(modo);
            var inicio = new InicioNativo(programa, carpeta, verificar, embebido);
            inicio.abrir(detener);
        } catch (Exception error) {
            error.printStackTrace();
            if (!verificar) {
                JOptionPane.showMessageDialog(null,
                        "No se pudo completar la operación de AppGDI. " + error.getMessage()
                                + "\nRegistro: " + carpeta.resolve("arranque.log"),
                        "AppGDI Dinosaurios", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }

    private static Path carpetaUsuario(String modo) {
        if (WINDOWS) {
            return Path.of(System.getenv("LOCALAPPDATA"), "AppGDI-Dinosaurios", modo);
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        Path raiz = xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(xdg);
        return raiz.resolve("AppGDI-Dinosaurios").resolve(modo).toAbsolutePath();
    }

    private void abrir(boolean soloDetener) throws Exception {
        if (soloDetener && !Files.exists(datos.resolve("PG_VERSION"))) {
            return;
        }
        Files.createDirectories(carpeta);
        if (!WINDOWS) {
            Files.setPosixFilePermissions(carpeta, PosixFilePermissions.fromString("rwx------"));
        }
        FileChannel canal = FileChannel.open(carpeta.resolve("inicio.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock bloqueo = canal.tryLock();
        if (bloqueo == null) {
            canal.close();
            throw new IOException("AppGDI ya está abierta.");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (embebido || verificar) {
                    detener();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    bloqueo.close();
                    canal.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "cerrar-postgresql"));

        if (soloDetener) {
            detener();
            return;
        }
        Properties claves = cargarOCrearClaves();
        String claveAdmin = claves.getProperty("admin.clave");
        if (!Files.isRegularFile(datos.resolve("PG_VERSION"))) {
            inicializar(claveAdmin);
        }
        int puerto;
        if (estaActivo() && !embebido) {
            puerto = Integer.parseInt(claves.getProperty("db.puerto"));
            try (Connection conexion = conectar(puerto, "postgres", "postgres", claveAdmin)) {
                if (!conexion.isValid(5)) {
                    throw new IOException("La instancia local de PostgreSQL no responde.");
                }
            }
        } else {
            detener();
            puerto = elegirPuerto();
            claves.setProperty("db.puerto", Integer.toString(puerto));
            guardar(claves, config);
            ejecutarPg("pg_ctl", "-D", datos.toString(), "-w", "-t", "60",
                    "-l", carpeta.resolve("postgresql.log").toString(), "-o",
                    "-h 127.0.0.1 -p " + puerto + " -c unix_socket_directories= -c jit=off", "start");
        }
        if (!Files.exists(carpeta.resolve("base-lista"))) {
            prepararBase(puerto, claveAdmin, claves.getProperty("db.clave"));
            Files.createFile(carpeta.resolve("base-lista"));
        }

        try (Connection conexion = conectar(puerto, BASE, USUARIO, claves.getProperty("db.clave"));
             Statement consulta = conexion.createStatement()) {
            consulta.executeQuery("SELECT 1").close();
        }
        System.setProperty(Configuracion.PROPIEDAD_RUTA, config.toString());
        if (verificar) {
            probar(puerto, claveAdmin);
        } else {
            App.main(new String[0]);
        }
    }

    private Properties cargarOCrearClaves() throws IOException {
        Properties claves = new Properties();
        if (Files.exists(config)) {
            try (var entrada = Files.newInputStream(config)) {
                claves.load(entrada);
            }
            for (String nombre : List.of("db.clave", "admin.clave")) {
                if (!claves.getProperty(nombre, "").matches("[A-Za-z0-9_-]{16,128}")) {
                    throw new IOException("Clave inválida en config.ini.");
                }
            }
            return claves;
        }
        if (Files.exists(datos.resolve("PG_VERSION"))) {
            throw new IOException("Restaura config.ini para conectar con la base existente.");
        }
        claves.setProperty("db.host", "127.0.0.1");
        claves.setProperty("db.base", BASE);
        claves.setProperty("db.usuario", USUARIO);
        claves.setProperty("db.clave", verificar ? generarClave() : pedirClave(generarClave()));
        claves.setProperty("admin.clave", generarClave());
        guardar(claves, config);
        return claves;
    }

    private static String pedirClave(String generada) {
        int respuesta = JOptionPane.showConfirmDialog(null,
                "Se generó una contraseña para gdi_app. ¿Quieres cambiarla?",
                "Primera ejecución de AppGDI", JOptionPane.YES_NO_OPTION);
        if (respuesta != JOptionPane.YES_OPTION) {
            return generada;
        }
        while (true) {
            JPasswordField campo = new JPasswordField(24);
            int decision = JOptionPane.showConfirmDialog(null, new Object[]{
                    "Usa de 16 a 128 letras ASCII, números, guiones o guiones bajos.", campo
            }, "Nueva contraseña de gdi_app", JOptionPane.OK_CANCEL_OPTION);
            if (decision != JOptionPane.OK_OPTION) {
                return generada;
            }
            char[] caracteres = campo.getPassword();
            String clave = new String(caracteres);
            Arrays.fill(caracteres, '\0');
            if (clave.matches("[A-Za-z0-9_-]{16,128}")) {
                return clave;
            }
            JOptionPane.showMessageDialog(null, "La contraseña no cumple el formato.");
        }
    }

    private static String generarClave() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void guardar(Properties propiedades, Path destino) throws IOException {
        Path temporal = Files.createTempFile(carpeta, "config-", ".tmp");
        try {
            try (var salida = Files.newOutputStream(temporal)) {
                propiedades.store(salida, "AppGDI Dinosaurios");
            }
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private void inicializar(String claveAdmin) throws Exception {
        Path archivo = Files.createTempFile(carpeta, "initdb-", ".tmp");
        try {
            Files.writeString(archivo, claveAdmin + "\n");
            ejecutarPg("initdb", "-D", datos.toString(), "-U", "postgres",
                    "--encoding=UTF8", "--locale=C", "--auth=scram-sha-256",
                    "--pwfile=" + archivo, "--no-instructions");
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    private static int elegirPuerto() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private void prepararBase(int puerto, String admin, String claveApp) throws Exception {
        String sql = Files.readString(programa.resolve("base.sql"));
        String separador = "\\connect " + BASE;
        int cambio = sql.indexOf("\n" + separador + "\n");
        if (cambio < 0) {
            throw new IOException("El script de instalación no contiene la sección de la base.");
        }
        // Solo se ejecuta sobre el clúster privado antes de crear el marcador base-lista.
        try (Connection conexion = conectar(puerto, "postgres", "postgres", admin);
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP DATABASE IF EXISTS " + BASE);
            sentencia.execute("CREATE DATABASE " + BASE + " ENCODING 'UTF8' TEMPLATE template0");
        }
        String esquema = sql.substring(cambio + separador.length() + 2)
                .replaceAll("(?m)^\\\\set ON_ERROR_STOP on$", "");
        try (Connection conexion = conectar(puerto, BASE, "postgres", admin);
             Statement sentencia = conexion.createStatement()) {
            for (var consulta : Parser.parseJdbcSql(esquema, true, false, true, false, false)) {
                sentencia.execute(consulta.nativeSql);
            }
            // La clave se ha limitado a letras ASCII, dígitos, '-' y '_'.
            sentencia.execute("ALTER ROLE " + USUARIO + " PASSWORD '" + claveApp + "'");
        }
    }

    private static Connection conectar(int puerto, String base, String usuario, String clave)
            throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + puerto + "/" + base
                + "?connectTimeout=5", usuario, clave);
    }

    private int pg(String herramienta, String... argumentos) throws Exception {
        var comando = new ArrayList<String>();
        comando.add(programa.resolve("postgresql/bin/" + herramienta + EXE).toString());
        comando.addAll(List.of(argumentos));
        var proceso = new ProcessBuilder(comando).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        return proceso.start().waitFor();
    }

    private void ejecutarPg(String herramienta, String... argumentos) throws Exception {
        int estado = pg(herramienta, argumentos);
        if (estado != 0) {
            throw new IOException(herramienta + " terminó con código " + estado + ".");
        }
    }

    private boolean estaActivo() throws Exception {
        return Files.isRegularFile(datos.resolve("PG_VERSION"))
                && pg("pg_ctl", "-D", datos.toString(), "status") == 0;
    }

    private void detener() throws Exception {
        if (estaActivo()) {
            ejecutarPg("pg_ctl", "-D", datos.toString(), "-w", "-t", "20", "-m", "fast", "stop");
        }
    }

    private void probar(int puerto, String claveAdmin) throws Exception {
        for (String prueba : List.of("PruebaConfiguracion", "PruebaConexion", "PruebasHito30",
                "PruebasHito70", "PruebasHito100", "PruebaInterfaz")) {
            ejecutarPrueba(prueba, List.of());
        }
        Path configAdmin = carpeta.resolve("pruebas-admin.ini");
        var admin = new Properties();
        admin.setProperty("db.host", "127.0.0.1");
        admin.setProperty("db.puerto", Integer.toString(puerto));
        admin.setProperty("db.base", BASE);
        admin.setProperty("db.usuario", "postgres");
        admin.setProperty("db.clave", claveAdmin);
        guardar(admin, configAdmin);
        try {
            ejecutarPrueba("PruebaIntegridad", List.of(configAdmin.toString()));
        } finally {
            Files.deleteIfExists(configAdmin);
        }
    }

    private void ejecutarPrueba(String prueba, List<String> argumentos) throws Exception {
        var comando = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java" + EXE).toString(),
                "-Djava.awt.headless=true", "-Djava.io.tmpdir=" + carpeta,
                "-D" + Configuracion.PROPIEDAD_RUTA + "=" + config,
                "-cp", programa.resolve("app.jar").toString(),
                "sermed.comprobantes.pruebas." + prueba
        ));
        comando.addAll(argumentos);
        if (new ProcessBuilder(comando).inheritIO().start().waitFor() != 0) {
            throw new IOException("Falló " + prueba);
        }
    }
}
