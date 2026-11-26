package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.modelo.Comprobante;
import sermed.comprobantes.modelo.ComprobanteResumen;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Serie;

/**
 * Pestaña de comprobantes de un periodo: consulta, registro, modificación,
 * borrado lógico y restauración. Un doble clic, o el botón «Ver detalle»,
 * abre el comprobante completo. Con «Ver eliminados» la lista muestra los
 * comprobantes borrados del periodo, que pueden restaurarse.
 */
public class PanelComprobantes extends JPanel {

    private final ComprobanteDAO dao;
    private final ClienteDAO clientes;
    private final CatalogoDAO catalogo;
    private final JCheckBox chkEliminados = new JCheckBox("Ver eliminados");
    private final JButton btnNuevo = new JButton("Nuevo");
    private final JButton btnModificar = new JButton("Modificar");
    private final JButton btnEliminar = new JButton("Eliminar");
    private final JButton btnRestaurar = new JButton("Restaurar");
    private final JTextField txtDesde = new JTextField(9);
    private final JTextField txtHasta = new JTextField(9);
    private final JLabel lblTotal = new JLabel(" ");
    private final ModeloTabla<ComprobanteResumen> modelo = new ModeloTabla<>(List.of(
            new ModeloTabla.Columna<>("Comprobante", ComprobanteResumen::numeracion, 110),
            new ModeloTabla.Columna<>("Tipo", ComprobanteResumen::tipoComprobante, 190),
            new ModeloTabla.Columna<>("Fecha de emisión", c -> Formato.fecha(c.fechaEmision()), 110),
            new ModeloTabla.Columna<>("Cliente", ComprobanteResumen::cliente, 340),
            new ModeloTabla.Columna<>("Importe total", c -> Formato.importe(c.importeTotal()), 110)));
    private final JTable tabla = Tablas.crear(modelo, 4);

    /**
     * @param dao      acceso a los comprobantes
     * @param clientes acceso a los clientes, para el formulario
     * @param catalogo acceso a las series y monedas, para el formulario
     */
    public PanelComprobantes(ComprobanteDAO dao, ClienteDAO clientes, CatalogoDAO catalogo) {
        super(new BorderLayout(8, 8));
        this.dao = dao;
        this.clientes = clientes;
        this.catalogo = catalogo;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        LocalDate hoy = LocalDate.now();
        txtDesde.setText(Formato.fecha(hoy.withDayOfMonth(1)));
        txtHasta.setText(Formato.fecha(hoy));

        JButton btnBuscar = new JButton("Buscar");
        JButton btnDetalle = new JButton("Ver detalle");
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(new JLabel("Desde (dd/mm/aaaa):"));
        barra.add(txtDesde);
        barra.add(new JLabel("Hasta:"));
        barra.add(txtHasta);
        barra.add(btnBuscar);
        barra.add(chkEliminados);
        barra.add(btnDetalle);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.add(btnNuevo);
        acciones.add(btnModificar);
        acciones.add(btnEliminar);
        acciones.add(btnRestaurar);
        btnRestaurar.setEnabled(false);
        JPanel norte = new JPanel(new BorderLayout());
        norte.add(barra, BorderLayout.NORTH);
        norte.add(acciones, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblTotal, BorderLayout.SOUTH);

        btnBuscar.addActionListener(e -> buscar());
        btnDetalle.addActionListener(e -> verDetalle());
        chkEliminados.addActionListener(e -> {
            boolean ver = chkEliminados.isSelected();
            btnRestaurar.setEnabled(ver);
            btnModificar.setEnabled(!ver);
            btnEliminar.setEnabled(!ver);
            btnDetalle.setEnabled(!ver);
            buscar();
        });
        btnNuevo.addActionListener(e -> editar(null));
        btnModificar.addActionListener(e -> modificar());
        btnEliminar.addActionListener(e -> eliminar());
        btnRestaurar.addActionListener(e -> restaurar());
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    verDetalle();
                }
            }
        });
    }

    /**
     * Fija el periodo y busca (lo usa la ventana principal al iniciar).
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     */
    public void buscar(LocalDate desde, LocalDate hasta) {
        txtDesde.setText(Formato.fecha(desde));
        txtHasta.setText(Formato.fecha(hasta));
        buscar();
    }

    /** Busca los comprobantes del periodo ingresado. */
    public void buscar() {
        LocalDate desde;
        LocalDate hasta;
        try {
            desde = LocalDate.parse(txtDesde.getText().trim(), Formato.FECHA);
            hasta = LocalDate.parse(txtHasta.getText().trim(), Formato.FECHA);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Las fechas deben tener el formato dd/mm/aaaa.",
                    "Fecha no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (hasta.isBefore(desde)) {
            JOptionPane.showMessageDialog(this, "La fecha final es anterior a la inicial.",
                    "Periodo no válido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean eliminados = chkEliminados.isSelected();
        Tarea.ejecutar(this, () -> eliminados ? dao.eliminados(desde, hasta)
                : dao.porPeriodo(desde, hasta), lista -> {
            modelo.setFilas(lista);
            BigDecimal total = lista.stream().map(ComprobanteResumen::importeTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblTotal.setText(lista.size() + (eliminados ? " comprobante(s) eliminado(s)"
                    : " comprobante(s)") + "; suma de los importes totales: "
                    + Formato.importe(total));
        });
    }

    private void verDetalle() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un comprobante de la lista.",
                    "Ver detalle", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ComprobanteResumen c = modelo.getFila(tabla.convertRowIndexToModel(fila));
        Tarea.ejecutar(this, () -> dao.buscar(c.serie(), c.numero()), encontrado -> {
            if (encontrado.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El comprobante ya no existe.",
                        "Ver detalle", JOptionPane.WARNING_MESSAGE);
                return;
            }
            new DialogoComprobante(SwingUtilities.getWindowAncestor(this), encontrado.get())
                    .setVisible(true);
        });
    }

    private ComprobanteResumen seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un comprobante de la lista.");
            return null;
        }
        return modelo.getFila(tabla.convertRowIndexToModel(fila));
    }

    private void editar(Comprobante c) {
        Tarea.ejecutar(this, () -> new Object[] {catalogo.series(), catalogo.monedas()}, cat -> {
            @SuppressWarnings("unchecked")
            List<Serie> series = (List<Serie>) cat[0];
            @SuppressWarnings("unchecked")
            List<Moneda> monedas = (List<Moneda>) cat[1];
            DialogoEdicionComprobante d = new DialogoEdicionComprobante(
                    SwingUtilities.getWindowAncestor(this), dao, clientes, series, monedas, c);
            d.setVisible(true);
            if (d.guardado()) {
                buscar();
            }
        });
    }

    private void modificar() {
        ComprobanteResumen r = seleccionado();
        if (r == null) {
            return;
        }
        Tarea.ejecutar(this, () -> dao.buscar(r.serie(), r.numero()), c -> {
            if (c.isEmpty()) {
                JOptionPane.showMessageDialog(this, "El comprobante ya no existe.");
                return;
            }
            editar(c.get());
        });
    }

    private void eliminar() {
        ComprobanteResumen r = seleccionado();
        if (r == null || !Formulario.confirmar(this, "¿Eliminar el comprobante " + r.numeracion()
                + "?\nSe conservará con sus líneas y podrá restaurarse desde «Ver eliminados».")) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            dao.eliminar(r.serie(), r.numero());
            return true;
        }, ok -> buscar());
    }

    private void restaurar() {
        ComprobanteResumen r = seleccionado();
        if (r == null || !Formulario.confirmar(this, "¿Restaurar el comprobante " + r.numeracion() + "?")) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            dao.restaurar(r.serie(), r.numero());
            return true;
        }, ok -> buscar());
    }
}
