package pe.edu.ucsm.gdi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** CALL y consumo de cursor en la misma transacción; no conserva conexiones abiertas. */
final class Database {
    private final DbConfig config;
    Database(DbConfig config) { this.config = config; }

    private Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", config.user()); p.setProperty("password", config.password());
        p.setProperty("connectTimeout", "5"); p.setProperty("socketTimeout", "30");
        p.setProperty("ApplicationName", "AppGDI_Dinosaurios");
        p.setProperty("options", "-c statement_timeout=15000 -c lock_timeout=5000");
        return DriverManager.getConnection(config.url(), p);
    }

    TableData query(String procedure, Object... args) throws SQLException {
        List<Object> parameters = new ArrayList<>(java.util.Arrays.asList(args));
        parameters.add("gdi_resultado");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                invoke(c, procedure, parameters);
                TableData data;
                try (PreparedStatement ps = c.prepareStatement("FETCH ALL FROM gdi_resultado");
                     ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    List<String> columns = new ArrayList<>(); List<List<Object>> rows = new ArrayList<>();
                    for (int i=1; i<=meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i));
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i=1; i<=meta.getColumnCount(); i++) row.add(rs.getObject(i));
                        rows.add(row);
                    }
                    data = new TableData(columns, rows);
                }
                c.commit(); return data;
            } catch (SQLException e) { rollback(c,e); throw e; }
        }
    }

    void execute(String procedure, Object... args) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try { invoke(c, procedure, java.util.Arrays.asList(args)); c.commit(); }
            catch (SQLException e) { rollback(c,e); throw e; }
        }
    }

    private static void rollback(Connection c, SQLException original) {
        try { c.rollback(); } catch (SQLException e) { original.addSuppressed(e); }
    }

    private static void invoke(Connection c, String procedure, List<Object> args) throws SQLException {
        if (!procedure.matches("app\\.sp_[a-z_]+")) throw new IllegalArgumentException("Procedimiento inválido");
        String placeholders = String.join(",", Collections.nCopies(args.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("CALL " + procedure + "(" + placeholders + ")")) {
            for (int i=0; i<args.size(); i++) {
                Object value=args.get(i);
                if (value instanceof String s) ps.setObject(i+1,s,java.sql.Types.OTHER);
                else ps.setObject(i+1,value);
            }
            ps.execute();
        }
    }
}
