package sermed.comprobantes.datos;

import java.util.List;

import sermed.comprobantes.modelo.ProductoServicio;

/** Acceso a los productos y servicios mediante las rutinas de la base de datos. */
public class ProductoDAO {

    private final ConexionBD bd;

    /** @param bd capa de conexión */
    public ProductoDAO(ConexionBD bd) {
        this.bd = bd;
    }

    /**
     * Busca productos o servicios por el inicio del código o por cualquier
     * parte de la descripción ({@code fn_buscar_productos}).
     *
     * @param texto  texto buscado; vacío devuelve todos
     * @param limite número máximo de filas
     * @return productos encontrados, ordenados por código
     * @throws AccesoDatosException si la consulta falla
     */
    public List<ProductoServicio> buscar(String texto, int limite) throws AccesoDatosException {
        return bd.consultar("SELECT * FROM fn_buscar_productos(?, ?)",
                ps -> {
                    ps.setString(1, texto == null ? "" : texto.trim());
                    ps.setInt(2, limite);
                },
                rs -> new ProductoServicio(rs.getString("codigo"), rs.getString("descripcion"),
                        rs.getString("unidad_medida"), rs.getLong("lineas")));
    }

    /**
     * Registra un producto o servicio ({@code sp_insertar_producto}). Si el
     * código pertenece a uno dado de baja, la base lo reactiva.
     *
     * @param p producto o servicio
     * @throws AccesoDatosException si el código ya existe o un dato no es válido
     */
    public void insertar(ProductoServicio p) throws AccesoDatosException {
        bd.ejecutar("CALL sp_insertar_producto(?, ?, ?)", ps -> {
            ps.setString(1, p.codigo());
            ps.setString(2, p.descripcion());
            ps.setString(3, p.unidadMedida());
        });
    }

    /**
     * Modifica la descripción y la unidad ({@code sp_modificar_producto}).
     *
     * @param p producto con los datos nuevos; el código lo identifica
     * @throws AccesoDatosException si no existe o un dato no es válido
     */
    public void modificar(ProductoServicio p) throws AccesoDatosException {
        bd.ejecutar("CALL sp_modificar_producto(?, ?, ?)", ps -> {
            ps.setString(1, p.codigo());
            ps.setString(2, p.descripcion());
            ps.setString(3, p.unidadMedida());
        });
    }

    /**
     * Da de baja un producto o servicio ({@code sp_eliminar_producto}, borrado lógico).
     *
     * @param codigo código del producto o servicio
     * @throws AccesoDatosException si no existe o ya estaba dado de baja
     */
    public void eliminar(String codigo) throws AccesoDatosException {
        bd.ejecutar("CALL sp_eliminar_producto(?)", ps -> ps.setString(1, codigo));
    }
}
