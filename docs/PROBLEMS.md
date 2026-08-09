# 问题与待办清单（持续更新）

> 记录推进过程中遇到的阻塞、限制与待办，完成后逐条勾掉。

## 待用户提供 / 需外部操作
- [x] **CSQAQ ApiToken 已配置**（2026-08-09）：token 存于 git 忽略的 `work/csqaq_token.txt`，
      启动脚本 `scripts/start-backend.ps1` 自动读取并注入 `CSQAQ_TOKEN`；刷新行情已实测返回 UU/BUFF 价格。
- [x] **测试数据磨损已补**（2026-08-09）：`AK-47 | Hydroponic` 已填写“略有磨损”，行情可正常匹配。

## 平台接口限制（已用替代方案）
- [ ] **UU(youpin898) 直连被阿里云 WAF 拦截（403）**：api.youpin898.com 需要 App 登录 token 且风控严格，
      本机实测无 token 请求返回 403/Data=null。当前通过 CSQAQ 获取 UU 价格；
      `YoupinPriceProvider`（需 work/uu_token.txt）已实现但仅作备选。
- [ ] **Steam 直连超时**：steamcommunity.com/market/priceoverview 在本机网络返回超时(http 000)。
      当前通过 CSQAQ 的 `steamSellPrice` 获取 Steam 价；`SteamPriceProvider` 已实现但可能不可达。
- [ ] **CSQAQ 的 UU/BUFF 价格为整数元粒度**：如需分位精度，需额外接入平台直连（受风控限制）。

## 未实现功能（依赖登录态/后续规划）
- [ ] **Steam/BUFF/UU 交易记录自动同步（M4）**：需要各平台登录态/Cookie 且抓取有账号风控。
      设计已预留：trades.external_trade_id 唯一去重 + sync_logs 表。建议后续做本地同步器：
      用户在浏览器/本机完成登录抓包，把规范化交易数据通过 API 提交，服务器不存凭据。
- [ ] **平台费率配置**：Steam/BUFF/UU 手续费率目前为手填字段；
      后续做成 settings 页配置项，卖出表单自动带出建议手续费。
- [ ] **Excel 导入（M5）**：CSV/JSON 导入已支持，Excel 解析待做。

## 技术债 / 优化项
- [ ] **前端主 bundle 1.2MB**：Vite 构建提示 chunk 过大，可路由级代码分割。
- [ ] **行情刷新串行**：多个持有批次时按顺序请求（Steam 直连尤慢），可改并发受限池。
- [ ] **残留表清理**：`platform_item_links` / `settings` / `sync_logs` 为早期设计遗留（空表），
      `alerts` 已启用；确认无用后可删除并补 V5 迁移。
- [ ] **多人部署注意**：CSQAQ ApiToken 绑定 IP 白名单，每个部署环境需各自配置；
      docker-compose.yml 已提供 MySQL+后端+前端一键部署，生产务必修改默认密码。