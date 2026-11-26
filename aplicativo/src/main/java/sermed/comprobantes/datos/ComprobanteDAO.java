package sermed.comprobantes.datos;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.ComprobanteResumen;
import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.Referencia;

/** Acceso a los comprobantes mediante las rutinas de la base de datos. */
public class ComprobanteDAO {

    private final ConexionBD bd;

    /** @param bd capa de conexión */
    public ComprobanteDAO(ConexionBD bd) {
        this.bd = bd;
    }

    /**
     * Lista los comprobantes emitidos entre dos fechas, incluidas
     * ({@code fn_comprobantes_periodo}, consulta C01).
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return comprobantes ordenados por fecha, serie y número
     * @throws AccesoDatosException si la consulta falla
     */
    public List<ComprobanteResumen> porPeriodo(LocalDate desde, LocalDate hasta)
            throws AccesoDatosException {
        return periodo("fn_comprobantes_periodo", desde, hasta);
    }

    /**
     * Lista los comprobantes eliminados (borrado lógico) de un periodo
     * ({@code fn_comprobantes_eliminados}).
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return comprobantes eliminados ordenados por fecha
     * @throws AccesoDatosException si la consulta falla
     */
    public List<ComprobanteResumen> eliminados(LocalDate desde, LocalDate hasta)
            throws AccesoDatosException {
        return periodo("fn_comprobantes_eliminados", desde, hasta);
    }

    private List<ComprobanteResumen> periodo(String funcion, LocalDate desde, LocalDate hasta)
            throws AccesoDatosException {
        return bd.consultar("SELECT * FROM " + funcion + "(?, ?)",
                ps -> {
                    ps.setDate(1, Date.valueOf(desde));
                    ps.setDate(2, Date.valueOf(hasta));
                },
                rs -> new ComprobanteResumen(rs.getString("serie"), rs.getInt("numero"),
                        rs.getString("tipo_comprobante"),
                        rs.getDate("fecha_emision").toLocalDate(), rs.getString("cliente"),
                        rs.getBigDecimal("importe_total")));
    }

    /**
     * Obtiene un comprobante completo: cabecera, cliente, líneas y
     * referencias (consulta C02).
     *
     * @param serie  serie
     * @param numero correlativo
     * @return el comprobante, o vacío si no existe
     * @throws AccesoDatosException si una consulta falla
     */
    public Optional<Comprobante> buscar(String serie, int numero) throws AccesoDatosException {
        ConexionBD.Parametros clave = ps -> {
            ps.setString(1, serie);
            ps.setInt(2, numero);
        };
        List<LineaDetalle> lineas = bd.consultar(
                "SELECT * FROM fn_comprobante_lineas(?, ?)", clave,
                rs -> new LineaDetalle(rs.getString("codigo"), rs.getString("descripcion"),
                        rs.getString("unidad_medida"), rs.getBigDecimal("cantidad"),
                        rs.getBigDecimal("valor_unitario"), rs.getBigDecimal("precio_unitario"),
                        rs.getBigDecimal("descuento"), rs.getBigDecimal("importe_venta")));
        List<Referencia> referencias = bd.consultar(
                "SELECT * FROM fn_comprobante_referencias(?, ?)", clave,
                rs -> new Referencia(rs.getString("tipo_relacionado"),
                        rs.getString("numero_relacionado")));
        return bd.consultarUno("SELECT * FROM fn_comprobante_cabecera(?, ?)", clave,
                rs -> cabecera(rs, lineas, referencias));
    }

    private static Comprobante cabecera(ResultSet rs, List<LineaDetalle> lineas,
                                        List<Referencia> referencias) throws SQLException {
        Comprobante.Importes importes = new Comprobante.Importes(
                rs.getBigDecimal("op_gravada"), rs.getBigDecimal("op_exonerada"),
                rs.getBigDecimal("op_inafecta"), rs.getBigDecimal("op_gratuita"),
                rs.getBigDecimal("isc"), rs.getBigDecimal("igv"),
                rs.getBigDecimal("otros_cargos"), rs.getBigDecimal("otros_tributos"),
                rs.getBigDecimal("monto_redondeo"), rs.getBigDecimal("importe_total"),
                rs.getBigDecimal("percepcion"));
        Cliente cliente = rs.getString("tipo_documento") == null ? null
                : new Cliente(rs.getString("tipo_documento"), rs.getString("numero_documento"),
                        rs.getString("nombre_razon_social"), rs.getString("direccion"), 0);
        Date vencimiento = rs.getDate("fecha_vencimiento");
        return new Comprobante(rs.getString("serie"), rs.getInt("numero"),
                rs.getString("tipo_comprobante"), rs.getDate("fecha_emision").toLocalDate(),
                vencimiento == null ? null : vencimiento.toLocalDate(),
                rs.getString("codigo_moneda"), rs.getString("nombre_moneda"),
                rs.getString("forma_pago"), rs.getString("medio_pago"),
                rs.getString("observacion"), importes, cliente, lineas, referencias);
    }

    /**
     * Resultado de {@code fn_calcular_importes}: las líneas completadas y el
     * resumen de importes calculado con las reglas de los comprobantes.
     *
     * @param lineas       líneas con valor unitario e importe de venta calculados
     * @param opGravada    operaciones gravadas
     * @param igv          IGV
     * @param importeTotal importe total
     */
    public record Calculo(List<LineaDetalle> lineas, BigDecimal opGravada, BigDecimal igv,
                          BigDecimal importeTotal) { }

    /**
     * Calcula los valores de las líneas y el resumen de importes a partir del
     * precio unitario con IGV y la cantidad ({@code fn_calcular_importes}).
     * Solo sugiere valores: el comprobante se registra tal como fue impreso.
     *
     * @param serie  serie, que determina el formato (SUNAT o proveedor)
     * @param lineas líneas con código, cantidad y precio unitario
     * @return líneas completadas e importes
     * @throws AccesoDatosException si el cálculo falla
     */
    public Calculo calcularImportes(String serie, List<LineaDetalle> lineas)
            throws AccesoDatosException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lineas.size(); i++) {
            LineaDetalle l = lineas.get(i);
            json.append(i > 0 ? "," : "").append("{\"codigo\":\"")
                .append(l.codigo().replace("\\", "\\\\").replace("\"", "\\\""))
                .append("\",\"cantidad\":").append(l.cantidad().toPlainString())
                .append(",\"precio_unitario\":").append(l.precioUnitario().toPlainString())
                .append('}');
        }
        json.append(']');
        List<Object[]> filas = bd.consultar("SELECT * FROM fn_calcular_importes(?, ?::jsonb)",
                ps -> {
                    ps.setString(1, serie);
                    ps.setString(2, json.toString());
                },
                rs -> new Object[] {rs.getString("codigo"), rs.getBigDecimal("cantidad"),
                    rs.getBigDecimal("valor_unitario"), rs.getBigDecimal("precio_unitario"),
                    rs.getBigDecimal("importe_venta"), rs.getBigDecimal("op_gravada"),
                    rs.getBigDecimal("igv"), rs.getBigDecimal("importe_total")});
        List<LineaDetalle> completas = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Object[] f = filas.get(i);
            LineaDetalle original = lineas.get(i);
            completas.add(new LineaDetalle((String) f[0], original.descripcion(),
                    original.unidadMedida(), (BigDecimal) f[1], (BigDecimal) f[2],
                    (BigDecimal) f[3], original.descuento() == null ? BigDecimal.ZERO
                            : original.descuento(), (BigDecimal) f[4]));
        }
        Object[] t = filas.get(0);
        return new Calculo(completas, (BigDecimal) t[5], (BigDecimal) t[6], (BigDecimal) t[7]);
    }

    /**
     * @param serie serie
     * @return siguiente correlativo libre de la serie ({@code fn_siguiente_numero})
     * @throws AccesoDatosException si la consulta falla
     */
    public int siguienteNumero(String serie) throws AccesoDatosException {
        return bd.consultarUno("SELECT fn_siguiente_numero(?)", ps -> ps.setString(1, serie),
                rs -> rs.getInt(1)).orElse(1);
    }

    /**
     * Registra un comprobante completo en una sola transacción
     * ({@code sp_insertar_comprobante}): cabecera, cliente (lo crea si no
     * existe), líneas (crea los productos nuevos) y referencias.
     *
     * @param c comprobante; {@code cliente} nulo significa «CLIENTES VARIOS»
     * @throws AccesoDatosException si la base rechaza el comprobante; en ese
     *                              caso no se registra nada
     */
    public void insertar(Comprobante c) throws AccesoDatosException {
        bd.ejecutar("CALL sp_insertar_comprobante(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)", ps -> fijar(ps, c));
    }

    /**
     * Reemplaza la cabecera, el cliente, las líneas y las referencias de un
     * comprobante vigente en una sola transacción ({@code sp_modificar_comprobante}).
     *
     * @param c comprobante con los datos nuevos; serie y número lo identifican
     * @throws AccesoDatosException si no existe o la base rechaza los datos
     */
    public void modificar(Comprobante c) throws AccesoDatosException {
        bd.ejecutar("CALL sp_modificar_comprobante(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)", ps -> fijar(ps, c));
    }

    /**
     * Borrado lógico de un comprobante ({@code sp_eliminar_comprobante}).
     *
     * @param serie  serie
     * @param numero correlativo
     * @throws AccesoDatosException si no existe un comprobante vigente
     */
    public void eliminar(String serie, int numero) throws AccesoDatosException {
        bd.ejecutar("CALL sp_eliminar_comprobante(?, ?)", ps -> {
            ps.setString(1, serie);
            ps.setInt(2, numero);
        });
    }

    /**
     * Deshace el borrado lógico de un comprobante ({@code sp_restaurar_comprobante}).
     *
     * @param serie  serie
     * @param numero correlativo
     * @throws AccesoDatosException si no existe un comprobante eliminado
     */
    public void restaurar(String serie, int numero) throws AccesoDatosException {
        bd.ejecutar("CALL sp_restaurar_comprobante(?, ?)", ps -> {
            ps.setString(1, serie);
            ps.setInt(2, numero);
        });
    }

    private static void fijar(PreparedStatement ps, Comprobante c) throws SQLException {
        Comprobante.Importes i = c.importes();
        ps.setString(1, c.serie());
        ps.setInt(2, c.numero());
        ps.setDate(3, c.fechaEmision() == null ? null : Date.valueOf(c.fechaEmision()));
        if (c.fechaVencimiento() == null) {
            ps.setNull(4, Types.DATE);
        } else {
            ps.setDate(4, Date.valueOf(c.fechaVencimiento()));
        }
        ps.setString(5, c.codigoMoneda());
        ps.setString(6, c.formaPago());
        ps.setString(7, c.medioPago());
        ps.setString(8, c.observacion());
        BigDecimal[] importes = {i.opGravada(), i.opExonerada(), i.opInafecta(), i.opGratuita(),
            i.isc(), i.igv(), i.otrosCargos(), i.otrosTributos(), i.montoRedondeo(),
            i.importeTotal(), i.percepcion()};
        for (int k = 0; k < importes.length; k++) {
            if (importes[k] == null) {
                ps.setNull(9 + k, Types.NUMERIC);
            } else {
                ps.setBigDecimal(9 + k, importes[k]);
            }
        }
        Cliente cl = c.cliente();
        ps.setString(20, cl == null ? null : cl.tipoDocumento());
        ps.setString(21, cl == null ? null : cl.numeroDocumento());
        ps.setString(22, cl == null ? null : cl.nombreRazonSocial());
        ps.setString(23, cl == null ? null : cl.direccion());
        ps.setString(24, Json.lineas(c.lineas()));
        ps.setString(25, Json.referencias(c.referencias() == null ? List.of() : c.referencias()));
    }
}
