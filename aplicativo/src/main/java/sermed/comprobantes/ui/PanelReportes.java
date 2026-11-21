package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.datos.Reporte;
import sermed.comprobantes.datos.ReporteDAO;
import sermed.comprobantes.datos.Tabla;
import sermed.comprobantes.modelo.Serie;
import sermed.comprobantes.validacion.Validador;

/**
 * Pestaña de reportes: selector de los doce reportes, campos para sus
 * parámetros y tabla con el resultado que devuelve la función de la base
 * de datos.
 */
public class PanelReportes extends JPanel {

    private static final String TODAS = "(Todas)";
    private static final String TODOS = "(Todos)";

    private final ReporteDAO dao;
    private final CatalogoDAO catalogo;
    private final JComboBox<Reporte> cboReporte = new JComboBox<>(Reporte.values());
    private final JLabel lblNecesidad = new JLabel(" ");
    private final JPanel pnlParametros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final List<JComponent> campos = new ArrayList<>();
    private final ModeloReporte modelo = new ModeloReporte();
    private final JTable tabla = new JTable(modelo);
    private final JLabel lblTotal = new JLabel(" ");
    private final JButton btnEjecutar = new JButton("Ejecutar");

    /**
     * @param dao      ejecuta los reportes
     * @param catalogo series para los parámetros de tipo serie
     */
    public PanelReportes(ReporteDAO dao, CatalogoDAO catalogo) {
        super(new BorderLayout(8, 8));
        this.dao = dao;
        this.catalogo = catalogo;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        cboReporte.setMaximumRowCount(12);
        cboReporte.setFont(cboReporte.getFont().deriveFont(Font.BOLD, 13f));
        JPanel seleccion = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        seleccion.add(new JLabel("Reporte:"));
        seleccion.add(cboReporte);
        JPanel cabecera = new JPanel();
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        lblNecesidad.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        for (JComponent c : List.of(seleccion, lblNecesidad, pnlParametros)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            cabecera.add(c);
        }

        tabla.setAutoCreateRowSorter(true);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(22);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setDefaultRenderer(Object.class, new Celda());
        tabla.setDefaultRenderer(BigDecimal.class, new Celda());
        tabla.setDefaultRenderer(Number.class, new Celda());
        tabla.setDefaultRenderer(Boolean.class, new Celda());

        add(cabecera, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblTotal, BorderLayout.SOUTH);

        cboReporte.addActionListener(e -> mostrar((Reporte) cboReporte.getSelectedItem()));
        btnEjecutar.addActionListener(e -> ejecutar());
        mostrar((Reporte) cboReporte.getSelectedItem());
    }

    private void mostrar(Reporte r) {
        lblNecesidad.setText("<html><div style='width:900px'><i>" + r.necesidad() + "</i></div></html>");
        pnlParametros.removeAll();
        campos.clear();
        for (Reporte.Parametro p : r.parametros()) {
            JComponent campo = campo(p);
            campos.add(campo);
            if (p.tipo() != Reporte.Tipo.BOOLEANO) {
                pnlParametros.add(new JLabel(p.etiqueta() + ":"));
            }
            pnlParametros.add(campo);
        }
        pnlParametros.add(btnEjecutar);
        pnlParametros.revalidate();
        pnlParametros.repaint();
        modelo.setTabla(new Tabla(List.of(), List.of()));
        lblTotal.setText("Indique los parámetros y pulse Ejecutar.");
    }

    private JComponent campo(Reporte.Parametro p) {
        Object inicial = p.inicial() == null ? null : p.inicial().get();
        switch (p.tipo()) {
            case FECHA:
                return new JTextField(inicial == null ? "" : Formato.fecha((LocalDate) inicial), 8);
            case BOOLEANO:
                return new JCheckBox(p.etiqueta(), Boolean.TRUE.equals(inicial));
            case TIPO_RELACIONADO: {
                JComboBox<String> c = new JComboBox<>();
                c.addItem(TODOS);
                Validador.TIPOS_RELACIONADOS.forEach(c::addItem);
                return c;
            }
            case SERIE: {
                JComboBox<String> c = new JComboBox<>();
                if (!p.obligatorio()) {
                    c.addItem(TODAS);
                }
                Tarea.ejecutar(this, catalogo::series, series -> {
                    for (Serie s : series) {
                        c.addItem(s.serie());
                    }
                });
                return c;
            }
            default:
                return new JTextField(inicial == null ? "" : String.valueOf(inicial),
                        p.tipo() == Reporte.Tipo.ENTERO ? 6 : 14);
        }
    }

    /** Lee los parámetros, los valida y ejecuta el reporte seleccionado. */
    private void ejecutar() {
        Reporte r = (Reporte) cboReporte.getSelectedItem();
        List<String> errores = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        for (int i = 0; i < r.parametros().size(); i++) {
            valores.add(valor(r.parametros().get(i), campos.get(i), errores));
        }
        if (errores.isEmpty() && r.parametros().size() >= 2
                && valores.get(0) instanceof LocalDate desde
                && valores.get(1) instanceof LocalDate hasta && hasta.isBefore(desde)) {
            errores.add("La fecha «Hasta» no puede ser anterior a «Desde».");
        }
        if (Formulario.mostrarErrores(this, errores)) {
            return;
        }
        lblTotal.setText("Ejecutando…");
        Tarea.ejecutar(this, () -> dao.ejecutar(r, valores), t -> {
            modelo.setTabla(t);
            ajustarColumnas();
            lblTotal.setText(t.filas().isEmpty()
                    ? "Sin resultados para los parámetros indicados."
                    : t.filas().size() + " fila(s)");
        });
    }

    private Object valor(Reporte.Parametro p, JComponent campo, List<String> errores) {
        if (campo instanceof JCheckBox caja) {
            return caja.isSelected();
        }
        if (campo instanceof JComboBox<?> combo) {
            Object v = combo.getSelectedItem();
            if (v == null && p.obligatorio()) {
                errores.add(p.etiqueta() + ": seleccione una opción.");
            }
            return v == null || TODAS.equals(v) || TODOS.equals(v) ? null : v.toString();
        }
        String texto = Formulario.texto(((JTextField) campo).getText());
        if (texto == null) {
            if (p.obligatorio()) {
                errores.add(p.etiqueta() + ": dato obligatorio.");
            }
            return null;
        }
        switch (p.tipo()) {
            case FECHA:
                return Formulario.fecha(texto, p.etiqueta(), errores);
            case ENTERO:
                try {
                    int n = Integer.parseInt(texto);
                    if (n < 1) {
                        errores.add(p.etiqueta() + ": debe ser mayor que cero.");
                    }
                    return n;
                } catch (NumberFormatException e) {
                    errores.add(p.etiqueta() + ": «" + texto + "» no es un número entero.");
                    return null;
                }
            default:
                return texto;
        }
    }

    private void ajustarColumnas() {
        for (int c = 0; c < tabla.getColumnCount(); c++) {
            int ancho = tabla.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(tabla, tabla.getColumnName(c), false, false, -1, c)
                    .getPreferredSize().width;
            for (int f = 0; f < Math.min(tabla.getRowCount(), 200); f++) {
                ancho = Math.max(ancho, tabla.prepareRenderer(tabla.getCellRenderer(f, c), f, c)
                        .getPreferredSize().width);
            }
            tabla.getColumnModel().getColumn(c).setPreferredWidth(Math.min(ancho + 12, 330));
        }
    }

    /** Modelo de solo lectura sobre el resultado de un reporte. */
    private static final class ModeloReporte extends AbstractTableModel {

        private Tabla tabla = new Tabla(List.of(), List.of());

        void setTabla(Tabla t) {
            this.tabla = t;
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return tabla.filas().size();
        }

        @Override
        public int getColumnCount() {
            return tabla.columnas().size();
        }

        @Override
        public String getColumnName(int c) {
            return Reporte.encabezado(tabla.columnas().get(c));
        }

        @Override
        public Class<?> getColumnClass(int c) {
            for (List<Object> fila : tabla.filas()) {
                if (fila.get(c) != null) {
                    return fila.get(c).getClass();
                }
            }
            return Object.class;
        }

        @Override
        public Object getValueAt(int f, int c) {
            return tabla.filas().get(f).get(c);
        }
    }

    /** Muestra importes, fechas y valores lógicos con el formato del aplicativo. */
    private static final class Celda extends DefaultTableCellRenderer {

        @Override
        protected void setValue(Object v) {
            setHorizontalAlignment(v instanceof Number ? RIGHT : LEFT);
            if (v instanceof BigDecimal b) {
                super.setValue(Formato.importe(b));
            } else if (v instanceof LocalDate d) {
                super.setValue(Formato.fecha(d));
            } else if (v instanceof Boolean b) {
                super.setValue(b ? "Sí" : "No");
            } else {
                super.setValue(v == null ? "" : v);
            }
        }
    }
}
