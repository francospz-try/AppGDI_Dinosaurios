package pe.edu.ucsm.gdi;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Comprueba eventos Swing reales y renderiza la vista sin necesitar un escritorio. */
public final class UiSmoke {
    private UiSmoke() { }
    private static Component find(Container parent,String name) {
        for(Component c:parent.getComponents()) {
            if(name.equals(c.getName()))return c;
            if(c instanceof Container nested){Component found=find(nested,name);if(found!=null)return found;}
        }
        return null;
    }
    private static void layout(Container parent) {
        parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container child)layout(child);
    }
    private static void capture(GdiWindow view,Path path) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            layout(view);BufferedImage image=new BufferedImage(1240,820,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();view.paint(g);g.dispose();
            try{ImageIO.write(image,"png",path.toFile());}catch(Exception e){throw new RuntimeException(e);}
        });
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TableData mixed=new TableData(java.util.List.of("Campo","Valor"),java.util.List.of(
                java.util.List.of("serie","F001"),java.util.List.of("numero",5206),
                java.util.List.of("total",new java.math.BigDecimal("300.00"))));
            JTable table=new JTable(new ResultTableModel(mixed));table.setAutoCreateRowSorter(true);
            table.getRowSorter().toggleSortOrder(1);
            if(table.getRowCount()!=3)throw new AssertionError("Ordenar cabecera pierde filas");
            if(table.isCellEditable(0,1))throw new AssertionError("Cabecera editable");
        });
        System.out.println("PASS Swing: ordenación de cabecera con texto, entero y decimal sin excepción");
        Path output=Path.of("build/qa");Files.createDirectories(output);
        AtomicReference<GdiWindow> ref=new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {GdiWindow w=new GdiWindow(Path.of(args[0]));w.setSize(1240,820);layout(w);ref.set(w);});
        GdiWindow view=ref.get();capture(view,output.resolve("01-conexion.png"));
        SwingUtilities.invokeAndWait(() -> ((JButton)find(view,"conectar")).doClick());
        boolean loaded=false;
        for(int n=0;n<100;n++) {
            AtomicReference<Boolean> ready=new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> ready.set(((JTable)find(view,"comprobantes")).getRowCount()==500));
            if(ready.get()){loaded=true;break;}Thread.sleep(100);
        }
        if(!loaded)throw new AssertionError("La interfaz no cargó comprobantes después de conectar");
        capture(view,output.resolve("02-comprobantes.png"));
        SwingUtilities.invokeAndWait(() -> {
            for(Component c:view.getComponents())if(c instanceof JTabbedPane tabs)tabs.setSelectedIndex(2);
        });
        boolean clientsLoaded=false;
        for(int n=0;n<100;n++) {
            AtomicReference<Boolean> ready=new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> ready.set(((JTable)find(view,"clientes")).getRowCount()>0));
            if(ready.get()){clientsLoaded=true;break;}Thread.sleep(100);
        }
        if(!clientsLoaded)throw new AssertionError("La pestaña Clientes no cargó los registros activos");
        capture(view,output.resolve("03-clientes.png"));
        System.out.println("PASS Swing: conexión por botón, carga de 500 comprobantes y clientes activos, renderizado de tres pantallas");
    }
}
