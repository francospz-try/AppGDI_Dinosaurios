package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.datos.ProductoDAO;
import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.ComprobanteResumen;
import sermed.comprobantes.modelo.ProductoServicio;

/**
 * Pruebas de las funciones de consulta del hito del 30 %
 * ({@code scripts/04_procedimientos.sql}) contra una base recién creada con
 * el script completo. Usan los tres comprobantes analizados, cuyos valores
 * impresos se conocen.
 */
public final class PruebasHito30 {

    private PruebasHito30() { }

    /**
     * @param args no se usan
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        ConexionBD bd = new ConexionBD(Configuracion.cargar());
        CatalogoDAO catalogo = new CatalogoDAO(bd);
        ClienteDAO clientes = new ClienteDAO(bd);
        ProductoDAO productos = new ProductoDAO(bd);
        ComprobanteDAO comprobantes = new ComprobanteDAO(bd);
        Verificador v = new Verificador();

        v.caso("F10 Lista las series y monedas", () -> {
            int series = catalogo.series().size();
            int monedas = catalogo.monedas().size();
            comprobar(series == 3 && monedas == 1, series + " series, " + monedas + " monedas");
            return catalogo.series() + "; " + catalogo.monedas();
        });

        v.caso("F6 Busca clientes por número de documento", () -> {
            List<Cliente> r = clientes.buscar("20900000000", 10);
            comprobar(r.size() == 1, "se esperaba 1 cliente y hubo " + r.size());
            return r.get(0).nombreRazonSocial() + ", " + r.get(0).comprobantes() + " comprobante(s)";
        });

        v.caso("F6 Busca clientes por parte del nombre, sin distinguir mayúsculas", () -> {
            List<Cliente> r = clientes.buscar("clínica san josé", 50);
            comprobar(!r.isEmpty(), "no hubo resultados");
            comprobar(r.stream().allMatch(c -> c.nombreRazonSocial().contains("CLÍNICA SAN JOSÉ")),
                    "resultado que no coincide");
            return r.size() + " cliente(s)";
        });

        v.caso("F6 Busca clientes sin tildes ni mayúsculas, en cualquier configuración regional", () -> {
            List<Cliente> r = clientes.buscar("clinica san jose", 50);
            List<Cliente> n = clientes.buscar("ÑANDÚ", 50);
            comprobar(!r.isEmpty() && r.stream().allMatch(c -> c.nombreRazonSocial()
                    .contains("CLÍNICA SAN JOSÉ")), "«clinica san jose»: " + r.size());
            comprobar(n.isEmpty(), "«ÑANDÚ» no debía encontrar clientes");
            return "«clinica san jose» encuentra " + r.size() + " clientes «CLÍNICA SAN JOSÉ»";
        });

        v.caso("F7 Busca productos sin tildes: «termometro» encuentra los termómetros", () -> {
            List<ProductoServicio> r = productos.buscar("termometro", 20);
            comprobar(r.size() >= 2 && r.stream().allMatch(p -> p.descripcion().contains("TERMÓMETRO")
                    || p.descripcion().contains("TERMOMETRO")), r.size() + " resultados");
            return r.size() + " productos o servicios";
        });

        v.caso("F7 Busca productos por código y por descripción", () -> {
            List<ProductoServicio> porCodigo = productos.buscar("349", 10);
            List<ProductoServicio> porTexto = productos.buscar("termohigr", 10);
            comprobar(porCodigo.size() == 1 && porCodigo.get(0).unidadMedida().equals("UND"),
                    "producto 349 no encontrado");
            comprobar(porTexto.size() >= 2, "se esperaban al menos 2 termohigrómetros");
            return "349: " + porCodigo.get(0).descripcion() + "; «termohigr»: "
                    + porTexto.size() + " resultado(s)";
        });

        v.caso("F8 Lista los comprobantes de un periodo (C01)", () -> {
            List<ComprobanteResumen> r = comprobantes.porPeriodo(
                    LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27));
            comprobar(r.size() == 8, "se esperaban 8 y hubo " + r.size());
            ComprobanteResumen ultimo = r.get(r.size() - 1);
            comprobar(ultimo.numeracion().equals("F001-005206"), "último: " + ultimo.numeracion());
            return r.size() + " comprobantes; el último es " + ultimo.numeracion() + " por "
                    + ultimo.importeTotal();
        });

        v.caso("F9 Detalle de F001-005206: cliente, línea y guía de remisión", () -> {
            Comprobante c = comprobantes.buscar("F001", 5206).orElseThrow();
            comprobar(c.importes().importeTotal().compareTo(new BigDecimal("300.00")) == 0, "total");
            comprobar(c.importes().igv().compareTo(new BigDecimal("45.76")) == 0, "IGV");
            comprobar(c.cliente() != null && c.cliente().tipoDocumento().equals("RUC"), "cliente");
            comprobar(c.lineas().size() == 1 && c.lineas().get(0).codigo().equals("349"), "línea");
            comprobar(c.referencias().size() == 1
                    && c.referencias().get(0).numeroRelacionado().equals("T001-003115"), "guía");
            return c.tipoComprobante() + " " + c.numeracion() + ", " + c.formaPago() + ", "
                    + c.lineas().get(0).cantidad() + " × " + c.lineas().get(0).precioUnitario();
        });

        v.caso("F9 Detalle de EB01-50: sin cliente y con siete decimales exactos", () -> {
            Comprobante c = comprobantes.buscar("EB01", 50).orElseThrow();
            comprobar(c.cliente() == null, "EB01-50 no debe tener cliente");
            BigDecimal importe = c.lineas().get(0).importeVenta();
            comprobar(importe.compareTo(new BigDecimal("119.9999997")) == 0, "importe " + importe);
            comprobar(c.lineas().get(0).precioUnitario() == null, "EB01 no imprime precio unitario");
            return "importe de venta " + importe.toPlainString() + "; precio unitario nulo";
        });

        v.caso("F9 Comprobante inexistente: resultado vacío, sin error", () -> {
            comprobar(comprobantes.buscar("F001", 99999999).isEmpty(), "no debía existir");
            return "F001-99999999 no existe";
        });

        v.caso("Borrado lógico: las funciones omiten las filas marcadas (con ROLLBACK)", () -> {
            try (Connection cn = bd.abrir()) {
                cn.setAutoCommit(false);
                try (Statement st = cn.createStatement()) {
                    // Se marcan con los procedimientos, como lo hace el aplicativo;
                    // el rol del aplicativo no puede escribir directo en las tablas
                    st.execute("CALL sp_eliminar_comprobante('F001', 5206)");
                    st.execute("CALL sp_eliminar_cliente('RUC', '20900000000')");
                    st.execute("CALL sp_eliminar_producto('349')");
                    long cab = contar(cn, "SELECT count(*) FROM fn_comprobante_cabecera('F001', 5206)");
                    long per = contar(cn, "SELECT count(*) FROM fn_comprobantes_periodo('2026-08-25',"
                            + " '2026-08-27')");
                    long cli = contar(cn, "SELECT count(*) FROM fn_buscar_clientes('20900000000', 10)");
                    long pro = contar(cn, "SELECT count(*) FROM fn_buscar_productos('349', 10)");
                    long fila = contar(cn, "SELECT count(*) FROM comprobante"
                            + " WHERE serie = 'F001' AND numero = 5206 AND eliminado");
                    comprobar(cab == 0 && per == 7 && cli == 0 && pro == 0,
                            "cabecera " + cab + ", periodo " + per + ", cliente " + cli
                            + ", producto " + pro);
                    comprobar(fila == 1, "la fila debía seguir en la tabla, marcada");
                    return "con F001-005206, su cliente y el producto 349 marcados: el detalle y las"
                            + " búsquedas no los devuelven, el periodo muestra 7 de 8 comprobantes"
                            + " y la fila sigue en la tabla";
                } finally {
                    cn.rollback();
                }
            }
        });

        v.terminar();
    }

    private static long contar(Connection cn, String sql) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
