# GitHub Issue #288 Task Contract

## Work gate
- Title: ERD·DBML·schema manifest를 Flyway V28 기준으로 현행화
- GitHub Issue: #288
- Branch: docs/gh-288-erd-dbml-refresh
- Base branch: main
- Task ID: TASK-GH-288-ERD-DBML-REFRESH
- Design ID: 해당 없음 (문서 동기화)
- 승인: 2026-09-29 사용자가 기존 계획에 대해 "진행해줘" 요청. 별도 worktree 및 Docs / In Progress / P2 / Sprint 미지정 선택.

## Objective
Flyway V1~V28의 최종 스키마를 기준으로 백엔드 ERD·DBML·manifest의 구조·관계·설명·집계·체크섬을 맞춘다.

## Scope
- docs/product/data-model/direction_communication.dbml
- docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md
- docs/product/data-model/schema-manifest.md
- docs/superpowers/plans/2026-09-26-erd-dbml-refresh.md (승인된 계획 및 실행 기록)
- TASK.md (계약·최종 검증 결과)
- 임시 작업공간의 비교·검증 산출물. 영구 실행 증거는 manifest와 TASK에 요약한다.

## Explicit exclusions
Java·테스트 소스, 기존/신규 migration, vault 원본, 인프라·운영 DB, CI 자동화.
과거 검증 보고서와 원본 migration 이력은 보존한다.

## Ownership
- 오케스트레이터: 계약·계획·진행 기록, 결과 통합
- 문서 실행 에이전트: DBML, 이어서 ERD와 manifest
- 독립 검증 에이전트: 소스 수정 없이 실제 diff·검사 결과·catalog 대조

## Existing user-owned changes
기존 main에는 이전 대화에서 만든 계획 파일만 untracked로 있었다. 해당 파일과 main을 원래 폴더에 보존하고, 별도 worktree에 사본을 가져왔다.

## Plan and evidence
- docs/superpowers/plans/2026-09-26-erd-dbml-refresh.md
- docs/adr/0001-database-schema-ownership.md
- docs/adr/0002-jpa-jdbc-boundary.md
- 현행 기준 commit: b7118f7628813bed86a15f9eb5b46063b4400a1d

## Validation
승인된 계획의 기존 테스트·임시 DB catalog 검증을 수행한다. 새 JUnit 코드는 작성하지 않는다.

현재 요청에서 승인한 범위는 기존 테스트 실행과 필수 검사다. 새 동작이나 테스트는 추가하지 않는다.
기존 Repo Map 테스트는 생성·freshness·조회·클래스 그룹·구문 한계와 실패 처리를 검증한다.
실행기 전용 테스트는 기능 제거와 함께 삭제하고 원본 테스트 계획·보고서는 역사적 증거로 보존한다.
애플리케이션, DB, 트랜잭션, 동시 요청, 외부 API, 인프라 동작 변경은 없다.

```bash
./gradlew integrationTest --tests com.dnd.qello.FlywayMigrationIntegrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```
추가: 고정 버전 DBML parser, 전체 임시 DB catalog와 구조 대조, SHA-256, migration 무변경.

필수 검사의 실제 결과와 실행할 수 없는 항목은 PR에 구분해서 보고한다.

## Common policy audit

- `git diff --name-status ac38168..origin/main`에서 기존 Repo Map 변경은 도구·선택형 문서·전용 테스트·당시 TASK·테스트 계획/보고서에 한정되어 있었다.
- `AGENTS.md`, `CLAUDE.md`, `.agents/`, `agents/` 및 그 외 공통 파일 변경은 없었다. 공통 정책에 B를 자동 적용한 변경은 발견되지 않았다.
- Repo Map 실행기 참조는 삭제 대상과 해당 사용 문서 및 기존 테스트 계획/보고서에 한정되어 있었다. 역사적 계획/보고서의 참조는 수정하지 않는다.
- 기존 `scripts/experiments/codex-agents-eval.zsh`와 관련 2026-09-10 계획은 Repo Map 도입 전 별개 실험으로 이번 범위 밖에 두고 보존한다.
- 기존 A/common 정책은 변경하지 않고 B의 명시적 선택 조건을 문서에 유지한다.

## Completion criteria
- [x] 제품·백엔드 52개 테이블의 최종 정의와 관계를 DBML에 반영한다.
- [x] Spring Session 2개는 별도 표시하고 전체 54개 catalog를 검증한다.
- [x] ERD의 낡은 도입부·경로·업무 설명을 실제 구현 근거와 맞춘다.
- [x] manifest의 현행 inventory·체크섬과 과거 이력을 구분한다.
- [x] 문법·catalog·기존 테스트·harness 결과와 독립 검토를 기록한다.
- [x] 미실행·실패 항목을 숨기지 않고 최종 상태를 보고한다.

## 실행 증거 (2026-09-29)
- Java 21에서 기존 FlywayMigrationIntegrationTest 11개 통과.
- `./harness test-run --id TEST-PLAN-GH-288-ERD-DBML-REFRESH`: 단위 1,064개 / 통합 742개, 실패·오류·skip 0.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate` 통과.
- 별도 빈 PostgreSQL 16/PostGIS 3.5에 기존 Flyway V1~V28 적용 성공. catalog 추출 후 임시 컨테이너 제거.
- catalog: 54 tables / 454 columns / 166 indexes / 12 user triggers / 13 user functions.
- `pg_constraint` 354행 중 8행은 제약 트리거이며 일반 테이블 제약은 346개(PK 54 / FK 82 / UNIQUE 23 / CHECK 187)다.
- migration 파일의 실행 전후 SHA-256 일치. 운영 DB는 조회·수정하지 않음.
- DBML 독립 검토 및 ERD·manifest 최종 검토에서 필수 항목이 통과했다. 초기 리뷰의 트리거 보장 범위·관계도 카디널리티·신고/수동검토 연결 설명을 수정했고, 현행 매칭 설명도 보충했다.

- DBML 독립 검토: spec PASS / quality PASS. 52 tables, 444 columns, 81 FK, 161 product/backend indexes, 187 CHECK, 44 partial predicates, 10 DESC notes, 12 trigger names 대조 통과.
- DBML parser: `@dbml/core@10.2.0`; 표현 불가 SQL 조건은 Notes에 정확한 정의로 보존.

## Final report
- status: PASS
- issue_number: 288
- task_id: TASK-GH-288-ERD-DBML-REFRESH
- design_id: 해당 없음
- changed_files:
  - docs/product/data-model/direction_communication.dbml
  - docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md
  - docs/product/data-model/schema-manifest.md
  - docs/superpowers/plans/2026-09-26-erd-dbml-refresh.md
  - TASK.md
- executed_checks: 기존 Flyway 11개, 단위 1,064개, 통합 742개, 임시 V1~V28 DB 전체 catalog, DBML parser/독립 구조 대조, 관계도 50개 edge의 nullable·유일성, 현재 링크 34개, SHA-256, migration 무변경, 하네스/PR readiness/Husky/공백 검사, 독립 문서 검토.
- passed_checks: 위 필수 검사 전부 통과. 테스트 실패·오류·skip 없음.
- failed_checks: 미해결 필수 실패 없음. 최초 문서 리뷰의 F1~F4는 수정 후 재검토 통과.
- blocked_checks: 없음.
- assumptions: 로컬의 빈 PostgreSQL/PostGIS에 적용한 V1~V28 catalog를 현행 문서의 실행 근거로 사용한다. Spring Session 두 테이블은 DBML 외 framework 범위로 manifest에 별도 집계한다.
- risks: DBML parser가 지원하지 않는 CHECK·GiST·partial/generated/지연 제약 의미는 Notes에 보존돼 있으므로 관계도만으로 SQL 계약 전체를 읽을 수 없다. 운영 DB 상태·외부 vault·실제 외부 공급자 전달·performance 태그 테스트는 이번 검증 범위가 아니다.
- required_human_decisions: 없음. 문서 완료 보고 후 사용자가 commit·push·PR 생성을 명시적으로 요청했다.

## 최종 문서 해시
- DBML: `8d11d7e861e574843af8311621f917438bda81eeb0b23f2143c169b8aa03ca31`
- ERD: `87a40bfa4726d3059a9fd055a8f6b0a3ad7ea2b5a116d20eeec6b8100f69d170`
- manifest 현행 행과 일치하며 과거 해시는 이력으로 보존했다.

## 실행 중 판단
- 문서 구현 완료 단계에서는 미커밋 상태로 보고했다. 이후 사용자의 커밋·PR 생성 요청으로 해당 후속 절차를 승인 범위에 포함했다.
- 문서 편집과 별도로 먼저 만든 DB catalog·기존 테스트 결과를 재사용했다. 런타임·migration·테스트 소스와 migration 해시가 그대로임을 확인했다. 해당 소스가 바뀌면 재검증해야 한다.
- 최종 리뷰의 두 문구 권고도 정밀하게 수정했다. FK 관계선 설명과 최신 case 조회 후 상태 판정 순서를 명확히 했으며, 추가 검토는 해당 문장과 해시에만 한정했다.

## 기존 작업 보존
원래 저장소는 main이며 이전 계획 파일의 바이트가 그대로 보존돼 있다. 현재 결과는 별도 worktree의 docs/gh-288-erd-dbml-refresh 브랜치에 있다. 문서 구현 완료 당시에는 커밋·push·PR을 생성하지 않았으며, 후속 요청으로 생성 절차를 진행한다.

## Commit / PR handoff
- 승인 근거: 사용자 후속 요청 "커밋 PR 생성 해줘".
- 단일 목적: Flyway V28 기준 ERD·DBML·manifest와 그 계획·검증 기록 동기화. 문서 간 체크섬과 근거를 함께 검토하도록 5개 파일을 한 커밋으로 묶는다.
- commit: `docs(database): ERD와 DBML을 Flyway V28 기준으로 최신화 (#288)`
- PR title: `docs: ERD와 DBML을 Flyway V28 기준으로 최신화`
- PR base: main; ready for review; 명시적 reviewer 지정 없음; Project In Progress 유지.
- Push 전에 `./harness sync`와 필수 검증을 실행하고 Hook을 우회하지 않는다.
