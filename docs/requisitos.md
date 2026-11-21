# Requisitos y entregas

Fuentes: informe acumulativo del proyecto, sus scripts y las transcripciones de
las entregas 5 y 6 proporcionadas por el equipo. No se publican aquí los
documentos personales ni los de la empresa.

## Entrega 5 (aplicativo)

| Requisito | Estado | Dónde |
|---|---|---|
| Cliente de PostgreSQL; lenguaje libre, sin frameworks que oculten el acceso a la base | Cumplido | `aplicativo/` (Java 21, Swing, JDBC) |
| Insertar, consultar, buscar, modificar y dar de baja lógicamente | Cumplido | Pestañas Comprobantes, Clientes, Productos y Catálogos; restaurar comprobantes |
| Procedimientos en todas las operaciones; disparadores donde correspondan | Cumplido | `scripts/04`–`06`; `08_permisos.sql` impide escribir sin procedimiento |
| Al menos 10 reportes, 6 con JOIN, mediante rutinas de la base | Cumplido: 12, 10 con JOIN | `scripts/06_reportes.sql`, pestaña Reportes |
| Script completo con datos suficientes para probar | Cumplido | `scripts/registro_comprobantes_completo.sql` (5631 comprobantes, 4 eliminados) |
| Código fuente documentado y ejecutable | Cumplido | Javadoc, `aplicativo/README.md`, `herramientas/compilar.*` |
| Memoria descriptiva de máximo 8 páginas | Cumplido (8 páginas) | `docs/memoria_descriptiva.pdf` |
| Manual de usuario | Cumplido | `docs/manual_de_usuario.pdf` |

## Entrega 6 (registro de software), pendiente

Cinco carpetas: manual (Word y PDF, con capturas y pasos), memoria (Word y PDF),
fuentes completas comprimidas con configuración y dependencias, ejecutable con
sus dependencias y un acceso de inicio que funcione desde la carpeta entregada,
y documentos personales de registro. La memoria también debe identificar
software, autores y entidad, problema, alcance, limitaciones, perfiles, base de
datos, interfaces, conclusiones y recomendaciones. La cesión de derechos
notariada y el DNI escaneado son gestiones del equipo y se entregan por el canal
oficial; no se generan firmas ni se publican en GitHub.

## Hitos

| Hito | Alcance | Estado |
|---|---|---|
| 30 % | Conexión, navegación, lectura y detalle | Terminado |
| 70 % | CRUD de catálogos y comprobantes, procedimientos, disparadores y pruebas | Terminado |
| 100 % | Reportes, datos de prueba, script completo, memoria y manual | Terminado e integrado |
