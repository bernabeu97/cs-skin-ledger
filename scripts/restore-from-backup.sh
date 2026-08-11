#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
: "${BACKUP_ENCRYPTION_PASSWORD:?请设置 BACKUP_ENCRYPTION_PASSWORD}"

backup_file="${1:-}"
if [[ -z "$backup_file" || ! -f "$backup_file" || "$backup_file" != *.sql.gz.enc ]]; then
  echo "用法：BACKUP_ENCRYPTION_PASSWORD=... $0 /path/skinledger-*.sql.gz.enc" >&2
  exit 2
fi
if [[ "${CONFIRM_RESTORE:-}" != "YES" ]]; then
  echo "恢复会覆盖当前数据库。确认后设置 CONFIRM_RESTORE=YES 再执行。" >&2
  exit 3
fi

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in "$backup_file" -pass env:BACKUP_ENCRYPTION_PASSWORD \
  | gzip -dc \
  | docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
      'exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'

echo "恢复完成，请执行：docker compose -f $COMPOSE_FILE restart backend frontend"
