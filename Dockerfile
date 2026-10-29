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

# CVE-2026-91403: 유출된 eMudhra CA를 신뢰하지 않도록 emSign 루트 4개를 제거한다.
# Temurin 21.0.13 이상에서 blocked.certs에 eMudhra 차단이 반영되면 삭제한다(#359).
RUN keytool -delete -cacerts -storepass changeit -alias "cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" \
    && keytool -delete -cacerts -storepass changeit -alias "cn_emsign_root_ca__c1,o_emudhra_inc,ou_emsign_pki,c_us [jdk]" \
    && keytool -delete -cacerts -storepass changeit -alias "cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]" \
    && keytool -delete -cacerts -storepass changeit -alias "cn_emsign_ecc_root_ca__c3,o_emudhra_inc,ou_emsign_pki,c_us [jdk]"

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
