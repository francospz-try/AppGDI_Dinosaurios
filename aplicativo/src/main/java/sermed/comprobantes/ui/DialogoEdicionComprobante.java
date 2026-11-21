package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.LineaDetalle;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Referencia;
import sermed.comprobantes.modelo.Serie;
import sermed.comprobantes.validacion.Validador;

/**
 * Formulario para registrar un comprobante completo o modificar uno
 * existente: cabecera, cliente, líneas de detalle, referencias e importes.
 * Al guardar, valida todo con las reglas de la base y llama a
 * {@code sp_insertar_comprobante} o {@code sp_modificar_comprobante}, que lo
 * escriben en una sola transacción.
 */
public class DialogoEdicionComprobante extends JDialog {

    private static final String VARIOS = "CLIENTES VARIOS";
    private static final String[] COLUMNAS_LINEA = {"Código", "Descripción (si es nuevo)",
        "Unidad (si es nuevo)", "Cantidad", "V. unitario", "P. unitario", "Descuento",
        "Importe de venta"};
    private static final String[] NOMBRES_IMPORTES = {"Op. gravada", "Op. exonerada",
        "Op. inafecta", "Op. gratuita", "ISC", "IGV", "Otros cargos", "Otros tributos",
        "Redondeo", "Importe total", "Percepción"};

    private final ComprobanteDAO dao;
    private final ClienteDAO clientes;
    private final boolean nuevo;

    private final JComboBox<Serie> cboSerie;
    private final JTextField txtNumero = new JTextField(9);
    private final JTextField txtEmision = new JTextField(9);
    private final JTextField txtVencimiento = new JTextField(9);
    private final JComboBox<Moneda> cboMoneda;
    private final JTextField txtFormaPago = new JTextField(10);
    private final JTextField txtMedioPago = new JTextField(10);
    private final JTextField txtObservacion = new JTextField(30);

    private final JComboBox<String> cboTipoCliente = new JComboBox<>(new String[] {VARIOS, "RUC", "DNI"});
    private final JTextField txtDocCliente = new JTextField(12);
    private final JTextField txtNombreCliente = new JTextField(30);
    private final JTextField txtDireccionCliente = new JTextField(30);

    private final DefaultTableModel lineas = new DefaultTableModel(COLUMNAS_LINEA, 0);
    private final DefaultTableModel referencias =
            new DefaultTableModel(new String[] {"Documento citado", "Número"}, 0);
    private final JTextField[] importes = new JTextField[NOMBRES_IMPORTES.length];
    private final JTable tablaLineas = new JTable(lineas);
    private final JTable tablaReferencias = new JTable(referencias);
    private boolean guardado;

    /**
     * @param padre       ventana dueña
     * @param dao         acceso a los comprobantes
     * @param clientes    acceso a los clientes (para buscar un cliente existente)
     * @param series      series registradas
     * @param monedas     monedas registradas
     * @param comprobante comprobante que se modifica, o {@code null} para uno nuevo
     */
    public DialogoEdicionComprobante(Window padre, ComprobanteDAO dao, ClienteDAO clientes,
                                     List<Serie> series, List<Moneda> monedas,
                                     Comprobante comprobante) {
        super(padre, comprobante == null ? "Registrar comprobante"
                : "Modificar " + comprobante.numeracion(), ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.clientes = clientes;
        this.nuevo = comprobante == null;
        cboSerie = new JComboBox<>(series.toArray(Serie[]::new));
        cboMoneda = new JComboBox<>(monedas.toArray(Moneda[]::new));
        for (int i = 0; i < importes.length; i++) {
            importes[i] = new JTextField(8);
            importes[i].setHorizontalAlignment(JTextField.RIGHT);
        }

        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel arriba = new JPanel(new GridLayout(1, 2, 8, 0));
        arriba.add(panelCabecera());
        arriba.add(panelCliente());
        contenido.add(arriba, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(8, 8));
        centro.add(panelLineas(), BorderLayout.CENTER);
        JPanel abajo = new JPanel(new BorderLayout(8, 8));
        abajo.add(panelReferencias(), BorderLayout.CENTER);
        abajo.add(panelImportes(), BorderLayout.EAST);
        centro.add(abajo, BorderLayout.SOUTH);
        contenido.add(centro, BorderLayout.CENTER);

        JButton guardar = new JButton("Guardar");
        JButton cancelar = new JButton("Cancelar");
        guardar.addActionListener(e -> guardar());
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel();
        botones.add(guardar);
        botones.add(cancelar);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);

        if (comprobante == null) {
            txtEmision.setText(Formato.fecha(LocalDate.now()));
            importes[1].setText("0.00");
            importes[2].setText("0.00");
            lineas.addRow(new Object[COLUMNAS_LINEA.length]);
            cboSerie.addActionListener(e -> sugerirNumero());
            sugerirNumero();
        } else {
            cargar(comprobante);
            cboSerie.setEnabled(false);
            txtNumero.setEditable(false);
        }
        pack();
        setLocationRelativeTo(padre);
    }

    private JPanel panelCabecera() {
        JButton siguiente = new JButton("Siguiente");
        siguiente.setToolTipText("Sugiere el siguiente correlativo libre de la serie");
        siguiente.addActionListener(e -> sugerirNumero());
        siguiente.setEnabled(nuevo);
        JPanel numero = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        numero.add(txtNumero);
        numero.add(siguiente);
        JPanel p = Formulario.campos("Serie:", cboSerie, "Número:", numero,
                "Fecha de emisión:", txtEmision, "Fecha de vencimiento:", txtVencimiento,
                "Moneda:", cboMoneda, "Forma de pago:", txtFormaPago,
                "Medio de pago:", txtMedioPago, "Observación:", txtObservacion);
        p.setBorder(BorderFactory.createTitledBorder("Comprobante"));
        return p;
    }

    private JPanel panelCliente() {
        JButton buscar = new JButton("Buscar");
        buscar.setToolTipText("Completa el nombre y la dirección de un cliente registrado");
        buscar.addActionListener(e -> buscarCliente());
        JPanel doc = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        doc.add(txtDocCliente);
        doc.add(buscar);
        cboTipoCliente.addActionListener(e -> {
            boolean identificado = !VARIOS.equals(cboTipoCliente.getSelectedItem());
            txtDocCliente.setEnabled(identificado);
            txtNombreCliente.setEnabled(identificado);
            txtDireccionCliente.setEnabled(identificado);
        });
        cboTipoCliente.setSelectedIndex(0);
        JPanel p = Formulario.campos("Tipo:", cboTipoCliente, "Número de documento:", doc,
                "Nombre o razón social:", txtNombreCliente, "Dirección:", txtDireccionCliente,
                " ", new JLabel("<html><i>Si el cliente no existe se registra; si existe, se "
                        + "actualizan su nombre y dirección con los indicados.</i></html>"));
        p.setBorder(BorderFactory.createTitledBorder("Cliente"));
        return p;
    }

    private JPanel panelLineas() {
        tablaLineas.setRowHeight(22);
        tablaLineas.getColumnModel().getColumn(1).setPreferredWidth(300);
        JButton agregar = new JButton("Agregar línea");
        JButton quitar = new JButton("Quitar línea");
        JButton calcular = new JButton("Calcular importes");
        calcular.setToolTipText("Calcula valores e importes a partir de la cantidad y el precio "
                + "unitario con IGV, con las reglas del formato de la serie");
        agregar.addActionListener(e -> lineas.addRow(new Object[COLUMNAS_LINEA.length]));
        quitar.addActionListener(e -> quitarFila(tablaLineas, lineas));
        calcular.addActionListener(e -> calcular());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(agregar);
        botones.add(quitar);
        botones.add(calcular);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Detalle"));
        JScrollPane sp = new JScrollPane(tablaLineas);
        sp.setPreferredSize(new Dimension(980, 130));
        p.add(sp, BorderLayout.CENTER);
        p.add(botones, BorderLayout.SOUTH);
        return p;
    }

    private JPanel panelReferencias() {
        tablaReferencias.setRowHeight(22);
        JComboBox<String> tipos = new JComboBox<>(Validador.TIPOS_RELACIONADOS.toArray(String[]::new));
        tablaReferencias.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(tipos));
        JButton agregar = new JButton("Agregar referencia");
        JButton quitar = new JButton("Quitar referencia");
        agregar.addActionListener(e -> referencias.addRow(new Object[] {"Guía de remisión", ""}));
        quitar.addActionListener(e -> quitarFila(tablaReferencias, referencias));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(agregar);
        botones.add(quitar);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Referencias"));
        JScrollPane sp = new JScrollPane(tablaReferencias);
        sp.setPreferredSize(new Dimension(400, 90));
        p.add(sp, BorderLayout.CENTER);
        p.add(botones, BorderLayout.SOUTH);
        return p;
    }

    private JPanel panelImportes() {
        JPanel p = new JPanel(new GridLayout(0, 4, 4, 2));
        p.setBorder(BorderFactory.createTitledBorder("Importes (en blanco si el formato no los imprime)"));
        for (int i = 0; i < importes.length; i++) {
            p.add(new JLabel(NOMBRES_IMPORTES[i] + ":", JLabel.RIGHT));
            p.add(importes[i]);
        }
        return p;
    }

    private static void quitarFila(JTable tabla, DefaultTableModel modelo) {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        int fila = tabla.getSelectedRow();
        if (fila >= 0) {
            modelo.removeRow(fila);
        }
    }

    private void sugerirNumero() {
        Serie s = (Serie) cboSerie.getSelectedItem();
        if (s == null) {
            return;
        }
        Tarea.ejecutar(this, () -> dao.siguienteNumero(s.serie()),
                n -> txtNumero.setText(String.valueOf(n)));
    }

    private void buscarCliente() {
        String doc = Formulario.texto(txtDocCliente.getText());
        if (doc == null) {
            return;
        }
        Tarea.ejecutar(this, () -> clientes.buscar(doc, 2), lista -> {
            Cliente c = lista.stream().filter(x -> x.numeroDocumento().equals(doc)).findFirst()
                    .orElse(null);
            if (c == null) {
                JOptionPane.showMessageDialog(this, "No hay un cliente vigente con ese documento; "
                        + "complete sus datos y se registrará al guardar.");
                return;
            }
            cboTipoCliente.setSelectedItem(c.tipoDocumento());
            txtNombreCliente.setText(c.nombreRazonSocial());
            txtDireccionCliente.setText(Formato.texto(c.direccion()));
        });
    }

    /** Calcula valores unitarios, importes de venta y resumen con fn_calcular_importes. */
    private void calcular() {
        detenerEdicion();
        List<String> errores = new ArrayList<>();
        List<LineaDetalle> base = new ArrayList<>();
        List<Integer> filas = new ArrayList<>();
        for (int f = 0; f < lineas.getRowCount(); f++) {
            if (filaVacia(lineas, f)) {
                continue;
            }
            String codigo = celda(lineas, f, 0);
            BigDecimal cantidad = Formulario.numero(celda(lineas, f, 3), "Línea " + (f + 1) + ", cantidad", errores);
            BigDecimal precio = Formulario.numero(celda(lineas, f, 5), "Línea " + (f + 1) + ", precio", errores);
            if (codigo == null || cantidad == null || precio == null) {
                errores.add("Línea " + (f + 1) + ": indique el código, la cantidad y el precio unitario.");
                continue;
            }
            base.add(new LineaDetalle(codigo, celda(lineas, f, 1), celda(lineas, f, 2), cantidad,
                    null, precio, BigDecimal.ZERO, null));
            filas.add(f);
        }
        if (base.isEmpty() && errores.isEmpty()) {
            errores.add("Agregue al menos una línea.");
        }
        if (Formulario.mostrarErrores(this, errores)) {
            return;
        }
        String serie = ((Serie) cboSerie.getSelectedItem()).serie();
        Tarea.ejecutar(this, () -> dao.calcularImportes(serie, base), k -> {
            for (int i = 0; i < k.lineas().size(); i++) {
                LineaDetalle l = k.lineas().get(i);
                int f = filas.get(i);
                lineas.setValueAt(Formato.importe(l.valorUnitario()), f, 4);
                lineas.setValueAt(Formato.importe(l.precioUnitario()), f, 5);
                lineas.setValueAt("0.00", f, 6);
                lineas.setValueAt(Formato.importe(l.importeVenta()), f, 7);
            }
            importes[0].setText(Formato.importe(k.opGravada()));
            importes[5].setText(Formato.importe(k.igv()));
            importes[9].setText(Formato.importe(k.importeTotal()));
            boolean sunat = serie.startsWith("E");
            importes[3].setText(sunat ? "" : "0.00");
            importes[10].setText(sunat ? "" : "0.00");
            for (int i : new int[] {4, 6, 7, 8}) {
                importes[i].setText(sunat ? "0.00" : "");
            }
        });
    }

    private void detenerEdicion() {
        if (tablaLineas.isEditing()) {
            tablaLineas.getCellEditor().stopCellEditing();
        }
        if (tablaReferencias.isEditing()) {
            tablaReferencias.getCellEditor().stopCellEditing();
        }
    }

    /** Una fila sin ningún dato (por ejemplo, agregada por error) se ignora. */
    private static boolean filaVacia(DefaultTableModel m, int fila) {
        for (int c = 0; c < m.getColumnCount(); c++) {
            if (celda(m, fila, c) != null) {
                return false;
            }
        }
        return true;
    }

    private static String celda(DefaultTableModel m, int fila, int columna) {
        Object v = m.getValueAt(fila, columna);
        return v == null ? null : Formulario.texto(v.toString());
    }

    private void cargar(Comprobante c) {
        cboSerie.setSelectedItem(series().stream().filter(s -> s.serie().equals(c.serie()))
                .findFirst().orElse(null));
        txtNumero.setText(String.valueOf(c.numero()));
        txtEmision.setText(Formato.fecha(c.fechaEmision()));
        txtVencimiento.setText(Formato.fecha(c.fechaVencimiento()));
        for (int i = 0; i < cboMoneda.getItemCount(); i++) {
            if (cboMoneda.getItemAt(i).codigo().equals(c.codigoMoneda())) {
                cboMoneda.setSelectedIndex(i);
            }
        }
        txtFormaPago.setText(Formato.texto(c.formaPago()));
        txtMedioPago.setText(Formato.texto(c.medioPago()));
        txtObservacion.setText(Formato.texto(c.observacion()));
        if (c.cliente() != null) {
            cboTipoCliente.setSelectedItem(c.cliente().tipoDocumento());
            txtDocCliente.setText(c.cliente().numeroDocumento());
            txtNombreCliente.setText(c.cliente().nombreRazonSocial());
            txtDireccionCliente.setText(Formato.texto(c.cliente().direccion()));
        }
        for (LineaDetalle l : c.lineas()) {
            lineas.addRow(new Object[] {l.codigo(), l.descripcion(), l.unidadMedida(),
                Formato.importe(l.cantidad()), Formato.importe(l.valorUnitario()),
                Formato.importe(l.precioUnitario()), Formato.importe(l.descuento()),
                Formato.importe(l.importeVenta())});
        }
        for (Referencia r : c.referencias()) {
            referencias.addRow(new Object[] {r.tipoRelacionado(), r.numeroRelacionado()});
        }
        Comprobante.Importes i = c.importes();
        BigDecimal[] v = {i.opGravada(), i.opExonerada(), i.opInafecta(), i.opGratuita(), i.isc(),
            i.igv(), i.otrosCargos(), i.otrosTributos(), i.montoRedondeo(), i.importeTotal(),
            i.percepcion()};
        for (int k = 0; k < v.length; k++) {
            importes[k].setText(Formato.importe(v[k]));
        }
    }

    private List<Serie> series() {
        List<Serie> l = new ArrayList<>();
        for (int i = 0; i < cboSerie.getItemCount(); i++) {
            l.add(cboSerie.getItemAt(i));
        }
        return l;
    }

    /** Reúne los datos del formulario, los valida y llama al procedimiento. */
    private void guardar() {
        detenerEdicion();
        List<String> errores = new ArrayList<>();
        int numero = 0;
        try {
            numero = Integer.parseInt(txtNumero.getText().trim());
        } catch (NumberFormatException e) {
            errores.add("El número del comprobante debe ser un entero.");
        }
        BigDecimal[] v = new BigDecimal[importes.length];
        for (int i = 0; i < importes.length; i++) {
            v[i] = Formulario.numero(importes[i].getText(), NOMBRES_IMPORTES[i], errores);
        }
        List<LineaDetalle> ls = new ArrayList<>();
        for (int f = 0; f < lineas.getRowCount(); f++) {
            if (filaVacia(lineas, f)) {
                continue;
            }
            String p = "Línea " + (f + 1) + ", ";
            ls.add(new LineaDetalle(celda(lineas, f, 0), celda(lineas, f, 1), celda(lineas, f, 2),
                    Formulario.numero(celda(lineas, f, 3), p + "cantidad", errores),
                    Formulario.numero(celda(lineas, f, 4), p + "valor unitario", errores),
                    Formulario.numero(celda(lineas, f, 5), p + "precio unitario", errores),
                    Formulario.numero(celda(lineas, f, 6), p + "descuento", errores),
                    Formulario.numero(celda(lineas, f, 7), p + "importe de venta", errores)));
        }
        List<Referencia> rs = new ArrayList<>();
        for (int f = 0; f < referencias.getRowCount(); f++) {
            if (celda(referencias, f, 1) == null) {
                continue;
            }
            rs.add(new Referencia(celda(referencias, f, 0), celda(referencias, f, 1)));
        }
        Cliente cliente = VARIOS.equals(cboTipoCliente.getSelectedItem()) ? null
                : new Cliente((String) cboTipoCliente.getSelectedItem(),
                        Formulario.texto(txtDocCliente.getText()),
                        Formulario.texto(txtNombreCliente.getText()),
                        Formulario.texto(txtDireccionCliente.getText()), 0);
        Serie serie = (Serie) cboSerie.getSelectedItem();
        Moneda moneda = (Moneda) cboMoneda.getSelectedItem();
        Comprobante c = new Comprobante(serie == null ? null : serie.serie(), numero, null,
                Formulario.fecha(txtEmision.getText(), "Fecha de emisión", errores),
                Formulario.fecha(txtVencimiento.getText(), "Fecha de vencimiento", errores),
                moneda == null ? null : moneda.codigo(), null,
                Formulario.texto(txtFormaPago.getText()), Formulario.texto(txtMedioPago.getText()),
                Formulario.texto(txtObservacion.getText()),
                new Comprobante.Importes(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10]),
                cliente, ls, rs);
        if (errores.isEmpty()) {
            errores.addAll(Validador.comprobante(c));
        }
        if (Formulario.mostrarErrores(this, errores)) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            if (nuevo) {
                dao.insertar(c);
            } else {
                dao.modificar(c);
            }
            return true;
        }, ok -> {
            guardado = true;
            dispose();
        });
    }

    /** @return verdadero si el comprobante se guardó */
    public boolean guardado() {
        return guardado;
    }
}
