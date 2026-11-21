package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.ConexionBD;

/**
 * Formulario de conexión: servidor, puerto, base, usuario y contraseña.
 * Se abre al iniciar si falta la contraseña o la conexión falla, y desde
 * el menú Archivo. La contraseña queda solo en memoria: no se escribe en
 * ningún archivo.
 */
public class DialogoConexion extends JDialog {

    private final JTextField txtHost = new JTextField(18);
    private final JTextField txtPuerto = new JTextField(6);
    private final JTextField txtBase = new JTextField(18);
    private final JTextField txtUsuario = new JTextField(12);
    private final JPasswordField txtClave = new JPasswordField(12);
    private boolean conectado;

    /**
     * @param padre ventana dueña
     * @param bd    capa de conexión; si la prueba resulta, pasa a usar los datos ingresados
     */
    public DialogoConexion(Window padre, ConexionBD bd) {
        super(padre, "Conexión con la base de datos", ModalityType.APPLICATION_MODAL);
        Configuracion actual = bd.configuracion();
        txtHost.setText(actual.host());
        txtPuerto.setText(String.valueOf(actual.puerto()));
        txtBase.setText(actual.base());
        txtUsuario.setText(actual.usuario());
        txtClave.setText(actual.clave());

        JPanel campos = Formulario.campos("Servidor:", txtHost, "Puerto:", txtPuerto,
                "Base de datos:", txtBase, "Usuario:", txtUsuario, "Contraseña:", txtClave,
                " ", new JLabel("<html><i>La contraseña no se guarda en ningún archivo.</i></html>"));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JButton conectar = new JButton("Conectar");
        JButton cancelar = new JButton("Cancelar");
        cancelar.addActionListener(e -> dispose());
        conectar.addActionListener(e -> conectar(bd, actual));
        JPanel botones = new JPanel();
        botones.add(conectar);
        botones.add(cancelar);
        getRootPane().setDefaultButton(conectar);

        setLayout(new BorderLayout());
        add(campos, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(padre);
    }

    private void conectar(ConexionBD bd, Configuracion actual) {
        String host = txtHost.getText().trim();
        String base = txtBase.getText().trim();
        String usuario = txtUsuario.getText().trim();
        int puerto;
        try {
            puerto = Integer.parseInt(txtPuerto.getText().trim());
        } catch (NumberFormatException ex) {
            puerto = -1;
        }
        List<String> errores = new ArrayList<>();
        if (!host.matches("[A-Za-z0-9.-]+")) {
            errores.add("Servidor: escriba un nombre de equipo o una dirección IP.");
        }
        if (puerto < 1 || puerto > 65535) {
            errores.add("Puerto: debe ser un número entre 1 y 65535 (normalmente 5432).");
        }
        if (!base.matches("[A-Za-z0-9_]+")) {
            errores.add("Base de datos: dato obligatorio, sin espacios.");
        }
        if (usuario.isEmpty()) {
            errores.add("Usuario: dato obligatorio.");
        }
        if (Formulario.mostrarErrores(this, errores)) {
            return;
        }
        Configuracion nueva = new Configuracion(host, puerto, base, usuario,
                new String(txtClave.getPassword()), actual.tiempoEspera());
        Tarea.ejecutar(this, () -> new ConexionBD(nueva).probar(), info -> {
            bd.usar(nueva);
            conectado = true;
            dispose();
        });
    }

    /** @return verdadero si la conexión con los datos ingresados resultó */
    public boolean conectado() {
        return conectado;
    }
}
