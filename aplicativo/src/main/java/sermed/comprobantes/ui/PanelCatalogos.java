package sermed.comprobantes.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import sermed.comprobantes.datos.CatalogoDAO;
import sermed.comprobantes.modelo.Moneda;
import sermed.comprobantes.modelo.Serie;

/**
 * Pestaña de catálogos: series de comprobante y monedas que aceptan los
 * formularios, con el registro de nuevas. No se modifican ni se borran,
 * porque los comprobantes registrados las citan.
 */
public class PanelCatalogos extends JPanel {

    private final CatalogoDAO dao;
    private final ModeloTabla<Serie> modeloSeries = new ModeloTabla<>(List.of(
            new ModeloTabla.Columna<>("Serie", Serie::serie, 80),
            new ModeloTabla.Columna<>("Tipo de comprobante", Serie::tipoComprobante, 260)));
    private final ModeloTabla<Moneda> modeloMonedas = new ModeloTabla<>(List.of(
            new ModeloTabla.Columna<>("Código", Moneda::codigo, 80),
            new ModeloTabla.Columna<>("Nombre", Moneda::nombre, 260)));

    /** @param dao acceso a las series y monedas */
    public PanelCatalogos(CatalogoDAO dao) {
        super(new BorderLayout(8, 8));
        this.dao = dao;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnSerie = new JButton("Nueva serie");
        JButton btnMoneda = new JButton("Nueva moneda");
        JButton btnActualizar = new JButton("Actualizar");
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(btnSerie);
        barra.add(btnMoneda);
        barra.add(btnActualizar);
        add(barra, BorderLayout.NORTH);

        JPanel tablas = new JPanel(new GridLayout(1, 2, 10, 0));
        tablas.add(conTitulo("Series de comprobante", Tablas.crear(modeloSeries)));
        tablas.add(conTitulo("Monedas", Tablas.crear(modeloMonedas)));
        add(tablas, BorderLayout.CENTER);
        add(new JLabel("Las series y monedas registradas no se modifican ni se eliminan,"
                + " porque los comprobantes las citan."), BorderLayout.SOUTH);

        btnSerie.addActionListener(e -> registrar(DialogoCatalogo.Tipo.SERIE));
        btnMoneda.addActionListener(e -> registrar(DialogoCatalogo.Tipo.MONEDA));
        btnActualizar.addActionListener(e -> cargar());
    }

    private static JScrollPane conTitulo(String titulo, JTable tabla) {
        JScrollPane s = new JScrollPane(tabla);
        s.setBorder(BorderFactory.createTitledBorder(titulo));
        return s;
    }

    private void registrar(DialogoCatalogo.Tipo tipo) {
        DialogoCatalogo d = new DialogoCatalogo(SwingUtilities.getWindowAncestor(this), dao, tipo);
        d.setVisible(true);
        if (d.guardado()) {
            cargar();
        }
    }

    /** Carga las series y monedas registradas. */
    public void cargar() {
        Tarea.ejecutar(this, () -> new Object[] {dao.series(), dao.monedas()}, r -> {
            @SuppressWarnings("unchecked")
            List<Serie> series = (List<Serie>) r[0];
            @SuppressWarnings("unchecked")
            List<Moneda> monedas = (List<Moneda>) r[1];
            modeloSeries.setFilas(series);
            modeloMonedas.setFilas(monedas);
        });
    }
}
