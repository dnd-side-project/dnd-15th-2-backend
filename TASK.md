# GitHub Issue #89 Task Contract

> Generated at: `2026-08-08T17:28:42+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `JDBC 저장소의 긴 SQL을 sql/ 서브패키지 상수 클래스로 분리`
- GitHub Issue: `#89`
- Branch: `refactor/gh-89-jdbc-query-sql-split`
- Base branch: `main`

## Objective

코드 리뷰 피드백("SQL이 길어지면 유지보수가 어려우니 긴 SQL은 따로 폴더에서 관리하는
방법을 고려해달라")에 따라, 13개 `Jdbc*Repository`의 긴 SQL 텍스트 블록을 리포지토리
클래스 밖으로 분리한다. 순수 리팩터링이며 SQL 실행 결과와 파라미터 바인딩 동작은
바꾸지 않는다.

1차 범위(서브쿼리·CTE가 중첩된 복잡한 `SELECT`)로 시작했으나, 구현 중 추가 피드백을
받아 "서브쿼리 유무와 무관하게 한 메서드의 SQL 리터럴 합이 대략 12줄을 넘으면 상수로
뺀다"는 기준으로 2차 확장했다(컬럼이 많은 단순 INSERT/UPDATE도 포함). Notification/Safety는
이 기준으로도 제외된다 — 개별 쿼리가 전부 8~9줄 이하라 SQL이 길어서가 아니라 엔티티가
5개·3개라서 파일이 긴 경우다.

## Scope

### 대상 저장소

**1차(복잡한 SELECT/CTE)**

- `feed.repository.jdbc.JdbcInboxQueryRepository` — `SELECT_CARD`
- `feed.repository.jdbc.JdbcSentPostQueryRepository` — `SELECT_CARD`
- `feed.repository.jdbc.JdbcPostAnswerQueryRepository` — `CAN_VIEW_ANSWERS_SQL`
- `direction.repository.jdbc.JdbcActiveUserPresenceRepository` — `findCandidates`의
  PostGIS CTE 쿼리

**2차(긴 단순 INSERT/UPDATE, 12줄 기준)**

- `direction.repository.jdbc.JdbcDirectionPostRepository` — `save`의 INSERT/UPDATE
  (id 유무 삼항 분기)
- `direction.repository.jdbc.JdbcDirectionSchemeRepository` — `save`,
  `saveSegment`의 INSERT/UPDATE(각 삼항 분기)
- `direction.repository.jdbc.JdbcPostAudienceRepository` — `save`의
  INSERT ON CONFLICT
- `direction.repository.jdbc.JdbcPostRecipientRepository` — `save`의 INSERT/UPDATE
  (id 유무 삼항 분기)
- `direction.repository.jdbc.JdbcActiveUserPresenceRepository` — `save`의
  INSERT ON CONFLICT (1차에서 이미 손댄 파일이라 같은 Sql 클래스에 추가)

제외 검토했으나 12줄 기준 미달로 뺀 곳: `JdbcRecipientReceiveStateRepository.save`(9줄),
`JdbcMediaAssetRepository`/`JdbcMediaAttachmentRepository`(전부 7줄 이하).

### 신설 `sql/` 서브패키지

각 feature 패키지의 `repository/jdbc/` 아래 `sql/` 서브패키지를 두고, 대상 저장소당
하나의 `public final class` 상수 클래스를 신설한다(`private` 생성자로 인스턴스화
금지, `public static final String` 상수만 포함).

- `feed/repository/jdbc/sql/InboxQuerySql.java`
- `feed/repository/jdbc/sql/SentPostQuerySql.java`
- `feed/repository/jdbc/sql/PostAnswerQuerySql.java`
- `direction/repository/jdbc/sql/ActiveUserPresenceSql.java` (`UPSERT` +
  `FIND_CANDIDATES_SQL`)
- `direction/repository/jdbc/sql/DirectionPostSql.java` (`INSERT` / `UPDATE`)
- `direction/repository/jdbc/sql/DirectionSchemeSql.java` (`SCHEME_INSERT` /
  `SCHEME_UPDATE` / `SEGMENT_INSERT` / `SEGMENT_UPDATE`)
- `direction/repository/jdbc/sql/PostAudienceSql.java` (`UPSERT`)
- `direction/repository/jdbc/sql/PostRecipientSql.java` (`INSERT` / `UPDATE`)

### 이동 원칙

- 이동 대상은 "정적 SQL 뼈대"뿐이다. `id == null ? INSERT : UPDATE` 같은 삼항
  분기, WHERE 필터 `switch`, cursor 페이지네이션, `StringBuilder` 조립 같은 동적
  조합 로직은 리포지토리 클래스에 그대로 둔다 — 옮기면 오히려 조립 순서가 Sql
  클래스와 리포지토리 두 곳에 흩어져 가독성이 떨어진다. 삼항의 각 분기(INSERT
  한 덩어리, UPDATE 한 덩어리)는 그 자체로 완전한 정적 SQL이므로 이동 대상이다.
- 상수 옆에 있던 WHY 주석(예: `JdbcInboxQueryRepository`의 `distance_m`/
  `distance_band` 상호배제 사유, `JdbcActiveUserPresenceRepository`의
  `ST_Azimuth` 인자 순서 사유)은 상수와 함께 그대로 옮긴다. `docs/adr/0002-jpa-jdbc-boundary.md`가
  raw JDBC를 선택한 이유("SQL 의도와 lock 대상을 가리지 않기 위함")를 지키기
  위함이다.
- 기존에 이름이 있던 상수는 이름을 바꾸지 않는다(`SELECT_CARD`,
  `CAN_VIEW_ANSWERS_SQL` 등). 원래 이름 없는 로컬 변수였던 것(예:
  `JdbcActiveUserPresenceRepository.findCandidates`의 `sql`, 2차 확장의 모든
  INSERT/UPDATE)은 상수로 빼면서 최소한으로 이름을 붙였다(`FIND_CANDIDATES_SQL`,
  `INSERT`/`UPDATE`, `UPSERT` 등).
- 리포지토리 클래스는 새 상수 클래스를 import해서 조회·조합·실행만 담당한다.
- 원본 SQL 텍스트 블록의 상대 들여쓰기를 그대로 보존해 옮긴다(`sed`로 줄 범위를
  추출하고, 메서드 로컬 변수 → 클래스 필드로 옮기며 깊이가 달라진 경우에만
  전체 블록을 동일한 탭 수만큼 일괄 이동한다) — 텍스트 블록의 공백 제거는
  블록 내부 최소 들여쓰기를 기준으로 계산되므로, 상대적 들여쓰기만 보존하면
  컴파일된 문자열이 완전히 동일하다.

## Explicit exclusions

- `JdbcNotificationRepository`, `JdbcSafetyRepository`는 다루지 않는다 — 길이의
  원인이 SQL이 아니라 한 클래스가 여러 엔티티를 담당하는 책임 분리 문제라서 이
  이슈의 해법(SQL 분리)으로는 풀리지 않는다.
- MyBatis, jOOQ 등 신규 SQL 관리 라이브러리를 도입하지 않는다.
- 외부 `.sql` 리소스 파일 + 클래스패스 로더 방식은 채택하지 않는다(동적 SQL 조합
  표현이 어색해지고 WHY 주석이 SQL과 호출부 사이에 쪼개진다 — 논의 후 Java 상수
  클래스 방식으로 결정).
- SQL 상수 이름 변경, 쿼리 성능 튜닝은 다루지 않는다.
- API, DTO, controller, endpoint 변경 없음.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `feed` 패키지 3개 Sql 클래스 이관 | Feed executor | 동적 조합 로직이 리포지토리에 남았는지, WHY 주석 보존 여부 리뷰 |
| `direction` 패키지 6개 Sql 클래스 이관(`ActiveUserPresenceSql`, `DirectionPostSql`, `DirectionSchemeSql`, `PostAudienceSql`, `PostRecipientSql`) | Direction executor | PostGIS 주석·인자 순서 보존 여부, 삼항 분기(id 유무)가 리포지토리에 남았는지 리뷰 |
| 회귀 검증 | Test orchestrator | 기존 단위/통합 테스트가 통과하는지, `FeedPersistenceBoundaryTest` 수정이 오탐 수정으로 타당한지 리뷰 |

## Existing user-owned changes

- `./harness start` 직전 `git status --short`는 비어 있었다. 보존할 다른 사람의
  미커밋 변경이 없다. `TASK.md`는 `h task-init --replace`로 #89 계약을 새로 썼다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- `./gradlew test`와 `./gradlew integrationTest`를 모두 통과시킨다. 이번 변경은
  SQL 텍스트 위치만 옮기므로 기존 테스트가 수정 없이 통과해야 회귀가 없다고 볼 수
  있다.

## Completion criteria

- [x] 9개 Sql 상수 클래스(1차 4개 + 2차 5개, `ActiveUserPresenceSql`은 1차·2차
      공용)가 `sql/` 서브패키지에 생성되고, 각 리포지토리가 이를 참조한다
- [x] SQL 실행 결과와 파라미터 바인딩 동작에 변화가 없다 (순수 위치 이동 —
      4개 통합 테스트 클래스가 무수정으로 전부 통과해 확인됨)
- [x] 기존 단위/통합 테스트가 그대로 통과한다 (`./gradlew test`,
      `./gradlew integrationTest`) — 단, `FeedPersistenceBoundaryTest`는 예외:
      기존 검증 로직이 "다른 feature의 jdbc/jpa 참조 금지"를 feature 이름 없이
      `.repository.jdbc.` 부분 문자열로만 체크해서, feed 자신의 신설
      `repository.jdbc.sql` 서브패키지까지 오탐하는 기존 버그였다. `answer`
      패키지의 동일 취지 테스트(`AnswerJdbcBoundaryTest`)가 이미 쓰던 "다른
      feature 이름을 명시" 패턴으로 맞춰 고쳤다 — 검증 의도(다른 feature
      비침투)는 그대로 유지되고, 같은 feature 내부 서브패키지만 허용하도록
      정밀해졌다.
- [x] 이동한 WHY 주석이 원래 의미를 유지한 채 새 위치에 남아 있다
- [x] `./harness check`, `./harness pr-ready --project-tests`가 통과한다
