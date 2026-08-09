# Test Report: TEST-PLAN-GH-89-JDBC-SQL-SPLIT

> Created at: `2026-08-08T17:55:00+09:00`
> GitHub Issue: `#89`
> Branch: `refactor/gh-89-jdbc-query-sql-split`
> Commit: 미커밋 — 작업 트리 기준(부모 `cac2d36`), 아래 검증은 전부 이 작업 트리 상태로 실행했다

## 1. Executive summary

- Result: `PASS`
- Tested scope: 1차 4개 저장소(`JdbcInboxQueryRepository`, `JdbcSentPostQueryRepository`,
  `JdbcPostAnswerQueryRepository`, `JdbcActiveUserPresenceRepository`)의 정적
  SELECT/CTE SQL + 2차 5곳(`JdbcDirectionPostRepository`, `JdbcDirectionSchemeRepository`,
  `JdbcPostAudienceRepository`, `JdbcPostRecipientRepository`,
  `JdbcActiveUserPresenceRepository.save`)의 긴 INSERT/UPDATE를 `repository/jdbc/sql/`
  서브패키지 상수 클래스로 이동. 컴파일, 전체 단위 테스트, 전체 통합 테스트, harness
  게이트 전부 확인. 총 9개 Sql 클래스(`ActiveUserPresenceSql`은 1·2차 공용).
- Unverified scope: 2차 상수 중 `DirectionPostSql.UPDATE`,
  `DirectionSchemeSql.SCHEME_UPDATE`/`SEGMENT_UPDATE` — 현재 애플리케이션 코드에
  기존 id로 save를 호출하는 경로가 없어 이번 리팩터링 이전부터 어떤 테스트로도
  실행되지 않는 죽은 분기다(§6 addendum 참고). 텍스트 내용이 원본과 동일함은
  `sed` 기반 추출 방식으로 구조적으로 보장되지만, 실행 검증은 하지 못했다.
- Release recommendation: PR 생성 가능. `FeedPersistenceBoundaryTest` 수정 건(§5, §6)과
  위 미실행 UPDATE 분기 2곳을 리뷰어가 확인해야 한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 25.0.3 LTS |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostgreSQL(PostGIS 확장 포함), local Docker |
| Test runner | JUnit 5 (Gradle `test` / `integrationTest` task) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew compileJava` | PASS | — | ~1s | BUILD SUCCESSFUL |
| `./gradlew test` | PASS | 156개 | ~3s | BUILD SUCCESSFUL (최초 1회는 `FeedPersistenceBoundaryTest` 1건 실패 → 원인 수정 후 재실행 전부 통과) |
| `./gradlew integrationTest` | PASS | 4개 대상 클래스 포함 전체 | ~1m16s | BUILD SUCCESSFUL |
| `./harness check` | PASS | — | — | Secret preflight, JUnit policy, convention, workflow, label policy, Husky 전부 통과 |
| `./harness pr-ready --project-tests` | PASS | — | ~1s (모두 UP-TO-DATE 재사용) | Local PR readiness checks passed |
| `git diff --check` | PASS | — | — | 출력 없음(공백 오류 없음) |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-UNIT-001 | PASS | `./gradlew compileJava` | 4개 `*Sql` 클래스 + 4개 리포지토리가 오류 없이 컴파일됨 |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-001 | PASS | `InboxQueryIntegrationTest` (11개 시나리오) | 무수정, 전부 통과 |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-002 | PASS | `SentPostQueryIntegrationTest` (6개 시나리오) | 무수정, 전부 통과 |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-003 | PASS | `PostAnswerQueryIntegrationTest` (6개 시나리오) | 무수정, 전부 통과 |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-004 | PASS | `DirectionPostgisPersistenceIntegrationTest` | 무수정, 전부 통과 |

## 5. Failures and diagnostics

최초 `./gradlew test` 실행에서 `FeedPersistenceBoundaryTest > feed는 다른 feature의
JPA Entity와 JDBC 구현을 직접 참조하지 않는다`가 실패했다.

- 재현 조건: `feed.repository.jdbc` 아래 `sql` 서브패키지(`InboxQuerySql`,
  `SentPostQuerySql`, `PostAnswerQuerySql`)를 신설하고 `JdbcInboxQueryRepository` 등이
  `import com.dnd.qello.feed.repository.jdbc.sql.InboxQuerySql;`로 참조하면 재현된다.
- 원인: 해당 테스트의 검사 로직이 다른 feature 이름을 명시하지 않고
  `.repository.jdbc.`라는 부분 문자열만으로 "다른 feature 구현 참조"를 판정했다.
  `com.dnd.qello.feed.repository.jdbc.sql.InboxQuerySql`이라는 문자열 자체가
  `.repository.jdbc.`를 부분 문자열로 포함하므로, feed가 **자기 자신의** 새
  서브패키지를 참조하는 것도 "다른 feature 침투"로 오탐했다. 같은 취지의
  `AnswerJdbcBoundaryTest`는 이미 `direction.repository.jdbc`, `feed.repository.jdbc`처럼
  feature 이름을 명시하는 정밀한 패턴을 쓰고 있어 이 문제가 없었다 — feed 쪽만
  느슨하게 작성돼 있던 기존 결함이다.
- 조치: `FeedPersistenceBoundaryTest`의 검사를 `AnswerJdbcBoundaryTest`와 동일한
  패턴(다른 feature 이름을 명시한 목록에 대해서만 검사)으로 고쳤다. 검증 의도(feed가
  다른 feature의 jdbc/jpa 구현을 직접 참조하지 않는다)는 그대로 유지했고, feed
  자신의 내부 서브패키지 구조만 허용 범위로 정밀화했다. 수정 후 재실행에서 전체
  통과를 확인했다.

## 6. Potential issues

### Application code

- `FeedPersistenceBoundaryTest`를 계획 범위 밖에서 수정했다. 순수 이동
  리팩터링이 기존 테스트를 건드리지 않을 것이라는 애초 가정과 달리, 이 특정
  테스트는 feed 패키지에 새 서브패키지가 생기면 항상 오탐하는 기존 결함을 갖고
  있었다. 리뷰어가 이 diff를 "테스트 통과를 위한 임의 완화"가 아니라 "오탐 버그
  수정"으로 판단할 수 있도록 diff와 사유를 분리해 커밋할 것을 권장한다.
- `JdbcPostAnswerQueryRepository.findAnswers`의 익명 SELECT 텍스트 블록(26줄,
  상관 서브쿼리 3개 포함)은 이번 이동 대상에서 제외했다 — TASK.md가 이 파일에
  대해 `CAN_VIEW_ANSWERS_SQL`만 명시적으로 지정했기 때문이다. 다만 이 SELECT도
  다른 세 저장소의 `SELECT_CARD`와 구조적으로 동일한 성격(정적 뼈대 + 동적
  꼬리)이라, 이슈의 근본 동기(긴 SQL을 리포지토리 밖으로) 관점에서는 후속
  범위로 남아 있다. 이번 PR 범위 확대는 하지 않았다.

### Infrastructure and resource limits

- 해당 없음.

### Database and migrations

- 해당 없음 — 스키마 변경 없음.

### Concurrency and idempotency

- 해당 없음 — 이번 이동 대상에 락·조건부 갱신 로직 없음.

### Transactions and event ordering

- 해당 없음.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 해당 없음.

## 6b. Addendum — 2차 확장(긴 단순 CRUD SQL, 12줄 기준)

구현 중 추가 피드백으로 서브쿼리 유무와 무관하게 "메서드당 SQL 리터럴 합 12줄
초과"를 기준으로 5곳을 더 이동했다: `JdbcDirectionPostRepository.save`,
`JdbcDirectionSchemeRepository.save`·`saveSegment`, `JdbcPostAudienceRepository.save`,
`JdbcPostRecipientRepository.save`, `JdbcActiveUserPresenceRepository.save`.
자세한 대상·근거는 `docs/test-plans/gh-89-JDBC-SQL-SPLIT.md` §11을 참고한다.

**회귀 커버리지 확인 결과** — 8개 상수 중 6개는 기존 통합 테스트(주로
`DirectionPostgisPersistenceIntegrationTest`의 send 플로우, `InboxSentPostWriteIntegrationTest`의
open/skip 플로우)로 실행이 확인됐다. 2개(`DirectionPostSql.UPDATE`,
`DirectionSchemeSql.SCHEME_UPDATE`/`SEGMENT_UPDATE`, 사실상 3개 상수)는 **이번
리팩터링 이전부터 애플리케이션 코드에 호출부가 없어 어떤 테스트로도 실행되지
않는 죽은 분기**였다 — 이동하면서 새로 발견한 기존 상태이며, 이 이슈 범위에서
새 호출부나 테스트를 추가하지 않았다.

`""" : """`처럼 두 텍스트 블록이 한 줄에 붙어 있는 삼항 분기 4곳은 `sed`로 독립된
줄 범위(INSERT 쪽 줄 범위, UPDATE 쪽 줄 범위)만 각각 추출해 두 블록의 내용이 서로
섞일 수 없는 방식으로 작업했다 — 수작업 복사가 아니므로 이 지점에서의 오염
가능성은 없다.

## 7. Regression and residual risk

- WHY 주석 보존은 자동화된 테스트로 검증할 수 없는 항목이다 — `git diff`
  리뷰로 4곳(거리 상호배제, ST_Azimuth 인자 순서, SKIP_PENDING 자격 유지 2건)
  전부 상수와 함께 이동했음을 육안으로 확인했으나, 사람 리뷰어의 재확인을
  권장한다.
- `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`은 메서드 로컬 변수(2탭 본문/3탭
  SQL)에서 클래스 필드(1탭 필드/2탭 SQL)로 옮기며 상대 들여쓰기를 한 단계
  일괄 조정했다. 다른 세 클래스는 원래도 클래스 필드였으므로 들여쓰기 조정이
  전혀 없었다. 이 한 곳만 조정이 있었다는 점을 리뷰에서 특히 확인하면 좋다
  (통합 테스트가 실제 PostgreSQL/PostGIS에 대해 통과했으므로 SQL 구문 자체는
  유효함이 이미 검증됐다).
- **잔존 위험**: `DirectionPostSql.UPDATE`, `DirectionSchemeSql.SCHEME_UPDATE`,
  `DirectionSchemeSql.SEGMENT_UPDATE`는 텍스트 내용이 원본과 동일함은 추출
  방식상 구조적으로 보장되지만(§6b), 실제 PostgreSQL에 대해 실행되어 검증된
  적은 리팩터링 전후 어느 시점에도 없다. 향후 이 UPDATE 경로를 실제로 쓰게 될
  때(예: 질문글 수정 기능) 첫 사용 전에 통합 테스트로 한 번 실행해 볼 것을
  권장한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-89-JDBC-SQL-SPLIT.md`
- CI run: 로컬 실행(이 세션) — CI 파이프라인 결과는 PR 생성 후 별도 확인 필요
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`
- PR: 아직 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1, §6b, §7 — `DirectionPostSql.UPDATE`,
      `DirectionSchemeSql.SCHEME_UPDATE`/`SEGMENT_UPDATE`는 호출부 부재로
      실행 검증 불가)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (§6 `findAnswers` SELECT 확장 여부는
      사람이 판단해 필요시 별도 이슈로 뗄 것을 권장 — 이 세션에서는 임의로
      이슈를 만들지 않았다)
- [x] 실행 결과와 PR 설명이 일치함 (PR 생성 시 이 보고서를 근거로 작성 예정)
