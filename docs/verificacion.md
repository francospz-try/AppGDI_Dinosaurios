# Verificación

Fecha: 21 de noviembre de 2026. Linux, OpenJDK 21, pgJDBC 42.7.7 y
PostgreSQL 17.12, en instancias descartables.

## Construcción de la base

- `scripts/registro_comprobantes_completo.sql` en una instancia vacía: sin
  errores, en unos 2,4 s. Los scripts numerados ejecutados uno por uno dejan
  exactamente los mismos datos (huella MD5 de cada tabla y lista de rutinas).
- Resultado: 5631 comprobantes (4 eliminados), 949 clientes (2 dados de baja),
  42 productos o servicios (1 dado de baja), 41 rutinas, 4 disparadores.
- `08_permisos.sql`: `gdi_app` ejecuta 38 rutinas, lee las 8 tablas y no puede
  modificar ninguna.

## Pruebas automáticas

Ejecutadas con `herramientas/probar.sh` (y con `herramientas/probar.ps1` en
PowerShell 7) sobre una base recién creada; las cinco primeras clases con el
rol `gdi_app`, la de integridad con un administrador.

| Clase | Resultado | Qué prueba |
|---|---|---|
| `PruebaConexion` | 9 de 9 | Conexión, tablas reales, cierre de recursos, rol sin privilegios que no puede escribir directo, mensajes ante contraseña errónea, servidor apagado y base inexistente |
| `PruebasHito30` | 9 de 9 | Búsquedas, periodo, detalle y omisión de filas eliminadas |
| `PruebasHito70` | 44 de 44 | Validación, CRUD de clientes, productos y comprobantes, procedimientos, disparadores y atomicidad |
| `PruebasHito100` | 22 de 22 | Los doce reportes (definición, JOIN, exclusión de eliminados, resultados) y registro de series y monedas |
| `PruebaInterfaz` | 4 de 4 | Pestañas reales construidas sin ventanas, usadas y verificadas por su contenido |
| `PruebaIntegridad` | 3 de 3 | Con SQL directo: última línea, total que no cuadra y borrados simultáneos de las dos líneas de un comprobante |
| **Total** | **91 de 91** | |

## Recorrido de la interfaz

Con la base completa se recorrieron, sin abrir ventanas en el escritorio, todas
las funciones como lo haría un usuario (conexión, registro, modificación,
eliminación y restauración de un comprobante, CRUD de clientes y productos, los
doce reportes, series y monedas), comprobando en la base el efecto de cada una.
Las capturas del manual provienen de ese recorrido.

## Límites

- `Iniciar.cmd` y los scripts `.ps1` no se ejecutaron en un Windows real: los
  `.ps1` se probaron con PowerShell 7 en Linux.
- Las pruebas dejan datos de prueba y bajas lógicas: no se repiten sin volver
  a crear la base.

## Versión anterior

La implementación anterior de este repositorio (paquete `pe.edu.ucsm.gdi`,
scripts `database/04`–`07`) se construyó y probó en las mismas condiciones
antes de integrarla: 79 de 79 comprobaciones. Queda en el historial de Git.
