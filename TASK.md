# GitHub Issue #31 Task Contract

> Generated at: `2026-08-03T16:03:19+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `PostgreSQL/PostGIS 로컬 개발 환경 구성`
- GitHub Issue: `#31`
- Branch: `build/gh-31-postgres-postgis-local`

## Objective

- 실제 비밀정보를 추적하지 않고 PostgreSQL 16/PostGIS 3.5를 로컬에서 실행한다.
- 애플리케이션의 `local` profile과 격리된 `test` profile이 동일한 PostgreSQL 계열
  연결 계약을 사용하도록 구성한다.
- Testcontainers로 개발자 로컬 DB 상태와 무관하게 JDBC 연결과 PostGIS 확장을
  검증한다.

## Scope

- 검증된 digest로 고정한 `postgis/postgis:16-3.5-alpine` DB 서비스, named volume,
  health check
- 공식 이미지가 `amd64`만 제공하므로 Compose와 Testcontainers에서
  `linux/amd64` 플랫폼을 명시하고 Apple Silicon은 Docker emulation을 사용
- placeholder만 포함하는 `.env.example`과 Git에서 제외되는 `.env`
- Compose의 `app`과 `db` 연결 및 health dependency
- Spring JDBC와 PostgreSQL runtime driver
- `application-local.properties`의 환경변수 기반 datasource 설정
- `application-test.properties`와 Testcontainers service connection
- PostgreSQL 연결 및 `PostGIS_Version()` 통합 테스트
- fresh clone 기준 시작·중지·초기화·검증 명령 문서
- 데이터 접근 및 migration 결정:
  - JDBC는 연결 smoke test와 이후 명시적 SQL 사용 기반으로 지금 도입한다.
  - JPA는 실제 Entity와 aggregate persistence 요구가 생기는 기능 Issue에서 결정한다.
  - Flyway는 첫 추적 DB schema 또는 extension migration을 추가하는 Issue에서 도입한다.

## Explicit exclusions

- 제품 ERD, 테이블, Entity, Repository 및 migration 구현
- JPA/Hibernate와 Flyway 의존성 도입
- 운영 DB, AWS RDS, Terraform/CDK 변경
- 위치·사진·알림 외부 서비스 연동
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Compose·profile·Gradle·통합 테스트 | 현재 작업 에이전트 | Backend owners |
| 제품 스키마·영속성 선택 | 후속 기능 Issue | Backend owners |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- Issue #30 merge 후 최신 `main`에서 브랜치를 생성했다.

## Validation

```bash
docker compose config
docker compose up -d db
docker compose ps
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT PostGIS_Version();"'

./gradlew check
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] `.env.example`만으로 필요한 환경변수 이름과 placeholder를 알 수 있다.
- [x] Compose DB가 named volume과 health check로 재실행 가능하게 구성된다.
- [x] local profile이 추적되지 않는 환경변수로 PostgreSQL에 연결된다.
- [x] test profile이 Testcontainers DB를 사용하고 개발자 로컬 DB에 의존하지 않는다.
- [x] 통합 테스트가 PostgreSQL 연결과 PostGIS 확장을 확인한다.
- [x] 시작·중지·초기화·검증 명령과 볼륨 삭제 위험이 문서화된다.
- [x] Java 21에서 Gradle, Harness, Hook, diff 검증이 모두 통과한다.
- [x] 제품 스키마, JPA, Flyway, 운영 인프라가 포함되지 않는다.
