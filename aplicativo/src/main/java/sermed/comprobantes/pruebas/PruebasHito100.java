package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.AccesoDatosException;
import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.datos.Reporte;
import sermed.comprobantes.datos.ReporteDAO;
import sermed.comprobantes.datos.Tabla;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Serie;
import sermed.comprobantes.validacion.Validador;

/**
 * Pruebas del hito del 100 %: los doce reportes de
 * {@code scripts/06_reportes.sql} y el registro de series y monedas. Cada
 * reporte se ejecuta con parámetros cuyo resultado se conoce por los datos
 * de prueba (los comprobantes analizados y los eliminados por
 * {@code 07_datos_aplicativo.sql}) y se contrasta con una consulta directa
 * a las tablas. Se ejecutan sobre una base recién creada con el script
 * completo, después de las demás pruebas.
 */
public final class PruebasHito100 {

    private static final LocalDate INICIO = LocalDate.of(2022, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 12, 31);

    /** Operación que debe fallar. */
    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    private PruebasHito100() { }

    /**
     * @param args no se usan
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        ConexionBD bd = new ConexionBD(Configuracion.cargar());
        ReporteDAO reportes = new ReporteDAO(bd);
        CatalogoDAO catalogo = new CatalogoDAO(bd);
        Verificador v = new Verificador();

        System.out.println("== Definición de los reportes");
        v.caso("D1 Doce reportes, al menos 6 con JOIN, según la definición de cada función", () -> {
            int conJoin = 0;
            for (Reporte r : Reporte.values()) {
                String def = definicion(bd, r.funcion()).toUpperCase();
                comprobar(def.contains(" JOIN ") == r.conJoin(), r + ": JOIN no coincide");
                conJoin += r.conJoin() ? 1 : 0;
            }
            comprobar(Reporte.values().length == 12 && conJoin >= 6, conJoin + " con JOIN");
            return Reporte.values().length + " reportes; " + conJoin + " con JOIN";
        });
        v.caso("D2 Todos los reportes filtran los comprobantes eliminados", () -> {
            for (Reporte r : Reporte.values()) {
                String def = definicion(bd, r.funcion());
                comprobar(def.contains("NOT c.eliminado"), r + " no filtra los eliminados");
            }
            return "las 12 funciones tienen la condición NOT c.eliminado";
        });

        System.out.println("\n== Reportes");
        v.caso("R01 Comprobantes de un periodo: 8 del 25 al 27/08/2026, el último F001-005206", () -> {
            Tabla t = reportes.ejecutar(Reporte.R01,
                    lista(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27), null));
            comprobar(t.filas().size() == 8, t.filas().size() + " filas");
            comprobar(valor(t, 7, "comprobante").equals("F001-005206"), "último");
            return t.filas().size() + " comprobantes; columnas " + t.columnas();
        });
        v.caso("R01 Filtro por serie y exclusión de los eliminados", () -> {
            Tabla b = reportes.ejecutar(Reporte.R01,
                    lista(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), "B001"));
            long vigentes = contar(bd, "SELECT count(*) FROM comprobante WHERE serie = 'B001'"
                    + " AND fecha_emision >= '2026-09-01' AND NOT eliminado");
            comprobar(b.filas().size() == vigentes, b.filas().size() + " de " + vigentes);
            comprobar(b.filas().stream().allMatch(f -> f.get(0).toString().startsWith("B001")),
                    "otra serie");
            List<String> eliminados = numeraciones(bd, "eliminado");
            comprobar(b.filas().stream().noneMatch(f -> eliminados.contains(f.get(0).toString())),
                    "aparece un comprobante eliminado");
            return b.filas().size() + " boletas B001 vigentes de setiembre a noviembre; no aparece "
                    + "ninguno de los eliminados " + eliminados;
        });
        v.caso("R02 Detalle de F001-005206; un comprobante eliminado no se muestra", () -> {
            Tabla t = reportes.ejecutar(Reporte.R02, lista("F001", 5206));
            comprobar(t.filas().size() == 1 && valor(t, 0, "codigo").equals("349"), "línea");
            comprobar(importe(t, 0, "importe_venta").compareTo(new BigDecimal("300")) == 0, "importe");
            String[] eliminado = numeraciones(bd, "eliminado").get(0).split("-");
            Tabla vacia = reportes.ejecutar(Reporte.R02,
                    lista(eliminado[0], Integer.parseInt(eliminado[1])));
            comprobar(vacia.filas().isEmpty(), "muestra un eliminado");
            return "349 × 2 = 300.00; " + String.join("-", eliminado) + " (eliminado): sin filas";
        });
        v.caso("R03 Comprobantes de un cliente, con importe acumulado", () -> {
            Tabla t = reportes.ejecutar(Reporte.R03, lista("20900000001"));
            long n = contar(bd, "SELECT count(*) FROM comprobante WHERE numero_documento ="
                    + " '20900000001' AND NOT eliminado");
            BigDecimal total = new BigDecimal(contarTexto(bd, "SELECT sum(importe_total) FROM"
                    + " comprobante WHERE numero_documento = '20900000001' AND NOT eliminado"));
            comprobar(t.filas().size() == n, t.filas().size() + " de " + n);
            comprobar(importe(t, 0, "importe_acumulado").compareTo(total) == 0, "acumulado");
            return n + " comprobantes; acumulado " + total;
        });
        v.caso("R04 Documento citado: la guía T001-003115 y la cotización 00042-04-2026", () -> {
            Tabla g = reportes.ejecutar(Reporte.R04, lista("Guía de remisión", "T001-003115"));
            Tabla q = reportes.ejecutar(Reporte.R04, lista(null, "00042-04-2026"));
            Tabla prefijo = reportes.ejecutar(Reporte.R04, lista("Guía de remisión", "T001-0031"));
            comprobar(g.filas().size() == 1 && valor(g, 0, "comprobante").equals("F001-005206"), "guía");
            comprobar(q.filas().size() == 1 && valor(q, 0, "comprobante").equals("B001-000072"), "cot.");
            comprobar(prefijo.filas().size() > 1, "prefijo");
            return "T001-003115 → F001-005206; 00042-04-2026 → B001-000072; «T001-0031» → "
                    + prefijo.filas().size() + " guías";
        });
        v.caso("R05 Ventas mensuales de 2026: coinciden con los comprobantes vigentes", () -> {
            Tabla t = reportes.ejecutar(Reporte.R05, lista(2026));
            long suma = t.filas().stream().mapToLong(f -> ((Number) f.get(2)).longValue()).sum();
            long vigentes = contar(bd, "SELECT count(*) FROM comprobante WHERE NOT eliminado"
                    + " AND fecha_emision >= '2026-01-01'");
            comprobar(suma == vigentes, suma + " de " + vigentes);
            return t.filas().size() + " filas (mes y tipo); " + suma + " comprobantes vigentes";
        });
        v.caso("R06 Productos más vendidos: ordenados y sin productos dados de baja", () -> {
            Tabla t = reportes.ejecutar(Reporte.R06, lista(INICIO, FIN, 50));
            comprobar(t.filas().size() <= 50 && !t.filas().isEmpty(), "filas");
            comprobar(ordenDescendente(t, "importe_vendido"), "orden");
            comprobar(t.filas().stream().noneMatch(f -> f.get(0).equals("MS-KN95")), "MS-KN95");
            return t.filas().size() + " productos; primero " + valor(t, 0, "codigo") + " con "
                    + importe(t, 0, "importe_vendido") + " (" + importe(t, 0, "participacion_pct")
                    + " %); el descontinuado MS-KN95 no aparece";
        });
        v.caso("R07 Clientes con mayor importe: ordenados y sin clientes dados de baja", () -> {
            Tabla t = reportes.ejecutar(Reporte.R07, lista(INICIO, FIN, 1000));
            List<String> bajas = textos(bd, "SELECT tipo_documento || ' ' || numero_documento"
                    + " FROM cliente WHERE eliminado");
            comprobar(ordenDescendente(t, "importe_total"), "orden");
            comprobar(t.filas().stream().noneMatch(f -> bajas.contains(f.get(0).toString())), "baja");
            return t.filas().size() + " clientes; el primero, " + valor(t, 0, "cliente")
                    + "; no aparecen " + bajas;
        });
        v.caso("R08 Cuadre de importes: todos los comprobantes cuadran", () -> {
            Tabla todos = reportes.ejecutar(Reporte.R08, lista(INICIO, FIN, false));
            Tabla malos = reportes.ejecutar(Reporte.R08, lista(INICIO, FIN, true));
            long vigentes = contar(bd, "SELECT count(*) FROM comprobante WHERE NOT eliminado");
            comprobar(todos.filas().size() == vigentes && malos.filas().isEmpty(),
                    todos.filas().size() + " / " + malos.filas().size());
            return vigentes + " comprobantes revisados; 0 con diferencias";
        });
        v.caso("R09 Diferencia entre líneas y total: solo EB01 difiere, en menos de un céntimo", () -> {
            Tabla t = reportes.ejecutar(Reporte.R09, List.of());
            comprobar(t.filas().size() == 3, t.filas().size() + " series");
            for (int i = 0; i < 3; i++) {
                comprobar(((Number) t.filas().get(i).get(3)).longValue() == 0, "diferencia al céntimo");
            }
            return "B001, EB01 y F001; diferencia máxima de EB01: "
                    + importe(t, 1, "diferencia_maxima").toPlainString();
        });
        v.caso("R10 Evolución del precio del producto 349; un producto dado de baja no se muestra", () -> {
            Tabla t = reportes.ejecutar(Reporte.R10, lista("349"));
            Tabla baja = reportes.ejecutar(Reporte.R10, lista("MS-KN95"));
            comprobar(t.filas().size() == 5, t.filas().size() + " años");
            comprobar(importe(t, 4, "precio_maximo").compareTo(new BigDecimal("150")) == 0, "2026");
            comprobar(baja.filas().isEmpty(), "MS-KN95");
            return "2022: " + importe(t, 0, "precio_minimo") + " … 2026: " + importe(t, 4, "precio_maximo");
        });
        v.caso("R11 Huecos en la numeración: son los comprobantes eliminados", () -> {
            Tabla t = reportes.ejecutar(Reporte.R11, lista((Object) null));
            List<String> huecos = new ArrayList<>();
            for (List<Object> f : t.filas()) {
                comprobar(f.get(1).equals(f.get(2)), "hueco de más de un número");
                huecos.add(f.get(1).toString());
            }
            List<String> eliminados = numeraciones(bd, "eliminado");
            comprobar(huecos.equals(eliminados), huecos + " / " + eliminados);
            return "huecos " + huecos;
        });
        v.caso("R12 Clientes sin compras en 2026: ninguno compró en 2026", () -> {
            Tabla t = reportes.ejecutar(Reporte.R12, lista(2026));
            long conCompras = contar(bd, "SELECT count(*) FROM comprobante WHERE NOT eliminado"
                    + " AND fecha_emision >= '2026-01-01' AND tipo_documento || ' ' ||"
                    + " numero_documento IN (SELECT documento_cliente FROM"
                    + " fn_reporte_clientes_inactivos(2026))");
            comprobar(!t.filas().isEmpty() && conCompras == 0, t.filas().size() + " / " + conCompras);
            return t.filas().size() + " clientes compraron en 2025 y no en 2026";
        });
        v.caso("R13 Número de parámetros incorrecto: rechazado antes de consultar", () -> {
            try {
                reportes.ejecutar(Reporte.R01, List.of());
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            throw new AssertionError("debía rechazarse");
        });

        System.out.println("\n== Series y monedas (sp_insertar_serie, sp_insertar_moneda)");
        v.caso("S1 Registrar la serie F002: queda disponible", () -> {
            catalogo.insertarSerie(new Serie("f002", "Factura electrónica"));
            comprobar(catalogo.series().stream().anyMatch(s -> s.serie().equals("F002")), "F002");
            return catalogo.series().size() + " series: " + catalogo.series();
        });
        v.caso("S2 Registrar una serie repetida: clave duplicada", () ->
                esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "ya está registrada",
                        () -> catalogo.insertarSerie(new Serie("F002", "Factura electrónica"))));
        v.caso("S3 Serie con formato o tipo no válidos: rechazados por el aplicativo y por la base", () -> {
            List<String> e = Validador.serie(new Serie("F-2", "Nota de crédito"));
            comprobar(e.size() == 2, e.toString());
            esperar(AccesoDatosException.Tipo.VERIFICACION, "4 letras",
                    () -> catalogo.insertarSerie(new Serie("F-2", "Factura electrónica")));
            String m = esperar(AccesoDatosException.Tipo.VERIFICACION, "tipo de comprobante",
                    () -> catalogo.insertarSerie(new Serie("F003", "Nota de crédito")));
            return String.join(" | ", e) + " — base: " + m;
        });
        v.caso("M1 Registrar la moneda USD", () -> {
            catalogo.insertarMoneda(new Moneda("usd", "Dólares americanos"));
            comprobar(catalogo.monedas().stream().anyMatch(m -> m.codigo().equals("USD")
                    && m.nombre().equals("DÓLARES AMERICANOS")), "USD");
            return catalogo.monedas().toString();
        });
        v.caso("M2 Moneda con código repetido o nombre repetido: clave duplicada", () -> {
            esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "ya está registrada",
                    () -> catalogo.insertarMoneda(new Moneda("PEN", "NUEVOS SOLES")));
            return esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "con ese nombre",
                    () -> catalogo.insertarMoneda(new Moneda("SOL", "Soles")));
        });
        v.caso("M3 Código de moneda de 2 letras: rechazado por el aplicativo y por la base", () -> {
            List<String> e = Validador.moneda(new Moneda("US", ""));
            comprobar(e.size() == 2, e.toString());
            return esperar(AccesoDatosException.Tipo.VERIFICACION, "3 letras",
                    () -> catalogo.insertarMoneda(new Moneda("US", "DÓLAR")));
        });

        v.terminar();
    }

    // ------------------------------------------------------------- utilidades

    private static List<Object> lista(Object... valores) {
        return Arrays.asList(valores);
    }

    private static Object valor(Tabla t, int fila, String columna) {
        return t.filas().get(fila).get(t.columnas().indexOf(columna));
    }

    private static BigDecimal importe(Tabla t, int fila, String columna) {
        return (BigDecimal) valor(t, fila, columna);
    }

    private static boolean ordenDescendente(Tabla t, String columna) {
        for (int i = 1; i < t.filas().size(); i++) {
            if (importe(t, i, columna).compareTo(importe(t, i - 1, columna)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static String esperar(AccesoDatosException.Tipo tipo, String fragmento, Accion accion)
            throws Exception {
        try {
            accion.ejecutar();
        } catch (AccesoDatosException e) {
            comprobar(e.tipo() == tipo, "tipo " + e.tipo() + " en lugar de " + tipo + ": " + e.getMessage());
            comprobar(e.getMessage().contains(fragmento), "mensaje inesperado: " + e.getMessage());
            return "Mensaje: «" + e.getMessage() + "»";
        }
        throw new AssertionError("la operación debía fallar");
    }

    private static String definicion(ConexionBD bd, String funcion) throws AccesoDatosException {
        return bd.consultarUno("SELECT pg_get_functiondef(?::regproc)",
                ps -> ps.setString(1, funcion), rs -> rs.getString(1)).orElseThrow();
    }

    private static List<String> numeraciones(ConexionBD bd, String condicion)
            throws AccesoDatosException {
        return textos(bd, "SELECT fn_numeracion(serie, numero) FROM comprobante WHERE "
                + condicion + " ORDER BY serie, numero");
    }

    private static List<String> textos(ConexionBD bd, String sql) throws AccesoDatosException {
        return bd.consultar(sql, ConexionBD.SIN_PARAMETROS, rs -> rs.getString(1));
    }

    private static long contar(ConexionBD bd, String sql) throws AccesoDatosException {
        return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS, rs -> rs.getLong(1)).orElse(0L);
    }

    private static String contarTexto(ConexionBD bd, String sql) throws AccesoDatosException {
        return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS, rs -> rs.getString(1)).orElse("0");
    }
}
