# Primer incremento GDI Implementation Plan

**Goal:** conexión y consulta funcional de la BD de Sermed desde escritorio.
**Architecture:** configuración externa, procedimientos, repositorio JDBC, Swing.
**Tech Stack:** Java 21, PostgreSQL 17+, pgJDBC, sin frameworks.
**Spec:** ../specs/2026-11-05-primer-incremento-design.md

## Review Focus
- Credenciales erróneas: error sin divulgar contraseña, UI sigue respondiendo.
- EB01-50: conservar 119.9999997 y cliente sin documento.
- Búsqueda vacía o con comillas: parámetros, sin SQL concatenado del usuario.
- Cliente duplicado/inválido: rollback y mensaje comprensible.
- Baja lógica: historial del comprobante conservado y registro fuera de lista activa.

## Tareas
- [x] Conexión: DbConfig.load(Path), Database.query(String,Object...), prueba de
  conexión mediante app.sp_probar_conexion(refcursor). Crear test que falle antes
  de implementar; compilar y ejecutarlo contra BD real.
- [x] Lectura: GdiRepository.comprobantes(String), ficha(String,int),
  detalle(String,int), referencias(String,int). Probar EB01-50 y F001-5206.
- [x] Clientes: clientes(String), guardarCliente(...), bajaCliente(...).
  Probar alta, lectura, cambio, duplicado y baja lógica a través del repositorio.
- [x] Reportes: reporte(int), 12 opciones basadas en SQL existente; prueba de todas
  las llamadas y al menos seis consultas con JOIN. No confundir resultados vacíos
  de un reporte de inconsistencias con errores.
- [x] Interfaz: conexión, lista, ficha, clientes y reportes. SwingWorker y tablas
  de solo lectura; confirmación de bajas y errores traducidos.
- [x] Distribución: scripts Linux y Windows, bootstrap de ruta pedida y paquetes
  de habilidades, instrucciones reproducibles, revisión de secretos y commit.

Ejecutar las tareas secuencialmente en esta sesión. La autorización de empezar el
desarrollo cubre este incremento; no se solicita aprobación de nuevo.
