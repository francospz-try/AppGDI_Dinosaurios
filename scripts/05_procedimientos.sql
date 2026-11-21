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
   WHERE tipo_documento = fn_mayusculas(btrim(p_tipo_documento))
     AND numero_documento = btrim(p_numero_documento);
  IF v_eliminado IS FALSE THEN
    RAISE EXCEPTION 'Ya existe un cliente con % %.', fn_mayusculas(btrim(p_tipo_documento)),
      btrim(p_numero_documento) USING ERRCODE = 'unique_violation';
  ELSIF v_eliminado IS TRUE THEN
    UPDATE cliente
       SET nombre_razon_social = fn_mayusculas(fn_limpiar(p_nombre)),
           direccion = fn_mayusculas(fn_limpiar(p_direccion)),
           eliminado = FALSE
     WHERE tipo_documento = fn_mayusculas(btrim(p_tipo_documento))
       AND numero_documento = btrim(p_numero_documento);
  ELSE
    INSERT INTO cliente (tipo_documento, numero_documento, nombre_razon_social, direccion)
    VALUES (fn_mayusculas(btrim(p_tipo_documento)), btrim(p_numero_documento),
            coalesce(fn_mayusculas(fn_limpiar(p_nombre)), ''), fn_mayusculas(fn_limpiar(p_direccion)));
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
     SET nombre_razon_social = coalesce(fn_mayusculas(fn_limpiar(p_nombre)), ''),
         direccion = fn_mayusculas(fn_limpiar(p_direccion))
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
   WHERE codigo = fn_mayusculas(btrim(p_codigo));
  IF v_eliminado IS FALSE THEN
    RAISE EXCEPTION 'Ya existe un producto o servicio con el código %.',
      fn_mayusculas(btrim(p_codigo)) USING ERRCODE = 'unique_violation';
  ELSIF v_eliminado IS TRUE THEN
    UPDATE producto_servicio
       SET descripcion = coalesce(fn_mayusculas(fn_limpiar(p_descripcion)), ''),
           unidad_medida = fn_mayusculas(btrim(p_unidad_medida)),
           eliminado = FALSE
     WHERE codigo = fn_mayusculas(btrim(p_codigo));
  ELSE
    INSERT INTO producto_servicio (codigo, descripcion, unidad_medida)
    VALUES (coalesce(fn_mayusculas(btrim(p_codigo)), ''),
            coalesce(fn_mayusculas(fn_limpiar(p_descripcion)), ''),
            fn_mayusculas(btrim(p_unidad_medida)));
  END IF;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_modificar_producto(
  p_codigo varchar, p_descripcion varchar, p_unidad_medida varchar)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE producto_servicio
     SET descripcion = coalesce(fn_mayusculas(fn_limpiar(p_descripcion)), ''),
         unidad_medida = fn_mayusculas(btrim(p_unidad_medida))
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
                    WHERE p.codigo = fn_mayusculas(btrim(r.codigo))) THEN
      IF fn_limpiar(r.descripcion) IS NULL OR fn_limpiar(r.unidad_medida) IS NULL THEN
        RAISE EXCEPTION 'El producto o servicio % no existe: indique su descripción y unidad de medida.',
          r.codigo USING ERRCODE = 'foreign_key_violation';
      END IF;
      CALL sp_insertar_producto(r.codigo, r.descripcion, r.unidad_medida);
    ELSE
      UPDATE producto_servicio SET eliminado = FALSE
       WHERE codigo = fn_mayusculas(btrim(r.codigo)) AND eliminado;
    END IF;
    INSERT INTO incluye (serie, numero, codigo, cantidad, valor_unitario,
                         precio_unitario, descuento, importe_venta)
    VALUES (p_serie, p_numero, fn_mayusculas(btrim(r.codigo)), r.cantidad, r.valor_unitario,
            r.precio_unitario, coalesce(r.descuento, 0), r.importe_venta);
  END LOOP;

  FOR r IN SELECT * FROM jsonb_to_recordset(coalesce(p_referencias, '[]'::jsonb))
             AS x(tipo_relacionado text, numero_relacionado text)
  LOOP
    INSERT INTO documento_relacionado (tipo_relacionado, numero_relacionado)
    VALUES (r.tipo_relacionado, fn_mayusculas(btrim(r.numero_relacionado)))
    ON CONFLICT DO NOTHING;
    INSERT INTO referencia (serie, numero, tipo_relacionado, numero_relacionado)
    VALUES (p_serie, p_numero, r.tipo_relacionado, fn_mayusculas(btrim(r.numero_relacionado)));
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
             nombre_razon_social = coalesce(fn_mayusculas(fn_limpiar(p_nombre_cliente)),
                                            nombre_razon_social),
             direccion = coalesce(fn_mayusculas(fn_limpiar(p_direccion_cliente)), direccion)
       WHERE tipo_documento = p_tipo_documento
         AND numero_documento = p_numero_documento
         AND (eliminado
              OR nombre_razon_social IS DISTINCT FROM
                 coalesce(fn_mayusculas(fn_limpiar(p_nombre_cliente)), nombre_razon_social)
              OR direccion IS DISTINCT FROM
                 coalesce(fn_mayusculas(fn_limpiar(p_direccion_cliente)), direccion));
    END IF;
  END IF;

  INSERT INTO comprobante (serie, numero, fecha_emision, fecha_vencimiento,
    codigo_moneda, forma_pago, medio_pago, observacion, op_gravada,
    op_exonerada, op_inafecta, op_gratuita, isc, igv, otros_cargos,
    otros_tributos, monto_redondeo, importe_total, percepcion,
    tipo_documento, numero_documento)
  VALUES (p_serie, p_numero, p_fecha_emision, p_fecha_vencimiento,
    p_codigo_moneda, fn_mayusculas(fn_limpiar(p_forma_pago)), fn_mayusculas(fn_limpiar(p_medio_pago)),
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
         codigo_moneda = p_codigo_moneda, forma_pago = fn_mayusculas(fn_limpiar(p_forma_pago)),
         medio_pago = fn_mayusculas(fn_limpiar(p_medio_pago)),
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
