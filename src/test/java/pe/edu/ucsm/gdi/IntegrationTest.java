package pe.edu.ucsm.gdi;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

/** Pruebas sin framework: mismas operaciones públicas que utiliza la interfaz. */
public final class IntegrationTest {
    private static int checks;
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++; System.out.println("PASS " + description);
    }
    private static void state(String expected, SqlAction action) throws Exception {
        try { action.run(); throw new AssertionError("Se esperaba SQLSTATE " + expected); }
        catch (SQLException e) { check(expected.equals(e.getSQLState()), "Rechazo " + expected); }
    }
    @FunctionalInterface private interface SqlAction { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        DbConfig config = DbConfig.load(Path.of(args.length==0 ? "config/database.properties" : args[0]));
        GdiRepository repo = new GdiRepository(config);
        check(repo.conexion().value(0,"base_datos").equals(config.name()), "Conexión al nombre configurado");
        check(!config.toString().contains("secreto-no-publicar"), "Configuración no expone contraseña");
        TableData anonymous = repo.ficha("EB01",50);
        check(anonymous.value(0,"numero_documento")==null, "CLIENTES VARIOS sin identidad ficticia");
        check(new BigDecimal("120.00").compareTo((BigDecimal)anonymous.value(0,"importe_total"))==0, "Total EB01-50 = 120.00");
        check(new BigDecimal("119.9999997").compareTo((BigDecimal)repo.detalle("EB01",50).value(0,"importe_venta"))==0,
                "Detalle conserva siete decimales");
        check(repo.comprobantes("F001-5206").rows().size()==1, "Búsqueda por serie y número");
        check(repo.comprobantes("F001-005206").rows().size()==1, "Búsqueda con ceros impresos del comprobante");
        check(!repo.comprobantes("CLIENTES VARIOS").rows().isEmpty(), "Búsqueda por nombre visible del cliente genérico");
        check(repo.comprobantes("' OR 1=1 --").rows().isEmpty(), "Búsqueda parametrizada no interpreta SQL");
        check(repo.comprobantes("").rows().size()==500, "Listado limitado a 500 resultados");
        check(repo.referencias("F001",5206).value(0,"numero_relacionado").equals("T001-003115"), "Referencia documental conservada");
        check(repo.ficha("ZZZZ",999).rows().isEmpty(), "Documento inexistente devuelve vacío");
        for (int n=1; n<=12; n++) check(!repo.reporte(n).columns().isEmpty(), "Reporte " + n + " por procedimiento");
        state("22023", () -> repo.reporte(99));
        String doc="8" + String.format("%07d", Math.abs(UUID.randomUUID().hashCode()) % 10000000);
        repo.crearCliente("DNI", doc, "PRUEBA GDI", "");
        check(repo.clientes(doc).value(0,"direccion")==null, "Alta conserva dirección ausente como NULL");
        state("23505", () -> repo.crearCliente("DNI", doc, "DUPLICADO", ""));
        check(repo.clientes(doc).value(0,"nombre_razon_social").equals("PRUEBA GDI"), "Duplicado no altera el cliente original");
        repo.modificarCliente("DNI",doc,"PRUEBA MODIFICADA","Arequipa");
        check(repo.clientes(doc).value(0,"nombre_razon_social").equals("PRUEBA MODIFICADA"), "Modificación persistida");
        state("23514", () -> repo.modificarCliente("DNI",doc,"   ",""));
        check(repo.clientes(doc).value(0,"nombre_razon_social").equals("PRUEBA MODIFICADA"), "Rollback preserva nombre tras cambio inválido");
        repo.bajaCliente("DNI",doc);
        check(repo.clientes(doc).rows().isEmpty(), "Baja lógica excluye cliente del listado");
        state("23505", () -> repo.crearCliente("DNI",doc,"OTRO",""));
        state("P0002", () -> repo.modificarCliente("DNI",doc,"OTRO",""));
        state("23514", () -> repo.crearCliente("DNI","123","INVÁLIDO",""));
        try {
            new GdiRepository(new DbConfig("127.0.0.1",1,config.name(),config.user(),"secreto-no-publicar")).conexion();
            throw new AssertionError("Se esperaba conexión fallida");
        } catch (SQLException e) {
            check(e.getSQLState().startsWith("08"), "Conexión fallida controlada");
            check(!UserMessages.error(e).contains("secreto-no-publicar"), "Mensaje no divulga contraseña");
        }
        System.out.println("RESULTADO: " + checks + " comprobaciones correctas");
    }
}
