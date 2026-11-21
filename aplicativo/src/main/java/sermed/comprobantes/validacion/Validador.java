package sermed.comprobantes.validacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.ProductoServicio;
import sermed.comprobantes.modelo.Referencia;
import sermed.comprobantes.modelo.Serie;

/**
 * Valida los datos antes de enviarlos a la base de datos, con las mismas
 * reglas que sus restricciones y disparadores (diccionario del modelo
 * físico). Así el usuario recibe todos los errores de un formulario a la
 * vez; la base de datos sigue siendo la última barrera.
 */
public final class Validador {

    /** Tipos de documento relacionado aceptados ({@code ck_documento_relacionado_tipo}). */
    public static final List<String> TIPOS_RELACIONADOS = List.of("Guía de remisión", "Cotización");

    /** Tipos de comprobante aceptados ({@code ck_serie_comprobante_tipo}). */
    public static final List<String> TIPOS_COMPROBANTE =
            List.of("Boleta de venta electrónica", "Factura electrónica");

    private Validador() { }

    /**
     * @param c cliente (se ignora el número de comprobantes)
     * @return errores encontrados; vacía si es válido
     */
    public static List<String> cliente(Cliente c) {
        List<String> e = new ArrayList<>();
        documento(c.tipoDocumento(), c.numeroDocumento(), e);
        texto(c.nombreRazonSocial(), "El nombre o razón social", 200, true, e);
        texto(c.direccion(), "La dirección", 250, false, e);
        return e;
    }

    /**
     * @param p producto o servicio
     * @return errores encontrados; vacía si es válido
     */
    public static List<String> producto(ProductoServicio p) {
        List<String> e = new ArrayList<>();
        texto(p.codigo(), "El código", 30, true, e);
        texto(p.descripcion(), "La descripción", 500, true, e);
        texto(p.unidadMedida(), "La unidad de medida", 10, true, e);
        return e;
    }

    /**
     * @param s serie de comprobante
     * @return errores encontrados; vacía si es válida
     */
    public static List<String> serie(Serie s) {
        List<String> e = new ArrayList<>();
        if (s.serie() == null || !s.serie().trim().toUpperCase().matches("[A-Z0-9]{4}")) {
            e.add("La serie debe tener 4 letras mayúsculas o dígitos, como F002.");
        }
        if (!TIPOS_COMPROBANTE.contains(s.tipoComprobante())) {
            e.add("Seleccione el tipo de comprobante.");
        }
        return e;
    }

    /**
     * @param m moneda
     * @return errores encontrados; vacía si es válida
     */
    public static List<String> moneda(Moneda m) {
        List<String> e = new ArrayList<>();
        if (m.codigo() == null || !m.codigo().trim().toUpperCase().matches("[A-Z]{3}")) {
            e.add("El código de moneda debe tener 3 letras, como USD.");
        }
        texto(m.nombre(), "El nombre de la moneda", 30, true, e);
        return e;
    }

    /**
     * Valida un comprobante completo: cabecera, cliente, importes, líneas y
     * referencias, incluidas las reglas de los disparadores (los importes
     * deben sumar el total y las líneas, redondeadas al céntimo, también).
     *
     * @param c comprobante
     * @return errores encontrados; vacía si es válido
     */
    public static List<String> comprobante(Comprobante c) {
        List<String> e = new ArrayList<>();
        if (c.serie() == null || !c.serie().matches("[A-Z0-9]{4}")) {
            e.add("La serie debe tener 4 letras mayúsculas o dígitos.");
        }
        if (c.numero() < 1 || c.numero() > 99_999_999) {
            e.add("El número del comprobante debe estar entre 1 y 99999999.");
        }
        if (c.fechaEmision() == null) {
            e.add("La fecha de emisión es obligatoria.");
        } else if (c.fechaVencimiento() != null && c.fechaVencimiento().isBefore(c.fechaEmision())) {
            e.add("La fecha de vencimiento no puede ser anterior a la fecha de emisión.");
        }
        if (c.codigoMoneda() == null || !c.codigoMoneda().matches("[A-Z]{3}")) {
            e.add("Seleccione la moneda.");
        }
        texto(c.formaPago(), "La forma de pago", 20, false, e);
        texto(c.medioPago(), "El medio de pago", 30, false, e);
        texto(c.observacion(), "La observación", 250, false, e);

        if (c.cliente() != null) {
            documento(c.cliente().tipoDocumento(), c.cliente().numeroDocumento(), e);
            texto(c.cliente().nombreRazonSocial(), "El nombre del cliente", 200, false, e);
            texto(c.cliente().direccion(), "La dirección del cliente", 250, false, e);
        }

        Comprobante.Importes i = c.importes();
        importe(i.opGravada(), "Op. gravada", true, false, 12, 2, e);
        importe(i.opExonerada(), "Op. exonerada", false, false, 12, 2, e);
        importe(i.opInafecta(), "Op. inafecta", false, false, 12, 2, e);
        importe(i.opGratuita(), "Op. gratuita", false, false, 12, 2, e);
        importe(i.isc(), "ISC", false, false, 12, 2, e);
        importe(i.igv(), "IGV", true, false, 12, 2, e);
        importe(i.otrosCargos(), "Otros cargos", false, false, 12, 2, e);
        importe(i.otrosTributos(), "Otros tributos", false, false, 12, 2, e);
        importe(i.montoRedondeo(), "Redondeo", false, true, 12, 2, e);
        importe(i.importeTotal(), "Importe total", true, false, 12, 2, e);
        importe(i.percepcion(), "Percepción", false, false, 12, 2, e);

        if (c.lineas() == null || c.lineas().isEmpty()) {
            e.add("El comprobante debe tener al menos una línea de detalle.");
        } else {
            Set<String> codigos = new HashSet<>();
            int n = 1;
            for (LineaDetalle l : c.lineas()) {
                String p = "Línea " + n++ + ": ";
                if (l.codigo() == null || l.codigo().isBlank()) {
                    e.add(p + "falta el código.");
                } else if (!codigos.add(l.codigo().trim().toUpperCase())) {
                    e.add(p + "el código " + l.codigo() + " se repite en otra línea.");
                }
                texto(l.codigo(), p + "el código", 30, false, e);
                texto(l.descripcion(), p + "la descripción", 500, false, e);
                texto(l.unidadMedida(), p + "la unidad", 10, false, e);
                if (l.cantidad() == null || l.cantidad().signum() <= 0) {
                    e.add(p + "la cantidad debe ser mayor que cero.");
                } else {
                    importe(l.cantidad(), p + "cantidad", true, false, 12, 2, e);
                }
                importe(l.valorUnitario(), p + "valor unitario", true, false, 18, 7, e);
                importe(l.precioUnitario(), p + "precio unitario", false, false, 18, 7, e);
                importe(l.descuento(), p + "descuento", false, false, 18, 7, e);
                importe(l.importeVenta(), p + "importe de venta", true, false, 18, 7, e);
            }
        }

        if (c.referencias() != null) {
            Set<String> vistas = new HashSet<>();
            for (Referencia r : c.referencias()) {
                if (!TIPOS_RELACIONADOS.contains(r.tipoRelacionado())) {
                    e.add("El documento relacionado debe ser una guía de remisión o una cotización.");
                }
                texto(r.numeroRelacionado(), "El número del documento relacionado", 30, true, e);
                if (r.numeroRelacionado() != null
                        && !vistas.add(r.tipoRelacionado() + "|" + r.numeroRelacionado().trim().toUpperCase())) {
                    e.add("El documento " + r.numeroRelacionado() + " se cita dos veces.");
                }
            }
        }

        if (e.isEmpty()) {
            cuadre(c, e);
        }
        return e;
    }

    /** Reglas de los disparadores tr_comprobante_importes y tr_comprobante_lineas_total. */
    private static void cuadre(Comprobante c, List<String> e) {
        Comprobante.Importes i = c.importes();
        BigDecimal suma = i.opGravada().add(cero(i.opExonerada())).add(cero(i.opInafecta()))
                .add(i.igv()).add(cero(i.isc())).add(cero(i.otrosCargos()))
                .add(cero(i.otrosTributos())).add(cero(i.montoRedondeo()));
        if (suma.compareTo(i.importeTotal()) != 0) {
            e.add("Las operaciones y los tributos suman " + suma.toPlainString()
                    + ", pero el importe total es " + i.importeTotal().toPlainString() + ".");
        }
        BigDecimal lineas = c.lineas().stream().map(LineaDetalle::importeVenta)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (lineas.compareTo(i.importeTotal()) != 0) {
            e.add("Las líneas suman " + lineas.toPlainString() + ", pero el importe total es "
                    + i.importeTotal().toPlainString() + ".");
        }
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void documento(String tipo, String numero, List<String> e) {
        if (!"DNI".equals(tipo) && !"RUC".equals(tipo)) {
            e.add("El tipo de documento debe ser DNI o RUC.");
        } else if (numero == null
                || !numero.matches("DNI".equals(tipo) ? "[0-9]{8}" : "[0-9]{11}")) {
            e.add("El número de documento debe tener 8 dígitos si es DNI y 11 si es RUC.");
        }
    }

    private static void texto(String valor, String campo, int maximo, boolean obligatorio,
                              List<String> e) {
        if (valor == null || valor.isBlank()) {
            if (obligatorio) {
                e.add(campo + ": dato obligatorio.");
            }
        } else if (valor.trim().length() > maximo) {
            e.add(campo + " admite como máximo " + maximo + " caracteres.");
        }
    }

    /**
     * Importe con la precisión de NUMERIC(p,d): hasta p-d dígitos enteros y
     * d decimales; no negativo salvo que se indique.
     */
    private static void importe(BigDecimal valor, String campo, boolean obligatorio,
                                boolean admiteNegativo, int precision, int escala,
                                List<String> e) {
        if (valor == null) {
            if (obligatorio) {
                e.add(campo + ": el importe es obligatorio.");
            }
            return;
        }
        if (!admiteNegativo && valor.signum() < 0) {
            e.add(campo + ": no puede ser negativo.");
        }
        BigDecimal v = valor.stripTrailingZeros();
        int decimales = Math.max(v.scale(), 0);
        int enteros = v.precision() - v.scale();
        if (decimales > escala || enteros > precision - escala) {
            e.add(campo + ": admite hasta " + (precision - escala) + " dígitos enteros y "
                    + escala + " decimales.");
        }
    }
}
