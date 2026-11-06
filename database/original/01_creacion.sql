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
