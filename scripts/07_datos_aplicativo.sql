-- =====================================================================
-- Script 7: datos registrados con los procedimientos del aplicativo
-- Gestor: PostgreSQL 17. Ejecutar después de 06_reportes.sql:
--   psql -U postgres -d registro_comprobantes -f 07_datos_aplicativo.sql
--
-- Completa los datos de 02_insercion.sql para probar el aplicativo y sus
-- reportes con meses recientes y con borrados lógicos:
-- Parte A: comprobantes emitidos del 1 de setiembre al 20 de noviembre de
-- 2026 en las tres series, después de los comprobantes analizados, con
-- el ritmo y los formatos de los datos generados de 02_insercion.sql. No
-- se insertan en las tablas: se registran con sp_insertar_comprobante,
-- como lo hace el aplicativo, de modo que los disparadores verifican sus
-- importes y los clientes y productos se crean o reactivan igual que
-- desde el formulario. Clientes nuevos y productos son ficticios.
-- Parte B: borrados lógicos y restauraciones con los procedimientos:
-- comprobantes eliminados (registrados por error) y restaurados, clientes
-- dados de baja y reactivados, y productos descontinuados.
-- El generador es determinista: cada ejecución produce los mismos datos.
-- =====================================================================

\set ON_ERROR_STOP on

-- Número pseudoaleatorio en [0, 1) que depende solo de la semilla (el
-- mismo generador de 02_insercion.sql)
CREATE OR REPLACE FUNCTION pg_temp.azar(semilla text) RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$
  SELECT ('x' || substr(md5(semilla), 1, 8))::bit(32)::bigint / 4294967296.0
$$;

-- Precio vigente de cada producto: el de su última venta. Ámbito EB:
-- artículos de la serie EB01; BF: productos y servicios de B001 y F001.
-- El servicio 262 es propio de la cotización de B001-000072 y no se
-- vuelve a vender.
CREATE TEMP TABLE precio AS
SELECT x.codigo, x.unidad_medida, x.precio, x.ambito,
       row_number() OVER (PARTITION BY x.ambito ORDER BY x.precio, x.codigo) - 1 AS orden
  FROM (SELECT DISTINCT ON (i.codigo)
               i.codigo, p.unidad_medida,
               coalesce(i.precio_unitario, round(i.valor_unitario * 1.18, 2)) AS precio,
               CASE WHEN c.serie = 'EB01' THEN 'EB' ELSE 'BF' END AS ambito
          FROM incluye i
          JOIN comprobante c ON c.serie = i.serie AND c.numero = i.numero
          JOIN producto_servicio p ON p.codigo = i.codigo
         WHERE NOT p.eliminado AND i.codigo <> '262'
         ORDER BY i.codigo, c.fecha_emision DESC, c.numero DESC) x;

-- Nombre y dirección de la empresa o persona ficticia número i, con las
-- mismas fórmulas de 02_insercion.sql: los números 1 a 900 (RUC) y 1 a 60
-- (DNI) ya pueden existir; los siguientes son clientes nuevos.
CREATE OR REPLACE FUNCTION pg_temp.empresa(i integer, OUT ruc text, OUT nombre text, OUT direccion text)
LANGUAGE sql IMMUTABLE AS $$
  SELECT '209' || lpad(i::text, 8, '0'),
         (ARRAY['CLÍNICA', 'LABORATORIO CLÍNICO', 'CENTRO MÉDICO', 'POLICLÍNICO',
                'DROGUERÍA', 'BOTICA', 'CENTRO ODONTOLÓGICO', 'LABORATORIO DE ANÁLISIS'])[1 + i % 8]
         || ' ' ||
         (ARRAY['SAN JOSÉ', 'SANTA MARÍA', 'LOS ANDES', 'DEL SUR', 'VALLE SAGRADO',
                'MISTI', 'CHACHANI', 'SAN MIGUEL', 'SANTA ROSA', 'LA MERCED',
                'SAN JUAN', 'VIRGEN DE CHAPI', 'SEÑOR DE LUREN', 'SAN PABLO',
                'SANTA ANA', 'LOS OLIVOS', 'EL CARMEN', 'SAN FRANCISCO',
                'SAN LUCAS', 'LA ESPERANZA', 'SANTA TERESA', 'SAN AGUSTÍN',
                'LOS PINOS', 'SAN ANTONIO', 'SANTA CLARA', 'VALLE DEL COLCA',
                'PACHACÚTEC', 'INTI', 'SAN MARTÍN', 'LA FAMILIA',
                'SALUD INTEGRAL', 'VIDA SANA', 'BIENESTAR', 'SANTA CRUZ',
                'SAN PEDRO', 'LOS ÁLAMOS', 'LA PAZ', 'SAN CRISTÓBAL',
                'SAN VICENTE', 'LAS LOMAS'])[1 + (i * 7) % 40]
         || ' ' ||
         (ARRAY['S.A.C.', 'E.I.R.L.', 'S.R.L.'])[1 + (i / 8) % 3],
         (ARRAY['AV. EJÉRCITO', 'CALLE MERCADERES', 'AV. INDEPENDENCIA',
                'CALLE SANTA CATALINA', 'AV. GOYENECHE', 'AV. PARRA', 'JR. TACNA',
                'AV. LA CULTURA', 'AV. EL SOL', 'JR. LIMA', 'AV. BOLOGNESI',
                'CALLE AREQUIPA'])[1 + (i * 5) % 12]
         || ' ' || (100 + (i * 37) % 900)::text || ' - ' ||
         (ARRAY['CAYMA - AREQUIPA - AREQUIPA', 'YANAHUARA - AREQUIPA - AREQUIPA',
                'AREQUIPA - AREQUIPA - AREQUIPA', 'JOSÉ LUIS BUSTAMANTE Y RIVERO - AREQUIPA - AREQUIPA',
                'CERRO COLORADO - AREQUIPA - AREQUIPA', 'PAUCARPATA - AREQUIPA - AREQUIPA',
                'WANCHAQ - CUSCO - CUSCO', 'CUSCO - CUSCO - CUSCO',
                'TACNA - TACNA - TACNA', 'MOQUEGUA - MARISCAL NIETO - MOQUEGUA',
                'JULIACA - SAN ROMÁN - PUNO', 'PUNO - PUNO - PUNO'])[1 + (i * 3) % 12];
$$;

CREATE OR REPLACE FUNCTION pg_temp.persona(i integer, OUT dni text, OUT nombre text)
LANGUAGE sql IMMUTABLE AS $$
  SELECT '9' || lpad(i::text, 7, '0'),
         (ARRAY['QUISPE', 'MAMANI', 'HUAMANI', 'FLORES', 'CONDORI', 'CHOQUE',
                'RAMOS', 'GUTIÉRREZ', 'ROJAS', 'VARGAS', 'MEDINA', 'ZEGARRA',
                'PAREDES', 'SALAS', 'VALDIVIA', 'CHÁVEZ'])[1 + i % 16]
         || ' ' ||
         (ARRAY['APAZA', 'TICONA', 'LAZO', 'HERRERA', 'CÁCERES', 'NÚÑEZ', 'PINTO',
                'DELGADO', 'TORRES', 'CUTIPA', 'LLERENA', 'BEJARANO'])[1 + (i * 5) % 12]
         || ' ' ||
         (ARRAY['MARÍA ELENA', 'JUAN CARLOS', 'ROSA LUZ', 'LUIS ALBERTO', 'ANA SOFÍA',
                'JOSÉ MIGUEL', 'CARMEN ROSA', 'JORGE LUIS', 'GLADYS', 'PEDRO PABLO',
                'LUCÍA', 'MARCO ANTONIO', 'ELIZABETH', 'VÍCTOR HUGO'])[1 + (i * 3) % 14];
$$;

-- Registra el comprobante número k del día con el siguiente correlativo
-- de la serie, mediante sp_insertar_comprobante.
CREATE OR REPLACE PROCEDURE pg_temp.registrar(p_serie text, p_fecha date, p_k integer)
LANGUAGE plpgsql AS $$
DECLARE
  v_semilla text := p_serie || p_fecha || '-' || p_k;
  v_numero integer := fn_siguiente_numero(p_serie);
  v_ambito text := CASE WHEN p_serie = 'EB01' THEN 'EB' ELSE 'BF' END;
  v_n integer;
  v_lineas integer;
  v_pedido jsonb;
  v_detalle jsonb;
  v_gravada numeric;
  v_igv numeric;
  v_total numeric;
  v_refs jsonb := '[]';
  v_tipo text;
  v_doc text;
  v_nombre text;
  v_direccion text;
  v_i integer;
  v_corr integer;
BEGIN
  SELECT count(*) INTO v_n FROM precio WHERE ambito = v_ambito;
  v_lineas := CASE WHEN pg_temp.azar(v_semilla || 'lin') < 0.70 THEN 1
                   WHEN pg_temp.azar(v_semilla || 'lin') < 0.90 THEN 2 ELSE 3 END;

  -- Productos distintos; el primero, sesgado hacia los más baratos
  SELECT jsonb_agg(jsonb_build_object(
           'codigo', p.codigo,
           'cantidad', CASE WHEN p.unidad_medida = 'SERV' OR p.precio >= 1000 THEN 1
                            ELSE 1 + floor(4 * pg_temp.azar(v_semilla || p.codigo))::int END,
           'precio_unitario', p.precio) ORDER BY p.codigo)
    INTO v_pedido
    FROM generate_series(0, v_lineas - 1) AS m
    JOIN precio p
      ON p.ambito = v_ambito
     AND p.orden = (floor(v_n * pg_temp.azar(v_semilla || 'prod') ^ 2)::int + 7 * m) % v_n;

  -- Importes con las reglas del formato de la serie (fn_calcular_importes)
  SELECT jsonb_agg(jsonb_build_object(
           'codigo', f.codigo, 'cantidad', f.cantidad,
           'valor_unitario', f.valor_unitario, 'precio_unitario', f.precio_unitario,
           'descuento', 0, 'importe_venta', f.importe_venta)),
         max(f.op_gravada), max(f.igv), max(f.importe_total)
    INTO v_detalle, v_gravada, v_igv, v_total
    FROM fn_calcular_importes(p_serie, v_pedido) f;

  -- Cliente: F001 siempre con RUC (uno de cada diez, nuevo); B001 con
  -- DNI en el 80 % de los casos; EB01 a CLIENTES VARIOS
  IF p_serie = 'F001' THEN
    v_i := CASE WHEN pg_temp.azar(v_semilla || 'nuevo') < 0.10
                  THEN 901 + floor(25 * pg_temp.azar(v_semilla || 'cli'))::int
                ELSE 1 + floor(900 * pg_temp.azar(v_semilla || 'cli') ^ 2)::int END;
    SELECT 'RUC', e.ruc, e.nombre, e.direccion INTO v_tipo, v_doc, v_nombre, v_direccion
      FROM pg_temp.empresa(v_i) e;
  ELSIF p_serie = 'B001' AND pg_temp.azar(v_semilla || 'cli') < 0.80 THEN
    v_i := 1 + floor(70 * pg_temp.azar(v_semilla || 'per'))::int;
    SELECT 'DNI', x.dni, x.nombre, NULL INTO v_tipo, v_doc, v_nombre, v_direccion
      FROM pg_temp.persona(v_i) x;
  END IF;
  -- Para un cliente registrado se omiten nombre y dirección, que se
  -- conservan; para uno nuevo se envían y el procedimiento lo crea
  IF EXISTS (SELECT 1 FROM cliente WHERE tipo_documento = v_tipo AND numero_documento = v_doc) THEN
    v_nombre := NULL;
    v_direccion := NULL;
  END IF;

  -- Guía de remisión para las facturas con algún bien (80 %), con el
  -- correlativo siguiente a T001-003115; cotización del mes para los
  -- comprobantes con algún servicio (60 %)
  IF p_serie = 'F001'
     AND EXISTS (SELECT 1 FROM jsonb_to_recordset(v_pedido) AS x(codigo text)
                   JOIN precio p ON p.codigo = x.codigo
                  WHERE p.unidad_medida <> 'SERV')
     AND pg_temp.azar(v_semilla || 'guia') < 0.80 THEN
    SELECT max(substr(numero_relacionado, 6)::int) + 1 INTO v_corr
      FROM documento_relacionado
     WHERE tipo_relacionado = 'Guía de remisión' AND numero_relacionado LIKE 'T001-%';
    v_refs := v_refs || jsonb_build_array(jsonb_build_object(
      'tipo_relacionado', 'Guía de remisión',
      'numero_relacionado', 'T001-' || lpad(v_corr::text, 6, '0')));
  END IF;
  IF EXISTS (SELECT 1 FROM jsonb_to_recordset(v_pedido) AS x(codigo text)
               JOIN precio p ON p.codigo = x.codigo
              WHERE p.unidad_medida = 'SERV')
     AND pg_temp.azar(v_semilla || 'cot') < 0.60 THEN
    SELECT count(*) + 1 INTO v_corr
      FROM documento_relacionado
     WHERE tipo_relacionado = 'Cotización'
       AND numero_relacionado LIKE '%-' || to_char(p_fecha, 'MM-YYYY');
    v_refs := v_refs || jsonb_build_array(jsonb_build_object(
      'tipo_relacionado', 'Cotización',
      'numero_relacionado', lpad(v_corr::text, 5, '0') || '-' || to_char(p_fecha, 'MM-YYYY')));
  END IF;

  -- Formato SUNAT (EB01): ISC, otros cargos, otros tributos y redondeo en
  -- cero, sin operación gratuita ni percepción. Formato del proveedor
  -- (B001, F001): operación gratuita y percepción en cero; F001 al contado.
  CALL sp_insertar_comprobante(
    p_serie, v_numero, p_fecha, NULL, 'PEN',
    CASE WHEN p_serie = 'F001' THEN 'CONTADO' END, NULL, NULL,
    v_gravada, 0, 0,
    CASE WHEN p_serie = 'EB01' THEN NULL ELSE 0 END,
    CASE WHEN p_serie = 'EB01' THEN 0 END,
    v_igv,
    CASE WHEN p_serie = 'EB01' THEN 0 END,
    CASE WHEN p_serie = 'EB01' THEN 0 END,
    CASE WHEN p_serie = 'EB01' THEN 0 END,
    v_total,
    CASE WHEN p_serie = 'EB01' THEN NULL ELSE 0 END,
    v_tipo, v_doc, v_nombre, v_direccion, v_detalle, v_refs);
END;
$$;

-- =====================================================================
-- PARTE A: comprobantes del 1 de setiembre al 20 de noviembre de 2026
-- =====================================================================
-- Por día hábil (de lunes a sábado): de 2 a 5 facturas F001, una boleta
-- B001 en el 60 % de los días y una boleta EB01 en el 4 %, un ritmo
-- cercano al de los datos generados de 02_insercion.sql.
DO $$
DECLARE
  d date;
  k integer;
BEGIN
  FOR d IN SELECT g::date
             FROM generate_series(DATE '2026-09-01', DATE '2026-11-20', INTERVAL '1 day') AS g
            WHERE extract(isodow FROM g) <> 7
            ORDER BY 1
  LOOP
    FOR k IN 1 .. 2 + floor(4 * pg_temp.azar('F001' || d || 'n'))::int LOOP
      CALL pg_temp.registrar('F001', d, k);
    END LOOP;
    IF pg_temp.azar('B001' || d || 'n') < 0.60 THEN
      CALL pg_temp.registrar('B001', d, 1);
    END IF;
    IF pg_temp.azar('EB01' || d || 'dia') < 0.04 THEN
      CALL pg_temp.registrar('EB01', d, 1);
    END IF;
  END LOOP;
END;
$$;

-- =====================================================================
-- PARTE B: borrados lógicos y restauraciones
-- =====================================================================
DO $$
DECLARE
  f integer := 5207;   -- primer número de F001 registrado en la parte A
  b integer := 73;     -- primer número de B001 registrado en la parte A
  r record;
  n integer := 0;
BEGIN
  -- Comprobantes registrados por error: quedan eliminados
  CALL sp_eliminar_comprobante('F001', f + 11);
  CALL sp_eliminar_comprobante('F001', f + 96);
  CALL sp_eliminar_comprobante('F001', f + 182);
  CALL sp_eliminar_comprobante('B001', b + 7);

  -- Comprobantes eliminados por equivocación y restaurados
  CALL sp_eliminar_comprobante('F001', f + 40);
  CALL sp_restaurar_comprobante('F001', f + 40);
  CALL sp_eliminar_comprobante('B001', b + 15);
  CALL sp_restaurar_comprobante('B001', b + 15);

  -- Clientes sin compras desde antes de 2023: los tres primeros por
  -- número de documento se dan de baja; el tercero vuelve a registrarse
  -- (sp_insertar_cliente lo reactiva)
  FOR r IN SELECT cl.tipo_documento, cl.numero_documento, cl.nombre_razon_social,
                  cl.direccion
             FROM cliente cl
            WHERE cl.tipo_documento = 'RUC'
              AND NOT EXISTS (SELECT 1 FROM comprobante c
                               WHERE c.tipo_documento = cl.tipo_documento
                                 AND c.numero_documento = cl.numero_documento
                                 AND c.fecha_emision >= DATE '2023-01-01')
            ORDER BY cl.numero_documento
            LIMIT 3
  LOOP
    n := n + 1;
    CALL sp_eliminar_cliente(r.tipo_documento, r.numero_documento);
    IF n = 3 THEN
      CALL sp_insertar_cliente(r.tipo_documento, r.numero_documento,
                               r.nombre_razon_social, r.direccion);
    END IF;
  END LOOP;

  -- Productos descontinuados; el 372 vuelve a ofrecerse y se registra
  -- de nuevo (sp_insertar_producto lo reactiva)
  CALL sp_eliminar_producto('MS-KN95');
  CALL sp_eliminar_producto('372');
  CALL sp_insertar_producto('372', 'GUANTES DE NITRILO TALLA M CAJA X 100 UNIDADES', 'UND');
END;
$$;

-- Estadísticas para el optimizador y limpieza de las versiones de filas
VACUUM ANALYZE;

-- Resumen de la carga
SELECT 'cliente' AS tabla, count(*) AS filas,
       count(*) FILTER (WHERE eliminado) AS eliminadas FROM cliente
UNION ALL SELECT 'producto_servicio', count(*), count(*) FILTER (WHERE eliminado)
  FROM producto_servicio
UNION ALL SELECT 'comprobante', count(*), count(*) FILTER (WHERE eliminado) FROM comprobante
UNION ALL SELECT 'incluye', count(*), NULL FROM incluye
UNION ALL SELECT 'documento_relacionado', count(*), NULL FROM documento_relacionado
UNION ALL SELECT 'referencia', count(*), NULL FROM referencia;
