#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
APP_URL="${APP_URL:-http://127.0.0.1}"

echo "== SkinLedger API =="
curl --fail --silent --show-error --max-time 8 "$APP_URL/api/health"
echo
echo "== 容器资源 =="
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}'
echo "== 系统内存 =="
free -h
echo "== 磁盘 =="
df -h /

failed=0
while IFS= read -r container; do
  state="$(docker inspect --format '{{.State.Status}} {{.State.OOMKilled}}' "$container")"
  if [[ "$state" != "running false" ]]; then
    echo "异常容器：$container ($state)" >&2
    failed=1
  fi
done < <(docker compose -f "$COMPOSE_FILE" ps -q)

disk_used="$(df -P / | awk 'NR==2 {gsub(/%/, "", $5); print $5}')"
if (( disk_used >= 80 )); then
  echo "磁盘使用率已达到 ${disk_used}%（升级/清理触发线：80%）" >&2
  failed=1
fi
exit "$failed"
