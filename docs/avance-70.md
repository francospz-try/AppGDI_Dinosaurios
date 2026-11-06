# Incremento hasta el 70 %

Alcance solicitado: completar CRUD de las ocho tablas del modelo y ejecutar el aplicativo.
No elaborar todavía la memoria o el manual finales.

- Cabecera, detalle y referencias se registran/actualizan en una transacción mediante procedimiento.
- Baja lógica de comprobantes y catálogos; conservar detalle e historial consultable.
- Catálogos: clientes, series, monedas, productos/servicios y documentos relacionados.
- Editores preservan NULL, claves compuestas, dos decimales de cabecera y siete de línea.
- Control de versión del comprobante para impedir sobrescritura de una edición concurrente.
- Trigger diferido también sobre cambios/borrados de detalle; no permitir quitar la última línea.
- Referencias nuevas se crean junto con el comprobante; corregir identidad de un documento relacionado actualiza sus asociaciones dentro de la misma transacción.
- Reportes operativos usan comprobantes activos; las bajas pueden consultarse por separado.
- Pruebas contra PostgreSQL y ejecución de la interfaz; indicar expresamente dónde se ejecutó.

Verificar: alta, edición, lectura, baja, claves duplicadas, producto inexistente,
rollback completo, último detalle, precisión decimal, referencias múltiples y edición obsoleta.
