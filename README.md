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