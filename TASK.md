# GitHub Issue #48 Task Contract

> Generated at: `2026-08-04T17:51:32+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `JPA 엔티티에 Lombok 적용 및 user_account 비밀번호 컬럼 추가`
- GitHub Issue: `#48`
- Branch: `refactor/gh-48-migrate-lombok`

## Objective

현재 JPA 엔티티의 반복적인 생성자·getter 코드를 Lombok으로 개선하고, 계정
인증에 필요한 비밀번호 해시 저장 컬럼을 `user_account`에 추가한다.

## Scope

### Lombok

- 적용 범위: `Account` 도메인과 관련 `AccountJpaEntity`,
  `JpaAuditableEntity`(공통 상위 클래스)로 한정한다. Issue의 "Lombok 적용
  대상 엔티티 범위" 블로커는 사용자 확인을 거쳐 Account만으로 확정했다.
  `Answer`/`ApprovedQuestion`/`QuestionAssignment*`/`QuestionProposal*` 등
  나머지 JPA 엔티티는 이번 이슈 범위 밖이며 별도 이슈로 남긴다.
- `@Getter(AccessLevel.PACKAGE)` + `@NoArgsConstructor(AccessLevel.PROTECTED)`
  패턴을 사용하고 `@Data` 등 무분별한 전체 생성 애노테이션은 사용하지 않는다.
- Hibernate가 사용할 protected 기본 생성자는 유지한다.

### password_hash

- `user_account.password_hash` 컬럼을 기존 V1 migration을 수정하지 않고
  `V3__add_user_account_password_hash.sql`로 신규 추가한다. (main에 #54로
  `V2__add_reactions_and_skip_pending.sql`이 먼저 병합되어 버전 3을 사용한다.)
- `role = 'OPERATOR'`는 password_hash 필수, `role = 'USER'`는 항상 NULL인
  것을 DB check constraint(`ck_user_account_password_hash`)로 강제한다.
- 해시 알고리즘은 Spring Security `BCryptPasswordEncoder`(bcrypt)로 확정했다.
  `account.security` 패키지에 `PasswordHasher`/`RawPassword`/
  `BCryptPasswordHasher`를 두고, 평문 `RawPassword`는 `toString()`을
  `REDACTED`로 재정의해 로그 노출을 막는다.
- Domain(`Account`, `PasswordHash`) ↔ JPA Entity ↔ Mapper 왕복 매핑과 기존
  Account persistence 테스트를 회귀 검증한다.

## Explicit exclusions

- Lombok을 Account 외 JPA 엔티티로 확장하는 작업(별도 이슈로 분리)
- 인증 API, 로그인 흐름, 세션/토큰 발급 등 실제 인증 기능 구현
- 기존 V1 Flyway migration 수정
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Account 도메인/Lombok 적용 | Account executor | JPA 매핑·auditing 회귀 리뷰 |
| password_hash migration/security 패키지 | Account executor | 평문 비노출·check constraint 리뷰 |
| 단위/통합 테스트 | Test orchestrator | Account persistence 회귀 리뷰 |

## Existing user-owned changes

- 세션 시작 시 작업 트리에는 이전 세션에서 만든 Account/Lombok/password_hash
  구현이 이미 존재했다(미커밋 상태). `git status --short`에는 계정 모듈 외
  약 170개 파일이 수정된 것으로 표시되지만, 실제 원인은 이전 세션 중 발생한
  전체 저장소 CRLF 개행 오염이었다. `git diff -w`/`--ignore-space-at-eol`로
  확인해 개행만 다른 파일은 LF로 되돌렸다(`sed -i 's/\r$//'`, 내용 변경 없음,
  바이트 단위로 HEAD와 동일함을 `md5sum`으로 검증).
- WSL DrvFs(`/mnt/c`) 마운트 특성상 `git status`가 개행 정리 이후에도 해당
  파일들을 계속 modified로 오탐지하는 stat-cache 이슈가 남아 있다. 실제
  변경 여부는 `git status`가 아니라 `git diff`(내용 비교)로 판단한다.
- 실제로 이번 작업으로 변경된 파일은 Account 도메인/보안 패키지, V2
  migration, 관련 테스트, `build.gradle`, `docs/product/data-model/
  direction_communication.dbml`(주석 변경) 정도로 한정된다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

Testcontainers 기반 통합 테스트(`AccountPersistenceIntegrationTest`)는 이 실행
환경에 Docker가 없어 로컬에서 실행하지 못했다. 단위 테스트(`./gradlew test
--tests "com.dnd.qello.account.*"`)는 통과를 확인했으며, 통합 테스트는 Docker
가용 환경에서 별도 검증이 필요하다.

## Completion criteria

- [x] Lombok 의존성 및 annotation processor 설정 (`build.gradle`)
- [x] 대상(Account) JPA 엔티티에 Lombok 적용
- [x] 기존 JPA 매핑 및 auditing 동작 유지 (단위 테스트로 회귀 확인)
- [x] `user_account.password_hash` 신규 migration 추가 (V1 미수정)
- [x] 비밀번호는 해시 값으로만 저장 (bcrypt, `RawPassword` 평문 미보관)
- [x] Domain ↔ JPA Entity ↔ Mapper 왕복 매핑 테스트 추가
- [x] 기존 Account persistence 테스트 회귀 검증(단위 테스트 통과)
- [x] 기존 데이터 nullable/초기값 정책 반영 (USER=NULL, OPERATOR=필수, check
      constraint)
- [x] 비밀번호 원문이 로그·예외·테스트 출력에 노출되지 않음
- [ ] Flyway 및 Gradle 테스트 전체 통과 — 단위 테스트는 통과, Testcontainers
      통합 테스트는 Docker 미가용으로 미검증
- [ ] `./harness check`
- [ ] `./harness pr-ready --project-tests`
- [ ] `npm run hooks:validate`
- [ ] `git diff --check`
