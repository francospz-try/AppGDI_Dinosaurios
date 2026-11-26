package sermed.comprobantes;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.config.ConfiguracionException;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.ui.VentanaPrincipal;

/**
 * Punto de entrada del aplicativo de registro de comprobantes de pago de
 * Sermed Ingenieros E.I.R.L. Lee la configuración (si existe), crea la capa
 * de conexión y abre la ventana principal; si falta la contraseña o el
 * archivo, pide los datos de conexión en pantalla.
 */
public final class App {

    private App() { }

    /**
     * @param args no se usan
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Configuracion config;
            boolean pedir = false;
            try {
                config = Configuracion.cargar();
            } catch (ConfiguracionException e) {
                // Sin archivo de configuración, o con un dato mal escrito, se
                // piden los datos en pantalla; en el segundo caso se avisa qué
                // dato está mal
                if (!e.getMessage().startsWith("No se encontró")) {
                    JOptionPane.showMessageDialog(null, e.getMessage(), "Configuración",
                            JOptionPane.WARNING_MESSAGE);
                }
                config = Configuracion.porDefecto();
                pedir = true;
            }
            VentanaPrincipal ventana = new VentanaPrincipal(new ConexionBD(config));
            ventana.setVisible(true);
            if (pedir || config.clave().isEmpty()) {
                ventana.pedirConexion();
            } else {
                ventana.probarConexion(false);
            }
        });
    }
}
