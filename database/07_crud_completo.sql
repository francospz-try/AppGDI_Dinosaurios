\set ON_ERROR_STOP on
BEGIN;
ALTER TABLE public.comprobante ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE public.comprobante ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 1;
ALTER TABLE public.serie_comprobante ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE public.moneda ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE public.producto_servicio ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE public.documento_relacionado ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;

CREATE OR REPLACE VIEW app.v_comprobante AS SELECT * FROM public.comprobante WHERE activo;
CREATE OR REPLACE VIEW app.v_incluye AS SELECT i.* FROM public.incluye i JOIN public.comprobante c USING(serie,numero) WHERE c.activo;
CREATE OR REPLACE VIEW app.v_referencia AS SELECT r.* FROM public.referencia r JOIN public.comprobante c USING(serie,numero) WHERE c.activo;

CREATE OR REPLACE PROCEDURE app.sp_buscar_comprobantes(IN busqueda text, IN incluir_bajas boolean, INOUT resultado refcursor)
LANGUAGE plpgsql AS $$ BEGIN
 OPEN resultado FOR SELECT c.serie,c.numero,c.fecha_emision,s.tipo_comprobante,
   coalesce(cl.nombre_razon_social,'CLIENTES VARIOS') AS cliente,c.codigo_moneda,c.importe_total,c.activo
 FROM public.comprobante c JOIN public.serie_comprobante s USING(serie)
 LEFT JOIN public.cliente cl ON (cl.tipo_documento,cl.numero_documento)=(c.tipo_documento,c.numero_documento)
 WHERE (c.activo OR incluir_bajas) AND (coalesce(busqueda,'')=''
 OR c.serie||'-'||c.numero::text ILIKE '%'||busqueda||'%'
 OR c.serie||'-'||lpad(c.numero::text,greatest(6,length(c.numero::text)),'0') ILIKE '%'||busqueda||'%'
 OR coalesce(cl.nombre_razon_social,'CLIENTES VARIOS') ILIKE '%'||busqueda||'%'
 OR cl.numero_documento ILIKE '%'||busqueda||'%')
 ORDER BY c.fecha_emision DESC,c.serie,c.numero DESC LIMIT 500;
END; $$;
CREATE OR REPLACE PROCEDURE app.sp_listar_comprobantes(IN busqueda text,INOUT resultado refcursor)
LANGUAGE plpgsql AS $$ BEGIN CALL app.sp_buscar_comprobantes(busqueda,false,resultado); END; $$;

CREATE OR REPLACE PROCEDURE app.sp_guardar_comprobante(IN datos jsonb,IN crear boolean)
LANGUAGE plpgsql AS $$
DECLARE r public.comprobante; anterior public.comprobante; item jsonb; v numeric; referencias_anteriores jsonb:='[]'::jsonb;
BEGIN
 IF jsonb_typeof(datos->'cabecera') IS DISTINCT FROM 'object'
 OR jsonb_typeof(datos->'detalle') IS DISTINCT FROM 'array'
 OR jsonb_array_length(datos->'detalle')=0
 OR jsonb_typeof(datos->'referencias') IS DISTINCT FROM 'array' THEN
  RAISE EXCEPTION 'Cabecera y al menos una línea son obligatorias' USING ERRCODE='23514';
 END IF;
 FOR item IN SELECT jsonb_build_object('campo',key,'valor',value) FROM jsonb_each(datos->'cabecera')
 WHERE key IN ('op_gravada','op_exonerada','op_inafecta','op_gratuita','isc','igv','otros_cargos','otros_tributos','monto_redondeo','importe_total','percepcion') LOOP
  IF item->'valor'<>'null'::jsonb THEN
   v:=(item->>'valor')::numeric;
   IF v<>round(v,2) THEN RAISE EXCEPTION 'Importe de cabecera con más de dos decimales' USING ERRCODE='23514'; END IF;
  END IF;
 END LOOP;
 FOR item IN SELECT value FROM jsonb_array_elements(datos->'detalle') LOOP
  IF (item->>'cantidad')::numeric<>round((item->>'cantidad')::numeric,2) THEN
   RAISE EXCEPTION 'Cantidad con más de dos decimales' USING ERRCODE='23514';
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_each(item) e WHERE e.key IN ('valor_unitario','precio_unitario','descuento','importe_venta')
   AND e.value<>'null'::jsonb AND (e.value#>>'{}')::numeric<>round((e.value#>>'{}')::numeric,7)) THEN
   RAISE EXCEPTION 'Valor de línea con más de siete decimales' USING ERRCODE='23514';
  END IF;
 END LOOP;
 r:=jsonb_populate_record(NULL::public.comprobante,datos->'cabecera');
 r.op_exonerada:=coalesce(r.op_exonerada,0);r.op_inafecta:=coalesce(r.op_inafecta,0);
 IF NOT crear THEN
  SELECT * INTO anterior FROM public.comprobante WHERE serie=r.serie AND numero=r.numero FOR UPDATE;
  IF NOT FOUND OR NOT anterior.activo THEN RAISE EXCEPTION 'Comprobante no disponible' USING ERRCODE='P0002'; END IF;
  IF anterior.version IS DISTINCT FROM r.version THEN RAISE EXCEPTION 'Edición obsoleta' USING ERRCODE='40001'; END IF;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM public.serie_comprobante WHERE serie=r.serie AND (activo OR NOT crear))
 OR NOT EXISTS(SELECT 1 FROM public.moneda WHERE codigo_moneda=r.codigo_moneda AND (activo OR codigo_moneda=anterior.codigo_moneda)) THEN
  RAISE EXCEPTION 'Serie o moneda no disponible' USING ERRCODE='23503';
 END IF;
 IF r.tipo_documento IS NOT NULL OR r.numero_documento IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM public.cliente cl WHERE (cl.tipo_documento,cl.numero_documento)=(r.tipo_documento,r.numero_documento)
   AND (cl.activo OR (cl.tipo_documento,cl.numero_documento)=(anterior.tipo_documento,anterior.numero_documento))) THEN
   RAISE EXCEPTION 'Cliente no disponible' USING ERRCODE='23503';
  END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM jsonb_array_elements(datos->'detalle') d WHERE NOT EXISTS(
  SELECT 1 FROM public.producto_servicio p WHERE p.codigo=d->>'codigo' AND (p.activo OR (NOT crear AND EXISTS(
   SELECT 1 FROM public.incluye i WHERE i.serie=r.serie AND i.numero=r.numero AND i.codigo=p.codigo))))) THEN
  RAISE EXCEPTION 'Producto no disponible' USING ERRCODE='23503';
 END IF;
 IF crear THEN
  INSERT INTO public.comprobante(serie,numero,fecha_emision,fecha_vencimiento,codigo_moneda,forma_pago,medio_pago,observacion,op_gravada,op_exonerada,op_inafecta,op_gratuita,isc,igv,otros_cargos,otros_tributos,monto_redondeo,importe_total,percepcion,tipo_documento,numero_documento)
  VALUES(r.serie,r.numero,r.fecha_emision,r.fecha_vencimiento,r.codigo_moneda,r.forma_pago,r.medio_pago,r.observacion,r.op_gravada,r.op_exonerada,r.op_inafecta,r.op_gratuita,r.isc,r.igv,r.otros_cargos,r.otros_tributos,r.monto_redondeo,r.importe_total,r.percepcion,r.tipo_documento,r.numero_documento);
 ELSE
  UPDATE public.comprobante SET fecha_emision=r.fecha_emision,fecha_vencimiento=r.fecha_vencimiento,codigo_moneda=r.codigo_moneda,forma_pago=r.forma_pago,medio_pago=r.medio_pago,observacion=r.observacion,op_gravada=r.op_gravada,op_exonerada=r.op_exonerada,op_inafecta=r.op_inafecta,op_gratuita=r.op_gratuita,isc=r.isc,igv=r.igv,otros_cargos=r.otros_cargos,otros_tributos=r.otros_tributos,monto_redondeo=r.monto_redondeo,importe_total=r.importe_total,percepcion=r.percepcion,tipo_documento=r.tipo_documento,numero_documento=r.numero_documento,version=version+1
  WHERE serie=r.serie AND numero=r.numero;
  SELECT coalesce(jsonb_agg(jsonb_build_object('tipo_relacionado',tipo_relacionado,'numero_relacionado',numero_relacionado)),'[]'::jsonb)
   INTO referencias_anteriores FROM public.referencia WHERE serie=r.serie AND numero=r.numero;
  DELETE FROM public.referencia WHERE serie=r.serie AND numero=r.numero;
  DELETE FROM public.incluye WHERE serie=r.serie AND numero=r.numero;
 END IF;
 INSERT INTO public.incluye(serie,numero,codigo,cantidad,valor_unitario,precio_unitario,descuento,importe_venta)
 SELECT r.serie,r.numero,d.codigo,d.cantidad,d.valor_unitario,d.precio_unitario,coalesce(d.descuento,0),d.importe_venta
 FROM jsonb_to_recordset(datos->'detalle') AS d(codigo text,cantidad numeric,valor_unitario numeric,precio_unitario numeric,descuento numeric,importe_venta numeric);
 FOR item IN SELECT value FROM jsonb_array_elements(datos->'referencias') LOOP
  IF btrim(coalesce(item->>'numero_relacionado',''))='' THEN RAISE EXCEPTION 'Referencia vacía' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM public.documento_relacionado d WHERE d.tipo_relacionado=item->>'tipo_relacionado'
    AND d.numero_relacionado=item->>'numero_relacionado' AND NOT d.activo) AND NOT EXISTS(
    SELECT 1 FROM jsonb_array_elements(referencias_anteriores) old_ref WHERE old_ref->>'tipo_relacionado'=item->>'tipo_relacionado' AND old_ref->>'numero_relacionado'=item->>'numero_relacionado') THEN
   RAISE EXCEPTION 'Documento relacionado dado de baja' USING ERRCODE='23503';
  END IF;
  INSERT INTO public.documento_relacionado(tipo_relacionado,numero_relacionado)
  VALUES(item->>'tipo_relacionado',item->>'numero_relacionado') ON CONFLICT DO NOTHING;
  INSERT INTO public.referencia(serie,numero,tipo_relacionado,numero_relacionado)
  VALUES(r.serie,r.numero,item->>'tipo_relacionado',item->>'numero_relacionado');
 END LOOP;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_baja_comprobante(IN p_serie text,IN p_numero int,IN p_version int)
LANGUAGE plpgsql AS $$
DECLARE anterior public.comprobante;
BEGIN
 SELECT * INTO anterior FROM public.comprobante WHERE serie=p_serie AND numero=p_numero FOR UPDATE;
 IF NOT FOUND OR NOT anterior.activo THEN RAISE EXCEPTION 'Comprobante no disponible' USING ERRCODE='P0002'; END IF;
 IF anterior.version<>p_version THEN RAISE EXCEPTION 'Edición obsoleta' USING ERRCODE='40001'; END IF;
 UPDATE public.comprobante SET activo=false,version=version+1 WHERE serie=p_serie AND numero=p_numero;
END; $$;

CREATE OR REPLACE FUNCTION public.fn_comprobante_con_detalle() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM public.comprobante WHERE serie=NEW.serie AND numero=NEW.numero)
 AND NOT EXISTS(SELECT 1 FROM public.incluye WHERE serie=NEW.serie AND numero=NEW.numero) THEN
  RAISE EXCEPTION 'El comprobante requiere detalle' USING ERRCODE='23514';
 END IF; RETURN NULL;
END; $$;
CREATE OR REPLACE FUNCTION app.fn_detalle_integridad() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
 PERFORM 1 FROM public.comprobante WHERE serie=OLD.serie AND numero=OLD.numero FOR UPDATE;
 IF FOUND AND NOT EXISTS(SELECT 1 FROM public.incluye WHERE serie=OLD.serie AND numero=OLD.numero) THEN
  RAISE EXCEPTION 'No puede eliminarse la última línea de un comprobante' USING ERRCODE='23514';
 END IF;RETURN NULL;
END; $$;
DROP TRIGGER IF EXISTS tr_detalle_conserva_comprobante ON public.incluye;
CREATE CONSTRAINT TRIGGER tr_detalle_conserva_comprobante AFTER DELETE OR UPDATE ON public.incluye
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION app.fn_detalle_integridad();
CREATE OR REPLACE PROCEDURE app.sp_catalogo_listar(IN tabla text,IN busqueda text,IN incluir_bajas boolean,INOUT resultado refcursor)
LANGUAGE plpgsql AS $$
DECLARE campos text;
BEGIN
 campos:=CASE tabla WHEN 'serie_comprobante' THEN 'serie,tipo_comprobante'
 WHEN 'moneda' THEN 'codigo_moneda,nombre_moneda' WHEN 'producto_servicio' THEN 'codigo,descripcion,unidad_medida'
 WHEN 'documento_relacionado' THEN 'tipo_relacionado,numero_relacionado' ELSE NULL END;
 IF campos IS NULL THEN RAISE EXCEPTION 'Catálogo inválido' USING ERRCODE='22023'; END IF;
 OPEN resultado FOR EXECUTE format('SELECT %s,activo FROM public.%I WHERE (activo OR $1) AND concat_ws('' '',%s) ILIKE $2 ORDER BY 1,2 LIMIT 500',campos,tabla,campos)
 USING incluir_bajas,'%'||coalesce(busqueda,'')||'%';
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_catalogo_guardar(IN tabla text,IN accion text,IN datos jsonb)
LANGUAGE plpgsql AS $$
DECLARE campos text; clave text; filtro text; cambios text; afectados int;
BEGIN
 CASE tabla
 WHEN 'serie_comprobante' THEN campos:='serie,tipo_comprobante';clave:='serie';cambios:='tipo_comprobante=p.tipo_comprobante';
 WHEN 'moneda' THEN campos:='codigo_moneda,nombre_moneda';clave:='codigo_moneda';cambios:='nombre_moneda=p.nombre_moneda';
 WHEN 'producto_servicio' THEN campos:='codigo,descripcion,unidad_medida';clave:='codigo';cambios:='descripcion=p.descripcion,unidad_medida=p.unidad_medida';
 WHEN 'documento_relacionado' THEN campos:='tipo_relacionado,numero_relacionado';clave:='tipo_relacionado';cambios:='numero_relacionado=p.numero_relacionado';
 ELSE RAISE EXCEPTION 'Catálogo inválido' USING ERRCODE='22023'; END CASE;
 IF accion NOT IN ('crear','modificar','baja') THEN RAISE EXCEPTION 'Acción inválida' USING ERRCODE='22023'; END IF;
 IF accion='baja' THEN
  filtro:=CASE WHEN tabla='documento_relacionado' THEN 'tipo_relacionado=$1 AND numero_relacionado=$2' ELSE format('%I=$1',clave) END;
  EXECUTE format('UPDATE public.%I SET activo=false WHERE %s AND activo',tabla,filtro) USING datos->>clave,datos->>'numero_relacionado';
  GET DIAGNOSTICS afectados=ROW_COUNT;
 ELSIF accion='crear' THEN
  IF tabla='moneda' AND btrim(coalesce(datos->>'nombre_moneda',''))='' OR
     tabla='producto_servicio' AND btrim(coalesce(datos->>'unidad_medida',''))='' OR
     tabla='documento_relacionado' AND btrim(coalesce(datos->>'numero_relacionado',''))='' THEN
   RAISE EXCEPTION 'Campo obligatorio vacío' USING ERRCODE='23514'; END IF;
  EXECUTE format('INSERT INTO public.%I(%s) SELECT %s FROM jsonb_populate_record(NULL::public.%I,$1)',tabla,campos,campos,tabla) USING datos;
  afectados:=1;
 ELSIF tabla='documento_relacionado' THEN
  PERFORM 1 FROM public.documento_relacionado WHERE tipo_relacionado=datos->>'anterior_tipo_relacionado'
   AND numero_relacionado=datos->>'anterior_numero_relacionado' AND activo FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Documento no disponible' USING ERRCODE='P0002'; END IF;
  IF btrim(coalesce(datos->>'numero_relacionado',''))='' THEN RAISE EXCEPTION 'Referencia vacía' USING ERRCODE='23514'; END IF;
  IF (datos->>'tipo_relacionado',datos->>'numero_relacionado') IS DISTINCT FROM
     (datos->>'anterior_tipo_relacionado',datos->>'anterior_numero_relacionado') THEN
   INSERT INTO public.documento_relacionado(tipo_relacionado,numero_relacionado) VALUES(datos->>'tipo_relacionado',datos->>'numero_relacionado');
   UPDATE public.comprobante c SET version=version+1 WHERE EXISTS(SELECT 1 FROM public.referencia r WHERE r.serie=c.serie AND r.numero=c.numero AND r.tipo_relacionado=datos->>'anterior_tipo_relacionado' AND r.numero_relacionado=datos->>'anterior_numero_relacionado');
   UPDATE public.referencia SET tipo_relacionado=datos->>'tipo_relacionado',numero_relacionado=datos->>'numero_relacionado'
    WHERE tipo_relacionado=datos->>'anterior_tipo_relacionado' AND numero_relacionado=datos->>'anterior_numero_relacionado';
   UPDATE public.documento_relacionado SET activo=false WHERE tipo_relacionado=datos->>'anterior_tipo_relacionado'
    AND numero_relacionado=datos->>'anterior_numero_relacionado';
  END IF; afectados:=1;
 ELSE
  IF tabla='serie_comprobante' AND EXISTS(SELECT 1 FROM public.comprobante c JOIN public.serie_comprobante s USING(serie)
    WHERE c.serie=datos->>'serie' AND s.tipo_comprobante<>datos->>'tipo_comprobante') THEN
   RAISE EXCEPTION 'No se cambia el tipo de una serie utilizada' USING ERRCODE='23503'; END IF;
  IF tabla='moneda' AND btrim(coalesce(datos->>'nombre_moneda',''))='' OR
     tabla='producto_servicio' AND btrim(coalesce(datos->>'unidad_medida',''))='' THEN
   RAISE EXCEPTION 'Campo obligatorio vacío' USING ERRCODE='23514'; END IF;
  EXECUTE format('UPDATE public.%I t SET %s FROM jsonb_populate_record(NULL::public.%I,$1) p WHERE t.%I=p.%I AND t.activo',tabla,cambios,tabla,clave,clave) USING datos;
  GET DIAGNOSTICS afectados=ROW_COUNT;
 END IF;
 IF afectados=0 THEN RAISE EXCEPTION 'Registro no disponible' USING ERRCODE='P0002'; END IF;
END; $$;

CREATE OR REPLACE PROCEDURE app.sp_opciones(IN tabla text,IN p_serie text,IN p_numero int,INOUT resultado refcursor)
LANGUAGE plpgsql AS $$ BEGIN
 CASE tabla
 WHEN 'cliente' THEN OPEN resultado FOR SELECT tipo_documento,numero_documento,nombre_razon_social FROM public.cliente cl
 WHERE activo OR EXISTS(SELECT 1 FROM public.comprobante c WHERE c.serie=p_serie AND c.numero=p_numero
  AND (c.tipo_documento,c.numero_documento)=(cl.tipo_documento,cl.numero_documento)) ORDER BY nombre_razon_social,tipo_documento,numero_documento;
 WHEN 'producto_servicio' THEN OPEN resultado FOR SELECT codigo,descripcion,unidad_medida FROM public.producto_servicio p
 WHERE activo OR EXISTS(SELECT 1 FROM public.incluye i WHERE i.serie=p_serie AND i.numero=p_numero AND i.codigo=p.codigo) ORDER BY codigo;
 WHEN 'serie_comprobante' THEN OPEN resultado FOR SELECT serie,tipo_comprobante FROM public.serie_comprobante
 WHERE activo OR serie=p_serie ORDER BY serie;
 WHEN 'moneda' THEN OPEN resultado FOR SELECT codigo_moneda,nombre_moneda FROM public.moneda m
 WHERE activo OR EXISTS(SELECT 1 FROM public.comprobante c WHERE c.serie=p_serie AND c.numero=p_numero AND c.codigo_moneda=m.codigo_moneda) ORDER BY codigo_moneda;
 ELSE RAISE EXCEPTION 'Catálogo inválido' USING ERRCODE='22023'; END CASE;
END; $$;
COMMIT;
