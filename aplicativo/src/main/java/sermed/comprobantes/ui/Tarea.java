package sermed.comprobantes.ui;

import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * Ejecuta una operación de base de datos fuera del hilo de la interfaz,
 * para que la ventana no se congele, y muestra los errores al usuario.
 */
public final class Tarea {

    /**
     * Operación que puede fallar.
     *
     * @param <T> tipo del resultado
     */
    @FunctionalInterface
    public interface Operacion<T> {
        /**
         * @return resultado de la operación
         * @throws Exception si la operación falla
         */
        T ejecutar() throws Exception;
    }

    private Tarea() { }

    /**
     * @param <T>       tipo del resultado
     * @param padre     componente sobre el que se muestran el cursor y los errores
     * @param operacion operación de base de datos
     * @param alTerminar recibe el resultado en el hilo de la interfaz
     */
    public static <T> void ejecutar(Component padre, Operacion<T> operacion,
                                    Consumer<T> alTerminar) {
        ejecutar(padre, operacion, alTerminar, error -> { });
    }

    /**
     * Igual que {@link #ejecutar(Component, Operacion, Consumer)}, pero
     * avisa también del error a quien llama, después de mostrarlo.
     *
     * @param <T>        tipo del resultado
     * @param padre      componente sobre el que se muestran el cursor y los errores
     * @param operacion  operación de base de datos
     * @param alTerminar recibe el resultado en el hilo de la interfaz
     * @param alFallar   recibe el error en el hilo de la interfaz
     */
    public static <T> void ejecutar(Component padre, Operacion<T> operacion,
                                    Consumer<T> alTerminar, Consumer<Throwable> alFallar) {
        padre.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return operacion.ejecutar();
            }

            @Override
            protected void done() {
                padre.setCursor(Cursor.getDefaultCursor());
                try {
                    alTerminar.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause();
                    JOptionPane.showMessageDialog(padre, causa.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    alFallar.accept(causa);
                }
            }
        }.execute();
    }
}
