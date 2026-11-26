# Registro de comprobantes

Registro de documentos comerciales ya emitidos por Sermed Ingenieros.

## Lenguaje

**Comprobante**: Boleta o factura previamente emitida, identificada por serie y
número. No acredita por sí sola un cobro recibido. La numeración se muestra como
la imprime su sistema: F001-005206 (proveedor) o EB01-50 (SUNAT).

**Cliente**: Persona o empresa identificada por tipo y número de documento. Un
comprobante sin cliente es de «CLIENTES VARIOS».

**Detalle**: Conjunto de líneas de productos o servicios de un comprobante. El
modelo admite una aparición de cada código por comprobante.

**Referencia documental**: Asociación con el tipo y número de una cotización o
guía de remisión.

**Baja lógica**: Marca `eliminado` en un cliente, producto o servicio, o
comprobante: deja de aparecer en búsquedas y reportes, pero conserva su historia.
Un comprobante eliminado puede restaurarse; un cliente o producto se reactiva al
registrarlo de nuevo.

**Rol del aplicativo**: `gdi_app`, el usuario de base de datos del aplicativo;
solo lee las tablas y ejecuta las rutinas.
