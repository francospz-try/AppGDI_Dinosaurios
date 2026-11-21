package sermed.comprobantes.datos;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Los doce reportes del aplicativo. Cada uno corresponde a una función de
 * {@code scripts/06_reportes.sql} y a una consulta de
 * {@code scripts/03_consultas.sql}, y responde a una necesidad de la
 * funcionalidad de registro de comprobantes.
 */
public enum Reporte {

    /** Comprobantes de un periodo (C01). */
    R01("Comprobantes de un periodo",
            "Ubicar los comprobantes emitidos entre dos fechas, de todas las series o de una,"
            + " para revisarlos.",
            "fn_reporte_periodo", true,
            Parametro.fecha("Desde", Parametro::inicioDeMes),
            Parametro.fecha("Hasta", LocalDate::now),
            Parametro.serie("Serie", false)),
    /** Detalle de un comprobante (C02). */
    R02("Detalle de un comprobante",
            "Consultar un comprobante tal como fue registrado: cliente, productos, cantidades"
            + " e importes de cada línea.",
            "fn_reporte_detalle", true,
            Parametro.serie("Serie", true),
            Parametro.entero("Número", null)),
    /** Comprobantes de un cliente (C03). */
    R03("Comprobantes de un cliente",
            "Reunir las operaciones de un mismo cliente, de la más reciente a la más antigua,"
            + " con el importe acumulado.",
            "fn_reporte_cliente", true,
            Parametro.texto("DNI o RUC", true)),
    /** Comprobantes que citan un documento relacionado (C04). */
    R04("Comprobantes que citan un documento",
            "Encontrar qué comprobante citó una guía de remisión o una cotización; basta con el"
            + " comienzo del número.",
            "fn_reporte_documento", true,
            Parametro.tipoRelacionado("Tipo"),
            Parametro.texto("Número o comienzo", false)),
    /** Ventas mensuales por tipo de comprobante (C05). */
    R05("Ventas mensuales por tipo de comprobante",
            "Resumir por mes los comprobantes y los importes registrados en un año.",
            "fn_reporte_ventas_mensuales", true,
            Parametro.entero("Año", () -> LocalDate.now().getYear())),
    /** Productos o servicios con mayor importe vendido (C06). */
    R06("Productos y servicios más vendidos",
            "Reunir las ventas de cada producto o servicio en un periodo y ver cuáles"
            + " concentran el importe vendido.",
            "fn_reporte_top_productos", true,
            Parametro.fecha("Desde", Parametro::inicioDeAnio),
            Parametro.fecha("Hasta", LocalDate::now),
            Parametro.entero("Cuántos", () -> 10)),
    /** Clientes con mayor importe registrado (C07). */
    R07("Clientes con mayor importe",
            "Identificar a los clientes con más compras en un periodo, con su primera y su"
            + " última compra.",
            "fn_reporte_top_clientes", true,
            Parametro.fecha("Desde", Parametro::inicioDeAnio),
            Parametro.fecha("Hasta", LocalDate::now),
            Parametro.entero("Cuántos", () -> 10)),
    /** Cuadre de importes (C08). */
    R08("Cuadre de importes",
            "Contrastar que las operaciones y los tributos, y también las líneas, sumen el"
            + " importe total de cada comprobante.",
            "fn_reporte_cuadre", true,
            Parametro.fecha("Desde", Parametro::inicioDeMes),
            Parametro.fecha("Hasta", LocalDate::now),
            Parametro.booleano("Solo los que no cuadran")),
    /** Diferencia entre las líneas y el total por serie (C09). */
    R09("Diferencia entre líneas y total por serie",
            "Comprobar, por serie, cuánto difiere la suma de las líneas del importe total"
            + " impreso (los 7 decimales de EB01 dejan diferencias menores que un céntimo).",
            "fn_reporte_diferencia_lineas", false),
    /** Evolución del precio de un producto (C10). */
    R10("Evolución del precio de un producto",
            "Comparar, año por año, los precios con que se vendió un mismo producto o servicio.",
            "fn_reporte_precio_producto", true,
            Parametro.texto("Código", true)),
    /** Huecos en la numeración (C11). */
    R11("Huecos en la numeración",
            "Detectar números de cada serie sin comprobante vigente, que aún no se registraron"
            + " o que fueron eliminados.",
            "fn_reporte_numeracion", false,
            Parametro.serie("Serie", false)),
    /** Clientes sin compras en el año (C12). */
    R12("Clientes sin compras en el año",
            "Identificar a los clientes que compraron el año anterior y no en el año indicado.",
            "fn_reporte_clientes_inactivos", true,
            Parametro.entero("Año", () -> LocalDate.now().getYear()));

    /** Tipo de dato de un parámetro, que determina el campo del formulario. */
    public enum Tipo {
        /** Fecha en dd/mm/aaaa. */
        FECHA,
        /** Texto libre. */
        TEXTO,
        /** Número entero. */
        ENTERO,
        /** Serie de comprobante, elegida de la lista. */
        SERIE,
        /** Guía de remisión o cotización. */
        TIPO_RELACIONADO,
        /** Sí o no. */
        BOOLEANO
    }

    /**
     * Parámetro de un reporte.
     *
     * @param etiqueta    texto que se muestra junto al campo
     * @param tipo        tipo de dato
     * @param obligatorio si debe indicarse para ejecutar el reporte
     * @param inicial     valor inicial del campo, o {@code null}
     */
    public record Parametro(String etiqueta, Tipo tipo, boolean obligatorio,
                            Supplier<Object> inicial) {

        static Parametro fecha(String etiqueta, Supplier<Object> inicial) {
            return new Parametro(etiqueta, Tipo.FECHA, true, inicial);
        }

        static Parametro entero(String etiqueta, Supplier<Object> inicial) {
            return new Parametro(etiqueta, Tipo.ENTERO, true, inicial);
        }

        static Parametro texto(String etiqueta, boolean obligatorio) {
            return new Parametro(etiqueta, Tipo.TEXTO, obligatorio, null);
        }

        static Parametro serie(String etiqueta, boolean obligatorio) {
            return new Parametro(etiqueta, Tipo.SERIE, obligatorio, null);
        }

        static Parametro tipoRelacionado(String etiqueta) {
            return new Parametro(etiqueta, Tipo.TIPO_RELACIONADO, false, null);
        }

        static Parametro booleano(String etiqueta) {
            return new Parametro(etiqueta, Tipo.BOOLEANO, true, () -> Boolean.FALSE);
        }

        static Object inicioDeMes() {
            return LocalDate.now().withDayOfMonth(1);
        }

        static Object inicioDeAnio() {
            return LocalDate.now().withDayOfYear(1);
        }
    }

    /** Palabras que se muestran con tilde en los encabezados de las columnas. */
    private static final Map<String, String> TILDES = Map.ofEntries(
            Map.entry("numero", "número"), Map.entry("emision", "emisión"),
            Map.entry("codigo", "código"), Map.entry("descripcion", "descripción"),
            Map.entry("ultima", "última"), Map.entry("anio", "año"),
            Map.entry("participacion", "participación"), Map.entry("lineas", "líneas"),
            Map.entry("minimo", "mínimo"), Map.entry("maximo", "máximo"),
            Map.entry("centimo", "céntimo"),
            Map.entry("op", "op."), Map.entry("igv", "IGV"), Map.entry("pct", "(%)"));

    private final String titulo;
    private final String necesidad;
    private final String funcion;
    private final boolean conJoin;
    private final List<Parametro> parametros;

    Reporte(String titulo, String necesidad, String funcion, boolean conJoin,
            Parametro... parametros) {
        this.titulo = titulo;
        this.necesidad = necesidad;
        this.funcion = funcion;
        this.conJoin = conJoin;
        this.parametros = List.of(parametros);
    }

    /** @return título del reporte */
    public String titulo() {
        return titulo;
    }

    /** @return necesidad de la funcionalidad a la que responde */
    public String necesidad() {
        return necesidad;
    }

    /** @return nombre de la función de la base de datos */
    public String funcion() {
        return funcion;
    }

    /** @return si la función combina tablas con JOIN */
    public boolean conJoin() {
        return conJoin;
    }

    /** @return parámetros, en el orden de la función */
    public List<Parametro> parametros() {
        return parametros;
    }

    /**
     * Convierte el nombre de una columna de la función (por ejemplo,
     * {@code fecha_emision}) en un encabezado legible («Fecha emisión»).
     *
     * @param columna nombre de la columna
     * @return encabezado
     */
    public static String encabezado(String columna) {
        StringBuilder sb = new StringBuilder();
        for (String palabra : columna.split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(TILDES.getOrDefault(palabra, palabra));
        }
        return Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
    }

    @Override
    public String toString() {
        return name() + " " + titulo;
    }
}
