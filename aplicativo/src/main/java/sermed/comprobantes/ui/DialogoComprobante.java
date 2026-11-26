package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.Referencia;

/**
 * Muestra un comprobante tal como fue registrado: cabecera, cliente, líneas
 * de detalle, documentos citados y resumen de importes. Los importes que el
 * formato no imprimió se dejan en blanco.
 */
public class DialogoComprobante extends JDialog {

    /**
     * @param padre       ventana dueña del diálogo
     * @param comprobante comprobante que se muestra
     */
    public DialogoComprobante(Window padre, Comprobante comprobante) {
        super(padre, comprobante.tipoComprobante() + " " + comprobante.numeracion(),
                ModalityType.APPLICATION_MODAL);
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contenido.add(cabecera(comprobante), BorderLayout.NORTH);

        ModeloTabla<LineaDetalle> mLineas = new ModeloTabla<>(List.of(
                new ModeloTabla.Columna<>("Código", LineaDetalle::codigo, 80),
                new ModeloTabla.Columna<>("Descripción", LineaDetalle::descripcion, 380),
                new ModeloTabla.Columna<>("Cantidad", l -> Formato.importe(l.cantidad()), 70),
                new ModeloTabla.Columna<>("Unidad", LineaDetalle::unidadMedida, 60),
                new ModeloTabla.Columna<>("V. unitario", l -> Formato.importe(l.valorUnitario()), 90),
                new ModeloTabla.Columna<>("P. unitario", l -> Formato.importe(l.precioUnitario()), 90),
                new ModeloTabla.Columna<>("Descuento", l -> Formato.importe(l.descuento()), 80),
                new ModeloTabla.Columna<>("Importe de venta", l -> Formato.importe(l.importeVenta()), 110)));
        mLineas.setFilas(comprobante.lineas());
        JTable tLineas = Tablas.crear(mLineas, 2, 4, 5, 6, 7);
        JScrollPane spLineas = new JScrollPane(tLineas);
        spLineas.setBorder(BorderFactory.createTitledBorder("Detalle"));
        spLineas.setPreferredSize(new Dimension(900, 150));

        ModeloTabla<Referencia> mRef = new ModeloTabla<>(List.of(
                new ModeloTabla.Columna<>("Documento citado", Referencia::tipoRelacionado, 180),
                new ModeloTabla.Columna<>("Número", Referencia::numeroRelacionado, 180)));
        mRef.setFilas(comprobante.referencias());
        JScrollPane spRef = new JScrollPane(Tablas.crear(mRef));
        spRef.setBorder(BorderFactory.createTitledBorder("Referencias"));
        spRef.setPreferredSize(new Dimension(420, 110));

        JPanel abajo = new JPanel(new BorderLayout(8, 8));
        abajo.add(spRef, BorderLayout.CENTER);
        abajo.add(importes(comprobante.importes()), BorderLayout.EAST);

        JPanel centro = new JPanel(new BorderLayout(8, 8));
        centro.add(spLineas, BorderLayout.CENTER);
        centro.add(abajo, BorderLayout.SOUTH);
        contenido.add(centro, BorderLayout.CENTER);

        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> dispose());
        JPanel botones = new JPanel();
        botones.add(cerrar);
        contenido.add(botones, BorderLayout.SOUTH);

        setContentPane(contenido);
        pack();
        setLocationRelativeTo(padre);
    }

    private static JPanel cabecera(Comprobante c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Comprobante"));
        String cliente = c.cliente() == null ? "CLIENTES VARIOS"
                : c.cliente().nombreRazonSocial() + " (" + c.cliente().tipoDocumento() + " "
                  + c.cliente().numeroDocumento() + ")";
        String direccion = c.cliente() == null ? "" : Formato.texto(c.cliente().direccion());
        Object[][] campos = {
            {"Tipo:", c.tipoComprobante(), "Serie y número:", c.numeracion()},
            {"Fecha de emisión:", Formato.fecha(c.fechaEmision()),
             "Fecha de vencimiento:", Formato.fecha(c.fechaVencimiento())},
            {"Moneda:", c.codigoMoneda() + " - " + c.nombreMoneda(),
             "Forma de pago:", Formato.texto(c.formaPago())},
            {"Medio de pago:", Formato.texto(c.medioPago()),
             "Observación:", Formato.texto(c.observacion())},
            {"Cliente:", cliente, "Dirección:", direccion},
        };
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        for (int fila = 0; fila < campos.length; fila++) {
            for (int col = 0; col < 4; col++) {
                g.gridx = col;
                g.gridy = fila;
                JLabel etiqueta = new JLabel(String.valueOf(campos[fila][col]));
                if (col % 2 == 0) {
                    etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD));
                }
                p.add(etiqueta, g);
            }
        }
        // Columna vacía que absorbe el ancho sobrante y alinea los datos a la izquierda
        g.gridx = 4;
        g.gridy = 0;
        g.weightx = 1;
        p.add(new JLabel(), g);
        return p;
    }

    private static JPanel importes(Comprobante.Importes i) {
        Object[][] filas = {
            {"Op. gravada", i.opGravada()}, {"Op. exonerada", i.opExonerada()},
            {"Op. inafecta", i.opInafecta()}, {"Op. gratuita", i.opGratuita()},
            {"ISC", i.isc()}, {"IGV", i.igv()}, {"Otros cargos", i.otrosCargos()},
            {"Otros tributos", i.otrosTributos()}, {"Redondeo", i.montoRedondeo()},
            {"Importe total", i.importeTotal()}, {"Percepción", i.percepcion()},
        };
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Importes"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(1, 6, 1, 6);
        for (int f = 0; f < filas.length; f++) {
            g.gridy = f;
            g.gridx = 0;
            g.anchor = GridBagConstraints.WEST;
            p.add(new JLabel((String) filas[f][0]), g);
            g.gridx = 1;
            g.anchor = GridBagConstraints.EAST;
            p.add(new JLabel(Formato.importe((BigDecimal) filas[f][1])), g);
        }
        return p;
    }
}
