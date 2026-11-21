package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.ClienteDAO;
import sermed.comprobantes.datos.ComprobanteDAO;
import sermed.comprobantes.datos.ConexionBD;
import sermed.comprobantes.datos.Reporte;
import sermed.comprobantes.datos.ReporteDAO;
import sermed.comprobantes.ui.PanelCatalogos;
import sermed.comprobantes.ui.PanelClientes;
import sermed.comprobantes.ui.PanelComprobantes;
import sermed.comprobantes.ui.PanelReportes;

/**
 * Prueba de la interfaz sin abrir ventanas: construye las pestañas reales,
 * ejecuta sus acciones en el hilo de eventos de Swing, como lo haría un
 * clic, y comprueba lo que muestran sus tablas. Solo lee la base.
 */
public final class PruebaInterfaz {

    private PruebaInterfaz() { }

    /**
     * @param args no se usan
     * @throws Exception si la configuración no puede leerse
     */
    public static void main(String[] args) throws Exception {
        ConexionBD bd = new ConexionBD(Configuracion.cargar());
        Verificador v = new Verificador();

        v.caso("U1 Pestaña Comprobantes: buscar un periodo llena la tabla", () -> {
            PanelComprobantes[] p = new PanelComprobantes[1];
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new PanelComprobantes(new ComprobanteDAO(bd), new ClienteDAO(bd),
                        new CatalogoDAO(bd));
                p[0].buscar(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27));
            });
            JTable t = (JTable) campo(p[0], "tabla");
            esperar(() -> t.getRowCount() == 8, "8 filas");
            Object ultimo = t.getValueAt(7, 0);
            comprobar("F001-005206".equals(ultimo), "último: " + ultimo);
            return "8 comprobantes del 25 al 27/08/2026; el último, " + ultimo;
        });

        v.caso("U2 Pestaña Clientes: buscar por documento", () -> {
            PanelClientes[] p = new PanelClientes[1];
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new PanelClientes(new ClienteDAO(bd));
                ((JTextField) campoSinError(p[0], "txtBuscar")).setText("20900000000");
                p[0].buscar();
            });
            JTable t = (JTable) campo(p[0], "tabla");
            esperar(() -> t.getRowCount() == 1, "1 fila");
            return "RUC 20900000000: " + t.getValueAt(0, 2);
        });

        v.caso("U3 Pestaña Reportes: elegir R05 y ejecutar", () -> {
            PanelReportes[] p = new PanelReportes[1];
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new PanelReportes(new ReporteDAO(bd), new CatalogoDAO(bd));
                ((JComboBox<?>) campoSinError(p[0], "cboReporte")).setSelectedItem(Reporte.R05);
                ((JTextField) campoSinError(p[0], "campos", 0)).setText("2026");
                invocar(p[0], "ejecutar");
            });
            JTable t = (JTable) campo(p[0], "tabla");
            esperar(() -> t.getRowCount() == 22, "22 filas");
            return "ventas mensuales de 2026: " + t.getRowCount() + " filas, columnas «"
                    + t.getColumnName(0) + "», «" + t.getColumnName(1) + "»…";
        });

        v.caso("U4 Pestaña Catálogos: carga series y monedas", () -> {
            PanelCatalogos[] p = new PanelCatalogos[1];
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new PanelCatalogos(new CatalogoDAO(bd));
                p[0].cargar();
            });
            JTable series = tabla(p[0], 0);
            esperar(() -> series.getRowCount() >= 3, "al menos 3 series");
            return series.getRowCount() + " series";
        });

        v.terminar();
    }

    private static void esperar(BooleanSupplier condicion, String descripcion) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> { });
        }
        throw new AssertionError("no se llegó a " + descripcion);
    }

    private static Object campo(Object o, String nombre) throws Exception {
        Field f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(o);
    }

    private static Object campoSinError(Object o, String nombre) {
        try {
            return campo(o, nombre);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object campoSinError(Object o, String nombre, int indice) {
        return ((List<?>) campoSinError(o, nombre)).get(indice);
    }

    private static void invocar(Object o, String metodo) {
        try {
            Method m = o.getClass().getDeclaredMethod(metodo);
            m.setAccessible(true);
            m.invoke(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tabla número {@code n} de un panel de catálogos, en orden de aparición. */
    private static JTable tabla(PanelCatalogos p, int n) {
        List<JTable> tablas = new ArrayList<>();
        buscarTablas(p, tablas);
        return tablas.get(n);
    }

    private static void buscarTablas(Container c, List<JTable> salida) {
        for (Component x : c.getComponents()) {
            if (x instanceof JTable t) {
                salida.add(t);
            } else if (x instanceof Container k) {
                buscarTablas(k, salida);
            }
        }
    }
}
