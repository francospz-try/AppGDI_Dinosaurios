# Desarrollo del aplicativo GDI

Leer README.md, GLOSSARY.md y docs/requisitos.md antes de cambiar comportamiento.
Java 21, Swing y JDBC directo; sin ORM ni frameworks de aplicación.
Todas las consultas y escrituras funcionales deben ejecutar procedimientos PostgreSQL.
Usar BigDecimal para importes. Conservar NULL y claves compuestas del modelo.
Mantener operaciones de base fuera del hilo de eventos Swing.
No incorporar datos personales, DNI escaneados, firmas, credenciales ni documentos
de autorización al repositorio público.
Ejecutar scripts/test.sh para las pruebas de integración; requiere base de prueba
configurada. Los scripts originales de creación son destructivos: nunca ejecutarlos
automáticamente al abrir el aplicativo.
Aplicar Superpowers (planificación y verificación) y Matt Pocock (diseño de módulos
y vocabulario del dominio) cuando estén disponibles. No confundir clonar un paquete
de habilidades con instalarlo en el equipo Windows del usuario.
