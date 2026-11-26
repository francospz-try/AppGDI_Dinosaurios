#!/usr/bin/env bash
set -euo pipefail
psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -f /opt/appgdi/base.sql
# psql quotes the variable as a SQL string before executing the statement.
psql -X -v ON_ERROR_STOP=1 -v clave="$GDI_APP_PASSWORD" -U "$POSTGRES_USER" -d postgres <<'SQL'
ALTER ROLE gdi_app PASSWORD :'clave';
SQL
