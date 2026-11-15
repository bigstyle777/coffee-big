# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制 pom，利用 Docker 层缓存预下载依赖
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata

COPY --from=builder /build/target/coffee-big-server-1.0.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
