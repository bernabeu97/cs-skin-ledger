#!/usr/bin/env bash
# SkinLedger 云端部署脚本（在服务器上执行）。
# 用法: sudo bash cloud-deploy.sh <release> <new.jar> <frontend.tar.gz>
# 流程: 备份 → 替换 jar/前端 → systemd 重启 + nginx reload → 健康检查 → 失败自动回滚。
set -euo pipefail

release="${1:?usage: cloud-deploy.sh <release> <jar> <web-tar.gz>}"
new_jar="${2:?missing jar}"
new_web="${3:?missing frontend tar.gz}"

app_root="/opt/cs-skin-ledger"
web_root="/var/www/cs-skin-ledger"
stage="/var/www/cs-skin-ledger.${release}.new"
old_web="/var/www/cs-skin-ledger.${release}.old"
backup="${app_root}/backups/${release}"

test -f "$new_jar"
test -f "$new_web"
test -f "${app_root}/app/app.jar"
test -d "$web_root"

# 幂等:清理上一次部署/回滚可能残留的暂存目录
if [ -e "$stage" ] || [ -e "$old_web" ]; then
  echo "==> 清理残留暂存目录"
  sudo rm -rf "$stage" "$old_web"
fi

echo "==> 备份当前版本到 ${backup}"
sudo install -d -m 0750 "$backup"
sudo cp -a "${app_root}/app/app.jar" "$backup/app.jar"
sudo tar -C /var/www -czf "$backup/frontend.tar.gz" cs-skin-ledger

echo "==> 解压前端到暂存目录"
sudo install -d -o nginx -g nginx -m 0755 "$stage"
sudo tar -xzf "$new_web" -C "$stage"
test -f "$stage/index.html"
sudo chown -R nginx:nginx "$stage"

echo "==> 替换后端 jar"
sudo install -o csledger -g csledger -m 0644 "$new_jar" "${app_root}/app/app.jar"

echo "==> 切换前端目录"
sudo mv "$web_root" "$old_web"
sudo mv "$stage" "$web_root"
sudo nginx -t

echo "==> 重启服务"
if ! sudo systemctl restart cs-skin-ledger; then
  echo "==> 服务启动失败，回滚…"
  sudo cp -a "$backup/app.jar" "${app_root}/app/app.jar"
  sudo mv "$web_root" "$stage"
  sudo mv "$old_web" "$web_root"
  sudo systemctl start cs-skin-ledger
  exit 1
fi

echo "==> 健康检查"
healthy=0
for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done

if [ "$healthy" -ne 1 ]; then
  echo "==> 健康检查失败，回滚…"
  sudo systemctl stop cs-skin-ledger || true
  sudo cp -a "$backup/app.jar" "${app_root}/app/app.jar"
  sudo mv "$web_root" "$stage"
  sudo mv "$old_web" "$web_root"
  sudo systemctl start cs-skin-ledger
  exit 1
fi

sudo systemctl reload nginx || true
test "$(sudo systemctl is-active cs-skin-ledger)" = "active"
echo "DEPLOY_OK=${release}"
