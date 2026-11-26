package sermed.comprobantes.pruebas;

import static sermed.comprobantes.pruebas.Verificador.comprobar;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import sermed.comprobantes.config.Configuracion;
import sermed.comprobantes.config.ConfiguracionException;

/**
 * Pruebas de la lectura del archivo de configuración, sin base de datos:
 * archivos con datos faltantes, en blanco, con espacios o con números no
 * válidos, y un archivo inexistente. Escribe archivos temporales que borra
 * al terminar.
 */
public final class PruebaConfiguracion {

    private PruebaConfiguracion() { }

    /**
     * @param args no se usan
     * @throws Exception si no se puede crear la carpeta temporal
     */
    public static void main(String[] args) throws Exception {
        Path carpeta = Files.createTempDirectory("prueba-configuracion");
        Verificador v = new Verificador();
        try {
            v.caso("G1 Sin db.usuario ni db.clave: rol gdi_app y contraseña vacía", () -> {
                Configuracion c = leer(carpeta, "db.host=servidor\ndb.puerto=5433\n"
                        + "db.base=otra_base\n");
                comprobar(c.usuario().equals("gdi_app") && c.clave().isEmpty(), c.toString());
                comprobar(c.host().equals("servidor") && c.puerto() == 5433
                        && c.base().equals("otra_base"), c.toString());
                return c + " (contraseña vacía: se pide en pantalla)";
            });
            v.caso("G2 Archivo vacío: todos los valores por defecto", () -> {
                Configuracion c = leer(carpeta, "");
                Configuracion d = Configuracion.porDefecto();
                comprobar(c.toString().equals(d.toString()) && c.tiempoEspera() == 5, c.toString());
                return c.toString();
            });
            v.caso("G3 Datos en blanco o con espacios: por defecto o recortados", () -> {
                Configuracion c = leer(carpeta, "db.host=   \ndb.usuario=  gdi_app  \n"
                        + "db.puerto= 5432 \ndb.clave=contraseña con ñ\n");
                comprobar(c.host().equals("localhost") && c.usuario().equals("gdi_app")
                        && c.puerto() == 5432 && c.clave().equals("contraseña con ñ"), c.toString());
                return c + "; la contraseña conserva sus caracteres";
            });
            v.caso("G4 Puerto que no es un número: error claro", () ->
                    error(carpeta, "db.puerto=cinco mil", "db.puerto"));
            v.caso("G5 Puerto fuera de rango: error claro", () ->
                    error(carpeta, "db.puerto=70000", "entre 1 y 65535"));
            v.caso("G6 Archivo inexistente: error que indica cómo crearlo", () -> {
                try {
                    Configuracion.cargar(carpeta.resolve("no-existe.properties"));
                } catch (ConfiguracionException e) {
                    comprobar(e.getMessage().startsWith("No se encontró"), e.getMessage());
                    return "Mensaje: «" + e.getMessage().replace(carpeta.toString(), "…") + "»";
                }
                throw new AssertionError("se leyó un archivo inexistente");
            });
        } finally {
            try (var archivos = Files.list(carpeta)) {
                for (Path a : archivos.toList()) {
                    Files.delete(a);
                }
            }
            Files.delete(carpeta);
        }
        v.terminar();
    }

    private static Configuracion leer(Path carpeta, String contenido) throws Exception {
        Path archivo = Files.createTempFile(carpeta, "conexion", ".properties");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return Configuracion.cargar(archivo);
    }

    private static String error(Path carpeta, String contenido, String fragmento) throws Exception {
        try {
            leer(carpeta, contenido);
        } catch (ConfiguracionException e) {
            comprobar(e.getMessage().contains(fragmento), e.getMessage());
            return "Mensaje: «" + e.getMessage() + "»";
        }
        throw new AssertionError("se aceptó un número no válido");
    }
}
