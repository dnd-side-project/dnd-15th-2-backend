# GitHub Issue #360 Task Contract

> Generated at: `2026-10-26T00:08:17+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Tomcat·Jackson·pgjdbc·Netty 보안 패치 버전 고정`
- GitHub Issue: `#360`
- Branch: `fix/gh-360-dependency-cve-oct2026`
- Base branch: `main`

## Objective

- Spring Boot 3.5.16 BOM이 관리하는 Tomcat·Jackson·PostgreSQL JDBC·Netty 버전에 남은 2026-10 Oracle CPU 관련 CVE를 BOM 버전 속성 고정으로 해소한다.

## Scope

- `build.gradle`에 `jackson-bom.version`=2.21.7, `postgresql.version`=42.7.12, `tomcat.version`=10.1.61, `netty.version`=4.1.137.Final 속성을 지정한다.
- 각 속성 위에 CVE 번호와 제거 조건(Spring Boot BOM이 같은 버전 이상을 관리하면 삭제)을 주석으로 남긴다.
- 변경 후 런타임 classpath를 OSV로 다시 조회한다.

## Explicit exclusions

- httpcore5·httpclient5(AWS SDK 전이 의존성), log4j-api, commons-lang3 갱신.
- spring-webmvc CVE-2026-47884, CVE-2026-47890(6.2.x 수정 버전 없음).
- Spring Boot 메이저·마이너 버전 변경, JDK 런타임 변경(#359, #362).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `build.gradle` 의존성 버전 속성 | 실행 에이전트 | 사용자 PR 리뷰 |
| `docs/api/openapi.json` 재생성 diff(변경 시) | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`6de8638d`, #361 병합) 위로 rebase했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- `./gradlew dependencyInsight --configuration runtimeClasspath`에서 4개 라이브러리가 목표 버전으로 해석된다.
- 변경 후 OSV 조회에서 이슈 #360 표의 CVE가 검출되지 않는다.
- `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`가 통과한다.
- 기존 테스트와 `docs/api/openapi.json` 재생성 diff로 직렬화 변화가 없음을 확인한다.
