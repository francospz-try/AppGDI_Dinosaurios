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
