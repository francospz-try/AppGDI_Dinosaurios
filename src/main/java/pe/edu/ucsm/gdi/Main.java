package pe.edu.ucsm.gdi;

import java.awt.Dimension;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada; no requiere abrir un IDE. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}
            catch(Exception ignored){/* Se conserva el estilo Swing predeterminado. */}
            JFrame frame=new JFrame("GDI · Registro de comprobantes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new GdiWindow(Path.of(args.length==0?"config/database.properties":args[0])));
            frame.setMinimumSize(new Dimension(1024,700));frame.setSize(1240,820);
            frame.setLocationRelativeTo(null);frame.setVisible(true);
        });
    }
}
