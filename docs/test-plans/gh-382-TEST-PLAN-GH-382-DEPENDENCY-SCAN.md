# Test Plan: TEST-PLAN-GH-382-DEPENDENCY-SCAN

> Created at: `2026-11-23T22:14:59+09:00`
> GitHub Issue: `#382`
> Status: APPROVED — 2026-11-23, @Byuntil (D1 수정 승인, 나머지 추천안 승인)

## 1. Objective

PR마다 실제 해석된 Gradle 의존성의 취약점을 검사하고, 기준 초과 항목과 검사 불능을 구별해 기록한다.
이 문서는 승인 전 설계·테스트 계획이며 구현 또는 스캔 통과 증거가 아니다.

## 2. Scope

### Included

- CONFIRMED: #382와 TASK.md의 런타임 포함 Gradle 의존성 스캔, 읽기 전용 권한,
  고정된 action 버전, 사유·추적 Issue·만료일을 갖춘 예외.
- ASSUMED: 아래 D1-D7의 추천 조합. 사람 승인 전 실행하지 않는다.
- 현재 작성 권한은 이 계획 파일 하나이며 기존 TASK.md 변경은 보존한다.

### Excluded

- 컨테이너 이미지, SAST, Terraform, 배포, Ruleset 변경, 커밋·push·PR 생성.
- Java 비즈니스 로직, DB 스키마 및 애플리케이션 JUnit 테스트 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #382 | 취약 버전 주입 시 실패, main 기준 결과와 처리 기록, 최소 권한, CI 추가 시간, 필수 검사 통과 |
| GitHub Issue #360 | 4개 BOM override와 이전 OSV 수동 점검. 제외했던 의존성이 있으므로 전체 스캔 무검출의 증거가 아님 |
| TASK.md | 도구·심각도·예외 형식은 사람 승인 대상. 이미지 스캔 제외 |
| 기존 workflow | Harness Policy는 opened/synchronize/reopened 및 수동 실행. PR metadata의 edited 이벤트와 분리 |
| 정책 검사 | name/on/permissions/jobs 필수, tab 및 continue-on-error: true 금지, apply는 보호된 workflow 밖 금지 |

관찰: 기존 외부 action은 `@v5` 등 major tag 고정이다. 검사 스크립트는
SHA 고정이나 권한 상한 자체를 검증하지 않으므로 정적 검사 성공만으로 #382의
보안 조건을 입증할 수 없다. 새 job은 전체 commit SHA와 대응 release 설명을
사용하고 `contents: read`만 선언할 것을 제안한다. PR 코드 실행에
`pull_request_target`을 사용하지 않으며 checkout 자격 증명은 지속하지 않는다.
기존 sync-api-docs job의 write 권한은 새 job으로 상속하지 않는다.

### 공식 문서 확인 기록

2026-11-23 확인. URL은 인계 금지 조건에 따라 기록하지 않는다.
문서 제목·공식 프로젝트·경로로 출처를 식별한다. 버전별 실제 CLI와 결과 스키마는
실행 에이전트가 선택한 고정 release에서 재확인해야 한다.

| ID | 공식 출처 | 확인한 사실 |
| --- | --- | --- |
| S1 | OSV-Scanner, Supported Artifacts and Manifests | Gradle lockfile과 source scan 지원. 실제 해석 버전에는 resolved lockfile 권장 |
| S2 | Gradle User Manual, Locking Versions | configuration별 locking 활성화 후 해석과 --write-locks로 전이 의존성 포함 버전 저장 |
| S3 | Trivy, Java coverage | JAR 및 Gradle lockfile 지원. build.gradle 선언만으로 완전한 런타임 그래프를 보장하지 않음 |
| S4 | Trivy, Filtering | severity 필터 지원 확인. native ignore의 세부 필드·만료 동작은 이번 조회에서 미확인 |
| S5 | dependency-check/DependencyCheck README 및 Gradle configuration | Gradle plugin과 scanConfigurations 제공. NVD API key 강력 권장, 미사용 시 갱신 지연. mirror 설정 지원 |
| S6 | gradle/actions, docs/dependency-submission.md | Gradle 실행에서 해석한 그래프를 제출. 제출에는 contents: write 필요. fork PR 세부 절차는 미확인 |

Context7 조회는 `library is not defined` 오류로 사용하지 못해 공식 문서 검색으로
보완했다. 공식 문서의 실제 소요 시간, 이 저장소의 탐지 건수, 사용할 release의
OSV severity 처리와 종료 코드, Trivy 배포 DB 접근 제한은 미확인이다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 전이·runtimeOnly 누락 또는 오래된 lock | 거짓 통과 | 높음 | P0 | 해석 그래프와 입력 좌표·버전 집합 비교 |
| DB/API 장애를 무검출로 간주 | 거짓 통과 | 중간 | P0 | 연결 실패·손상 응답에서 실패 |
| 예외 만료·누락·과도한 범위 | 취약점 은폐 | 중간 | P0 | 만료 경계 및 package/version 한정 |
| CVSS 미제공 | 기준 누락 | 중간 | P0 | unknown 판정 증거 |
| PR의 빌드 코드 실행 | 자격 증명 노출 | 중간 | P0 | 읽기 전용·secret 미사용·비지속 checkout |
| DB 다운로드·중복 Gradle 해석 | CI 지연 | 높음 | P1 | cold/warm 단계별 시간, 전체 critical path 비교 |
| 보고서 누락·취소 후 성공 처리 | 감사 불가 | 중간 | P0 | 결과 없는 실행이 성공하지 않음 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-001 | CVSS 6.9/7.0/9.0과 unknown 결과 | 판정 | 6.9 통과, 나머지 실패(D2 승인 시) | P0 | E1 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-002 | 사유·issue·expires_at 중 하나 누락/공백/잘못된 타입 | 예외 검증 | 실패, 해당 필드명만 출력 | P0 | E1 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-003 | 고정 UTC 날짜와 전일/당일/익일 만료 | 예외 검증 | 전일·당일 실패, 익일만 유효 | P0 | E1 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-004 | 동일 CVE지만 다른 package/version | 예외 적용 | 정확히 일치하는 대상만 제외 | P0 | E1 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-005 | 빈 파일·손상 JSON·지원하지 않는 결과 스키마 | 결과 읽기 | 무검출 성공으로 바꾸지 않고 실패 | P0 | E1 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-UNIT-006 | 중복 예외와 취약점이 사라진 만료 예외 | 예외 검증 | 중복 및 만료는 발견 여부와 무관하게 실패 | P1 | E1 |

위 표는 정책 판정의 독립 시나리오다. 구현 언어 및 테스트 배치는 승인 시 확정한다.
Java 테스트를 추가하는 경우 하위 AGENTS.md를 먼저 읽고 JUnit 5,
@DisplayName 및 정확한 timestamp/source scenario 헤더를 적용한다.
문서 문구나 workflow 원문 일치만 검사하는 테스트는 만들지 않는다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-001 | Gradle+scanner | 깨끗한 main snapshot | runtimeClasspath 해석 및 스캔 | 모든 직접·전이·runtimeOnly 좌표 포함, 결과·DB 시점 기록(P0) | 임시 workspace 폐기 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-002 | Gradle+실제 scanner | 격리 fixture에 취약 runtimeOnly 의존성 | 해석 버전 확인 후 스캔 | 알려진 advisory 검출과 job 실패, 도구 오류와 구별(P0) | fixture 제거 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-003 | scanner+정책 | INT-002와 유효한 한정 예외 | 스캔 및 예외 판정 | 해당 건만 제외, 예외 처리 기록(P0) | 임시 예외 제거 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-004 | DB/API+scanner | 빈 캐시, 연결 거부 fixture | scan 실행 | bounded timeout 안에 실패, no findings 금지(P0) | 임시 캐시 폐기 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-005 | scanner+workflow | 잘못된 DB 또는 scanner 강제 비정상 종료 | scan 실행 | 도구 장애로 실패, 예외 파일로 우회 불가(P0) | 임시 fixture 제거 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-006 | PR workflow | 일반 및 fork PR 조건 | opened/synchronize/reopened 실행 | 고유 check 생성, contents: read 이하, secret 없음(P0) | 사람이 승인한 시험 PR 정리 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-007 | CI init script+Gradle | 의존성 선언을 바꾼 임시 workspace, 저장소에 lockfile 없음 | CI와 같은 init script로 lock 생성 후 스캔 | 바뀐 선언이 생성된 lock과 스캔 입력에 반영됨; lock 생성 실패나 빈 lock은 job 실패(P0) | 임시 workspace 폐기 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-008 | cache+CI | 동일 SHA, cold/warm 각 3회 | 단계별 시간 측정 | 실행 시간·비용·critical path 증가 기록(P1) | 격리 캐시만 정리 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-009 | workflow+artifact | 검출 실패 및 새 commit 도착 | 실패 보고/이전 run 취소 | 실패 결과 보존, 취소를 성공으로 바꾸지 않음(P0) | 보존 기한 적용 |
| TEST-PLAN-GH-382-DEPENDENCY-SCAN-INT-010 | main+remediation | 기준 초과 항목 존재 | 업그레이드 또는 승인 예외 후 재실행 | 미처리 기준 초과 0, 변경 전후 연결 기록(P0) | 검증 fixture만 폐기 |

## 7. Cross-cutting scenarios

### Database and transactions

- 해당 없음: DB와 트랜잭션을 실행하거나 수정하지 않는다.

### Concurrency and idempotency

- INT-009: workflow/ref별 concurrency 및 cancel-in-progress를 사용한다.
  서로 다른 PR의 결과·캐시·artifact를 혼합하지 않는다.
- 같은 SHA도 DB 갱신으로 결과가 달라질 수 있으므로 scanner 버전과 DB 갱신 시각을 기록한다.

### External APIs

- INT-004/005: 취약점 서비스뿐 아니라 Maven 해석 실패도 검사 실패다.
  승인된 timeout 안에 끝내고 연결 실패와 취약점 검출을 별도 원인으로 출력한다.

### Failure recovery and reconciliation

- D5 fail-closed 승인 시 복구 후 같은 SHA를 재실행하고 이전 실패 기록을 보존한다.
  캐시된 성공 결과를 재사용하지 않는다. 예외는 취약점만 처리하며 도구 장애는 제외하지 않는다.

## 8. Test data and isolation

- Fixtures: scanner의 선택 release와 맞는 최소 JSON, valid/invalid 예외,
  격리된 Gradle runtimeOnly fixture. 실제 저장소에 취약 버전을 커밋하지 않는다.
- Database isolation: 애플리케이션 DB 불필요; 취약점 DB 캐시는 fixture별 별도 경로.
- Clock/randomness: UTC 날짜를 입력으로 고정. 현재 날짜 의존 테스트나 고정 sleep 금지.
- External API doubles: 연결 거부/손상 응답을 제어하되 실제 scanner 프로세스를 실행한다.
  정상 검출 시나리오는 실제 공식 DB를 이용해 mock 자체를 검사하는 테스트가 되지 않게 한다.
- Cleanup: 실행 에이전트가 소유한 임시 디렉터리만 정리. 사용자 파일, 공유 캐시, TASK.md 보존.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 사람 | D1-D7 승인, TASK.md의 최종 소유 범위 확정 | 전체 | 승인자·시각·결정 증거 |
| 2 | E1 단일 실행 에이전트 | 아래 제안 경로만 승인 후 수정 | UNIT-001~006, INT-001~010 | 정책 테스트, 실제 scanner, 필수 검사 |
| 3 | 독립 검증 에이전트 | 읽기 전용 | 전체 | 실제 diff·종료 코드·CI 기록 대조 |

E1 소유 범위(D1 수정안 반영): `.github/workflows/dependency-scan.yml`,
`config/security/dependency-lock.init.gradle`(CI 전용 locking init script), `config/security/dependency-exceptions.json`,
`scripts/check-dependency-scan.py`, `scripts/test-dependency-scan.py`,
`docs/security/dependency-scan.md`. `build.gradle`과 `gradle.lockfile`은 소유 범위에서 뺀다(D1 수정안). 기준 초과 항목의 업그레이드에 `build.gradle` 버전 override 변경이 필요하면 그 변경만 허용하며 보고서에 항목별로 기록한다.
테스트 배치와 Python 사용은 기존 실행 규칙 검토 후 사람에게 확정받는다.
추가 경로나 Java 변경이 필요하면 먼저 범위를 재승인받는다.
Ruleset은 사람 소유이며 실행 에이전트가 변경하지 않는다.

## 10. Completion criteria

- [ ] D1-D7 사람 승인 및 구현 파일 범위 확정
- [ ] 모든 P0 시나리오와 시간 측정 P1 수행, 예상 결과와 실제 결과 분리 기록
- [ ] Java 테스트를 만들 때만 JUnit 메타데이터 요건 적용
- [ ] main baseline, 취약 fixture 실패, 처리 후 통과를 각각 증명
- [ ] action SHA·권한·취소·artifact·필수 check 연결을 실제 CI에서 확인
- [ ] `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check`
- [ ] `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests`
- [ ] `npm run hooks:validate` 및 `git diff --check`
- [ ] 승인 후 실행 전 harness-test-run과 reporting 계약을 읽고 테스트 보고서 생성

위 체크리스트는 구현 완료 조건이다. 이번 계획 작성에서 스캔·JUnit·CI 실행이나
새 도구 설치를 수행하지 않는다. 사람이 승인하기 전 구현 상태는 BLOCKED다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — 시나리오 16개 계획 승인. D1 수정안, D2~D7 추천안대로
- Approved at: 2026-11-23

## 12. Human design decisions

### 승인 기록(2026-11-23, @Byuntil)

- D1 수정 승인: 도구는 OSV-Scanner다. lockfile은 저장소에 커밋하지 않는다. CI job이 init script로 `runtimeClasspath` locking을 켜고 `--write-locks`로 그 실행에서만 lockfile을 만든 뒤 스캔한다. `build.gradle`·`settings.gradle`에 locking을 켜지 않으며 개발 흐름은 바꾸지 않는다. 오래된 lock 검사는 필요 없으므로 INT-007은 생성 lock의 반영·실패 검증으로 바꾼다.
- D2~D7: 아래 추천안대로 승인한다. D3은 전용 Python 검사 스크립트와 그 테스트를 쓴다.

### D1. 도구와 실제 Gradle 해석 입력

| 대안 | 전이 의존성과 입력 | 비밀값 | 시간·유지 부담 및 단점 |
| --- | --- | --- | --- |
| OSV-Scanner | S1/S2: resolved Gradle lockfile 스캔. build.gradle만 입력하지 않음 | 공개 OSV 조회 경로 제안; 선택 release 인증 요구 재확인 | #360과 데이터 연속성. severity 판정·JSON 정규화 유지 필요, 실측 없음 |
| Trivy filesystem | S2/S3: resolved lockfile 또는 JAR. Gradle 전이 그래프는 lock 생성 단계에서 확보 | 공개 DB 사용 제안; 사설 mirror 인증은 도입하지 않음 | severity 필터 제공, DB 다운로드/캐시 관리 필요. 실측 없음 |
| OWASP Dependency-Check | S5: Gradle plugin의 해석 configuration을 명시, lock 자체는 필수 입력 아님 | NVD key 강력 권장 | Gradle 통합 장점, 초기 DB 갱신과 key/mirror 운영 부담. 실측 없음 |
| dependency submission + review | S6: Gradle 해석 그래프 제출, lock 불필수 | GITHUB_TOKEN 제출 write 권한 필요 | GitHub 통합 장점. 현재 contents: read 상한에 부합하지 않아 비추천; 차이 검토만으로 전체 baseline 대체 금지 |

추천: OSV-Scanner와 커밋된 `runtimeClasspath` 전용 Gradle lockfile.
기존 수동 OSV 점검과 비교할 수 있다. 대안은 Trivy+동일 lockfile로 severity 처리 부담을
줄이는 것이며, OSV 고정 release의 결과 스키마 검증이 불가능하면 사람에게 D1 재결정을 요청한다.
현재 build/settings에 locking 설정이 없고 추적된 Gradle lockfile도 없다.
잠금 활성화 후 `./gradlew dependencies --configuration runtimeClasspath --write-locks`로
초기 생성한다. PR 검증에서는 --write-locks 없이 해석하여 stale lock을 실패시킨다.
runtimeOnly와 전이 좌표가 모두 포함되는지는 해석 결과와 집합 비교한다.
추가 test/annotationProcessor configuration까지 잠그는 대안은 범위가 넓고 갱신 부담이 있으므로
초기 추천은 runtimeClasspath이며 다른 configuration은 미검사 범위로 명시한다.

### D2. 심각도와 CVSS 없는 항목

- A: HIGH/CRITICAL(CVSS 7.0 이상) 및 unknown 차단. 불확실성을 숨기지 않지만 triage 부담.
- B: CRITICAL(9.0 이상)만 차단, unknown 경고. CI 가용성은 높지만 HIGH가 통과.
- 추천 A. 지원 CVSS 수치가 여러 개면 최대값 사용; 숫자가 없으면 공식 severity를
  이용하고 둘 다 없거나 파싱 불가능하면 unknown. 선택 release의 벡터 지원은 구현 전 확정한다.
  수정 버전이 없거나 미사용 기능이라는 이유만으로 자동 제외하지 않는다.

### D3. 예외 형식과 만료

- A: `config/security/dependency-exceptions.json`의 배열에 advisory ID, Maven package,
  정확한 version, reason, issue(양의 정수), expires_at(UTC YYYY-MM-DD) 필수.
  scanner 결과 후 별도 정책 판정. 자체 검증기 유지가 필요하지만 필드와 만료 실패를 강제 가능.
- B: scanner native ignore 형식. 코드가 적지만 사유·Issue 필수와 만료 시 job 실패를
  도구만으로 보장하는지는 미확인이다. 만료 시 단순 ignore 해제와 job 실패는 다른 계약이므로
  고정 release에서 확인하고 별도 검증 필요 여부를 결정한다.
- 추천 A. 만료일 당일 00:00 UTC부터 실패하며 결과에 해당 advisory가 없어도 만료 예외는 실패.
  wildcard·전역 패키지 제외 금지. 신규·연장 예외는 사람 리뷰, 최대 30일 제안.
  advisory alias 처리 시 원본 ID와 alias를 보존하고 package/version 범위를 넓히지 않는다.

### D4. job 위치와 필수 검사

- A: 새 `Dependency Scan` workflow에 `dependency-scan` job. 소유권·timeout·이력 분리,
  대신 필수 상태 검사 등록이 추가로 필요.
- B: 기존 Harness Policy에 독립 job 추가. 이벤트 재사용 가능하지만 기존 write job과 문맥 혼재.
- 추천 A. opened/synchronize/reopened와 workflow_dispatch, path filter 없이 실행.
  사람은 실제 첫 run의 check 이름과 app을 확인한 뒤 Ruleset에 required로 등록한다.
  실패 job 존재만으로 merge 차단을 보장한다고 보고하지 않는다.

### D5. 도구·DB 장애

- A: fail-closed. 판단 불능 PR을 차단하지만 공급자 장애가 merge 가용성에 영향.
- B: 경고만 남기는 fail-open. 가용성은 높지만 검사 성공 의미를 훼손하며 현재 실패 무시 금지와 충돌.
- 추천 A. job timeout 10분 제안, 복구 후 재실행. 도구 비정상 종료·미완료 보고서·
  DB 다운로드 실패를 vulnerability와 다른 failure reason으로 기록한다.
  예외·continue-on-error·오래된 성공 결과로 장애를 숨기지 않는다.

### D6. 실패 재현

- A: 격리 Gradle fixture에 `org.apache.logging.log4j:log4j-core:2.14.1`을 runtimeOnly로
  추가하고 실제 해석 좌표 및 기준 초과 advisory를 확인한 뒤 같은 scan 경로로 실패 검증.
  재현이 쉽지만 현재 DB 매칭은 실행 시 확인 필요.
- B: 저장소의 기존 BOM override를 취약 버전으로 낮춘 임시 workspace.
  실제 앱과 가깝지만 BOM 충돌·기존 수정 훼손 위험 및 빌드 비용 증가.
- 추천 A와 현재 main baseline을 함께 실행. fixture가 의도한 버전으로 해석되지 않거나
  advisory를 찾지 못하면 fixture 검증 실패이며 scanner 오류를 성공적인 재현으로 세지 않는다.
  취약 버전 코드를 실행하거나 서비스를 시작하지 않는다.

### D7. 시간 측정

- A: 동일 SHA·runner 종류에서 cold/warm 각 3회; setup, Gradle resolve, DB, scan,
  판정·artifact와 job 전체 시간의 중앙값·최댓값 및 cache hit 기록.
  측정 비용이 있지만 변동을 드러낸다.
- B: 단일 run 전체 시간만 기록. 저렴하지만 캐시 효과와 runner 대기 시간을 혼동.
- 추천 A. 기존 CI 대비 전체 critical path 증가와 추가 runner 시간을 구분하고 queue 시간은
  별도 기록한다. 같은 SHA라도 DB 시각을 기록해 비교한다.
  warm 중앙값 3분·cold 최대 10분을 초기 목표로 제안하며 실측 보장은 아니다.
  초과 시 원인을 보고하고 timeout 상향·범위 축소는 재승인받는다.

## 13. Baseline and remaining evidence

- 관찰 SHA: `15bae1e2f13672e77d1ed68b7b52a2042289f403`.
  작업 시작 시 HEAD/main/origin/main이 같았다. 네트워크 fetch로 원격 최신성을 재확인하지 않았다.
- #360에 기록된 jackson 2.21.7, postgresql 42.7.12, tomcat 10.1.61,
  netty 4.1.137.Final override가 build.gradle에 존재한다.
- #360에서 httpcore5/httpclient5/log4j-api/commons-lang3와 spring-webmvc 항목이 제외됐다.
  새 스캔에서 재평가할 후보이며 오늘의 검출 건수·심각도·수정 가능 버전은 미확인이다.
- 로컬 osv-scanner/trivy/dependency-check 실행 파일은 PATH에서 발견되지 않았다.
  설치하지 않았으며 실제 baseline 결과를 추정해 통과 처리하지 않는다.
- 승인 후 고정 버전 scanner를 CI에서 준비하고, 잠금 해석→전체 결과 생성→예외 검증→
  심각도 판정 순으로 실행한다. CLI 정확한 옵션과 종료 코드는 고정 release로 확인한다.
  baseline의 각 기준 초과 항목에 업그레이드 또는 승인 예외를 대응시키고 처리 후 재스캔한다.
- 미확인: 실제 Ruleset required checks, scanner release/SHA, baseline 건수,
  각 configuration 최종 해석 그래프, 성능 수치, fork CI 동작.
  이 값들은 구현·검증 단계의 증거로 채우며 본 계획 승인과 혼동하지 않는다.
