# CS 饰品账本后端：多阶段构建（Maven 构建 -> JRE 运行）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/skin-ledger-0.1.0.jar app.jar
# 数据源通过环境变量注入（见 docker-compose.yml）
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]