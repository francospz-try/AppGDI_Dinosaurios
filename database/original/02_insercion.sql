-- =====================================================================
-- Script 2 de 3: inserción de datos en registro_comprobantes
-- Gestor: PostgreSQL 17. Ejecutar después de 01_creacion.sql:
--   psql -U postgres -d registro_comprobantes -f 02_insercion.sql
--
-- Parte A: series de comprobante y moneda.
-- Parte B: datos de prueba generados con SQL, que completan las series
-- con los números anteriores a los correlativos observados (F001 del 1
-- al 5205, B001 del 1 al 71 y EB01 del 1 al 49), con el formato de cada
-- serie. Clientes, productos, cantidades y fechas son ficticios; el
-- generador es determinista, de modo que cada ejecución produce los
-- mismos datos. Se insertan en orden cronológico, como se registrarían.
-- Parte C: los tres comprobantes analizados (EB01-50, B001-000072 y
-- F001-005206), con sus importes, productos y referencias tal como
-- fueron impresos. Los datos de sus clientes se sustituyeron por datos
-- ficticios, para no publicar datos personales.
-- =====================================================================

\set ON_ERROR_STOP on

-- =====================================================================
-- PARTE A: series de comprobante y moneda
-- =====================================================================
BEGIN;

INSERT INTO serie_comprobante (serie, tipo_comprobante) VALUES
  ('EB01', 'Boleta de venta electrónica'),
  ('B001', 'Boleta de venta electrónica'),
  ('F001', 'Factura electrónica');

INSERT INTO moneda (codigo_moneda, nombre_moneda) VALUES
  ('PEN', 'SOLES');

COMMIT;

-- =====================================================================
-- PARTE B: datos de prueba generados (historial anterior)
-- =====================================================================

-- Número pseudoaleatorio en [0, 1) que depende solo de la semilla
CREATE FUNCTION pg_temp.azar(semilla text) RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$
  SELECT ('x' || substr(md5(semilla), 1, 8))::bit(32)::bigint / 4294967296.0
$$;

-- Catálogo de productos y servicios ficticios, con su precio de venta
-- con IGV en 2026; en los años anteriores el precio es 4 % menor por año,
-- redondeado a soles enteros. Ámbito EB: artículos vendidos con la serie EB01 (unidad
-- UNIDAD); ámbito BF: productos y servicios de las series B001 y F001.
CREATE TEMP TABLE catalogo (
  codigo        varchar(30) PRIMARY KEY,
  descripcion   varchar(500),
  unidad_medida varchar(10),
  precio        numeric(12,2),
  ambito        char(2),
  orden         int
);

INSERT INTO catalogo (codigo, descripcion, unidad_medida, precio, ambito) VALUES
  ('BY-128M9', 'CASCO PARA NIÑO IRON MAN MARCA ROYAL HELMETS MODELO BY-128M9', 'UNIDAD', 120.00, 'EB'),
  ('MS-KN95', 'MASCARILLA KN95 CAJA X 20 UNIDADES', 'UNIDAD', 30.00, 'EB'),
  ('AG-500', 'ALCOHOL EN GEL DE 500 ML', 'UNIDAD', 15.00, 'EB'),
  ('TD-101', 'TERMÓMETRO DIGITAL CLÍNICO', 'UNIDAD', 25.00, 'EB'),
  ('BT-01', 'BOTIQUÍN DE PRIMEROS AUXILIOS EQUIPADO', 'UNIDAD', 85.00, 'EB'),
  ('263', 'SERVICIO DE CALIBRACIÓN DE TERMOHIGRÓMETRO DIGITAL CON CERTIFICADO', 'SERV', 80.00, 'BF'),
  ('264', 'SERVICIO DE MANTENIMIENTO PREVENTIVO DE AUTOCLAVE DE MESA', 'SERV', 450.00, 'BF'),
  ('265', 'SERVICIO DE CALIBRACIÓN DE BALANZA ANALÍTICA CON CERTIFICADO', 'SERV', 180.00, 'BF'),
  ('266', 'SERVICIO DE MANTENIMIENTO PREVENTIVO DE CENTRÍFUGA DE MESA', 'SERV', 220.00, 'BF'),
  ('267', 'SERVICIO DE CALIBRACIÓN DE MICROPIPETA DE VOLUMEN VARIABLE', 'SERV', 70.00, 'BF'),
  ('268', 'SERVICIO DE MANTENIMIENTO CORRECTIVO DE MICROSCOPIO BINOCULAR', 'SERV', 260.00, 'BF'),
  ('269', 'SERVICIO DE CALIBRACIÓN DE TERMÓMETRO DIGITAL CON SONDA', 'SERV', 60.00, 'BF'),
  ('270', 'SERVICIO DE MANTENIMIENTO PREVENTIVO DE INCUBADORA', 'SERV', 300.00, 'BF'),
  ('271', 'SERVICIO DE CALIBRACIÓN DE ESTUFA DE SECADO', 'SERV', 250.00, 'BF'),
  ('272', 'SERVICIO DE MAPEO TÉRMICO DE ALMACÉN DE PRODUCTOS FARMACÉUTICOS', 'SERV', 1800.00, 'BF'),
  ('273', 'SERVICIO DE CALIBRACIÓN DE REFRIGERADORA PARA VACUNAS', 'SERV', 280.00, 'BF'),
  ('274', 'SERVICIO DE MANTENIMIENTO PREVENTIVO DE BAÑO MARÍA', 'SERV', 180.00, 'BF'),
  ('349', 'TERMOHIGROMETRO DIGITAL CALIBRADO MARCA: BOECO MODELO: SH-110', 'UND', 150.00, 'BF'),
  ('350', 'TERMÓMETRO DIGITAL DE MÁXIMA Y MÍNIMA CON SONDA', 'UND', 85.00, 'BF'),
  ('351', 'TERMOHIGRÓMETRO DIGITAL CON REGISTRADOR DE DATOS USB', 'UND', 420.00, 'BF'),
  ('352', 'BALANZA ANALÍTICA DE 220 G CON RESOLUCIÓN DE 0.0001 G', 'UND', 6500.00, 'BF'),
  ('353', 'BALANZA DE PRECISIÓN DE 3000 G CON RESOLUCIÓN DE 0.01 G', 'UND', 1450.00, 'BF'),
  ('354', 'CENTRÍFUGA DE MESA PARA 12 TUBOS', 'UND', 2300.00, 'BF'),
  ('355', 'MICROPIPETA MONOCANAL DE VOLUMEN VARIABLE 100-1000 UL', 'UND', 520.00, 'BF'),
  ('356', 'MICROSCOPIO BINOCULAR CON OBJETIVOS ACROMÁTICOS', 'UND', 3200.00, 'BF'),
  ('357', 'AGITADOR MAGNÉTICO CON PLACA CALEFACTORA', 'UND', 1350.00, 'BF'),
  ('358', 'BAÑO MARÍA DIGITAL DE 6 LITROS', 'UND', 1900.00, 'BF'),
  ('359', 'ESTUFA DE SECADO DE 50 LITROS', 'UND', 4800.00, 'BF'),
  ('360', 'INCUBADORA DE 30 LITROS', 'UND', 5200.00, 'BF'),
  ('361', 'AUTOCLAVE DE MESA DE 18 LITROS', 'UND', 9800.00, 'BF'),
  ('362', 'PHMETRO DE MESA CON ELECTRODO', 'UND', 1600.00, 'BF'),
  ('363', 'MEZCLADOR VÓRTEX PARA TUBOS', 'UND', 780.00, 'BF'),
  ('364', 'REFRIGERADORA PARA VACUNAS DE 100 LITROS', 'UND', 7600.00, 'BF'),
  ('365', 'TERMÓMETRO INFRARROJO CLÍNICO', 'UND', 120.00, 'BF'),
  ('366', 'OXÍMETRO DE PULSO DE DEDO', 'UND', 95.00, 'BF'),
  ('367', 'TENSIÓMETRO DIGITAL DE BRAZO', 'UND', 180.00, 'BF'),
  ('368', 'GLUCÓMETRO CON 50 TIRAS REACTIVAS', 'UND', 140.00, 'BF'),
  ('369', 'PIPETAS PASTEUR DE PLÁSTICO X 500 UNIDADES', 'UND', 45.00, 'BF'),
  ('370', 'PUNTAS PARA MICROPIPETA DE 1000 UL X 1000 UNIDADES', 'UND', 65.00, 'BF'),
  ('371', 'TUBOS DE ENSAYO DE VIDRIO 13 X 100 MM X 250 UNIDADES', 'UND', 210.00, 'BF'),
  ('372', 'GUANTES DE NITRILO TALLA M CAJA X 100 UNIDADES', 'UND', 32.00, 'BF');

UPDATE catalogo c
   SET orden = o.rn - 1
  FROM (SELECT codigo, row_number() OVER (PARTITION BY ambito ORDER BY precio, codigo) AS rn
          FROM catalogo) o
 WHERE o.codigo = c.codigo;

-- Clientes ficticios: empresas (RUC 209...) y personas naturales (DNI 9...)
CREATE TEMP TABLE empresa AS
SELECT i,
       '209' || lpad(i::text, 8, '0') AS ruc,
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
       (ARRAY['S.A.C.', 'E.I.R.L.', 'S.R.L.'])[1 + (i / 8) % 3] AS nombre,
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
              'JULIACA - SAN ROMÁN - PUNO', 'PUNO - PUNO - PUNO'])[1 + (i * 3) % 12] AS direccion
  FROM generate_series(1, 900) AS i;

CREATE TEMP TABLE persona AS
SELECT i,
       '9' || lpad(i::text, 7, '0') AS dni,
       (ARRAY['QUISPE', 'MAMANI', 'HUAMANI', 'FLORES', 'CONDORI', 'CHOQUE',
              'RAMOS', 'GUTIÉRREZ', 'ROJAS', 'VARGAS', 'MEDINA', 'ZEGARRA',
              'PAREDES', 'SALAS', 'VALDIVIA', 'CHÁVEZ'])[1 + i % 16]
       || ' ' ||
       (ARRAY['APAZA', 'TICONA', 'LAZO', 'HERRERA', 'CÁCERES', 'NÚÑEZ', 'PINTO',
              'DELGADO', 'TORRES', 'CUTIPA', 'LLERENA', 'BEJARANO'])[1 + (i * 5) % 12]
       || ' ' ||
       (ARRAY['MARÍA ELENA', 'JUAN CARLOS', 'ROSA LUZ', 'LUIS ALBERTO', 'ANA SOFÍA',
              'JOSÉ MIGUEL', 'CARMEN ROSA', 'JORGE LUIS', 'GLADYS', 'PEDRO PABLO',
              'LUCÍA', 'MARCO ANTONIO', 'ELIZABETH', 'VÍCTOR HUGO'])[1 + (i * 3) % 14] AS nombre
  FROM generate_series(1, 60) AS i;

-- Cabeceras: una por cada número de serie anterior al correlativo
-- observado. Las fechas avanzan con el número; si caen en domingo, se
-- emiten el lunes.
CREATE TEMP TABLE cab AS
WITH serie_param (serie, ultimo, inicio, fin) AS (
  VALUES ('F001', 5206, DATE '2022-08-29', DATE '2026-08-27'),
         ('B001', 72,   DATE '2026-01-05', DATE '2026-04-29'),
         ('EB01', 50,   DATE '2022-08-29', DATE '2026-08-03')
), base AS (
  SELECT p.serie, n AS numero,
         p.inicio + ((n - 1) * (p.fin - p.inicio)) / (p.ultimo - 1) AS fecha
    FROM serie_param p, generate_series(1, p.ultimo - 1) AS n
)
SELECT serie, numero,
       fecha + CASE WHEN extract(isodow FROM fecha) = 7 THEN 1 ELSE 0 END AS fecha_emision,
       CASE
         WHEN serie = 'F001'
           THEN 1 + floor(900 * pg_temp.azar(serie || numero || 'cli') ^ 2)::int
         WHEN serie = 'B001' AND pg_temp.azar(serie || numero || 'cli') < 0.85
           THEN 1 + floor(60 * pg_temp.azar(serie || numero || 'per'))::int
       END AS id_cliente,
       CASE
         WHEN pg_temp.azar(serie || numero || 'lin') < 0.70 THEN 1
         WHEN pg_temp.azar(serie || numero || 'lin') < 0.90 THEN 2
         ELSE 3
       END AS lineas
  FROM base;

-- Líneas de detalle: productos distintos dentro de un mismo comprobante.
-- El catálogo está ordenado por precio y el primer producto se elige con
-- una distribución sesgada hacia los más baratos.
CREATE TEMP TABLE lin AS
WITH tamanio AS (
  SELECT ambito, count(*) AS n FROM catalogo GROUP BY ambito
)
SELECT c.serie, c.numero, k.codigo, k.unidad_medida,
       CASE WHEN extract(year FROM c.fecha_emision) = 2026 THEN k.precio
            ELSE round(k.precio * power(0.96, 2026 - extract(year FROM c.fecha_emision)::int), 0)
       END AS precio,
       CASE
         WHEN k.unidad_medida = 'SERV' THEN 1
         WHEN k.precio >= 1000 THEN 1
         ELSE 1 + floor(4 * pg_temp.azar(c.serie || c.numero || k.codigo))::int
       END AS cantidad
  FROM cab c
  JOIN tamanio t ON t.ambito = CASE WHEN c.serie = 'EB01' THEN 'EB' ELSE 'BF' END
  CROSS JOIN LATERAL generate_series(0, c.lineas - 1) AS m
  JOIN catalogo k
    ON k.ambito = t.ambito
   AND k.orden = (floor(t.n * pg_temp.azar(c.serie || c.numero || 'prod') ^ 2)::int + 7 * m) % t.n;

BEGIN;

-- Clientes, en el orden de su primer comprobante
INSERT INTO cliente (tipo_documento, numero_documento, nombre_razon_social, direccion)
SELECT x.tipo_documento, x.numero_documento, x.nombre, x.direccion
  FROM (SELECT 'RUC' AS tipo_documento, e.ruc AS numero_documento, e.nombre,
               e.direccion, min(c.fecha_emision) AS primera
          FROM empresa e JOIN cab c ON c.serie = 'F001' AND c.id_cliente = e.i
         GROUP BY e.ruc, e.nombre, e.direccion
        UNION ALL
        SELECT 'DNI', p.dni, p.nombre, NULL, min(c.fecha_emision)
          FROM persona p JOIN cab c ON c.serie = 'B001' AND c.id_cliente = p.i
         GROUP BY p.dni, p.nombre) x
 ORDER BY x.primera, x.numero_documento;

INSERT INTO producto_servicio (codigo, descripcion, unidad_medida)
SELECT k.codigo, k.descripcion, k.unidad_medida
  FROM catalogo k
 WHERE k.codigo IN (SELECT codigo FROM lin)
 ORDER BY k.codigo;

-- Comprobantes. Formato SUNAT (EB01): valor unitario con 6 decimales,
-- sin precio unitario, con ISC, otros cargos, otros tributos y redondeo.
-- Formato del proveedor (B001 y F001): valor unitario con 2 decimales,
-- precio unitario, operación gratuita y percepción; F001 al contado.
INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
  codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
  op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
  otros_tributos, monto_redondeo, importe_total, percepcion,
  tipo_documento, numero_documento)
SELECT c.serie, c.numero, c.fecha_emision, NULL, 'PEN',
       CASE WHEN c.serie = 'F001' THEN 'CONTADO' END, NULL, NULL,
       t.op_gravada, 0, 0,
       CASE WHEN c.serie = 'EB01' THEN NULL ELSE 0 END,
       CASE WHEN c.serie = 'EB01' THEN 0 END,
       t.total - t.op_gravada,
       CASE WHEN c.serie = 'EB01' THEN 0 END,
       CASE WHEN c.serie = 'EB01' THEN 0 END,
       CASE WHEN c.serie = 'EB01' THEN 0 END,
       t.total,
       CASE WHEN c.serie = 'EB01' THEN NULL ELSE 0 END,
       CASE WHEN c.serie = 'F001' THEN 'RUC'
            WHEN c.id_cliente IS NOT NULL THEN 'DNI' END,
       CASE WHEN c.serie = 'F001' THEN e.ruc ELSE p.dni END
  FROM cab c
  JOIN LATERAL (
    SELECT CASE WHEN c.serie = 'EB01'
                  THEN round(sum(l.cantidad * round(l.precio / 1.18, 6)), 2)
                ELSE round(sum(l.cantidad * l.precio) / 1.18, 2) END AS op_gravada,
           CASE WHEN c.serie = 'EB01'
                  THEN round(sum(round(l.cantidad * round(l.precio / 1.18, 6) * 1.18, 7)), 2)
                ELSE sum(l.cantidad * l.precio) END AS total
      FROM lin l
     WHERE l.serie = c.serie AND l.numero = c.numero
  ) t ON true
  LEFT JOIN empresa e ON c.serie = 'F001' AND e.i = c.id_cliente
  LEFT JOIN persona p ON c.serie = 'B001' AND p.i = c.id_cliente
 ORDER BY c.fecha_emision, c.serie, c.numero;

INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
  precio_unitario, descuento, importe_venta)
SELECT l.serie, l.numero, l.codigo,
       CASE WHEN l.serie = 'EB01' THEN round(l.cantidad, 2) ELSE l.cantidad END,
       CASE WHEN l.serie = 'EB01' THEN round(l.precio / 1.18, 6)
            ELSE round(l.precio / 1.18, 2) END,
       CASE WHEN l.serie = 'EB01' THEN NULL ELSE l.precio END,
       0,
       CASE WHEN l.serie = 'EB01'
              THEN round(l.cantidad * round(l.precio / 1.18, 6) * 1.18, 7)
            ELSE l.cantidad * l.precio END
  FROM lin l
  JOIN cab c ON c.serie = l.serie AND c.numero = l.numero
 ORDER BY c.fecha_emision, l.serie, l.numero, l.codigo;

-- Guías de remisión: facturas con al menos un bien (no servicio), en el
-- 80 % de los casos. Se numeran hacia atrás desde T001-003114, de modo
-- que la última guía generada precede a T001-003115, que corresponde a
-- F001-005206; las facturas más antiguas que quedarían con un número
-- menor que 1 no reciben guía.
CREATE TEMP TABLE guia AS
SELECT serie, numero,
       'T001-' || lpad((3115 - rn)::text, 6, '0') AS numero_relacionado
  FROM (SELECT c.serie, c.numero, row_number() OVER (ORDER BY c.numero DESC) AS rn
          FROM cab c
         WHERE c.serie = 'F001'
           AND EXISTS (SELECT 1 FROM lin l
                        WHERE l.serie = c.serie AND l.numero = c.numero
                          AND l.unidad_medida <> 'SERV')
           AND pg_temp.azar(c.serie || c.numero || 'guia') < 0.80) g
 WHERE rn < 3115;

-- Cotizaciones: comprobantes con algún servicio, en el 60 % de los casos.
-- Formato NNNNN-MM-AAAA, correlativo por mes; en abril de 2026 se salta
-- el 00042, que corresponde a B001-000072.
CREATE TEMP TABLE cotizacion AS
SELECT serie, numero,
       lpad((rn + CASE WHEN mes = '04-2026' AND rn >= 42 THEN 1 ELSE 0 END)::text, 5, '0')
       || '-' || mes AS numero_relacionado
  FROM (SELECT c.serie, c.numero, to_char(c.fecha_emision, 'MM-YYYY') AS mes,
               row_number() OVER (PARTITION BY to_char(c.fecha_emision, 'MM-YYYY')
                                  ORDER BY c.fecha_emision, c.serie, c.numero) AS rn
          FROM cab c
         WHERE EXISTS (SELECT 1 FROM lin l
                        WHERE l.serie = c.serie AND l.numero = c.numero
                          AND l.unidad_medida = 'SERV')
           AND pg_temp.azar(c.serie || c.numero || 'cot') < 0.60) q;

CREATE TEMP TABLE ref AS
SELECT c.fecha_emision, g.serie, g.numero,
       'Guía de remisión'::varchar(20) AS tipo_relacionado, g.numero_relacionado
  FROM guia g JOIN cab c ON c.serie = g.serie AND c.numero = g.numero
UNION ALL
SELECT c.fecha_emision, q.serie, q.numero, 'Cotización', q.numero_relacionado
  FROM cotizacion q JOIN cab c ON c.serie = q.serie AND c.numero = q.numero;

INSERT INTO documento_relacionado (tipo_relacionado, numero_relacionado)
SELECT tipo_relacionado, numero_relacionado
  FROM ref
 ORDER BY fecha_emision, serie, numero, tipo_relacionado;

INSERT INTO referencia (serie, numero, tipo_relacionado, numero_relacionado)
SELECT serie, numero, tipo_relacionado, numero_relacionado
  FROM ref
 ORDER BY fecha_emision, serie, numero, tipo_relacionado;

COMMIT;

-- =====================================================================
-- PARTE C: comprobantes analizados
-- =====================================================================
BEGIN;

-- Clientes ficticios en lugar de los adquirentes reales
INSERT INTO cliente (tipo_documento, numero_documento, nombre_razon_social, direccion) VALUES
  ('DNI', '90000000', 'HUAMANI CONDORI ROSA ELENA', NULL),
  ('RUC', '20900000000', 'CLÍNICA SAN MARTÍN DEL SUR E.I.R.L.',
   'AV. EL SOL 450 - WANCHAQ - CUSCO - CUSCO');

-- BY-128M9 y 349 ya pueden existir por los datos generados
INSERT INTO producto_servicio (codigo, descripcion, unidad_medida) VALUES
  ('BY-128M9', 'CASCO PARA NIÑO IRON MAN MARCA ROYAL HELMETS MODELO BY-128M9', 'UNIDAD'),
  ('262', 'SERVICIO DE MANTENIMIENTO PREVENTIVO Y/O CALIBRACIÓN DE 01 TERMOHIGROMETRO SEGUN COTIZACIÓN N° 00042-04-2026', 'SERV'),
  ('349', 'TERMOHIGROMETRO DIGITAL CALIBRADO MARCA: BOECO MODELO: SH-110', 'UND')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO documento_relacionado (tipo_relacionado, numero_relacionado) VALUES
  ('Cotización', '00042-04-2026'),
  ('Guía de remisión', 'T001-003115');

-- EB01-50: formato del sistema de la SUNAT, emitido a CLIENTES VARIOS
INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
  codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
  op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
  otros_tributos, monto_redondeo, importe_total, percepcion,
  tipo_documento, numero_documento)
VALUES ('EB01', 50, DATE '2026-08-03', NULL, 'PEN', NULL, NULL, NULL,
  101.69, 0.00, 0.00, NULL, 0.00, 18.31, 0.00, 0.00, 0.00, 120.00, NULL,
  NULL, NULL);

INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
  precio_unitario, descuento, importe_venta)
VALUES ('EB01', 50, 'BY-128M9', 1.00, 101.694915, NULL, 0.00, 119.9999997);

-- B001-000072: formato del proveedor de facturación electrónica
INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
  codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
  op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
  otros_tributos, monto_redondeo, importe_total, percepcion,
  tipo_documento, numero_documento)
VALUES ('B001', 72, DATE '2026-04-29', NULL, 'PEN', NULL, NULL, NULL,
  76.27, 0.00, 0.00, 0.00, NULL, 13.73, NULL, NULL, NULL, 90.00, 0.00,
  'DNI', '90000000');

INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
  precio_unitario, descuento, importe_venta)
VALUES ('B001', 72, '262', 1, 76.27, 90.00, 0.00, 90.00);

INSERT INTO referencia (serie, numero, tipo_relacionado, numero_relacionado)
VALUES ('B001', 72, 'Cotización', '00042-04-2026');

-- F001-005206: formato del proveedor de facturación electrónica
INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
  codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
  op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
  otros_tributos, monto_redondeo, importe_total, percepcion,
  tipo_documento, numero_documento)
VALUES ('F001', 5206, DATE '2026-08-27', NULL, 'PEN', 'CONTADO', NULL, NULL,
  254.24, 0.00, 0.00, 0.00, NULL, 45.76, NULL, NULL, NULL, 300.00, 0.00,
  'RUC', '20900000000');

INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
  precio_unitario, descuento, importe_venta)
VALUES ('F001', 5206, '349', 2, 127.12, 150.00, 0.00, 300.00);

INSERT INTO referencia (serie, numero, tipo_relacionado, numero_relacionado)
VALUES ('F001', 5206, 'Guía de remisión', 'T001-003115');

COMMIT;

-- Estadísticas para el optimizador y limpieza de las versiones de filas
VACUUM ANALYZE;

-- Resumen de la carga
SELECT 'cliente' AS tabla, count(*) AS filas FROM cliente
UNION ALL SELECT 'serie_comprobante', count(*) FROM serie_comprobante
UNION ALL SELECT 'moneda', count(*) FROM moneda
UNION ALL SELECT 'comprobante', count(*) FROM comprobante
UNION ALL SELECT 'producto_servicio', count(*) FROM producto_servicio
UNION ALL SELECT 'documento_relacionado', count(*) FROM documento_relacionado
UNION ALL SELECT 'incluye', count(*) FROM incluye
UNION ALL SELECT 'referencia', count(*) FROM referencia;
