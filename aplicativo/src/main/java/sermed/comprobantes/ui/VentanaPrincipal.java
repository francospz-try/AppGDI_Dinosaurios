package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.datos.ProductoDAO;
import sermed.comprobantes.datos.ReporteDAO;

/**
 * Ventana principal: una pestaña por módulo (comprobantes, clientes,
 * productos, reportes y catálogos) y una barra de estado con la conexión.
 */
public class VentanaPrincipal extends JFrame {

    private final ConexionBD bd;
    private final JLabel lblEstado = new JLabel(" ");
    private final PanelComprobantes panelComprobantes;
    private final PanelClientes panelClientes;
    private final PanelProductos panelProductos;
    private final PanelCatalogos panelCatalogos;

    /** @param bd capa de conexión */
    public VentanaPrincipal(ConexionBD bd) {
        super("Registro de comprobantes de pago — Sermed Ingenieros E.I.R.L.");
        this.bd = bd;
        panelComprobantes = new PanelComprobantes(new ComprobanteDAO(bd), new ClienteDAO(bd),
                new CatalogoDAO(bd));
        panelClientes = new PanelClientes(new ClienteDAO(bd));
        panelProductos = new PanelProductos(new ProductoDAO(bd));
        panelCatalogos = new PanelCatalogos(new CatalogoDAO(bd));

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Comprobantes", panelComprobantes);
        pestanas.addTab("Clientes", panelClientes);
        pestanas.addTab("Productos y servicios", panelProductos);
        pestanas.addTab("Reportes", new PanelReportes(new ReporteDAO(bd), new CatalogoDAO(bd)));
        pestanas.addTab("Catálogos", panelCatalogos);

        lblEstado.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(pestanas, BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);
        setJMenuBar(menu());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1100, 650));
        pack();
        setLocationRelativeTo(null);
    }

    private JMenuBar menu() {
        JMenu archivo = new JMenu("Archivo");
        JMenuItem conectar = new JMenuItem("Conectar…");
        conectar.addActionListener(e -> pedirConexion());
        JMenuItem probar = new JMenuItem("Probar conexión");
        probar.addActionListener(e -> probarConexion(true));
        JMenuItem salir = new JMenuItem("Salir");
        salir.addActionListener(e -> dispose());
        archivo.add(conectar);
        archivo.add(probar);
        archivo.addSeparator();
        archivo.add(salir);
        JMenuBar barra = new JMenuBar();
        barra.add(archivo);
        return barra;
    }

    /**
     * Prueba la conexión, muestra el resultado en la barra de estado y, si
     * conecta, carga los datos iniciales.
     *
     * @param avisar si es verdadero, muestra también un mensaje con el resultado
     */
    public void probarConexion(boolean avisar) {
        lblEstado.setText("Conectando con " + bd.configuracion() + "…");
        Tarea.ejecutar(this, () -> {
            ConexionBD.InfoConexion info = bd.probar();
            int series = new CatalogoDAO(bd).series().size();
            return new Object[] {info, series};
        }, r -> {
            ConexionBD.InfoConexion info = (ConexionBD.InfoConexion) r[0];
            lblEstado.setText("Conectado a " + info.base() + " en " + info.servidor() + " como "
                    + info.usuario() + " — " + info.version() + " — " + r[1] + " series registradas");
            if (avisar) {
                JOptionPane.showMessageDialog(this, "Conexión correcta (" + info.milisegundos()
                        + " ms).\n" + info.version(), "Probar conexión",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                LocalDate hoy = LocalDate.now();
                panelComprobantes.buscar(hoy.minusMonths(3).withDayOfMonth(1), hoy);
                panelClientes.buscar();
                panelProductos.buscar();
                panelCatalogos.cargar();
            }
        }, error -> {
            lblEstado.setText("Sin conexión con " + bd.configuracion()
                    + ". Ingrese los datos de conexión (Archivo > Conectar…).");
            if (!avisar) {
                pedirConexion();
            }
        });
    }

    /**
     * Abre el formulario de conexión y, si la conexión resulta, carga los
     * datos con los nuevos datos de conexión.
     */
    public void pedirConexion() {
        DialogoConexion d = new DialogoConexion(this, bd);
        d.setVisible(true);
        if (d.conectado()) {
            probarConexion(false);
        }
    }
}
