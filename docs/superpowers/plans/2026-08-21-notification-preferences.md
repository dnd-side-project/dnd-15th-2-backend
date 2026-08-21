# Notification Preferences Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 사용자가 앱 푸시 전체 설정, 알림 6종별 설정과 사용자 공통 방해 금지 시간을 조회·변경하고, 설정과 무관하게 알림함 원장을 보존한다.

**Architecture:** 사용자 공통 설정은 sparse `notification_user_setting`, 종류별 설정은 기존 `notification_preference`에 저장한다. `NotificationPreferenceService`가 계정 자격, 완성 snapshot 검증, 사용자 단위 직렬화와 transaction을 소유하고, fan-out은 전용 preference repository의 `isPushEnabled`를 notification 저장 뒤 delivery 생성 직전에 호출한다.

**Tech Stack:** Java 21, Spring Boot, Spring MVC/Security, Spring JDBC, PostgreSQL/PostGIS Testcontainers, Flyway, JUnit 5, AssertJ, Mockito, springdoc OpenAPI.

**Spec:** `TASK.md`, `docs/test-plans/gh-178-TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES.md`, `docs/product/NOTIFICATION_INBOX_DESIGN.md` §1·§3·§10·§12-2.

## Global Constraints

- GitHub Issue `#178`, branch `feat/gh-178-notification-preferences`, base `main`을 유지한다.
- 전체 기본값은 ON, 6종 기본값도 ON, quiet 기본값은 OFF다.
- 기존 quiet 값은 아직 배포되지 않았으므로 이관하지 않는다. 기존 종류별 `enabled`만 보존한다.
- quiet는 start/end/IANA Zone ID가 모두 있거나 모두 없고, start=end는 거부하며 자정 통과는 허용한다.
- PUT은 정확히 6종인 완성 snapshot 하나를 transaction으로 교체한다.
- `notification`을 먼저 저장하고 global/type push gate를 통과할 때만 신규 delivery를 만든다.
- quiet 실제 억제, provider 호출, 이미 생성된 delivery 취소는 `#179`·`#180` 범위다.
- 모든 신규 테스트는 `@DisplayName`과 정확한 ISO 8601·Source scenario 클래스 헤더를 갖는다.
- 아래 commit 단계는 향후 사람의 커밋 승인을 위한 경계이며 현재 커밋 권한을 의미하지 않는다.

---

### Task 1: V25 사용자 공통 설정 migration

**Files:**
- Create: `src/main/resources/db/migration/V25__split_notification_user_setting.sql`
- Create: `src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationSql.java`
- Modify: `src/integrationTest/java/com/dnd/qello/NotificationFanOutPersistenceIntegrationTest.java`
- Modify: `docs/product/data-model/direction_communication.dbml`
- Modify: `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`
- Modify: `docs/product/data-model/schema-manifest.md`

**Interfaces:**
- Consumes: V24 `notification_preference(notification_type, user_id, enabled, quiet_start, quiet_end, updated_at)`.
- Produces: `notification_user_setting`과 quiet 필드가 제거된 `notification_preference`.

- [ ] **Step 1: V24→V25 enabled 보존 실패 테스트 작성**

```java
@Test
@DisplayName("V25는 미배포 quiet 값은 버리고 종류별 enabled 값은 그대로 보존한다")
void preservesEnabledAndDropsLegacyQuietValues() {
    migrateTo("24");
    insertPreference("ANSWER_RECEIVED", false, "22:00", "07:00");
    insertPreference("ANSWER_REACTED", true, "21:00", "06:00");

    migrateToLatest();

    assertThat(enabled("ANSWER_RECEIVED")).isFalse();
    assertThat(enabled("ANSWER_REACTED")).isTrue();
    assertThat(columnExists("notification_preference", "quiet_start")).isFalse();
    assertThat(columnExists("notification_preference", "quiet_end")).isFalse();
    assertThat(count("notification_user_setting")).isZero();
}
```

- [ ] **Step 2: migration 테스트가 V25 부재로 실패하는지 실행**

Run:

```bash
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferenceMigrationIntegrationTest" --console=plain
```

Expected: 신규 테이블 또는 V25 migration 부재로 FAIL.

- [ ] **Step 3: V25 DDL 작성**

```sql
CREATE TABLE notification_user_setting (
    user_id         BIGINT PRIMARY KEY,
    push_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_start     TIME,
    quiet_end       TIME,
    quiet_zone_id   VARCHAR(50),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_notification_user_setting_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_user_setting_quiet_hours
        CHECK (num_nonnulls(quiet_start, quiet_end, quiet_zone_id) IN (0, 3)),
    CONSTRAINT ck_notification_user_setting_distinct_quiet_hours
        CHECK (quiet_start IS NULL OR quiet_start <> quiet_end)
);

ALTER TABLE notification_preference
    DROP CONSTRAINT ck_notification_preference_quiet_hours,
    DROP COLUMN quiet_start,
    DROP COLUMN quiet_end;
```

기존 quiet 값을 읽거나 신규 테이블에 복사하는 SQL은 추가하지 않는다.

- [ ] **Step 4: catalog·재실행 테스트 추가**

```java
@Test
@DisplayName("V25는 사용자 설정 FK와 quiet 삼중값 CHECK를 만들고 재실행할 migration이 없다")
void createsUserSettingContractAndIsFullyApplied() {
    migrateToLatest();
    assertThat(constraintDefinition("ck_notification_user_setting_quiet_hours"))
        .contains("num_nonnulls").contains("quiet_zone_id");
    assertThat(migrateToLatest().migrationsExecuted).isZero();
}
```

- [ ] **Step 5: Flyway inventory와 데이터 모델 문서 동기화**

`EXPECTED_TABLES`에 `notification_user_setting`을 추가하고 DBML·ERD에는 두 테이블의
책임을 다음처럼 분리해 기록한다.

```text
notification_user_setting: 사용자 공통 push master와 quiet schedule
notification_preference: 알림 종류별 push enabled override
```

- [ ] **Step 6: migration 검증 실행**

```bash
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferenceMigrationIntegrationTest" --tests "com.dnd.qello.FlywayMigrationIntegrationTest" --console=plain
```

Expected: PASS, `enabled` 값 단위 비교 성공.

- [ ] **Step 7: V25 이후 기존 preference 저장 bridge 회귀 테스트 추가**

`NotificationFanOutPersistenceIntegrationTest`에 V25 schema에서 전용
`NotificationPreferenceRepository.replaceTypePreferences`를 호출해 `enabled`가
저장되는 시나리오를 유지한다. Task 1의 bridge 회귀는 이미 RED/GREEN으로 확인했고,
Task 5에서 bridge를 제거했으므로 최종 계획의 INT-013은 전용 repository의 V25 저장
경로를 검증한다.

```sql
INSERT INTO notification_preference (notification_type, user_id, enabled)
VALUES (:notificationType, :userId, :enabled)
ON CONFLICT (notification_type, user_id) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    updated_at = clock_timestamp()
```

Run:

```bash
./gradlew integrationTest --tests "com.dnd.qello.NotificationFanOutPersistenceIntegrationTest" --console=plain
```

Expected: 제거된 quiet 컬럼을 참조하지 않고 저장·재조회 PASS.

- [ ] **Step 8: PK·CHECK catalog 검증 보강**

migration integration test에서 `notification_user_setting_pkey`와 기존
`pk_notification_preference` 존재를 확인하고, `(22:00, 07:00, Asia/Seoul)`은 허용,
같은 시작·종료 또는 quiet 세 값 중 하나만 NULL인 row는 PostgreSQL CHECK로 거부되는지
검증한다.

- [ ] **Step 9: commit 승인 게이트**

검토 파일을 named path로 제시한 뒤 승인되면 다음 형식으로 커밋한다.

```bash
git add -- src/main/resources/db/migration/V25__split_notification_user_setting.sql src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationSql.java src/integrationTest/java/com/dnd/qello/NotificationFanOutPersistenceIntegrationTest.java docs/product/data-model/direction_communication.dbml docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md docs/product/data-model/schema-manifest.md
git commit -m "feat(database): split user notification settings (#178)"
```

---

### Task 2: Preference domain과 전용 repository

**Files:**
- Create: `src/main/java/com/dnd/qello/notification/domain/NotificationQuietHours.java`
- Create: `src/main/java/com/dnd/qello/notification/domain/NotificationPreferenceSnapshot.java`
- Create: `src/main/java/com/dnd/qello/notification/repository/NotificationPreferenceRepository.java`
- Create: `src/main/java/com/dnd/qello/notification/repository/jdbc/JdbcNotificationPreferenceRepository.java`
- Create: `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationPreferenceSql.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/jdbc/JdbcNotificationRepository.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationSql.java`
- Modify: `src/main/java/com/dnd/qello/notification/error/NotificationErrorCode.java`
- Create: `src/test/java/com/dnd/qello/notification/domain/NotificationQuietHoursTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/NotificationPreferencePersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1의 두 테이블.
- Produces: `NotificationQuietHours`, `NotificationPreferenceSnapshot`, `NotificationPreferenceRepository`.

- [ ] **Step 1: quiet 불변식 단위 테스트 작성**

```java
@Test
@DisplayName("자정을 통과하는 quiet 시간은 유효하다")
void acceptsOvernightQuietHours() {
    var quiet = new NotificationQuietHours(
        LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("Asia/Seoul"));
    assertThat(quiet.start()).isEqualTo(LocalTime.of(22, 0));
}

@Test
@DisplayName("시작과 종료가 같으면 전체 차단으로 추정하지 않고 거부한다")
void rejectsEqualTimes() {
    assertThatThrownBy(() -> new NotificationQuietHours(
        LocalTime.NOON, LocalTime.NOON, ZoneId.of("Asia/Seoul")))
        .isInstanceOf(NotificationException.class);
}
```

- [ ] **Step 2: domain 테스트 RED 확인**

```bash
./gradlew test --tests "*NotificationQuietHoursTest" --console=plain
```

Expected: 신규 type 부재로 FAIL.

- [ ] **Step 3: preference 전용 오류 코드와 quiet domain type 구현**

```java
INVALID_PREFERENCE(
    HttpStatus.BAD_REQUEST,
    "NOT-VAL-008",
    ErrorCategory.VAL,
    "알림 설정 값이 올바르지 않습니다."
)
```

```java
public record NotificationQuietHours(LocalTime start, LocalTime end, ZoneId zoneId) {
    public NotificationQuietHours {
        if (start == null || end == null || zoneId == null || start.equals(end)) {
            throw new NotificationException(
                NotificationErrorCode.INVALID_PREFERENCE,
                "quietHours",
                "시작·종료·시간대를 모두 지정하고 시작과 종료를 다르게 설정해야 합니다");
        }
    }
}
```

`NotificationPreferenceSnapshot`은 생성 시 `NotificationType.values()` 여섯 개가 정확히
한 번씩 있는지 검사하고 `EnumMap`의 불변 복사본을 보관한다.

- [ ] **Step 4: repository default와 round-trip 통합 테스트 작성**

```java
@Test
@DisplayName("설정 행이 없으면 global과 6종은 ON이고 quiet는 없다")
void readsSparseDefaults() {
    NotificationPreferenceSnapshot snapshot = preferences.findByUserId(userId);
    assertThat(snapshot.pushEnabled()).isTrue();
    assertThat(snapshot.quietHours()).isNull();
    assertThat(snapshot.typeEnabled()).containsOnlyValues(true).hasSize(6);
}
```

- [ ] **Step 5: 전용 repository interface와 SQL 구현**

```java
public interface NotificationPreferenceRepository {
    NotificationPreferenceSnapshot findByUserId(long userId);
    void lockUser(long userId);
    void saveUserSetting(long userId, boolean pushEnabled, NotificationQuietHours quietHours);
    void replaceTypePreferences(long userId, Map<NotificationType, Boolean> typeEnabled);
    boolean isPushEnabled(long userId, NotificationType type);
}
```

`lockUser`는 다음 쿼리를 사용한다.

```sql
SELECT id FROM user_account WHERE id = :userId FOR UPDATE;
```

`isPushEnabled`는 두 sparse default를 함께 계산한다.

```sql
SELECT
    COALESCE((SELECT push_enabled FROM notification_user_setting WHERE user_id = :userId), TRUE)
    AND
    COALESCE((SELECT enabled FROM notification_preference
              WHERE user_id = :userId AND notification_type = :notificationType), TRUE)
```

`replaceTypePreferences`는 enum 이름 순으로 6종을 upsert해 lock 순서를 고정한다.

- [ ] **Step 6: 기존 repository를 V25와 호환되는 임시 bridge로 유지**

Task 5에서 모든 call site를 한 번에 옮기기 전까지 기존 `savePreference`와
`isPreferenceEnabled`는 유지한다. 단, V25에서 quiet 컬럼이 사라지므로 기존 upsert SQL은
`notification_type`, `user_id`, `enabled`만 쓰게 바꾸고 quiet parameter는 무시한다.
`NotificationPreference`의 3-field 변경과 bridge 제거도 Task 5에서 call site와 함께 한다.

- [ ] **Step 7: domain·persistence 검증 실행**

```bash
./gradlew test --tests "*NotificationQuietHoursTest" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferencePersistenceIntegrationTest" --console=plain
```

Expected: PASS.

- [ ] **Step 8: commit 승인 게이트**

승인 후 domain/repository와 직접 테스트 파일만 named path로 커밋한다.

```bash
git commit -m "feat(notification): separate notification preference storage (#178)"
```

---

### Task 3: Transactional preference service

**Files:**
- Create: `src/main/java/com/dnd/qello/notification/service/UpdateNotificationPreferences.java`
- Create: `src/main/java/com/dnd/qello/notification/service/NotificationPreferenceService.java`
- Create: `src/test/java/com/dnd/qello/notification/service/NotificationPreferenceServiceTest.java`
- Modify: `docs/error-codes.md`

**Interfaces:**
- Consumes: Task 2의 snapshot/repository와 `AccountEligibilityGate`.
- Produces: `findMine(long)`, `replaceMine(long, UpdateNotificationPreferences)`.

- [ ] **Step 1: 6종 완전성과 계정 자격 service 테스트 작성**

```java
@Test
@DisplayName("PUT command에 알림 종류가 누락되면 저장을 시작하지 않는다")
void rejectsIncompleteTypeSetBeforeWrite() {
    var command = commandWithout(NotificationType.REPORT_RESOLVED);
    assertThatThrownBy(() -> service.replaceMine(USER_ID, command))
        .isInstanceOf(NotificationException.class);
    verifyNoInteractions(preferences);
}

@Test
@DisplayName("전체 OFF 저장은 종류별 혼합값을 덮어쓰지 않는다")
void preservesTypeChoicesWhenMasterIsOff() {
    service.replaceMine(USER_ID, command(false, mixedTypes(), null));
    verify(preferences).replaceTypePreferences(USER_ID, mixedTypes());
}
```

- [ ] **Step 2: service 테스트 RED 확인**

```bash
./gradlew test --tests "*NotificationPreferenceServiceTest" --console=plain
```

Expected: service 부재로 FAIL.

- [ ] **Step 3: request command와 service 구현**

```java
public record UpdateNotificationPreferences(
    boolean pushEnabled,
    NotificationQuietHours quietHours,
    Map<NotificationType, Boolean> typeEnabled
) {}

@Transactional(readOnly = true)
public NotificationPreferenceSnapshot findMine(long userId) {
    requireEligible(userId);
    return preferences.findByUserId(userId);
}

@Transactional
public NotificationPreferenceSnapshot replaceMine(long userId, UpdateNotificationPreferences command) {
    requireEligible(userId);
    command.requireCompleteTypeSet();
    preferences.lockUser(userId);
    preferences.saveUserSetting(userId, command.pushEnabled(), command.quietHours());
    preferences.replaceTypePreferences(userId, command.typeEnabled());
    return preferences.findByUserId(userId);
}
```

- [ ] **Step 4: 오류 코드 문서와 command 방어 검증 완성**

`NOT-VAL-008`을 `docs/error-codes.md`에 추가한다. quiet 일부 누락, invalid Zone ID,
same-time, 종류 누락·중복은 이 코드의 `field`와 `reason`으로 구분한다.

- [ ] **Step 5: service 검증 실행**

```bash
./gradlew test --tests "*NotificationPreferenceServiceTest" --console=plain
```

Expected: 6종·global 보존·계정 404/403 시나리오 PASS.

- [ ] **Step 6: commit 승인 게이트**

```bash
git commit -m "feat(notification): add transactional preference service (#178)"
```

---

### Task 4: GET·PUT Web API

**Files:**
- Create: `src/main/java/com/dnd/qello/notification/web/request/QuietHoursRequest.java`
- Create: `src/main/java/com/dnd/qello/notification/web/request/NotificationTypePreferenceRequest.java`
- Create: `src/main/java/com/dnd/qello/notification/web/request/UpdateNotificationPreferencesRequest.java`
- Create: `src/main/java/com/dnd/qello/notification/web/response/QuietHoursResponse.java`
- Create: `src/main/java/com/dnd/qello/notification/web/response/NotificationTypePreferenceResponse.java`
- Create: `src/main/java/com/dnd/qello/notification/web/response/InboxRecordingPolicy.java`
- Create: `src/main/java/com/dnd/qello/notification/web/response/NotificationPreferenceResponse.java`
- Create: `src/test/java/com/dnd/qello/notification/web/NotificationPreferenceApiMockMvcTest.java`
- Modify: `src/main/java/com/dnd/qello/notification/web/NotificationApiSpec.java`
- Modify: `src/main/java/com/dnd/qello/notification/web/NotificationController.java`
- Modify: `src/test/java/com/dnd/qello/notification/web/NotificationWebContractTest.java`

**Interfaces:**
- Consumes: Task 3 service.
- Produces: 본인 전용 `GET`·`PUT /api/v1/notifications/preferences`.

- [ ] **Step 1: MockMvc RED 테스트 작성**

```java
@Test
@DisplayName("인증 사용자는 전체·6종·quiet와 ALWAYS_RECORD 정책을 조회한다")
void getsOwnPreferences() throws Exception {
    mvc.perform(get("/api/v1/notifications/preferences").with(user(appUser())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pushEnabled").value(true))
        .andExpect(jsonPath("$.data.preferences.length()").value(6))
        .andExpect(jsonPath("$.data.inboxRecordingPolicy").value("ALWAYS_RECORD"));
}
```

- [ ] **Step 2: API 테스트 RED 확인**

```bash
./gradlew test --tests "*NotificationPreferenceApiMockMvcTest" --console=plain
```

Expected: 404 또는 handler 부재로 FAIL.

- [ ] **Step 3: request/response 계약 구현**

```java
public record UpdateNotificationPreferencesRequest(
    @NotNull Boolean pushEnabled,
    @Valid QuietHoursRequest quietHours,
    @NotNull List<@Valid NotificationTypePreferenceRequest> preferences
) {
    public UpdateNotificationPreferences toCommand() {
        return new UpdateNotificationPreferences(
            pushEnabled,
            quietHours == null ? null : quietHours.toDomain(),
            toCompleteEnumMap(preferences));
    }
}
```

```java
public record NotificationPreferenceResponse(
    boolean pushEnabled,
    QuietHoursResponse quietHours,
    List<NotificationTypePreferenceResponse> preferences,
    InboxRecordingPolicy inboxRecordingPolicy
) {}
```

- [ ] **Step 4: spec와 controller 연결**

```java
@GetMapping("/notifications/preferences")
ResponseEntity<ApiResponse<NotificationPreferenceResponse>> preferences(
    @Parameter(hidden = true) Authentication authentication);

@PutMapping("/notifications/preferences")
ResponseEntity<ApiResponse<NotificationPreferenceResponse>> replacePreferences(
    @Valid @RequestBody UpdateNotificationPreferencesRequest request,
    @Parameter(hidden = true) Authentication authentication);
```

Controller는 두 메서드 모두 `AuthenticatedUserId.require(authentication)`만 service에
전달하며 request/path/query에 대상 사용자 ID를 추가하지 않는다.

- [ ] **Step 5: 400·401·403·404와 web contract 테스트 완성**

quiet 일부 누락, invalid Zone ID, same-time, 종류 누락·중복은 400 `NOT-VAL-008`을
검증한다. 익명은 401, 비활성 계정은 403, 없는 계정은 404다.

- [ ] **Step 6: web 검증 실행**

```bash
./gradlew test --tests "*NotificationPreferenceApiMockMvcTest" --tests "*NotificationWebContractTest" --console=plain
```

Expected: PASS.

- [ ] **Step 7: commit 승인 게이트**

```bash
git commit -m "feat(notification): expose notification preference API (#178)"
```

---

### Task 5: Fan-out effective push gate

**Files:**
- Modify: `src/main/java/com/dnd/qello/notification/fanout/NotificationFanOutWorker.java`
- Modify: `src/main/java/com/dnd/qello/notification/fanout/RecipientNotificationFanOutWorker.java`
- Modify: `src/main/java/com/dnd/qello/notification/fanout/ReportResolutionFanOutWorker.java`
- Modify: `src/main/java/com/dnd/qello/notification/domain/NotificationPreference.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/NotificationRepository.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/jdbc/JdbcNotificationRepository.java`
- Modify: `src/main/java/com/dnd/qello/notification/repository/jdbc/sql/NotificationSql.java`
- Modify: `src/test/java/com/dnd/qello/notification/fanout/NotificationFanOutWorkerTest.java`
- Modify: `src/test/java/com/dnd/qello/notification/fanout/RecipientNotificationFanOutWorkerTest.java`
- Create: `src/test/java/com/dnd/qello/notification/fanout/ReportResolutionFanOutWorkerTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/NotificationFanOutExpansionIntegrationTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/RecipientNotificationFanOutWorkerIntegrationTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/ReportResolutionIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2 `NotificationPreferenceRepository.isPushEnabled`.
- Produces: 세 fan-out 경계의 `global && type` delivery gate.

- [ ] **Step 1: global/type OFF 회귀 테스트를 먼저 변경**

```java
when(preferences.isPushEnabled(RECIPIENT_ID, NotificationType.ANSWER_RECEIVED))
    .thenReturn(false);

worker.processBatch(command());

verify(notifications).saveIfAbsent(any(Notification.class));
verify(notifications, never()).saveDeliveryIfAbsent(any(NotificationDelivery.class));
```

세 worker에서 notification 선저장과 device 조회 생략을 각각 고정한다.

- [ ] **Step 2: 변경 전 compile/test 실패 확인**

```bash
./gradlew test --tests "com.dnd.qello.notification.fanout.*" --console=plain
```

Expected: 새 repository dependency와 method 부재 또는 기존 interaction 불일치로 FAIL.

- [ ] **Step 3: 세 worker dependency와 gate 교체**

```java
private final NotificationPreferenceRepository preferenceRepository;

private void persistPendingDeliveries(long notificationId, long recipientId,
    NotificationType type, Instant at) {
    if (!preferenceRepository.isPushEnabled(recipientId, type)) return;
    for (long deviceId : notificationRepository.findActiveDeviceIdsByUserId(recipientId)) {
        notificationRepository.saveDeliveryIfAbsent(
            NotificationDelivery.pending(notificationId, deviceId, at));
    }
}
```

notification 생성·dedup·outbox complete/retry 순서는 바꾸지 않는다.

같은 변경에서 `NotificationPreference`를 `(notificationType, userId, enabled)` 3-field
record로 바꾸고 기존 fixture 생성자를 갱신한다. 모든 call site가 새 전용 repository로
이동한 뒤 `NotificationRepository.savePreference/isPreferenceEnabled`와 기존 SQL bridge를
제거한다. 이 순서를 지켜 중간 commit이 compile 불가 상태가 되지 않게 한다.

- [ ] **Step 4: PostgreSQL 원장/delivery 증거 추가**

global OFF/type ON과 global ON/type OFF를 분리해 각각 notification 1, delivery 0을
검증한다. ON/ON은 active device 수만큼 delivery가 생겨야 한다.

- [ ] **Step 5: fan-out 검증 실행**

```bash
./gradlew test --tests "com.dnd.qello.notification.fanout.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*NotificationFanOut*" --tests "com.dnd.qello.ReportResolutionIntegrationTest" --console=plain
```

Expected: 기존 dedup·retry·lease 테스트 포함 PASS.

- [ ] **Step 6: commit 승인 게이트**

```bash
git commit -m "feat(notification): apply global and type push gates (#178)"
```

---

### Task 6: PUT atomicity·concurrency와 OpenAPI

**Files:**
- Modify: `src/integrationTest/java/com/dnd/qello/NotificationPreferencePersistenceIntegrationTest.java`
- Create: `src/integrationTest/java/com/dnd/qello/NotificationPreferenceApiIntegrationTest.java`
- Modify: `src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java`
- Modify: `docs/api/openapi.json`

**Interfaces:**
- Consumes: Tasks 1~5 전체.
- Produces: P0 transaction/concurrency 증거와 공개 API 스펙.

- [ ] **Step 1: 중간 실패 rollback 통합 테스트 작성**

```java
@Test
@DisplayName("마지막 종류 저장이 실패하면 global과 앞선 종류도 요청 전 snapshot으로 롤백한다")
void rollsBackWholeSnapshotOnTypeWriteFailure() {
    saveOriginalSnapshot();
    assertThatThrownBy(() -> service.replaceMine(userId, commandTriggeringLastTypeFailure()))
        .isInstanceOf(DataAccessException.class);
    assertThat(preferences.findByUserId(userId)).isEqualTo(originalSnapshot());
}
```

- [ ] **Step 2: 두 PUT 경합 테스트 작성**

두 executor를 barrier로 동시에 시작하고 서로 다른 6종 snapshot A·B를 저장한다.
최종 조회가 A 전체 또는 B 전체와 같고 조합값이 아니어야 한다.

```java
assertThat(finalSnapshot).isIn(snapshotA, snapshotB);
```

- [ ] **Step 3: persistence 통합 테스트 실행**

```bash
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferencePersistenceIntegrationTest" --console=plain
```

Expected: rollback·경합 제한시간 내 PASS, executor leak 없음.

- [ ] **Step 4: OpenAPI 재생성 및 계약 검증**

```bash
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
```

GET·PUT operation, request/response, 6종 enum, `ALWAYS_RECORD`, 400/401/403/404가
스펙에 포함되고 토큰·내부 사용자 식별자가 예시에 없어야 한다.

- [ ] **Step 5: commit 승인 게이트**

```bash
git commit -m "feat(notification): verify preference transaction contract (#178)"
```

---

### Task 7: Full verification and safe report

**Files:**
- Create: `docs/test-reports/gh-178-TEST-REPORT-GH-178-NOTIFICATION-PREFERENCES.md`
- Modify: `TASK.md` only after evidence exists.
- Modify: `docs/test-plans/gh-178-TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES.md` approval metadata only after human approval is recorded.

**Interfaces:**
- Consumes: 모든 구현·테스트와 승인된 test plan.
- Produces: 병합 가능 여부를 판정할 로컬 증거. Push provider·quiet 억제는 여전히 미검증이다.

- [ ] **Step 1: 승인된 test plan 실행**

```bash
./harness test-run --id TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES
```

- [ ] **Step 2: 대상 테스트 실행**

```bash
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.NotificationPreference*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*NotificationFanOut*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest" --console=plain
```

- [ ] **Step 3: 저장소 완료 검증 실행**

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- [ ] **Step 4: 테스트 보고서 작성**

`templates/test-report.md`를 사용해 각 명령, 실행 시각, 성공·실패, 환경 문제,
미검증 범위와 다음 위험을 기록한다.

```text
#179 발송 직전 최신 preference 재검사
#180 quiet 실제 억제와 timezone 계산
이미 생성된 PENDING/FAILED delivery 처리
클라이언트 OS 권한과 서버 설정의 불일치
```

- [ ] **Step 5: 계획 self-review와 완료 조건 대조**

`TASK.md` 각 체크박스에 실제 test scenario/명령 증거를 연결한다. 실행하지 않은 검증은
성공으로 표시하지 않고 `BLOCKED` 또는 남은 위험으로 기록한다.

- [ ] **Step 6: 최종 commit 승인 게이트**

보고서와 계약 문서 diff를 제시하고 승인되면 named path만 커밋한다.

```bash
git commit -m "feat(notification): document preference verification (#178)"
```

커밋, push와 PR 생성은 각각 별도 사람 승인을 받는다.
