-- =====================================================================
-- Script completo de la base de datos registro_comprobantes
-- Proyecto: Desarrollo de la base de datos para la funcionalidad de
-- registro de comprobantes de pago de Sermed Ingenieros E.I.R.L.
-- Gestor: PostgreSQL 17
--
-- Construye la base de datos desde cero, en este orden:
--   01_creacion.sql          base de datos, dominios, tablas, índices
--   02_insercion.sql         series, moneda, datos generados y los tres
--                            comprobantes analizados
--   04_procedimientos.sql    funciones de consulta (aplicativo, 30 %)
--   05_procedimientos.sql    procedimientos del CRUD y disparadores (70 %)
--   06_reportes.sql          doce reportes y catálogos (100 %)
--   07_datos_aplicativo.sql  comprobantes de setiembre a noviembre de 2026,
--                            borrados lógicos y restauraciones, registrados
--                            con los procedimientos
--   08_permisos.sql          rol gdi_app del aplicativo: solo lee las
--                            tablas y ejecuta las rutinas
-- Generado con scripts/generar_completo.sh a partir de esos archivos;
-- para cambiar algo, modifique el archivo numerado y vuelva a generarlo.
--
-- Ejecución (con psql, conectado a la base postgres como superusuario;
-- elimina la base registro_comprobantes si ya existe):
--   psql -U postgres -d postgres -f registro_comprobantes_completo.sql
-- Después, asignar una vez la contraseña del rol del aplicativo:
--   psql -U postgres -d registro_comprobantes -c '\password gdi_app'
-- =====================================================================


-- #####################################################################
-- 01_creacion.sql
-- #####################################################################

-- =====================================================================
-- Script 1 de 3: creación de la base de datos registro_comprobantes
-- Proyecto: Desarrollo de la base de datos para la funcionalidad de
-- registro de comprobantes de pago de Sermed Ingenieros E.I.R.L.
-- Gestor: PostgreSQL 17
--
-- Ejecución (con psql, conectado a la base postgres como superusuario):
--   psql -U postgres -d postgres -f 01_creacion.sql
-- El script elimina la base si ya existe y la crea de nuevo, vacía.
-- Los nombres, tipos, longitudes y restricciones coinciden con el
-- diccionario del modelo físico del informe.
-- Borrado lógico: cliente, producto_servicio y comprobante tienen la
-- columna eliminado (FALSE al registrar). Borrar marca la fila con TRUE
-- en lugar de eliminarla, de modo que los comprobantes conservan su
-- historia y sus referencias.
-- =====================================================================

\set ON_ERROR_STOP on

DROP DATABASE IF EXISTS registro_comprobantes;
CREATE DATABASE registro_comprobantes
  ENCODING 'UTF8'
  TEMPLATE template0;

\connect registro_comprobantes

-- ---------------------------------------------------------------------
-- Dominios de los importes
-- ---------------------------------------------------------------------
CREATE DOMAIN dom_importe AS NUMERIC(12,2)
  CONSTRAINT ck_dom_importe CHECK (VALUE >= 0);

CREATE DOMAIN dom_importe_linea AS NUMERIC(18,7)
  CONSTRAINT ck_dom_importe_linea CHECK (VALUE >= 0);

-- ---------------------------------------------------------------------
-- Tablas sin claves foráneas
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
  tipo_documento      VARCHAR(3)   NOT NULL,
  numero_documento    VARCHAR(11)  NOT NULL,
  nombre_razon_social VARCHAR(200) NOT NULL,
  direccion           VARCHAR(250),
  eliminado           BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_cliente PRIMARY KEY (tipo_documento, numero_documento),
  CONSTRAINT ck_cliente_tipo_documento
    CHECK (tipo_documento IN ('DNI', 'RUC')),
  CONSTRAINT ck_cliente_numero_documento CHECK (
    (tipo_documento = 'DNI' AND numero_documento ~ '^[0-9]{8}$') OR
    (tipo_documento = 'RUC' AND numero_documento ~ '^[0-9]{11}$')),
  CONSTRAINT ck_cliente_nombre CHECK (btrim(nombre_razon_social) <> '')
);

CREATE TABLE serie_comprobante (
  serie            VARCHAR(4)  NOT NULL,
  tipo_comprobante VARCHAR(30) NOT NULL,
  CONSTRAINT pk_serie_comprobante PRIMARY KEY (serie),
  CONSTRAINT ck_serie_comprobante_serie CHECK (serie ~ '^[A-Z0-9]{4}$'),
  CONSTRAINT ck_serie_comprobante_tipo CHECK (
    tipo_comprobante IN ('Boleta de venta electrónica', 'Factura electrónica'))
);

CREATE TABLE moneda (
  codigo_moneda VARCHAR(3)  NOT NULL,
  nombre_moneda VARCHAR(30) NOT NULL,
  CONSTRAINT pk_moneda PRIMARY KEY (codigo_moneda),
  CONSTRAINT uq_moneda_nombre UNIQUE (nombre_moneda),
  CONSTRAINT ck_moneda_codigo CHECK (codigo_moneda ~ '^[A-Z]{3}$')
);

CREATE TABLE producto_servicio (
  codigo        VARCHAR(30)  NOT NULL,
  descripcion   VARCHAR(500) NOT NULL,
  unidad_medida VARCHAR(10)  NOT NULL,
  eliminado     BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_producto_servicio PRIMARY KEY (codigo),
  CONSTRAINT ck_producto_servicio_codigo CHECK (btrim(codigo) <> ''),
  CONSTRAINT ck_producto_servicio_descripcion CHECK (btrim(descripcion) <> '')
);

CREATE TABLE documento_relacionado (
  tipo_relacionado   VARCHAR(20) NOT NULL,
  numero_relacionado VARCHAR(30) NOT NULL,
  CONSTRAINT pk_documento_relacionado
    PRIMARY KEY (tipo_relacionado, numero_relacionado),
  CONSTRAINT ck_documento_relacionado_tipo CHECK (
    tipo_relacionado IN ('Guía de remisión', 'Cotización'))
);

-- ---------------------------------------------------------------------
-- Tabla comprobante
-- ---------------------------------------------------------------------
CREATE TABLE comprobante (
  serie             VARCHAR(4)    NOT NULL,
  numero            INTEGER       NOT NULL,
  fecha_emision     DATE          NOT NULL,
  fecha_vencimiento DATE,
  codigo_moneda     VARCHAR(3)    NOT NULL,
  forma_pago        VARCHAR(20),
  medio_pago        VARCHAR(30),
  observacion       VARCHAR(250),
  op_gravada        dom_importe   NOT NULL,
  op_exonerada      dom_importe   NOT NULL DEFAULT 0,
  op_inafecta       dom_importe   NOT NULL DEFAULT 0,
  op_gratuita       dom_importe,
  isc               dom_importe,
  igv               dom_importe   NOT NULL,
  otros_cargos      dom_importe,
  otros_tributos    dom_importe,
  monto_redondeo    NUMERIC(12,2),
  importe_total     dom_importe   NOT NULL,
  percepcion        dom_importe,
  tipo_documento    VARCHAR(3),
  numero_documento  VARCHAR(11),
  eliminado         BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_comprobante PRIMARY KEY (serie, numero),
  CONSTRAINT fk_comprobante_serie FOREIGN KEY (serie)
    REFERENCES serie_comprobante (serie)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_comprobante_moneda FOREIGN KEY (codigo_moneda)
    REFERENCES moneda (codigo_moneda)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_comprobante_cliente
    FOREIGN KEY (tipo_documento, numero_documento)
    REFERENCES cliente (tipo_documento, numero_documento)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_comprobante_numero CHECK (numero BETWEEN 1 AND 99999999),
  CONSTRAINT ck_comprobante_vencimiento
    CHECK (fecha_vencimiento >= fecha_emision),
  CONSTRAINT ck_comprobante_cliente
    CHECK ((tipo_documento IS NULL) = (numero_documento IS NULL))
);

-- ---------------------------------------------------------------------
-- Tablas de las relaciones M:N
-- ---------------------------------------------------------------------
CREATE TABLE incluye (
  serie           VARCHAR(4)        NOT NULL,
  numero          INTEGER           NOT NULL,
  codigo          VARCHAR(30)       NOT NULL,
  cantidad        NUMERIC(12,2)     NOT NULL,
  valor_unitario  dom_importe_linea NOT NULL,
  precio_unitario dom_importe_linea,
  descuento       dom_importe_linea NOT NULL DEFAULT 0,
  importe_venta   dom_importe_linea NOT NULL,
  CONSTRAINT pk_incluye PRIMARY KEY (serie, numero, codigo),
  CONSTRAINT fk_incluye_comprobante FOREIGN KEY (serie, numero)
    REFERENCES comprobante (serie, numero)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_incluye_producto_servicio FOREIGN KEY (codigo)
    REFERENCES producto_servicio (codigo)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_incluye_cantidad CHECK (cantidad > 0)
);

CREATE TABLE referencia (
  serie              VARCHAR(4)  NOT NULL,
  numero             INTEGER     NOT NULL,
  tipo_relacionado   VARCHAR(20) NOT NULL,
  numero_relacionado VARCHAR(30) NOT NULL,
  CONSTRAINT pk_referencia
    PRIMARY KEY (serie, numero, tipo_relacionado, numero_relacionado),
  CONSTRAINT fk_referencia_comprobante FOREIGN KEY (serie, numero)
    REFERENCES comprobante (serie, numero)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_referencia_documento_relacionado
    FOREIGN KEY (tipo_relacionado, numero_relacionado)
    REFERENCES documento_relacionado (tipo_relacionado, numero_relacionado)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- Índices secundarios (los de las claves primarias y de la restricción
-- de unicidad los crea PostgreSQL automáticamente)
-- ---------------------------------------------------------------------
CREATE INDEX idx_comprobante_cliente
  ON comprobante (tipo_documento, numero_documento)
  WHERE tipo_documento IS NOT NULL;

CREATE INDEX idx_comprobante_fecha_emision
  ON comprobante (fecha_emision);

CREATE INDEX idx_incluye_codigo
  ON incluye (codigo);

CREATE INDEX idx_referencia_documento
  ON referencia (tipo_relacionado, numero_relacionado);

-- ---------------------------------------------------------------------
-- Participación total de comprobante en incluye: todo comprobante debe
-- tener al menos una línea de detalle al confirmar la transacción
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_comprobante_con_detalle() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (SELECT 1
                   FROM incluye i
                  WHERE i.serie = NEW.serie
                    AND i.numero = NEW.numero) THEN
    RAISE EXCEPTION 'El comprobante %-% no tiene líneas de detalle',
      NEW.serie, NEW.numero;
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER tr_comprobante_con_detalle
  AFTER INSERT ON comprobante
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_comprobante_con_detalle();


-- #####################################################################
-- 02_insercion.sql
-- #####################################################################

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


-- #####################################################################
-- 04_procedimientos.sql
-- #####################################################################

-- =====================================================================
-- Script 4: rutinas que usa el aplicativo
-- Gestor: PostgreSQL 17. Ejecutar después de 01_creacion.sql (y de
-- 02_insercion.sql si se quieren datos):
--   psql -U postgres -d registro_comprobantes -f 04_procedimientos.sql
--
-- El aplicativo no consulta las tablas directamente: lee mediante
-- funciones que devuelven tablas (SELECT * FROM fn_...) y, desde el hito
-- del 70 %, modifica mediante procedimientos (CALL sp_...).
-- El script puede ejecutarse varias veces: reemplaza las rutinas.
-- Las funciones omiten las filas marcadas como eliminadas (borrado
-- lógico, columna eliminado).
--
-- Hito del 30 %: funciones de consulta para la ventana principal.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------
-- Catálogos para los formularios
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_listar_series()
RETURNS TABLE (serie varchar, tipo_comprobante varchar)
LANGUAGE sql STABLE AS $$
  SELECT s.serie, s.tipo_comprobante
    FROM serie_comprobante s
   ORDER BY s.serie;
$$;

CREATE OR REPLACE FUNCTION fn_listar_monedas()
RETURNS TABLE (codigo_moneda varchar, nombre_moneda varchar)
LANGUAGE sql STABLE AS $$
  SELECT m.codigo_moneda, m.nombre_moneda
    FROM moneda m
   ORDER BY m.codigo_moneda;
$$;

-- ---------------------------------------------------------------------
-- Búsqueda de clientes por número de documento (inicio) o por nombre
-- (cualquier parte, sin distinguir mayúsculas). Texto vacío: todos.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_buscar_clientes(p_texto varchar, p_limite integer)
RETURNS TABLE (tipo_documento varchar, numero_documento varchar,
               nombre_razon_social varchar, direccion varchar,
               comprobantes bigint)
LANGUAGE sql STABLE AS $$
  SELECT cl.tipo_documento, cl.numero_documento, cl.nombre_razon_social,
         cl.direccion,
         (SELECT count(*) FROM comprobante c
           WHERE c.tipo_documento = cl.tipo_documento
             AND c.numero_documento = cl.numero_documento
             AND NOT c.eliminado)
    FROM cliente cl
   WHERE NOT cl.eliminado
     AND (coalesce(p_texto, '') = ''
          OR cl.numero_documento LIKE p_texto || '%'
          OR cl.nombre_razon_social ILIKE '%' || p_texto || '%')
   ORDER BY cl.nombre_razon_social, cl.numero_documento
   LIMIT p_limite;
$$;

-- ---------------------------------------------------------------------
-- Búsqueda de productos o servicios por código (inicio) o descripción
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_buscar_productos(p_texto varchar, p_limite integer)
RETURNS TABLE (codigo varchar, descripcion varchar, unidad_medida varchar,
               lineas bigint)
LANGUAGE sql STABLE AS $$
  SELECT p.codigo, p.descripcion, p.unidad_medida,
         (SELECT count(*) FROM incluye i
            JOIN comprobante c ON c.serie = i.serie AND c.numero = i.numero
           WHERE i.codigo = p.codigo AND NOT c.eliminado)
    FROM producto_servicio p
   WHERE NOT p.eliminado
     AND (coalesce(p_texto, '') = ''
          OR p.codigo ILIKE p_texto || '%'
          OR p.descripcion ILIKE '%' || p_texto || '%')
   ORDER BY p.codigo
   LIMIT p_limite;
$$;

-- ---------------------------------------------------------------------
-- Comprobantes emitidos en un periodo (consulta C01)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_comprobantes_periodo(p_desde date, p_hasta date)
RETURNS TABLE (serie varchar, numero integer, tipo_comprobante varchar,
               fecha_emision date, cliente varchar, importe_total numeric)
LANGUAGE sql STABLE AS $$
  SELECT c.serie, c.numero, s.tipo_comprobante, c.fecha_emision,
         coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS')::varchar,
         c.importe_total
    FROM comprobante c
    JOIN serie_comprobante s ON s.serie = c.serie
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
   WHERE c.fecha_emision BETWEEN p_desde AND p_hasta
     AND NOT c.eliminado
   ORDER BY c.fecha_emision, c.serie, c.numero;
$$;

-- ---------------------------------------------------------------------
-- Detalle de un comprobante (consulta C02, separada en cabecera, líneas
-- y referencias para mostrarlas en el formulario)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_comprobante_cabecera(p_serie varchar, p_numero integer)
RETURNS TABLE (serie varchar, numero integer, tipo_comprobante varchar,
               fecha_emision date, fecha_vencimiento date,
               codigo_moneda varchar, nombre_moneda varchar,
               forma_pago varchar, medio_pago varchar, observacion varchar,
               op_gravada numeric, op_exonerada numeric, op_inafecta numeric,
               op_gratuita numeric, isc numeric, igv numeric,
               otros_cargos numeric, otros_tributos numeric,
               monto_redondeo numeric, importe_total numeric,
               percepcion numeric, tipo_documento varchar,
               numero_documento varchar, nombre_razon_social varchar,
               direccion varchar)
LANGUAGE sql STABLE AS $$
  SELECT c.serie, c.numero, s.tipo_comprobante, c.fecha_emision,
         c.fecha_vencimiento, c.codigo_moneda, m.nombre_moneda,
         c.forma_pago, c.medio_pago, c.observacion,
         c.op_gravada, c.op_exonerada, c.op_inafecta, c.op_gratuita,
         c.isc, c.igv, c.otros_cargos, c.otros_tributos, c.monto_redondeo,
         c.importe_total, c.percepcion, c.tipo_documento,
         c.numero_documento, cl.nombre_razon_social, cl.direccion
    FROM comprobante c
    JOIN serie_comprobante s ON s.serie = c.serie
    JOIN moneda m ON m.codigo_moneda = c.codigo_moneda
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
   WHERE c.serie = p_serie AND c.numero = p_numero
     AND NOT c.eliminado;
$$;

CREATE OR REPLACE FUNCTION fn_comprobante_lineas(p_serie varchar, p_numero integer)
RETURNS TABLE (codigo varchar, descripcion varchar, unidad_medida varchar,
               cantidad numeric, valor_unitario numeric,
               precio_unitario numeric, descuento numeric,
               importe_venta numeric)
LANGUAGE sql STABLE AS $$
  SELECT i.codigo, p.descripcion, p.unidad_medida, i.cantidad,
         i.valor_unitario, i.precio_unitario, i.descuento, i.importe_venta
    FROM incluye i
    JOIN producto_servicio p ON p.codigo = i.codigo
   WHERE i.serie = p_serie AND i.numero = p_numero
   ORDER BY i.codigo;
$$;

CREATE OR REPLACE FUNCTION fn_comprobante_referencias(p_serie varchar, p_numero integer)
RETURNS TABLE (tipo_relacionado varchar, numero_relacionado varchar)
LANGUAGE sql STABLE AS $$
  SELECT r.tipo_relacionado, r.numero_relacionado
    FROM referencia r
   WHERE r.serie = p_serie AND r.numero = p_numero
   ORDER BY r.tipo_relacionado, r.numero_relacionado;
$$;


-- #####################################################################
-- 05_procedimientos.sql
-- #####################################################################

-- =====================================================================
-- Script 5: procedimientos, funciones de cálculo y disparadores del CRUD
-- Gestor: PostgreSQL 17. Ejecutar después de 04_procedimientos.sql:
--   psql -U postgres -d registro_comprobantes -f 05_procedimientos.sql
--
-- Hito del 70 %. El aplicativo inserta, modifica y borra únicamente con
-- estos procedimientos (CALL sp_...). Cada uno valida las reglas del
-- negocio y se ejecuta en una sola transacción: si algo falla, no queda
-- nada a medias. Los mensajes de error están en español, porque el
-- aplicativo los muestra tal cual.
-- El script puede ejecutarse varias veces: reemplaza las rutinas.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------
-- Funciones auxiliares
-- ---------------------------------------------------------------------

-- Texto recortado y sin espacios repetidos; NULL si queda vacío
CREATE OR REPLACE FUNCTION fn_limpiar(p_texto varchar)
RETURNS varchar LANGUAGE sql IMMUTABLE AS $$
  SELECT nullif(regexp_replace(btrim(p_texto), '\s+', ' ', 'g'), '');
$$;

-- Numeración como la imprime cada sistema de emisión: con seis cifras en
-- las series del proveedor (F001-005206) y sin ceros en las de la SUNAT
-- (EB01-50)
CREATE OR REPLACE FUNCTION fn_numeracion(p_serie varchar, p_numero integer)
RETURNS varchar LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN p_serie LIKE 'E%' THEN p_serie || '-' || p_numero
              ELSE p_serie || '-' || lpad(p_numero::text, greatest(6, length(p_numero::text)), '0') END;
$$;

-- ---------------------------------------------------------------------
-- CLIENTES
-- ---------------------------------------------------------------------

-- Registra un cliente. Si el documento pertenece a un cliente dado de
-- baja, lo reactiva con los datos nuevos en lugar de rechazarlo.
CREATE OR REPLACE PROCEDURE sp_insertar_cliente(
  p_tipo_documento varchar, p_numero_documento varchar,
  p_nombre varchar, p_direccion varchar)
LANGUAGE plpgsql AS $$
DECLARE
  v_eliminado boolean;
BEGIN
  SELECT eliminado INTO v_eliminado
    FROM cliente
   WHERE tipo_documento = upper(btrim(p_tipo_documento))
     AND numero_documento = btrim(p_numero_documento);
  IF v_eliminado IS FALSE THEN
    RAISE EXCEPTION 'Ya existe un cliente con % %.', upper(btrim(p_tipo_documento)),
      btrim(p_numero_documento) USING ERRCODE = 'unique_violation';
  ELSIF v_eliminado IS TRUE THEN
    UPDATE cliente
       SET nombre_razon_social = upper(fn_limpiar(p_nombre)),
           direccion = upper(fn_limpiar(p_direccion)),
           eliminado = FALSE
     WHERE tipo_documento = upper(btrim(p_tipo_documento))
       AND numero_documento = btrim(p_numero_documento);
  ELSE
    INSERT INTO cliente (tipo_documento, numero_documento, nombre_razon_social, direccion)
    VALUES (upper(btrim(p_tipo_documento)), btrim(p_numero_documento),
            coalesce(upper(fn_limpiar(p_nombre)), ''), upper(fn_limpiar(p_direccion)));
  END IF;
END;
$$;

-- Modifica el nombre y la dirección de un cliente vigente
CREATE OR REPLACE PROCEDURE sp_modificar_cliente(
  p_tipo_documento varchar, p_numero_documento varchar,
  p_nombre varchar, p_direccion varchar)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE cliente
     SET nombre_razon_social = coalesce(upper(fn_limpiar(p_nombre)), ''),
         direccion = upper(fn_limpiar(p_direccion))
   WHERE tipo_documento = p_tipo_documento
     AND numero_documento = p_numero_documento
     AND NOT eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un cliente vigente con % %.', p_tipo_documento,
      p_numero_documento USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- Da de baja un cliente (borrado lógico). Sus comprobantes lo conservan.
CREATE OR REPLACE PROCEDURE sp_eliminar_cliente(
  p_tipo_documento varchar, p_numero_documento varchar)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE cliente SET eliminado = TRUE
   WHERE tipo_documento = p_tipo_documento
     AND numero_documento = p_numero_documento
     AND NOT eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un cliente vigente con % %.', p_tipo_documento,
      p_numero_documento USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------
-- PRODUCTOS Y SERVICIOS
-- ---------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_insertar_producto(
  p_codigo varchar, p_descripcion varchar, p_unidad_medida varchar)
LANGUAGE plpgsql AS $$
DECLARE
  v_eliminado boolean;
BEGIN
  SELECT eliminado INTO v_eliminado FROM producto_servicio
   WHERE codigo = upper(btrim(p_codigo));
  IF v_eliminado IS FALSE THEN
    RAISE EXCEPTION 'Ya existe un producto o servicio con el código %.',
      upper(btrim(p_codigo)) USING ERRCODE = 'unique_violation';
  ELSIF v_eliminado IS TRUE THEN
    UPDATE producto_servicio
       SET descripcion = coalesce(upper(fn_limpiar(p_descripcion)), ''),
           unidad_medida = upper(btrim(p_unidad_medida)),
           eliminado = FALSE
     WHERE codigo = upper(btrim(p_codigo));
  ELSE
    INSERT INTO producto_servicio (codigo, descripcion, unidad_medida)
    VALUES (coalesce(upper(btrim(p_codigo)), ''),
            coalesce(upper(fn_limpiar(p_descripcion)), ''),
            upper(btrim(p_unidad_medida)));
  END IF;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_modificar_producto(
  p_codigo varchar, p_descripcion varchar, p_unidad_medida varchar)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE producto_servicio
     SET descripcion = coalesce(upper(fn_limpiar(p_descripcion)), ''),
         unidad_medida = upper(btrim(p_unidad_medida))
   WHERE codigo = p_codigo AND NOT eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un producto o servicio vigente con el código %.',
      p_codigo USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- Da de baja un producto o servicio (borrado lógico). Las líneas de los
-- comprobantes que lo vendieron lo conservan.
CREATE OR REPLACE PROCEDURE sp_eliminar_producto(p_codigo varchar)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE producto_servicio SET eliminado = TRUE
   WHERE codigo = p_codigo AND NOT eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un producto o servicio vigente con el código %.',
      p_codigo USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------
-- COMPROBANTES
-- ---------------------------------------------------------------------

-- Siguiente correlativo libre de una serie (sugerencia para el formulario)
CREATE OR REPLACE FUNCTION fn_siguiente_numero(p_serie varchar)
RETURNS integer LANGUAGE sql STABLE AS $$
  SELECT coalesce(max(numero), 0) + 1 FROM comprobante WHERE serie = p_serie;
$$;

-- Calcula el resumen de importes a partir de las líneas, con las mismas
-- reglas que los comprobantes analizados: IGV del 18 %; en la serie de la
-- SUNAT (EB01) el valor unitario lleva 6 decimales y el importe de venta
-- 7; en las del proveedor, 2. Devuelve las líneas completadas y los
-- importes. Las líneas llegan como JSON:
--   [{"codigo": "349", "cantidad": 2, "precio_unitario": 150.00}, ...]
CREATE OR REPLACE FUNCTION fn_calcular_importes(p_serie varchar, p_lineas jsonb)
RETURNS TABLE (codigo varchar, cantidad numeric, valor_unitario numeric,
               precio_unitario numeric, importe_venta numeric,
               op_gravada numeric, igv numeric, importe_total numeric)
LANGUAGE plpgsql STABLE AS $$
DECLARE
  v_sunat boolean := p_serie LIKE 'E%';
BEGIN
  RETURN QUERY
  WITH l AS (
    SELECT x.codigo::varchar AS codigo, x.cantidad,
           CASE WHEN v_sunat THEN round(x.precio_unitario / 1.18, 6)
                ELSE round(x.precio_unitario / 1.18, 2) END AS valor_unitario,
           x.precio_unitario
      FROM jsonb_to_recordset(p_lineas)
           AS x(codigo text, cantidad numeric, precio_unitario numeric)
  ), c AS (
    SELECT l.*,
           CASE WHEN v_sunat THEN round(l.cantidad * l.valor_unitario * 1.18, 7)
                ELSE l.cantidad * l.precio_unitario END AS importe_venta
      FROM l
  ), t AS (
    SELECT CASE WHEN v_sunat THEN round(sum(c.cantidad * c.valor_unitario), 2)
                ELSE round(sum(c.importe_venta) / 1.18, 2) END AS op_gravada,
           round(sum(c.importe_venta), 2) AS total
      FROM c
  )
  SELECT c.codigo, c.cantidad, c.valor_unitario,
         CASE WHEN v_sunat THEN NULL ELSE c.precio_unitario END,
         c.importe_venta, t.op_gravada, t.total - t.op_gravada, t.total
    FROM c CROSS JOIN t;
END;
$$;

-- Escribe las líneas y referencias de un comprobante a partir de JSON.
-- Crea los productos y documentos relacionados que aún no existan y
-- reactiva los productos dados de baja que se vuelven a vender.
--   p_lineas: [{"codigo", "descripcion", "unidad_medida", "cantidad",
--               "valor_unitario", "precio_unitario", "descuento",
--               "importe_venta"}, ...]
--   p_referencias: [{"tipo_relacionado", "numero_relacionado"}, ...]
CREATE OR REPLACE PROCEDURE sp_escribir_detalle(
  p_serie varchar, p_numero integer, p_lineas jsonb, p_referencias jsonb)
LANGUAGE plpgsql AS $$
DECLARE
  r record;
BEGIN
  IF p_lineas IS NULL OR jsonb_typeof(p_lineas) <> 'array'
     OR jsonb_array_length(p_lineas) = 0 THEN
    RAISE EXCEPTION 'El comprobante % debe tener al menos una línea de detalle.',
      fn_numeracion(p_serie, p_numero) USING ERRCODE = 'check_violation';
  END IF;

  FOR r IN SELECT * FROM jsonb_to_recordset(p_lineas) AS x(
             codigo text, descripcion text, unidad_medida text, cantidad numeric,
             valor_unitario numeric, precio_unitario numeric, descuento numeric,
             importe_venta numeric)
  LOOP
    IF NOT EXISTS (SELECT 1 FROM producto_servicio p
                    WHERE p.codigo = upper(btrim(r.codigo))) THEN
      IF fn_limpiar(r.descripcion) IS NULL OR fn_limpiar(r.unidad_medida) IS NULL THEN
        RAISE EXCEPTION 'El producto o servicio % no existe: indique su descripción y unidad de medida.',
          r.codigo USING ERRCODE = 'foreign_key_violation';
      END IF;
      CALL sp_insertar_producto(r.codigo, r.descripcion, r.unidad_medida);
    ELSE
      UPDATE producto_servicio SET eliminado = FALSE
       WHERE codigo = upper(btrim(r.codigo)) AND eliminado;
    END IF;
    INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
                         precio_unitario, descuento, importe_venta)
    VALUES (p_serie, p_numero, upper(btrim(r.codigo)), r.cantidad, r.valor_unitario,
            r.precio_unitario, coalesce(r.descuento, 0), r.importe_venta);
  END LOOP;

  FOR r IN SELECT * FROM jsonb_to_recordset(coalesce(p_referencias, '[]'::jsonb))
             AS x(tipo_relacionado text, numero_relacionado text)
  LOOP
    INSERT INTO documento_relacionado (tipo_relacionado, numero_relacionado)
    VALUES (r.tipo_relacionado, upper(btrim(r.numero_relacionado)))
    ON CONFLICT DO NOTHING;
    INSERT INTO referencia (serie, numero, tipo_relacionado, numero_relacionado)
    VALUES (p_serie, p_numero, r.tipo_relacionado, upper(btrim(r.numero_relacionado)));
  END LOOP;
END;
$$;

-- Registra un comprobante completo en una sola transacción: cabecera,
-- cliente, líneas y referencias.
--  * Sin documento de cliente: se registra a «CLIENTES VARIOS».
--  * Con documento: si el cliente no existe se crea; si existe, se
--    reactiva si estaba dado de baja y se actualizan su nombre y su
--    dirección con los impresos, que son los vigentes.
-- Los importes se registran tal como fueron impresos; los disparadores
-- verifican que sean coherentes entre sí y con las líneas.
CREATE OR REPLACE PROCEDURE sp_insertar_comprobante(
  p_serie varchar, p_numero integer, p_fecha_emision date,
  p_fecha_vencimiento date, p_codigo_moneda varchar, p_forma_pago varchar,
  p_medio_pago varchar, p_observacion varchar,
  p_op_gravada numeric, p_op_exonerada numeric, p_op_inafecta numeric,
  p_op_gratuita numeric, p_isc numeric, p_igv numeric, p_otros_cargos numeric,
  p_otros_tributos numeric, p_monto_redondeo numeric, p_importe_total numeric,
  p_percepcion numeric,
  p_tipo_documento varchar, p_numero_documento varchar,
  p_nombre_cliente varchar, p_direccion_cliente varchar,
  p_lineas jsonb, p_referencias jsonb)
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM comprobante WHERE serie = p_serie AND numero = p_numero) THEN
    RAISE EXCEPTION 'El comprobante % ya está registrado.',
      fn_numeracion(p_serie, p_numero)
      USING ERRCODE = 'unique_violation';
  END IF;

  IF fn_limpiar(p_numero_documento) IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM cliente
                    WHERE tipo_documento = p_tipo_documento
                      AND numero_documento = p_numero_documento) THEN
      IF fn_limpiar(p_nombre_cliente) IS NULL THEN
        RAISE EXCEPTION 'El cliente % % no existe: indique su nombre o razón social.',
          p_tipo_documento, p_numero_documento USING ERRCODE = 'foreign_key_violation';
      END IF;
      CALL sp_insertar_cliente(p_tipo_documento, p_numero_documento,
                               p_nombre_cliente, p_direccion_cliente);
    ELSE
      -- Solo se escribe si algo cambia, para no crear versiones de fila
      -- innecesarias en cada comprobante del mismo cliente
      UPDATE cliente
         SET eliminado = FALSE,
             nombre_razon_social = coalesce(upper(fn_limpiar(p_nombre_cliente)),
                                            nombre_razon_social),
             direccion = coalesce(upper(fn_limpiar(p_direccion_cliente)), direccion)
       WHERE tipo_documento = p_tipo_documento
         AND numero_documento = p_numero_documento
         AND (eliminado
              OR nombre_razon_social IS DISTINCT FROM
                 coalesce(upper(fn_limpiar(p_nombre_cliente)), nombre_razon_social)
              OR direccion IS DISTINCT FROM
                 coalesce(upper(fn_limpiar(p_direccion_cliente)), direccion));
    END IF;
  END IF;

  INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
    codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
    op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
    otros_tributos, monto_redondeo, importe_total, percepcion,
    tipo_documento, numero_documento)
  VALUES (p_serie, p_numero, p_fecha_emision, p_fecha_vencimiento,
    p_codigo_moneda, upper(fn_limpiar(p_forma_pago)), upper(fn_limpiar(p_medio_pago)),
    fn_limpiar(p_observacion), p_op_gravada, coalesce(p_op_exonerada, 0),
    coalesce(p_op_inafecta, 0), p_op_gratuita, p_isc, p_igv, p_otros_cargos,
    p_otros_tributos, p_monto_redondeo, p_importe_total, p_percepcion,
    nullif(fn_limpiar(p_tipo_documento), ''), fn_limpiar(p_numero_documento));

  CALL sp_escribir_detalle(p_serie, p_numero, p_lineas, p_referencias);
END;
$$;

-- Modifica un comprobante vigente: reemplaza la cabecera, el cliente, las
-- líneas y las referencias por los nuevos, en una sola transacción. Los
-- documentos relacionados que dejen de estar citados se eliminan, porque
-- solo existen en el registro por haber sido citados.
CREATE OR REPLACE PROCEDURE sp_modificar_comprobante(
  p_serie varchar, p_numero integer, p_fecha_emision date,
  p_fecha_vencimiento date, p_codigo_moneda varchar, p_forma_pago varchar,
  p_medio_pago varchar, p_observacion varchar,
  p_op_gravada numeric, p_op_exonerada numeric, p_op_inafecta numeric,
  p_op_gratuita numeric, p_isc numeric, p_igv numeric, p_otros_cargos numeric,
  p_otros_tributos numeric, p_monto_redondeo numeric, p_importe_total numeric,
  p_percepcion numeric,
  p_tipo_documento varchar, p_numero_documento varchar,
  p_nombre_cliente varchar, p_direccion_cliente varchar,
  p_lineas jsonb, p_referencias jsonb)
LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM comprobante
                  WHERE serie = p_serie AND numero = p_numero AND NOT eliminado) THEN
    RAISE EXCEPTION 'No existe un comprobante vigente %.',
      fn_numeracion(p_serie, p_numero)
      USING ERRCODE = 'no_data_found';
  END IF;

  IF fn_limpiar(p_numero_documento) IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM cliente
                      WHERE tipo_documento = p_tipo_documento
                        AND numero_documento = p_numero_documento) THEN
    IF fn_limpiar(p_nombre_cliente) IS NULL THEN
      RAISE EXCEPTION 'El cliente % % no existe: indique su nombre o razón social.',
        p_tipo_documento, p_numero_documento USING ERRCODE = 'foreign_key_violation';
    END IF;
    CALL sp_insertar_cliente(p_tipo_documento, p_numero_documento,
                             p_nombre_cliente, p_direccion_cliente);
  END IF;

  UPDATE comprobante
     SET fecha_emision = p_fecha_emision, fecha_vencimiento = p_fecha_vencimiento,
         codigo_moneda = p_codigo_moneda, forma_pago = upper(fn_limpiar(p_forma_pago)),
         medio_pago = upper(fn_limpiar(p_medio_pago)),
         observacion = fn_limpiar(p_observacion),
         op_gravada = p_op_gravada, op_exonerada = coalesce(p_op_exonerada, 0),
         op_inafecta = coalesce(p_op_inafecta, 0), op_gratuita = p_op_gratuita,
         isc = p_isc, igv = p_igv, otros_cargos = p_otros_cargos,
         otros_tributos = p_otros_tributos, monto_redondeo = p_monto_redondeo,
         importe_total = p_importe_total, percepcion = p_percepcion,
         tipo_documento = nullif(fn_limpiar(p_tipo_documento), ''),
         numero_documento = fn_limpiar(p_numero_documento)
   WHERE serie = p_serie AND numero = p_numero;

  DELETE FROM referencia WHERE serie = p_serie AND numero = p_numero;
  DELETE FROM incluye WHERE serie = p_serie AND numero = p_numero;
  CALL sp_escribir_detalle(p_serie, p_numero, p_lineas, p_referencias);

  DELETE FROM documento_relacionado d
   WHERE NOT EXISTS (SELECT 1 FROM referencia r
                      WHERE r.tipo_relacionado = d.tipo_relacionado
                        AND r.numero_relacionado = d.numero_relacionado);
END;
$$;

-- Borrado lógico de un comprobante: se conserva con sus líneas y
-- referencias, pero deja de aparecer en las consultas.
CREATE OR REPLACE PROCEDURE sp_eliminar_comprobante(p_serie varchar, p_numero integer)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE comprobante SET eliminado = TRUE
   WHERE serie = p_serie AND numero = p_numero AND NOT eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un comprobante vigente %.',
      fn_numeracion(p_serie, p_numero)
      USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- Deshace el borrado lógico de un comprobante
CREATE OR REPLACE PROCEDURE sp_restaurar_comprobante(p_serie varchar, p_numero integer)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE comprobante SET eliminado = FALSE
   WHERE serie = p_serie AND numero = p_numero AND eliminado;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No existe un comprobante eliminado %.',
      fn_numeracion(p_serie, p_numero)
      USING ERRCODE = 'no_data_found';
  END IF;
END;
$$;

-- Comprobantes eliminados de un periodo (para restaurarlos)
CREATE OR REPLACE FUNCTION fn_comprobantes_eliminados(p_desde date, p_hasta date)
RETURNS TABLE (serie varchar, numero integer, tipo_comprobante varchar,
               fecha_emision date, cliente varchar, importe_total numeric)
LANGUAGE sql STABLE AS $$
  SELECT c.serie, c.numero, s.tipo_comprobante, c.fecha_emision,
         coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS')::varchar,
         c.importe_total
    FROM comprobante c
    JOIN serie_comprobante s ON s.serie = c.serie
    LEFT JOIN cliente cl
           ON cl.tipo_documento = c.tipo_documento
          AND cl.numero_documento = c.numero_documento
   WHERE c.fecha_emision BETWEEN p_desde AND p_hasta
     AND c.eliminado
   ORDER BY c.fecha_emision, c.serie, c.numero;
$$;

-- ---------------------------------------------------------------------
-- DISPARADORES
-- ---------------------------------------------------------------------

-- Las operaciones y los tributos deben sumar el importe total (tarea
-- «Registro de datos económicos»). Se verifica en cada inserción o
-- modificación de la cabecera.
CREATE OR REPLACE FUNCTION fn_comprobante_importes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_suma numeric := NEW.op_gravada + NEW.op_exonerada + NEW.op_inafecta + NEW.igv
                    + coalesce(NEW.isc, 0) + coalesce(NEW.otros_cargos, 0)
                    + coalesce(NEW.otros_tributos, 0) + coalesce(NEW.monto_redondeo, 0);
BEGIN
  IF v_suma <> NEW.importe_total THEN
    RAISE EXCEPTION 'Los importes del comprobante % no cuadran: operaciones y tributos suman %, pero el importe total es %.',
      fn_numeracion(NEW.serie, NEW.numero), v_suma, NEW.importe_total USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_comprobante_importes ON comprobante;
CREATE TRIGGER tr_comprobante_importes
  BEFORE INSERT OR UPDATE OF op_gravada, op_exonerada, op_inafecta, isc, igv,
    otros_cargos, otros_tributos, monto_redondeo, importe_total ON comprobante
  FOR EACH ROW EXECUTE FUNCTION fn_comprobante_importes();

-- Al confirmar la transacción, todo comprobante debe tener al menos una
-- línea, y la suma de sus importes de venta, redondeada al céntimo, debe
-- ser igual a su importe total. Se verifica al final (diferido) porque
-- la cabecera y las líneas se escriben en sentencias distintas.
CREATE OR REPLACE FUNCTION fn_comprobante_lineas_total() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_serie varchar;
  v_numero integer;
  v_total numeric;
  v_lineas integer;
  v_suma numeric;
BEGIN
  IF TG_TABLE_NAME = 'incluye' AND TG_OP = 'DELETE' THEN
    v_serie := OLD.serie; v_numero := OLD.numero;
  ELSE
    v_serie := NEW.serie; v_numero := NEW.numero;
  END IF;
  -- El bloqueo de la cabecera ordena las transacciones que modifican las
  -- líneas de un mismo comprobante: dos borrados simultáneos no pueden
  -- dejarlo sin líneas aunque cada uno, por separado, deje una.
  SELECT importe_total INTO v_total
    FROM comprobante WHERE serie = v_serie AND numero = v_numero
     FOR UPDATE;
  IF NOT FOUND THEN
    RETURN NULL;
  END IF;
  SELECT count(*), round(coalesce(sum(importe_venta), 0), 2) INTO v_lineas, v_suma
    FROM incluye WHERE serie = v_serie AND numero = v_numero;
  IF v_lineas = 0 THEN
    RAISE EXCEPTION 'El comprobante % no puede quedar sin líneas de detalle.',
      fn_numeracion(v_serie, v_numero) USING ERRCODE = 'check_violation';
  ELSIF v_suma <> v_total THEN
    RAISE EXCEPTION 'Las líneas del comprobante % suman %, pero su importe total es %.',
      fn_numeracion(v_serie, v_numero), v_suma, v_total USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS tr_comprobante_lineas_total ON comprobante;
CREATE CONSTRAINT TRIGGER tr_comprobante_lineas_total
  AFTER INSERT OR UPDATE ON comprobante
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_comprobante_lineas_total();

DROP TRIGGER IF EXISTS tr_incluye_lineas_total ON incluye;
CREATE CONSTRAINT TRIGGER tr_incluye_lineas_total
  AFTER INSERT OR UPDATE OR DELETE ON incluye
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_comprobante_lineas_total();


-- #####################################################################
-- 06_reportes.sql
-- #####################################################################

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


-- #####################################################################
-- 07_datos_aplicativo.sql
-- #####################################################################

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


-- #####################################################################
-- 08_permisos.sql
-- #####################################################################

-- =====================================================================
-- Script 8: rol del aplicativo y permisos
-- Gestor: PostgreSQL 17. Ejecutar como administrador después de los
-- scripts 04 a 07, y otra vez cada vez que se vuelvan a crear las
-- rutinas (CREATE OR REPLACE restablece sus atributos):
--   psql -U postgres -d registro_comprobantes -f 08_permisos.sql
--
-- El aplicativo no se conecta como superusuario, sino con el rol
-- gdi_app, que solo puede:
--  * leer las tablas (SELECT), para consultas de verificación, y
--  * ejecutar las funciones y los procedimientos del aplicativo.
-- No puede insertar, modificar ni borrar filas directamente: toda
-- escritura pasa por un procedimiento, que se ejecuta con los permisos
-- de su dueño (SECURITY DEFINER) y con search_path fijo.
-- La contraseña no se escribe aquí; se asigna una vez, desde psql:
--   \password gdi_app
-- El script puede ejecutarse varias veces.
-- =====================================================================

\set ON_ERROR_STOP on

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gdi_app') THEN
    CREATE ROLE gdi_app LOGIN;
  END IF;
END;
$$;

GRANT CONNECT ON DATABASE registro_comprobantes TO gdi_app;
GRANT USAGE ON SCHEMA public TO gdi_app;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO gdi_app;

-- Las rutinas que leen o escriben tablas se ejecutan con los permisos de
-- su dueño; las auxiliares inmutables (fn_limpiar, fn_numeracion) no lo
-- necesitan. Nadie más que gdi_app (y el dueño) puede ejecutarlas.
-- Las funciones de los disparadores también se ejecutan con los permisos
-- del dueño: los disparadores diferidos se disparan al confirmar, fuera
-- del procedimiento, y deben poder bloquear la cabecera del comprobante.
DO $$
DECLARE
  r record;
  v_clase text;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS firma, p.prokind, p.provolatile, p.prorettype
      FROM pg_proc p
     WHERE p.pronamespace = 'public'::regnamespace
       AND p.prokind IN ('f', 'p')
       AND NOT EXISTS (SELECT 1 FROM pg_depend d
                        WHERE d.objid = p.oid AND d.deptype = 'e')
  LOOP
    v_clase := CASE r.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END;
    IF r.provolatile <> 'i' THEN
      EXECUTE format('ALTER %s %s SECURITY DEFINER SET search_path = public, pg_temp',
                     v_clase, r.firma);
    END IF;
    EXECUTE format('REVOKE ALL ON %s %s FROM PUBLIC', v_clase, r.firma);
    IF r.prorettype <> 'trigger'::regtype THEN
      EXECUTE format('GRANT EXECUTE ON %s %s TO gdi_app', v_clase, r.firma);
    END IF;
  END LOOP;
END;
$$;

-- Resumen de los permisos del rol
SELECT 'rutinas ejecutables' AS permiso, count(*) AS cantidad
  FROM pg_proc p
 WHERE p.pronamespace = 'public'::regnamespace
   AND has_function_privilege('gdi_app', p.oid, 'EXECUTE')
   AND p.prorettype <> 'trigger'::regtype
UNION ALL
SELECT 'tablas legibles', count(*) FILTER (WHERE has_table_privilege('gdi_app', c.oid, 'SELECT'))
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r'
UNION ALL
SELECT 'tablas modificables',
       count(*) FILTER (WHERE has_table_privilege('gdi_app', c.oid, 'INSERT,UPDATE,DELETE'))
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r';
