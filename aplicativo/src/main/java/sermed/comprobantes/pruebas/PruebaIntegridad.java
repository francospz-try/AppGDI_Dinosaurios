package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.ConexionBD;

/**
 * Pruebas de integridad por debajo del aplicativo: con SQL directo, como
 * administrador, comprueba que los disparadores protegen el registro aunque
 * nadie use la interfaz ni los procedimientos. Recibe la ruta de un archivo
 * de configuración con un usuario administrador (el rol del aplicativo no
 * puede escribir directo en las tablas). Se ejecuta sobre una base recién
 * creada con el script completo; no deja cambios, salvo un comprobante de
 * prueba con importe cero.
 */
public final class PruebaIntegridad {

    private PruebaIntegridad() { }

    /**
     * @param args ruta del archivo de configuración de un administrador
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Uso: PruebaIntegridad <configuración de un administrador>");
            System.exit(2);
        }
        ConexionBD bd = new ConexionBD(Configuracion.cargar(Path.of(args[0])));
        Verificador v = new Verificador();

        v.caso("I1 Borrar la última línea con SQL directo: se rechaza al confirmar", () -> {
            try (Connection cn = bd.abrir(); Statement st = cn.createStatement()) {
                cn.setAutoCommit(false);
                st.executeUpdate("DELETE FROM incluye WHERE serie = 'EB01' AND numero = 50");
                String m = rechazoAlConfirmar(cn);
                comprobar(contar(bd, "SELECT count(*) FROM incluye WHERE serie = 'EB01'"
                        + " AND numero = 50") == 1, "se perdió la línea");
                return m;
            }
        });

        v.caso("I2 Cambiar el total con SQL directo sin cambiar los importes: rechazado", () -> {
            try (Connection cn = bd.abrir(); Statement st = cn.createStatement()) {
                cn.setAutoCommit(false);
                try {
                    st.executeUpdate("UPDATE comprobante SET importe_total = 0"
                            + " WHERE serie = 'F001' AND numero = 5206");
                } catch (SQLException e) {
                    cn.rollback();
                    comprobar("23514".equals(e.getSQLState()), "SQLSTATE " + e.getSQLState());
                    return e.getMessage().lines().findFirst().orElse("");
                }
                cn.rollback();
                throw new AssertionError("se aceptó un total que no cuadra");
            }
        });

        v.caso("I3 Dos transacciones borran a la vez una línea cada una: no queda sin líneas", () -> {
            // Comprobante de importe cero con dos líneas de importe cero: borrar
            // cualquiera de ellas no altera la suma, y por separado deja una
            try (Connection cn = bd.abrir(); Statement st = cn.createStatement()) {
                st.execute("CALL sp_insertar_comprobante('F001', 99000001, DATE '2026-11-21',"
                        + " NULL, 'PEN', 'CONTADO', NULL, 'Prueba de integridad', 0, 0, 0, 0,"
                        + " NULL, 0, NULL, NULL, NULL, 0, 0, NULL, NULL, NULL, NULL,"
                        + " '[{\"codigo\":\"349\",\"cantidad\":1,\"valor_unitario\":0,"
                        + "\"precio_unitario\":0,\"importe_venta\":0},"
                        + "{\"codigo\":\"350\",\"cantidad\":1,\"valor_unitario\":0,"
                        + "\"precio_unitario\":0,\"importe_venta\":0}]', '[]')");
            }
            try (Connection a = bd.abrir(); Connection b = bd.abrir();
                 Statement sa = a.createStatement(); Statement sb = b.createStatement()) {
                a.setAutoCommit(false);
                b.setAutoCommit(false);
                sa.executeUpdate("DELETE FROM incluye WHERE serie = 'F001' AND numero = 99000001"
                        + " AND codigo = '349'");
                sb.executeUpdate("DELETE FROM incluye WHERE serie = 'F001' AND numero = 99000001"
                        + " AND codigo = '350'");
                a.commit();
                String m = rechazoAlConfirmar(b);
                long lineas = contar(bd, "SELECT count(*) FROM incluye WHERE serie = 'F001'"
                        + " AND numero = 99000001");
                comprobar(lineas == 1, lineas + " líneas");
                comprobar(m.contains("F001-99000001"), "numeración de más de seis cifras: " + m);
                return "la primera confirmó; la segunda: " + m;
            }
        });

        v.terminar();
    }

    /** Confirma y espera que un disparador diferido rechace la transacción. */
    private static String rechazoAlConfirmar(Connection cn) throws SQLException {
        try {
            cn.commit();
        } catch (SQLException e) {
            cn.rollback();
            comprobar("23514".equals(e.getSQLState()), "SQLSTATE " + e.getSQLState());
            return e.getMessage().lines().findFirst().orElse("");
        }
        throw new AssertionError("la transacción se confirmó");
    }

    private static long contar(ConexionBD bd, String sql) throws Exception {
        return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS, rs -> rs.getLong(1)).orElse(0L);
    }
}
