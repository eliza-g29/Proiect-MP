#!/bin/sh
set -eu
umask 077
mkdir -p /backups /keys
if [ ! -s /keys/backup.agekey ]; then age-keygen -o /keys/backup.agekey; fi
recipient=$(age-keygen -y /keys/backup.agekey)
while :; do
  started=$(date +%s)
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  pg_dump --format=custom --no-owner --file="$work/database.dump"
  mkdir "$work/documents"
  cp -a /documents/. "$work/documents/"
  tar -C "$work" -czf "$work/archive.tar.gz" database.dump documents
  age -r "$recipient" -o "/backups/medflow-$stamp.tar.gz.age.partial" "$work/archive.tar.gz"
  mv "/backups/medflow-$stamp.tar.gz.age.partial" "/backups/medflow-$stamp.tar.gz.age"
  date +%s > /backups/last-success
  rm -rf "$work"
  trap - EXIT
  elapsed=$(( $(date +%s) - started ))
  delay=$((86400 - elapsed))
  if [ "$delay" -lt 60 ]; then delay=60; fi
  sleep "$delay" &
  wait $!
done
