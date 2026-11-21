package sermed.comprobantes.datos;

import java.util.List;

import sermed.comprobantes.modelo.Cliente;

/** Acceso a los clientes mediante las rutinas de la base de datos. */
public class ClienteDAO {

    private final ConexionBD bd;

    /** @param bd capa de conexión */
    public ClienteDAO(ConexionBD bd) {
        this.bd = bd;
    }

    /**
     * Busca clientes por el inicio del número de documento o por cualquier
     * parte del nombre ({@code fn_buscar_clientes}).
     *
     * @param texto  texto buscado; vacío devuelve todos
     * @param limite número máximo de filas
     * @return clientes encontrados, ordenados por nombre
     * @throws AccesoDatosException si la consulta falla
     */
    public List<Cliente> buscar(String texto, int limite) throws AccesoDatosException {
        return bd.consultar("SELECT * FROM fn_buscar_clientes(?, ?)",
                ps -> {
                    ps.setString(1, texto == null ? "" : texto.trim());
                    ps.setInt(2, limite);
                },
                rs -> new Cliente(rs.getString("tipo_documento"), rs.getString("numero_documento"),
                        rs.getString("nombre_razon_social"), rs.getString("direccion"),
                        rs.getLong("comprobantes")));
    }

    /**
     * Registra un cliente ({@code sp_insertar_cliente}). Si el documento
     * pertenece a un cliente dado de baja, la base lo reactiva.
     *
     * @param c cliente
     * @throws AccesoDatosException si ya existe un cliente vigente con ese
     *                              documento o un dato viola una restricción
     */
    public void insertar(Cliente c) throws AccesoDatosException {
        bd.ejecutar("CALL sp_insertar_cliente(?, ?, ?, ?)", ps -> {
            ps.setString(1, c.tipoDocumento());
            ps.setString(2, c.numeroDocumento());
            ps.setString(3, c.nombreRazonSocial());
            ps.setString(4, c.direccion());
        });
    }

    /**
     * Modifica el nombre y la dirección de un cliente ({@code sp_modificar_cliente}).
     *
     * @param c cliente con los datos nuevos; el documento identifica al cliente
     * @throws AccesoDatosException si el cliente no existe o un dato no es válido
     */
    public void modificar(Cliente c) throws AccesoDatosException {
        bd.ejecutar("CALL sp_modificar_cliente(?, ?, ?, ?)", ps -> {
            ps.setString(1, c.tipoDocumento());
            ps.setString(2, c.numeroDocumento());
            ps.setString(3, c.nombreRazonSocial());
            ps.setString(4, c.direccion());
        });
    }

    /**
     * Da de baja un cliente ({@code sp_eliminar_cliente}, borrado lógico).
     *
     * @param tipoDocumento   DNI o RUC
     * @param numeroDocumento número del documento
     * @throws AccesoDatosException si el cliente no existe o ya estaba dado de baja
     */
    public void eliminar(String tipoDocumento, String numeroDocumento) throws AccesoDatosException {
        bd.ejecutar("CALL sp_eliminar_cliente(?, ?)", ps -> {
            ps.setString(1, tipoDocumento);
            ps.setString(2, numeroDocumento);
        });
    }
}
