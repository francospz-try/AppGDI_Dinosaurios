# Registro de comprobantes de pago — aplicativo

Aplicativo de escritorio de Sermed Ingenieros E.I.R.L. para registrar,
consultar, modificar y dar de baja (borrado lógico) los comprobantes de pago
emitidos, con sus clientes, productos y servicios, y para obtener doce
reportes. Es un cliente de la base de datos PostgreSQL `registro_comprobantes`.

Java 21 con Swing y JDBC, sin frameworks: el aplicativo no consulta ni
modifica las tablas directamente, sino mediante las funciones (`SELECT * FROM
fn_...`) y los procedimientos (`CALL sp_...`) de la base de datos, y se
conecta con el rol `gdi_app`, que no puede hacer otra cosa. La única
dependencia es el controlador JDBC oficial de PostgreSQL (42.7.7); se
compila con los scripts de `herramientas/` o con Maven.

## Requisitos

| Programa | Versión | Para qué |
|---|---|---|
| Java (JDK) | 21 o superior | Compilar y ejecutar; para solo ejecutar basta un JRE 21 |
| Maven | 3.9 o superior | Opcional: `herramientas/compilar.*` compila sin Maven |
| PostgreSQL | 17 | Servidor de la base de datos; incluye `psql` |

## 1. Crear o migrar la base de datos

Elija **uno** de los dos caminos, con un usuario administrador de
PostgreSQL y desde la carpeta principal:

**Base nueva** (instalación inicial o base de pruebas descartable):

```sh
psql -U postgres -d postgres -f scripts/registro_comprobantes_completo.sql
```

> **Atención:** el script completo **elimina la base `registro_comprobantes`
> si ya existe**, con todos sus datos. No lo ejecute sobre una base en uso.

**Base existente de la versión anterior del aplicativo** (la de los
scripts `database/` del repositorio hasta el commit b8be9b7), cuyos datos
deben conservarse: respáldela y migre.

```sh
pg_dump -U postgres -Fc -d registro_comprobantes -f antes_de_migrar.dump
psql -U postgres -d registro_comprobantes -f scripts/migrar_desde_version_anterior.sql
```

La migración ocurre en una sola transacción (si falla, la base no cambia):
convierte `activo` en `eliminado`, guarda en el esquema `migracion` las bajas
de series, monedas y documentos y las versiones de los comprobantes, que el
esquema actual no tiene, retira el código anterior, crea las rutinas actuales
y aplica los permisos. Comprueba que ninguna tabla pierda filas. Si se
ejecuta otra vez, se detiene sin cambiar nada.

**Base nueva**, en detalle:

El script completo crea la base `registro_comprobantes` desde cero (la
elimina si ya existe), la llena con los datos de prueba, crea las funciones,
los procedimientos, los disparadores y los reportes, y el rol `gdi_app` del
aplicativo. Equivale a ejecutar, en orden, los scripts numerados
`01_creacion.sql`, `02_insercion.sql`, `04_procedimientos.sql`,
`05_procedimientos.sql`, `06_reportes.sql`, `07_datos_aplicativo.sql` y
`08_permisos.sql` (el primero conectado a la base `postgres`, los demás a
`registro_comprobantes`). `03_consultas.sql` solo consulta.

Después, asigne una vez la contraseña del rol (no está en ningún script;
en una base migrada, `gdi_app` conserva la que ya tenía):

```sh
psql -U postgres -d registro_comprobantes -c '\password gdi_app'
```

`gdi_app` puede leer las tablas y ejecutar las rutinas, y nada más: toda
escritura pasa por un procedimiento. Si vuelve a ejecutar los scripts 04 a
06, ejecute después otra vez `08_permisos.sql`.

## 2. Configurar la conexión

Las credenciales no están en el código. Si PostgreSQL está en el mismo
equipo con el puerto estándar, no hace falta configurar nada: al abrirse,
el aplicativo pide la contraseña de `gdi_app` en un formulario y la conserva
solo en memoria. Para otro servidor o puerto, o para no escribir la
contraseña cada vez, cree `config/conexion.properties` (no se versiona) a
partir del ejemplo:

```sh
cd aplicativo
cp config/conexion.example.properties config/conexion.properties
```

Configuración por defecto (`config/conexion.example.properties`), para un
PostgreSQL instalado en el mismo equipo con el puerto estándar:

```properties
db.host=localhost
db.puerto=5432
db.base=registro_comprobantes
db.usuario=gdi_app
db.clave=
db.tiempoEspera=5
```

Si `db.clave` queda vacío, el aplicativo la pide al iniciar; si se escribe,
el archivo contiene la contraseña y no debe compartirse. Para usar otro archivo
sin tocar este, agregue `-Dcomprobantes.config=<ruta>` al comando `java`.

## 3. Compilar

Sin Maven (descarga el controlador la primera vez y verifica su SHA-256):

```sh
bash herramientas/compilar.sh                                     # Linux
powershell -ExecutionPolicy Bypass -File herramientas\compilar.ps1  # Windows
```

o con Maven: `cd aplicativo && mvn package`. Ambos generan
`aplicativo/target/registro-comprobantes.jar`, un único archivo ejecutable
que incluye el controlador JDBC.

## 4. Ejecutar

Desde la carpeta `aplicativo/` (el archivo de configuración se busca en
`config/`, respecto de la carpeta de trabajo):

```sh
java -jar target/registro-comprobantes.jar
```

En Windows, `Iniciar.cmd` (en la carpeta principal del repositorio) lo abre
con doble clic.

Para un usuario final, la carpeta del ejecutable tiene
`registro-comprobantes.jar`, la carpeta `config/` y los lanzadores de
`distribucion/` (`iniciar.bat` en Windows, `iniciar.sh` en Linux), que abren
el aplicativo con doble clic desde su propia carpeta.

La barra inferior de la ventana indica si la conexión fue correcta. El uso
de cada pantalla se explica en el manual de usuario
(`manual.pdf`, en la carpeta del proyecto).

## 5. Probar

Siete programas de prueba ejecutables, sin bibliotecas de pruebas, que
imprimen `[PASA]` o `[FALLA]` por caso y terminan con código 0 si todo pasa.
Modifican la base, por lo que se ejecutan sobre una base **descartable**
recién creada con el script completo, y no se repiten sin volver a crearla.
`PruebaConfiguracion` no usa la base; las cinco siguientes usan el rol
`gdi_app`, y la de integridad, un administrador, porque prueba los
disparadores con SQL directo. Se necesitan dos archivos de
configuración, con el formato de `config/conexion.example.properties`:

```sh
COMPROBANTES_BD_DESCARTABLE=si bash herramientas/probar.sh <config gdi_app> <config administrador>
```

En Windows: `$env:COMPROBANTES_BD_DESCARTABLE='si'` y
`herramientas\probar.ps1 <config gdi_app> <config administrador>`.

| Clase | Casos | Qué prueba |
|---|---|---|
| `PruebaConfiguracion` | 6 | Archivo de configuración con datos faltantes o no válidos |
| `PruebaConexion` | 9 | Conexión, rol sin privilegios, mensajes de error |
| `PruebasHito30` | 11 | Consultas, búsqueda sin mayúsculas ni tildes, filas eliminadas |
| `PruebasHito70` | 44 | Validación, CRUD, procedimientos y disparadores |
| `PruebasHito100` | 22 | Doce reportes, series y monedas |
| `PruebaInterfaz` | 4 | Pestañas reales sin abrir ventanas |
| `PruebaIntegridad` | 3 | Disparadores con SQL directo y borrados simultáneos |

## Estructura del código

```
src/main/java/sermed/comprobantes/
  App.java          punto de entrada: lee la configuración y abre la ventana
  config/           lectura del archivo de configuración
  datos/            capa única de conexión (ConexionBD), un DAO por módulo,
                    traducción de errores de PostgreSQL a mensajes en español
                    y definición de los reportes
  modelo/           clases de datos (cliente, comprobante, línea, serie…)
  validacion/       validación de formularios con las reglas de la base
  ui/               ventana principal, pestañas y formularios Swing
  pruebas/          pruebas ejecutables
```

| Pestaña | Funciones | Rutinas de la base |
|---|---|---|
| Comprobantes | Buscar por periodo, ver detalle, registrar, modificar, eliminar, ver eliminados y restaurar | `fn_comprobantes_periodo`, `fn_comprobante_*`, `sp_insertar_comprobante`, `sp_modificar_comprobante`, `sp_eliminar_comprobante`, `sp_restaurar_comprobante`, `fn_comprobantes_eliminados`, `fn_calcular_importes`, `fn_siguiente_numero` |
| Clientes | Buscar, registrar, modificar y dar de baja | `fn_buscar_clientes`, `sp_*_cliente` |
| Productos y servicios | Buscar, registrar, modificar y dar de baja | `fn_buscar_productos`, `sp_*_producto` |
| Reportes | Doce reportes con parámetros | `fn_reporte_*` |
| Catálogos | Series y monedas: listar y registrar | `fn_listar_series`, `fn_listar_monedas`, `sp_insertar_serie`, `sp_insertar_moneda` |
