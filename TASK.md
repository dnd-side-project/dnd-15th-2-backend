# GitHub Issue #168 Task Contract

> Generated at: `2026-08-19T00:30:48+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `닉네임 등록·수정 API — 중복 검사와 moderation 연동`
- GitHub Issue: `#168`
- Branch: `feat/gh-168-nickname-duplicate-moderation`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION`
- Test plan approval: `APPROVED` — 사용자가 2026-08-19 계획과 §11 결정 사항(자기 자신과의 중복도 409, 오류 코드 3종, PATCH 경로)을 승인했다.

## Objective

닉네임 설정·변경이 실제로 반영되기 전에 중복 검사와 moderation 판정을 통과하도록
한다. #106이 만든 `NicknameSyncModerationGate`는 아직 어떤 호출 지점에도
연결되지 않았고, `DeviceRegistrationService.register()`는 닉네임을
중복·moderation 검증 없이 그대로 저장한다. 닉네임 변경 API도 아직 없다.

테스트 계획 작성 중 `ModerationPipelineService`가 의존하는 `TextNormalizer`·
`LocalRuleEngine`·`PolicyEngine`과 `SecondaryModerationClient`가 저장소 전체에
프로덕션 구현체가 하나도 없다는 사실을 확인했다(#103~#113 모두 "실제 내용은
이 이슈의 범위가 아니다"로 반복 유보). 사용자가 2026-08-19 최소 실제 구현체를
함께 만드는 방향으로 승인해 범위에 포함했다.

## Scope

- `user_account.nickname`에 대소문자를 구분하지 않는 유일성 제약 추가(삭제된
  계정 제외 partial unique index) — 신규 Flyway 마이그레이션(V21).
- `AccountErrorCode.DUPLICATED_NICKNAME` 추가, `ConstraintExceptionMapper`에
  새 제약 이름 매핑 추가.
- 최소 실제 moderation 구현체 3종: `TextNormalizer`(trim 통과),
  `LocalRuleEngine`(항상 `noMatch()`), `PolicyEngine`(flagged 카테고리가
  하나라도 있으면 BLOCK).
- `SecondaryModerationClient` fail-closed placeholder(즉시
  `SECONDARY_MODERATOR_UNAVAILABLE`).
- OpenAI moderation 호출용 `RestClient` 설정 — API 키는 환경 변수로만 주입.
- `NicknameSyncModerationGate` 구성 Spring 설정 — `qello.filtering.production.enabled`
  (#113)가 `true`일 때만 게이트 빈을 등록한다. 꺼져 있으면 닉네임 경로는 중복
  검사만 수행한다(ASSUMED).
- `DeviceRegistrationService.register()`와 신규 닉네임 변경 유스케이스에 중복
  검사 + (게이트가 있으면) moderation 연결.
- `account/web` 신규 패키지 — `PATCH /api/v1/users/me/nickname` +
  `AccountController`/`AccountApiSpec` + request/response record.
- `docs/api/openapi.json` 재생성, `docs/error-codes.md` ACC 절 갱신.

## Explicit exclusions

- moderation 실행 자원의 실제 timeout·quota·동시성 수치(`UNKNOWN`, #106과 동일).
- `PolicyEngine`의 카테고리별 세부 threshold·언어별 차등 정책, `LocalRuleEngine`의
  실제 로컬 사전·패턴 — 최소 동작 버전만 제공한다.
- 독립 보조 판정기의 실제 공급자 확정 — fail-closed placeholder만 추가한다.
- 닉네임 변경 빈도 제한(rate limit).
- 프로필 이미지 등 닉네임 외 다른 프로필 필드 변경(#166에서 별도로 다룬다).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `Account`/`AccountRepository` 확장, `account/web`, `SafetyReportService`류 패턴을 따르는 닉네임 서비스, moderation 최소 구현체·Spring 설정, 단위·통합 테스트 | Feature executor | #106 `INV-NICK` 계약 회귀 확인, #113 production gate 조건부 등록 검증, 기존 `DeviceRegistrationService` 동작과의 호환성 리뷰 |

## Existing user-owned changes

- `git status --short` 결과 없음(clean). `main`에서 새로 분기했다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.account.*" --console=plain
./gradlew test --tests "com.dnd.qello.filtering.moderation.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NicknameDuplicateModerationIntegrationTest" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 이미 존재하는(대소문자 무시) 닉네임으로 기기 등록을 시도하면 계정이
      생성되지 않고 `ACC-APP-002 DUPLICATED_NICKNAME`을 반환한다 —
      `DeviceRegistrationServiceTest#rejectsRegistrationWhenNicknameAlreadyExists`,
      `NicknameDuplicateModerationIntegrationTest#secondRegistrationWithDuplicateNicknameDoesNotCreateAccount`.
- [x] production gate가 켜진 상태에서 moderation이 BLOCK을 반환하면 등록·변경
      모두 거부된다 — `NicknameModerationGateConfigTest`(빈 등록 조건),
      `NicknameDuplicateModerationIntegrationTest#changeNicknameFailsWhenModerationBlocks`.
      실제 OpenAI HTTP 왕복 자체는 검증하지 못했다(보고서 §6 External APIs).
- [x] moderation이 판정 불가(주·보조 모두 실패)면 등록·변경 모두 반영되지
      않는다(#106 `INV-NICK-001`~`005` 계약 유지) —
      `NicknameDuplicateModerationIntegrationTest#changeNicknameFailsWhenModerationIsUnavailable`.
- [x] production gate가 꺼진 상태에서는 moderation 호출 없이 중복 검사만
      수행되고 정상 등록·변경된다 —
      `NicknameRegistrationServiceTest#passesWithDuplicateCheckOnlyWhenGateIsNoOp`,
      `NicknameModerationGateConfigTest#noGateBeanWhenProductionDisabled`.
- [x] 닉네임 변경 API가 중복 검사와(게이트가 있으면) moderation을 모두 통과한
      뒤에만 `Account.updateProfile`을 호출한다 —
      `NicknameRegistrationServiceTest#savesNewNicknameWhenModerationAllows`.
- [x] 서로 다른 두 요청이 동시에 같은 닉네임을 선점하려 하면 DB 유일성 제약이
      최종 방어선으로 동작한다 —
      `NicknameDuplicateModerationIntegrationTest#concurrentRegistrationsWithCaseVariantNicknameYieldExactlyOneWinner`(2-way).
- [x] `docs/api/openapi.json`이 재생성돼 있다 — `/api/v1/users/me/nickname` 반영 확인.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 상세는
      `docs/reports/tests/gh-168-TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION.md`
      §6·§7 참고. release 실시간 재로딩 미지원, OpenAI 실제 HTTP 왕복 미검증,
      N-way(3+) 동시성 미실측, 등록 경로의 트랜잭션 내 외부 호출 트레이드오프.
