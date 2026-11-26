package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.modelo.Cliente;
import sermed.comprobantes.validacion.Validador;

/**
 * Formulario para registrar o modificar un cliente. Valida los datos con
 * las reglas de la base antes de llamar a {@code sp_insertar_cliente} o
 * {@code sp_modificar_cliente}.
 */
public class DialogoCliente extends JDialog {

    private final JComboBox<String> cboTipo = new JComboBox<>(new String[] {"RUC", "DNI"});
    private final JTextField txtNumero = new JTextField(14);
    private final JTextField txtNombre = new JTextField(36);
    private final JTextField txtDireccion = new JTextField(36);
    private boolean guardado;

    /**
     * @param padre   ventana dueña
     * @param dao     acceso a los clientes
     * @param cliente cliente que se modifica, o {@code null} para uno nuevo
     */
    public DialogoCliente(Window padre, ClienteDAO dao, Cliente cliente) {
        super(padre, cliente == null ? "Nuevo cliente" : "Modificar cliente",
                ModalityType.APPLICATION_MODAL);
        if (cliente != null) {
            cboTipo.setSelectedItem(cliente.tipoDocumento());
            txtNumero.setText(cliente.numeroDocumento());
            txtNombre.setText(cliente.nombreRazonSocial());
            txtDireccion.setText(Formato.texto(cliente.direccion()));
            cboTipo.setEnabled(false);
            txtNumero.setEditable(false);
        }
        JPanel campos = Formulario.campos("Tipo de documento:", cboTipo,
                "Número de documento:", txtNumero, "Nombre o razón social:", txtNombre,
                "Dirección:", txtDireccion);
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JButton guardar = new JButton("Guardar");
        JButton cancelar = new JButton("Cancelar");
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> {
            Cliente c = new Cliente((String) cboTipo.getSelectedItem(),
                    Formulario.texto(txtNumero.getText()), Formulario.texto(txtNombre.getText()),
                    Formulario.texto(txtDireccion.getText()), 0);
            if (Formulario.mostrarErrores(this, Validador.cliente(c))) {
                return;
            }
            Tarea.ejecutar(this, () -> {
                if (cliente == null) {
                    dao.insertar(c);
                } else {
                    dao.modificar(c);
                }
                return true;
            }, ok -> {
                guardado = true;
                dispose();
            });
        });
        JPanel botones = new JPanel();
        botones.add(guardar);
        botones.add(cancelar);
        getRootPane().setDefaultButton(guardar);

        setLayout(new BorderLayout());
        add(campos, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(padre);
    }

    /** @return verdadero si el cliente se guardó */
    public boolean guardado() {
        return guardado;
    }
}
