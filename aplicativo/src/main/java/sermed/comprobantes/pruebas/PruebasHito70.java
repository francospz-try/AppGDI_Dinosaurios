package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.AccesoDatosException;
import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.datos.ProductoDAO;
import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.ProductoServicio;
import sermed.comprobantes.modelo.Referencia;
import sermed.comprobantes.validacion.Validador;

/**
 * Pruebas del hito del 70 %: validación de datos, CRUD de clientes,
 * productos y comprobantes, y procedimientos y disparadores de
 * {@code scripts/05_procedimientos.sql}. Cubren casos correctos y casos en
 * que la base debe rechazar la operación. Modifican la base, por lo que se
 * ejecutan sobre una base recién creada con el script completo, después
 * de {@code PruebaConexion} y {@code PruebasHito30}.
 */
public final class PruebasHito70 {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 21);

    /** Siguientes correlativos libres de F001 y EB01 al empezar las pruebas. */
    private static int f1;
    private static int e1;

    /** Operación que debe fallar. */
    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    private PruebasHito70() { }

    /**
     * @param args no se usan
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        ConexionBD bd = new ConexionBD(Configuracion.cargar());
        ClienteDAO clientes = new ClienteDAO(bd);
        ProductoDAO productos = new ProductoDAO(bd);
        ComprobanteDAO comprobantes = new ComprobanteDAO(bd);
        Verificador v = new Verificador();
        f1 = comprobantes.siguienteNumero("F001");
        e1 = comprobantes.siguienteNumero("EB01");
        final int f2 = f1 + 1;
        final int f3 = f1 + 2;
        final String n1 = numeracion(f1);
        final String n2 = numeracion(f2);

        // ---------------------------------------------------------- validación
        System.out.println("== Validación de datos en el aplicativo");
        v.caso("V1 Cliente válido: sin errores", () -> {
            List<String> e = Validador.cliente(cliente("RUC", "20123456789", "EMPRESA S.A.C.", null));
            comprobar(e.isEmpty(), e.toString());
            return "RUC de 11 dígitos y nombre: válido";
        });
        v.caso("V2 DNI de 7 dígitos y RUC con letras: rechazados", () -> {
            List<String> a = Validador.cliente(cliente("DNI", "1234567", "PERSONA", null));
            List<String> b = Validador.cliente(cliente("RUC", "20A23456789", "EMPRESA", null));
            comprobar(a.size() == 1 && b.size() == 1, a + " / " + b);
            return a.get(0);
        });
        v.caso("V3 Nombre vacío y dirección de más de 250 caracteres: rechazados", () -> {
            List<String> e = Validador.cliente(cliente("DNI", "12345678", " ", "X".repeat(251)));
            comprobar(e.size() == 2, e.toString());
            return String.join(" | ", e);
        });
        v.caso("V4 Producto sin descripción ni unidad: rechazado", () -> {
            List<String> e = Validador.producto(new ProductoServicio("500", "", null, 0));
            comprobar(e.size() == 2, e.toString());
            return String.join(" | ", e);
        });
        v.caso("V5 Comprobante válido (F001 de dos líneas): sin errores", () -> {
            List<String> e = Validador.comprobante(facturaPrueba(f1));
            comprobar(e.isEmpty(), e.toString());
            return "cabecera, cliente, líneas, referencia e importes coherentes";
        });
        v.caso("V6 Importes que no suman el total: rechazado", () -> {
            Comprobante c = conImportes(facturaPrueba(f1), "322.03", "50.00", "380.00");
            List<String> e = Validador.comprobante(c);
            comprobar(e.size() == 1 && e.get(0).contains("suman 372.03"), e.toString());
            return e.get(0);
        });
        v.caso("V7 Vencimiento anterior a la emisión, código repetido y cantidad cero", () -> {
            Comprobante base = facturaPrueba(f1);
            LineaDetalle l = base.lineas().get(0);
            LineaDetalle cero = new LineaDetalle(l.codigo(), null, null, BigDecimal.ZERO,
                    l.valorUnitario(), l.precioUnitario(), BigDecimal.ZERO, l.importeVenta());
            Comprobante c = new Comprobante(base.serie(), base.numero(), null, HOY,
                    HOY.minusDays(1), "PEN", null, "CONTADO", null, null, base.importes(),
                    base.cliente(), List.of(l, cero), base.referencias());
            List<String> e = Validador.comprobante(c);
            comprobar(e.size() == 3, e.toString());
            return String.join(" | ", e);
        });
        v.caso("V8 Importe con 3 decimales e importe negativo: rechazados", () -> {
            Comprobante c = conImportes(facturaPrueba(f1), "322.031", "-57.97", "380.00");
            List<String> e = Validador.comprobante(c);
            comprobar(e.size() == 2, e.toString());
            return String.join(" | ", e);
        });

        // ------------------------------------------------------------- clientes
        System.out.println("\n== CRUD de clientes (sp_insertar/modificar/eliminar_cliente)");
        v.caso("C1 Registrar cliente: se normaliza a mayúsculas sin espacios repetidos", () -> {
            clientes.insertar(cliente("RUC", "20111111111", "  Clínica   Nueva  S.A.C. ", "Av. Ejército 101"));
            Cliente c = unicoCliente(clientes, "20111111111");
            comprobar(c.nombreRazonSocial().equals("CLÍNICA NUEVA S.A.C."), c.nombreRazonSocial());
            return c.nombreRazonSocial() + "; " + c.direccion();
        });
        v.caso("C2 Registrar un documento repetido: clave duplicada", () ->
                esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "Ya existe un cliente",
                        () -> clientes.insertar(cliente("RUC", "20111111111", "OTRA", null))));
        v.caso("C3 DNI de 7 dígitos enviado sin validar: lo rechaza la restricción", () ->
                esperar(AccesoDatosException.Tipo.VERIFICACION, "8 dígitos si es DNI",
                        () -> clientes.insertar(cliente("DNI", "1234567", "PERSONA", null))));
        v.caso("C4 Nombre de más de 200 caracteres: dato demasiado largo", () ->
                esperar(AccesoDatosException.Tipo.DATO_INVALIDO, "longitud máxima",
                        () -> clientes.insertar(cliente("DNI", "12345678", "N".repeat(201), null))));
        v.caso("C5 Modificar nombre y dirección", () -> {
            clientes.modificar(cliente("RUC", "20111111111", "Clínica Nueva E.I.R.L.", null));
            Cliente c = unicoCliente(clientes, "20111111111");
            comprobar(c.nombreRazonSocial().equals("CLÍNICA NUEVA E.I.R.L.") && c.direccion() == null,
                    c.toString());
            return c.nombreRazonSocial() + "; dirección borrada";
        });
        v.caso("C6 Modificar un cliente inexistente: no encontrado", () ->
                esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un cliente vigente",
                        () -> clientes.modificar(cliente("RUC", "20000000009", "X", null))));
        v.caso("C7 Borrar cliente con comprobantes: deja de aparecer, sus comprobantes lo conservan", () -> {
            clientes.eliminar("RUC", "20900000002");
            comprobar(clientes.buscar("20900000002", 5).isEmpty(), "sigue apareciendo");
            int numero = (int) contar(bd, "SELECT max(numero) FROM comprobante"
                    + " WHERE numero_documento = '20900000002'");
            Comprobante c = comprobantes.buscar("F001", numero).orElseThrow();
            comprobar(c.cliente() != null && c.cliente().nombreRazonSocial().startsWith("CENTRO"),
                    String.valueOf(c.cliente()));
            return "búsqueda vacía; " + c.numeracion() + " sigue mostrando a "
                    + (c.cliente() == null ? "CLIENTES VARIOS" : c.cliente().nombreRazonSocial());
        });
        v.caso("C8 Borrar un cliente ya dado de baja: no encontrado", () ->
                esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un cliente vigente",
                        () -> clientes.eliminar("RUC", "20900000002")));
        v.caso("C9 Registrar de nuevo un cliente dado de baja: se reactiva", () -> {
            clientes.insertar(cliente("RUC", "20900000002", "Centro Médico Santa Ana S.A.C.", null));
            Cliente c = unicoCliente(clientes, "20900000002");
            return c.nombreRazonSocial() + " vigente otra vez, con " + c.comprobantes()
                    + " comprobantes";
        });

        // ------------------------------------------------------------ productos
        System.out.println("\n== CRUD de productos y servicios (sp_*_producto)");
        v.caso("P1 Registrar producto", () -> {
            productos.insertar(new ProductoServicio("ps-100", "Termómetro de punzón", "und", 0));
            ProductoServicio p = productos.buscar("PS-100", 5).get(0);
            comprobar(p.codigo().equals("PS-100") && p.unidadMedida().equals("UND"), p.toString());
            return p.codigo() + " " + p.descripcion() + " (" + p.unidadMedida() + ")";
        });
        v.caso("P2 Registrar un código repetido: clave duplicada", () ->
                esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "Ya existe un producto",
                        () -> productos.insertar(new ProductoServicio("349", "X", "UND", 0))));
        v.caso("P3 Descripción en blanco enviada sin validar: la rechaza la restricción", () ->
                esperar(AccesoDatosException.Tipo.VERIFICACION, "descripción",
                        () -> productos.insertar(new ProductoServicio("PS-101", "   ", "UND", 0))));
        v.caso("P4 Unidad de más de 10 caracteres: dato demasiado largo", () ->
                esperar(AccesoDatosException.Tipo.DATO_INVALIDO, "longitud máxima",
                        () -> productos.insertar(new ProductoServicio("PS-102", "X", "UNIDADES-XL", 0))));
        v.caso("P5 Modificar descripción y unidad", () -> {
            productos.modificar(new ProductoServicio("PS-100", "Termómetro digital de punzón", "UNIDAD", 0));
            ProductoServicio p = productos.buscar("PS-100", 5).get(0);
            comprobar(p.descripcion().equals("TERMÓMETRO DIGITAL DE PUNZÓN"), p.descripcion());
            return p.descripcion() + " (" + p.unidadMedida() + ")";
        });
        v.caso("P6 Borrar producto vendido: deja de aparecer, las líneas lo conservan", () -> {
            productos.eliminar("363");
            comprobar(productos.buscar("363", 5).isEmpty(), "sigue apareciendo");
            return "363 dado de baja; búsqueda vacía";
        });
        v.caso("P7 Borrar un producto inexistente: no encontrado", () ->
                esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un producto",
                        () -> productos.eliminar("NO-EXISTE")));

        // --------------------------------------------------------- comprobantes
        System.out.println("\n== Procedimientos y CRUD de comprobantes");
        v.caso("K1 Siguiente correlativo de la serie (fn_siguiente_numero)", () -> {
            int f = comprobantes.siguienteNumero("F001");
            int e = comprobantes.siguienteNumero("EB01");
            long ultimoF = contar(bd, "SELECT max(numero) FROM comprobante WHERE serie = 'F001'");
            long eliminados = contar(bd, "SELECT count(*) FROM comprobante WHERE serie = 'F001'"
                    + " AND eliminado");
            comprobar(f == ultimoF + 1 && e == contar(bd,
                    "SELECT max(numero) FROM comprobante WHERE serie = 'EB01'") + 1, f + ", " + e);
            comprobar(eliminados > 0, "los datos de prueba deben tener facturas eliminadas");
            return "F001 → " + f + "; EB01 → " + e + " (los números eliminados no se reutilizan)";
        });
        v.caso("K2 Cálculo de importes con las reglas de cada formato (fn_calcular_importes)", () -> {
            ComprobanteDAO.Calculo f = comprobantes.calcularImportes("F001", List.of(
                    linea("349", "2", null, "150"), linea("263", "1", null, "80")));
            ComprobanteDAO.Calculo e = comprobantes.calcularImportes("EB01", List.of(
                    linea("BY-128M9", "1", null, "120")));
            comprobar(f.opGravada().compareTo(new BigDecimal("322.03")) == 0
                    && f.igv().compareTo(new BigDecimal("57.97")) == 0
                    && f.importeTotal().compareTo(new BigDecimal("380.00")) == 0, f.toString());
            BigDecimal ev = e.lineas().get(0).importeVenta();
            comprobar(ev.compareTo(new BigDecimal("119.9999997")) == 0
                    && e.lineas().get(0).precioUnitario() == null, e.toString());
            return "F001: 322.03 + 57.97 = 380.00; EB01: importe de venta " + ev.toPlainString()
                    + ", total " + e.importeTotal();
        });
        v.caso("K3 Registrar comprobante completo: crea cliente y producto nuevos en una transacción", () -> {
            comprobantes.insertar(facturaPrueba(f1));
            Comprobante c = comprobantes.buscar("F001", f1).orElseThrow();
            comprobar(c.lineas().size() == 2 && c.referencias().size() == 2, c.lineas() + " " + c.referencias());
            comprobar(c.cliente() != null && c.cliente().nombreRazonSocial().equals("LABORATORIO PRUEBA S.A.C."),
                    String.valueOf(c.cliente()));
            comprobar(productos.buscar("PS-200", 5).size() == 1, "no se creó PS-200");
            return c.numeracion() + ": 2 líneas, 2 referencias, cliente RUC 20222222222 y producto PS-200 creados";
        });
        v.caso("K4 Registrar el mismo comprobante otra vez: clave duplicada", () ->
                esperar(AccesoDatosException.Tipo.CLAVE_DUPLICADA, "ya está registrado",
                        () -> comprobantes.insertar(facturaPrueba(f1))));
        v.caso("K5 Registrar boleta EB01 a CLIENTES VARIOS con importes calculados", () -> {
            ComprobanteDAO.Calculo k = comprobantes.calcularImportes("EB01",
                    List.of(linea("BY-128M9", "1", null, "120")));
            Comprobante.Importes i = new Comprobante.Importes(k.opGravada(), BigDecimal.ZERO,
                    BigDecimal.ZERO, null, BigDecimal.ZERO, k.igv(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, k.importeTotal(), null);
            comprobantes.insertar(new Comprobante("EB01", e1, null, HOY, null, "PEN", null, null,
                    null, null, i, null, k.lineas(), List.of()));
            Comprobante c = comprobantes.buscar("EB01", e1).orElseThrow();
            comprobar(c.cliente() == null, "debía quedar sin cliente");
            return c.numeracion() + " por " + c.importes().importeTotal() + ", sin cliente";
        });
        v.caso("K6 Importes que no cuadran: lo rechaza el disparador y no se crea nada", () -> {
            String m = esperar(AccesoDatosException.Tipo.VERIFICACION, "no cuadran", () ->
                    comprobantes.insertar(conCliente(conImportes(facturaPrueba(f2),
                            "322.03", "50.00", "380.00"), "20333333333")));
            comprobar(clientes.buscar("20333333333", 5).isEmpty(), "se creó el cliente");
            return m + " El cliente nuevo no quedó registrado.";
        });
        v.caso("K7 Líneas que no suman el total: lo rechaza el disparador diferido y no se crea nada", () -> {
            Comprobante base = facturaPrueba(f2);
            LineaDetalle l = base.lineas().get(0);
            LineaDetalle otra = new LineaDetalle("PS-300", "PRODUCTO QUE NO DEBE QUEDAR", "UND",
                    new BigDecimal("1"), new BigDecimal("67.80"), new BigDecimal("80"),
                    BigDecimal.ZERO, new BigDecimal("70"));
            Comprobante c = new Comprobante("F001", f2, null, HOY, null, "PEN", null, "CONTADO",
                    null, null, base.importes(), base.cliente(), List.of(l, otra), List.of());
            String m = esperar(AccesoDatosException.Tipo.VERIFICACION, "suman 370.00", () ->
                    comprobantes.insertar(c));
            comprobar(productos.buscar("PS-300", 5).isEmpty(), "se creó PS-300");
            comprobar(comprobantes.buscar("F001", f2).isEmpty(), "se registró " + n2);
            return m + " Ni el comprobante ni el producto nuevo quedaron registrados.";
        });
        v.caso("K8 Comprobante sin líneas: rechazado", () ->
                esperar(AccesoDatosException.Tipo.VERIFICACION, "al menos una línea", () ->
                        comprobantes.insertar(sinLineas(facturaPrueba(f2)))));
        v.caso("K9 Serie no registrada: rechazada por la clave foránea", () ->
                esperar(AccesoDatosException.Tipo.REFERENCIA, "serie indicada no está registrada", () ->
                        comprobantes.insertar(conSerie(facturaPrueba(f2), "F002"))));
        v.caso("K10 Vencimiento anterior a la emisión: rechazado por la restricción", () -> {
            Comprobante b = facturaPrueba(f2);
            Comprobante c = new Comprobante("F001", f2, null, HOY, HOY.minusDays(3), "PEN", null,
                    "CONTADO", null, null, b.importes(), b.cliente(), b.lineas(), b.referencias());
            return esperar(AccesoDatosException.Tipo.VERIFICACION, "vencimiento", () ->
                    comprobantes.insertar(c));
        });
        v.caso("K11 Cantidad cero en una línea: rechazada por la restricción", () -> {
            Comprobante b = facturaPrueba(f2);
            LineaDetalle l = b.lineas().get(0);
            LineaDetalle cero = new LineaDetalle(l.codigo(), null, null, BigDecimal.ZERO,
                    l.valorUnitario(), l.precioUnitario(), BigDecimal.ZERO, l.importeVenta());
            Comprobante c = new Comprobante("F001", f2, null, HOY, null, "PEN", null, "CONTADO",
                    null, null, b.importes(), b.cliente(), List.of(cero, b.lineas().get(1)),
                    b.referencias());
            return esperar(AccesoDatosException.Tipo.VERIFICACION, "mayor que cero", () ->
                    comprobantes.insertar(c));
        });
        v.caso("K12 Cliente nuevo sin nombre: rechazado por el procedimiento", () ->
                esperar(AccesoDatosException.Tipo.REFERENCIA, "indique su nombre", () ->
                        comprobantes.insertar(conCliente(facturaPrueba(f2), "20444444444", null))));
        v.caso("K13 Registrar para un cliente existente actualiza su dirección con la impresa", () -> {
            Comprobante c = conCliente(facturaPrueba(f2), "20111111111", "CLÍNICA NUEVA E.I.R.L.");
            Comprobante conDir = new Comprobante(c.serie(), c.numero(), null, HOY, null, "PEN", null,
                    "CONTADO", null, null, c.importes(),
                    cliente("RUC", "20111111111", null, "Calle Mercaderes 200 - Arequipa"),
                    c.lineas(), List.of());
            comprobantes.insertar(conDir);
            Cliente cl = unicoCliente(clientes, "20111111111");
            comprobar(cl.direccion().equals("CALLE MERCADERES 200 - AREQUIPA"), cl.direccion());
            return n2 + " registrado; dirección actualizada a " + cl.direccion();
        });
        v.caso("K14 Modificar comprobante: cabecera, una línea menos y una referencia menos", () -> {
            Comprobante b = facturaPrueba(f1);
            LineaDetalle l = b.lineas().get(0);
            Comprobante.Importes i = importes("254.24", "45.76", "300.00");
            Comprobante c = new Comprobante("F001", f1, null, HOY, HOY.plusDays(30), "PEN", null,
                    "CRÉDITO", null, "Corregido por error de transcripción", i, b.cliente(),
                    List.of(l), List.of(b.referencias().get(0)));
            comprobantes.modificar(c);
            Comprobante r = comprobantes.buscar("F001", f1).orElseThrow();
            comprobar(r.lineas().size() == 1 && r.referencias().size() == 1
                    && r.formaPago().equals("CRÉDITO") && r.fechaVencimiento().equals(HOY.plusDays(30)),
                    r.toString());
            long huerfanos = contar(bd, "SELECT count(*) FROM documento_relacionado d WHERE NOT EXISTS"
                    + " (SELECT 1 FROM referencia r WHERE r.tipo_relacionado = d.tipo_relacionado"
                    + " AND r.numero_relacionado = d.numero_relacionado)");
            comprobar(huerfanos == 0, huerfanos + " documentos sin citar");
            return "1 línea, 1 referencia, CRÉDITO a 30 días, total 300.00; el documento que dejó de"
                    + " citarse se eliminó";
        });
        v.caso("K15 Modificar dejando el comprobante sin líneas: rechazado, sin cambios", () -> {
            String m = esperar(AccesoDatosException.Tipo.VERIFICACION, "al menos una línea", () ->
                    comprobantes.modificar(sinLineas(facturaPrueba(f1))));
            comprobar(comprobantes.buscar("F001", f1).orElseThrow().lineas().size() == 1,
                    "cambió el comprobante");
            return m;
        });
        v.caso("K16 Modificar con un total que no coincide con las líneas: rechazado, sin cambios", () -> {
            Comprobante b = facturaPrueba(f1);
            Comprobante c = new Comprobante("F001", f1, null, HOY, null, "PEN", null, "CONTADO",
                    null, null, importes("254.24", "45.76", "300.00"), b.cliente(), b.lineas(),
                    List.of());
            String m = esperar(AccesoDatosException.Tipo.VERIFICACION, "suman 380.00", () ->
                    comprobantes.modificar(c));
            comprobar(comprobantes.buscar("F001", f1).orElseThrow().formaPago().equals("CRÉDITO"),
                    "cambió el comprobante");
            return m;
        });
        v.caso("K17 Borrar comprobante (lógico): desaparece de las consultas y queda en eliminados", () -> {
            comprobantes.eliminar("F001", f1);
            comprobar(comprobantes.buscar("F001", f1).isEmpty(), "sigue visible");
            comprobar(comprobantes.porPeriodo(HOY, HOY).stream().noneMatch(r -> r.numero() == f1),
                    "sigue en el periodo");
            comprobar(comprobantes.eliminados(HOY, HOY).size() == 1, "no figura en eliminados");
            long filas = contar(bd, "SELECT count(*) FROM incluye WHERE serie = 'F001' AND numero = " + f1);
            comprobar(filas == 1, "se borraron sus líneas");
            return "oculto en el detalle y en el periodo; figura en eliminados; conserva su línea";
        });
        v.caso("K18 Borrar o modificar un comprobante ya eliminado: no encontrado", () -> {
            String a = esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un comprobante vigente",
                    () -> comprobantes.eliminar("F001", f1));
            esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un comprobante vigente",
                    () -> comprobantes.modificar(facturaPrueba(f1)));
            return a;
        });
        v.caso("K19 Restaurar comprobante eliminado", () -> {
            comprobantes.restaurar("F001", f1);
            comprobar(comprobantes.buscar("F001", f1).isPresent(), "no se restauró");
            String m = esperar(AccesoDatosException.Tipo.NO_ENCONTRADO, "No existe un comprobante eliminado",
                    () -> comprobantes.restaurar("F001", f1));
            return n1 + " vigente otra vez; restaurarlo de nuevo: " + m;
        });
        v.caso("K20 Vender un producto dado de baja lo reactiva", () -> {
            List<LineaDetalle> l = List.of(new LineaDetalle("363", null, null, new BigDecimal("1"),
                    new BigDecimal("661.02"), new BigDecimal("780"), BigDecimal.ZERO, new BigDecimal("780")));
            comprobantes.insertar(new Comprobante("F001", f3, null, HOY, null, "PEN", null, "CONTADO",
                    null, null, importes("661.02", "118.98", "780.00"),
                    cliente("RUC", "20900000001", null, null), l, List.of()));
            comprobar(productos.buscar("363", 5).size() == 1, "363 sigue dado de baja");
            return numeracion(f3) + " registrado con el producto 363, que vuelve a estar vigente";
        });

        v.terminar();
    }

    // ------------------------------------------------------------- utilidades

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

    private static String numeracion(int numero) {
        return String.format("F001-%06d", numero);
    }

    private static Cliente cliente(String tipo, String numero, String nombre, String direccion) {
        return new Cliente(tipo, numero, nombre, direccion, 0);
    }

    private static Cliente unicoCliente(ClienteDAO dao, String numero) throws AccesoDatosException {
        List<Cliente> r = dao.buscar(numero, 5);
        comprobar(r.size() == 1, "se esperaba un cliente " + numero + " y hubo " + r.size());
        return r.get(0);
    }

    private static LineaDetalle linea(String codigo, String cantidad, String valor, String precio) {
        return new LineaDetalle(codigo, null, null, new BigDecimal(cantidad),
                valor == null ? null : new BigDecimal(valor),
                precio == null ? null : new BigDecimal(precio), BigDecimal.ZERO, null);
    }

    private static Comprobante.Importes importes(String gravada, String igv, String total) {
        return new Comprobante.Importes(new BigDecimal(gravada), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, new BigDecimal(igv), null, null, null,
                new BigDecimal(total), BigDecimal.ZERO);
    }

    /**
     * Factura de prueba con el formato del proveedor: un producto existente
     * (349), un producto nuevo (PS-200), un cliente nuevo y dos referencias.
     */
    private static Comprobante facturaPrueba(int numero) {
        List<LineaDetalle> lineas = List.of(
                new LineaDetalle("349", null, null, new BigDecimal("2"), new BigDecimal("127.12"),
                        new BigDecimal("150"), BigDecimal.ZERO, new BigDecimal("300")),
                new LineaDetalle("PS-200", "Servicio de verificación de balanza", "SERV",
                        new BigDecimal("1"), new BigDecimal("67.80"), new BigDecimal("80"),
                        BigDecimal.ZERO, new BigDecimal("80")));
        return new Comprobante("F001", numero, null, HOY, null, "PEN", null, "CONTADO", null, null,
                importes("322.03", "57.97", "380.00"),
                cliente("RUC", "20222222222", "Laboratorio Prueba S.A.C.", "Av. Goyeneche 120"),
                lineas, List.of(new Referencia("Guía de remisión", "T001-009001"),
                        new Referencia("Cotización", "00001-08-2026")));
    }

    private static Comprobante conImportes(Comprobante c, String gravada, String igv, String total) {
        return new Comprobante(c.serie(), c.numero(), null, c.fechaEmision(), c.fechaVencimiento(),
                c.codigoMoneda(), null, c.formaPago(), c.medioPago(), c.observacion(),
                importes(gravada, igv, total), c.cliente(), c.lineas(), c.referencias());
    }

    private static Comprobante conCliente(Comprobante c, String ruc) {
        return conCliente(c, ruc, "Cliente Que No Debe Quedar S.A.C.");
    }

    private static Comprobante conCliente(Comprobante c, String ruc, String nombre) {
        return new Comprobante(c.serie(), c.numero(), null, c.fechaEmision(), c.fechaVencimiento(),
                c.codigoMoneda(), null, c.formaPago(), c.medioPago(), c.observacion(), c.importes(),
                cliente("RUC", ruc, nombre, null), c.lineas(), c.referencias());
    }

    private static Comprobante conSerie(Comprobante c, String serie) {
        return new Comprobante(serie, c.numero(), null, c.fechaEmision(), c.fechaVencimiento(),
                c.codigoMoneda(), null, c.formaPago(), c.medioPago(), c.observacion(), c.importes(),
                c.cliente(), c.lineas(), c.referencias());
    }

    private static Comprobante sinLineas(Comprobante c) {
        return new Comprobante(c.serie(), c.numero(), null, c.fechaEmision(), c.fechaVencimiento(),
                c.codigoMoneda(), null, c.formaPago(), c.medioPago(), c.observacion(), c.importes(),
                c.cliente(), List.of(), c.referencias());
    }

    private static long contar(ConexionBD bd, String sql) throws AccesoDatosException {
        return bd.consultarUno(sql, ConexionBD.SIN_PARAMETROS, rs -> rs.getLong(1)).orElse(0L);
    }
}
