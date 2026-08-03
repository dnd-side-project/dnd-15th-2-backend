# GitHub Issue #30 Task Contract

> Generated at: `2026-08-03T15:37:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Spring Boot Package-by-Feature 및 3계층 기반 구성`
- GitHub Issue: `#30`
- Branch: `build/gh-30-package-foundation`

## Objective

- Qello 백엔드의 기본 Java 패키지를 제품명 기준으로 정리한다.
- 하나의 Spring Boot 애플리케이션에서 Package-by-Feature와 기능 내부
  Controller → Service → Repository 3계층 규칙을 확정한다.
- 후속 기능과 로컬 DB 작업이 같은 구조에서 시작할 수 있는 최소 기반을 만든다.

## Scope

- `com.dnd.backend`를 `com.dnd.qello`로 이동
- `account`, `question`, `direction`, `feed`, `safety`, `notification` 기능 패키지
  책임 정의
- 기능 내부 Controller, Service, Repository와 보조 `domain`·DTO 책임 정의
- 기능 간 Controller, Repository, Entity 직접 참조 금지 규칙 정의
- `common` 패키지의 최소 허용 범위 정의
- `local`, `test` profile 골격과 context-load 회귀 테스트
- 로컬 작업 계획과 실행 결과 동기화

## Explicit exclusions

- Spring Modulith 또는 Gradle 멀티모듈 도입
- 제품 Controller, Service, Repository, Entity 구현
- 데이터베이스 의존성, DDL, Flyway 마이그레이션, Docker Compose
- 인증, 위치 계산, 알림, 콘텐츠 안전 외부 연동 구현
- AWS 리소스와 `infra/**` 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Java 기본 패키지·기능 패키지 | 현재 작업 에이전트 | Backend owners |
| Profile·통합 테스트 | 현재 작업 에이전트 | Backend owners |
| PostgreSQL/PostGIS 로컬 환경 | Issue #31 | Backend owners |

## Existing user-owned changes

- 작업 시작 시 worktree는 clean 상태였다.
- `docs/reports/**/*.local.md` 계획 문서는 Git에서 제외된 사용자 작업 문서로
  보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 애플리케이션과 테스트의 기본 패키지가 `com.dnd.qello`다.
- [x] Package-by-Feature와 기능 내부 3계층 책임이 코드에 기록된다.
- [x] 기능 간 허용·금지 의존성이 명확하다.
- [x] 빈 기능 구현이나 제품 API·DB 스키마가 추가되지 않는다.
- [x] `local`, `test` profile에서 Spring Context가 로드된다.
- [x] Java 21에서 전체 검증이 통과한다.
