# Test Plan: TEST-PLAN-GH-290-REPO-MAP

> Created at: `2026-09-29T18:34:21.471328+00:00`
> GitHub Issue: `#290`
> Status: Approved scope (conversation)

## 1. Objective
Prove that the local Java index and condition selector are deterministic, detect stale/unsafe inputs, preserve A, and never invoke actual models during offline checks.

## 2. Scope
Included: standalone CLI behavior through JUnit 5 synthetic temporary fixtures and fake product executables; real repo index spot checks. Excluded: application changes, real product execution, DB/infrastructure/external API tests, complete semantic resolution.

## 3. Source requirements
Issue #290, TASK.md, approved intake scenarios, 2026-09-30 user continuation. These tests validate local tooling, not randomized experiment effectiveness.

## 4. Risk inventory
P0: stale/mismatched index, partial overwrite, unsafe paths, A/B contamination, unintended model calls.
P1: deterministic ordering, overload/nested type ambiguity, hidden effective settings, honest output limits.

## 5. Unit scenarios
| ID suffix | Given / When / Then | Priority | Owner |
| --- | --- | --- | --- |
| UNIT-001 | Synthetic declarations/records/overloads/comments/strings; generate twice; stable syntax-only rows and no false declarations | P0 | map executor |
| UNIT-002 | Generated index; add/edit/delete source or change runtime/tool identity; check/query rejects stale | P0 | map executor |
| UNIT-003 | Existing valid index; invalid syntax; generation fails and preserves prior file | P0 | map executor |
| UNIT-004 | Symlink escape or unsafe output; generate; reject without external writes | P0 | map executor |
| UNIT-005 | Duplicate names/many matches; query; disambiguated bounded output and honest truncation/no-match | P1 | map executor |
| UNIT-006 | Same clean synthetic revision; prepare A and B; same common identity, different condition identity, B-only overlay | P0 | session executor |
| UNIT-007 | Dirty/wrong revision/invalid condition/reused output; prepare/launch; reject | P0 | session executor |
| UNIT-008 | Fake executable; inspect/explicit launch; no call by default, fresh exec only, manifest/changed-input checks | P0 | session executor |

## 6. Integration scenarios
No database/Spring/service integration added. CLI subprocess boundary checks above use synthetic local repositories and fixtures. Actual repository map generation/queries are manual offline acceptance evidence.

## 7. Cross-cutting scenarios
Database/transactions/external APIs: not touched. Concurrency: detect source snapshot changes; atomic publication; no concurrent writer guarantee unless implemented and tested. Failure recovery: old index remains valid on failed generation; no automatic product retry. Idempotency: deterministic index; run output refuses overwrite.

## 8. Test data and isolation
JUnit temporary directories only; synthetic Java text and fake product executable; no real sessions/credentials. No clock/random content in deterministic payload. Automatic test temp cleanup only; native product artifacts never created during checks.

## 9. Execution contracts
Map executor owns RepoMapToolTest.java and map implementation. Session executor owns HarnessSessionToolTest.java and session implementation. No overlapping writes. Run focused Gradle tests sequentially to avoid shared report races, then coordinator runs required repository gates.

## 10. Completion criteria
JUnit 5, @DisplayName, exact timestamp/source-scenario headers; P0 scenarios pass; independent review; test report includes unverified scope and failure distinctions.

## 11. Human approval
Reviewer: requesting user. Decision: implement B with offline validation and independent review; explicit intake/Issue/branch/TASK approval on 2026-09-30. Original intake enumerates map scenarios; session fake-executable checks are implementation of approved A/B execution-preparation scope. No live model approval granted.

## Follow-up scenarios (user request: class grouped map with dependencies)
- UNIT-009: same class declares fields, overloaded methods and explicit constructors; grouped query returns source-relative path, deduplicated method symbols and declared reference dependencies with field/constructor evidence. Exclude primitives, generic type parameters, annotations, initializer/body-only types and literal values; preserve generic/array referenced types as documented.
- UNIT-010: duplicate simple names and even same fully-qualified names across source roots remain distinct; bounded class results count complete groups, not truncated method rows; stable order and no-match result.
- UNIT-011: old schema/tool index rejected after extension; regenerate then legacy rows and class queries both pass. B overlay demonstrates new query without inserting full index; existing fake session tests still pass.
No new database/external integration boundaries. Map worker writes failing JUnit tests before implementation; final checks run serially. This extends the user-requested offline map validation, not live-run authorization.
