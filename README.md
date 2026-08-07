# CS 饰品买卖统计系统

记录 CS 饰品买入/卖出，计算已实现盈亏与持仓。前后端分离：Spring Boot 3 后端 + Vue 3 前端，数据库 MySQL 8。

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

验证：`Invoke-RestMethod http://localhost:8080/api/analytics/portfolio` 返回 JSON。

## 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问 http://localhost:5173 。

## 功能

- 交易记录：新增/编辑/删除/筛选，CSV 导入，CSV/JSON/Excel 导出
- 仪表盘：持仓总成本、已实现盈亏、持仓列表、月度盈亏图
- 卖出校验：卖出数量不能超过当前持仓（含手续费成本）

## 说明

- 浮动盈亏（当前市价）待行情采集模块（M2/M3）接入。
- 平台费率（Steam 约 15%、BUFF 约 2.5%）后续做成配置项。
- 数据在本机 MySQL，请定期导出 CSV/JSON 备份。

## 饰品数据字典（CSGO-API）

- 数据来源：https://github.com/ByMykel/CSGO-API（中英文按 id 对齐导入，共 1.7 万条：武器皮肤/箱子/贴纸/钥匙/挂件/徽章/涂鸦/探员/音乐盒/收藏品）。
- 首次/更新导入（后端运行中执行，数据文件默认放在项目 work/csgoapi）：
  `Invoke-RestMethod -Uri 'http://localhost:8080/api/items/import?dir=work/csgoapi' -Method Post`
- 交易录入时饰品为字典下拉（支持中文关键词搜索），皮肤类可选磨损等级与磨损值（0-1）。

## 行情模块（M2/M3：Steam/UU/BUFF 市场价 + 浮动盈亏）

### 数据源说明（重要）
- 主数据源 **CSQAQ 数据开放 API**（https://csqaq.com，免费注册）：一次批量请求最多 50 个 marketHashName，同时返回
  **UU(悠悠有品)/Steam/BUFF 三平台出售价**。这是当前获取 UU 市场价最可靠的途径。
- 备用：Steam 社区市场直连（app.price.steam.enabled=true，本机网络可能超时）；UU 直连（app.price.youpin.enabled=true，
  需要抓包登录 token 写入 work/uu_token.txt，且 api.youpin898.com 有阿里云 WAF 风控，可能被拦截）。

### 首次配置 CSQAQ
1. 注册 https://csqaq.com → 个人中心复制 ApiToken。
2. 在个人中心绑定**本机出口 IP** 白名单。
3. 设置环境变量后重启后端（或直接改 application.yml）：
   ```powershell
   $env:CSQAQ_TOKEN="你的Token"
   # 重启后端
   ```

### 导入平台商品 ID 映射（UU/BUFF 模板 ID）
数据来自 https://github.com/chinap/buff163-ids （work/cs2_marketplaceids.json，约 3.8 万条）：
```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/prices/import-market-ids?dir=work' -Method Post
```

### 使用
- 仪表盘 → “刷新行情”：为所有持有批次抓取最新价并写入 price_snapshots。
- 仪表盘新增卡片：当前市值、浮动盈亏；持仓表新增 UU 价 / Steam 价 / 当前价 / 浮动盈亏列（估值按 UU → Steam → BUFF 优先级取价）。
- 接口：
  - POST /api/prices/refresh?platforms=uu,steam,buff
  - GET  /api/prices/valuation   （持仓估值）
  - GET  /api/prices/config      （数据源配置状态）
- 批次需填写“磨损等级”才能拼出完整市场名（如 AK-47 | Hydroponic (Field-Tested)）查询到价格。

### 已知限制（待处理）
- UU 直连受 WAF 风控，优先使用 CSQAQ。
- Steam 直连在本机网络超时，优先使用 CSQAQ 的 steamSellPrice。
- CSQAQ 的 UU/BUFF 价格为整数元粒度。
- CSQAQ ApiToken 绑定本机 IP，多人部署时各环境需各自配置。