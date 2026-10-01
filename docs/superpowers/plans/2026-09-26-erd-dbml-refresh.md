# ERD·DBML 현행화 계획

> **For agentic workers:** 실행 시 `superpowers:subagent-driven-development`를 사용한다. 저장소 역할 계약에 따라 문서 실행 담당과 독립 검증 담당을 분리한다. 2026-09-29 사용자가 이 계획의 실행을 승인했다.

**Goal:** 현재 main의 Flyway V1~V28이 정의하는 스키마와 ERD·DBML·schema manifest의 차이를 해소한다.

**Architecture:** ADR-0001에 따라 Flyway history를 실행 스키마의 권위로, DBML을 논리 설계 원본으로, ERD를 설명 문서로 구분한다. 이번에는 이미 반영된 구현의 누락을 문서에 역반영한다. 논리 모델과 구현이 실제로 충돌하는 항목은 차이와 근거를 기록하고 별도 의사결정 대상으로 남긴다.

**Tech Stack:** PostgreSQL/PostGIS, Flyway SQL, DBML, Markdown, 기존 Gradle/Testcontainers 검증.

**Spec:** 사용자 요청(ERD 문서와 DBML 최신화 계획), `AGENTS.md`, `docs/adr/0001-database-schema-ownership.md`, `docs/harness/TASK_DOCUMENT_ROUTING.md`.

## 상태와 전제

- CONFIRMED: 2026-09-29 사용자가 계획 실행을 요청했다. Issue 생성·문서 갱신·포함된 검증을 수행하며, commit·push·PR은 제외한다.
- CONFIRMED: 2026-09-26 조회 시 로컬 main과 GitHub main의 HEAD는 `b7118f7628813bed86a15f9eb5b46063b4400a1d`로 같다. 시작 시 작업 트리는 깨끗했다.
- CONFIRMED: 계획 당시 TASK는 이전 #281 계약이었다. 실행 시 Project draft를 #288로 전환하고 `docs/gh-288-erd-dbml-refresh` 브랜치와 TASK-GH-288-ERD-DBML-REFRESH 계약을 연결했다.
- CONFIRMED: 사용자가 백엔드 ERD·DBML·manifest 최신화를 선택했다. 별도 vault 원본은 이번 수정 범위에서 제외한다. 이전 문서의 byte-for-byte 복사 이력과 이번 저장소 유지본 갱신을 명확히 구분한다.
- ASSUMED: Spring Session 테이블 두 개는 기존 방침대로 DBML에서 제외하고 manifest에 별도 표기한다.
- Project item: `PVTI_lADOBD3v1M4BfKwozg9W8K0` → Issue #288. 사용자 선택: Docs / In Progress / P2 / Sprint 미지정, 별도 worktree 사용.

## Global Constraints

- 실행 시작 시 최신 `origin/main`에서 Issue 번호가 포함된 브랜치를 만들고, Issue·브랜치·`TASK.md` 식별자를 일치시킨다.
- 이미 적용된 migration, 애플리케이션 코드, 운영 DB와 과거 검증 보고서를 변경하지 않는다.
- DBML에서 표현하기 어려운 지연 제약·partial/expression index·trigger·generated column은 설명과 원본 migration 경로로 추적한다.
- DB 제약이 보장하는 것과 서비스/조회 계층이 보장하는 것을 구분한다.
- 기존 이력 체크섬은 보존하고 현행 파일 체크섬을 별도 갱신한다. 접근하지 않은 외부 원본과 일치한다고 표시하지 않는다.
- 문서 수정용 JUnit 테스트는 신설하지 않는다. 기존 검사와 문법·카탈로그·체크섬 대조를 사용한다. 테스트 코드 변경이 필요해지면 별도 범위와 테스트 계획을 먼저 확정한다.
- 테스트 실행 전 저장소의 사람 승인·harness-test-run·보고 계약을 적용한다. 계획 작성 단계에서는 테스트를 실행하지 않았으며 실행 결과는 TASK와 manifest에 별도로 기록한다.

## 확인한 차이

마이그레이션 SQL의 CREATE TABLE과 DBML Table 선언을 정적으로 비교한 결과다. 실제 DB catalog를 조회한 결과는 아니다. V1~V28에서 DROP TABLE 또는 RENAME TO는 발견하지 못했다. PostgreSQL의 비인용 식별자 소문자 정규화를 적용한다.

| 항목 | 현재 상태 | 계획 |
| --- | --- | --- |
| 테이블 범위 | migration 54개, DBML 31개 | 제품·백엔드 52개와 프레임워크 2개로 범위를 명시하고 실제 catalog에서 확정 |
| V10~V18 필터링·운영 | DBML에 13개 테이블 누락 | 아래 목록의 구조·관계·상태·제약 반영 |
| V19 신고 케이스 | DBML에 3개 테이블 누락 | 케이스·증거·이벤트와 기존 report/notification 확장 반영 |
| V20 운영 감사 | operator_action_audit 누락 | 감사 테이블과 관계 반영 |
| V21~V22 계정 | 닉네임 유일 인덱스·프로필 이미지 컬럼 누락 | 표현식/조건과 소유자 복합 FK 반영 |
| V24 알림 읽음 기준선 | notification_seen_state 누락 | 개별 알림 read_at과 사용자별 seen_at 의미 구분 |
| V28 푸시 | 묶음·멤버·일일 예산 3개 테이블 누락 | 멱등성·예산·상태 제약 반영 |
| ERD 도입부 | 2026-08-07 초안, 실제 백엔드가 없다는 설명, 오래된 경로 | 현재 구현 기준·기준 commit·migration 범위와 실제 경로로 갱신 |
| manifest | 이전 baseline과 후속 일부 변경이 혼재 | 현행 inventory와 이력 분리, 분류별 집계 재산출 |
| 체크섬 | 현재 DBML·ERD SHA-256이 manifest에 없음 | 최종 편집 후 두 현행 해시 기록 |
| 검증 범위 | FlywayMigrationIntegrationTest의 EXPECTED_TABLES와 제약 집계가 일부 테이블만 포함 | 기존 테스트 통과와 전체 스키마 대조 결과를 별도로 보고 |

누락된 제품·백엔드 테이블 21개:

- V10: `filter_release`, `filter_job`, `filter_job_status_history`, `filter_decision`, `manual_review_case`, `appeal_case`
- V11: `release_promotion_history`
- V14: `filter_release_retry_gate`
- V15: `snapshot_health`, `snapshot_health_probe_result`, `snapshot_emergency_migration_history`
- V16: `manual_review_priority_evaluation`
- V17: `notification_event`
- V19: `report_case`, `report_content_snapshot`, `report_case_event`
- V20: `operator_action_audit`
- V24: `notification_seen_state`
- V28: `push_dispatch_group`, `push_dispatch_group_member`, `push_daily_budget`

의도적으로 별도 관리하는 프레임워크 테이블: `spring_session`, `spring_session_attributes`. Flyway/PostGIS 자체 관리 테이블은 제품 테이블 수에 포함하지 않는다.

## Review Focus

1. 최종 상태: CREATE만 읽지 않고 뒤따르는 ALTER·DROP CONSTRAINT·재생성·데이터 전환을 순서대로 반영한다.
2. 관계 정확성: 복합 FK의 컬럼 순서, nullable, 참조 동작을 단일 FK로 단순화하지 않는다.
3. 인덱스 정확성: partial 조건·식·정렬과 UNIQUE CONSTRAINT가 자동 생성하는 인덱스를 구분한다.
4. 설명 정확성: 답변 공개·검토 완료·슬롯 해제·읽음·알림 생성 시점을 현재 구현 근거와 대조한다.
5. 출처 정확성: 과거 검증·vault 사본 이력을 이번 V28 검증이나 최신 원본 동기화 증거로 사용하지 않는다.

## Task 1: 작업 계약과 스키마 차이표 확정

**담당:** 오케스트레이터. **산출물:** Project draft 계획, 실행 시 Repository Issue와 `TASK.md`, migration별 차이표.

- [x] Project draft 제목을 `ERD·DBML·schema manifest를 Flyway V28 기준으로 현행화`로 제안하고 중복 draft를 확인한다. 실제 실행을 요청받은 시점에만 Issue로 전환한다.
- [x] 실행 Issue의 type은 `docs`로 제안한다. `./harness start`를 통해 최신 origin/main에서 분기하고 `./harness task-init`으로 계약을 기록한다.
- [x] 계약의 수정 허용 파일을 아래 세 문서와 `TASK.md`로 제한한다. 사용자가 선택한 범위에 따라 vault 원본은 제외한다.
- [x] V1~V28을 숫자 순으로 읽어 `대상 오브젝트 / 변경 migration / 최종 정의 / DBML 위치 / ERD 위치 / 표현 차이` 표를 만든다. V12·V26처럼 일부 반영된 migration도 누락 여부를 확인한다.
- [x] V13~V18의 filter_job·manual_review_case·appeal_case·outbox_event 변경, V19/V25/V27의 신고 상태·사유·SLA·증거 보존 변경까지 최종 정의에 합성한다.
- [x] 알려진 `ck_direction_post_answers_read_at` 누락과 `ck_answer_edit_count_matches_edited_at` 대 `ck_answer_edit_count_edited_at` 이름 차이를 재확인한다. 문서 표현 오류인지 제품 정책 충돌인지 구분한다.

**완료 조건:** 모든 migration이 차이표에서 처리되며, 문서 최신화로 해결할 항목과 별도 결정이 필요한 항목이 구분된다.

## Task 2: DBML 구조와 관계 최신화

**담당:** 문서 실행 에이전트. **수정:** `docs/product/data-model/direction_communication.dbml`.

- [x] 위 21개 테이블을 추가하고 필터링·신고·운영 감사·알림/푸시 TableGroup으로 묶는다. 기존 그룹 구성과 색상 관례를 따른다.
- [x] 컬럼 타입·길이·nullable·default·identity·PK·UNIQUE·CHECK·FK를 최종 migration 정의와 맞춘다.
- [x] 기존 테이블의 profile_image_media_id, report.case_id/sub_reason_code, notification.report_id, outbox 상태/이벤트 제약 등 누락을 반영한다.
- [x] 닉네임 유일성, 신고자 답변 차단 조회, 알림 목록 정렬, 푸시 묶음 멱등성·처리 대상 index의 실제 조건과 식을 기록한다.
- [x] 복합 FK는 선택한 DBML parser가 지원하는 문법으로 표현하고, 표현 불가 항목만 Note에 SQL 원본과 함께 남긴다. 기존 '복합 참조를 표현하지 못한다'는 설명도 재검증한다.
- [x] 기존 Note의 낡은 논리를 제거하거나 이력으로 표시한다. 미래 설계나 아직 구현되지 않은 기능을 현행 구조에 섞지 않는다.
- [x] 실행 환경에서 버전을 고정한 DBML parser로 전체 파일을 parse하고 Ref/TableGroup의 모든 대상이 존재하는지 확인한다. 검증용 의존성은 격리된 임시 디렉터리에서 사용하고 저장소 의존성은 변경하지 않는다.

**완료 조건:** DBML은 가정한 범위에서 52개 테이블을 포함하고, 실제 catalog 대조에서 확정한 모든 차이를 반영한다. 문법 검사 결과와 표현상 예외 목록이 존재한다.

## Task 3: ERD 설명과 manifest 동기화

**담당:** 같은 문서 실행 에이전트. **수정:** `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md`, `docs/product/data-model/schema-manifest.md`.

- [x] ERD 도입부에 현재 기준 commit·갱신일·V1~V28 범위를 적고 ADR-0001의 권위 구분을 적용한다. 경로는 이 저장소 실제 경로를 사용하며 외부 자료는 외부 출처라고 명시한다.
- [x] 전체 구조 개요와 도메인별 관계도·테이블 설명에 신규 영역을 추가한다. 핵심 흐름을 읽을 수 있도록 전체 52개 테이블을 단일 대형 그림에 몰아넣지 않는다.
- [x] 답변 moderation과 공개 시점, 수신 용량 반환, 신고→케이스→수동 검토, 알림함 seen/read, 푸시 묶음/예산 설명을 관련 application 서비스·JDBC 쿼리·제품 문서와 대조한다. DB 구조만으로 업무 정책을 추측하지 않는다.
- [x] 과거 변경·검증 기록은 이력으로 보존하고 현재 규칙과 구분한다. 새 카탈로그 검증 결과에 과거 검증 날짜나 성공 여부를 재사용하지 않는다.
- [x] manifest 현행 summary를 전체 테이블 범위로 재작성하고 제품/백엔드와 프레임워크를 나눈다. PK/FK/UNIQUE/CHECK/index/function/trigger 수는 동일한 포함 범위로 집계한다.
- [x] DBML과 ERD 편집이 끝나면 아래 명령으로 해시를 계산한다. 이력 행을 덮어쓰지 않고 현행 snapshot 행과 검증 근거를 갱신한다.

```bash
shasum -a 256 docs/product/data-model/direction_communication.dbml docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md
```

**완료 조건:** 세 문서의 기준·테이블 범위·관계·검증 시점이 일치하고, 확인하지 않은 vault 동기화나 운영 DB 일치 주장이 없다.

## Task 4: 독립 검증과 완료 보고

**담당:** 문서를 수정하지 않은 독립 검증 에이전트. **소스 수정:** 없음.

- [x] 작업 전후 `src/main/resources/db/migration` 파일의 체크섬과 diff를 대조해 원본 migration이 변경되지 않았음을 확인한다.
- [x] 승인된 계획과 테스트 실행 계약을 확인한 뒤 기존 Flyway 검증을 실행한다.

```bash
./gradlew integrationTest --tests com.dnd.qello.FlywayMigrationIntegrationTest
```

- [x] 임시 PostgreSQL/PostGIS 환경에 기존 프로젝트 migration 실행 경로로 V1~V28을 적용하고 전체 catalog를 대조한다. 운영 DB에 연결하지 않는다. 테이블·컬럼·constraint·index·trigger를 전수 추출하며, 테스트 내부 부분 집계를 전체 검증으로 간주하지 않는다.
- [x] `information_schema.columns`의 타입/nullable/default, `pg_constraint`와 `pg_get_constraintdef`의 제약, `pg_indexes.indexdef`의 partial/표현식/정렬, `pg_trigger`의 사용자 trigger를 차이표 및 manifest와 비교한다. 시스템·PostGIS·Flyway 관리 오브젝트는 명시적으로 제외한다.
- [x] parser 성공, 52개 제품/백엔드 테이블 및 2개 프레임워크 테이블의 coverage, 끊어진 참조, 복합 FK, 인덱스 조건, 업무 설명의 근거, SHA-256을 독립적으로 확인한다.
- [x] Issue 브랜치에서 저장소 필수 검사를 실행한다. 앞선 검사가 통과해도 저장소 필수 전체 검사를 생략하지 않는다.

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- [x] 필수 검사 실패는 FAIL, 환경·승인 부족으로 미실행된 필수 검사는 BLOCKED로 보고한다. 실제 catalog를 확인하지 못하면 'V28 전체 검증 완료'로 보고하지 않는다.
- [x] 최종 보고에 status, issue_number, task_id, design_id(해당 없음), changed_files, executed/passed/failed/blocked_checks, assumptions, risks, required_human_decisions를 기록한다.

**완료 조건:** 세 문서가 같은 최종 스키마를 설명하고 필수 검증이 통과하며 독립 검토가 완료된다. 커밋·PR은 후속 요청과 저장소 절차를 따른다.

## 범위 밖과 후속 후보

- 신규 migration, 실행 DB 수정, Java 동작 변경, 정책 변경은 이번 문서 작업에 포함하지 않는다.
- 전체 스키마 drift를 지속 검출하는 CI 도입과 기존 부분 catalog 테스트 확대는 별도 Project draft 후보로 둔다. 이번 동기화와 자동화 구축을 한꺼번에 확장하지 않는다.
- 롤백은 이번 문서 변경만 되돌리는 방식이다. DB 복구나 과거 migration 수정은 필요하지 않다.

## 최초 계획 작성 결과 (2026-09-26 이력)

- status: PASS (읽기·분석 및 계획 작성 범위)
- issue_number / task_id / design_id: 미할당 / 미할당 / 해당 없음
- changed_files: 이 계획 파일 1개
- executed_checks: 작업 트리·브랜치 확인, GitHub main SHA 비교, 열린 Issue 조회, SQL/DBML 정적 테이블 비교, 문서 체크섬 대조, 기존 테스트의 집계 범위 확인
- passed_checks: main 일치, 시작 시 변경 없음, 차이표와 수정·검증 범위 작성
- failed_checks: 계획 작성 자체의 실패 없음. 발견한 문서 불일치는 위 차이표에 기록
- blocked_checks: 없음(계획 범위). 구현 검증·DB 실행·JUnit·전체 harness 검사는 실행하지 않음
- assumptions: Spring Session은 DBML에서 제외하고 manifest에서 별도 집계
- risks: 외부 vault와 출처 혼동, 일부만 집계하는 기존 테스트, 표현식/복합 제약 누락
- required_human_decisions: 계획 작성 당시에는 실행 승인이 필요했으며 2026-09-29 진행 요청으로 확인됨. 문서 범위·worktree·Project 필드는 사용자 답변으로 확정됨


## 실행 완료 기록 (2026-09-29)

- Issue #288, TASK-GH-288-ERD-DBML-REFRESH, branch `docs/gh-288-erd-dbml-refresh`.
- 사용자 선택대로 별도 worktree를 사용하고 원래 main과 계획 파일은 보존했다.
- Task 1~4 완료. 백엔드 문서 세 개, 계획 실행 기록, TASK만 변경했다.
- DBML: 52 tables / 444 columns / 81 FK, Spring Session 2 tables 별도 관리.
- 빈 DB: V1~V28, 54 tables / 454 columns / 일반 table constraints346 + constraint-trigger8 / indexes166 / functions13 / triggers12.
- 테스트: Flyway11, unit1064, integration742 통과. 하네스·PR readiness·Husky 통과.
- DBML parser와 독립 catalog 대조, 현재 링크34개, 관계도50개 edge의 cardinality/optionality, 파일 해시, migration 보존 검증 통과.
- 독립 문서 리뷰에서 발견한 F1~F4를 반영하고 재검토했다. SQL과 서비스의 보장 범위, nullable/unique 관계, opaque manual-review 연결, 현재 매칭 경계를 명확히 했다.
- 최종 상세 상태·실행 증거·표현 한계·판단은 `TASK.md`와 현행 `schema-manifest.md`에 기록한다.
- 실행 결과: PASS. commit·push·PR은 수행하지 않았다.


## 커밋·PR 후속 요청

문서 구현 완료 보고 후 사용자가 "커밋 PR 생성 해줘"라고 요청해 commit·push·PR 생성을 승인했다. 위 계획의 구현 단계 제외 범위와 미커밋 보고는 그 요청 전 시점의 기록이다. 현행 작업 계약은 TASK.md의 Commit / PR handoff 절을 따른다.
