# GitHub Issue #103 Task Contract

> Generated at: `2026-08-11T11:16:28+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `필터링 시스템 — 도메인 경계와 정합성 기반 (Foundation, F00)`
- GitHub Issue: `#103`
- Branch: `feat/gh-103-filtering-foundation`
- Base branch: `main`

## Objective

- 필터링 시스템 전체(#104~#113)가 의존하는 도메인 모델과 정합성 기반을 고정한다.
  answer·user_account 도메인은 소유하지 않고, 그 도메인이 호출할 판정 진입점과
  콜백 계약만 소유한다.

## Scope

- `com.dnd.qello.filtering` 패키지: `FilterTarget`(opaque target reference),
  `FilterJob`(authority 상태 머신), `FilterDecision`(append-only attempt 원장),
  `FilterRelease`(최소 식별자), `ManualReviewCase`/`AppealCase`(정체성·유일성만)
- `FilterJobStatusHistoryEntry` — 상태 변경 감사 이력
- `V10__create_filtering_schema.sql` — filter_release/filter_job/
  filter_job_status_history/filter_decision/manual_review_case/appeal_case
- 각 aggregate의 repository 포트 + JPA 영속화(entity/mapper/jpa repo/spring data repo)
- `FilteringErrorCode`/`FilteringException`

## Explicit exclusions

- `answer`, `user_account`(Account) 테이블·엔티티·상태 머신 수정
- 답변 편집(edit)과 revision 이력 모델 — 편집 기능이 결정되기 전까지 범위 밖
- moderation release registry의 실제 정책 pipeline(#104), 공통 pipeline
  실행(#105), 닉네임/답변 실제 연동(#106, #107) — 이 이슈는 도메인 기반만 만든다
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `filtering` 도메인·영속화·마이그레이션 | Feature executor | authority 상태 전이(attemptGeneration, manuallyResolved), DB 유일성 제약 |

## Existing user-owned changes

- 브랜치 생성 시점 `git status --short`: `?? docs/frontend/` (기존 `#73` 작업의
  미커밋 문서, 이 브랜치와 무관). `git stash`로 보관 후 브랜치를 만들었다
  (`feat/gh-73-device-credential-token`용 stash 항목, 이 브랜치에는 없음).
- 이 브랜치에서 새로 만든 변경 외에 보존해야 할 기존 변경은 없다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- `./harness check` 통과 (secret/JUnit 정책/컨벤션/label/husky).
- `./gradlew test --tests "com.dnd.qello.filtering.*"` — 단위 테스트 16개 통과.
- `./gradlew integrationTest --tests "com.dnd.qello.FilteringPersistenceIntegrationTest"` — 7개 통과.
- V10 추가로 인해 마이그레이션 개수·목록에 의존하던 기존 테스트
  (`AccountPersistenceIntegrationTest`, `FlywayMigrationIntegrationTest`)를
  같은 브랜치에서 함께 갱신하고 재실행해 통과를 확인했다.
- `./gradlew check` 전체 실행 결과는 PR 본문에 최종 기록한다.

## Completion criteria

- [x] 동일 이벤트를 반복 적용해도 상태와 case가 중복 생성되지 않는다 — DB 유일성
      제약(`uq_filter_job_idempotency_key`, `uq_filter_decision_job_attempt`,
      `uq_manual_review_case_target`, `uq_appeal_case_target_decision`) +
      통합 테스트로 검증.
- [x] 오래된 target reference 또는 비권위 attempt 결과가 호출자 도메인에 잘못된
      콜백을 보내지 않는다 — `FilterJob.applyAutomatedDecision`의
      `STALE_ATTEMPT_GENERATION`/`ALREADY_MANUALLY_RESOLVED` 가드로 검증.
- [x] moderation workflow 상태를 호출자 도메인과 독립적으로 조회할 수 있다 —
      `filtering` 패키지가 `answer`/`user_account` 테이블을 전혀 참조하지 않는
      구조로 자연히 만족한다.
- [x] `INV-GEN-001`~`INV-GEN-007`을 위반하지 않는다.
- [ ] `#104`(F01) 이후 하위 패키지가 이 기반 위에서 실제로 동작하는지는 그
      이슈들에서 별도로 검증한다 — 이 이슈의 완료 기준이 아니다.
