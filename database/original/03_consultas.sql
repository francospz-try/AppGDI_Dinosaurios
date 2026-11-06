-- =====================================================================
-- Script 3 de 3: consultas sobre registro_comprobantes
-- Gestor: PostgreSQL 17. Ejecutar después de 02_insercion.sql:
--   psql -U postgres -d registro_comprobantes -f 03_consultas.sql
-- Cada consulta responde a una necesidad de la funcionalidad de
-- registro de comprobantes de pago; van de las más simples a las más
-- complejas.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------
-- C01. Comprobantes registrados en un periodo.
-- Necesidad: ubicar los comprobantes emitidos entre dos fechas para
-- revisarlos.
-- ---------------------------------------------------------------------
SELECT c.serie || '-' || lpad(c.numero::text, 6, '0') AS comprobante,
       s.tipo_comprobante,
       c.fecha_emision,
       c.importe_total
  FROM comprobante c
  JOIN serie_comprobante s ON s.serie = c.serie
 WHERE c.fecha_emision BETWEEN DATE '2026-08-25' AND DATE '2026-08-27'
 ORDER BY c.fecha_emision, c.serie, c.numero;

-- ---------------------------------------------------------------------
-- C02. Detalle completo de un comprobante.
-- Necesidad: consultar un comprobante tal como fue registrado: cabecera,
-- cliente y líneas de detalle.
-- ---------------------------------------------------------------------
SELECT c.serie || '-' || lpad(c.numero::text, 6, '0') AS comprobante,
       c.fecha_emision,
       m.nombre_moneda,
       coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS') AS cliente,
       p.codigo,
       p.descripcion,
       i.cantidad,
       p.unidad_medida,
       i.valor_unitario,
       i.precio_unitario,
       i.importe_venta
  FROM comprobante c
  JOIN moneda m ON m.codigo_moneda = c.codigo_moneda
  LEFT JOIN cliente cl
         ON cl.tipo_documento = c.tipo_documento
        AND cl.numero_documento = c.numero_documento
  JOIN incluye i ON i.serie = c.serie AND i.numero = c.numero
  JOIN producto_servicio p ON p.codigo = i.codigo
 WHERE c.serie = 'F001' AND c.numero = 5206;

-- ---------------------------------------------------------------------
-- C03. Comprobantes de un cliente.
-- Necesidad: reunir las operaciones de un mismo cliente (los cinco
-- comprobantes más recientes de un RUC).
-- ---------------------------------------------------------------------
SELECT c.serie || '-' || lpad(c.numero::text, 6, '0') AS comprobante,
       c.fecha_emision,
       c.forma_pago,
       c.importe_total
  FROM comprobante c
 WHERE c.tipo_documento = 'RUC'
   AND c.numero_documento = '20900000001'
 ORDER BY c.fecha_emision DESC, c.numero DESC
 LIMIT 5;

-- ---------------------------------------------------------------------
-- C04. Documentos citados por los comprobantes.
-- Necesidad: encontrar qué comprobante citó una guía de remisión o una
-- cotización determinada.
-- ---------------------------------------------------------------------
SELECT r.tipo_relacionado,
       r.numero_relacionado,
       r.serie || '-' || lpad(r.numero::text, 6, '0') AS comprobante,
       c.fecha_emision
  FROM referencia r
  JOIN comprobante c ON c.serie = r.serie AND c.numero = r.numero
 WHERE (r.tipo_relacionado, r.numero_relacionado)
       IN (('Guía de remisión', 'T001-003115'), ('Cotización', '00042-04-2026'));

-- ---------------------------------------------------------------------
-- C05. Ventas mensuales por tipo de comprobante en 2026.
-- Necesidad: resumir los importes registrados por mes.
-- ---------------------------------------------------------------------
SELECT to_char(c.fecha_emision, 'YYYY-MM') AS mes,
       s.tipo_comprobante,
       count(*) AS comprobantes,
       sum(c.op_gravada) AS op_gravada,
       sum(c.igv) AS igv,
       sum(c.importe_total) AS importe_total
  FROM comprobante c
  JOIN serie_comprobante s ON s.serie = c.serie
 WHERE c.fecha_emision >= DATE '2026-01-01'
   AND c.fecha_emision <  DATE '2027-01-01'
 GROUP BY to_char(c.fecha_emision, 'YYYY-MM'), s.tipo_comprobante
 ORDER BY mes, s.tipo_comprobante;

-- ---------------------------------------------------------------------
-- C06. Los diez productos o servicios con mayor importe vendido.
-- Necesidad: reunir las operaciones de un mismo producto o servicio.
-- ---------------------------------------------------------------------
SELECT p.codigo,
       p.descripcion,
       p.unidad_medida,
       sum(i.cantidad) AS cantidad,
       round(sum(i.importe_venta), 2) AS importe_vendido
  FROM incluye i
  JOIN producto_servicio p ON p.codigo = i.codigo
 GROUP BY p.codigo, p.descripcion, p.unidad_medida
 ORDER BY importe_vendido DESC
 LIMIT 10;

-- ---------------------------------------------------------------------
-- C07. Clientes con mayor importe registrado.
-- Necesidad: identificar a los clientes con más operaciones (al menos
-- 20 comprobantes), con su primera y su última compra.
-- ---------------------------------------------------------------------
SELECT cl.numero_documento,
       cl.nombre_razon_social,
       count(*) AS comprobantes,
       min(c.fecha_emision) AS primera,
       max(c.fecha_emision) AS ultima,
       sum(c.importe_total) AS importe_total
  FROM comprobante c
  JOIN cliente cl
    ON cl.tipo_documento = c.tipo_documento
   AND cl.numero_documento = c.numero_documento
 GROUP BY cl.numero_documento, cl.nombre_razon_social
HAVING count(*) >= 20
 ORDER BY importe_total DESC
 LIMIT 5;

-- ---------------------------------------------------------------------
-- C08. Comprobantes cuyos importes no suman el total.
-- Necesidad: contrastar que las operaciones y los tributos sumen el
-- importe total (tarea «Registro de datos económicos»). Un resultado
-- vacío indica que no hay errores de transcripción.
-- ---------------------------------------------------------------------
SELECT c.serie || '-' || lpad(c.numero::text, 6, '0') AS comprobante,
       c.importe_total,
       c.op_gravada + c.op_exonerada + c.op_inafecta + c.igv
         + coalesce(c.isc, 0) + coalesce(c.otros_cargos, 0)
         + coalesce(c.otros_tributos, 0) + coalesce(c.monto_redondeo, 0)
         AS suma_componentes
  FROM comprobante c
 WHERE c.op_gravada + c.op_exonerada + c.op_inafecta + c.igv
         + coalesce(c.isc, 0) + coalesce(c.otros_cargos, 0)
         + coalesce(c.otros_tributos, 0) + coalesce(c.monto_redondeo, 0)
       <> c.importe_total;

-- ---------------------------------------------------------------------
-- C09. Diferencia entre las líneas de detalle y el importe total.
-- Necesidad: comprobar, por serie, que la suma de los importes de venta
-- de las líneas coincide con el importe total impreso (subconsulta).
-- ---------------------------------------------------------------------
SELECT d.serie,
       count(*) AS comprobantes,
       max(abs(d.importe_total - d.suma_lineas)) AS diferencia_maxima,
       count(*) FILTER (WHERE round(d.suma_lineas, 2) <> d.importe_total)
         AS con_diferencia_al_centimo
  FROM (SELECT c.serie, c.importe_total,
               (SELECT sum(i.importe_venta)
                  FROM incluye i
                 WHERE i.serie = c.serie AND i.numero = c.numero) AS suma_lineas
          FROM comprobante c) d
 GROUP BY d.serie
 ORDER BY d.serie;

-- ---------------------------------------------------------------------
-- C10. Evolución del precio de un producto.
-- Necesidad: comparar los valores con que un mismo producto se vendió en
-- distintos comprobantes (producto 349).
-- ---------------------------------------------------------------------
SELECT extract(year FROM c.fecha_emision)::int AS anio,
       count(*) AS lineas,
       min(i.precio_unitario) AS precio_minimo,
       max(i.precio_unitario) AS precio_maximo,
       sum(i.cantidad) AS unidades
  FROM incluye i
  JOIN comprobante c ON c.serie = i.serie AND c.numero = i.numero
 WHERE i.codigo = '349'
 GROUP BY 1
 ORDER BY 1;

-- ---------------------------------------------------------------------
-- C11. Continuidad de la numeración por serie.
-- Necesidad: detectar comprobantes emitidos que aún no se registraron,
-- como huecos en la numeración de cada serie (función de ventana).
-- ---------------------------------------------------------------------
SELECT serie,
       count(*) AS registrados,
       min(numero) AS primero,
       max(numero) AS ultimo,
       count(*) FILTER (WHERE numero - anterior > 1) AS huecos
  FROM (SELECT serie, numero,
               lag(numero) OVER (PARTITION BY serie ORDER BY numero) AS anterior
          FROM comprobante) n
 GROUP BY serie
 ORDER BY serie;

-- ---------------------------------------------------------------------
-- C12. Clientes que compraron en 2025 y no en 2026.
-- Necesidad: identificar clientes sin operaciones registradas en el año
-- en curso (subconsulta con NOT EXISTS).
-- ---------------------------------------------------------------------
SELECT count(*) AS clientes_sin_compras_2026
  FROM cliente cl
 WHERE EXISTS (SELECT 1 FROM comprobante c
                WHERE c.tipo_documento = cl.tipo_documento
                  AND c.numero_documento = cl.numero_documento
                  AND c.fecha_emision >= DATE '2025-01-01'
                  AND c.fecha_emision <  DATE '2026-01-01')
   AND NOT EXISTS (SELECT 1 FROM comprobante c
                    WHERE c.tipo_documento = cl.tipo_documento
                      AND c.numero_documento = cl.numero_documento
                      AND c.fecha_emision >= DATE '2026-01-01');
