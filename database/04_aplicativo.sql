\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA IF NOT EXISTS app;
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;

CREATE OR REPLACE PROCEDURE app.sp_probar_conexion(INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR SELECT current_database() AS base_datos,
    current_user AS usuario, current_setting('server_version') AS version;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_listar_comprobantes(IN busqueda text, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR
  SELECT c.serie, c.numero, c.fecha_emision, s.tipo_comprobante,
    coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS') AS cliente,
    c.codigo_moneda, c.importe_total
  FROM public.comprobante c
  JOIN public.serie_comprobante s ON s.serie=c.serie
  LEFT JOIN public.cliente cl ON (cl.tipo_documento,cl.numero_documento)=(c.tipo_documento,c.numero_documento)
  WHERE coalesce(busqueda,'')=''
    OR c.serie || '-' || c.numero::text ILIKE '%' || busqueda || '%'
    OR c.serie || '-' || lpad(c.numero::text,greatest(6,length(c.numero::text)),'0') ILIKE '%' || busqueda || '%'
    OR coalesce(cl.nombre_razon_social,'CLIENTES VARIOS') ILIKE '%' || busqueda || '%'
    OR cl.numero_documento ILIKE '%' || busqueda || '%'
  ORDER BY c.fecha_emision DESC, c.serie, c.numero DESC LIMIT 500;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_ficha_comprobante(IN p_serie text, IN p_numero int, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR SELECT c.*, s.tipo_comprobante,
    coalesce(cl.nombre_razon_social, 'CLIENTES VARIOS') AS cliente,
    m.nombre_moneda,
    c.importe_total + coalesce(c.percepcion,0) AS total_a_cobrar
  FROM public.comprobante c
  JOIN public.serie_comprobante s ON s.serie=c.serie
  JOIN public.moneda m ON m.codigo_moneda=c.codigo_moneda
  LEFT JOIN public.cliente cl ON (cl.tipo_documento,cl.numero_documento)=(c.tipo_documento,c.numero_documento)
  WHERE c.serie=p_serie AND c.numero=p_numero;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_detalle_comprobante(IN p_serie text, IN p_numero int, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR SELECT i.codigo, p.descripcion, p.unidad_medida,
    i.cantidad, i.valor_unitario, i.precio_unitario, i.descuento, i.importe_venta
  FROM public.incluye i JOIN public.producto_servicio p ON p.codigo=i.codigo
  WHERE i.serie=p_serie AND i.numero=p_numero ORDER BY i.codigo;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_referencias_comprobante(IN p_serie text, IN p_numero int, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR SELECT r.tipo_relacionado, r.numero_relacionado
  FROM public.referencia r WHERE r.serie=p_serie AND r.numero=p_numero
  ORDER BY r.tipo_relacionado, r.numero_relacionado;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_listar_clientes(IN busqueda text, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
BEGIN
  OPEN resultado FOR SELECT tipo_documento, numero_documento, nombre_razon_social, direccion
  FROM public.cliente WHERE activo AND
    (coalesce(busqueda,'')='' OR nombre_razon_social ILIKE '%'||busqueda||'%'
       OR numero_documento ILIKE '%'||busqueda||'%')
  ORDER BY nombre_razon_social, tipo_documento, numero_documento LIMIT 500;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_crear_cliente(IN tipo text, IN documento text, IN nombre text, IN domicilio text)
LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO public.cliente(tipo_documento,numero_documento,nombre_razon_social,direccion)
  VALUES(tipo,documento,btrim(nombre),nullif(btrim(domicilio),''));
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_modificar_cliente(IN tipo text, IN documento text, IN nombre text, IN domicilio text)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE public.cliente SET nombre_razon_social=btrim(nombre),direccion=nullif(btrim(domicilio),'')
  WHERE tipo_documento=tipo AND numero_documento=documento AND activo;
  IF NOT FOUND THEN RAISE EXCEPTION 'Cliente no disponible' USING ERRCODE='P0002'; END IF;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_baja_cliente(IN tipo text, IN documento text)
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE public.cliente SET activo=false
  WHERE tipo_documento=tipo AND numero_documento=documento AND activo;
  IF NOT FOUND THEN RAISE EXCEPTION 'Cliente no disponible' USING ERRCODE='P0002'; END IF;
END; $$;

COMMIT;
