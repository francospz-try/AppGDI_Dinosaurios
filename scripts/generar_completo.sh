#!/bin/sh
# Genera registro_comprobantes_completo.sql: une, en el orden de ejecución,
# los scripts que crean la base de datos desde cero (creación, datos,
# rutinas, disparadores, reportes, datos registrados con los
# procedimientos y permisos del rol del aplicativo). 03_consultas.sql no se incluye: consulta, no construye.
# Uso: sh scripts/generar_completo.sh
cd "$(dirname "$0")" || exit 1
SALIDA=registro_comprobantes_completo.sql
{
  cat <<'CABECERA'
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
CABECERA
  for f in 01_creacion.sql 02_insercion.sql 04_procedimientos.sql \
           05_procedimientos.sql 06_reportes.sql 07_datos_aplicativo.sql \
           08_permisos.sql; do
    printf '\n\n-- #####################################################################\n'
    printf -- '-- %s\n' "$f"
    printf -- '-- #####################################################################\n\n'
    cat "$f"
  done
} > "$SALIDA"
echo "$SALIDA: $(wc -l < "$SALIDA") líneas"
