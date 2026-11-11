FROM postgres:16-alpine
RUN apk add --no-cache age
COPY ops/backup.sh ops/restore.sh /scripts/
RUN chmod 755 /scripts/*.sh
ENTRYPOINT ["/scripts/backup.sh"]
