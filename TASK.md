# GitHub Issue #290 Task Contract

## Work gate
- Title: 기계적 Repo Map과 선택적 검색 절차로 하네스 B 준비
- GitHub Issue: #290
- Branch: chore/gh-290-repo-map-search
- Base branch: main
- TASK-ID: GH-290-REPO-MAP-SEARCH
- Baseline: ac381685fc71e4614253fc03e64de9cae27d7e27

## Objective
기존 A를 보존하고 같은 애플리케이션 코드에 기계적 Java Repo Map과 검색 우선 절차를 추가한 B를 준비한다. A/B 입력을 받는 로컬 새 세션 준비·실행 접점과 구성 manifest를 제공한다. 향후 팀 실험은 사전 무작위 배정 규칙을 따르며 수동 선택/교대는 smoke 용도다.

## Scope / Ownership
- Map executor: scripts/repo-map/RepoMap.java, run.py, README.md; src/test/java/com/dnd/qello/harness/RepoMapToolTest.java.
- Session executor: scripts/repo-map/session.py; docs/harness/REPO_MAP_SEARCH.md; src/test/java/com/dnd/qello/harness/HarnessSessionToolTest.java.
- Coordinator: TASK.md, docs/test-plans/gh-290-TEST-PLAN-GH-290-REPO-MAP.md, docs/reports/tests/gh-290-TEST-PLAN-GH-290-REPO-MAP.md.
- Generated source indices are ignored local backend artifacts, excluded from measurement records. No existing application or common harness policy files are changed.

## Explicit exclusions
Application behavior, infrastructure, global settings, allocation/statistics engine, collector implementation, product model calls, push/merge/deploy, source content in measurement data.

## Existing user-owned changes
Original main and new worktree were clean before task initialization. Original main remains unchanged. Prior baseline b7118f7 observation retained separately; current main advanced before this work began.

## Validation
- TEST-PLAN-GH-290-REPO-MAP: existing approved intake offline scenarios plus synthetic fresh-session/manifest checks implementing the user's continuation scope.
- ./gradlew test --tests '*RepoMapToolTest' --tests '*HarnessSessionToolTest'
- ./harness check
- ./harness pr-ready --project-tests
- npm run hooks:validate
- git diff --check
- Actual local generation, repeat generation, stale detection and selective query checks; independent exact-scope implementation review.

## Completion criteria
- [x] AST declarations/relative paths/imports/syntactic relationships and explicit limitations
- [x] Determinism, input/tool/runtime identity, stale detection and safe failure
- [x] A/B selector and matching common source/configuration manifests
- [x] Fresh-session procedure; B-specific context only in B; no live calls in preparation
- [x] Offline evidence, independent review, required gates and honest blocked checks

## Authorization
User requested B implementation/offline validation/independent review, then explicitly approved the proposed draft/Issue/fields/branch/TASK connection on 2026-09-30. Separate fresh approval required for actual product model execution.

## Follow-up: grouped symbols and declared dependencies
User supplied a class-grouped path/symbols/dependencies example and requested proceeding after the extraction gap was explained. Extend the existing map, preserving the row query default, with `query --format classes`. Include direct field and explicit constructor-parameter reference types as syntax-only dependencies; no method body, Spring binding or resolved call graph. Package/file ownership must prevent duplicate-name overwrites. Exclude annotation values/initializers and report declared-type evidence. Keep application/A unchanged. Map executor owns existing map/helper/test/docs; session overlay/documentation may be updated to expose the new optional query. Rerun focused and required checks, regenerate B map and replace prepared manifests with fresh output directories because identities change. Live model execution remains excluded.

User explicitly confirmed the follow-up dependency scope: declared field and constructor types (recommended option). Full method call relationships and Spring binding remain excluded.

## Finalization and bounded smoke evidence
User subsequently explicitly authorized final checks, separate pull requests with a Korean backend PR, merge when repository protections pass, preservation of ignored local records, and archival of unused worktrees. This supersedes the earlier commit/push/merge exclusion only for publication; it does not authorize additional live experiments or default B activation. B remains optional: merging these tools does not activate B, change global instructions, or inject an index into ordinary sessions. The randomized allocator and statistical analysis remain unimplemented.

A separately approved private Docker smoke completed four source-exploration sessions and eight bounded CLI calls, with operational collection and independent review passing. Both B sessions used a map query. Human answer correctness/completeness remains pending; usage covers only partial observed resume intervals, not complete task totals or cost. This manual two-question check establishes neither B superiority nor adoption readiness. The private runner and native evidence remain ignored local artifacts outside this product change. This finalization executes no additional model calls.
