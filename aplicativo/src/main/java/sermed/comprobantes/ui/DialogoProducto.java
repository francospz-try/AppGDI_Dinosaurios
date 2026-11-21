package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

import sermed.comprobantes.datos.ProductoDAO;
import sermed.comprobantes.modelo.ProductoServicio;
import sermed.comprobantes.validacion.Validador;

/**
 * Formulario para registrar o modificar un producto o servicio
 * ({@code sp_insertar_producto}, {@code sp_modificar_producto}).
 */
public class DialogoProducto extends JDialog {

    private final JTextField txtCodigo = new JTextField(14);
    private final JTextField txtDescripcion = new JTextField(44);
    private final JTextField txtUnidad = new JTextField(8);
    private boolean guardado;

    /**
     * @param padre    ventana dueña
     * @param dao      acceso a los productos
     * @param producto producto que se modifica, o {@code null} para uno nuevo
     */
    public DialogoProducto(Window padre, ProductoDAO dao, ProductoServicio producto) {
        super(padre, producto == null ? "Nuevo producto o servicio" : "Modificar producto o servicio",
                ModalityType.APPLICATION_MODAL);
        if (producto != null) {
            txtCodigo.setText(producto.codigo());
            txtDescripcion.setText(producto.descripcion());
            txtUnidad.setText(producto.unidadMedida());
            txtCodigo.setEditable(false);
        }
        JPanel campos = Formulario.campos("Código:", txtCodigo, "Descripción:", txtDescripcion,
                "Unidad de medida (UND, SERV, UNIDAD…):", txtUnidad);
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JButton guardar = new JButton("Guardar");
        JButton cancelar = new JButton("Cancelar");
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> {
            ProductoServicio p = new ProductoServicio(Formulario.texto(txtCodigo.getText()),
                    Formulario.texto(txtDescripcion.getText()), Formulario.texto(txtUnidad.getText()), 0);
            if (Formulario.mostrarErrores(this, Validador.producto(p))) {
                return;
            }
            Tarea.ejecutar(this, () -> {
                if (producto == null) {
                    dao.insertar(p);
                } else {
                    dao.modificar(p);
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

    /** @return verdadero si el producto se guardó */
    public boolean guardado() {
        return guardado;
    }
}
