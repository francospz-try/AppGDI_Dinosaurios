package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import sermed.comprobantes.datos.ProductoDAO;
import sermed.comprobantes.modelo.ProductoServicio;

/** Pestaña de productos y servicios: búsqueda, registro, modificación y baja lógica. */
public class PanelProductos extends JPanel {

    private static final int LIMITE = 500;

    private final ProductoDAO dao;
    private final JTextField txtBuscar = new JTextField(28);
    private final JLabel lblTotal = new JLabel(" ");
    private final ModeloTabla<ProductoServicio> modelo = new ModeloTabla<>(List.of(
            new ModeloTabla.Columna<>("Código", ProductoServicio::codigo, 90),
            new ModeloTabla.Columna<>("Descripción", ProductoServicio::descripcion, 560),
            new ModeloTabla.Columna<>("Unidad", ProductoServicio::unidadMedida, 70),
            new ModeloTabla.Columna<>("Líneas vendidas", ProductoServicio::lineas, 110)));
    private final JTable tabla = Tablas.crear(modelo, 3);

    /** @param dao acceso a los productos */
    public PanelProductos(ProductoDAO dao) {
        super(new BorderLayout(8, 8));
        this.dao = dao;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnBuscar = new JButton("Buscar");
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(new JLabel("Código o descripción:"));
        barra.add(txtBuscar);
        barra.add(btnBuscar);
        add(barra, BorderLayout.NORTH);

        JButton btnNuevo = new JButton("Nuevo");
        JButton btnModificar = new JButton("Modificar");
        JButton btnEliminar = new JButton("Eliminar");
        barra.add(btnNuevo);
        barra.add(btnModificar);
        barra.add(btnEliminar);

        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblTotal, BorderLayout.SOUTH);

        btnBuscar.addActionListener(e -> buscar());
        txtBuscar.addActionListener(e -> buscar());
        btnNuevo.addActionListener(e -> editar(null));
        btnModificar.addActionListener(e -> {
            ProductoServicio p = seleccionado();
            if (p != null) {
                editar(p);
            }
        });
        btnEliminar.addActionListener(e -> eliminar());
    }

    private ProductoServicio seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un producto o servicio de la lista.");
            return null;
        }
        return modelo.getFila(tabla.convertRowIndexToModel(fila));
    }

    private void editar(ProductoServicio p) {
        DialogoProducto d = new DialogoProducto(SwingUtilities.getWindowAncestor(this), dao, p);
        d.setVisible(true);
        if (d.guardado()) {
            buscar();
        }
    }

    private void eliminar() {
        ProductoServicio p = seleccionado();
        if (p == null || !Formulario.confirmar(this, "¿Dar de baja " + p.codigo() + " — "
                + p.descripcion() + "?\nLas " + p.lineas() + " líneas que lo vendieron lo conservarán.")) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            dao.eliminar(p.codigo());
            return true;
        }, ok -> buscar());
    }

    /** Ejecuta la búsqueda con el texto ingresado. */
    public void buscar() {
        String texto = txtBuscar.getText();
        Tarea.ejecutar(this, () -> dao.buscar(texto, LIMITE), productos -> {
            modelo.setFilas(productos);
            lblTotal.setText(productos.size() + " producto(s) o servicio(s)");
        });
    }
}
