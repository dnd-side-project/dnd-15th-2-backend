# Test Report: TEST-PLAN-GH-382-DEPENDENCY-SCAN

> Created at: `2026-11-23T22:39:58+09:00`
> GitHub Issue: `#382`
> Branch: `ci/gh-382-vulnerability-scan`
> Commit: `15bae1e2`
> CI measurement commit: `e14a2d37adb2b851046d33f3f34ec4c0fcd488e6` (2026-11-23)

## 1. Executive summary

- Result: `PARTIAL`; repository status: `BLOCKED` (fork PR, 일부 장애 증거와 사람 리뷰가 남음).
- Tested scope: 승인된 E1 구현과 로컬 회귀 검사, 일반 PR의 실제 scan 성공·읽기 token 권한·artifact 보존, 동일 commit의 cold/warm 순차 3회씩 측정, 별도 중복 dispatch의 선행 취소·후속 성공.
- Unverified scope: fork PR, 검출 실패 artifact의 실제 CI 보존, required check 등록. INT-005 손상 DB/강제 signal 종료와 Spring 예외 사람 리뷰도 미확인이다.
- Release recommendation: INT-008 시간 목표는 충족했다(warm job 중앙값 29초, cold 최대 53초). 일반 PR에서 43초에 통과했다. Spring MVC CRITICAL 2건의 예외를 사람 승인 완료로 간주하거나, 남은 시나리오를 전체 PASS로 바꾸지는 않는다.
- issue_number: `382`; task_id: `TEST-PLAN-GH-382-DEPENDENCY-SCAN`; design_id: 해당 없음.
- changed_files: `.github/workflows/dependency-scan.yml`, `config/security/dependency-lock.init.gradle`, `config/security/dependency-exceptions.json`, `scripts/check-dependency-scan.py`, `scripts/test-dependency-scan.py`, `docs/security/dependency-scan.md`, `build.gradle`, 본 보고서.
- `TASK.md`와 승인된 계획의 기존 변경은 보존했다. commit/push/PR/설정 변경은 하지 않았다.
- 추가 측정은 2026-11-23 @Byuntil 승인 인계에 따라 수행했다. 이번 수정 파일은 본 보고서 하나이며, 오케스트레이터가 연 PR #383과 push된 동일 commit을 사용했다.

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

### INT-008: actual CI measurements

2026-11-23, `ci/gh-382-vulnerability-scan`의 동일 commit
`e14a2d37adb2b851046d33f3f34ec4c0fcd488e6`에서 `Dependency Scan`을
`workflow_dispatch`로 순차 실행했다. 각 실행의 job 종료 후 다음 실행을 생성했다.
runner label은 모두 `ubuntu-latest`이며, 실제 물리 호스트가 같다는 의미는 아니다.

다음 명령은 모두 `env -u SSL_CERT_FILE -u SSL_CERT_DIR` prefix로 실행했다.

- 실행: `gh workflow run "Dependency Scan" --ref ci/gh-382-vulnerability-scan`.
- cold 직전: `gh cache list --ref refs/heads/ci/gh-382-vulnerability-scan --limit 100 --json id,key,ref`.
- 삭제: `gh cache delete --all --ref refs/heads/ci/gh-382-vulnerability-scan --succeed-on-no-caches`.
  첫 회는 해당 ref cache 0개였고, 둘째·셋째 회 직전에는 각각 해당 ref cache 1개를 삭제했다.
  `main`, PR merge ref 등 다른 ref의 cache는 삭제하지 않았다.
- warm 세 번은 cache 삭제 없이 cold 3회 종료 후 순차 실행했다.
- 시간 출처: 각 `gh run view --json jobs,createdAt,startedAt`의 job/step timestamps.
  단위는 초이며 1초 해상도다. 0초는 해당 timestamp 사이의 초 경계가 같다는 뜻으로,
  단계가 실행되지 않았다는 뜻이 아니다.
- queue는 `job.startedAt - run.createdAt`로 정의하여 runner 할당·시작 대기를 포함한다.
  별도로 `run.startedAt - run.createdAt`도 확인했으며 여섯 번 모두 0초였다.
  생성→job 종료는 queue+job 시간이다. step 합과 job 시간의 차이에는 단계 사이 간격과
  setup/post 작업이 포함되므로 step 합으로 job 시간을 대체하지 않았다.
- 실행 생성 직후 `gh run watch`에서 run/jobs API가 일시적으로 404를 반환했다.
  해당 실행을 재생성하지 않고 기존 실행의 완료 상태를 구독했다. 표본 교체나 중복 측정은 없다.

| 표본 | job UTC 시작–종료 (2026-11-23) | queue | job | 생성→job 종료 | 실제 Gradle cache | 근거 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| cold 1 | 14:29:27–14:30:15 | 4 | 48 | 52 | miss, fallback 복원 없음 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45194743270) |
| cold 2 | 14:30:39–14:31:21 | 4 | 42 | 46 | miss, fallback 복원 없음 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45194960136) |
| cold 3 | 14:31:39–14:32:32 | 4 | 53 | 57 | miss, fallback 복원 없음 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195142932) |
| warm 1 | 14:32:46–14:33:15 | 5 | 29 | 34 | exact-key hit, 복원 성공 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195339918) |
| warm 2 | 14:33:29–14:33:58 | 3 | 29 | 32 | exact-key hit, 복원 성공 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195470762) |
| warm 3 | 14:34:14–14:34:35 | 4 | 21 | 25 | exact-key hit, 복원 성공 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195601699) |

여섯 실행 모두 `success`다. cold의 setup-java 로그는 모두
`gradle cache is not found`였고 이후 cache를 저장했다.
warm은 모두 `Cache hit for`와 `Cache restored successfully`를 기록했다.
따라서 cold는 단순히 브랜치 cache만 지운 명목상 cold가 아니라 실제 Actions Gradle
cache 미복원 실행이다. 기본 브랜치 fallback으로 복원된 실행은 없었다.
다만 runner image에 미리 있는 Java/tool cache나 Maven/CDN의 외부 cache까지
비어 있었다는 의미는 아니다. OSV DB는 매회 live API이며 불변 snapshot 시각은 제공되지 않는다.
조회 구간은 각 실행의 scan step timestamps로 구분할 수 있다.

| 단계 / 지표 (초) | cold 1 / 2 / 3 | cold 중앙값 / 최대 | warm 1 / 2 / 3 | warm 중앙값 / 최대 |
| --- | --- | --- | --- | --- |
| Set up Java | 1 / 0 / 0 | 0 / 1 | 5 / 2 / 3 | 3 / 5 |
| Test scan policy | 0 / 0 / 1 | 0 / 1 | 0 / 0 / 0 | 0 / 0 |
| Prepare OSV-Scanner | 0 / 1 / 0 | 0 / 1 | 0 / 1 / 0 | 0 / 1 |
| Resolve runtime dependencies | 35 / 31 / 39 | 35 / 39 | 12 / 16 / 12 | 12 / 16 |
| Scan resolved runtime dependencies | 1 / 1 / 1 | 1 / 1 | 1 / 1 / 1 | 1 / 1 |
| Preserve dependency scan evidence | 1 / 1 / 2 | 1 / 2 | 1 / 0 / 1 | 1 / 1 |
| Post Set up Java (cache 저장/확인) | 4 / 3 / 3 | 3 / 4 | 1 / 1 / 0 | 1 / 1 |
| job 전체 | 48 / 42 / 53 | 48 / 53 | 29 / 29 / 21 | 29 / 29 |
| queue (생성→job 시작) | 4 / 4 / 4 | 4 / 4 | 5 / 3 / 4 | 4 / 5 |
| 생성→job 종료 | 52 / 46 / 57 | 52 / 57 | 34 / 32 / 25 | 32 / 34 |

초기 목표와 비교하면 warm job 중앙값 29초는 180초 이내이고, cold job 최대 53초는
600초 이내다. queue를 포함해도 warm 중앙값 32초·cold 최대 57초로 두 목표를 충족한다.
warm job 중앙값은 cold보다 19초 짧다. 여섯 측정 job의 runner 점유 시간 합은
222초이며, 이는 과금 분 단위 올림이나 queue·취소 실험을 포함한 비용 산정이 아니다.

### INT-006: normal PR and critical path

첫 일반 PR 실행에서 `Dependency Scan / dependency-scan`은 43초에 성공했다
([실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45188663749)).
job queue는 3초, 생성→job 종료는 46초였다. 실제 job 로그에서
`GITHUB_TOKEN Permissions`의 `Contents: read`, `Metadata: read`를 확인했다.
workflow는 secret 참조 없이 실행됐다. fork PR은 실행하지 않았고 여전히 미확인이다.

같은 PR의 기존 Harness Policy에서 가장 긴 `test` job은 303초, queue 3초였다
([실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45188663868)).
두 workflow의 job이 병렬로 시작했으므로 이 PR의 비교에서는
`max(303, 43) - 303 = 0초`로 scan이 critical path를 늘리지 않았다.
별도 측정의 가장 느린 cold job 53초(생성→종료 57초)도 해당 test보다 짧다.
대신 독립 scan job의 runner 점유 시간은 추가되며, 관측한 첫 PR에서는 43초다.
이는 scan 유무를 바꾼 동일 환경 A/B 실험이나 향후 runner 경합에 대한 보장은 아니다.

첫 PR의 `dependency-scan-attempt-1` artifact는 API에서 만료되지 않은 상태로 확인했고,
생성 2026-11-23T13:55:39Z, 만료 2026-12-23T13:55:38Z로 약 30일 보존을 확인했다.
검출 실패 run에서의 artifact 보존은 아직 실제 CI로 재현하지 않았다.

### INT-009: separate cancellation experiment

cold/warm 여섯 측정이 모두 종료된 다음, 같은 ref와 commit으로 dispatch 두 개를
연달아 생성했다. 이 두 실행은 INT-008 표본·통계에 포함하지 않았다.
사람이 승인한 concurrency 검증이며 별도의 수동 run 취소 명령은 실행하지 않았다.

| 순서 | 생성 시각 UTC (2026-11-23) | job 시작–종료 UTC | 실제 결론 | artifact | 근거 |
| --- | --- | --- | --- | --- | --- |
| 선행 dispatch | 14:35:07 | 14:35:11–14:40:11 | `cancelled` | API 목록 비어 있음 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195769438) |
| 후속 dispatch | 14:35:09 | 14:40:14–14:40:36 | `success` | `dependency-scan-attempt-1`, 30일 보존 | [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45195776092) |

동일 ref의 새 실행으로 이전 실행이 취소되고, 취소가 성공으로 바뀌지 않는 것을
실제 결론으로 확인했다. 선행 실행에는 artifact 업로드 step 기록이나 artifact가 없었고,
후속 실행은 업로드 step이 성공했으며 artifact 만료 시각은
2026-12-23T14:40:33Z였다. 취소 실행에서 업로드하지 않는 workflow 조건과 일치한다.

다만 취소가 즉시 완료된 것은 아니다. 선행 job timestamps의 간격은 300초였고
후속 실행의 생성→job 시작 대기는 305초, job 자체는 22초였다.
따라서 정상 cold/warm의 queue 최대 5초와 이 취소 상황을 혼합하지 않는다.
취소 반영 지연의 원인은 확인하지 않았으며, 즉시 취소나 후속 run의 즉시 시작을
보장한다고 해석하지 않는다. 검출 실패 run의 artifact 보존은 이번 실험으로 검증되지 않는다.

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
| INT-006 | PARTIAL | 실제 일반 PR 성공, fork PR 미확인 | 일반 PR 43초 성공·읽기 token 권한·artifact 보존을 위 실행 링크로 확인. fork 동작은 추정하지 않음 |
| INT-007 | PASS, local | 동일 CI init script + 선언 변경 | core 2.14.1→2.14.0이 새 lock·해석 좌표·OSV 결과에 반영됨. 빈/불일치 입력은 UNIT-005와 독립 검증에서 실패 확인 |
| INT-008 | PASS | 동일 SHA cold/warm 각 3회 실제 CI | cold 실제 miss 3회, warm hit 3회. job cold 최대 53초·warm 중앙값 29초로 목표 충족. 단계·queue·critical path 비교는 §3 |
| INT-009 | PARTIAL | 일반 PR artifact + 별도 중복 dispatch | 선행 cancelled·후속 success, 취소 artifact 없음·후속 artifact 30일 보존 확인. 후속 시작 대기 305초 관찰. 검출 실패 artifact는 미확인 |
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

공식 기술 출처는 초기 인계 조건에 따라 프로젝트·tag·파일 경로로 식별한다.
추가 측정의 CI 실행 증거는 이번 인계 요청대로 실행 기록 링크를 사용한다.

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
- 필수 로컬 검사에서 실패한 항목은 없다. 추가 CI 시간 측정 6회도 모두 성공했다.
  INT-005의 손상 DB/강제 signal, fork PR, 실패 artifact 보존은 실행한 것으로 보고하지 않는다.
- 별도 취소 실험의 선행 run은 예상대로 cancelled, 후속 run은 success였다.
  다만 후속 job 시작 대기 305초를 관찰했다. 원인을 추정하거나 즉시 취소로 보고하지 않는다.

## 6. Potential issues

### Application code

- Guava와 HttpCore override는 승인된 버전 변경만 적용했다. 1506 단위·874 통합 검사는
  통과했지만 모든 실제 운영 기능의 호환성을 보증하지는 않는다.
- Spring MVC CRITICAL 2건은 남아 있다. 예외 등록은 수정·완화를 의미하지 않는다(#382).

### Infrastructure and resource limits

- Terraform/배포 변경 없음. scanner 300s, 다운로드 120s, job 전체 10분 제한.
  실제 cold job 최대 53초·warm 중앙값 29초로 초기 목표를 충족했다.
  향후 runner 경합이나 공급자 지연까지 보장하는 수치는 아니다(#382).

### Database and migrations

- scanner는 애플리케이션 DB를 사용하지 않고 DB schema/migration을 변경하지 않았다.
  공개 취약점 데이터는 실시간 API로 조회하므로 결과 재현에는 조회 시각도 필요하다.

### Concurrency and idempotency

- workflow/ref별 concurrency 취소와 실행 attempt별 artifact 이름을 사용한다.
  동일 ref의 별도 두 dispatch에서 선행 cancelled·후속 success를 확인했으나,
  후속 시작까지 305초 대기가 있었다. 검출 실패 artifact 보존과 required check 등록은
  미확인이며, 이 취소 지연을 정상 warm queue 통계에 넣지 않는다(#382).

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
- blocked_checks: INT-005 일부, INT-006 fork PR, INT-009 검출 실패 artifact, 실제 required check 등록 및 예외 사람 리뷰. INT-008과 INT-009의 이전 실행 취소 부분은 실제 CI로 확인했다.
- assumptions: 단일 Java root project의 runtimeClasspath 범위이며 test/compileOnly/annotationProcessor는 제외.
- risks (주요 3개): Spring CRITICAL 예외 잔존, 외부 API 장애·데이터 오탐/미탐, fork PR·실패 artifact·required check 실증 잔여와 취소 반영 지연.
- required_human_decisions: Spring 2건의 위험 수용·완화책/지원 버전 전환, 첫 실제 CI 결과 확인 후 Ruleset 등록.
- 계획과 차이: D1 수정 승인대로 CI 전용 lock 생성, 저장소 lock/build locking 없음.
  Python 표준 라이브러리 unittest를 사용했다. INT-005는 손상 DB 대신 손상 config의 실제
  scanner 실패와 손상 JSON double로 부분 검증했다. 추가 승인으로 INT-008은 동일 SHA의
  실제 cold/warm 3회씩 측정하여 완료했고, 미확인 fork 동작 등은 추정하지 않았다.
- 독립 검증: 별도 read-only reviewer가 실제 schema/pin/로컬 결과와 추가 실패 주입을 확인했다.
  CRITICAL/HIGH 코드 결함 없음, 조건부 APPROVE이며 사람 승인이나 CI 완료를 대신하지 않는다.
  낮은 수준 잔여 사항은 group-name 기반 입력 필터가 동일 group 외부 모듈을 제외할 경우
  fail-closed로 중단하는 점, 일부 경계의 단위 검사 부재, 기존 workflow와 artifact action major 차이다.
- 생성 lockfile은 저장소에서 정리했다. fixture·binary·원본 OSV JSON은 저장소 밖에만 있다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-382-TEST-PLAN-GH-382-DEPENDENCY-SCAN.md` (APPROVED, §12 최종 결정).
- CI run: 일반 PR [실행 기록](https://github.com/dnd-side-project/dnd-15th-2-backend/actions/runs/45188663749), 측정 6회와 별도 취소 실험 2회의 개별 링크는 §3. 추가 인계에서 CI dispatch와 대상 ref cache 삭제가 승인됐다.
- Related ADR: 없음.
- PR: 오케스트레이터가 생성한 [PR #383](https://github.com/dnd-side-project/dnd-15th-2-backend/pull/383). 이 실행 에이전트는 PR 생성·수정·댓글·push를 하지 않았다.
- Local evidence: `/private/tmp/gh382.h1Na7C/`의 baseline, vulnerable,
  fixture-excepted, network-failure, corrupt-results, invalid-config, changed-declaration,
  remediated 디렉터리의 안전한 summary JSON. 임시 경로라 영구 CI 증거가 아니다.
- JUnit XML: `build/test-results/test/`, `build/test-results/integrationTest/`.
- 독립 검토 기록: git-excluded `.omo/evidence/gh-382-code-review.md`.
- CI artifact: `dependency-scan-attempt-<attempt>`의 raw JSON, summary JSON,
  runtime 좌표 및 생성 lockfile. 첫 PR의 실제 artifact 존재와 약 30일 보존을 확인했다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (#382에서 잔여 검증/결정 추적)
- [ ] 실행 결과와 PR 설명이 일치함
