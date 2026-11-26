package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Serie;
import sermed.comprobantes.validacion.Validador;

/**
 * Formulario para registrar una serie de comprobante
 * ({@code sp_insertar_serie}) o una moneda ({@code sp_insertar_moneda}).
 */
public class DialogoCatalogo extends JDialog {

    /** Qué se registra. */
    public enum Tipo {
        /** Serie de comprobante. */
        SERIE,
        /** Moneda. */
        MONEDA
    }

    private final JTextField txtCodigo = new JTextField(6);
    private final JTextField txtNombre = new JTextField(20);
    private final JComboBox<String> cmbTipo =
            new JComboBox<>(Validador.TIPOS_COMPROBANTE.toArray(new String[0]));
    private boolean guardado;

    /**
     * @param padre ventana dueña
     * @param dao   acceso a las series y monedas
     * @param tipo  serie o moneda
     */
    public DialogoCatalogo(Window padre, CatalogoDAO dao, Tipo tipo) {
        super(padre, tipo == Tipo.SERIE ? "Nueva serie de comprobante" : "Nueva moneda",
                ModalityType.APPLICATION_MODAL);
        JPanel campos = tipo == Tipo.SERIE
                ? Formulario.campos("Serie (4 caracteres, como F002):", txtCodigo,
                        "Tipo de comprobante:", cmbTipo)
                : Formulario.campos("Código ISO 4217 (3 letras, como USD):", txtCodigo,
                        "Nombre impreso (como DÓLARES AMERICANOS):", txtNombre);
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JButton guardar = new JButton("Guardar");
        JButton cancelar = new JButton("Cancelar");
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> {
            String codigo = Formulario.texto(txtCodigo.getText());
            codigo = codigo == null ? null : codigo.toUpperCase();
            if (tipo == Tipo.SERIE) {
                Serie s = new Serie(codigo, (String) cmbTipo.getSelectedItem());
                guardar(Validador.serie(s), () -> dao.insertarSerie(s));
            } else {
                Moneda m = new Moneda(codigo, Formulario.texto(txtNombre.getText()));
                guardar(Validador.moneda(m), () -> dao.insertarMoneda(m));
            }
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

    /** Operación de registro que puede fallar. */
    @FunctionalInterface
    private interface Registro {
        void ejecutar() throws Exception;
    }

    private void guardar(List<String> errores, Registro registro) {
        if (Formulario.mostrarErrores(this, errores)) {
            return;
        }
        Tarea.ejecutar(this, () -> {
            registro.ejecutar();
            return true;
        }, ok -> {
            guardado = true;
            dispose();
        });
    }

    /** @return verdadero si se guardó el registro */
    public boolean guardado() {
        return guardado;
    }
}
