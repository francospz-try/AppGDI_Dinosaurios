package sermed.comprobantes.datos;

import java.util.List;

import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Serie;

/**
 * Acceso a las series y monedas: las listas de los formularios y su
 * registro ({@code sp_insertar_serie}, {@code sp_insertar_moneda}).
 */
public class CatalogoDAO {

    private final ConexionBD bd;

    /** @param bd capa de conexión */
    public CatalogoDAO(ConexionBD bd) {
        this.bd = bd;
    }

    /**
     * @return series registradas ({@code fn_listar_series})
     * @throws AccesoDatosException si la consulta falla
     */
    public List<Serie> series() throws AccesoDatosException {
        return bd.consultar("SELECT * FROM fn_listar_series()", ConexionBD.SIN_PARAMETROS,
                rs -> new Serie(rs.getString("serie"), rs.getString("tipo_comprobante")));
    }

    /**
     * @return monedas registradas ({@code fn_listar_monedas})
     * @throws AccesoDatosException si la consulta falla
     */
    public List<Moneda> monedas() throws AccesoDatosException {
        return bd.consultar("SELECT * FROM fn_listar_monedas()", ConexionBD.SIN_PARAMETROS,
                rs -> new Moneda(rs.getString("codigo_moneda"), rs.getString("nombre_moneda")));
    }

    /**
     * Registra una serie de comprobante ({@code sp_insertar_serie}).
     *
     * @param s serie y tipo de comprobante
     * @throws AccesoDatosException si la serie ya existe o no es válida
     */
    public void insertarSerie(Serie s) throws AccesoDatosException {
        bd.ejecutar("CALL sp_insertar_serie(?, ?)", ps -> {
            ps.setString(1, s.serie());
            ps.setString(2, s.tipoComprobante());
        });
    }

    /**
     * Registra una moneda ({@code sp_insertar_moneda}).
     *
     * @param m código ISO 4217 y nombre
     * @throws AccesoDatosException si la moneda ya existe o no es válida
     */
    public void insertarMoneda(Moneda m) throws AccesoDatosException {
        bd.ejecutar("CALL sp_insertar_moneda(?, ?)", ps -> {
            ps.setString(1, m.codigo());
            ps.setString(2, m.nombre());
        });
    }
}
