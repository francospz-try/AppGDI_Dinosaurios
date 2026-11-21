package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.AccesoDatosException;
import sermed.comprobantes.datos.ConexionBD;

/**
 * Prueba de conexión: comprueba que el aplicativo se conecta con la
 * configuración real y con el rol del aplicativo, sin privilegios de
 * administrador ni permiso para escribir directo en las tablas; que
 * consulta tablas reales y, ante una contraseña errónea, un servidor
 * apagado o una base inexistente, informa el problema con un mensaje claro.
 *
 * <p>Ejecución: {@code java -cp registro-comprobantes.jar
 * sermed.comprobantes.pruebas.PruebaConexion}</p>
 */
public final class PruebaConexion {

    private PruebaConexion() { }

    /**
     * @param args no se usan
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        Configuracion config = Configuracion.cargar();
        System.out.println("Configuración: " + config + " (archivo "
                + System.getProperty(Configuracion.PROPIEDAD_RUTA,
                        Configuracion.RUTA_POR_DEFECTO.toString()) + ")");
        System.out.println();
        ConexionBD bd = new ConexionBD(config);
        Verificador v = new Verificador();

        v.caso("Conecta con la configuración del archivo", () -> {
            ConexionBD.InfoConexion info = bd.probar();
            comprobar(info.base().equals(config.base()), "base inesperada: " + info.base());
            return info.version() + "; base " + info.base() + "; usuario " + info.usuario()
                    + "; " + info.milisegundos() + " ms";
        });

        v.caso("Consulta las tablas reales del esquema", () -> {
            String sql = "SELECT (SELECT count(*) FROM comprobante), (SELECT count(*) FROM incluye),"
                    + " (SELECT count(*) FROM cliente), (SELECT count(*) FROM producto_servicio)";
            try (Connection cn = bd.abrir();
                 PreparedStatement ps = cn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                comprobar(rs.getLong(1) > 0, "la tabla comprobante está vacía");
                return rs.getLong(1) + " comprobantes, " + rs.getLong(2) + " líneas, "
                        + rs.getLong(3) + " clientes, " + rs.getLong(4) + " productos o servicios";
            }
        });

        v.caso("Lee un comprobante analizado mediante una función de la base", () -> {
            String sql = "SELECT importe_total, nombre_moneda FROM fn_comprobante_cabecera('F001', 5206)";
            return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS,
                    rs -> "F001-005206: " + rs.getBigDecimal(1) + " " + rs.getString(2))
                    .orElseThrow(() -> new AssertionError("no se encontró F001-005206"));
        });

        v.caso("Cierra la conexión después de usarla", () -> {
            Connection cn;
            try (Connection abierta = bd.abrir()) {
                cn = abierta;
                comprobar(!cn.isClosed(), "la conexión no se abrió");
            }
            comprobar(cn.isClosed(), "la conexión sigue abierta");
            return "isClosed() = true tras el bloque try-with-resources";
        });

        v.caso("El aplicativo se conecta con un rol sin privilegios de administrador", () -> {
            String sql = "SELECT current_user, rolsuper, rolcreatedb FROM pg_roles"
                    + " WHERE rolname = current_user";
            return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS, rs -> {
                comprobar(!rs.getBoolean(2) && !rs.getBoolean(3),
                        rs.getString(1) + " es superusuario o puede crear bases");
                return "rol " + rs.getString(1) + ": no es superusuario ni crea bases de datos";
            }).orElseThrow();
        });

        v.caso("El rol no puede escribir directo en las tablas: debe usar los procedimientos", () -> {
            try {
                bd.ejecutar("UPDATE comprobante SET observacion = 'X' WHERE serie = 'F001'"
                        + " AND numero = 5206", ConexionBD.SIN_PARAMETROS);
            } catch (AccesoDatosException e) {
                comprobar(e.tipo() == AccesoDatosException.Tipo.PERMISO, "tipo " + e.tipo());
                return "Mensaje: «" + e.getMessage() + "»";
            }
            throw new AssertionError("el rol pudo modificar la tabla directamente");
        });

        v.caso("Contraseña errónea: mensaje claro", () ->
                esperarError(new ConexionBD(config.conClave("clave-incorrecta")),
                        "contraseña incorrectos"));

        v.caso("Servidor apagado o puerto incorrecto: mensaje claro", () ->
                esperarError(new ConexionBD(config.conPuerto(config.puerto() + 1)),
                        "No se pudo conectar con el servidor"));

        v.caso("Base de datos inexistente: mensaje claro", () ->
                esperarError(new ConexionBD(config.conBase("base_inexistente")),
                        "no existe"));

        v.terminar();
    }

    private static String esperarError(ConexionBD bd, String fragmento) {
        try {
            bd.probar();
        } catch (AccesoDatosException e) {
            comprobar(e.getMessage().contains(fragmento),
                    "mensaje inesperado: " + e.getMessage());
            return "Mensaje: «" + e.getMessage() + "»";
        }
        throw new AssertionError("se conectó cuando debía fallar");
    }
}
