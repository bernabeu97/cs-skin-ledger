# SkinLedger / CS 饰品账本

面向小团队私有实例的 CS 饰品统计账本。记录买入/卖出、持仓估值、已实现与浮动盈亏、其他收支、行情盯盘和价格提醒。前后端分离：Spring Boot 3 + Vue 3 + MySQL 8。

> 当前版本：`v0.3.0`。项目采用 AGPL-3.0；公网部署者需向网络用户提供对应版本源代码。详见 [LICENSE](LICENSE)。

## 环境要求

- JDK 21（本机 D:\Java\jdk-21）
- Maven（本机 D:\Java\apache-maven）
- MySQL 8.4（localhost:3306，库 cs_skin_ledger，账号 ledger）
- Node 22（npm 用 npm.cmd）

## 初始化数据库

用 root 密码执行（把 root 密码交互输入，不要写进命令行）：

```powershell
& 'D:\mysql\mysql-8.4.11-winx64\bin\mysql.exe' -uroot -p -e "CREATE DATABASE IF NOT EXISTS cs_skin_ledger DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'ledger'@'localhost' IDENTIFIED BY 'ledger_pass'; GRANT ALL PRIVILEGES ON cs_skin_ledger.* TO 'ledger'@'localhost'; FLUSH PRIVILEGES;"
```

## 启动后端

```powershell
$env:JAVA_HOME='D:\Java\jdk-21'
& 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml spring-boot:run
```

本地开发需要设置 `APP_ADMIN_USERNAME` 和至少 12 位的 `APP_ADMIN_PASSWORD`。首次启动创建管理员后，由管理员生成一次性邀请码；项目不开放自由注册。未登录访问业务 API 返回 401。

## 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问 http://localhost:5173 。

## 功能

- 饰品账本：新增/编辑/筛选、标准 Excel 导入、CSV/JSON/Excel 导出、30 天回收站
- 仪表盘：持仓总成本、已实现盈亏、持仓列表、月度盈亏图、总 ROI / 已实现 ROI / 胜率、单品盈亏 TOP10
- 账本效率：服务端分页（20/50/100）、跨页批量选择、批量补填买入价、批量导出、批量删除（撤销式）
- 账本分析：按单品/分类分组汇总（已实现盈亏、买入成本、卖出笔数、胜率）
- 行情盯盘：持仓指数、自选指数、UU 单品历史、自选清单与磨损级提醒
- 行情自动化：后端定时刷新（环境变量 `APP_PRICE_REFRESH_INTERVAL_MINUTES` 开启）、网页轮询提醒横幅与标签页闪烁（系统通知依赖 HTTPS）
- UU 同步：导入前「比对预览」（新增/重复/未匹配/忽略统计），确认后增量入库，幂等去重
- 卖出校验：卖出数量不能超过当前持仓（含手续费成本）
- 账号与安全：邀请制注册、PBKDF2 密码、TOTP 双重验证（可选）、恢复码、登录限流、CSRF、会话失效、审计日志
- 管理：成员禁用/启用、临时密码、一次性邀请码、最近安全事件
- 界面：CS 电竞风深浅主题（玻璃态卡片、荧光青/绿）、命令面板（Ctrl+K）、响应式桌面布局、移动端底部导航与表格固定首列/操作列

## 说明

- 当前市值、浮动盈亏、行情历史和价格提醒统一采用 UU 出售价。
- 数据在 MySQL；云端部署必须执行加密异地备份和恢复演练。
- 网页端价格提醒为轮询横幅 + 标签页标题闪烁；浏览器系统通知需要 HTTPS，当前公网 HTTP 阶段不可用（桌面端 Tauri 已有系统通知）。

## 饰品数据字典（CSGO-API）

- 数据来源：https://github.com/ByMykel/CSGO-API（中英文按 id 对齐导入，共 1.7 万条：武器皮肤/箱子/贴纸/钥匙/挂件/徽章/涂鸦/探员/音乐盒/收藏品）。
- 首次/更新导入（后端运行中执行，数据文件默认放在项目 work/csgoapi）：
  `Invoke-RestMethod -Uri 'http://localhost:8080/api/items/import?dir=work/csgoapi' -Method Post`
- 交易录入时饰品为字典下拉（支持中文关键词搜索），皮肤类可选磨损等级与磨损值（0-1）。

## 行情模块（UU 市场价 + 浮动盈亏）

### 数据源说明（重要）
- 主数据源 **CSQAQ 数据开放 API**（https://csqaq.com，免费注册）：一次批量请求最多 50 个 marketHashName；项目只读取并展示其返回的 **UU（悠悠有品）出售价**。
- 备用：Steam 社区市场直连（app.price.steam.enabled=true，本机网络可能超时）；UU 直连（app.price.youpin.enabled=true，
  需要抓包登录 token 写入 work/uu_token.txt，且 api.youpin898.com 有阿里云 WAF 风控，可能被拦截）。

### 首次配置 CSQAQ
1. 注册 https://csqaq.com → 个人中心复制 ApiToken。
2. 在个人中心绑定**本机出口 IP** 白名单。
3. 登录项目 → 设置 → CSQAQ 行情 Token，粘贴并绑定。Token 使用 AES-GCM 加密入库，页面和接口只返回末 4 位掩码。

### 导入平台商品 ID 映射（UU/BUFF 模板 ID）
数据来自 https://github.com/chinap/buff163-ids （work/cs2_marketplaceids.json，约 3.8 万条）：
```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/prices/import-market-ids?dir=work' -Method Post
```

### 使用
- 仪表盘或行情盯盘 → “刷新 UU 行情”：为持有批次和自选饰品抓取最新价并写入 price_snapshots。
- 仪表盘新增卡片：当前市值、浮动盈亏；持仓表新增 UU 价 / Steam 价 / 当前价 / 浮动盈亏列；当前市值与浮动盈亏统一只采用 UU 价。
- 接口：
  - POST /api/prices/refresh?platforms=uu
  - GET  /api/prices/valuation   （持仓估值）
  - GET  /api/prices/config      （数据源配置状态）
  - GET  /api/prices/history     （按饰品和磨损查询 24h/7d/30d/90d 历史）
  - GET  /api/prices/index       （持仓/自选组合指数）
  - GET/POST/DELETE /api/watchlist（最多 50 个“饰品 + 磨损”组合）
- 批次需填写“磨损等级”才能拼出完整市场名（如 AK-47 | Hydroponic (Field-Tested)）查询到价格。

### 已知限制（待处理）
- UU 直连受 WAF 风控，优先使用 CSQAQ。
- CSQAQ 的 UU/BUFF 价格为整数元粒度。
- CSQAQ ApiToken 绑定本机 IP，多人部署时各环境需各自配置。

## Docker 部署（多人访问版）

开发构建使用 `docker-compose.yml`；云服务器推荐使用 GHCR 镜像版 `docker-compose.prod.yml`。完整步骤见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

```powershell
cp .env.example .env
# 编辑 .env 并替换全部 CHANGE_ME
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
./scripts/health-check.sh

# 2. 访问 http://服务器IP/
```

- 后端 API 由 Nginx 反代到 8080，前端静态文件由 Nginx 托管（SPA 路由已配置）。
- 数据卷 `mysql_data` 持久化数据库；升级镜像后 Flyway 自动迁移。
- 注意：CSQAQ ApiToken 绑定注册时的 IP 白名单，部署环境需各自注册/绑定。
- 当前确认使用公网 HTTP，密码、Cookie、TOTP 和业务数据在传输途中没有 TLS 保护，不能视为完成公网安全验收；取得域名后必须切换 HTTPS。

### Git 方式部署（推荐）

打 `v0.3.0` 之类的 `v*` 标签后，GitHub Actions（`.github/workflows/deploy-cloud.yml`）会自动构建并通过 SSH 部署到云端（备份 → 替换 jar/前端 → 重启 + nginx reload → 健康检查 → 失败回滚）。首次配置：

1. 在仓库 Settings → Secrets and variables → Actions 添加：
   - `CLOUD_HOST`：服务器公网 IP
   - `CLOUD_USER`：SSH 用户（如 `admin`）
   - `CLOUD_SSH_KEY`：部署私钥内容（用 `ssh-keygen -t ed25519` 生成，公钥加入服务器 `~/.ssh/authorized_keys`）
2. 推送标签后自动部署；本地手动部署可执行 `scripts/cloud-deploy.sh <release> <jar> <frontend.tar.gz>`。

## UU（悠悠有品）库存/交易导入

通过浏览器已登录的 UU 会话抓取（脚本流程见 docs/PROBLEMS.md），数据经 `POST /api/sync/uu/import` 导入：

- 库存/Excel → HOLDING 批次（饰品/磨损/数量/买入价），卖出 → SOLD 批次（卖出价/手续费/时间）。
- 单价口径：buyPrice/sellPrice 为单件单价，quantity 表达数量；盈亏=数量×卖出价−手续费−数量×买入价。
- `lots.source_ref` 幂等去重（Excel 导入用 `xls:行号`），重复导入自动跳过。
- Excel 解析脚本：work/uu_import/parse_excel.py；原始抓取数据存档在 work/uu_import/。
- 网页首次点击“导入 UU JSON”会显示三步使用说明；导出插件源码：<https://github.com/bernabeu97/youpin898-record-exporter>。Chrome 需下载 ZIP 后在 `chrome://extensions` 开启开发者模式并“加载已解压的扩展程序”。
- 当前网页不能自动判断插件是否安装，因此保留固定的“安装/使用帮助”入口；首次成功导入后，主按钮会直接打开文件选择器。

## Windows 桌面盯盘

- 技术栈：Tauri 2 + Vue 3；支持 340×258 无边框桌面悬浮窗、顶部拖动、始终置顶、隐藏到托盘、持仓/自选指数、单品价格历史、磨损级提醒、离线缓存与 1/5/10/30 分钟刷新。
- 普通窗口顶部点击“悬浮”进入桌面监控模式；悬浮窗可刷新、展开完整盯盘、打开设置或隐藏到托盘，设置中可选择下次启动后直接进入悬浮模式。
- 网页与桌面“大盘”页接入 CSQAQ 官方分类指数：展示当前值、今日涨跌、开高低收，并支持 1小时/4小时/日/周 K 线缩放查看；后端接口为 `/api/prices/csqaq/indices` 与 `/api/prices/csqaq/index-kline`。
- 指数概览来自无需 Token 的 `current_data`；K 线来自 `sub/kline`，必须绑定当前服务器出口 IP。所有 CSQAQ 请求共用每秒一次的限流闸门，并对指数结果做短时缓存。
- 本地开发 API 默认 `http://localhost:8080`。公网 HTTP 仅允许手动登录且不保存密码；使用 Windows 凭据管理器记住密码需要 `localhost` 或 HTTPS。
- 本机不安装 Rust/Windows SDK。推送 `desktop-v*` 标签或在 GitHub Actions 手动运行 `build-desktop-msi`，可获得未签名 MSI 与对应 SHA256；首次安装可能出现 Windows SmartScreen 提示。

## 其他收支（会员费 / 赔偿 / 退款等）

- 页面：导航「其他收支」（/costs），6 类：会员费 / 平台服务费 / 赔偿支出 / 赔偿收入 / 退款 / 其他。
- 每条记录：分类、方向（支出/收入）、金额、时间、平台、可选关联饰品、备注；支持增删改、筛选、CSV/Excel/JSON 导出。
- 统计口径：其他收支净额 = 收入 − 支出；仪表盘「已实现盈亏」旁显示含其他收支，另有「其他收支净额」卡片。
- 接口：/api/costs（GET/POST/PUT/DELETE）、/api/costs/summary、/api/costs/export。
- 存量数据：驾驶手套深红织物预售赔偿 -700、UU 会员费 -999/-88、沙漠之鹰古铜密码撤回获赔 +325 已录入（来源行号 source_ref 幂等）。

## 设置与平台费率
- 导航「设置」（/settings）：配置 Steam/UU/BUFF 手续费率（默认 15% / 0.5% / 2.5%），保存后卖出表单按「出售价 × 费率」自动带出建议手续费（可手动修改）。
- 接口：GET/PUT /api/settings/fees（存于 settings 表，key=fees）。
- 同页可绑定/替换/解绑当前账号的 CSQAQ Token；密文存于 settings 表，不会向前端返回明文。
- 仪表盘首卡为「总盈亏 = 已实现盈亏 + 其他收支净额」；价格提醒在账本页 Tab 中管理。
