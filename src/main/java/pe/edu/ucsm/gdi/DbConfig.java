package pe.edu.ucsm.gdi;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuración externa; la contraseña nunca forma parte de mensajes o logs. */
public record DbConfig(String host, int port, String name, String user, String password) {
    public DbConfig {
        if (host == null || !host.matches("[a-zA-Z0-9.-]+") || port < 1 || port > 65535
                || name == null || !name.matches("[a-zA-Z0-9_]+") || user == null || user.isBlank()) {
            throw new IllegalArgumentException("Revise servidor, puerto, base de datos y usuario.");
        }
        if (password == null) password = "";
    }

    public static DbConfig load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        return new DbConfig(p.getProperty("db.host", "127.0.0.1"),
                Integer.parseInt(p.getProperty("db.port", "5432")),
                p.getProperty("db.name", "registro_comprobantes"),
                p.getProperty("db.user", "gdi_app"), p.getProperty("db.password", ""));
    }

    public String url() { return "jdbc:postgresql://" + host + ":" + port + "/" + name; }

    @Override public String toString() { return user + "@" + host + ":" + port + "/" + name; }
}
