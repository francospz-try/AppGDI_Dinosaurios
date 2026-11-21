-- =====================================================================
-- Script 6: reportes y mantenimiento de los catálogos
-- Gestor: PostgreSQL 17. Ejecutar después de 05_procedimientos.sql:
--   psql -U postgres -d registro_comprobantes -f 06_reportes.sql
--
-- Hito del 100 %. Doce reportes, cada uno en una función que devuelve
-- una tabla (SELECT * FROM fn_reporte_...), que el aplicativo muestra en
-- la pestaña «Reportes». Responden a las consultas C01 a C12 de
-- 03_consultas.sql, con parámetros en lugar de valores fijos.
-- Borrado lógico: ningún reporte cuenta los comprobantes eliminados. Los
-- clientes y productos dados de baja no aparecen cuando son el objeto
-- del reporte (rankings, inactivos, evolución de precios), pero sí como
-- dato de un comprobante vigente, porque el comprobante impreso los
-- contiene.
-- También agrega los procedimientos para registrar series y monedas.
-- El script puede ejecutarse varias veces: reemplaza las rutinas.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------
-- R01. Comprobantes de un periodo (C01). Serie opcional (NULL: todas).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_periodo(
  p_desde date, p_hasta date, p_serie varchar)
RETURNS TABLE (comprobante varchar, tipo_comprobante varchar,
               fecha_emision date, documento_cliente varchar,
               cliente varchar, moneda varchar, op_gravada numeric,
               igv numeric, importe_total numeric)
LANGUAGE sql STABLE AS $$
  SELECT fn_numeracion(c.serie, c.numero), s.tipo_comprobante,
         c.fecha_emision,
         (c.tipo_documento || ' ' || c.numero_documento)::varchar,
         coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS')::varchar,
         m.nombre_moneda, c.op_gravada, c.igv, c.importe_total
    FROM comprobante c
    JOIN serie_comprobante s ON s.serie = c.serie
    JOIN moneda m ON m.codigo_moneda = c.codigo_moneda
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
   WHERE NOT c.eliminado
     AND c.fecha_emision BETWEEN p_desde AND p_hasta
     AND (fn_limpiar(p_serie) IS NULL OR c.serie = upper(btrim(p_serie)))
   ORDER BY c.fecha_emision, c.serie, c.numero;
$$;

-- ---------------------------------------------------------------------
-- R02. Detalle de un comprobante (C02): cabecera, cliente y líneas.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_detalle(p_serie varchar, p_numero integer)
RETURNS TABLE (comprobante varchar, fecha_emision date, cliente varchar,
               codigo varchar, descripcion varchar, cantidad numeric,
               unidad_medida varchar, valor_unitario numeric,
               precio_unitario numeric, importe_venta numeric)
LANGUAGE sql STABLE AS $$
  SELECT fn_numeracion(c.serie, c.numero), c.fecha_emision,
         coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS')::varchar,
         p.codigo, p.descripcion, i.cantidad, p.unidad_medida,
         i.valor_unitario, i.precio_unitario, i.importe_venta
    FROM comprobante c
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
    JOIN incluye i ON i.serie = c.serie AND i.numero = c.numero
    JOIN producto_servicio p ON p.codigo = i.codigo
   WHERE NOT c.eliminado
     AND c.serie = upper(btrim(p_serie)) AND c.numero = p_numero
   ORDER BY p.codigo;
$$;

-- ---------------------------------------------------------------------
-- R03. Comprobantes de un cliente (C03), del más reciente al más
-- antiguo, con el importe acumulado desde su primera compra.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_cliente(p_numero_documento varchar)
RETURNS TABLE (comprobante varchar, tipo_comprobante varchar,
               fecha_emision date, forma_pago varchar, lineas bigint,
               importe_total numeric, importe_acumulado numeric)
LANGUAGE sql STABLE AS $$
  SELECT fn_numeracion(c.serie, c.numero), s.tipo_comprobante,
         c.fecha_emision, c.forma_pago,
         (SELECT count(*) FROM incluye i
           WHERE i.serie = c.serie AND i.numero = c.numero),
         c.importe_total,
         sum(c.importe_total) OVER (ORDER BY c.fecha_emision, c.serie, c.numero)
    FROM comprobante c
    JOIN cliente cl
      ON cl.tipo_documento = c.tipo_documento
     AND cl.numero_documento = c.numero_documento
    JOIN serie_comprobante s ON s.serie = c.serie
   WHERE NOT c.eliminado
     AND NOT cl.eliminado
     AND cl.numero_documento = btrim(p_numero_documento)
   ORDER BY c.fecha_emision DESC, c.serie DESC, c.numero DESC;
$$;

-- ---------------------------------------------------------------------
-- R04. Comprobantes que citan un documento relacionado (C04). Tipo
-- opcional; número completo o su comienzo (T001-0031 encuentra todas
-- las guías de T001-003100 a T001-003199).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_documento(
  p_tipo_relacionado varchar, p_numero_relacionado varchar)
RETURNS TABLE (tipo_relacionado varchar, numero_relacionado varchar,
               comprobante varchar, fecha_emision date, cliente varchar,
               importe_total numeric)
LANGUAGE sql STABLE AS $$
  SELECT r.tipo_relacionado, r.numero_relacionado,
         fn_numeracion(c.serie, c.numero), c.fecha_emision,
         coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS')::varchar,
         c.importe_total
    FROM referencia r
    JOIN comprobante c ON c.serie = r.serie AND c.numero = r.numero
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
   WHERE NOT c.eliminado
     AND (fn_limpiar(p_tipo_relacionado) IS NULL
          OR r.tipo_relacionado = p_tipo_relacionado)
     AND r.numero_relacionado LIKE coalesce(upper(btrim(p_numero_relacionado)), '') || '%'
   ORDER BY r.tipo_relacionado, r.numero_relacionado, c.serie, c.numero
   LIMIT 500;
$$;

-- ---------------------------------------------------------------------
-- R05. Ventas mensuales por tipo de comprobante en un año (C05).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_ventas_mensuales(p_anio integer)
RETURNS TABLE (mes varchar, tipo_comprobante varchar, comprobantes bigint,
               op_gravada numeric, igv numeric, importe_total numeric)
LANGUAGE sql STABLE AS $$
  SELECT to_char(c.fecha_emision, 'YYYY-MM')::varchar, s.tipo_comprobante,
         count(*), sum(c.op_gravada), sum(c.igv), sum(c.importe_total)
    FROM comprobante c
    JOIN serie_comprobante s ON s.serie = c.serie
   WHERE NOT c.eliminado
     AND c.fecha_emision >= make_date(p_anio, 1, 1)
     AND c.fecha_emision <  make_date(p_anio + 1, 1, 1)
   GROUP BY 1, s.tipo_comprobante
   ORDER BY 1, s.tipo_comprobante;
$$;

-- ---------------------------------------------------------------------
-- R06. Productos o servicios con mayor importe vendido en un periodo
-- (C06), con su participación en el total vendido del periodo.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_top_productos(
  p_desde date, p_hasta date, p_limite integer)
RETURNS TABLE (codigo varchar, descripcion varchar, unidad_medida varchar,
               comprobantes bigint, cantidad numeric, importe_vendido numeric,
               participacion_pct numeric)
LANGUAGE sql STABLE AS $$
  SELECT p.codigo, p.descripcion, p.unidad_medida, count(*),
         sum(i.cantidad), round(sum(i.importe_venta), 2),
         round(100 * sum(i.importe_venta) / sum(sum(i.importe_venta)) OVER (), 1)
    FROM incluye i
    JOIN comprobante c ON c.serie = i.serie AND c.numero = i.numero
    JOIN producto_servicio p ON p.codigo = i.codigo
   WHERE NOT c.eliminado
     AND NOT p.eliminado
     AND c.fecha_emision BETWEEN p_desde AND p_hasta
   GROUP BY p.codigo, p.descripcion, p.unidad_medida
   ORDER BY 6 DESC, p.codigo
   LIMIT p_limite;
$$;

-- ---------------------------------------------------------------------
-- R07. Clientes con mayor importe registrado en un periodo (C07), con su
-- primera y su última compra y el importe medio por comprobante.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_top_clientes(
  p_desde date, p_hasta date, p_limite integer)
RETURNS TABLE (documento_cliente varchar, cliente varchar,
               comprobantes bigint, primera_compra date, ultima_compra date,
               importe_total numeric, importe_medio numeric)
LANGUAGE sql STABLE AS $$
  SELECT (cl.tipo_documento || ' ' || cl.numero_documento)::varchar,
         cl.nombre_razon_social, count(*), min(c.fecha_emision),
         max(c.fecha_emision), sum(c.importe_total),
         round(avg(c.importe_total), 2)
    FROM comprobante c
    JOIN cliente cl
      ON cl.tipo_documento = c.tipo_documento
     AND cl.numero_documento = c.numero_documento
   WHERE NOT c.eliminado
     AND NOT cl.eliminado
     AND c.fecha_emision BETWEEN p_desde AND p_hasta
   GROUP BY cl.tipo_documento, cl.numero_documento, cl.nombre_razon_social
   ORDER BY 6 DESC, 2
   LIMIT p_limite;
$$;

-- ---------------------------------------------------------------------
-- R08. Cuadre de importes de los comprobantes de un periodo (C08): suma
-- de operaciones y tributos, suma de las líneas e importe total. Con
-- p_solo_diferencias, solo los que no cuadran al céntimo.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_cuadre(
  p_desde date, p_hasta date, p_solo_diferencias boolean)
RETURNS TABLE (comprobante varchar, fecha_emision date,
               suma_componentes numeric, suma_lineas numeric,
               importe_total numeric, cuadra boolean)
LANGUAGE sql STABLE AS $$
  SELECT x.comprobante, x.fecha_emision, x.suma_componentes,
         x.suma_lineas, x.importe_total,
         x.suma_componentes = x.importe_total
           AND round(x.suma_lineas, 2) = x.importe_total
    FROM (SELECT fn_numeracion(c.serie, c.numero) AS comprobante,
                 c.serie, c.numero, c.fecha_emision, c.importe_total,
                 c.op_gravada + c.op_exonerada + c.op_inafecta + c.igv
                   + coalesce(c.isc, 0) + coalesce(c.otros_cargos, 0)
                   + coalesce(c.otros_tributos, 0)
                   + coalesce(c.monto_redondeo, 0) AS suma_componentes,
                 coalesce(l.suma, 0) AS suma_lineas
            FROM comprobante c
            LEFT JOIN (SELECT i.serie, i.numero, sum(i.importe_venta) AS suma
                         FROM incluye i
                        GROUP BY i.serie, i.numero) l
                   ON l.serie = c.serie AND l.numero = c.numero
           WHERE NOT c.eliminado
             AND c.fecha_emision BETWEEN p_desde AND p_hasta) x
   WHERE NOT coalesce(p_solo_diferencias, FALSE)
      OR x.suma_componentes <> x.importe_total
      OR round(x.suma_lineas, 2) <> x.importe_total
   ORDER BY x.fecha_emision, x.serie, x.numero;
$$;

-- ---------------------------------------------------------------------
-- R09. Diferencia entre las líneas y el importe total por serie (C09):
-- la diferencia máxima muestra el efecto de los 7 decimales de EB01.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_diferencia_lineas()
RETURNS TABLE (serie varchar, comprobantes bigint,
               diferencia_maxima numeric, con_diferencia_al_centimo bigint)
LANGUAGE sql STABLE AS $$
  SELECT d.serie, count(*), max(abs(d.importe_total - d.suma_lineas)),
         count(*) FILTER (WHERE round(d.suma_lineas, 2) <> d.importe_total)
    FROM (SELECT c.serie, c.importe_total,
                 (SELECT sum(i.importe_venta)
                    FROM incluye i
                   WHERE i.serie = c.serie AND i.numero = c.numero) AS suma_lineas
            FROM comprobante c
           WHERE NOT c.eliminado) d
   GROUP BY d.serie
   ORDER BY d.serie;
$$;

-- ---------------------------------------------------------------------
-- R10. Evolución anual del precio de un producto o servicio (C10). En la
-- serie de la SUNAT, que no imprime el precio unitario, se usa el valor
-- unitario con IGV redondeado al céntimo.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_precio_producto(p_codigo varchar)
RETURNS TABLE (anio integer, lineas bigint, precio_minimo numeric,
               precio_maximo numeric, precio_medio numeric, unidades numeric)
LANGUAGE sql STABLE AS $$
  SELECT extract(year FROM c.fecha_emision)::int, count(*),
         round(min(coalesce(i.precio_unitario, i.valor_unitario * 1.18)), 2),
         round(max(coalesce(i.precio_unitario, i.valor_unitario * 1.18)), 2),
         round(avg(coalesce(i.precio_unitario, i.valor_unitario * 1.18)), 2),
         sum(i.cantidad)
    FROM incluye i
    JOIN comprobante c ON c.serie = i.serie AND c.numero = i.numero
    JOIN producto_servicio p ON p.codigo = i.codigo
   WHERE NOT c.eliminado
     AND NOT p.eliminado
     AND p.codigo = upper(btrim(p_codigo))
   GROUP BY 1
   ORDER BY 1;
$$;

-- ---------------------------------------------------------------------
-- R11. Huecos en la numeración de cada serie (C11): rangos de números
-- sin comprobante vigente entre el primero y el último registrados.
-- Serie opcional (NULL: todas).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_numeracion(p_serie varchar)
RETURNS TABLE (serie varchar, desde varchar, hasta varchar, faltantes integer)
LANGUAGE sql STABLE AS $$
  SELECT n.serie, fn_numeracion(n.serie, n.anterior + 1),
         fn_numeracion(n.serie, n.numero - 1), n.numero - n.anterior - 1
    FROM (SELECT c.serie, c.numero,
                 lag(c.numero) OVER (PARTITION BY c.serie ORDER BY c.numero) AS anterior
            FROM comprobante c
           WHERE NOT c.eliminado
             AND (fn_limpiar(p_serie) IS NULL OR c.serie = upper(btrim(p_serie)))) n
   WHERE n.numero - n.anterior > 1
   ORDER BY n.serie, n.numero;
$$;

-- ---------------------------------------------------------------------
-- R12. Clientes que compraron el año anterior y no en el año indicado
-- (C12), con lo que compraron y su última compra.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_clientes_inactivos(p_anio integer)
RETURNS TABLE (documento_cliente varchar, cliente varchar,
               comprobantes_anio_anterior bigint,
               importe_anio_anterior numeric, ultima_compra date)
LANGUAGE sql STABLE AS $$
  SELECT (cl.tipo_documento || ' ' || cl.numero_documento)::varchar,
         cl.nombre_razon_social, count(*), sum(c.importe_total),
         max(c.fecha_emision)
    FROM cliente cl
    JOIN comprobante c
      ON c.tipo_documento = cl.tipo_documento
     AND c.numero_documento = cl.numero_documento
   WHERE NOT cl.eliminado
     AND NOT c.eliminado
     AND c.fecha_emision >= make_date(p_anio - 1, 1, 1)
     AND c.fecha_emision <  make_date(p_anio, 1, 1)
     AND NOT EXISTS (SELECT 1 FROM comprobante n
                      WHERE n.tipo_documento = cl.tipo_documento
                        AND n.numero_documento = cl.numero_documento
                        AND NOT n.eliminado
                        AND n.fecha_emision >= make_date(p_anio, 1, 1))
   GROUP BY cl.tipo_documento, cl.numero_documento, cl.nombre_razon_social
   ORDER BY 4 DESC, 2;
$$;

-- ---------------------------------------------------------------------
-- Mantenimiento de los catálogos
-- ---------------------------------------------------------------------

-- Registra una serie de comprobante (por ejemplo, cuando la SUNAT o el
-- proveedor asignan una serie nueva). Las restricciones de la tabla
-- verifican el formato y el tipo.
CREATE OR REPLACE PROCEDURE sp_insertar_serie(p_serie varchar, p_tipo_comprobante varchar)
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM serie_comprobante WHERE serie = upper(btrim(p_serie))) THEN
    RAISE EXCEPTION 'La serie % ya está registrada.', upper(btrim(p_serie))
      USING ERRCODE = 'unique_violation';
  END IF;
  INSERT INTO serie_comprobante (serie, tipo_comprobante)
  VALUES (upper(btrim(p_serie)), fn_limpiar(p_tipo_comprobante));
END;
$$;

-- Registra una moneda con su código ISO 4217 (por ejemplo, USD).
CREATE OR REPLACE PROCEDURE sp_insertar_moneda(p_codigo_moneda varchar, p_nombre_moneda varchar)
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM moneda WHERE codigo_moneda = upper(btrim(p_codigo_moneda))) THEN
    RAISE EXCEPTION 'La moneda % ya está registrada.', upper(btrim(p_codigo_moneda))
      USING ERRCODE = 'unique_violation';
  END IF;
  INSERT INTO moneda (codigo_moneda, nombre_moneda)
  VALUES (upper(btrim(p_codigo_moneda)), upper(fn_limpiar(p_nombre_moneda)));
END;
$$;
