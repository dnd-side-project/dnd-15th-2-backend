# Test Report: TEST-PLAN-GH-382-DEPENDENCY-SCAN

> Created at: `2026-11-23T22:39:58+09:00`
> GitHub Issue: `#382`
> Branch: `ci/gh-382-vulnerability-scan`
> Commit: `15bae1e2`

## 1. Executive summary

- Result: `PARTIAL`; repository status: `BLOCKED` (CI-only evidence and human review remain).
- Tested scope: 승인된 E1 파일 구현, runtimeClasspath 전체 좌표 대조, 실제 OSV main baseline/취약 fixture/처리 후 재스캔, 정책 단위 검사와 애플리케이션 회귀 검사.
- Unverified scope: 일반/fork PR 실행, 실패 artifact/취소의 실제 CI 동작, cold/warm 각 3회 성능 및 required check 등록.
- Release recommendation: 로컬 검증은 통과했다. Spring MVC CRITICAL 2건의 임시 예외는 사람의 위험 수용·완화책 리뷰 전 승인으로 취급하지 않는다. 실제 CI 증거 전 전체 PASS를 선언하지 않는다.
- issue_number: `382`; task_id: `TEST-PLAN-GH-382-DEPENDENCY-SCAN`; design_id: 해당 없음.
- changed_files: `.github/workflows/dependency-scan.yml`, `config/security/dependency-lock.init.gradle`, `config/security/dependency-exceptions.json`, `scripts/check-dependency-scan.py`, `scripts/test-dependency-scan.py`, `docs/security/dependency-scan.md`, `build.gradle`, 본 보고서.
- `TASK.md`와 승인된 계획의 기존 변경은 보존했다. commit/push/PR/설정 변경은 하지 않았다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Java 21 toolchain; JAVA_HOME openjdk 21.0.13 |
| Spring Boot | 3.5.16; 변경 없음 |
| Gradle | wrapper 8.14.3 |
| Python | 로컬 3.14.10, 정책 검사는 표준 라이브러리만 사용 |
| OSV-Scanner | 2.7.1; osv-scalibr 0.5.3 |
| Database | dependency scan에는 애플리케이션 DB 불필요; 기존 통합 검사는 Testcontainers |
| Test runner | JUnit 5; 정책은 기존 Python 스크립트 관례를 따라 unittest |
| Host | macOS arm64; GitHub ubuntu runner 시간과 비교할 수 없음 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `python3 scripts/test-dependency-scan.py` | PASS, exit 0 | 6 methods, 다중 subcase | 0.001s | UNIT-001~006 |
| `dependencyScanLock --write-locks` + CI init script, clean origin/main snapshot | PASS, exit 0 | 172 좌표 | Gradle 3s | main SHA `15bae1e2f13672e77d1ed68b7b52a2042289f403` |
| baseline `check-dependency-scan.py` | 예상 정책 실패, exit 1 | 8 advisory; 기준 초과 5 | 1.207s | `baseline/summary.json` |
| runtimeOnly log4j-core 2.14.1 fixture 해석 | PASS, exit 0 | core + 전이 api, 2 좌표 | Gradle 2s | 격리 workspace |
| 취약 fixture 실제 scan | 예상 정책 실패, exit 1 | 기준 초과 3 | 시간 목표 평가에 사용하지 않음 | CVE-2021-44228, CVE-2021-45046 등 검출 |
| fixture CVE-2021-44228 한정 예외 scan | 예상 정책 실패 유지, exit 1 | 제외 1, 나머지 기준 초과 2 | 1.218s | 다른 advisory는 제외하지 않음 |
| 연결 거부, 빈 HOME, 실제 scanner | 예상 도구 실패, 정책 exit 2 / scanner 127 | 무검출 통과 금지 | 22.758s | `network-failure/summary.json` |
| 손상 JSON scanner double | 예상 입력 실패, 정책 exit 2 | JSONDecodeError | 300s 이내 | `corrupt-results/summary.json` |
| 손상 config, 실제 scanner | 예상 도구 실패, 정책 exit 2 / scanner 127 | 예외로 도구 오류 우회 불가 | 0.515s | `invalid-config/summary.json` |
| 선언을 2.14.0으로 변경 후 lock 재생성 | 해석 exit 0 / 정책 exit 1 | lock·해석 좌표·실제 결과 모두 2.14.0 | 해석 3s / scan 0.951s | `changed-declaration/summary.json` |
| upgrade 후 lock 생성 / scan | PASS, 각각 exit 0 | 172 좌표, 미처리 0, 예외 2 | 해석 4s / scan 1.321s | `remediated/summary.json` |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-run --id TEST-PLAN-GH-382-DEPENDENCY-SCAN` | PASS, exit 0 | 아래 두 JUnit suite | test 44s / integration 2m38s | 보고서 scaffold 생성 |
| 위 명령의 `./gradlew test` | PASS, exit 0 | 203 suites / 1506 tests, 실패·오류·skip 0 | XML 실행 시간 합 33.833s | `build/test-results/test` |
| 위 명령의 `./gradlew integrationTest` | PASS, exit 0 | 112 suites / 874 tests, 실패·오류·skip 0 | XML 실행 시간 합 92.731s | `build/test-results/integrationTest` |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check` | PASS, exit 0 | preflight·JUnit·convention·workflow·label·Husky·baseline | 측정 안 함 | Harness checks passed |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests` | PASS, exit 0 | 전체 check 및 Java convention 포함 | Gradle 8s, 기존 결과 up-to-date 포함 | Local PR readiness checks passed |
| `npm run hooks:validate` | PASS, exit 0 | Husky 계약 | 측정 안 함 | Husky validation passed |
| `git diff --check` | PASS, exit 0 | 추적 변경 공백 검사 | 측정 안 함 | 오류 출력 없음 |

`gh`·`harness`는 시스템 CA를 사용하도록 인계된 환경 제거 prefix로 실행했다.
Gradle 실행에는 Java 21 JAVA_HOME을 지정했다. 위 로컬 시간은 PR CI 추가 시간이나
cold/warm 측정의 대체 증거가 아니다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `test_unit_001_severity_boundary_and_unknown` | 6.9/7.0/9.0, unknown/NaN, 공식 severity fallback |
| UNIT-002 | PASS | `test_unit_002_required_exception_fields` | 모든 필수 필드 누락/공백/잘못된 타입; 안전한 필드명 오류 |
| UNIT-003 | PASS | `test_unit_003_expiry_utc_boundary` | 고정 UTC 날짜 전일/당일 실패, 익일 유효, 30일 초과 실패 |
| UNIT-004 | PASS | `test_unit_004_exception_matches_alias_and_exact_coordinate` | alias 일치, 다른 package/version 미적용 |
| UNIT-005 | PASS | `test_unit_005_incomplete_schema_and_coverage_fail_closed` | 빈 문자열·손상 JSON·미지원 schema·빈/불일치 집합 실패 |
| UNIT-006 | PASS | `test_unit_006_duplicate_and_absent_expired_exception` | 중복 및 검출 여부와 독립적인 만료 실패 |
| INT-001 | PASS, local | 실제 Gradle + OSV | main의 직접·전이·runtimeOnly 전체 172 좌표를 해석 그래프=lock=OSV 집합으로 확인 |
| INT-002 | PASS, local | 실제 Gradle + OSV | runtimeOnly 취약 core 2.14.1과 전이 api 확인, scanner 1 / 정책 1 |
| INT-003 | PASS, local | 실제 scanner + 한정 예외 | Log4Shell 하나만 제외, 별도 기준 초과 2건은 계속 실패 |
| INT-004 | PASS, local | 실제 scanner + 연결 거부 proxy | 빈 HOME, 외부 접근 거부, 22.758s에 scanner 127 / 정책 2 |
| INT-005 | PARTIAL | 손상 JSON double + 실제 scanner 손상 config | 양쪽 모두 정책 2. 손상 DB 자체와 강제 signal 종료는 별도로 실행하지 않음 |
| INT-006 | NOT_RUN / BLOCKED | 실제 일반/fork PR | 새 PR/push 권한 범위가 아니므로 미실행. YAML 파싱으로 trigger/권한/checkout 설정만 확인 |
| INT-007 | PASS, local | 동일 CI init script + 선언 변경 | core 2.14.1→2.14.0이 새 lock·해석 좌표·OSV 결과에 반영됨. 빈/불일치 입력은 UNIT-005와 독립 검증에서 실패 확인 |
| INT-008 | NOT_RUN / BLOCKED | 동일 SHA cold/warm 각 3회 | 실제 CI runner 접근·실행 없음. 중앙값·최댓값·critical path 증가 미확인 |
| INT-009 | NOT_RUN / BLOCKED | 실제 artifact 업로드·동시 run 취소 | concurrency, !cancelled 업로드, 30일 보존, 미완료 JSON은 정적으로 확인. 실제 CI 미실행 |
| INT-010 | PASS, local; human review pending | main remediation | 업그레이드 3건 + 만료 예외 2건 후 미처리 0. 예외는 승인 완료로 간주하지 않음 |

Scenario ID의 공통 prefix는 `TEST-PLAN-GH-382-DEPENDENCY-SCAN-`이다.
6개 정책 테스트에는 소스 파일을 변경하지 않는 메모리 mutation 검증도 수행했다.
각각 임계값, 필수 필드, 당일 만료, version 범위, coverage, 중복 검사를 깨뜨렸을 때
해당 UNIT가 모두 실패했고 원본 모듈 복원 후 6개가 다시 통과했다.

### Baseline and remediation

기준 시각은 2026-11-23T13:34:40Z, 처리 후 조회는 13:37:22Z다.
공개 OSV API는 불변 DB snapshot 시각을 제공하지 않아 조회 시각과 scanner 버전을 기록한다.

| Advisory | Baseline coordinate / score | 처리 |
| --- | --- | --- |
| GHSA-xxph-c9ww-hj94 / CVE-2026-102554 | guava 33.6.0-jre / 8.2 | `dependencyManagement` 버전 override로 33.7.2-jre |
| GHSA-hf6x-8p5f-cgmf / CVE-2026-54399 | httpcore5 5.3.6 / 7.5 | Boot `httpcore5.version` 5.4.3 |
| GHSA-v3jc-474w-2wm6 / CVE-2026-54428 | httpcore5-h2 5.3.6 / 7.5 | 같은 속성으로 5.4.3 |
| GHSA-j9f9-w8pj-32f8 / CVE-2026-47890 | spring-webmvc 6.2.19 / 9.8 | 정확한 advisory/package/version 예외, 2026-12-07 만료, 추적 #382 |
| GHSA-pc63-qcmh-9cmg / CVE-2026-47884 | spring-webmvc 6.2.19 / 9.8 | 같은 만료일의 별도 한정 예외, 추적 #382 |

처리 후 기준 초과 미처리 0, 예외 2, 기준 미만 advisory 3이다.
기준 미만 commons-lang3 6.5, httpclient5 5.3, log4j-api 6.3은 승인된 임계값에 따라
통과하며 이번 범위에서 버전을 추가 변경하지 않았다.
Spring MVC의 공개 6.2 metadata는 6.2.19까지이고 두 advisory는 해당 버전을
last_affected로 기록하며 공개 7.0 수정은 7.0.9다. 호환되는 공개 6.2 수정은 확인되지 않았다.
Boot 4/Framework 7 전환을 이번 CI 작업에 섞지 않고 한정 예외를 제출했다.
예외의 사람 리뷰, 실제 완화책과 지원 버전 전환 결정은 남아 있다.

### Fixed release contract and official evidence

출처는 인계 조건에 따라 URL 대신 공식 프로젝트·tag·파일 경로로 식별한다.

- OSV-Scanner `v2.7.1`, `cmd/osv-scanner/scan/source/command.go` 및 로컬 바이너리
  `scan source --help`: `scan source`, `-L`, `--format=json`, `--config`,
  `--all-packages`, `--all-vulns` 확인. 명시적인 빈 config로 native ignore를 사용하지 않는다.
- 같은 tag의 `pkg/models/results.go`: `results[].packages[].package`,
  `vulnerabilities`, `groups[].max_severity`와 원본 ID/alias 계약 확인.
- 같은 tag의 `internal/utility/severity/severity.go`: CVSS v2, v3.0, v3.1, v4 계산 지원 확인.
  자체 벡터 계산기를 추가하지 않고 scanner의 그룹 최대값을 사용한다.
- OSV-Scanner 공식 Output / Return Codes 문서와 실제 실행:
  0 무검출, 1 검출, 127 일반 오류, 128 패키지 없음. 정책에서는 0/1만 결과 입력으로
  인정하고 다른 코드와 JSON/coverage 불일치는 도구 실패로 분리한다.
- 공식 release `v2.7.1` asset digest:
  linux_amd64 `fb7dab19a8bd9bbe5c3189f09d4b7ee1820c9553a23bac202648a56efd2f6ee8`,
  로컬 darwin_arm64 `b22c02877a811c8f78956e8643efa554932b7378f590d13f8e97d4e9448a29f3`.
  로컬 다운로드 후 SHA-256 일치와 버전 출력을 확인했다.
- 공식 GitHub tag ref 조회로 checkout v5.0.0, setup-java v5.0.0,
  upload-artifact v4.6.2의 full commit SHA를 확인했다.
- OSV 공식 advisory API의 `affected.ranges.events`와 Maven Central의 각
  `maven-metadata.xml`, Spring Boot 3.5.16 dependency BOM의 실제 property를 대조했다.
- Context7 도구는 catalog 검색 후 호출 시 Unknown tool로 사용할 수 없어 공식 문서와
  tag 소스로 보완했다.

## 5. Failures and diagnostics

- 예상한 취약점 실패는 exit 1, 연결/도구/JSON 장애는 exit 2로 구분했다.
  검사 약화, continue-on-error, skip 또는 오래된 성공 재사용은 없다.
- 초기 curl은 로컬 CA 환경 설정 때문에 exit 77이었다. 인증 검증을 끄지 않고
  인계된 시스템 CA prefix로 재실행하여 다운로드와 digest 검증을 통과했다.
- Python/JSON/YAML LSP 서버가 설치되지 않아 LSP 진단은 실행할 수 없었다.
  임의 설치 없이 Python 실제 실행, JSON 파싱, YAML 의미 파싱, workflow 정적 검사로 검증했다.
- 필수 로컬 검사에서 실패한 항목은 없다. INT-005의 손상 DB/강제 signal 케이스 및
  CI 전용 시나리오는 실행한 것으로 보고하지 않는다.

## 6. Potential issues

### Application code

- Guava와 HttpCore override는 승인된 버전 변경만 적용했다. 1506 단위·874 통합 검사는
  통과했지만 모든 실제 운영 기능의 호환성을 보증하지는 않는다.
- Spring MVC CRITICAL 2건은 남아 있다. 예외 등록은 수정·완화를 의미하지 않는다(#382).

### Infrastructure and resource limits

- Terraform/배포 변경 없음. scanner 300s, 다운로드 120s, job 전체 10분 제한.
  cold runner 전체 시간이 목표 이내인지는 INT-008에서 확인해야 한다(#382).

### Database and migrations

- scanner는 애플리케이션 DB를 사용하지 않고 DB schema/migration을 변경하지 않았다.
  공개 취약점 데이터는 실시간 API로 조회하므로 결과 재현에는 조회 시각도 필요하다.

### Concurrency and idempotency

- workflow/ref별 concurrency 취소와 실행 attempt별 artifact 이름을 사용한다.
  취소/실패 artifact 보존의 실제 동작과 required check 의미는 CI에서 확인해야 한다(#382).

### Transactions and event ordering

- 애플리케이션 트랜잭션·이벤트 변경 없음. 코드 검출 순서에 의존하지 않고 좌표 집합을 대조한다.

### External APIs

- OSV API/배포물/Maven 장애는 fail-closed이므로 merge 가용성에 영향을 준다.
  실제 연결 거부는 무검출 성공이 아니라 도구 실패였다(#382).
- 오탐·미탐은 데이터/좌표 매칭에 좌우된다. 현재 해석된 좌표 전체를 대조하지만
  DB에 없는 취약점과 runtimeClasspath 밖 구성은 검출 범위가 아니다(#382).

### Failure recovery and reconciliation

- 공급자 복구 후 같은 commit을 재실행하고 이전 실패 기록을 보존한다.
  예외는 advisory에만 적용되며 손상 결과나 도구 오류를 해제하지 않는다.
  2026-12-07부터 두 예외는 검출 여부와 무관하게 모든 scan을 실패시킨다(#382).

## 7. Regression and residual risk

- executed_checks / passed_checks: §3의 모든 필수 로컬 명령 exit 0.
- failed_checks: 필수 로컬 검사 없음; 취약/장애 fixture의 비영 종료는 예상 결과다.
- blocked_checks: INT-005 일부, INT-006/008/009, 실제 required check 등록 및 예외 사람 리뷰.
- assumptions: 단일 Java root project의 runtimeClasspath 범위이며 test/compileOnly/annotationProcessor는 제외.
- risks (주요 3개): Spring CRITICAL 예외 잔존, 외부 API 장애·데이터 오탐/미탐, CI 시간·권한·취소 실증 부재.
- required_human_decisions: Spring 2건의 위험 수용·완화책/지원 버전 전환, 첫 실제 CI 결과 확인 후 Ruleset 등록.
- 계획과 차이: D1 수정 승인대로 CI 전용 lock 생성, 저장소 lock/build locking 없음.
  Python 표준 라이브러리 unittest를 사용했다. INT-005는 손상 DB 대신 손상 config의 실제
  scanner 실패와 손상 JSON double로 부분 검증했다. CI 전용 수행·시간 수치는 추정하지 않았다.
- 독립 검증: 별도 read-only reviewer가 실제 schema/pin/로컬 결과와 추가 실패 주입을 확인했다.
  CRITICAL/HIGH 코드 결함 없음, 조건부 APPROVE이며 사람 승인이나 CI 완료를 대신하지 않는다.
  낮은 수준 잔여 사항은 group-name 기반 입력 필터가 동일 group 외부 모듈을 제외할 경우
  fail-closed로 중단하는 점, 일부 경계의 단위 검사 부재, 기존 workflow와 artifact action major 차이다.
- 생성 lockfile은 저장소에서 정리했다. fixture·binary·원본 OSV JSON은 저장소 밖에만 있다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-382-TEST-PLAN-GH-382-DEPENDENCY-SCAN.md` (APPROVED, §12 최종 결정).
- CI run: 없음; 이번 권한에는 push/PR 생성/CI dispatch가 포함되지 않았다.
- Related ADR: 없음.
- PR: 생성하지 않음.
- Local evidence: `/private/tmp/gh382.h1Na7C/`의 baseline, vulnerable,
  fixture-excepted, network-failure, corrupt-results, invalid-config, changed-declaration,
  remediated 디렉터리의 안전한 summary JSON. 임시 경로라 영구 CI 증거가 아니다.
- JUnit XML: `build/test-results/test/`, `build/test-results/integrationTest/`.
- 독립 검토 기록: git-excluded `.omo/evidence/gh-382-code-review.md`.
- 향후 CI artifact: `dependency-scan-attempt-<attempt>`의 raw JSON, summary JSON,
  runtime 좌표 및 생성 lockfile. 아직 업로드된 artifact는 없다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (#382에서 잔여 검증/결정 추적)
- [ ] 실행 결과와 PR 설명이 일치함
