# Test Report: TEST-PLAN-GH-386-TEST-GIT-ENV

> Created at: `2026-11-24T19:20:39+09:00`
> GitHub Issue: `#386`
> Branch: `fix/gh-386-test-git-env-isolation`
> Base commit: `c1d7d7d4` (검증 대상은 이 기준점 위의 미커밋 변경)

## 1. Executive summary

- Result: `BLOCKED` (로컬 구현·검증은 통과, 최종 CI·실제 PR push 인수는 미실행).
- Tested scope: UNIT-001~006, INT-001/004/006. 이전 전체 검증 XML은 단위 1,555개,
  통합 882개, 아키텍처 23개, 소스 규칙 3개 모두 실패·오류·skip 0이다.
  후속 메서드 분리 뒤 최신 단위 XML은 RepoMapToolTest 16개이며 모두 성공했다(§10).
- Unverified scope: 원격 CI policy job 실행, 별도 사람 승인이 필요한 INT-007.
- Release recommendation: 오케스트레이터에게 반환한다. 로컬 성공을 CI 성공이나 실제
  PR push 승인으로 대체하지 않는다. 실제 저장소에서는 commit/push/PR을 수행하지 않았다.
- issue_number: 386.
- task_id: `fix/gh-386-test-git-env-isolation`.
- design_id: 해당 없음. 테스트 계획은 `TEST-PLAN-GH-386-TEST-GIT-ENV`.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.13.0.3 |
| Spring Boot | 3.5.16 |
| Database | 로컬 Docker/Testcontainers, Docker 29.6.2 |
| Test runner | JUnit 5 |
| Git / Python / Node / npm | 2.56.0 / 3.14.10 / 26.13.0 / 11.20.0 |

모든 Gradle 실행은 인계받은 JDK 21의 JAVA_HOME을 사용했다. gh와 harness에는
`env -u SSL_CERT_FILE -u SSL_CERT_DIR`를 적용했다. 최종 TTY 검증은
`GIT_PAGER=cat`으로 pager 대기를 차단했다. 상시 Python fixture도 별도 pager 값을
사용하며, 실제 hook이나 검사 항목을 우회하지 않는다.

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests com.dnd.qello.harness.RepoMapToolTest --rerun` | PASS | 기존 14 + 신규 2, skip 0 | 후속 분리 뒤 build 21s; 최신 XML 19.015s | `build/test-results/test/TEST-com.dnd.qello.harness.RepoMapToolTest.xml` |
| `./gradlew javaConventionArchitectureTest --tests com.dnd.qello.architecture.ChangedJavaTypesTest` | PASS | 5, skip 0 | build 2s; XML 0.458s | 해당 architecture XML |
| `node scripts/python.mjs scripts/test-run-hook.py` | PASS | 3 | 최종 직접 실행 1.415s | 직접 실행 exit 0 |
| `node scripts/python.mjs scripts/validate-workflows.py .github/workflows/harness-policy.yml` | PASS | workflow 1개 | 미계측 | exit 0 |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check` | PASS | 새 회귀 3개 포함 | 최종 별도 실행 회귀 1.373s | 전체 검사 및 `Harness checks passed`, exit 0 |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests` | PASS | full check | 최종 build 약 1분 | `Local PR readiness checks passed`, exit 0 |
| `npm run hooks:validate` | PASS | 기존 Husky 계약 | 미계측 | exit 0 |
| `GIT_MASTER=1 git diff --check` | PASS | 전체 변경 공백 | 미계측 | exit 0 |
| `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-run --id TEST-PLAN-GH-386-TEST-GIT-ENV` | PASS | test + integrationTest + 보고서 생성 | build 996ms / 748ms, UP-TO-DATE 포함 | pr-ready 이후 직렬 실행 exit 0, 이 템플릿 생성 |
| 분리 전 전체 단위 XML 합계 | PASS | 206 suites / 1,555 tests | suite time 합계 46.090s | 이전 전체 검증, failure/error/skip 모두 0; 최신 단일 클래스 결과와 구분 |
| 최종 통합 XML 합계 | PASS | 114 suites / 882 tests | suite time 합계 125.078s | failure/error/skip 모두 0 |
| 최종 architecture/source XML | PASS | 23 / 3 tests | suite time 3.754s / 0.991s | failure/error/skip 모두 0 |

build 시간과 XML suite time은 서로 다른 지표다. UP-TO-DATE인 후속 명령을
테스트 재실행으로 표현하지 않는다. INT-001은 별도로 재실행을 강제했다.
기존 build 설정에서 비활성화된 checkstyleTest/checkstyleIntegrationTest는 그대로이며,
이번 작업에서 검사 skip이나 suppression을 추가하지 않았다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | RepoMapToolTest 기존 14개 | 깨끗한 환경과 INT-001에서 모두 성공 |
| UNIT-002 | PASS | ChangedJavaTypesTest 5개 | 기존 origin/main 및 missing-origin 계약 유지, 소스 수정 없음 |
| UNIT-003 | PASS | foreignGitEnvironment | D2 변수 각각 및 조합, fixture만 변경, sentinel 메타데이터 불변; 후속 재실행 0.995s |
| UNIT-004 | PASS | foreignPythonEnvironment | Python generate/check/query·HEAD·parse 실패·설정/인증/비Git/JAVA_HOME 보존; 후속 재실행 1.420s |
| UNIT-005 | PASS | Python 회귀 2개 | linked worktree 재탐색, 정확히 네 위치 변수만 제거, config 보존, 자식 exit 23 전파 |
| UNIT-006 | PASS | Python 회귀 1개 | pre-commit의 실제 대체 index와 두 메시지 hook 환경 보존 |
| INT-001 | PASS | 일회용 clone의 기존 14개 | 절대 GIT_DIR, 강제 재실행, skip 0, sentinel 불변 |
| INT-004 | PASS | 일회용 linked worktree의 실제 로컬 push | 정상 Husky/pre-push 경유, 최초 full 검사 실행 후 최종 push는 캐시로 확인; 최종 config·HEAD·index·staged inventory 불변 |
| INT-006 | PASS | Java/Python 독립 격리 제거 mutant | 의도한 실패 확인 후 정상 바이트 복원·green, 공통 검사 실패 전파도 확인 |
| INT-007 | BLOCKED / 미실행 | 실제 PR branch push | 별도 사람 승인 없음. 실제 worktree에서는 push하지 않음 |

위 suffix는 모두 `TEST-PLAN-GH-386-TEST-GIT-ENV-` 접두사를 갖는다.

### 일회성 red/green 및 불변 증거

모든 일회성 실행은 `mktemp -d` 아래 `git clone --no-local`로 만든 독립 clone과
sentinel·로컬 bare remote만 사용했다. 실제 저장소에 worktree를 추가하거나 실제
git dir을 환경에 주입하지 않았다. mutable Git 경로는 임시 최상위 경로 아래로 한정했다.
fixture HOME 및 GLOBAL/SYSTEM 설정도 임시 경로를 사용했다.

- Java 수정 전 red: 일회용 clone에서 회귀만 추가하고 격리는 없는 상태로
  `./gradlew test --tests "com.dnd.qello.harness.RepoMapToolTest.foreign*Environment" --no-daemon --rerun-tasks`.
  exit 1, 2개 실패, build 12s. 실제 수정 전에 실패를 확인했다.
- Java 최종 회귀 mutant: 같은 명령으로 command의 제거 블록만 삭제한 clone 실행.
  안정화된 실행은 exit 1, 2개 실패, build 15s. UNIT-003은 fixture init exit 128,
  UNIT-004는 Python generate exit 2와 `root must be the repository root`로 실패했다.
  UNIT-004의 fixture 초기화는 오염 주입 전에 끝나므로 Python 경계 자체의 실패다.
  복원 후 같은 강제 실행 exit 0, build 18s.
- Python 수정 전 red: 일회용 스크립트 복사본의 `python3 -B scripts/test-run-hook.py`.
  3개 중 UNIT-005 1개 실패, exit 1, wall 1.20s. sentinel을 linked worktree로
  잘못 탐색하는 실패였다. 수정 후 3개 성공, wall 1.22s.
- Python 격리 제거 mutant: 일회용 복사본의 제거 루프만 삭제.
  같은 UNIT-005 실패, exit 1, wall 1.70s. 바이트 동일 복원 후 3개 성공,
  wall 1.49s. 최종 fixture 제어를 반영한 clone도 cmp 일치, 3개 성공, 1.191s.
- 실제 공통 실패 전파: 최종 clone에서 pre-push 제거 루프만 다시 삭제하고
  `env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check`와
  `env -u SSL_CERT_FILE -u SSL_CERT_DIR python3 scripts/run-hook.py pre-push` 실행.
  두 명령 모두 새 회귀의 동일한 실패를 받아 exit 1을 반환했다.
  각각 회귀 시간 1.165s / 1.406s이며 하위 Gradle 실행 전에 중단했다. 이후 원복했다.
- INT-001: `python3 /private/tmp/gh386-evidence.8d3eoR/evidence.py int001`.
  내부 명령은 `./gradlew test --rerun-tasks --no-daemon`에 기존 14개 메서드를
  각각 `--tests`로 지정한다. 자식에 disposable sentinel의 절대 GIT_DIR만 주입했다.
  exit 0, wall 37.925s, XML tests=14/failures=0/errors=0/skipped=0, suite 22.493s.
  XML의 14개 testcase 각각을 확인했다.
- INT-001 sentinel: core.bare=false, resolved HEAD와 config/HEAD/index/refs/object
  inventory 전후 동일. config SHA-256 전후
  `cae33efdb02cf774435c1ff9cb16bcc1014606908530c6e1dc727615fe3e8cda`,
  HEAD 파일 SHA-256 전후
  `f6f2b945f6c411b02ba3da9c7ace88dcf71b6af65ba2e0d89aa82900042b5a10`.
- INT-004: 일회용 clone에서만 npm으로 Husky를 설치했다. 해당 환경의 npm 설정은
  prepare 자동 실행을 생략하므로 `npm run hooks:install`을 명시적으로 실행하고
  `.husky/_/pre-push`와 core.hooksPath를 확인했다. hook 우회는 사용하지 않았다.
- INT-004 최종 명령:
  `git push origin HEAD:refs/heads/fix/gh-386-local-hook-verified`.
  origin은 독립 임시 bare remote다. exit 0, wall 2.980s. 실제 hook에서 공통 회귀
  3개는 재실행했고, Gradle full check는 14개 task 모두 UP-TO-DATE로 확인했다.
  실제 Gradle 테스트 재실행은 앞선 59.334s push에서 수행됐다.
  최종 원격 ref는 fixture HEAD와 일치했다. 최종 push 시간을 전체 테스트 재실행 시간으로 보지 않는다.
  config·HEAD·index 바이트 및 staged inventory가 전후 동일했고 clone non-bare=false다.
- INT-004 최종 전후 SHA-256: config
  `b5e240b213bbb5103362ae0c35adcdf71dafa4c2d2755d1e2331ae33726a8299`,
  HEAD `5bd6243c368719b45a2ab33cbd732e7700cb64d0c93df34161ce97c5ad22e44f`,
  index `f9265487bd55d0cb9ecb22a659c346c9e8a9726cd5f53698111a9078ea883da3`.
  staged inventory SHA-256은 전후
  `ef329b0a6957b435ee560881b8de5b780d9297010cfbff84e2d6e96684374e6f`.

최종 Java overlay는 source/clone/linked 모두 SHA-256
`d6392f9d15e9c9ab2d0a5b84978d0713951578315fd59399cd54e14400a5406a`로 일치한다.
INT-001 당시 clone에는 마지막 빈 줄 하나가 추가돼 있었고, 이후 두 Spotless 줄바꿈을
고쳤다. 실행 코드 차이는 없었으며 최종 full check는 최종 바이트로 통과했다.
clone·하위 Gradle 실행을 상시 테스트에 추가하지 않았다.

## 5. Failures and diagnostics

- Java mutant의 첫 시도에서 Git 자동 maintenance의 임시 lock 파일과 snapshot이
  경합했다. fixture commit에 maintenance.auto=false/gc.auto=0을 적용해 원인을
  제거한 뒤 의도한 red를 다시 확인했다. Python fixture에도 같은 제어를 적용했다.
- 최초 TTY Python 실행은 Git pager에서 대기했다. fixture GIT_PAGER=cat으로 해결했다.
  이후 직접 회귀·공통 검사 모두 1초대에 완료했다. 대기한 실행을 정상 소요 시간으로 쓰지 않는다.
- 최초 test-run과 pr-ready를 같은 worktree에서 겹쳐 실행해 integration XML 기록이
  충돌했다. 실행 에이전트의 검증 운영 오류다. 이후 동일 worktree의 Gradle 명령을
  직렬화했으며 최종 pr-ready와 test-run은 연속 exit 0이다.
- 초기 장기 구독은 기본 5분 제한으로 중단됐다. 최종 full 검사에는 30분 제한을
  명시했다. 중단된 실행을 PASS로 계산하지 않았다.
- 최초 full 검사에서 새 Java의 Spotless 줄바꿈 2곳이 실패했다. 해당 코드만 수정하고
  workspace 및 로컬 hook full check 모두 다시 통과했다.
- INT-004 첫 성공 push는 exit 0, 59.334s이고 config/HEAD는 동일했지만 index hash가
  바뀌어 최초 불변 판정은 실패했다. 해당 index의 staged diff는 없었다.
  Git의 stat cache 갱신 가능성은 있으나 바이트 원인을 단정하지 않는다.
  source 수정이나 검사 우회 없이 안정된 동일 fixture에서 새 ref로 재실행했으며,
  최종 push는 index 바이트와 staged inventory까지 동일했다. 최초 불일치를 숨기지 않는다.
- 재현 가능한 최종 필수 검사 실패는 없다. LSP 서버는 설치되지 않아 LSP 진단은
  미실행이며 실제 Gradle 컴파일·Spotless·저장소 정책 검증으로 확인했다.
- blocked_checks: CI policy job 실제 실행, INT-007 실제 PR push.
  CI는 push/PR 생성 없이 실행 증거를 만들 수 없어 로컬 성공과 별도로 미검증이다.

## 6. Potential issues

### Application code

- production Java는 변경하지 않았다. 위험한 임시 저장소 writer는 RepoMapToolTest의
  공통 git/Python 경계였고 그 경계에서만 D2 변수를 제거했다. 이미 격리된
  ChangedJavaTypes는 유지했다. 다른 실제 저장소 읽기 도구의 상속 위험은 계획 조사표에
  남아 있으며 이번 승인 범위를 넓혀 수정하지 않았다.

### Infrastructure and resource limits

- Terraform·AWS 자원 변경 없음. 로컬 검사에는 JDK 21과 Docker/Testcontainers가 필요하다.
  같은 worktree에서 병렬 Gradle 검증을 실행하면 결과 파일 충돌이 있으므로 직렬 실행한다.

### Database and migrations

- DB schema·migration 변경 없음. 기존 통합 검사 882개는 최종 XML에서 성공했다.

### Concurrency and idempotency

- 상시 테스트는 매번 독립 @TempDir/TemporaryDirectory를 사용한다. Java는 자식 환경
  사본만 정화하고 Python runner도 사본만 전달한다. Python 테스트의 scoped patch는
  각 호출 뒤 복원하며 부모 환경 불변을 검증한다. sleep·시간 임계값 assertion은 없다.

### Transactions and event ordering

- 트랜잭션·이벤트·원장 로직 변경 없음. 이번 테스트가 새 업무 동시성 보장을 추가하지 않는다.

### External APIs

- 상시 회귀와 일회성 push는 외부 GitHub/AWS API에 쓰지 않는다. 실제 remote 인증과
  원격 CI는 로컬 bare remote로 검증할 수 없어 미검증으로 남긴다.

### Failure recovery and reconciliation

- 실제 저장소 non-bare 검사를 단계 전후 수행했다. true는 한 번도 관찰하지 않았고
  실제 Git 설정 복구·rewrite를 실행하지 않았다. 테스트 자식은 bounded timeout으로
  종료를 확인하고 상시 fixture는 자식 종료 뒤 정리한다. 일회성 임시 clone/remote와
  안전하게 정리한 증거 파일은 오케스트레이터 확인을 위해 보존했다.

## 7. Regression and residual risk

- status: BLOCKED. 로컬 9/10 시나리오 통과, INT-007은 미실행. CI 실행도 미검증이다.
- passed_checks: §3의 모든 로컬 필수 명령, UNIT-001~006, INT-001/004/006.
- failed_checks: 최종 미해결 실패 없음. 예상 red와 해결한 운영·형식 실패는 §5에 구분했다.
- assumptions: 확인한 사람 승인 범위는 D1~D5와 공통 검사·CI 한 단계 등록뿐이다.
- required_human_decisions: 오케스트레이터의 commit/PR 절차 및 INT-007 실제 push 별도 승인.
- risks: 실제 PR push의 인증·환경 차이, 최초 disposable index hash 불일치의 세부 원인,
  원격 CI 플랫폼 결과는 로컬 결과로 확정할 수 없다. 새 추적 Issue나 외부 코멘트를 생성하지 않았다.
- 실제 저장소 common config, main HEAD/index SHA-256은 시작·종료 동일했다:
  config `718f14ed9709bc1463171d70387c9ef6898de475a6105d826107fb7f8fae225b`,
  HEAD `e5e11bd0138be8f652c716a1edacce370b5c59ab7f868e62655aec4b9605e1c1`,
  index `af761b960137c510d010e70147e8b563c22b7931b8f3789bdde6559a3ad2437c`.
- 기존 사용자 소유 TASK SHA-256
  `86ffb8533a1fb2a7b886dc6d11eda729b4ec9b3fafabc7ffa9cb1bfa117c3201`,
  승인 계획 SHA-256
  `848c77080806755cf2338d5e4f5fe56d98073de2fb3dfc4c7747c4501d495a35`를 보존했다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-386-TEST-PLAN-GH-386-TEST-GIT-ENV.md`.
- CI run: 미실행. workflow 정적 정책 검사와 로컬의 동일 회귀 실행만 확인했다.
- Related ADR: 해당 없음.
- PR: 생성하지 않음.
- changed_files:
  `src/test/java/com/dnd/qello/harness/RepoMapToolTest.java`,
  `scripts/run-hook.py`, `scripts/test-run-hook.py`,
  `scripts/harness.py`, `.github/workflows/harness-policy.yml`, 이 보고서.
- E2 상세 증거: `/private/tmp/gh386-e2.XdV1xo/evidence.txt` 및 red/green/mutant/restored 로그.
- 일회성 상세 증거: `/private/tmp/gh386-evidence.8d3eoR/evidence.py`,
  `results.txt`, `int001.log`, `int004.log`, clone의 JUnit XML.
  driver의 stdout 불변 요약은 실행 에이전트가 관찰해 이 보고서에 옮겼다.

## 9. Reviewer checklist

- [x] 보고서에 실제 설정 원문·환경 비밀값·원격 주소를 기록하지 않음.
- [x] 미실행 CI 및 INT-007 명시.
- [x] 잔여 위험을 #386의 이 보고서에 연결. 별도 Issue 생성 권한은 사용하지 않음.
- [ ] PR 설명과 대조: PR을 생성하지 않아 해당 없음.

독립 reviewer `st_01a20d0c`가 실제 diff와 회귀 실행 증거를 확인했다.
리뷰어는 테스트를 직접 실행하지 않았다.
최종 코드·보고서 판정은 CLEAR/APPROVE이며 CRITICAL/HIGH/MEDIUM 결함은 없었다.
중복 헤더, 한정된 fully-qualified 이름, fixture 내부 임시 로그 등 LOW 관찰은
이번 범위를 넓혀 정리하지 않았다. 초기 리뷰 시점의 전체 검사·Java mutant·로컬 push
증거 부족은 이 보고서와 상세 receipt로 보완했고 최종 독립 대조도 완료했다.
리뷰어는 최종 source/clone/linked hash, 단위 1,555개·통합 882개·아키텍처 23개의
XML 합계, 실제 로컬 hook 로그와 CI·INT-007 미실행 표기를 독립 확인했다.
source 규칙 3개 합계는 실행 에이전트가 확인했으며 리뷰어가 별도 합산하지는 않았다.
보고서 생성 후에도 harness check와 git diff --check가 exit 0이었고,
해당 공통 회귀 3개 실행 시간은 1.017s였다.

## 10. 커밋 차단 후속 수정 (2026-11-24)

- 이번 수정 범위 상태: PASS. pre-commit에서 QELLO-JAVA-SIZE-001이
  foreignPythonEnvironment 53줄(최대 50줄)을 검출했다. 기존 full check는
  checkstyleTest가 비활성화되어 이 staged 전용 검사를 실행하지 못했다.
- Python 환경 probe와 관련 assertion 블록을 assertPythonEnvironment helper로
  그대로 이동했다. assertion의 내용·실행 순서를 보존했으며 테스트 본문은 33줄이다.
  해당 파일 전체 checkstyleStagedJava XML에 error가 없어 다른 메서드도 규칙을 통과했다.
- JDK 21에서 `./gradlew test --tests com.dnd.qello.harness.RepoMapToolTest --rerun`:
  exit 0, build 21s. 최신 XML tests=16, failures=0, errors=0, skipped=0,
  suite time=19.015s. 전체 단위 테스트 1,555개를 이번 수정 후 다시 실행한 것은 아니다.
- 한 파일 manifest `/private/tmp/gh386-staged-fix.dT9Oiv/manifest.json`의 내용은
  `{"paths":["src/test/java/com/dnd/qello/harness/RepoMapToolTest.java"]}`이다.
  `./gradlew javaConventionStagedCheck -PqelloConventionManifest=/private/tmp/gh386-staged-fix.dT9Oiv/manifest.json -PspotlessFiles=/Users/byuntil/.herdr/worktrees/dnd-15th-2-backend/fix-gh-386-test-git-env-isolation/src/test/java/com/dnd/qello/harness/RepoMapToolTest.java`:
  exit 0, build 7s. pre-commit과 같은 Spotless·Checkstyle·source/architecture 규약을
  실제 작업 트리 파일에 적용했다. index 보존 요청 때문에 hook의 부분 staging 확인을
  직접 실행한 것이 아니라 승인된 manifest 방식으로 동등한 Java 규약 gate를 실행했다.
- `git diff --check`: exit 0. 작업 전후 index SHA-256은
  `8c98586bee9aba31b0612c6b0c9e623eb0c703a469307a01a5a6e29ab53c2aec`로 동일했다.
  작업 트리 Java 파일과 이 보고서만 수정했고 commit/stage/push는 수행하지 않았다.
  실제 저장소는 계속 non-bare=false였다. 기존 CI·INT-007 미실행 상태는 그대로다.
