package pe.edu.ucsm.gdi;

import java.sql.SQLException;

/** Operaciones disponibles para la interfaz y las pruebas de integración. */
public final class GdiRepository {
    private final Database db;
    public GdiRepository(DbConfig config) { db = new Database(config); }
    public TableData conexion() throws SQLException { return db.query("app.sp_probar_conexion"); }
    public TableData comprobantes(String search) throws SQLException { return db.query("app.sp_listar_comprobantes", search); }
    public TableData ficha(String serie, int numero) throws SQLException { return db.query("app.sp_ficha_comprobante", serie, numero); }
    public TableData detalle(String serie, int numero) throws SQLException { return db.query("app.sp_detalle_comprobante", serie, numero); }
    public TableData referencias(String serie, int numero) throws SQLException { return db.query("app.sp_referencias_comprobante", serie, numero); }
    public TableData clientes(String search) throws SQLException { return db.query("app.sp_listar_clientes", search); }
    public void crearCliente(String tipo, String doc, String nombre, String direccion) throws SQLException {
        db.execute("app.sp_crear_cliente", tipo, doc, nombre, direccion);
    }
    public void modificarCliente(String tipo, String doc, String nombre, String direccion) throws SQLException {
        db.execute("app.sp_modificar_cliente", tipo, doc, nombre, direccion);
    }
    public void bajaCliente(String tipo, String doc) throws SQLException { db.execute("app.sp_baja_cliente", tipo, doc); }
    public TableData reporte(int codigo) throws SQLException { return db.query("app.sp_reporte", codigo); }
}
