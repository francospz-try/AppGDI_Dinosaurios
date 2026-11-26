# Verificación

Fecha: 21 de noviembre de 2026. Linux, OpenJDK 21, pgJDBC 42.7.7 y
PostgreSQL 17.12, en instancias descartables. Incluye las correcciones de la
revisión del PR #1.

## Construcción de la base

- `scripts/registro_comprobantes_completo.sql` en una instancia vacía: sin
  errores, en unos 2,4 s. Los scripts numerados ejecutados uno por uno dejan
  exactamente los mismos datos (huella MD5 de cada tabla y lista de rutinas).
- Resultado: 5631 comprobantes (4 eliminados), 949 clientes (2 dados de baja),
  42 productos o servicios (1 dado de baja), 43 rutinas, 4 disparadores.
- `08_permisos.sql`: `gdi_app` ejecuta 40 rutinas, lee las 8 tablas y no puede
  modificar ninguna. Si antes tenía permisos de escritura (se le concedieron
  `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` y `CREATE`), el script se los retira;
  ejecutado dos veces seguidas da el mismo resultado y no cambia los datos.

## Pruebas automáticas

Ejecutadas con `herramientas/probar.sh`, y con `herramientas/probar.ps1` en
PowerShell 7 con y sin un `.jar` previo, sobre tres bases: una recién creada en
una instancia con configuración regional C.UTF-8, otra en una instancia con
configuración C y la base migrada (ver abajo). Las clases que usan el
aplicativo se conectan con `gdi_app`; la de integridad, con un administrador.

| Clase | Resultado | Qué prueba |
|---|---|---|
| `PruebaConfiguracion` | 6 de 6 | Archivo de configuración sin `db.usuario` ni `db.clave`, vacío, con datos en blanco o con espacios, con puerto no numérico o fuera de rango, e inexistente |
| `PruebaConexion` | 9 de 9 | Conexión, tablas reales, cierre de recursos, rol sin privilegios que no puede escribir directo, mensajes ante contraseña errónea, servidor apagado y base inexistente |
| `PruebasHito30` | 11 de 11 | Búsquedas (también sin mayúsculas ni tildes), periodo, detalle y omisión de filas eliminadas |
| `PruebasHito70` | 44 de 44 | Validación, CRUD de clientes, productos y comprobantes, procedimientos, disparadores y atomicidad |
| `PruebasHito100` | 22 de 22 | Los doce reportes (definición, JOIN, exclusión de eliminados, resultados) y registro de series y monedas |
| `PruebaInterfaz` | 4 de 4 | Pestañas reales construidas sin ventanas, usadas y verificadas por su contenido |
| `PruebaIntegridad` | 3 de 3 | Con SQL directo: última línea, total que no cuadra y borrados simultáneos de las dos líneas de un comprobante |
| **Total** | **99 de 99** | |

## Correcciones de la revisión

| Hallazgo | Cómo se comprobó |
|---|---|
| `probar.ps1` sin `.jar` previo buscaba las clases en `aplicativo/aplicativo/target` | Reproducido (`ClassNotFoundException`); tras usar rutas desde `$PSScriptRoot` y restaurar la carpeta, `probar.ps1` y `probar.sh` pasan con y sin `.jar` previo, y la carpeta de trabajo no cambia |
| `preparar-windows.ps1` remitía a archivos retirados | Apunta a `aplicativo/config/conexion.example.properties`, `herramientas/compilar.ps1` e `Iniciar.cmd`; búsqueda sin otras referencias a `database/`, `config/database.properties`, `scripts/build.*`, `scripts/test.*` ni `build/gdi.jar`, salvo los documentos históricos marcados como tales |
| `Configuracion` fallaba sin `db.usuario` | `PruebaConfiguracion` G1 falla con el código revisado (`NullPointerException`) y pasa con el corregido |
| `08_permisos.sql` no retiraba permisos anteriores | Con permisos de escritura concedidos a mano, tras el script `gdi_app` queda sin ninguno |
| Bases existentes | Script de migración probado de punta a punta (abajo) |
| Búsqueda en una instancia con configuración C | Reproducido: 3 pruebas fallan y `upper('clínica')` da `CLíNICA`; tras la corrección, 99 de 99 en las instancias C y C.UTF-8 |

## Migración de una base de la versión anterior

`scripts/migrar_desde_version_anterior.sql` se probó sobre bases construidas con
los scripts del commit `b8be9b7` (`database/original/01`–`02`, `04`, `07`, `05`
y `06`), en dos casos:

1. **Recién creada** (5328 comprobantes). Huella MD5 de los datos de las 8
   tablas idéntica antes y después (`eliminado = NOT activo`); `gdi_app` no
   puede `UPDATE`, `INSERT`, `DELETE` ni `TRUNCATE`; estructura (columnas,
   restricciones, índices, disparadores, rutinas, permisos) idéntica a la de una
   base nueva, salvo el esquema `migracion`. Con los datos de
   `scripts/07_datos_aplicativo.sql` registrados por los procedimientos sobre la
   base migrada, las 7 clases de pruebas pasan: 99 de 99.
2. **Después de ejecutar las pruebas de la versión anterior** (79 de 79), que
   dejan bajas de 1 comprobante, 1 cliente, 1 producto, 1 serie, 1 moneda y
   2 documentos, y 2 comprobantes con versión 3. Huella de datos idéntica;
   las bajas de comprobante, cliente y producto pasan a `eliminado`; las de
   serie, moneda y documentos (4) y las versiones (2) quedan en el esquema
   `migracion`; misma estructura que una base nueva; `PruebaConexion` 9 de 9.

Ejecutada una segunda vez, la migración se detiene con «La base ya tiene el
esquema actual» sin cambiar nada.

## Límites

- `Iniciar.cmd` y los scripts `.ps1` no se ejecutaron en un Windows real: los
  `.ps1` se probaron con PowerShell 7 en Linux. La corrección de la búsqueda no
  usa la configuración regional de la instancia, por lo que no depende del
  sistema operativo.
- Las pruebas dejan datos de prueba y bajas lógicas: no se repiten sin volver
  a crear la base.

## Versión anterior

La implementación anterior de este repositorio (paquete `pe.edu.ucsm.gdi` y
scripts `database/04`–`07`, ya retirados) se construyó y probó en las mismas
condiciones antes de integrarla: 79 de 79 comprobaciones. Queda en el historial
de Git.
