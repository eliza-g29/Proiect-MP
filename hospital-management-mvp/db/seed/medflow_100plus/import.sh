#!/bin/sh
set -eu
export PGCONNECT_TIMEOUT=10
seed_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
echo '[1/3] Verific conexiunea și schema...'
psql -X -v ON_ERROR_STOP=1 -c "SELECT current_database() AS database, current_setting('server_version') AS postgres_version;"
echo '[2/3] Import date fictive într-o singură tranzacție, fără ștergeri...'
psql -X -v ON_ERROR_STOP=1 -f "$seed_dir/seed_medflow.sql"
psql -X -v ON_ERROR_STOP=1 -f "$seed_dir/refine_existing.sql"
psql -X -v ON_ERROR_STOP=1 -f "$seed_dir/longitudinal.sql"
echo '[3/3] Verific înregistrările și relațiile dintre spitale...'
psql -X -v ON_ERROR_STOP=1 -f "$seed_dir/verifica_medflow.sql"
echo 'Import finalizat. Reîncarcă aplicația pentru a vedea datele.'
