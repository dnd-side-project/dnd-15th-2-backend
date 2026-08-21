# GitHub Issue #178 Task Contract

> Generated at: `2026-08-21T16:31:50+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `알림 설정 API와 방해 금지 시간`
- GitHub Issue: `#178`
- Branch: `feat/gh-178-notification-preferences`
- Base branch: `main`
- 선행 이슈: `#176`, 관련 fan-out 확장 `#177`은 `main` 반영 완료.
- Product design: `docs/product/NOTIFICATION_INBOX_DESIGN.md` §1, §3, §10, §12-2.
- Test plan: `docs/test-plans/gh-178-TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES.md`
  (`Status: Approved`, 2026-08-21 사용자 구현 승인).
- Planning approval: 2026-08-21 사람 승인. 기존 quiet 값은 아직 배포되지 않은
  데이터이므로 이관하지 않고 신규 사용자 단위 계약으로 교체한다.

## Objective

- 사용자가 앱 푸시 전체 on/off, 알림 6종별 on/off와 사용자 공통 방해 금지 시간을
  본인 계정에서 조회·변경할 수 있게 한다.
- 푸시 설정과 알림함 원장을 분리해 어떤 설정에서도 `notification`은 남기고 실제
  `notification_delivery` 생성만 억제한다.
- 종류별 행마다 중복된 quiet 필드를 사용자 단위 설정으로 분리한다.

## Scope

1. `notification_user_setting`을 추가한다. 사용자별 `push_enabled`, `quiet_start`,
   `quiet_end`, `quiet_zone_id`, `updated_at`을 저장하며 행이 없으면 전체 푸시 ON,
   방해 금지 OFF로 해석한다.
2. `notification_preference`에서는 `quiet_start`, `quiet_end`와
   `ck_notification_preference_quiet_hours`를 제거한다. 기존 quiet 값은 이관하지 않고
   종류별 `enabled` 값만 보존한다.
3. `NotificationPreference`에서 quiet 필드를 분리하고 사용자 공통 설정과 quiet value
   object를 추가한다.
4. `GET /api/v1/notifications/preferences`로 전역 설정, 6종 설정, quiet 설정과
   `inboxRecordingPolicy=ALWAYS_RECORD`를 반환한다. 저장 행이 없는 종류는 ON이다.
5. `PUT /api/v1/notifications/preferences`는 인증 사용자의 전역 설정, 정확히 6종의
   설정과 quiet 설정을 한 트랜잭션에서 완전 교체하고 canonical 응답을 반환한다.
6. 전체 푸시 OFF는 종류별 선택을 덮어쓰지 않는다. 전체를 다시 켜면 이전 종류별
   선택이 복원된다.
7. quiet 설정은 시작·종료·IANA Zone ID를 모두 지정하거나 모두 비운다. 시작은 포함,
   종료는 미포함이며 자정 통과를 허용하고 같은 시작·종료는 400이다.
8. fan-out의 effective push gate를 `globalEnabled && typeEnabled`로 바꾼다. gate는
   `notification` 저장 뒤 `notification_delivery` 생성 직전에 유지한다.
9. 신규 요청 오류는 `NOT-VAL-008`로 매핑하고 field/reason으로 세부 원인을 구분한다.
10. Flyway, domain/repository/service/web, OpenAPI, ERD/DBML/schema manifest와 JUnit 5
    단위·PostgreSQL 통합 테스트를 함께 갱신한다.
11. V26 이후에도 기존 preference 저장 bridge가 quiet 컬럼을 참조하지 않도록 SQL을
    함께 갱신하고, 실제 repository 저장 회귀를 검증한다.

## Approved design decisions

- 기본값: 전체 푸시 ON, 6종 모두 ON, 방해 금지 OFF.
- 전체 OFF: 종류별 설정을 보존하고 모든 신규 delivery 생성만 막는다.
- PUT concurrency: 사용자 설정 단위로 직렬화하고 마지막으로 lock을 획득한 완성
  snapshot이 남는다. 부분 snapshot은 허용하지 않는다.
- quiet 표현: `quietHours=null` 또는 start/end/zoneId가 모두 있는 object.
- 기존 quiet 데이터: 미배포 상태이므로 이관·충돌 판정 없이 제거한다.
- 이미 만들어진 PENDING/FAILED delivery: 이 이슈에서 삭제·취소하지 않는다. `#179`
  발송기가 발송 직전에 최신 설정을 재검사해야 한다.

## Explicit exclusions

- 방해 금지 시간의 실제 발송 억제·묶음·일 상한 — `#180`.
- Push provider 호출, 토큰 등록, 발송 스케줄링과 발송 직전 preference 재검사 —
  `#179`, `#182`.
- 운영체제 알림 권한과 클라이언트 UI 구현.
- 기존 quiet 값의 이관. 아직 배포되지 않은 컬럼의 값은 신규 계약의 근거로 사용하지 않는다.
- 이미 생성된 notification 삭제·REVOKED 전이 또는 delivery 일괄 취소.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Flyway·데이터 모델 문서 | Database executor | `enabled` 보존, quiet 컬럼 제거, 신규 CHECK/FK, migration 재실행 안전성 |
| preference domain·repository·service | Notification executor | sparse defaults, complete snapshot, transaction·concurrency, account eligibility |
| web·OpenAPI | API executor | 인증 subject 전용, 6종 완전성, 400/401/403/404 계약, 민감정보 비노출 |
| fan-out gate | Fan-out verifier | notification 선저장, global/type OFF delivery 0, 기존 retry·dedup 회귀 없음 |

## Existing user-owned changes

- 2026-08-21 작업 시작 시 `main`은 `origin/main`과 일치했고
  `git status --short`는 clean이었다.
- `./harness start`와 `task-init` 이후 생긴 `TASK.md`·테스트 계획·구현 계획만 #178이
  소유한다. 범위 밖 변경은 정리하거나 되돌리지 않는다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreference*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*NotificationFanOut*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

### Task 7 verification evidence (2026-08-21)

- `./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES` 실행 결과 `FAIL`.
  `FlywayMigrationContractTest.migrationsMatchAcceptedContent`가 실제 목록의
  `V26__split_notification_user_setting.sql`을 기존 expected migration 목록에서
  누락한 구현·테스트 계약 불일치를 재현했다(859개 완료, 1개 실패, exit 1).
- 위 구현 실패로 지시된 후속 단위·PostgreSQL 통합·fan-out·OpenAPI·저장소 완료 검증
  명령은 실행하지 않았다. 따라서 완료 체크박스는 유지하며 PASS로 표시하지 않는다.
- 상세 증거: `docs/test-reports/gh-178-TEST-REPORT-GH-178-NOTIFICATION-PREFERENCES.md`.
- migration expected 목록을 보강한 뒤 동일 harness 명령을 재실행했다. unit 단계는
  성공했으나 integration 단계에서 `AccountPersistenceIntegrationTest`가
  `expected: 50 but was: 51`로 실패했다. V26의 `notification_user_setting` 신규
  테이블을 기존 table-count assertion이 반영하지 않은 구현·테스트 계약 불일치다.
- 두 번째 구현 실패로 승인된 targeted unit/integration·fan-out·OpenAPI 및 저장소 완료
  검증 명령은 다시 실행하지 않았다.
- 두 stale contract를 보강한 뒤 승인된 순서로 최종 재실행했다. harness test-run,
  notification unit 147개, NotificationPreference integration 9개, fan-out integration
  44개, OpenAPI integration 10개, `./harness check`, `npm run hooks:validate`,
  `git diff --check`가 모두 성공했다.
- V26 rebase 후 승인된 순서를 다시 실행했다. 전체 harness test-run은 5분 25초,
  `./harness pr-ready --project-tests`는 전체 check를 포함해 5분 33초에 성공했고
  `Local PR readiness checks passed`를 확인했다. `harness check`, hooks, diff도
  성공했다. 상세 증거는 테스트 보고서에 누적했다.

## Completion criteria

- [x] 설정 행이 없는 사용자는 전체 ON, 6종 ON, quiet OFF로 조회된다.
- [x] 전체 OFF 후 다시 ON해도 종류별 선택값이 보존된다.
- [x] quiet 시작·종료·Zone ID 중 일부만 있거나 시작과 종료가 같으면 400이다.
- [x] 자정 통과 quiet 구간이 저장·조회된다.
- [x] PUT은 정확히 6종을 중복 없이 받고 실패 시 어떤 설정도 부분 반영하지 않는다.
- [x] migration이 기존 `notification_preference.enabled` 값을 보존하고 quiet 컬럼·CHECK를 제거한다.
- [x] global 또는 type OFF에서도 `notification`은 1건이고 신규 delivery는 0건이다.
- [x] 인증 subject 외 사용자 식별자를 조회·변경 입력으로 받지 않는다.
- [x] 응답은 `inboxRecordingPolicy=ALWAYS_RECORD`를 명시한다.
- [x] 단위·MockMvc 18개와 PostgreSQL 통합 13개 계획의 P0가 모두 통과한다.
- [x] 모든 신규 테스트에 `@DisplayName`과 정확한 ISO 8601·Source scenario 헤더가 있다.
- [x] 테스트 보고서와 잠재 문제 분석을 작성한다.
- [x] 완료 전 검증을 모두 실행하고 실패·미실행 범위를 구분해 기록한다.
