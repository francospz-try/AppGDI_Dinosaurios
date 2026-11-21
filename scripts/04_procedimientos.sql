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
-- Texto independiente de la configuración regional de la instancia: con
-- la configuración C, upper() y ILIKE no tratan las letras con tilde ni
-- la ñ. fn_mayusculas las convierte antes de upper(); fn_clave_busqueda
-- las lleva a ASCII en minúsculas, para buscar sin distinguir mayúsculas
-- ni tildes («clinica» encuentra «CLÍNICA»; la ñ se busca como n).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_mayusculas(p_texto varchar)
RETURNS varchar LANGUAGE sql IMMUTABLE AS $$
  SELECT upper(translate(p_texto, 'áéíóúüñàèìòùâêîôûäëïöç', 'ÁÉÍÓÚÜÑÀÈÌÒÙÂÊÎÔÛÄËÏÖÇ'));
$$;

CREATE OR REPLACE FUNCTION fn_clave_busqueda(p_texto varchar)
RETURNS varchar LANGUAGE sql IMMUTABLE AS $$
  SELECT lower(translate(p_texto, 'ÁÉÍÓÚÜÑÀÈÌÒÙÂÊÎÔÛÄËÏÖÇáéíóúüñàèìòùâêîôûäëïöç', 'AEIOUUNAEIOUAEIOUAEIOCaeiouunaeiouaeiouaeioc'));
$$;

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
-- (cualquier parte, sin distinguir mayúsculas ni tildes). Texto vacío:
-- todos.
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
          OR fn_clave_busqueda(cl.nombre_razon_social)
             LIKE '%' || fn_clave_busqueda(p_texto) || '%')
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
          OR fn_clave_busqueda(p.codigo) LIKE fn_clave_busqueda(p_texto) || '%'
          OR fn_clave_busqueda(p.descripcion) LIKE '%' || fn_clave_busqueda(p_texto) || '%')
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
