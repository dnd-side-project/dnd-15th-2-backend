# GitHub Issue #78 Task Contract

> Generated at: `2026-08-07T18:14:50+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `V7 마이그레이션과 매핑 갱신 — 2026-08-07 스키마 개정 반영`
- GitHub Issue: `#78`
- Branch: `feat/gh-78-schema-revision-v7`
- Base branch: `main`

## Objective

- 2026-08-07 제품 개정(답변 격리 폐기)으로 기능 명세서·ERD·DBML이 바뀌었으나 저장소의
  스키마 계약 문서 3종은 8/4 판에 멈춰 있다. `answer_reaction`의 `answer_id` 단독 PK는
  두 번째 사용자의 공감을 PK 충돌로 거부하므로, 후속 두 이슈(#79, #80)의 선행 조건이다.
- `V7`이 `post_recipient`와 `answer`에 `NOT NULL` 컬럼을 추가하므로 매핑 갱신을 함께
  포함한다. `JdbcPostRecipientRepository`의 INSERT 컬럼 목록과 `AnswerJpaEntity`가 그대로면
  마이그레이션 직후 기존 쓰기 경로가 깨진다. #54가 V2 마이그레이션과 매핑 갱신을 묶었던
  것과 같은 이유다.
- PR #81(#73)이 `main`에 먼저 merge되어 `device_credential`을 `V7`로 점유했다. 이
  브랜치의 마이그레이션은 `V8`로 재번호를 매기고, Flyway 카탈로그 잠금 값은 두
  마이그레이션이 모두 적용된 실제 DB에서 재측정한다.

## Scope

- `docs/product/data-model/direction_communication.dbml` — 원본 최신본으로 갱신
- `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` — 원본 최신본으로 갱신
- `docs/product/data-model/schema-manifest.md` — SHA-256 3개, snapshot date, V7·V8 항목 갱신
- `V8__…sql` 신규 migration (원래 `V7`로 작성했으나 #81의 `V7__add_device_credential.sql`과
  충돌해 재번호)
  - `answer_reaction` PK `answer_id` → `(answer_id, reactor_id)`
  - `ct_answer_reaction_reactor_is_sender` → `ct_answer_reaction_reactor_can_view`
    (질문자 또는 수신자 집합 소속, 자기 답변 금지)
  - `post_recipient` +`inbound_bearing_deg NUMERIC(7,3) NOT NULL`,
    +`distance_m BIGINT NOT NULL`, +`answers_read_at TIMESTAMPTZ`
  - `answer` +`distance_m BIGINT NOT NULL`, +`edited_at TIMESTAMPTZ`,
    +`edit_count INTEGER NOT NULL DEFAULT 0`
    (안전 상한 10, `(edit_count = 0) = (edited_at IS NULL)` 동치 CHECK)
  - `uq_answer_one_per_recipient` 조건 `status NOT IN ('REJECTED','DELETED')`
    → `status <> 'REJECTED'`
  - 폐기된 COMMENT 갱신
- 매핑 갱신
  - `JdbcPostRecipientRepository` INSERT/UPDATE 컬럼, `PostRecipient` 도메인 필드
  - `AnswerJpaEntity`, `Answer` 도메인
  - `AnswerReactionJpaEntity` 복합키(`@IdClass`), `AnswerReactionRepository` 시그니처
- `FlywayMigrationContractTest` 잠금 값 갱신 (`V8` 기준)
- `schema-manifest.md`에 `device_credential`(#81, `V7`) 항목 반영

## Explicit exclusions

- 자격 판정 service 로직과 feed 조회 계층 — #79
- 수신함 방향 칩 집계 — #80
- 답변 수정 쓰기 경로 — 이번 회차에서 다루지 않는다. 컬럼과 제약만 만든다.
- controller, DTO, API 문서 — 이번 회차는 API 계층을 만들지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TODO | TODO | TODO |

## Existing user-owned changes

- 브랜치 생성 시점의 `git status --short`는 비어 있었다. 보존할 기존 변경이 없다.
- PR #81(#73)이 `main`에 merge된 뒤 `./harness sync`로 rebase하며 발생한 `V7` 번호
  충돌은 이 브랜치의 마이그레이션을 `V8`로 재번호해 해결했다. #81의 변경 내용은
  손대지 않았다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 한 답변에 서로 다른 두 사용자가 공감을 남길 수 있다. (`ReactionPersistenceIntegrationTest.senderAndEligibleRecipientCanBothReactToTheSameAnswer`)
- [x] 같은 사용자가 같은 답변에 두 번 공감하면 거절된다. (복합 PK, `ReactionPersistenceIntegrationTest.cancellingAnAnswerReactionDeletesTheRow`가 취소 후 재반응 경로를 함께 검증)
- [x] 자기 답변에 남긴 공감을 `ct_answer_reaction_reactor_can_view`가 거절한다. (`ReactionPersistenceIntegrationTest.theAnswerAuthorCannotReactToTheirOwnAnswer`)
- [x] 답변을 삭제한 뒤 같은 `post_recipient`로 새 답변을 만들 수 없다. (`AnswerSafetyNotificationPersistenceIntegrationTest.deletedAnswerNoLongerFreesTheSlot`)
- [x] `(edit_count = 0) = (edited_at IS NULL)` 위반과 `edit_count > 10`이 거절된다. (도메인: `AnswerPersistenceBoundaryTest.requiresEditCountAndEditedAtToAgree`, DB: `AnswerSafetyNotificationPersistenceIntegrationTest.rejectsEditCountEditedAtViolationsAtTheDatabaseLevel`)
- [x] `NOT NULL` 신규 컬럼의 기존 행 백필 경로가 migration에 포함돼 있다. (`SchemaRevisionMigrationIntegrationTest.v7BackfillsLegacyRowsWithConstraintSatisfyingPlaceholders`. 백필 값은 정확한 값이 아니라 제약을 만족하는 placeholder다 — 근거는 `V8__…sql` 상단 주석과 테스트 보고서 §6 참고)
- [ ] `schema-manifest.md`의 SHA-256이 실제 파일과 일치한다. (DBML·ERD·target DDL 3곳 갱신, `V8` 기준 재검증 + `device_credential`(#81) 반영 필요 — rebase 진행 중)
- [ ] `FlywayMigrationContractTest`와 기존 통합 테스트가 통과한다. (`V8` 리네이밍과 두 마이그레이션 합산 카탈로그 카운트 재측정 필요 — rebase 진행 중)
