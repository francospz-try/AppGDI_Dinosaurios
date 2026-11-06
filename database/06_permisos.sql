\set ON_ERROR_STOP on
-- Ejecutar como administrador. Después asignar contraseña de forma interactiva:
--   \password gdi_app
-- No se incluye una contraseña predeterminada en el repositorio.
BEGIN;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='gdi_app') THEN
    CREATE ROLE gdi_app LOGIN;
  END IF;
END $$;
GRANT CONNECT ON DATABASE registro_comprobantes TO gdi_app;
GRANT USAGE ON SCHEMA public, app TO gdi_app;
GRANT SELECT ON public.cliente,public.comprobante,public.serie_comprobante,
  public.moneda,public.producto_servicio,public.incluye,public.referencia,
  public.documento_relacionado TO gdi_app;
GRANT INSERT,UPDATE ON public.cliente,public.comprobante,public.serie_comprobante,
 public.moneda,public.producto_servicio,public.incluye,public.referencia,public.documento_relacionado TO gdi_app;
GRANT DELETE ON public.incluye,public.referencia TO gdi_app;
GRANT SELECT ON app.v_comprobante,app.v_incluye,app.v_referencia TO gdi_app;
REVOKE ALL ON ALL PROCEDURES IN SCHEMA app FROM PUBLIC;
GRANT EXECUTE ON ALL PROCEDURES IN SCHEMA app TO gdi_app;
COMMIT;
