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

import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.modelo.Cliente;

/** Pestaña de clientes: búsqueda, registro, modificación y baja (borrado lógico). */
public class PanelClientes extends JPanel {

    private static final int LIMITE = 500;

    private final ClienteDAO dao;
    private final JTextField txtBuscar = new JTextField(28);
    private final JLabel lblTotal = new JLabel(" ");
    private final ModeloTabla<Cliente> modelo = new ModeloTabla<>(List.of(
            new ModeloTabla.Columna<>("Tipo", Cliente::tipoDocumento, 50),
            new ModeloTabla.Columna<>("Número de documento", Cliente::numeroDocumento, 130),
            new ModeloTabla.Columna<>("Nombre o razón social", Cliente::nombreRazonSocial, 320),
            new ModeloTabla.Columna<>("Dirección", c -> Formato.texto(c.direccion()), 380),
            new ModeloTabla.Columna<>("Comprobantes", Cliente::comprobantes, 100)));
    private final JTable tabla = Tablas.crear(modelo, 4);

    /** @param dao acceso a los clientes */
    public PanelClientes(ClienteDAO dao) {
        super(new BorderLayout(8, 8));
        this.dao = dao;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnBuscar = new JButton("Buscar");
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(new JLabel("Documento o nombre:"));
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
            Cliente c = seleccionado();
            if (c != null) {
                editar(c);
            }
        });
        btnEliminar.addActionListener(e -> eliminar());
    }

    private Cliente seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un cliente de la lista.");
            return null;
        }
        return modelo.getFila(tabla.convertRowIndexToModel(fila));
    }

    private void editar(Cliente c) {
        DialogoCliente d = new DialogoCliente(SwingUtilities.getWindowAncestor(this), dao, c);
        d.setVisible(true);
        if (d.guardado()) {
            buscar();
        }
    }

    private void eliminar() {
        Cliente c = seleccionado();
        if (c == null || !Formulario.confirmar(this, "¿Dar de baja a " + c.nombreRazonSocial()
                + " (" + c.tipoDocumento() + " " + c.numeroDocumento() + ")?\nSus "
                + c.comprobantes() + " comprobantes lo conservarán.")) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            dao.eliminar(c.tipoDocumento(), c.numeroDocumento());
            return true;
        }, ok -> buscar());
    }

    /** Ejecuta la búsqueda con el texto ingresado. */
    public void buscar() {
        String texto = txtBuscar.getText();
        Tarea.ejecutar(this, () -> dao.buscar(texto, LIMITE), clientes -> {
            modelo.setFilas(clientes);
            lblTotal.setText(clientes.size() + " cliente(s)"
                    + (clientes.size() == LIMITE ? " (se muestran los primeros " + LIMITE + ")" : ""));
        });
    }
}
