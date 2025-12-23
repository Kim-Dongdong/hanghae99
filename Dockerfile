# Stage 1: Build
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# 전체 프로젝트 복사 (간단하고 확실함)
COPY . .

# 애플리케이션 빌드
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 애플리케이션 사용자 생성
RUN addgroup -S spring && adduser -S spring -G spring

# curl 설치 (헬스 체크용)
RUN apk add --no-cache curl

# 빌드된 JAR 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 권한 설정
RUN chown spring:spring app.jar

# 사용자 전환
USER spring:spring

# JVM 옵션
ENV JAVA_OPTS="-Xmx2g -Xms2g -XX:+UseG1GC"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

# 포트 노출
EXPOSE 8080

# 헬스 체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
