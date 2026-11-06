\set ON_ERROR_STOP on
BEGIN;
CREATE OR REPLACE PROCEDURE app.sp_reporte(IN codigo int, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  CASE codigo
    WHEN 1 THEN OPEN resultado FOR
SELECT c.serie || '-' || lpad(c.numero::text, greatest(6,length(c.numero::text)), '0') AS comprobante,
       s.tipo_comprobante,
       c.fecha_emision,
       c.importe_total
  FROM comprobante c
  JOIN serie_comprobante s ON s.serie = c.serie
 WHERE c.fecha_emision BETWEEN DATE '2026-08-25' AND DATE '2026-08-27'
 ORDER BY c.fecha_emision, c.serie, c.numero;
    WHEN 2 THEN OPEN resultado FOR
SELECT c.serie || '-' || lpad(c.numero::text, greatest(6,length(c.numero::text)), '0') AS comprobante,
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
    WHEN 3 THEN OPEN resultado FOR
SELECT c.serie || '-' || lpad(c.numero::text, greatest(6,length(c.numero::text)), '0') AS comprobante,
       c.fecha_emision,
       c.forma_pago,
       c.importe_total
  FROM comprobante c
 WHERE c.tipo_documento = 'RUC'
   AND c.numero_documento = '20900000001'
 ORDER BY c.fecha_emision DESC, c.numero DESC
 LIMIT 5;
    WHEN 4 THEN OPEN resultado FOR
SELECT r.tipo_relacionado,
       r.numero_relacionado,
       r.serie || '-' || lpad(r.numero::text, greatest(6,length(r.numero::text)), '0') AS comprobante,
       c.fecha_emision
  FROM referencia r
  JOIN comprobante c ON c.serie = r.serie AND c.numero = r.numero
 WHERE (r.tipo_relacionado, r.numero_relacionado)
       IN (('Guía de remisión', 'T001-003115'), ('Cotización', '00042-04-2026'));
    WHEN 5 THEN OPEN resultado FOR
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
    WHEN 6 THEN OPEN resultado FOR
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
    WHEN 7 THEN OPEN resultado FOR
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
    WHEN 8 THEN OPEN resultado FOR
SELECT c.serie || '-' || lpad(c.numero::text, greatest(6,length(c.numero::text)), '0') AS comprobante,
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
    WHEN 9 THEN OPEN resultado FOR
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
    WHEN 10 THEN OPEN resultado FOR
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
    WHEN 11 THEN OPEN resultado FOR
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
    WHEN 12 THEN OPEN resultado FOR
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
    ELSE RAISE EXCEPTION 'Reporte inexistente' USING ERRCODE='22023';
  END CASE;
END; $$;
COMMIT;
