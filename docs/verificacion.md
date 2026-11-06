# Verificación del primer incremento

Fecha: 5 de noviembre de 2026. Entorno ejecutado: Linux, OpenJDK 21.0.13,
compilador Eclipse ECJ 3.40.0 (el entorno tiene JRE sin javac), pgJDBC 42.7.7 y
PostgreSQL 17.11. Los scripts entregados usan javac del JDK 21 en el equipo destino.

## Evidencia

- Base descartable con los scripts originales: 5328 comprobantes.
- 37 comprobaciones de integración desde GdiRepository, usando el rol gdi_app.
- Conservación de EB01-50: total 120.00, detalle 119.9999997, cliente sin documento.
- Búsqueda normal, con ceros impresos y con una cadena de intento de inyección.
- Doce reportes invocados por procedimiento; siete consultas contienen JOIN.
- Alta, modificación, baja lógica, duplicados, restricciones y rollback.
- Conexión fallida y mensajes sin divulgación de contraseña.
- Ordenación de cabecera con valores heterogéneos sin ClassCastException.
- Búsqueda del nombre visible CLIENTES VARIOS.
- Eventos Swing en modo headless: botón de conexión y carga real de 500 filas.
- Renderizado de conexión, comprobantes y clientes para inspección visual.

Prueba observada antes de corregir: `F001-005206` no encontraba el registro aunque
`F001-5206` sí. Se añadió la búsqueda con formato impreso y pasó la misma prueba.

## Límites

No se ha ejecutado en el Windows del usuario ni se ha validado allí PowerShell,
el doble clic, PostgreSQL instalado o las junctions de habilidades. La prueba
headless comprueba eventos y pintura Swing; no sustituye una prueba completa con
teclado, ratón y ventanas modales en Windows.

El hito 30 % es una propuesta de alcance y queda sujeto a la demostración en el
equipo de entrega. No se afirma 70 % ni 100 %. El trigger original todavía no
impide retirar la última línea de un comprobante ya existente; se resolverá al
implementar el CRUD de comprobantes. No hay emisión electrónica ni cobros.
