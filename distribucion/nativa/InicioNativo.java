package sermed.comprobantes.distribucion;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.postgresql.core.Parser;
import sermed.comprobantes.App;
import sermed.comprobantes.config.Configuracion;

/**
 * Inicia AppGDI con un clúster PostgreSQL privado del usuario.
 *
 * <p>Uso: {@code InicioNativo <carpeta del programa> [--detener | --respaldar [zip]
 * | --restaurar [zip] | --verificar <carpeta temporal>]}.
 * Los datos viven en {@code %LOCALAPPDATA%\AppGDI-Dinosaurios\<variante>} (Windows) o en
 * {@code $XDG_DATA_HOME/AppGDI-Dinosaurios/<variante>} (Linux), nunca en la carpeta
 * del programa. El servidor escucha solo en 127.0.0.1, en un puerto libre que elige
 * el sistema operativo, de modo que no choca con otro PostgreSQL (por ejemplo, en 5432).
 */
public final class InicioNativo {
    private static final String BASE = "registro_comprobantes";
    private static final String USUARIO = "gdi_app";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String EXE = WINDOWS ? ".exe" : "";
    private static final long MB = 1024L * 1024L;
    /** Espacio libre que exige la primera ejecución: clúster cargado (~70 MB) y margen para WAL. */
    static final long ESPACIO_PRIMERA_VEZ = 300 * MB;
    /** Espacio libre que exigen las ejecuciones siguientes para que PostgreSQL pueda escribir. */
    static final long ESPACIO_MINIMO = 100 * MB;
    private static final int INTENTOS_PUERTO = 3;

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
        String orden = args.length > 1 ? args[1] : "";
        boolean verificar = args.length == 3 && orden.equals("--verificar");
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
            Path archivo = args.length == 3 && !verificar ? Path.of(args[2]) : null;
            switch (orden) {
                case "--detener" -> inicio.detenerSolo();
                case "--respaldar" -> inicio.respaldarDesdeOrden(archivo);
                case "--restaurar" -> inicio.restaurarDesdeOrden(archivo);
                case "", "--verificar" -> inicio.abrir();
                default -> throw new IOException("Orden desconocida: " + orden);
            }
        } catch (Exception error) {
            error.printStackTrace();
            if (!verificar && !GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null,
                        envolver("No se pudo completar la operación de AppGDI. " + error.getMessage()
                                + "\nRegistro: " + carpeta.resolve("arranque.log")),
                        "AppGDI Dinosaurios", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }

    static Path carpetaUsuario(String modo) {
        if (WINDOWS) {
            String local = System.getenv("LOCALAPPDATA");
            Path raiz = local == null || local.isBlank()
                    ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
            return raiz.resolve("AppGDI-Dinosaurios").resolve(modo).toAbsolutePath();
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        Path raiz = xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share") : Path.of(xdg);
        return raiz.resolve("AppGDI-Dinosaurios").resolve(modo).toAbsolutePath();
    }

    /** Bloquea la carpeta de datos para este proceso; falla si AppGDI ya está abierta. */
    private void bloquear(boolean detenerAlSalir) throws IOException {
        Files.createDirectories(carpeta);
        if (!WINDOWS) {
            Files.setPosixFilePermissions(carpeta, PosixFilePermissions.fromString("rwx------"));
        }
        FileChannel canal = FileChannel.open(carpeta.resolve("inicio.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock bloqueo = canal.tryLock();
        if (bloqueo == null) {
            canal.close();
            throw new IOException("AppGDI ya está abierta. Ciérrala e inténtalo de nuevo.");
        }
        // El sistema operativo libera el bloqueo si el proceso termina de golpe,
        // así que un cierre inesperado no deja un bloqueo vencido.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (detenerAlSalir) {
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
    }

    private void detenerSolo() throws Exception {
        if (!Files.exists(datos.resolve("PG_VERSION"))) {
            return;
        }
        bloquear(false);
        detener();
    }

    private void abrir() throws Exception {
        bloquear(embebido || verificar);
        boolean primeraVez = !Files.isRegularFile(datos.resolve("PG_VERSION"))
                || !Files.exists(carpeta.resolve("base-lista"));
        comprobarEspacio(primeraVez ? ESPACIO_PRIMERA_VEZ : ESPACIO_MINIMO, primeraVez);
        JWindow aviso = primeraVez && !verificar ? mostrarAviso() : null;
        try {
            Properties claves = cargarOCrearClaves();
            String claveAdmin = claves.getProperty("admin.clave");
            if (!Files.isRegularFile(datos.resolve("PG_VERSION"))) {
                inicializar(claveAdmin);
            }
            int puerto = iniciarServidor(claves);
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
                probar(puerto, claves);
                return;
            }
        } finally {
            if (aviso != null) {
                aviso.dispose();
            }
        }
        App.main(new String[0]);
    }

    /** Arranca el clúster (o reutiliza el de la variante Headless) y devuelve su puerto. */
    private int iniciarServidor(Properties claves) throws Exception {
        String claveAdmin = claves.getProperty("admin.clave");
        if (!embebido && estaActivo()) {
            int puerto = Integer.parseInt(claves.getProperty("db.puerto"));
            try (Connection conexion = conectar(puerto, "postgres", "postgres", claveAdmin)) {
                if (conexion.isValid(5)) {
                    return puerto;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        detener();
        IOException ultimo = null;
        for (int intento = 1; intento <= INTENTOS_PUERTO; intento++) {
            // El puerto queda libre entre elegirlo y que PostgreSQL lo abra; si otro
            // programa lo toma en ese instante, se reintenta con otro puerto.
            int puerto = elegirPuerto();
            claves.setProperty("db.puerto", Integer.toString(puerto));
            guardar(claves, config);
            int estado = pg("pg_ctl", "-D", datos.toString(), "-w", "-t", "60",
                    "-l", carpeta.resolve("postgresql.log").toString(), "-o",
                    "-h 127.0.0.1 -p " + puerto + " -c unix_socket_directories= -c jit=off", "start");
            if (estado == 0) {
                return puerto;
            }
            ultimo = new IOException("PostgreSQL no arrancó (pg_ctl terminó con código " + estado
                    + "). Revisa " + carpeta.resolve("postgresql.log") + ".");
            detener();
        }
        throw ultimo;
    }

    /** Falla con un mensaje claro si la unidad de los datos no tiene el espacio indicado. */
    void comprobarEspacio(long requerido, boolean primeraVez) throws IOException {
        long libre = Files.getFileStore(carpeta).getUsableSpace();
        if (libre < requerido) {
            throw new IOException(String.format(Locale.ROOT,
                    "No hay espacio suficiente en el disco de %s: hay %d MB libres y %s necesita %d MB. "
                            + "Libera espacio y vuelve a abrir AppGDI.",
                    carpeta, libre / MB,
                    primeraVez ? "la primera ejecución (crear y cargar la base)" : "AppGDI",
                    requerido / MB));
        }
    }

    private static JWindow mostrarAviso() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        JWindow ventana = new JWindow();
        JLabel texto = new JLabel("<html><div style='padding:16px'>Preparando la base de datos de AppGDI "
                + "por primera vez.<br>Esto puede tardar hasta un minuto.</div></html>", SwingConstants.CENTER);
        ventana.add(texto);
        ventana.pack();
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);
        return ventana;
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
        if (GraphicsEnvironment.isHeadless()) {
            return generada;
        }
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
            // initdb escribe la ruta de su carpeta share dentro de un COPY, en la página
            // de códigos ANSI de Windows. Con un clúster UTF8, una ruta como
            // C:\Users\José\... rompe initdb ("invalid byte sequence for encoding UTF8").
            // SQL_ASCII acepta esos bytes; solo afecta a las bases postgres y template1,
            // porque registro_comprobantes se crea en UTF8 desde template0.
            ejecutarPg("initdb", "-D", datos.toString(), "-U", "postgres",
                    "--encoding=SQL_ASCII", "--locale=C", "--auth=scram-sha-256",
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
        // Un PostgreSQL ya instalado puede dejar PGDATA, PGPORT, PGOPTIONS, etc.;
        // el clúster privado no debe heredarlos.
        proceso.environment().keySet().removeIf(nombre -> nombre.toUpperCase(Locale.ROOT).startsWith("PG"));
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
        if (!estaActivo()) {
            return;
        }
        // Tras un apagado brusco, postmaster.pid puede nombrar un PID que ahora usa
        // otro proceso: pg_ctl lo cree activo, pero detenerlo fallaría (o, en Linux,
        // enviaría la señal a ese otro proceso). El servidor abre su puerto antes de
        // quedar listo, así que si nadie atiende en el puerto anotado el archivo está
        // vencido y se elimina sin enviar ninguna señal.
        Path pid = datos.resolve("postmaster.pid");
        if (Files.exists(pid) && !atiende(pid)) {
            Files.delete(pid);
            return;
        }
        int estado = pg("pg_ctl", "-D", datos.toString(), "-w", "-t", "20", "-m", "fast", "stop");
        if (estado != 0) {
            throw new IOException("No se pudo detener PostgreSQL (pg_ctl terminó con código " + estado + ").");
        }
    }

    /**
     * Indica si alguien atiende en el puerto anotado en postmaster.pid. Ante cualquier
     * duda (archivo ilegible o con otro formato) responde que sí, para no borrar
     * nunca el archivo de un servidor vivo; solo una conexión rechazada cuenta como vencido.
     */
    private static boolean atiende(Path pid) {
        int puerto;
        try {
            // En Windows, la línea de la carpeta de datos va en la página de códigos ANSI
            // (C:\Users\José...), no en UTF-8; ISO-8859-1 lee cualquier byte sin fallar.
            List<String> lineas = Files.readAllLines(pid, java.nio.charset.StandardCharsets.ISO_8859_1);
            puerto = Integer.parseInt(lineas.get(3).strip());
        } catch (Exception formatoDesconocido) {
            return true;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), puerto), 2000);
            return true;
        } catch (IOException rechazada) {
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // Respaldo y restauración en frío: con el servidor detenido, se copia la
    // carpeta del clúster y config.ini. No necesita pg_dump, que no se incluye.
    // ----------------------------------------------------------------------

    private void respaldarDesdeOrden(Path archivo) throws Exception {
        if (!Files.isRegularFile(datos.resolve("PG_VERSION"))) {
            throw new IOException("Todavía no hay una base que respaldar. Abre AppGDI una vez.");
        }
        if (archivo == null) {
            archivo = elegirArchivo(true);
            if (archivo == null) {
                return;
            }
        }
        bloquear(false);
        detener();
        respaldar(archivo);
        informar("Respaldo guardado en\n" + archivo
                + "\n\nContiene las contraseñas de la base: guárdalo en un lugar seguro.");
    }

    private void restaurarDesdeOrden(Path archivo) throws Exception {
        if (archivo == null) {
            archivo = elegirArchivo(false);
            if (archivo == null) {
                return;
            }
            int decision = JOptionPane.showConfirmDialog(null,
                    "Se reemplazarán los datos actuales de AppGDI por los del respaldo.\n"
                            + "Los datos actuales se conservarán en una carpeta aparte. ¿Continuar?",
                    "Restaurar AppGDI", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (decision != JOptionPane.OK_OPTION) {
                return;
            }
        }
        bloquear(false);
        detener();
        Path anterior = restaurar(archivo);
        informar("Respaldo restaurado. Abre AppGDI para usarlo."
                + (anterior == null ? "" : "\nLos datos anteriores quedaron en " + anterior));
    }

    private static Path elegirArchivo(boolean guardar) {
        var selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Respaldo de AppGDI (*.zip)", "zip"));
        if (guardar) {
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
            selector.setSelectedFile(Path.of(System.getProperty("user.home"),
                    "AppGDI-respaldo-" + fecha + ".zip").toFile());
        }
        int respuesta = guardar ? selector.showSaveDialog(null) : selector.showOpenDialog(null);
        return respuesta == JFileChooser.APPROVE_OPTION ? selector.getSelectedFile().toPath() : null;
    }

    private void informar(String mensaje) {
        System.out.println(mensaje);
        if (!verificar && !GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, envolver(mensaje), "AppGDI Dinosaurios",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Texto de diálogo con saltos de línea automáticos: las rutas largas no ensanchan la ventana. */
    static JTextArea envolver(String texto) {
        JTextArea area = new JTextArea(texto);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        area.setSize(new java.awt.Dimension(480, Short.MAX_VALUE));
        area.setSize(area.getPreferredSize());
        return area;
    }

    /** Escribe en un .zip el clúster detenido y config.ini. */
    void respaldar(Path archivo) throws IOException {
        Path destino = archivo.toAbsolutePath();
        Files.createDirectories(destino.getParent());
        Path temporal = Files.createTempFile(destino.getParent(), "appgdi-respaldo-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporal));
                 Stream<Path> archivos = Files.walk(datos)) {
                agregar(zip, config, "config.ini");
                for (Path ruta : (Iterable<Path>) archivos::iterator) {
                    String nombre = ruta.getFileName().toString();
                    if (Files.isRegularFile(ruta) && !nombre.equals("postmaster.pid")) {
                        agregar(zip, ruta, "postgresql/" + datos.relativize(ruta).toString().replace('\\', '/'));
                    } else if (Files.isDirectory(ruta) && !ruta.equals(datos)) {
                        // PostgreSQL exige que existan carpetas aunque estén vacías (pg_notify, pg_twophase…).
                        zip.putNextEntry(new ZipEntry("postgresql/"
                                + datos.relativize(ruta).toString().replace('\\', '/') + "/"));
                        zip.closeEntry();
                    }
                }
            }
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static void agregar(ZipOutputStream zip, Path origen, String nombre) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        Files.copy(origen, zip);
        zip.closeEntry();
    }

    /**
     * Reemplaza el clúster y config.ini por los del respaldo. Devuelve la carpeta
     * donde quedaron los datos anteriores, o null si no había.
     */
    Path restaurar(Path archivo) throws IOException {
        Path extraido = Files.createTempDirectory(carpeta, "restaurar-");
        try {
            try (var zip = new ZipInputStream(Files.newInputStream(archivo))) {
                for (ZipEntry entrada; (entrada = zip.getNextEntry()) != null; ) {
                    Path destino = extraido.resolve(entrada.getName()).normalize();
                    if (!destino.startsWith(extraido)) {
                        throw new IOException("El respaldo contiene rutas no válidas.");
                    }
                    if (entrada.isDirectory()) {
                        Files.createDirectories(destino);
                    } else {
                        Files.createDirectories(destino.getParent());
                        Files.copy(zip, destino);
                    }
                }
            }
            Path version = extraido.resolve("postgresql/PG_VERSION");
            if (!Files.isRegularFile(version) || !Files.readString(version).strip().equals("17")
                    || !Files.isRegularFile(extraido.resolve("config.ini"))) {
                throw new IOException("El archivo no es un respaldo de AppGDI con PostgreSQL 17.");
            }
            Path anterior = null;
            if (Files.exists(datos) || Files.exists(config)) {
                String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                anterior = carpeta.resolve("antes-de-restaurar-" + fecha);
                Files.createDirectories(anterior);
                if (Files.exists(datos)) {
                    mover(datos, anterior.resolve("postgresql"));
                }
                if (Files.exists(config)) {
                    mover(config, anterior.resolve("config.ini"));
                }
            }
            mover(extraido.resolve("postgresql"), datos);
            mover(extraido.resolve("config.ini"), config);
            if (!WINDOWS) {
                Files.setPosixFilePermissions(datos, PosixFilePermissions.fromString("rwx------"));
            }
            if (!Files.exists(carpeta.resolve("base-lista"))) {
                Files.createFile(carpeta.resolve("base-lista"));
            }
            return anterior;
        } finally {
            borrar(extraido);
        }
    }

    /**
     * Mueve un archivo o carpeta. En Windows, los procesos de PostgreSQL tardan un
     * momento en soltar sus archivos (y su carpeta de trabajo) después de que
     * pg_ctl informa que se detuvieron; mientras tanto el cambio de nombre falla
     * con "Sharing violation", así que se reintenta durante unos segundos.
     */
    private static void mover(Path origen, Path destino) throws IOException {
        for (int intento = 1; ; intento++) {
            try {
                Files.move(origen, destino);
                return;
            } catch (java.nio.file.FileSystemException ocupado) {
                if (intento == 20 || ocupado instanceof java.nio.file.FileAlreadyExistsException) {
                    throw ocupado;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ocupado;
                }
            }
        }
    }

    private static void borrar(Path raiz) throws IOException {
        if (!Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(ruta);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Verificación (instaladores en CI y pruebas manuales)
    // ----------------------------------------------------------------------

    private void probar(int puerto, Properties claves) throws Exception {
        String claveAdmin = claves.getProperty("admin.clave");
        try (Connection conexion = conectar(puerto, BASE, USUARIO, claves.getProperty("db.clave"));
             Statement sentencia = conexion.createStatement();
             ResultSet fila = sentencia.executeQuery("SELECT pg_encoding_to_char(encoding) FROM pg_database"
                     + " WHERE datname = current_database()")) {
            fila.next();
            if (!fila.getString(1).equals("UTF8")) {
                throw new IOException("registro_comprobantes no está en UTF8: " + fila.getString(1));
            }
            System.out.println("[PASA]  registro_comprobantes usa UTF8 (programa en " + programa + ")");
        }
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
        probarRespaldo(puerto, claves);
    }

    /** Respalda, restaura y comprueba que gdi_app ve los mismos datos y conserva sus límites. */
    private void probarRespaldo(int puerto, Properties claves) throws Exception {
        String antes = huella(puerto, claves.getProperty("db.clave"));
        Path zip = carpeta.resolve("respaldo prueba ñ.zip");
        detener();
        respaldar(zip);
        restaurar(zip);
        Properties restauradas = cargarOCrearClaves();
        int nuevo = iniciarServidor(restauradas);
        String despues = huella(nuevo, restauradas.getProperty("db.clave"));
        if (!antes.equals(despues)) {
            throw new IOException("El respaldo restaurado no coincide: " + antes + " / " + despues);
        }
        try (Connection conexion = conectar(nuevo, BASE, USUARIO, restauradas.getProperty("db.clave"));
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DELETE FROM comprobante");
            throw new IOException("Tras restaurar, gdi_app pudo borrar directamente en comprobante.");
        } catch (SQLException esperado) {
            if (!"42501".equals(esperado.getSQLState())) {
                throw esperado;
            }
        }
        System.out.println("[PASA]  Respaldo y restauración en frío: " + despues
                + "; gdi_app sigue sin permiso de escritura directa");
    }

    private static String huella(int puerto, String clave) throws Exception {
        try (Connection conexion = conectar(puerto, BASE, USUARIO, clave);
             Statement sentencia = conexion.createStatement();
             ResultSet fila = sentencia.executeQuery("SELECT (SELECT count(*) FROM comprobante) || ' comprobantes, '"
                     + " || (SELECT count(*) FROM cliente) || ' clientes'")) {
            fila.next();
            return fila.getString(1);
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
