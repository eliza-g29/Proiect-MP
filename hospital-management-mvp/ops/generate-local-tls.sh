#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")"
mkdir -p tls
if [ -e tls/localhost.key ] || [ -e tls/localhost.crt ]; then echo 'Local TLS files already exist; no files replaced.'; exit 0; fi
openssl req -x509 -newkey rsa:3072 -sha256 -days 365 -nodes \
 -keyout tls/localhost.key -out tls/localhost.crt -subj '/CN=localhost' \
 -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
