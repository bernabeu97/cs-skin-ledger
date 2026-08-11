#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BACKUP_DIR="${BACKUP_DIR:-/opt/skinledger/backups}"
: "${BACKUP_ENCRYPTION_PASSWORD:?请设置 BACKUP_ENCRYPTION_PASSWORD}"
: "${OSS_BACKUP_URI:?请设置 OSS_BACKUP_URI，例如 oss://bucket/skinledger}"

command -v docker >/dev/null || { echo "缺少 docker" >&2; exit 1; }
command -v openssl >/dev/null || { echo "缺少 openssl" >&2; exit 1; }
command -v ossutil >/dev/null || { echo "缺少阿里云 ossutil" >&2; exit 1; }

mkdir -p -- "$BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
plain="$BACKUP_DIR/skinledger-$timestamp.sql.gz"
encrypted="$plain.enc"
trap 'rm -f -- "$plain"' EXIT

docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'exec mysqldump --single-transaction --quick --routines --triggers -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
  | gzip -9 > "$plain"

gzip -t "$plain"
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
  -in "$plain" -out "$encrypted" -pass env:BACKUP_ENCRYPTION_PASSWORD

# 上传后用服务端文件大小确认对象存在。推荐服务器使用 RAM 角色授权 OSS，避免长期 AccessKey。
ossutil cp "$encrypted" "${OSS_BACKUP_URI%/}/$(basename "$encrypted")" --force
ossutil stat "${OSS_BACKUP_URI%/}/$(basename "$encrypted")" >/dev/null

echo "备份成功：$encrypted"
