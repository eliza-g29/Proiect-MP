#!/bin/sh
set -eu
umask 077
if [ "$#" -ne 2 ] || [ "$2" != '--replace-existing-data' ]; then
  echo 'Usage: restore.sh medflow-TIMESTAMP.tar.gz.age --replace-existing-data' >&2
  exit 2
fi
case "$1" in medflow-*.tar.gz.age) ;; *) echo 'Invalid archive name' >&2; exit 2;; esac
case "$1" in */*|*..*) echo 'Invalid archive path' >&2; exit 2;; esac
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
age -d -i /keys/backup.agekey -o "$work/archive.tar.gz" "/backups/$1"
tar -C "$work" -xzf "$work/archive.tar.gz"
# Verify the archive before changing the destination.
pg_restore --list "$work/database.dump" >/dev/null
test -d "$work/documents"
# Copy immutable document files first. A failed copy leaves the current database untouched.
# Existing application files are never deleted; unreferenced extras can be reviewed separately.
cp -a "$work/documents/." /documents/
pg_restore --clean --if-exists --exit-on-error --single-transaction --no-owner --dbname="$PGDATABASE" "$work/database.dump"
echo 'Database and document snapshot restored. Run the application role provisioning service before restarting the backend.'
