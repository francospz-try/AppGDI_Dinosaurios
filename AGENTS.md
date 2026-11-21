# Desarrollo del aplicativo GDI

Leer README.md, GLOSSARY.md, docs/requisitos.md y aplicativo/README.md antes de
cambiar comportamiento. Java 21, Swing y JDBC directo; sin ORM ni frameworks de
aplicación.

- Toda escritura del aplicativo pasa por un procedimiento (`CALL sp_...`); las
  lecturas, por funciones (`SELECT * FROM fn_...`). El rol `gdi_app` no tiene
  permisos sobre las tablas para otra cosa que leer.
- Usar BigDecimal para importes. Conservar NULL y claves compuestas del modelo.
- Mantener las operaciones de base fuera del hilo de eventos de Swing (`Tarea`).
- Al cambiar un script numerado, regenerar el completo con
  `sh scripts/generar_completo.sh`; si se vuelven a crear las rutinas, ejecutar
  otra vez `08_permisos.sql`.
- Probar con `herramientas/probar.sh` sobre una base descartable recién creada.
  El script completo es destructivo: nunca ejecutarlo al abrir el aplicativo.
- No incorporar datos personales, DNI escaneados, firmas, credenciales ni
  documentos de autorización al repositorio público.
- Aplicar Superpowers (planificación y verificación) y Matt Pocock (diseño de
  módulos y vocabulario del dominio) cuando estén disponibles. No confundir
  clonar un paquete de habilidades con instalarlo en el equipo del usuario.
