FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
# 테스트는 이 단계에서 실행하지 않는다. 단위·통합 테스트와 Java 규약 검사는
# 모든 PR과 main에 대해 CI가 게이트하고, 이 이미지는 그 게이트를 통과한
# 커밋으로만 빌드된다. 더 중요한 이유는 규약 테스트가 git 저장소와
# origin/main을 읽는다는 점이다 — .dockerignore가 .git을 제외하므로(이미지
# 레이어에 git 메타데이터를 넣지 않기 위해) 이미지 빌드 안에서는 구조적으로
# 통과할 수 없다(#274).
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
