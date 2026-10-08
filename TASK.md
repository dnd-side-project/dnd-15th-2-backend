# GitHub Issue #332 Task Contract

> Generated at: `2026-10-08T02:12:32+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `알림함 전체 지우기와 30일 보존 기간 숨김`
- GitHub Issue: `#332`
- Task ID: `GH-332-NOTIFICATION-CLEAR`
- Branch: `feat/gh-332-notification-clear-retention`
- Base branch: `main`
- Source: 2026-10-08 현재 세션 사용자가 승인한 채팅 설계를 Issue로 생성하고 Project 141에 연결
- Test plan: `docs/test-plans/gh-332-TEST-PLAN-GH-332-NOTIFICATION-CLEAR.md`
- Test report: `docs/reports/tests/gh-332-TEST-REPORT-GH-332-NOTIFICATION-CLEAR.md`
- Design ID: 해당 없음
- Implementation gate: 테스트 계획 사람 승인 완료; 승인된 범위의 구현·테스트 작성·실행 허용
- Approval evidence: 2026-10-08 현재 세션 사용자 메시지 “어 승인해” (기록 시각: `2026-10-08T02:48:27+09:00`)
- Execution status: `PASS` — 승인 범위 구현, sync 후 필수 검사, 독립 검증 지적 사항 반영 완료. 사람의 GitHub PR 승인을 대체하지 않는다.

## Objective

- 사용자가 알림함을 한 번에 비우고, 생성 후 30일이 지난 알림은 목록과 배지에서 자동으로 빠지게 한다.

## Scope

- `DELETE /api/v1/notifications` 추가, 응답 `{ dismissedCount, dismissedAt }`.
- 서버 시각 이전에 생성된 본인 `UNREAD`·`READ` 알림을 UPDATE 한 번으로 `DISMISSED`로 전이. `read_at`은 바꾸지 않는다.
- `Notification.markRead`가 `DISMISSED`를 `READ`로 바꾸지 않게 하고, 서비스는 `UNREAD`일 때만 update를 호출한다.
- `qello.notification.inbox.retention`(기본 `P30D`) 설정 추가.
- 목록, `COUNT_UNREAD`, `EXISTS_UNSEEN`에 `created_at > :retentionFloor` 조건 추가. 하한은 서비스가 `clock.instant() - retention`으로 계산한다.
- 생성 OpenAPI 갱신.

## Design and allowed files

전체 지우기는 기존 `DISMISSED` 상태와 부분 인덱스를 그대로 사용하고 스키마를 바꾸지 않는다.
보존 기간은 조회 시 필터만 적용하며 행을 삭제하지 않는다. `/target`과 `findCard`는 ID 조회이므로 필터를 적용하지 않는다.

Production:
- `src/main/java/com/dnd/qello/notification/web/NotificationApiSpec.java`
- `src/main/java/com/dnd/qello/notification/web/NotificationController.java`
- `src/main/java/com/dnd/qello/notification/web/response/NotificationDismissResponse.java` (신규)
- `src/main/java/com/dnd/qello/notification/view/NotificationDismissal.java` (신규)
- `src/main/java/com/dnd/qello/notification/service/NotificationInboxService.java`
- `src/main/java/com/dnd/qello/notification/domain/Notification.java`
- `src/main/java/com/dnd/qello/notification/config/NotificationInboxProperties.java` (신규)
- `src/main/java/com/dnd/qello/notification/repository/NotificationRepository.java`
- `src/main/java/com/dnd/qello/notification/repository/jdbc/JdbcNotificationRepository.java`
- `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationSql.java`
- `src/main/java/com/dnd/qello/notification/repository/NotificationInboxQueryRepository.java`
- `src/main/java/com/dnd/qello/notification/repository/jdbc/JdbcNotificationInboxQueryRepository.java`
- `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationInboxQuerySql.java`
- `src/main/resources/application.yml`
- `config/java-conventions/baseline.json` (JAVA-CONV-0007·0014 항목 삭제만 허용)

Tests (신규):
- `src/test/java/com/dnd/qello/notification/NotificationDismissTest.java`
- `src/test/java/com/dnd/qello/notification/config/NotificationInboxPropertiesTest.java`
- `src/integrationTest/java/com/dnd/qello/NotificationInboxDismissRetentionIntegrationTest.java`
- `src/integrationTest/java/com/dnd/qello/NotificationInboxDismissConcurrencyIntegrationTest.java`

Tests (수정):
- `src/test/java/com/dnd/qello/notification/service/NotificationInboxServiceTest.java`
- `src/test/java/com/dnd/qello/notification/web/NotificationApiMockMvcTest.java`
- `src/test/java/com/dnd/qello/notification/web/NotificationWebContractTest.java`
- `src/integrationTest/java/com/dnd/qello/NotificationInboxQueryIntegrationTest.java` (시그니처만)
- `src/integrationTest/java/com/dnd/qello/NotificationInboxCommandIntegrationTest.java` (시그니처만)
- `src/integrationTest/java/com/dnd/qello/NotificationFanOutExpansionIntegrationTest.java` (시그니처 갱신과 `cleanupFixtures` 메서드 분리)

Documentation:
- `TASK.md`
- `docs/test-plans/gh-332-TEST-PLAN-GH-332-NOTIFICATION-CLEAR.md`
- `docs/reports/tests/gh-332-TEST-REPORT-GH-332-NOTIFICATION-CLEAR.md`
- `docs/api/openapi.json` (생성 테스트만 사용; 직접 편집 금지)

## Explicit exclusions

- 알림 단건 지우기, 행 물리 삭제, 스위프 워커, 사용자별 보존 기간 설정.
- `/notifications/{id}/target` 진입 판정 변경, 푸시 발송·fan-out 변경.
- DB 스키마·마이그레이션 변경.
- 전역 formatter 또는 범위 밖 코드 정리.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·Issue·TASK·테스트 계획 | 현재 부모 오케스트레이터 | 사람의 테스트 계획 승인 (완료) |
| 위 production/test/생성 OpenAPI·테스트 보고서 | notification-executor (계획 승인 후 호출) | 독립 검증 |
| 실제 diff·필수 검사·범위 확인 | notification-verifier (구현 후 호출) | 사람의 최종 리뷰 |

구현자는 다른 변경을 되돌리지 않으며 허용 파일만 수정한다.

## Existing user-owned changes

- 작업 시작 시 메인 작업 디렉터리의 `git status --short`는 비어 있었다.
- 브랜치는 `./harness start`로 최신 `origin/main`(`0be1e925`)에서 분기했다.
- 2026-10-08 사용자 승인에 따라 미커밋 변경을 고유 태그 stash로 보관하고 `./harness sync`로 `3387fcf0`(#331)에 맞춘 뒤 SHA로 되돌렸다. 충돌은 `TASK.md` 하나였고 이 브랜치 계약으로 해결했다. `baseline.json`은 0007·0014·0018 삭제가 합쳐졌다. 임시 stash 항목은 삭제했다.
- `.worktrees/gh-323-inbox-category`의 #323 worktree는 건드리지 않는다.

## Validation

테스트 계획 승인 이후 Java 21 및 Testcontainers용 Docker 환경에서 실행한다.

```bash
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

OpenAPI는 `OpenApiSpecificationIntegrationTest`로 생성한다.
실행하지 못한 검증은 BLOCKED로 보고하며 성공으로 간주하지 않는다.

## Completion criteria

- Issue #332의 완료 조건을 충족한다.
- 전체 지우기는 본인·요청 시각 이전·`UNREAD`/`READ`만 전이하고 멱등하다.
- 전체 지우기 직후 목록·`unreadCount`·`hasUnseen`이 비어 있다.
- `DISMISSED` 알림의 읽음 요청은 상태를 바꾸지 않는다.
- 30일 하한이 목록·`countUnread`·`existsUnseen`에 같은 기준으로 적용된다.
- 기존 알림 경로 계약과 생성 OpenAPI의 다른 경로가 바뀌지 않는다.
- 승인된 테스트와 필수 검사에 실패·차단 항목이 없다.

## Decisions, risks and rollback

- CONFIRMED (2026-10-08): 커밋 hook의 `checkstyleStagedJava`가 `NotificationFanOutExpansionIntegrationTest.cleanupFixtures`(기존 119줄, `QELLO-JAVA-SIZE-001`)를 거부했다. 사용자가 hook 우회 대신 실행 순서를 유지한 private 메서드 분리를 선택했다.

- CONFIRMED (2026-10-08): 사용자가 이번 PR에서 수정하는 legacy target의 기존 위반을 해결하기로 결정했다. `JdbcNotificationRepository`의 wildcard import(JAVA-CONV-0007)를 명시 import로 바꾸고, `Notification` 생성자 검증을 private 메서드로 분리해 복잡도 위반(JAVA-CONV-0014)을 해소한 뒤 두 baseline 항목을 삭제한다. 동작 변경은 없다.
- CONFIRMED (2026-10-08): `origin/main`의 #329 baseline 불일치(JAVA-CONV-0018, `DirectionPostService`)는 별도 Issue(#333)로 분리했다. 이후 #331(`3387fcf0`)이 같은 수정을 먼저 반영해 사용자 결정으로 #333을 not planned로 닫았다. 이 브랜치는 커밋 후 `./harness sync`로 `3387fcf0` 이후 main을 반영한다.

- CONFIRMED: 2026-10-08 사용자가 보존 기간 서버 고정 30일·조회 시 숨김을 선택했다.
- CONFIRMED: 2026-10-08 사용자가 전체 지우기 범위를 서버 요청 시각 이전 전부로 선택했다.
- CONFIRMED: 2026-10-08 사용자가 두 기능을 Issue 하나로 묶고 Sprint Week 10, Priority P1, Status In Progress로 지정했다.
- 확인한 사실: `JdbcNotificationRepository.update`의 `status = 'UNREAD'` 조건 때문에 현재도 `DISMISSED` 행은 읽음 요청으로 바뀌지 않는다. 도메인 변경은 모델 일관성 목적이다.
- 위험: 30일이 지난 알림이 기존 응답에서 빠지므로 프론트가 오래된 알림을 기대하는 화면이 있으면 영향을 받는다.
- 위험: 요청 시각 이전 `created_at`을 가진 미커밋 fan-out 삽입은 전체 지우기 후에도 남는다. 새 알림으로 보이는 허용 동작으로 기록한다.
- 복구: 변경 commit revert. `DISMISSED` 행은 목록에서 계속 제외되며 DB 복구 작업은 없다.
