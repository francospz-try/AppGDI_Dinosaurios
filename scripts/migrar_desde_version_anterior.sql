-- =====================================================================
-- Migración de una base existente de la versión anterior del aplicativo
-- (scripts database/ del commit b8be9b7 del repositorio AppGDI_Dinosaurios:
-- originales 01 y 02, y 04, 07, 05 y 06) al esquema actual, SIN PERDER
-- DATOS y sin volver a crear la base.
-- Gestor: PostgreSQL 17. Ejecutar como administrador, conectado a la base
-- existente, desde la carpeta principal del repositorio:
--   pg_dump -U postgres -Fc -d registro_comprobantes -f antes_de_migrar.dump
--   psql -U postgres -d registro_comprobantes -f scripts/migrar_desde_version_anterior.sql
--
-- Todo ocurre en una sola transacción: si algún paso falla, la base queda
-- exactamente como estaba. Pasos:
--  1. Comprueba que la base sea de la versión anterior y cuenta sus filas.
--  2. Baja lógica: la columna activo de cliente, producto_servicio y
--     comprobante pasa a eliminado (eliminado = NOT activo).
--  3. Lo que el esquema actual no representa ---bajas de series, monedas y
--     documentos relacionados, y la versión de los comprobantes editados---
--     se copia al esquema aparte «migracion», que puede consultarse o
--     borrarse después de revisarlo.
--  4. Retira el código de la versión anterior: el esquema app (vistas y
--     procedimientos, sin datos) y su disparador sobre incluye; restaura la
--     función original de tr_comprobante_con_detalle.
--  5. Crea las rutinas, disparadores y reportes actuales (scripts 04 a 06).
--  6. Verifica que ninguna tabla perdió filas e informa los comprobantes que
--     no cumplirían las reglas nuevas de importes (no los modifica: los
--     disparadores nuevos solo se aplican a los cambios futuros).
--  7. Aplica los permisos del rol gdi_app (script 08): solo lectura de las
--     tablas y ejecución de las rutinas. Su contraseña no cambia.
-- Una base nueva no necesita este script: se crea con
-- registro_comprobantes_completo.sql (que BORRA la base si existe).
-- =====================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------
-- 1. Comprobaciones y conteo inicial
-- ---------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = 'cliente'
                AND column_name = 'eliminado') THEN
    RAISE EXCEPTION 'La base ya tiene el esquema actual (cliente.eliminado): no hay nada que migrar.';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'public' AND table_name = 'cliente'
                    AND column_name = 'activo')
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'comprobante'
                       AND column_name = 'version') THEN
    RAISE EXCEPTION 'La base no corresponde a la versión anterior del aplicativo (faltan las columnas activo o version).';
  END IF;
END;
$$;

CREATE TEMP TABLE conteo_antes ON COMMIT DROP AS
SELECT 'cliente' AS tabla, count(*) AS filas FROM cliente
UNION ALL SELECT 'serie_comprobante', count(*) FROM serie_comprobante
UNION ALL SELECT 'moneda', count(*) FROM moneda
UNION ALL SELECT 'producto_servicio', count(*) FROM producto_servicio
UNION ALL SELECT 'documento_relacionado', count(*) FROM documento_relacionado
UNION ALL SELECT 'comprobante', count(*) FROM comprobante
UNION ALL SELECT 'incluye', count(*) FROM incluye
UNION ALL SELECT 'referencia', count(*) FROM referencia;

-- ---------------------------------------------------------------------
-- 2. Baja lógica: activo pasa a eliminado
-- ---------------------------------------------------------------------
ALTER TABLE cliente ADD COLUMN eliminado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE producto_servicio ADD COLUMN eliminado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comprobante ADD COLUMN eliminado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE cliente SET eliminado = TRUE WHERE NOT activo;
UPDATE producto_servicio SET eliminado = TRUE WHERE NOT activo;
UPDATE comprobante SET eliminado = TRUE WHERE NOT activo;

-- ---------------------------------------------------------------------
-- 3. Datos sin lugar en el esquema actual: se conservan aparte
-- ---------------------------------------------------------------------
CREATE SCHEMA migracion;
COMMENT ON SCHEMA migracion IS
  'Datos de la versión anterior del aplicativo sin columna en el esquema actual; '
  'creado por scripts/migrar_desde_version_anterior.sql. Puede borrarse tras revisarlo.';

CREATE TABLE migracion.bajas_version_anterior (
  tabla text  NOT NULL,
  clave jsonb NOT NULL,
  PRIMARY KEY (tabla, clave)
);
COMMENT ON TABLE migracion.bajas_version_anterior IS
  'Series, monedas y documentos relacionados que estaban dados de baja (activo = false).';

INSERT INTO migracion.bajas_version_anterior (tabla, clave)
SELECT 'serie_comprobante', jsonb_build_object('serie', serie)
  FROM serie_comprobante WHERE NOT activo
UNION ALL
SELECT 'moneda', jsonb_build_object('codigo_moneda', codigo_moneda)
  FROM moneda WHERE NOT activo
UNION ALL
SELECT 'documento_relacionado', jsonb_build_object('tipo_relacionado', tipo_relacionado,
                                                   'numero_relacionado', numero_relacionado)
  FROM documento_relacionado WHERE NOT activo;

CREATE TABLE migracion.versiones_comprobante (
  serie   varchar(4) NOT NULL,
  numero  integer    NOT NULL,
  version integer    NOT NULL,
  PRIMARY KEY (serie, numero)
);
COMMENT ON TABLE migracion.versiones_comprobante IS
  'Número de versión (control de ediciones simultáneas) de los comprobantes editados.';

INSERT INTO migracion.versiones_comprobante (serie, numero, version)
SELECT serie, numero, version FROM comprobante WHERE version > 1;

-- ---------------------------------------------------------------------
-- 4. Código de la versión anterior
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS tr_detalle_conserva_comprobante ON incluye;
DROP SCHEMA app CASCADE;

ALTER TABLE cliente DROP COLUMN activo;
ALTER TABLE serie_comprobante DROP COLUMN activo;
ALTER TABLE moneda DROP COLUMN activo;
ALTER TABLE producto_servicio DROP COLUMN activo;
ALTER TABLE documento_relacionado DROP COLUMN activo;
ALTER TABLE comprobante DROP COLUMN activo;
ALTER TABLE comprobante DROP COLUMN version;

-- Función original del disparador tr_comprobante_con_detalle (01_creacion.sql)
CREATE OR REPLACE FUNCTION fn_comprobante_con_detalle() RETURNS trigger
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

-- ---------------------------------------------------------------------
-- 5. Rutinas, disparadores y reportes actuales
-- ---------------------------------------------------------------------
\ir 04_procedimientos.sql
\ir 05_procedimientos.sql
\ir 06_reportes.sql

-- ---------------------------------------------------------------------
-- 6. Verificación
-- ---------------------------------------------------------------------
DO $$
DECLARE
  r record;
  v_ahora bigint;
  v_importes bigint;
  v_lineas bigint;
BEGIN
  FOR r IN SELECT tabla, filas FROM conteo_antes LOOP
    EXECUTE format('SELECT count(*) FROM %I', r.tabla) INTO v_ahora;
    IF v_ahora <> r.filas THEN
      RAISE EXCEPTION 'La tabla % tenía % filas y ahora tiene %: se cancela la migración.',
        r.tabla, r.filas, v_ahora;
    END IF;
  END LOOP;

  SELECT count(*) INTO v_importes
    FROM comprobante c
   WHERE c.op_gravada + c.op_exonerada + c.op_inafecta + c.igv + coalesce(c.isc, 0)
         + coalesce(c.otros_cargos, 0) + coalesce(c.otros_tributos, 0)
         + coalesce(c.monto_redondeo, 0) <> c.importe_total;
  SELECT count(*) INTO v_lineas
    FROM comprobante c
   WHERE round((SELECT coalesce(sum(i.importe_venta), 0) FROM incluye i
                 WHERE i.serie = c.serie AND i.numero = c.numero), 2) <> c.importe_total;
  RAISE NOTICE 'Filas conservadas en las 8 tablas. Comprobantes cuyos importes no suman el total: %; cuyas líneas no suman el total: %. Revíselos con el reporte R08; los disparadores nuevos rechazarán cualquier cambio que no cuadre.',
    v_importes, v_lineas;
END;
$$;

-- ---------------------------------------------------------------------
-- 7. Permisos del rol del aplicativo
-- ---------------------------------------------------------------------
\ir 08_permisos.sql

COMMIT;

ANALYZE;

-- Resumen
SELECT 'cliente' AS tabla, count(*) AS filas, count(*) FILTER (WHERE eliminado) AS eliminadas
  FROM cliente
UNION ALL SELECT 'producto_servicio', count(*), count(*) FILTER (WHERE eliminado) FROM producto_servicio
UNION ALL SELECT 'comprobante', count(*), count(*) FILTER (WHERE eliminado) FROM comprobante
UNION ALL SELECT 'migracion.bajas_version_anterior', count(*), NULL FROM migracion.bajas_version_anterior
UNION ALL SELECT 'migracion.versiones_comprobante', count(*), NULL FROM migracion.versiones_comprobante;
