package sermed.comprobantes.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Utilidades comunes a los formularios de registro y modificación. */
public final class Formulario {

    private Formulario() { }

    /**
     * Crea un panel de dos columnas (etiqueta y campo) por fila.
     *
     * @param filas pares etiqueta, componente
     * @return panel con los campos alineados
     */
    public static JPanel campos(Object... filas) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        for (int i = 0; i < filas.length; i += 2) {
            g.gridy = i / 2;
            g.gridx = 0;
            g.anchor = GridBagConstraints.EAST;
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            p.add(new JLabel((String) filas[i]), g);
            g.gridx = 1;
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            p.add((JComponent) filas[i + 1], g);
        }
        return p;
    }

    /**
     * Muestra la lista de errores de validación.
     *
     * @param padre   componente sobre el que se muestra el mensaje
     * @param errores errores encontrados
     * @return verdadero si había errores
     */
    public static boolean mostrarErrores(Component padre, List<String> errores) {
        if (errores.isEmpty()) {
            return false;
        }
        JOptionPane.showMessageDialog(padre, "Corrija los siguientes datos:\n• "
                + String.join("\n• ", errores), "Datos no válidos", JOptionPane.WARNING_MESSAGE);
        return true;
    }

    /**
     * @param texto texto ingresado
     * @return el texto recortado, o {@code null} si está vacío
     */
    public static String texto(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /**
     * Convierte un importe ingresado; acepta coma o punto decimal.
     *
     * @param texto   texto ingresado
     * @param campo   nombre del campo, para el mensaje de error
     * @param errores lista donde se agrega el error si el texto no es un número
     * @return el importe, o {@code null} si el campo está vacío o no es válido
     */
    public static BigDecimal numero(String texto, String campo, List<String> errores) {
        String t = texto(texto);
        if (t == null) {
            return null;
        }
        try {
            return new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            errores.add(campo + ": «" + t + "» no es un número.");
            return null;
        }
    }

    /**
     * @param texto   fecha en dd/mm/aaaa
     * @param campo   nombre del campo
     * @param errores lista donde se agrega el error si la fecha no es válida
     * @return la fecha, o {@code null} si está vacía o no es válida
     */
    public static LocalDate fecha(String texto, String campo, List<String> errores) {
        String t = texto(texto);
        if (t == null) {
            return null;
        }
        try {
            return LocalDate.parse(t, Formato.FECHA);
        } catch (DateTimeParseException e) {
            errores.add(campo + ": use el formato dd/mm/aaaa.");
            return null;
        }
    }

    /**
     * Pide confirmación antes de una operación.
     *
     * @param padre   componente padre
     * @param mensaje pregunta
     * @return verdadero si el usuario acepta
     */
    public static boolean confirmar(Component padre, String mensaje) {
        return JOptionPane.showConfirmDialog(padre, mensaje, "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
