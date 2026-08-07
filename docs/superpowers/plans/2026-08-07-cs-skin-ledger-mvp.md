# CS 饰品买卖统计系统 MVP（账本核心）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 M0+M1：可本地运行的 Spring Boot 3 + MySQL 后端（交易 CRUD、盈亏引擎、统计、CSV/JSON/Excel 导入导出）与 Vue 3 前端（交易记录页、仪表盘）。

**Architecture:** 前后端分离。后端 Spring Boot 3 单体（REST API + Flyway + Spring Data JPA），数据库用本机 MySQL 8.4；前端 Vue 3 + Vite + TypeScript + Pinia + ECharts，开发期经 Vite 代理访问后端。

**Tech Stack:** Java 21（`D:\Java\jdk-21`）、Spring Boot 3.5.6、MySQL 8.4.11、Flyway、JUnit 5、MockMvc、H2（测试，MySQL 兼容模式）；Node 22、Vue 3、Vite、TypeScript、Pinia、ECharts、axios、vitest。

---

## 范围说明（重要）

- 本计划只实现设计文档的 **M0 + M1**：脚手架、交易录入/CRUD、盈亏引擎、统计 API、CSV/JSON/Excel 导入导出、基础页面。
- **未实现**（后续独立计划）：价格采集（M2）、UU/Steam 比价（M3）、Steam/BUFF/UU 交易同步（M4）、价格提醒（M5）、多用户（M6）。
- 未实现盈亏：M1 持仓页的"当前市价/浮动盈亏"列显示 `-`，等 M2/M3 接入行情后补齐。
- Excel 导出在本轮实现；Excel 导入与价格提醒放 M5 计划（POI 解析需要额外错误处理，且 CSV/JSON 已覆盖备份诉求）。

## 环境与执行前假设（已核实）

- 构建/运行后端时，每个 PowerShell 会话先执行：`$env:JAVA_HOME='D:\Java\jdk-21'`（系统 JAVA_HOME 指向 JDK 8，必须覆盖）。
- Maven：`D:\Java\apache-maven\bin\mvn.cmd`；依赖下载到 `D:\m2repo`（首次构建需要联网，Maven 自动下载，无需审批额外安装）。
- MySQL 8.4.11 运行于 `localhost:3306`，root 需要密码（执行 Task 0 时由用户提供；不得把密码写进代码/文档）。
- 前端命令一律用 `npm.cmd`（PowerShell 执行策略禁止 `npm.ps1`）。
- 所有构建产物（`backend/target`、`frontend/node_modules`、`dist`）只放项目目录内，不触碰系统目录。

## 目录结构（本计划将创建/修改的文件）

```
（项目根 = 本仓库根）
├── .gitignore                                  # 追加 target/ dist/ 等
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/cs/skinledger/
│       │   ├── SkinLedgerApplication.java
│       │   ├── domain/           User.java, Item.java, Trade.java,
│       │   │                     TradeDirection.java, TradeStatus.java
│       │   ├── repository/       UserRepository.java, ItemRepository.java,
│       │   │                     TradeRepository.java
│       │   ├── dto/              TradeCreateRequest.java, TradeResponse.java,
│       │   │                     TradeFilter.java, ImportResult.java,
│       │   │                     PnlRow.java, HoldingRow.java, PortfolioView.java,
│       │   │                     PnlGroupBy.java
│       │   ├── service/          PnlEngine.java, TradeService.java,
│       │   │                     ImportExportService.java
│       │   └── web/              TradeController.java, AnalyticsController.java,
│       │                         TradeNotFoundException.java, ApiExceptionHandler.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__init.sql
│   └── src/test/
│       ├── java/com/cs/skinledger/
│       │   ├── service/PnlEngineTest.java
│       │   └── web/TradeControllerTest.java
│       └── resources/application-test.yml
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router.ts
│       ├── types.ts
│       ├── api/client.ts
│       ├── utils/format.ts
│       ├── utils/format.test.ts
│       ├── stores/trades.ts
│       ├── views/TradesView.vue
│       ├── views/DashboardView.vue
│       └── components/TradeForm.vue, TradeTable.vue, PnlChart.vue
└── README.md                                    # 启动与使用说明
```

---

## Task 0: MySQL 初始化（需要用户提供 root 密码）

**Files:**
- 无（仅执行 SQL）

- [ ] **Step 1: 询问用户 MySQL root 密码并创建数据库与专用账号**

  由用户提供 root 密码后执行（`-p` 会交互式提示输入密码，不要写在命令行里）：

  ```powershell
  & 'D:\mysql\mysql-8.4.11-winx64\bin\mysql.exe' -uroot -p -e "CREATE DATABASE IF NOT EXISTS cs_skin_ledger DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'ledger'@'localhost' IDENTIFIED BY 'ledger_pass'; GRANT ALL PRIVILEGES ON cs_skin_ledger.* TO 'ledger'@'localhost'; FLUSH PRIVILEGES;"
  ```

  预期：无输出（成功）。若密码错误会输出 `ERROR 1045 (28000): Access denied`，需换密码重试。

- [ ] **Step 2: 验证专用账号可连接**

  ```powershell
  & 'D:\mysql\mysql-8.4.11-winx64\bin\mysql.exe' -uledger -pledger_pass cs_skin_ledger -e "SELECT 1;"
  ```

  预期：输出 `1`。若报 `Access denied`，检查上一步的账号/密码拼写。

> 提示：如果用户想用其他账号/密码，把本计划所有 `ledger` / `ledger_pass` 换成实际值，并通过环境变量 `DB_USER` / `DB_PASSWORD` 传给后端。

## Task 1: 后端 Maven 工程骨架（可编译、可连库）

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/cs/skinledger/SkinLedgerApplication.java`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Modify: `.gitignore`

- [ ] **Step 1: 创建 `backend/pom.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <project xmlns="http://maven.apache.org/POM/4.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-parent</artifactId>
      <version>3.5.6</version>
      <relativePath/>
    </parent>
    <groupId>com.cs</groupId>
    <artifactId>skin-ledger</artifactId>
    <version>0.1.0</version>
    <name>skin-ledger</name>
    <description>CS 饰品买卖统计系统后端</description>
    <properties>
      <java.version>21</java.version>
    </properties>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
      </dependency>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
      </dependency>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
      </dependency>
      <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
      </dependency>
      <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
      </dependency>
      <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
      </dependency>
      <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
      </dependency>
      <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.3.0</version>
      </dependency>
      <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-csv</artifactId>
        <version>1.12.0</version>
      </dependency>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
      </dependency>
    </dependencies>
    <build>
      <plugins>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <configuration>
            <excludes>
              <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
              </exclude>
            </excludes>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </project>
  ```

- [ ] **Step 2: 创建 `backend/src/main/resources/application.yml`**

  ```yaml
  spring:
    application:
      name: skin-ledger
    datasource:
      url: jdbc:mysql://localhost:3306/cs_skin_ledger?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
      username: ${DB_USER:ledger}
      password: ${DB_PASSWORD:ledger_pass}
    jpa:
      hibernate:
        ddl-auto: validate
      open-in-view: false
    flyway:
      enabled: true
      locations: classpath:db/migration
    jackson:
      serialization:
        write-dates-as-timestamps: false
  server:
    port: 8080
  ```

- [ ] **Step 3: 创建启动类 `SkinLedgerApplication.java`**

  ```java
  package com.cs.skinledger;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;

  @SpringBootApplication
  public class SkinLedgerApplication {
      public static void main(String[] args) {
          SpringApplication.run(SkinLedgerApplication.class, args);
      }
  }
  ```

- [ ] **Step 4: 创建 Flyway 初始迁移 `V1__init.sql`**

  ```sql
  -- CS 饰品买卖统计系统 初始表结构（MySQL 8，utf8mb4）
  CREATE TABLE users (
      id            BIGINT       NOT NULL AUTO_INCREMENT,
      username      VARCHAR(64)  NOT NULL,
      password_hash VARCHAR(255) NULL,
      created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      PRIMARY KEY (id),
      UNIQUE KEY uk_users_username (username)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE items (
      id               BIGINT       NOT NULL AUTO_INCREMENT,
      market_hash_name VARCHAR(255) NOT NULL,
      category         VARCHAR(64)  NULL,
      exterior         VARCHAR(16)  NULL,
      stat_trak        TINYINT(1)   NULL,
      icon_url         VARCHAR(512) NULL,
      source           VARCHAR(32)  NULL,
      PRIMARY KEY (id),
      UNIQUE KEY uk_items_market_hash_name (market_hash_name)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE platform_item_links (
      id               BIGINT       NOT NULL AUTO_INCREMENT,
      item_id          BIGINT       NOT NULL,
      platform         VARCHAR(16)  NOT NULL,
      platform_item_id VARCHAR(128) NOT NULL,
      extra            JSON         NULL,
      PRIMARY KEY (id),
      UNIQUE KEY uk_pil_platform_item (platform, platform_item_id),
      KEY idx_pil_item (item_id),
      CONSTRAINT fk_pil_item FOREIGN KEY (item_id) REFERENCES items (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE trades (
      id                BIGINT        NOT NULL AUTO_INCREMENT,
      user_id           BIGINT        NOT NULL,
      item_id           BIGINT        NOT NULL,
      platform          VARCHAR(16)   NOT NULL,
      direction         VARCHAR(8)    NOT NULL,
      quantity          DECIMAL(18,4) NOT NULL,
      unit_price        DECIMAL(18,4) NOT NULL,
      total_amount      DECIMAL(18,4) NOT NULL,
      fee               DECIMAL(18,4) NOT NULL DEFAULT 0,
      fee_rate          DECIMAL(10,6) NULL,
      currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
      traded_at         DATETIME(6)   NOT NULL,
      external_trade_id VARCHAR(128)  NULL,
      status            VARCHAR(16)   NOT NULL DEFAULT 'COMPLETED',
      note              VARCHAR(500)  NULL,
      created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      PRIMARY KEY (id),
      UNIQUE KEY uk_trades_external (platform, external_trade_id),
      KEY idx_trades_user_time (user_id, traded_at),
      KEY idx_trades_item (item_id),
      CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users (id),
      CONSTRAINT fk_trades_item FOREIGN KEY (item_id) REFERENCES items (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE price_snapshots (
      id         BIGINT        NOT NULL AUTO_INCREMENT,
      item_id    BIGINT        NOT NULL,
      platform   VARCHAR(16)   NOT NULL,
      price      DECIMAL(18,4) NOT NULL,
      buy_price  DECIMAL(18,4) NULL,
      sell_price DECIMAL(18,4) NULL,
      volume     INT           NULL,
      currency   VARCHAR(8)    NOT NULL DEFAULT 'CNY',
      fetched_at DATETIME(6)   NOT NULL,
      PRIMARY KEY (id),
      KEY idx_ps_item_platform_time (item_id, platform, fetched_at),
      CONSTRAINT fk_ps_item FOREIGN KEY (item_id) REFERENCES items (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE alerts (
      id           BIGINT        NOT NULL AUTO_INCREMENT,
      user_id      BIGINT        NOT NULL,
      item_id      BIGINT        NOT NULL,
      platform     VARCHAR(16)   NOT NULL,
      `condition`  VARCHAR(8)    NOT NULL,
      threshold    DECIMAL(18,4) NOT NULL,
      enabled      TINYINT(1)    NOT NULL DEFAULT 1,
      triggered_at DATETIME(6)   NULL,
      PRIMARY KEY (id),
      KEY idx_alerts_user (user_id),
      CONSTRAINT fk_alerts_user FOREIGN KEY (user_id) REFERENCES users (id),
      CONSTRAINT fk_alerts_item FOREIGN KEY (item_id) REFERENCES items (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE sync_logs (
      id            BIGINT        NOT NULL AUTO_INCREMENT,
      user_id       BIGINT        NOT NULL,
      platform      VARCHAR(16)   NOT NULL,
      kind          VARCHAR(16)   NOT NULL,
      started_at    DATETIME(6)   NOT NULL,
      finished_at   DATETIME(6)   NULL,
      status        VARCHAR(16)   NOT NULL,
      records_added INT           NOT NULL DEFAULT 0,
      message       VARCHAR(1000) NULL,
      PRIMARY KEY (id),
      KEY idx_sync_logs_user (user_id),
      CONSTRAINT fk_sync_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  CREATE TABLE settings (
      id      BIGINT      NOT NULL AUTO_INCREMENT,
      user_id BIGINT      NOT NULL,
      `key`   VARCHAR(64) NOT NULL,
      `value` JSON        NOT NULL,
      PRIMARY KEY (id),
      UNIQUE KEY uk_settings_user_key (user_id, `key`),
      CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  INSERT INTO users (id, username) VALUES (1, 'local');
  ```

- [ ] **Step 5: 更新根 `.gitignore`（追加）**

  ```gitignore
  target/
  dist/
  .idea/
  *.iml
  .vite/
  ```

- [ ] **Step 6: 首次编译（验证骨架）**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q compile
  ```

  预期：BUILD SUCCESS（首次会联网下载依赖到 D:\m2repo，耗时数分钟）。

- [ ] **Step 7: 启动后端并验证 Flyway 建表**

  新开一个 PowerShell 窗口（保持运行）：

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml spring-boot:run
  ```

  另开窗口验证：

  ```powershell
  & 'D:\mysql\mysql-8.4.11-winx64\bin\mysql.exe' -uledger -pledger_pass cs_skin_ledger -e "SHOW TABLES;"
  ```

  预期：出现 `users`、`items`、`trades`、`price_snapshots`、`alerts`、`sync_logs`、`settings`、`platform_item_links` 共 8 张表；后端日志显示 `Flyway ... Successfully applied 1 migration`。

- [ ] **Step 8: 提交**

  ```powershell
  git add .gitignore backend
  git commit -m "feat(backend): M0 脚手架（Spring Boot + Flyway + MySQL 迁移）"
  ```

## Task 2: 实体与 Repository

**Files:**
- Create: `backend/src/main/java/com/cs/skinledger/domain/TradeDirection.java`
- Create: `backend/src/main/java/com/cs/skinledger/domain/TradeStatus.java`
- Create: `backend/src/main/java/com/cs/skinledger/domain/User.java`
- Create: `backend/src/main/java/com/cs/skinledger/domain/Item.java`
- Create: `backend/src/main/java/com/cs/skinledger/domain/Trade.java`
- Create: `backend/src/main/java/com/cs/skinledger/repository/UserRepository.java`
- Create: `backend/src/main/java/com/cs/skinledger/repository/ItemRepository.java`
- Create: `backend/src/main/java/com/cs/skinledger/repository/TradeRepository.java`

- [ ] **Step 1: 创建枚举 `TradeDirection.java` / `TradeStatus.java`**

  ```java
  package com.cs.skinledger.domain;

  public enum TradeDirection {
      BUY, SELL
  }
  ```

  ```java
  package com.cs.skinledger.domain;

  public enum TradeStatus {
      COMPLETED, PENDING
  }
  ```

- [ ] **Step 2: 创建 `User.java`**

  ```java
  package com.cs.skinledger.domain;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.GeneratedValue;
  import jakarta.persistence.GenerationType;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import lombok.Getter;
  import lombok.NoArgsConstructor;
  import lombok.Setter;

  import java.time.LocalDateTime;

  @Entity
  @Table(name = "users")
  @Getter
  @Setter
  @NoArgsConstructor
  public class User {

      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @Column(nullable = false, unique = true, length = 64)
      private String username;

      @Column(name = "password_hash")
      private String passwordHash;

      @Column(name = "created_at", nullable = false)
      private LocalDateTime createdAt = LocalDateTime.now();
  }
  ```

- [ ] **Step 3: 创建 `Item.java`**

  ```java
  package com.cs.skinledger.domain;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.GeneratedValue;
  import jakarta.persistence.GenerationType;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import lombok.Getter;
  import lombok.NoArgsConstructor;
  import lombok.Setter;

  @Entity
  @Table(name = "items")
  @Getter
  @Setter
  @NoArgsConstructor
  public class Item {

      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @Column(name = "market_hash_name", nullable = false, unique = true, length = 255)
      private String marketHashName;

      @Column(length = 64)
      private String category;

      @Column(length = 16)
      private String exterior;

      @Column(name = "stat_trak")
      private Boolean statTrak;

      @Column(name = "icon_url", length = 512)
      private String iconUrl;

      @Column(length = 32)
      private String source;
  }
  ```

- [ ] **Step 4: 创建 `Trade.java`**

  ```java
  package com.cs.skinledger.domain;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.EnumType;
  import jakarta.persistence.Enumerated;
  import jakarta.persistence.FetchType;
  import jakarta.persistence.GeneratedValue;
  import jakarta.persistence.GenerationType;
  import jakarta.persistence.Id;
  import jakarta.persistence.JoinColumn;
  import jakarta.persistence.ManyToOne;
  import jakarta.persistence.Table;
  import lombok.Getter;
  import lombok.NoArgsConstructor;
  import lombok.Setter;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;

  @Entity
  @Table(name = "trades")
  @Getter
  @Setter
  @NoArgsConstructor
  public class Trade {

      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "user_id", nullable = false)
      private User user;

      @ManyToOne(fetch = FetchType.LAZY, optional = false)
      @JoinColumn(name = "item_id", nullable = false)
      private Item item;

      @Column(nullable = false, length = 16)
      private String platform;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false, length = 8)
      private TradeDirection direction;

      @Column(nullable = false, precision = 18, scale = 4)
      private BigDecimal quantity;

      @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
      private BigDecimal unitPrice;

      @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
      private BigDecimal totalAmount;

      @Column(nullable = false, precision = 18, scale = 4)
      private BigDecimal fee = BigDecimal.ZERO;

      @Column(name = "fee_rate", precision = 10, scale = 6)
      private BigDecimal feeRate;

      @Column(nullable = false, length = 8)
      private String currency = "CNY";

      @Column(name = "traded_at", nullable = false)
      private LocalDateTime tradedAt;

      @Column(name = "external_trade_id", length = 128)
      private String externalTradeId;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false, length = 16)
      private TradeStatus status = TradeStatus.COMPLETED;

      @Column(length = 500)
      private String note;

      @Column(name = "created_at", nullable = false)
      private LocalDateTime createdAt = LocalDateTime.now();
  }
  ```

- [ ] **Step 5: 创建三个 Repository**

  ```java
  package com.cs.skinledger.repository;

  import com.cs.skinledger.domain.User;
  import org.springframework.data.jpa.repository.JpaRepository;

  import java.util.Optional;

  public interface UserRepository extends JpaRepository<User, Long> {
      Optional<User> findByUsername(String username);
  }
  ```

  ```java
  package com.cs.skinledger.repository;

  import com.cs.skinledger.domain.Item;
  import org.springframework.data.jpa.repository.JpaRepository;

  import java.util.Optional;

  public interface ItemRepository extends JpaRepository<Item, Long> {
      Optional<Item> findByMarketHashName(String marketHashName);
  }
  ```

  ```java
  package com.cs.skinledger.repository;

  import com.cs.skinledger.domain.Trade;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

  import java.util.List;

  public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {
      List<Trade> findByUserIdOrderByTradedAtAsc(Long userId);

      List<Trade> findByUserIdOrderByTradedAtDesc(Long userId);
  }
  ```

- [ ] **Step 6: 编译验证**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q compile
  ```

  预期：BUILD SUCCESS。

- [ ] **Step 7: 提交**

  ```powershell
  git add backend
  git commit -m "feat(backend): 实体与 Repository（users/items/trades）"
  ```

## Task 3: 盈亏计算引擎（TDD）

**Files:**
- Test: `backend/src/test/java/com/cs/skinledger/service/PnlEngineTest.java`
- Create: `backend/src/main/java/com/cs/skinledger/service/PnlEngine.java`

- [ ] **Step 1: 写失败的测试 `PnlEngineTest.java`**

  ```java
  package com.cs.skinledger.service;

  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.service.PnlEngine.Position;
  import com.cs.skinledger.service.PnlEngine.TradeInput;
  import org.junit.jupiter.api.Test;

  import java.math.BigDecimal;
  import java.util.List;

  import static org.junit.jupiter.api.Assertions.assertEquals;
  import static org.junit.jupiter.api.Assertions.assertThrows;

  class PnlEngineTest {

      private static TradeInput buy(String qty, String price, String fee) {
          return new TradeInput(TradeDirection.BUY, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee));
      }

      private static TradeInput sell(String qty, String price, String fee) {
          return new TradeInput(TradeDirection.SELL, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee));
      }

      @Test
      void buyThenFullSellWithFee() {
          Position pos = PnlEngine.replay(List.of(
                  buy("2", "100", "10"),
                  sell("1", "120", "3")
          ));
          assertEquals(0, new BigDecimal("1").compareTo(pos.remainingQty()));
          assertEquals(0, new BigDecimal("105").compareTo(pos.avgCost()));
          assertEquals(0, new BigDecimal("12").compareTo(pos.realizedPnl()));
      }

      @Test
      void fullLiquidationRealizesTotalProfit() {
          Position pos = PnlEngine.replay(List.of(
                  buy("1", "100", "0"),
                  buy("1", "100", "0"),
                  sell("1", "150", "0"),
                  sell("1", "150", "0")
          ));
          assertEquals(0, BigDecimal.ZERO.compareTo(pos.remainingQty()));
          assertEquals(0, BigDecimal.ZERO.compareTo(pos.remainingCost()));
          assertEquals(0, new BigDecimal("100").compareTo(pos.realizedPnl()));
      }

      @Test
      void movingAverageAcrossTwoBuys() {
          Position pos = PnlEngine.replay(List.of(
                  buy("1", "100", "0"),
                  buy("1", "200", "0")
          ));
          assertEquals(0, new BigDecimal("150").compareTo(pos.avgCost()));
          Position afterSell = PnlEngine.apply(pos, sell("1", "180", "0"));
          assertEquals(0, new BigDecimal("30").compareTo(afterSell.realizedPnl()));
          assertEquals(0, new BigDecimal("150").compareTo(afterSell.remainingCost()));
      }

      @Test
      void buyWithFeeAddsToCostBasis() {
          Position pos = PnlEngine.replay(List.of(buy("1", "100", "5")));
          assertEquals(0, new BigDecimal("105").compareTo(pos.remainingCost()));
          assertEquals(0, new BigDecimal("105").compareTo(pos.avgCost()));
      }

      @Test
      void sellMoreThanHeldThrows() {
          assertThrows(IllegalArgumentException.class, () ->
                  PnlEngine.replay(List.of(buy("1", "100", "0"), sell("2", "100", "0"))));
      }

      @Test
      void sellFromEmptyPositionThrows() {
          assertThrows(IllegalArgumentException.class, () ->
                  PnlEngine.apply(Position.empty(), sell("1", "100", "0")));
      }

      @Test
      void negativeQuantityThrows() {
          assertThrows(IllegalArgumentException.class, () ->
                  PnlEngine.apply(Position.empty(), buy("-1", "100", "0")));
      }
  }
  ```

- [ ] **Step 2: 运行测试，确认失败**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q -Dtest=PnlEngineTest test
  ```

  预期：FAIL（编译失败：`PnlEngine` 不存在），即测试先红。

- [ ] **Step 3: 实现 `PnlEngine.java`**

  ```java
  package com.cs.skinledger.service;

  import com.cs.skinledger.domain.TradeDirection;

  import java.math.BigDecimal;
  import java.math.RoundingMode;
  import java.util.List;

  /**
   * 盈亏引擎：移动平均成本法。
   * 买入：成本 += 数量*单价 + 手续费；卖出：已实现盈亏 += 净卖出额 - 数量*平均成本。
   */
  public final class PnlEngine {

      public static final int SCALE = 4;
      private static final int AVG_SCALE = 8;

      private PnlEngine() {
      }

      public record Position(BigDecimal remainingQty, BigDecimal remainingCost,
                             BigDecimal realizedPnl, BigDecimal avgCost) {

          public static Position empty() {
              return new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
          }
      }

      public record TradeInput(TradeDirection direction, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal fee) {
      }

      public static Position apply(Position pos, TradeInput trade) {
          BigDecimal qty = trade.quantity();
          BigDecimal price = trade.unitPrice();
          BigDecimal fee = trade.fee() == null ? BigDecimal.ZERO : trade.fee();
          if (qty.signum() <= 0 || price.signum() < 0 || fee.signum() < 0) {
              throw new IllegalArgumentException("数量必须大于 0，价格和手续费不能为负");
          }
          if (trade.direction() == TradeDirection.BUY) {
              BigDecimal cost = qty.multiply(price).add(fee);
              BigDecimal newQty = pos.remainingQty().add(qty);
              BigDecimal newCost = pos.remainingCost().add(cost);
              return new Position(newQty, newCost, pos.realizedPnl(), avgCost(newQty, newCost));
          }
          if (qty.compareTo(pos.remainingQty()) > 0) {
              throw new IllegalArgumentException("卖出数量超过当前持仓");
          }
          BigDecimal avg = avgCost(pos.remainingQty(), pos.remainingCost());
          BigDecimal sellNet = qty.multiply(price).subtract(fee);
          BigDecimal realized = pos.realizedPnl().add(sellNet.subtract(qty.multiply(avg)));
          BigDecimal newQty = pos.remainingQty().subtract(qty);
          BigDecimal newCost = pos.remainingCost().subtract(qty.multiply(avg));
          if (newQty.signum() == 0) {
              newCost = BigDecimal.ZERO;
          }
          return new Position(newQty, newCost, realized, avgCost(newQty, newCost));
      }

      public static Position replay(List<TradeInput> trades) {
          Position pos = Position.empty();
          for (TradeInput trade : trades) {
              pos = apply(pos, trade);
          }
          return pos;
      }

      private static BigDecimal avgCost(BigDecimal qty, BigDecimal cost) {
          if (qty.signum() == 0) {
              return BigDecimal.ZERO;
          }
          return cost.divide(qty, AVG_SCALE, RoundingMode.HALF_UP);
      }
  }
  ```

- [ ] **Step 4: 运行测试，确认通过**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q -Dtest=PnlEngineTest test
  ```

  预期：`Tests run: 7, Failures: 0`，BUILD SUCCESS。

- [ ] **Step 5: 提交**

  ```powershell
  git add backend
  git commit -m "feat(backend): 盈亏计算引擎（移动平均成本法，TDD）"
  ```

## Task 4: 交易 CRUD + CSV/JSON/Excel 导入导出 API（TDD）

**Files:**
- Create: `backend/src/main/java/com/cs/skinledger/dto/TradeCreateRequest.java`
- Create: `backend/src/main/java/com/cs/skinledger/dto/TradeResponse.java`
- Create: `backend/src/main/java/com/cs/skinledger/dto/TradeFilter.java`
- Create: `backend/src/main/java/com/cs/skinledger/dto/ImportResult.java`
- Create: `backend/src/main/java/com/cs/skinledger/service/TradeService.java`
- Create: `backend/src/main/java/com/cs/skinledger/service/ImportExportService.java`
- Create: `backend/src/main/java/com/cs/skinledger/web/TradeNotFoundException.java`
- Create: `backend/src/main/java/com/cs/skinledger/web/ApiExceptionHandler.java`
- Create: `backend/src/main/java/com/cs/skinledger/web/TradeController.java`
- Create: `backend/src/test/resources/application-test.yml`
- Test: `backend/src/test/java/com/cs/skinledger/web/TradeControllerTest.java`

- [ ] **Step 1: 创建 DTO（四个文件）**

  ```java
  package com.cs.skinledger.dto;

  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.domain.TradeStatus;
  import com.fasterxml.jackson.annotation.JsonFormat;
  import jakarta.validation.constraints.DecimalMin;
  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.NotNull;
  import jakarta.validation.constraints.Pattern;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;

  public record TradeCreateRequest(
          @NotBlank String itemName,
          @NotBlank @Pattern(regexp = "steam|uu|buff", message = "platform 仅支持 steam/uu/buff") String platform,
          @NotNull TradeDirection direction,
          @NotNull @DecimalMin(value = "0.0001", message = "数量必须大于 0") BigDecimal quantity,
          @NotNull @DecimalMin(value = "0", message = "单价不能为负") BigDecimal unitPrice,
          @DecimalMin(value = "0", message = "手续费不能为负") BigDecimal fee,
          @DecimalMin(value = "0", message = "费率不能为负") BigDecimal feeRate,
          String currency,
          @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime tradedAt,
          String externalTradeId,
          TradeStatus status,
          String note) {
  }
  ```

  ```java
  package com.cs.skinledger.dto;

  import com.cs.skinledger.domain.Trade;
  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.domain.TradeStatus;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;

  public record TradeResponse(
          Long id,
          String itemName,
          String platform,
          TradeDirection direction,
          BigDecimal quantity,
          BigDecimal unitPrice,
          BigDecimal totalAmount,
          BigDecimal fee,
          BigDecimal feeRate,
          String currency,
          LocalDateTime tradedAt,
          String externalTradeId,
          TradeStatus status,
          String note) {

      public static TradeResponse from(Trade t) {
          return new TradeResponse(
                  t.getId(),
                  t.getItem().getMarketHashName(),
                  t.getPlatform(),
                  t.getDirection(),
                  t.getQuantity(),
                  t.getUnitPrice(),
                  t.getTotalAmount(),
                  t.getFee(),
                  t.getFeeRate(),
                  t.getCurrency(),
                  t.getTradedAt(),
                  t.getExternalTradeId(),
                  t.getStatus(),
                  t.getNote());
      }
  }
  ```

  ```java
  package com.cs.skinledger.dto;

  import com.cs.skinledger.domain.TradeDirection;

  import java.time.LocalDateTime;

  public record TradeFilter(
          String platform,
          TradeDirection direction,
          LocalDateTime from,
          LocalDateTime to,
          String q,
          String category) {
  }
  ```

  ```java
  package com.cs.skinledger.dto;

  import java.util.List;

  public record ImportResult(int created, int failed, List<String> errors) {

      public ImportResult withCreated(int created) {
          return new ImportResult(created, failed, errors);
      }

      public ImportResult withFailed(int failed) {
          return new ImportResult(created, failed, errors);
      }
  }
  ```

- [ ] **Step 2: 创建 `TradeService.java`**

  ```java
  package com.cs.skinledger.service;

  import com.cs.skinledger.domain.Item;
  import com.cs.skinledger.domain.Trade;
  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.domain.TradeStatus;
  import com.cs.skinledger.domain.User;
  import com.cs.skinledger.dto.HoldingRow;
  import com.cs.skinledger.dto.PnlGroupBy;
  import com.cs.skinledger.dto.PnlRow;
  import com.cs.skinledger.dto.PortfolioView;
  import com.cs.skinledger.dto.TradeCreateRequest;
  import com.cs.skinledger.dto.TradeFilter;
  import com.cs.skinledger.dto.TradeResponse;
  import com.cs.skinledger.repository.ItemRepository;
  import com.cs.skinledger.repository.TradeRepository;
  import com.cs.skinledger.repository.UserRepository;
  import com.cs.skinledger.service.PnlEngine.Position;
  import com.cs.skinledger.service.PnlEngine.TradeInput;
  import com.cs.skinledger.web.TradeNotFoundException;
  import jakarta.persistence.criteria.Predicate;
  import lombok.RequiredArgsConstructor;
  import org.springframework.data.domain.Sort;
  import org.springframework.data.jpa.domain.Specification;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.math.BigDecimal;
  import java.time.YearMonth;
  import java.time.YearWeek;
  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.LinkedHashMap;
  import java.util.List;
  import java.util.Map;
  import java.util.Set;

  @Service
  @RequiredArgsConstructor
  public class TradeService {

      private static final long LOCAL_USER_ID = 1L;
      private static final Set<String> SUPPORTED_PLATFORMS = Set.of("steam", "uu", "buff");

      private final TradeRepository tradeRepository;
      private final UserRepository userRepository;
      private final ItemRepository itemRepository;

      @Transactional
      public TradeResponse create(TradeCreateRequest req) {
          validate(req);
          validateSellHolding(req, null);
          User user = localUser();
          Item item = findOrCreateItem(req.itemName());
          Trade trade = new Trade();
          applyFields(trade, req, item, user);
          return TradeResponse.from(tradeRepository.save(trade));
      }

      @Transactional(readOnly = true)
      public List<TradeResponse> list(TradeFilter filter) {
          Specification<Trade> spec = filter == null
                  ? (root, query, cb) -> cb.equal(root.get("user").get("id"), LOCAL_USER_ID)
                  : buildSpec(filter);
          return tradeRepository.findAll(spec, Sort.by("tradedAt").descending()).stream()
                  .map(TradeResponse::from)
                  .toList();
      }

      @Transactional
      public TradeResponse update(Long id, TradeCreateRequest req) {
          Trade trade = tradeRepository.findById(id).orElseThrow(() -> new TradeNotFoundException(id));
          validate(req);
          validateSellHolding(req, trade.getId());
          Item item = findOrCreateItem(req.itemName());
          applyFields(trade, req, item, trade.getUser());
          return TradeResponse.from(tradeRepository.save(trade));
      }

      @Transactional
      public void delete(Long id) {
          Trade trade = tradeRepository.findById(id).orElseThrow(() -> new TradeNotFoundException(id));
          tradeRepository.delete(trade);
      }

      @Transactional(readOnly = true)
      public PortfolioView portfolio() {
          List<Trade> trades = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID);
          Map<Long, Position> positions = new LinkedHashMap<>();
          Map<Long, Item> items = new LinkedHashMap<>();
          for (Trade t : trades) {
              if (t.getStatus() != TradeStatus.COMPLETED) {
                  continue;
              }
              Position prev = positions.getOrDefault(t.getItem().getId(), Position.empty());
              positions.put(t.getItem().getId(), PnlEngine.apply(prev, toInput(t)));
              items.put(t.getItem().getId(), t.getItem());
          }
          BigDecimal totalCost = BigDecimal.ZERO;
          BigDecimal totalRealized = BigDecimal.ZERO;
          List<HoldingRow> holdings = new ArrayList<>();
          for (Map.Entry<Long, Position> e : positions.entrySet()) {
              Item item = items.get(e.getKey());
              Position p = e.getValue();
              totalCost = totalCost.add(p.remainingCost());
              totalRealized = totalRealized.add(p.realizedPnl());
              holdings.add(new HoldingRow(item.getMarketHashName(), p.remainingQty(), p.avgCost(), p.realizedPnl(), null));
          }
          return new PortfolioView(totalCost, totalRealized, holdings);
      }

      @Transactional(readOnly = true)
      public List<PnlRow> realizedPnl(PnlGroupBy groupBy) {
          List<Trade> trades = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID);
          Map<Long, Position> positions = new HashMap<>();
          Map<String, BigDecimal> sums = new LinkedHashMap<>();
          Map<String, Integer> counts = new LinkedHashMap<>();
          for (Trade t : trades) {
              if (t.getStatus() != TradeStatus.COMPLETED) {
                  continue;
              }
              Position prev = positions.getOrDefault(t.getItem().getId(), Position.empty());
              Position next = PnlEngine.apply(prev, toInput(t));
              positions.put(t.getItem().getId(), next);
              if (t.getDirection() == TradeDirection.SELL) {
                  BigDecimal delta = next.realizedPnl().subtract(prev.realizedPnl());
                  String key = groupKey(t, groupBy);
                  sums.merge(key, delta, BigDecimal::add);
                  counts.merge(key, 1, Integer::sum);
              }
          }
          return sums.entrySet().stream()
                  .map(e -> new PnlRow(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0)))
                  .toList();
      }

      private String groupKey(Trade t, PnlGroupBy groupBy) {
          return switch (groupBy) {
              case day -> t.getTradedAt().toLocalDate().toString();
              case week -> YearWeek.from(t.getTradedAt()).toString();
              case month -> YearMonth.from(t.getTradedAt()).toString();
              case year -> String.valueOf(t.getTradedAt().getYear());
              case platform -> t.getPlatform();
              case category -> {
                  String c = t.getItem().getCategory();
                  yield (c == null || c.isBlank()) ? "未分类" : c;
              }
              case item -> t.getItem().getMarketHashName();
          };
      }

      private Specification<Trade> buildSpec(TradeFilter f) {
          return (root, query, cb) -> {
              List<Predicate> ps = new ArrayList<>();
              ps.add(cb.equal(root.get("user").get("id"), LOCAL_USER_ID));
              if (f.platform() != null && !f.platform().isBlank()) {
                  ps.add(cb.equal(root.get("platform"), f.platform()));
              }
              if (f.direction() != null) {
                  ps.add(cb.equal(root.get("direction"), f.direction()));
              }
              if (f.from() != null) {
                  ps.add(cb.greaterThanOrEqualTo(root.get("tradedAt"), f.from()));
              }
              if (f.to() != null) {
                  ps.add(cb.lessThanOrEqualTo(root.get("tradedAt"), f.to()));
              }
              if (f.q() != null && !f.q().isBlank()) {
                  ps.add(cb.like(root.get("item").get("marketHashName"), "%" + f.q() + "%"));
              }
              if (f.category() != null && !f.category().isBlank()) {
                  ps.add(cb.equal(root.get("item").get("category"), f.category()));
              }
              return cb.and(ps.toArray(new Predicate[0]));
          };
      }

      private void validate(TradeCreateRequest req) {
          if (!SUPPORTED_PLATFORMS.contains(req.platform())) {
              throw new IllegalArgumentException("platform 仅支持 steam/uu/buff");
          }
      }

      private void validateSellHolding(TradeCreateRequest req, Long excludeId) {
          if (req.direction() != TradeDirection.SELL) {
              return;
          }
          Item item = itemRepository.findByMarketHashName(req.itemName()).orElse(null);
          if (item == null) {
              throw new IllegalArgumentException("卖出数量超过当前持仓：该饰品尚无任何持仓");
          }
          List<TradeInput> inputs = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID).stream()
                  .filter(t -> t.getStatus() == TradeStatus.COMPLETED)
                  .filter(t -> t.getItem().getId().equals(item.getId()))
                  .filter(t -> excludeId == null || !t.getId().equals(excludeId))
                  .map(this::toInput)
                  .toList();
          Position pos = PnlEngine.replay(inputs);
          if (req.quantity().compareTo(pos.remainingQty()) > 0) {
              throw new IllegalArgumentException("卖出数量超过当前持仓（当前可卖 " + pos.remainingQty() + "）");
          }
      }

      private TradeInput toInput(Trade t) {
          return new TradeInput(t.getDirection(), t.getQuantity(), t.getUnitPrice(), t.getFee());
      }

      private void applyFields(Trade trade, TradeCreateRequest req, Item item, User user) {
          trade.setUser(user);
          trade.setItem(item);
          trade.setPlatform(req.platform());
          trade.setDirection(req.direction());
          trade.setQuantity(req.quantity());
          trade.setUnitPrice(req.unitPrice());
          trade.setTotalAmount(req.quantity().multiply(req.unitPrice()));
          trade.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
          trade.setFeeRate(req.feeRate());
          trade.setCurrency(req.currency() == null || req.currency().isBlank() ? "CNY" : req.currency());
          trade.setTradedAt(req.tradedAt());
          trade.setExternalTradeId(req.externalTradeId());
          trade.setStatus(req.status() == null ? TradeStatus.COMPLETED : req.status());
          trade.setNote(req.note());
      }

      private Item findOrCreateItem(String marketHashName) {
          return itemRepository.findByMarketHashName(marketHashName)
                  .orElseGet(() -> {
                      Item item = new Item();
                      item.setMarketHashName(marketHashName);
                      item.setSource("manual");
                      return itemRepository.save(item);
                  });
      }

      private User localUser() {
          return userRepository.findByUsername("local")
                  .orElseGet(() -> {
                      User user = new User();
                      user.setUsername("local");
                      return userRepository.save(user);
                  });
      }
  }
  ```

  > 依赖的 `HoldingRow`、`PnlGroupBy`、`PnlRow`、`PortfolioView` 在 Task 5 创建；本步编译会暂时报找不到这四个类型，属于预期（Task 5 补齐后恢复）。如需本步可编译，可先创建空的 record，Task 5 再填充。

- [ ] **Step 3: 创建 `ImportExportService.java`**

  ```java
  package com.cs.skinledger.service;

  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.domain.TradeStatus;
  import com.cs.skinledger.dto.ImportResult;
  import com.cs.skinledger.dto.TradeCreateRequest;
  import com.cs.skinledger.dto.TradeResponse;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import org.apache.commons.csv.CSVFormat;
  import org.apache.commons.csv.CSVParser;
  import org.apache.commons.csv.CSVPrinter;
  import org.apache.commons.csv.CSVRecord;
  import org.apache.poi.ss.usermodel.Row;
  import org.apache.poi.ss.usermodel.Sheet;
  import org.apache.poi.xssf.usermodel.XSSFWorkbook;
  import org.springframework.stereotype.Service;
  import org.springframework.web.multipart.MultipartFile;

  import java.io.ByteArrayOutputStream;
  import java.io.IOException;
  import java.io.InputStreamReader;
  import java.io.StringWriter;
  import java.math.BigDecimal;
  import java.nio.charset.StandardCharsets;
  import java.time.LocalDateTime;
  import java.util.ArrayList;
  import java.util.List;

  @Service
  @RequiredArgsConstructor
  public class ImportExportService {

      private static final String[] HEADER = {
              "itemName", "platform", "direction", "quantity", "unitPrice",
              "fee", "feeRate", "currency", "tradedAt", "externalTradeId", "status", "note"
      };

      private final TradeService tradeService;
      private final ObjectMapper objectMapper;

      public ImportResult importCsv(MultipartFile file) throws IOException {
          ImportResult result = new ImportResult(0, 0, new ArrayList<>());
          try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
               CSVParser parser = CSVFormat.DEFAULT.builder()
                       .setHeader()
                       .setSkipHeaderRecord(true)
                       .setTrim(true)
                       .build()
                       .parse(reader)) {
              int line = 1;
              for (CSVRecord record : parser) {
                  line++;
                  try {
                      tradeService.create(toRequest(record));
                      result = result.withCreated(result.created() + 1);
                  } catch (Exception e) {
                      result = result.withFailed(result.failed() + 1);
                      result.errors().add("第 " + line + " 行: " + e.getMessage());
                  }
              }
          }
          return result;
      }

      public ImportResult importJson(List<TradeCreateRequest> requests) {
          ImportResult result = new ImportResult(0, 0, new ArrayList<>());
          for (int i = 0; i < requests.size(); i++) {
              try {
                  tradeService.create(requests.get(i));
                  result = result.withCreated(result.created() + 1);
              } catch (Exception e) {
                  result = result.withFailed(result.failed() + 1);
                  result.errors().add("第 " + (i + 1) + " 条: " + e.getMessage());
              }
          }
          return result;
      }

      public byte[] export(String format) throws IOException {
          return switch (format) {
              case "json" -> exportJson();
              case "xlsx" -> exportXlsx();
              default -> exportCsv();
          };
      }

      private byte[] exportCsv() throws IOException {
          StringWriter out = new StringWriter();
          try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
              for (TradeResponse t : tradeService.list(null)) {
                  printer.printRecord(t.itemName(), t.platform(), t.direction(), t.quantity(),
                          t.unitPrice(), t.fee(), t.feeRate(), t.currency(), t.tradedAt(),
                          t.externalTradeId(), t.status(), t.note());
              }
          }
          return out.toString().getBytes(StandardCharsets.UTF_8);
      }

      private byte[] exportJson() throws IOException {
          return objectMapper.writeValueAsBytes(tradeService.list(null));
      }

      private byte[] exportXlsx() throws IOException {
          try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
              Sheet sheet = wb.createSheet("trades");
              Row header = sheet.createRow(0);
              for (int i = 0; i < HEADER.length; i++) {
                  header.createCell(i).setCellValue(HEADER[i]);
              }
              int r = 1;
              for (TradeResponse t : tradeService.list(null)) {
                  Row row = sheet.createRow(r++);
                  row.createCell(0).setCellValue(t.itemName());
                  row.createCell(1).setCellValue(t.platform());
                  row.createCell(2).setCellValue(t.direction().name());
                  row.createCell(3).setCellValue(t.quantity().doubleValue());
                  row.createCell(4).setCellValue(t.unitPrice().doubleValue());
                  row.createCell(5).setCellValue(t.fee().doubleValue());
                  row.createCell(6).setCellValue(t.feeRate() == null ? null : t.feeRate().doubleValue());
                  row.createCell(7).setCellValue(t.currency());
                  row.createCell(8).setCellValue(t.tradedAt().toString());
                  row.createCell(9).setCellValue(t.externalTradeId());
                  row.createCell(10).setCellValue(t.status().name());
                  row.createCell(11).setCellValue(t.note());
              }
              for (int i = 0; i < HEADER.length; i++) {
                  sheet.autoSizeColumn(i);
              }
              wb.write(out);
              return out.toByteArray();
          }
      }

      private TradeCreateRequest toRequest(CSVRecord record) {
          return new TradeCreateRequest(
                  record.get("itemName"),
                  record.get("platform"),
                  TradeDirection.valueOf(record.get("direction").toUpperCase()),
                  new BigDecimal(record.get("quantity")),
                  new BigDecimal(record.get("unitPrice")),
                  blankToNull(record.get("fee")) == null ? BigDecimal.ZERO : new BigDecimal(blankToNull(record.get("fee"))),
                  blankToNull(record.get("feeRate")) == null ? null : new BigDecimal(blankToNull(record.get("feeRate"))),
                  blankToNull(record.get("currency")),
                  LocalDateTime.parse(record.get("tradedAt")),
                  blankToNull(record.get("externalTradeId")),
                  blankToNull(record.get("status")) == null ? null
                          : TradeStatus.valueOf(blankToNull(record.get("status")).toUpperCase()),
                  blankToNull(record.get("note")));
      }

      private String blankToNull(String s) {
          return (s == null || s.isBlank()) ? null : s.trim();
      }
  }
  ```

- [ ] **Step 4: 创建异常类与全局异常处理（两个文件）**

  ```java
  package com.cs.skinledger.web;

  public class TradeNotFoundException extends RuntimeException {
      public TradeNotFoundException(Long id) {
          super("交易不存在: " + id);
      }
  }
  ```

  ```java
  package com.cs.skinledger.web;

  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.MethodArgumentNotValidException;
  import org.springframework.web.bind.annotation.ExceptionHandler;
  import org.springframework.web.bind.annotation.RestControllerAdvice;
  import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

  import java.util.Map;

  @RestControllerAdvice
  public class ApiExceptionHandler {

      @ExceptionHandler(TradeNotFoundException.class)
      public ResponseEntity<Map<String, String>> notFound(TradeNotFoundException e) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
      }

      @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class,
              MethodArgumentTypeMismatchException.class})
      public ResponseEntity<Map<String, String>> badRequest(Exception e) {
          String message = e instanceof MethodArgumentNotValidException ex
                  ? ex.getBindingResult().getFieldErrors().stream()
                  .findFirst()
                  .map(f -> f.getField() + ": " + f.getDefaultMessage())
                  .orElse("参数错误")
                  : e.getMessage();
          return ResponseEntity.badRequest().body(Map.of("message", message));
      }

      @ExceptionHandler(Exception.class)
      public ResponseEntity<Map<String, String>> serverError(Exception e) {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                  .body(Map.of("message", "服务器内部错误: " + e.getMessage()));
      }
  }
  ```

- [ ] **Step 5: 创建 `TradeController.java`**

  ```java
  package com.cs.skinledger.web;

  import com.cs.skinledger.domain.TradeDirection;
  import com.cs.skinledger.dto.ImportResult;
  import com.cs.skinledger.dto.TradeCreateRequest;
  import com.cs.skinledger.dto.TradeFilter;
  import com.cs.skinledger.dto.TradeResponse;
  import com.cs.skinledger.service.ImportExportService;
  import com.cs.skinledger.service.TradeService;
  import jakarta.validation.Valid;
  import lombok.RequiredArgsConstructor;
  import org.springframework.format.annotation.DateTimeFormat;
  import org.springframework.http.HttpHeaders;
  import org.springframework.http.MediaType;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.DeleteMapping;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.PathVariable;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.PutMapping;
  import org.springframework.web.bind.annotation.RequestBody;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestParam;
  import org.springframework.web.bind.annotation.RestController;
  import org.springframework.web.multipart.MultipartFile;

  import java.io.IOException;
  import java.time.LocalDateTime;
  import java.util.List;

  @RestController
  @RequestMapping("/api/trades")
  @RequiredArgsConstructor
  public class TradeController {

      private final TradeService tradeService;
      private final ImportExportService importExportService;

      @GetMapping
      public List<TradeResponse> list(
              @RequestParam(required = false) String platform,
              @RequestParam(required = false) String direction,
              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
              @RequestParam(required = false) String q,
              @RequestParam(required = false) String category) {
          TradeFilter filter = new TradeFilter(
                  platform,
                  direction == null ? null : TradeDirection.valueOf(direction),
                  from, to, q, category);
          return tradeService.list(filter);
      }

      @PostMapping
      public TradeResponse create(@Valid @RequestBody TradeCreateRequest req) {
          return tradeService.create(req);
      }

      @PutMapping("/{id}")
      public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeCreateRequest req) {
          return tradeService.update(id, req);
      }

      @DeleteMapping("/{id}")
      public ResponseEntity<Void> delete(@PathVariable Long id) {
          tradeService.delete(id);
          return ResponseEntity.noContent().build();
      }

      @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
      public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
          return importExportService.importCsv(file);
      }

      @PostMapping("/import/json")
      public ImportResult importJson(@RequestBody List<@Valid TradeCreateRequest> requests) {
          return importExportService.importJson(requests);
      }

      @GetMapping("/export")
      public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) throws IOException {
          byte[] body = importExportService.export(format);
          String ext = "xlsx".equals(format) ? "xlsx" : format;
          return ResponseEntity.ok()
                  .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trades." + ext)
                  .contentType(mediaType(format))
                  .body(body);
      }

      private MediaType mediaType(String format) {
          return switch (format) {
              case "json" -> MediaType.APPLICATION_JSON;
              case "xlsx" -> MediaType.parseMediaType(
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
              default -> MediaType.parseMediaType("text/csv");
          };
      }
  }
  ```

- [ ] **Step 6: 创建测试配置 `application-test.yml`**

  ```yaml
  spring:
    datasource:
      url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
      driver-class-name: org.h2.Driver
      username: sa
      password:
    jpa:
      hibernate:
        ddl-auto: create-drop
      open-in-view: false
    flyway:
      enabled: false
    jackson:
      serialization:
        write-dates-as-timestamps: false
  ```

- [ ] **Step 7: 写失败测试 `TradeControllerTest.java`**

  ```java
  package com.cs.skinledger.web;

  import com.cs.skinledger.repository.ItemRepository;
  import com.cs.skinledger.repository.TradeRepository;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.http.MediaType;
  import org.springframework.mock.web.MockMultipartFile;
  import org.springframework.test.context.ActiveProfiles;
  import org.springframework.test.web.servlet.MockMvc;
  import org.springframework.test.web.servlet.MvcResult;

  import java.nio.charset.StandardCharsets;
  import java.util.Map;

  import static org.hamcrest.Matchers.containsString;
  import static org.junit.jupiter.api.Assertions.assertTrue;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class TradeControllerTest {

      @Autowired
      private MockMvc mockMvc;

      @Autowired
      private ObjectMapper objectMapper;

      @Autowired
      private TradeRepository tradeRepository;

      @Autowired
      private ItemRepository itemRepository;

      @BeforeEach
      void cleanDatabase() {
          tradeRepository.deleteAll();
          itemRepository.deleteAll();
      }

      private String body(String item, String platform, String direction, String qty, String price, String fee)
              throws Exception {
          return objectMapper.writeValueAsString(Map.of(
                  "itemName", item,
                  "platform", platform,
                  "direction", direction,
                  "quantity", qty,
                  "unitPrice", price,
                  "fee", fee,
                  "tradedAt", "2026-01-05T10:00:00"));
      }

      @Test
      void createTradePersistsAndComputesTotal() throws Exception {
          mockMvc.perform(post("/api/trades")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("AK-47 | Redline (Field-Tested)", "steam", "BUY", "2", "100", "10")))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").isNumber())
                  .andExpect(jsonPath("$.itemName").value("AK-47 | Redline (Field-Tested)"))
                  .andExpect(jsonPath("$.totalAmount").value(200.0))
                  .andExpect(jsonPath("$.currency").value("CNY"))
                  .andExpect(jsonPath("$.status").value("COMPLETED"));
      }

      @Test
      void listFiltersByPlatform() throws Exception {
          mockMvc.perform(post("/api/trades")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("Case 1", "steam", "BUY", "1", "50", "0")));
          mockMvc.perform(post("/api/trades")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("Case 2", "uu", "BUY", "1", "50", "0")));

          mockMvc.perform(get("/api/trades").param("platform", "uu"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.length()").value(1))
                  .andExpect(jsonPath("$[0].platform").value("uu"));
      }

      @Test
      void updateTradeChangesFields() throws Exception {
          String created = mockMvc.perform(post("/api/trades")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("Gloves", "steam", "BUY", "1", "100", "0")))
                  .andReturn().getResponse().getContentAsString();
          long id = objectMapper.readTree(created).get("id").asLong();

          mockMvc.perform(put("/api/trades/" + id)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("Gloves", "steam", "BUY", "2", "90", "0")))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.quantity").value(2.0));
      }

      @Test
      void deleteTradeThenGetReturns404() throws Exception {
          String created = mockMvc.perform(post("/api/trades")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("Sticker", "steam", "BUY", "1", "10", "0")))
                  .andReturn().getResponse().getContentAsString();
          long id = objectMapper.readTree(created).get("id").asLong();

          mockMvc.perform(delete("/api/trades/" + id)).andExpect(status().isNoContent());
          mockMvc.perform(delete("/api/trades/" + id)).andExpect(status().isNotFound());
      }

      @Test
      void invalidPlatformReturns400() throws Exception {
          mockMvc.perform(post("/api/trades")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("Knife", "csgobackpack", "BUY", "1", "100", "0")))
                  .andExpect(status().isBadRequest());
      }

      @Test
      void sellExceedingHoldingsReturns400() throws Exception {
          mockMvc.perform(post("/api/trades")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("AK-47 | Redline (Field-Tested)", "steam", "BUY", "1", "100", "0")));
          mockMvc.perform(post("/api/trades")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body("AK-47 | Redline (Field-Tested)", "steam", "SELL", "2", "150", "0")))
                  .andExpect(status().isBadRequest());
      }

      @Test
      void importCsvCreatesTrades() throws Exception {
          String csv = "itemName,platform,direction,quantity,unitPrice,fee,feeRate,currency,tradedAt,externalTradeId,status,note\n"
                  + "Test Case,steam,BUY,1,50,0,,CNY,2026-01-05T10:00:00,,COMPLETED,\n";
          mockMvc.perform(multipart("/api/trades/import/csv")
                          .file(new MockMultipartFile("file", "trades.csv", "text/csv",
                                  csv.getBytes(StandardCharsets.UTF_8))))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.created").value(1))
                  .andExpect(jsonPath("$.failed").value(0));
      }

      @Test
      void importJsonCreatesTrades() throws Exception {
          String json = "[{\"itemName\":\"JSON Knife\",\"platform\":\"steam\",\"direction\":\"BUY\","
                  + "\"quantity\":1,\"unitPrice\":80,\"fee\":0,\"tradedAt\":\"2026-01-05T10:00:00\"}]";
          mockMvc.perform(post("/api/trades/import/json")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(json))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.created").value(1))
                  .andExpect(jsonPath("$.failed").value(0));
      }

      @Test
      void exportCsvContainsHeaderAndRows() throws Exception {
          mockMvc.perform(post("/api/trades")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("Export Knife", "steam", "BUY", "1", "100", "0")));

          mockMvc.perform(get("/api/trades/export").param("format", "csv"))
                  .andExpect(status().isOk())
                  .andExpect(header().string("Content-Disposition", containsString("trades.csv")))
                  .andExpect(content().string(containsString("itemName")))
                  .andExpect(content().string(containsString("Export Knife")));
      }

      @Test
      void exportXlsxReturnsNonEmptyBytes() throws Exception {
          MvcResult result = mockMvc.perform(get("/api/trades/export").param("format", "xlsx"))
                  .andExpect(status().isOk())
                  .andExpect(content().contentType(
                          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                  .andReturn();
          assertTrue(result.getResponse().getContentAsByteArray().length > 0);
      }
  }
  ```

- [ ] **Step 8: 先创建 Task 5 的四个统计 DTO 空壳，再跑测试（预期先红后绿）**

  在 `backend/src/main/java/com/cs/skinledger/dto/` 下创建四个临时 record（Task 5 会填充）：

  ```java
  // HoldingRow.java（临时）
  package com.cs.skinledger.dto;
  public record HoldingRow(String itemName, java.math.BigDecimal quantity,
                           java.math.BigDecimal avgCost, java.math.BigDecimal realizedPnl,
                           java.math.BigDecimal unrealizedPnl) {}
  ```

  ```java
  // PnlRow.java（临时）
  package com.cs.skinledger.dto;
  public record PnlRow(String key, java.math.BigDecimal realizedPnl, int tradeCount) {}
  ```

  ```java
  // PnlGroupBy.java（临时）
  package com.cs.skinledger.dto;
  public enum PnlGroupBy { day, week, month, year, platform, category, item }
  ```

  ```java
  // PortfolioView.java（临时）
  package com.cs.skinledger.dto;
  public record PortfolioView(java.math.BigDecimal totalCost,
                              java.math.BigDecimal totalRealizedPnl,
                              java.util.List<HoldingRow> holdings) {}
  ```

  先运行测试确认它们因为"控制器/服务尚不存在"而失败：

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q -Dtest=TradeControllerTest test
  ```

  预期：FAIL（编译错误：找不到 TradeController 等）。

- [ ] **Step 9: 实现以上所有文件后，重新运行测试**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q -Dtest=TradeControllerTest test
  ```

  预期：`Tests run: 10, Failures: 0, Errors: 0`，BUILD SUCCESS。

- [ ] **Step 10: 提交**

  ```powershell
  git add backend
  git commit -m "feat(backend): 交易 CRUD + 导入导出 API（含校验与测试）"
  ```

## Task 5: 统计 API（TDD）

**Files:**
- 说明：`HoldingRow`、`PnlRow`、`PnlGroupBy`、`PortfolioView` 已在 Task 4 以最终形态创建。
- Create: `backend/src/main/java/com/cs/skinledger/web/AnalyticsController.java`
- Test: `backend/src/test/java/com/cs/skinledger/web/AnalyticsControllerTest.java`

- [ ] **Step 1: 写失败测试 `AnalyticsControllerTest.java`**

  ```java
  package com.cs.skinledger.web;

  import com.cs.skinledger.repository.ItemRepository;
  import com.cs.skinledger.repository.TradeRepository;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.ActiveProfiles;
  import org.springframework.test.web.servlet.MockMvc;

  import java.util.Map;

  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class AnalyticsControllerTest {

      @Autowired
      private MockMvc mockMvc;

      @Autowired
      private ObjectMapper objectMapper;

      @Autowired
      private TradeRepository tradeRepository;

      @Autowired
      private ItemRepository itemRepository;

      @BeforeEach
      void cleanDatabase() {
          tradeRepository.deleteAll();
          itemRepository.deleteAll();
      }

      private String body(String item, String direction, String qty, String price) throws Exception {
          return objectMapper.writeValueAsString(Map.of(
                  "itemName", item,
                  "platform", "steam",
                  "direction", direction,
                  "quantity", qty,
                  "unitPrice", price,
                  "fee", "0",
                  "tradedAt", "2026-01-05T10:00:00"));
      }

      @Test
      void realizedPnlGroupedByItem() throws Exception {
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Stats Knife", "BUY", "1", "100")));
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Stats Knife", "BUY", "1", "100")));
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Stats Knife", "SELL", "1", "150")));
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Stats Knife", "SELL", "1", "150")));

          mockMvc.perform(get("/api/analytics/pnl").param("group_by", "item"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$[0].key").value("Stats Knife"))
                  .andExpect(jsonPath("$[0].realizedPnl").value(100.0))
                  .andExpect(jsonPath("$[0].tradeCount").value(2));
      }

      @Test
      void portfolioReturnsHoldingsWithRealizedPnl() throws Exception {
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Hold Knife", "BUY", "2", "100")));
          mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                  .content(body("Hold Knife", "SELL", "1", "120")));

          mockMvc.perform(get("/api/analytics/portfolio"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.holdings.length()").value(1))
                  .andExpect(jsonPath("$.holdings[0].itemName").value("Hold Knife"))
                  .andExpect(jsonPath("$.holdings[0].quantity").value(1.0))
                  .andExpect(jsonPath("$.holdings[0].realizedPnl").value(20.0))
                  .andExpect(jsonPath("$.totalRealizedPnl").value(20.0));
      }
  }
  ```

- [ ] **Step 2: 运行测试，确认失败**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q -Dtest=AnalyticsControllerTest test
  ```

  预期：FAIL（找不到 `/api/analytics` 端点）。

- [ ] **Step 3: 实现 `AnalyticsController.java`**

  ```java
  package com.cs.skinledger.web;

  import com.cs.skinledger.dto.PnlGroupBy;
  import com.cs.skinledger.dto.PnlRow;
  import com.cs.skinledger.dto.PortfolioView;
  import com.cs.skinledger.service.TradeService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestParam;
  import org.springframework.web.bind.annotation.RestController;

  import java.util.List;

  @RestController
  @RequestMapping("/api/analytics")
  @RequiredArgsConstructor
  public class AnalyticsController {

      private final TradeService tradeService;

      @GetMapping("/pnl")
      public List<PnlRow> pnl(@RequestParam(defaultValue = "month") PnlGroupBy groupBy) {
          return tradeService.realizedPnl(groupBy);
      }

      @GetMapping("/portfolio")
      public PortfolioView portfolio() {
          return tradeService.portfolio();
      }
  }
  ```

- [ ] **Step 4: 运行全部后端测试**

  ```powershell
  $env:JAVA_HOME='D:\Java\jdk-21'
  & 'D:\Java\apache-maven\bin\mvn.cmd' -f backend\pom.xml -q test
  ```

  预期：`Tests run: 19`（7 引擎 + 10 控制器 + 2 统计），全部通过，BUILD SUCCESS。

- [ ] **Step 5: 提交**

  ```powershell
  git add backend
  git commit -m "feat(backend): 统计 API（已实现/未实现盈亏占位 + 聚合）"
  ```

## Task 6: 前端工程骨架（可安装、可跑、单测通过）

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/index.html`
- Create: `frontend/src/style.css`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router.ts`
- Create: `frontend/src/types.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/utils/format.ts`
- Test: `frontend/src/utils/format.test.ts`

- [ ] **Step 1: 创建 `frontend/package.json`**

  ```json
  {
    "name": "skin-ledger-frontend",
    "private": true,
    "version": "0.1.0",
    "type": "module",
    "scripts": {
      "dev": "vite",
      "build": "vite build",
      "preview": "vite preview",
      "test": "vitest run"
    },
    "dependencies": {
      "axios": "^1.7.9",
      "echarts": "^5.6.0",
      "pinia": "^2.3.0",
      "vue": "^3.5.13",
      "vue-router": "^4.5.0"
    },
    "devDependencies": {
      "@vitejs/plugin-vue": "^5.2.1",
      "jsdom": "^25.0.1",
      "typescript": "~5.6.3",
      "vite": "^6.0.5",
      "vitest": "^2.1.8"
    }
  }
  ```

- [ ] **Step 2: 创建 `frontend/vite.config.ts`**

  ```ts
  /// <reference types="vitest" />
  import { defineConfig } from 'vite'
  import vue from '@vitejs/plugin-vue'

  export default defineConfig({
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: 'http://localhost:8080', changeOrigin: true }
      }
    },
    test: {
      environment: 'jsdom'
    }
  })
  ```

- [ ] **Step 3: 创建 `frontend/tsconfig.json`**

  ```json
  {
    "compilerOptions": {
      "target": "ES2020",
      "useDefineForClassFields": true,
      "module": "ESNext",
      "lib": ["ES2020", "DOM", "DOM.Iterable"],
      "skipLibCheck": true,
      "moduleResolution": "bundler",
      "allowImportingTsExtensions": true,
      "resolveJsonModule": true,
      "isolatedModules": true,
      "noEmit": true,
      "jsx": "preserve",
      "strict": true,
      "types": ["vite/client", "vitest/globals"]
    },
    "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.vue"]
  }
  ```

- [ ] **Step 4: 创建 `frontend/index.html` 与 `frontend/src/style.css`**

  ```html
  <!doctype html>
  <html lang="zh-CN">
    <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <title>CS 饰品买卖统计</title>
    </head>
    <body>
      <div id="app"></div>
      <script type="module" src="/src/main.ts"></script>
    </body>
  </html>
  ```

  ```css
  body {
    margin: 0;
    font-family: "Segoe UI", "Microsoft YaHei", sans-serif;
    background: #f4f5f7;
    color: #222;
  }
  ```

- [ ] **Step 5: 创建 `src/main.ts`、`src/App.vue`、`src/router.ts`**

  ```ts
  import { createApp } from 'vue'
  import { createPinia } from 'pinia'
  import App from './App.vue'
  import { router } from './router'
  import './style.css'

  createApp(App).use(createPinia()).use(router).mount('#app')
  ```

  ```vue
  <script setup lang="ts">
  </script>

  <template>
    <header class="nav">
      <span class="brand">CS 饰品买卖统计</span>
      <nav>
        <router-link to="/">仪表盘</router-link>
        <router-link to="/trades">交易记录</router-link>
      </nav>
    </header>
    <main class="container">
      <router-view />
    </main>
  </template>

  <style>
  .nav { display: flex; align-items: center; gap: 24px; padding: 12px 24px; background: #1b1b1f; color: #fff; }
  .nav .brand { font-weight: 600; }
  .nav a { color: #c9c9d4; text-decoration: none; margin-right: 12px; }
  .nav a.router-link-active { color: #fff; font-weight: 600; }
  .container { max-width: 1100px; margin: 24px auto; padding: 0 16px; }
  </style>
  ```

  ```ts
  import { createRouter, createWebHistory } from 'vue-router'
  import DashboardView from './views/DashboardView.vue'
  import TradesView from './views/TradesView.vue'

  export const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: DashboardView },
      { path: '/trades', name: 'trades', component: TradesView }
    ]
  })
  ```

  > 注：`DashboardView.vue` / `TradesView.vue` 在 Task 7/8 创建；本步完成后先创建两个空占位组件（`<template><div /></template>`）以便 `npm run dev` 通过。

- [ ] **Step 6: 创建 `src/types.ts` 与 `src/api/client.ts`**

  ```ts
  export type Direction = 'BUY' | 'SELL'
  export type Status = 'COMPLETED' | 'PENDING'

  export interface Trade {
    id: number
    itemName: string
    platform: string
    direction: Direction
    quantity: number
    unitPrice: number
    totalAmount: number
    fee: number
    feeRate: number | null
    currency: string
    tradedAt: string
    externalTradeId: string | null
    status: Status
    note: string | null
  }

  export interface TradeCreateRequest {
    itemName: string
    platform: string
    direction: Direction
    quantity: number
    unitPrice: number
    fee?: number
    feeRate?: number
    currency?: string
    tradedAt: string
    externalTradeId?: string
    status?: Status
    note?: string
  }

  export interface PnlRow {
    key: string
    realizedPnl: number
    tradeCount: number
  }

  export interface HoldingRow {
    itemName: string
    quantity: number
    avgCost: number
    realizedPnl: number
    unrealizedPnl: number | null
  }

  export interface ImportResult {
    created: number
    failed: number
    errors: string[]
  }
  ```

  ```ts
  import axios from 'axios'

  export const api = axios.create({ baseURL: '/api' })

  export interface TradeQuery {
    platform?: string
    direction?: string
    from?: string
    to?: string
    q?: string
    category?: string
  }
  ```

- [ ] **Step 7: 创建 `src/utils/format.ts` 与测试 `src/utils/format.test.ts`**

  ```ts
  export function formatMoney(n: number | null | undefined): string {
    if (n === null || n === undefined || Number.isNaN(n)) return '-'
    return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
  }

  export function toIsoLocal(d: Date): string {
    const pad = (x: number) => String(x).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
      `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }

  export function downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }
  ```

  ```ts
  import { describe, expect, it } from 'vitest'
  import { formatMoney, toIsoLocal } from './format'

  describe('format', () => {
    it('formats money with thousand separators', () => {
      expect(formatMoney(1234.5)).toBe('1,234.50')
    })

    it('handles null as dash', () => {
      expect(formatMoney(null)).toBe('-')
    })

    it('formats local datetime to ISO string', () => {
      const d = new Date(2026, 0, 5, 9, 7, 8)
      expect(toIsoLocal(d)).toBe('2026-01-05T09:07:08')
    })
  })
  ```

- [ ] **Step 8: 安装依赖**

  ```powershell
  cd frontend
  npm.cmd install
  ```

  预期：生成 `node_modules` 与 `package-lock.json`（依赖全部在项目目录内）。若网络超时，重试 `npm.cmd install`。

- [ ] **Step 9: 跑前端单测**

  ```powershell
  npm.cmd test
  ```

  预期：3 个测试全部通过。

- [ ] **Step 10: 启动开发服务器验证**

  ```powershell
  npm.cmd run dev
  ```

  浏览器打开 http://localhost:5173 ，预期看到顶部导航（仪表盘/交易记录）与两个空占位页面。

- [ ] **Step 11: 提交**

  ```powershell
  cd ..
  git add frontend
  git commit -m "feat(frontend): Vite+Vue3 工程骨架（路由/类型/工具函数+测试）"
  ```

## Task 7: 前端交易记录页（Pinia store + 列表/表单/导入导出）

**Files:**
- Create: `frontend/src/stores/trades.ts`
- Create: `frontend/src/views/TradesView.vue`
- Create: `frontend/src/components/TradeTable.vue`
- Create: `frontend/src/components/TradeForm.vue`

- [ ] **Step 1: 创建 `src/stores/trades.ts`**

  ```ts
  import { ref } from 'vue'
  import { defineStore } from 'pinia'
  import { api, type TradeQuery } from '../api/client'
  import { downloadBlob } from '../utils/format'
  import type { HoldingRow, ImportResult, PnlRow, Trade, TradeCreateRequest } from '../types'

  export const useTradesStore = defineStore('trades', () => {
    const trades = ref<Trade[]>([])
    const holdings = ref<HoldingRow[]>([])
    const pnlRows = ref<PnlRow[]>([])
    const totalCost = ref(0)
    const totalRealizedPnl = ref(0)
    const loading = ref(false)
    const error = ref('')

    async function loadTrades(query: TradeQuery = {}) {
      loading.value = true
      error.value = ''
      try {
        const { data } = await api.get<Trade[]>('/trades', { params: query })
        trades.value = data
      } catch (e) {
        error.value = String(e)
      } finally {
        loading.value = false
      }
    }

    async function loadPortfolio() {
      const { data } = await api.get<{
        totalCost: number
        totalRealizedPnl: number
        holdings: HoldingRow[]
      }>('/analytics/portfolio')
      totalCost.value = data.totalCost
      totalRealizedPnl.value = data.totalRealizedPnl
      holdings.value = data.holdings
    }

    async function loadPnl(groupBy: string) {
      const { data } = await api.get<PnlRow[]>('/analytics/pnl', { params: { group_by: groupBy } })
      pnlRows.value = data
    }

    async function createTrade(payload: TradeCreateRequest) {
      await api.post<Trade>('/trades', payload)
    }

    async function updateTrade(id: number, payload: TradeCreateRequest) {
      await api.put<Trade>(`/trades/${id}`, payload)
    }

    async function deleteTrade(id: number) {
      await api.delete(`/trades/${id}`)
    }

    async function importCsv(file: File) {
      const fd = new FormData()
      fd.append('file', file)
      const { data } = await api.post<ImportResult>('/trades/import/csv', fd)
      return data
    }

    async function exportTrades(format: 'csv' | 'json' | 'xlsx') {
      const { data } = await api.get<Blob>('/trades/export', {
        params: { format },
        responseType: 'blob'
      })
      downloadBlob(data, `trades.${format === 'xlsx' ? 'xlsx' : format}`)
    }

    return {
      trades, holdings, pnlRows, totalCost, totalRealizedPnl, loading, error,
      loadTrades, loadPortfolio, loadPnl, createTrade, updateTrade, deleteTrade,
      importCsv, exportTrades
    }
  })
  ```

- [ ] **Step 2: 创建 `src/views/TradesView.vue`**

  ```vue
  <script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import TradeForm from '../components/TradeForm.vue'
  import TradeTable from '../components/TradeTable.vue'
  import { useTradesStore } from '../stores/trades'
  import type { Trade, TradeCreateRequest } from '../types'

  const store = useTradesStore()
  const q = ref('')
  const platform = ref('')
  const direction = ref('')
  const showForm = ref(false)
  const editing = ref<Trade | null>(null)
  const fileInput = ref<HTMLInputElement | null>(null)

  async function applyFilters() {
    await store.loadTrades({
      q: q.value || undefined,
      platform: platform.value || undefined,
      direction: direction.value || undefined
    })
  }

  function openCreate() {
    editing.value = null
    showForm.value = true
  }

  function openEdit(t: Trade) {
    editing.value = t
    showForm.value = true
  }

  async function onSaved(payload: TradeCreateRequest) {
    if (editing.value) {
      await store.updateTrade(editing.value.id, payload)
    } else {
      await store.createTrade(payload)
    }
    showForm.value = false
    await applyFilters()
  }

  async function onDelete(t: Trade) {
    if (confirm(`确认删除「${t.itemName}」这笔交易？`)) {
      await store.deleteTrade(t.id)
      await applyFilters()
    }
  }

  async function onImport() {
    const file = fileInput.value?.files?.[0]
    if (!file) return
    const result = await store.importCsv(file)
    const msg = `导入完成：成功 ${result.created} 条，失败 ${result.failed} 条`
      + (result.errors.length ? '\n' + result.errors.join('\n') : '')
    alert(msg)
    if (fileInput.value) fileInput.value.value = ''
    await applyFilters()
  }

  onMounted(applyFilters)
  </script>

  <template>
    <div>
      <h1>交易记录</h1>
      <div class="toolbar">
        <input v-model="q" placeholder="搜索饰品名称" @keyup.enter="applyFilters" />
        <select v-model="platform" @change="applyFilters">
          <option value="">全部平台</option>
          <option value="steam">Steam</option>
          <option value="uu">UU</option>
          <option value="buff">BUFF</option>
        </select>
        <select v-model="direction" @change="applyFilters">
          <option value="">全部方向</option>
          <option value="BUY">买入</option>
          <option value="SELL">卖出</option>
        </select>
        <button @click="openCreate">新增交易</button>
        <button @click="store.exportTrades('csv')">导出 CSV</button>
        <button @click="store.exportTrades('json')">导出 JSON</button>
        <button @click="store.exportTrades('xlsx')">导出 Excel</button>
        <label class="btn">
          导入 CSV
          <input ref="fileInput" type="file" accept=".csv" style="display:none" @change="onImport" />
        </label>
      </div>
      <p v-if="store.error" class="error">{{ store.error }}</p>
      <TradeTable :trades="store.trades" @edit="openEdit" @delete="onDelete" />

      <TradeForm v-if="showForm" :editing="editing" @close="showForm = false" @saved="onSaved" />
    </div>
  </template>

  <style scoped>
  .toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
  .toolbar input, .toolbar select { padding: 6px 8px; }
  .toolbar button, .btn { padding: 6px 12px; cursor: pointer; border: 1px solid #bbb; background: #fff; border-radius: 4px; }
  .error { color: #c00; }
  </style>
  ```

- [ ] **Step 3: 创建 `src/components/TradeTable.vue`**

  ```vue
  <script setup lang="ts">
  import type { Trade } from '../types'

  defineProps<{ trades: Trade[] }>()
  const emit = defineEmits<{ (e: 'edit', t: Trade): void; (e: 'delete', t: Trade): void }>()
  </script>

  <template>
    <table>
      <thead>
        <tr>
          <th>时间</th><th>饰品</th><th>平台</th><th>方向</th><th>数量</th>
          <th>单价</th><th>总额</th><th>手续费</th><th>状态</th><th>备注</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="t in trades" :key="t.id">
          <td>{{ t.tradedAt.replace('T', ' ') }}</td>
          <td>{{ t.itemName }}</td>
          <td>{{ t.platform }}</td>
          <td :class="t.direction === 'BUY' ? 'buy' : 'sell'">
            {{ t.direction === 'BUY' ? '买入' : '卖出' }}
          </td>
          <td>{{ t.quantity }}</td>
          <td>{{ t.unitPrice }}</td>
          <td>{{ t.totalAmount }}</td>
          <td>{{ t.fee }}</td>
          <td>{{ t.status }}</td>
          <td>{{ t.note }}</td>
          <td>
            <button @click="emit('edit', t)">编辑</button>
            <button @click="emit('delete', t)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </template>

  <style scoped>
  table { width: 100%; border-collapse: collapse; background: #fff; }
  th, td { border: 1px solid #e2e2e8; padding: 8px; text-align: left; font-size: 14px; }
  th { background: #f0f1f4; }
  .buy { color: #0a7d33; }
  .sell { color: #c00; }
  button { margin-right: 4px; }
  </style>
  ```

- [ ] **Step 4: 创建 `src/components/TradeForm.vue`**

  ```vue
  <script setup lang="ts">
  import { reactive } from 'vue'
  import type { Trade, TradeCreateRequest } from '../types'

  const props = defineProps<{ editing: Trade | null }>()
  const emit = defineEmits<{
    (e: 'close'): void
    (e: 'saved', payload: TradeCreateRequest): void
  }>()

  const form = reactive({
    itemName: props.editing?.itemName ?? '',
    platform: props.editing?.platform ?? 'steam',
    direction: props.editing?.direction ?? 'BUY',
    quantity: props.editing ? String(props.editing.quantity) : '1',
    unitPrice: props.editing ? String(props.editing.unitPrice) : '',
    fee: props.editing ? String(props.editing.fee) : '0',
    feeRate: props.editing?.feeRate != null ? String(props.editing.feeRate) : '',
    currency: props.editing?.currency ?? 'CNY',
    tradedAt: props.editing
      ? props.editing.tradedAt.slice(0, 16)
      : new Date().toISOString().slice(0, 16),
    externalTradeId: props.editing?.externalTradeId ?? '',
    status: props.editing?.status ?? 'COMPLETED',
    note: props.editing?.note ?? ''
  })

  function submit() {
    const tradedAt = form.tradedAt.length === 16 ? form.tradedAt + ':00' : form.tradedAt
    emit('saved', {
      itemName: form.itemName.trim(),
      platform: form.platform,
      direction: form.direction as 'BUY' | 'SELL',
      quantity: Number(form.quantity),
      unitPrice: Number(form.unitPrice),
      fee: Number(form.fee),
      feeRate: form.feeRate ? Number(form.feeRate) : undefined,
      currency: form.currency,
      tradedAt,
      externalTradeId: form.externalTradeId || undefined,
      status: form.status as 'COMPLETED' | 'PENDING',
      note: form.note || undefined
    })
  }
  </script>

  <template>
    <div class="mask">
      <div class="panel">
        <h2>{{ props.editing ? '编辑交易' : '新增交易' }}</h2>
        <div class="grid">
          <label>饰品名称<input v-model="form.itemName" /></label>
          <label>平台
            <select v-model="form.platform">
              <option value="steam">Steam</option>
              <option value="uu">UU</option>
              <option value="buff">BUFF</option>
            </select>
          </label>
          <label>方向
            <select v-model="form.direction">
              <option value="BUY">买入</option>
              <option value="SELL">卖出</option>
            </select>
          </label>
          <label>数量<input v-model="form.quantity" type="number" step="0.0001" min="0.0001" /></label>
          <label>单价<input v-model="form.unitPrice" type="number" step="0.0001" min="0" /></label>
          <label>手续费<input v-model="form.fee" type="number" step="0.0001" min="0" /></label>
          <label>费率 %<input v-model="form.feeRate" type="number" step="0.000001" min="0" /></label>
          <label>币种<input v-model="form.currency" /></label>
          <label>成交时间<input v-model="form.tradedAt" type="datetime-local" /></label>
          <label>平台单号<input v-model="form.externalTradeId" /></label>
          <label>状态
            <select v-model="form.status">
              <option value="COMPLETED">已完成</option>
              <option value="PENDING">进行中</option>
            </select>
          </label>
          <label class="wide">备注<input v-model="form.note" /></label>
        </div>
        <div class="actions">
          <button class="primary" @click="submit">保存</button>
          <button @click="emit('close')">取消</button>
        </div>
      </div>
    </div>
  </template>

  <style scoped>
  .mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; }
  .panel { background: #fff; padding: 20px 24px; border-radius: 8px; width: 560px; max-height: 90vh; overflow: auto; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
  .grid .wide { grid-column: 1 / -1; }
  input, select { padding: 6px 8px; }
  .actions { margin-top: 16px; display: flex; gap: 8px; justify-content: flex-end; }
  .primary { background: #2563eb; color: #fff; border: none; padding: 8px 16px; border-radius: 4px; }
  </style>
  ```

- [ ] **Step 5: 手工验收（后端需在运行中）**

  1. `npm.cmd run dev` 后打开 http://localhost:5173/trades 。
  2. 新增一笔买入（如 AK-47 | Redline，数量 1，单价 100），列表应出现该记录。
  3. 新增一笔卖出（数量 1，单价 120），应成功；再试数量 2，应报错"卖出数量超过当前持仓"。
  4. 编辑、删除各验证一次。
  5. 点"导出 CSV"，下载文件含表头与数据；导入刚才导出的 CSV，应提示成功 1 条。

- [ ] **Step 6: 提交**

  ```powershell
  git add frontend
  git commit -m "feat(frontend): 交易记录页（筛选/录入/编辑/删除/导入导出）"
  ```

## Task 8: 前端仪表盘（持仓 + 月度盈亏图）

**Files:**
- Create: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/components/PnlChart.vue`

- [ ] **Step 1: 创建 `src/components/PnlChart.vue`**

  ```vue
  <script setup lang="ts">
  import * as echarts from 'echarts'
  import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import type { PnlRow } from '../types'

  const props = defineProps<{ rows: PnlRow[] }>()
  const el = ref<HTMLDivElement | null>(null)
  let chart: echarts.ECharts | null = null

  function render() {
    if (!el.value) return
    if (!chart) chart = echarts.init(el.value)
    chart.setOption({
      title: { text: '月度已实现盈亏' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: props.rows.map(r => r.key) },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: props.rows.map(r => r.realizedPnl),
        itemStyle: { color: '#2563eb' }
      }]
    })
  }

  onMounted(render)
  watch(() => props.rows, render, { deep: true })
  onBeforeUnmount(() => chart?.dispose())
  </script>

  <template>
    <div ref="el" style="height: 320px; background: #fff; border-radius: 8px; margin-bottom: 24px;"></div>
  </template>
  ```

- [ ] **Step 2: 创建 `src/views/DashboardView.vue`**

  ```vue
  <script setup lang="ts">
  import { onMounted } from 'vue'
  import PnlChart from '../components/PnlChart.vue'
  import { useTradesStore } from '../stores/trades'
  import { formatMoney } from '../utils/format'

  const store = useTradesStore()

  onMounted(async () => {
    await Promise.all([store.loadPortfolio(), store.loadPnl('month')])
  })
  </script>

  <template>
    <div>
      <h1>仪表盘</h1>
      <div class="cards">
        <div class="card">
          <div class="label">持仓总成本</div>
          <div class="value">{{ formatMoney(store.totalCost) }}</div>
        </div>
        <div class="card">
          <div class="label">已实现盈亏</div>
          <div class="value" :class="store.totalRealizedPnl >= 0 ? 'up' : 'down'">
            {{ formatMoney(store.totalRealizedPnl) }}
          </div>
        </div>
        <div class="card">
          <div class="label">持仓数</div>
          <div class="value">{{ store.holdings.length }}</div>
        </div>
      </div>

      <PnlChart :rows="store.pnlRows" />

      <h2>当前持仓</h2>
      <table>
        <thead>
          <tr><th>饰品</th><th>数量</th><th>平均成本</th><th>已实现盈亏</th><th>浮动盈亏</th></tr>
        </thead>
        <tbody>
          <tr v-for="h in store.holdings" :key="h.itemName">
            <td>{{ h.itemName }}</td>
            <td>{{ h.quantity }}</td>
            <td>{{ formatMoney(h.avgCost) }}</td>
            <td :class="h.realizedPnl >= 0 ? 'up' : 'down'">{{ formatMoney(h.realizedPnl) }}</td>
            <td>{{ h.unrealizedPnl == null ? '-' : formatMoney(h.unrealizedPnl) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>

  <style scoped>
  .cards { display: flex; gap: 16px; margin-bottom: 24px; }
  .card { background: #fff; border-radius: 8px; padding: 16px 24px; flex: 1; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  .label { font-size: 13px; color: #666; }
  .value { font-size: 24px; font-weight: 600; margin-top: 6px; }
  .up { color: #0a7d33; }
  .down { color: #c00; }
  table { width: 100%; border-collapse: collapse; background: #fff; }
  th, td { border: 1px solid #e2e2e8; padding: 8px; text-align: left; font-size: 14px; }
  th { background: #f0f1f4; }
  </style>
  ```

- [ ] **Step 3: 手工验收**

  1. 打开 http://localhost:5173/ ，应显示三张汇总卡片、月度盈亏柱状图、持仓表。
  2. 在交易页新增几笔不同月份的交易后回仪表盘刷新，柱状图与持仓应更新。
  3. 浮动盈亏列显示 `-`（属预期，M2 接入行情后补齐）。

- [ ] **Step 4: 提交**

  ```powershell
  git add frontend
  git commit -m "feat(frontend): 仪表盘（汇总卡片/持仓/月度盈亏图）"
  ```

## Task 9: 端到端联调验收 + README

**Files:**
- Create: `README.md`

- [ ] **Step 1: 编写 `README.md`**

  ```markdown
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
  ```

- [ ] **Step 2: 端到端冒烟（后端运行中）**

  ```powershell
  $body = @{ itemName='AK-47 | Redline (Field-Tested)'; platform='steam'; direction='BUY';
             quantity=1; unitPrice=100; fee=0; tradedAt='2026-01-05T10:00:00' } | ConvertTo-Json
  Invoke-RestMethod -Uri http://localhost:8080/api/trades -Method Post -ContentType 'application/json' -Body $body
  Invoke-RestMethod -Uri 'http://localhost:8080/api/analytics/pnl?group_by=month'
  Invoke-RestMethod -Uri 'http://localhost:8080/api/analytics/portfolio'
  ```

  预期：三条命令均返回合法 JSON，且第二条含 `2026-01` 分组、第三条 holdings 含该饰品。

- [ ] **Step 3: 浏览器全流程验收**

  1. 交易页新增 2 笔买入、1 笔卖出（同一饰品）。
  2. 导出 CSV，确认内容；删除其中一条再导入 CSV 验证幂等性（重复导入会因持仓校验或平台单号去重而失败，属预期）。
  3. 仪表盘检查汇总卡片、柱状图、持仓表与账本一致。

- [ ] **Step 4: 提交**

  ```powershell
  git add README.md
  git commit -m "docs: 使用说明与端到端验收（M0+M1 完成）"
  ```

---

## 计划自检记录

- **Spec 覆盖**：M0（脚手架/迁移）→ Task 0-2；M1（手动录入 CRUD/盈亏引擎/统计/导入导出/基础页面）→ Task 3-8；验收 → Task 9。设计文档 §5 数据模型由 V1 迁移覆盖；§7 盈亏模型由 PnlEngine 覆盖；§8 API 由 TradeController/AnalyticsController 覆盖；F5 的 CSV/JSON 与 Excel 导出已覆盖（Excel 导入留待 M5 计划）。
- **占位检查**：无 TODO/TBD；所有代码步骤给出完整文件内容。
- **一致性**：TradeCreateRequest 字段与 Trade 实体、CSV 表头一致；`totalAmount = quantity * unitPrice`（不含手续费）在服务层统一计算；PnlEngine 的 SCALE=4 与 MySQL `DECIMAL(18,4)` 一致；本地用户固定 id=1（Flyway 种子 + 服务兜底创建）。
- **明确的范围决定**：M1 阶段浮动盈亏为 null（前端显示 `-`）；Excel 导入、价格提醒、行情采集、平台同步均不在本计划内，属后续计划。
