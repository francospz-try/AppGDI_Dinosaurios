# Primer incremento del cliente GDI

## Intención y alcance
Hacer demostrable la conexión de una interfaz de escritorio a la BD existente.
El encargado consulta comprobantes, abre cabecera/detalle/referencias, mantiene
clientes y ejecuta los doce reportes existentes. No emitir comprobantes ni cobrar.

## Enfoque
Decisión técnica del implementador, basada en el inicio autorizado tras la propuesta:
Java 21 + Swing + JDBC. Evita un servidor web adicional y respeta el acceso nativo.
Una aplicación web sin framework es viable, pero añade despliegue y sesiones para
este primer incremento. Un ORM contradice la restricción del docente.

## Diseño
DbConfig carga un archivo local externo. Database abre conexiones efímeras con
timeouts, ejecuta CALL parametrizado y consume cursores dentro de una transacción.
GdiRepository ofrece operaciones del dominio. La ventana Swing usa SwingWorker para
evitar bloqueos. Campos monetarios se obtienen como BigDecimal sin convertir a double.
La base conserva sus ocho tablas; una migración agrega baja lógica de clientes y
procedimientos en el esquema app. Nuevos clientes pueden existir antes de su primer
comprobante: se documenta este cambio de participación frente al modelo original.

## Verificación
Pruebas en las interfaces públicas de conexión y repositorio, contra PostgreSQL 17
con la carga original: 5328 comprobantes, EB01-50 total 120.00 y línea 119.9999997,
CLIENTES VARIOS sin identificación ficticia, doce reportes, CRUD y baja de cliente,
duplicados, conexión fallida y rollback. Verificación visual adicional antes de
considerar terminada la pantalla. No se declara prueba en Windows sin ejecutarla.
