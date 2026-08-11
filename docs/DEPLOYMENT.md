# SkinLedger v0.2.0 云端部署与运维

## 1. 安全边界

当前方案按已确认决策开放公网 HTTP。账号密码、会话 Cookie、TOTP 验证码和业务数据在传输途中**不具备 TLS 加密保护**，因此不能把当前部署判定为“公网安全验收通过”。代码侧已提供邀请码、PBKDF2 密码哈希、管理员强制 TOTP、CSRF、防暴力登录、账号隔离和审计日志；获得域名后应优先接入 HTTPS，并把 `APP_SECURE_COOKIES` 改为 `true`。

服务器安全组只开放实际需要的端口：SSH 管理端口和 TCP 80。不要开放 3306、8080；MySQL 只接入 Docker 内部网络，后端不映射主机端口，仅通过前端反向代理访问。后端额外接入 `edge` 网络，用于访问 CSQAQ、Steam 等外部行情服务。

## 2. 环境依赖

- Linux x86_64 服务器，Docker Engine 与 Compose v2
- 现有 2 vCPU / 1 GiB 机器可用于小规模、低并发试运行
- 可选：阿里云 `ossutil`，用于加密异地备份
- 建议服务器目录：`/opt/skinledger`

## 3. 首次启动

```bash
sudo mkdir -p /opt/skinledger
sudo chown "$USER":"$USER" /opt/skinledger
cd /opt/skinledger

# 放入 docker-compose.prod.yml、.env.example 与 scripts/ 后：
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，所有 `CHANGE_ME` 必须替换为独立随机值。`APP_ENCRYPTION_KEY` 用于加密数据库中的行情 Token 和 TOTP 密钥，升级、迁移和恢复时必须保持不变。

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
curl --fail http://127.0.0.1/api/health
```

首次启动会按 `.env` 创建管理员。管理员第一次登录后必须绑定 TOTP，之后才能访问业务页；普通账号由管理员在「实例管理」生成一次性邀请码。

## 4. 发布与升级

推送 `v0.2.0` 之类的 Git 标签后，GitHub Actions 会先运行后端测试、前端类型检查/测试/构建，再向 GHCR 发布前后端镜像。

升级前先备份：

```bash
set -a
. ./.env
set +a
./scripts/backup-to-oss.sh

# 修改 .env 的 IMAGE_TAG 后：
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
./scripts/health-check.sh
```

Flyway 会在后端启动时执行数据库迁移。不要手工修改已执行的迁移文件。

## 5. 加密 OSS 备份

备份脚本先用一致性快照导出 MySQL，gzip 校验后使用 AES-256-CBC + PBKDF2（200000 次迭代）加密，再上传 OSS。加密密码不得与数据库、管理员或 `APP_ENCRYPTION_KEY` 共用。

```bash
chmod 700 scripts/*.sh
set -a
. ./.env
set +a
./scripts/backup-to-oss.sh
```

推荐给轻量应用服务器绑定仅允许指定 OSS 前缀读写的 RAM 角色；若只能使用 AccessKey，应放在 `ossutil` 的受限配置中，不写入仓库或命令历史。OSS 建议开启版本控制，并设置生命周期：保留至少 30 天，关键月末备份保留更久。

每月至少做一次恢复演练：

```bash
export BACKUP_ENCRYPTION_PASSWORD='从安全位置读取'
export CONFIRM_RESTORE=YES
./scripts/restore-from-backup.sh /opt/skinledger/backups/skinledger-YYYYMMDDTHHMMSSZ.sql.gz.enc
```

恢复会覆盖当前库，只能在明确选定的维护窗口或隔离演练实例中执行。

## 6. 1 GiB 机器监控与升级触发线

```bash
./scripts/health-check.sh
docker compose -f docker-compose.prod.yml logs --since=30m --tail=300
```

建议每 5 分钟运行健康检查并接入通知。满足任一条件就升级到至少 2 GiB：

- 任一容器出现 `OOMKilled=true`
- 连续 15 分钟总内存超过 85%，或 swap 持续增长
- 磁盘达到 80%
- 登录/列表接口连续 5 分钟 P95 超过 1 秒
- MySQL 连接接近 30 或出现排队/超时

## 7. 故障回滚

应用回滚只需把 `.env` 的 `IMAGE_TAG` 改回上一个已验证标签并重新 `up -d`。数据库迁移通常不能靠镜像回退撤销；若新迁移造成数据问题，应停止写入、保存现场并从升级前备份恢复。

## 8. HTTPS 后续切换

取得域名并解析到服务器后，在入口代理配置有效证书和 80→443 跳转，将 `APP_SECURE_COOKIES=true`，只开放 80/443，并重新验证登录、TOTP、导入、下载和桌面端连接。完成前，HTTP 风险始终保留。
