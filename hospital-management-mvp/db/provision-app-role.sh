#!/bin/sh
set -eu
# psql quotes the environment password as a SQL literal; never interpolate it as SQL.
psql -v ON_ERROR_STOP=1 -v app_password="$APP_DATABASE_PASSWORD" <<'SQL'
SELECT 'CREATE ROLE hospital_app LOGIN' WHERE NOT EXISTS (
  SELECT FROM pg_roles WHERE rolname='hospital_app'
) \gexec
SELECT format('ALTER ROLE hospital_app PASSWORD %L', :'app_password') \gexec
GRANT CONNECT ON DATABASE hospital TO hospital_app;
GRANT USAGE ON SCHEMA public TO hospital_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hospital_app;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO hospital_app;
ALTER DEFAULT PRIVILEGES FOR ROLE hospital IN SCHEMA public
  GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO hospital_app;
ALTER DEFAULT PRIVILEGES FOR ROLE hospital IN SCHEMA public
  GRANT USAGE,SELECT ON SEQUENCES TO hospital_app;
SQL
psql -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'REVOKE ALL ON TABLE public.flyway_schema_history FROM hospital_app'
 WHERE to_regclass('public.flyway_schema_history') IS NOT NULL \gexec
SQL
