# Test Plan: TEST-PLAN-GH-386-TEST-GIT-ENV

> Created at: `2026-11-24T18:13:40+09:00`
> GitHub Issue: `#386`
> Status: APPROVED

## 1. Objective

linked worktree의 pre-push에서 상속된 Git 저장소 환경이 임시 테스트 저장소 밖으로
전파되지 않게 한다. 실제 저장소의 config, HEAD, index, refs를 보존하면서 기존
RepoMapToolTest 14개가 통과하는 수정과 회귀 검증을 설계한다.

이 문서는 2026-11-24 사람 결정이 반영된 승인 계획이다. 테스트 통과나 실제 push 승인
증거는 아니다. 현재 계획 수정 작업은 이 파일 하나만 수정하며 기존 TASK.md 변경을 보존한다.

## 2. Scope

### Included

- RepoMapToolTest의 git 및 Python 하위 프로세스 환경 격리.
- scripts/run-hook.py의 pre-push 하위 검사에만 네 저장소 위치 변수 제거.
- scripts/test-run-hook.py를 harness 공통 검사와 CI policy job에 등록.
- src/test, src/integrationTest, scripts의 직접/간접 Git 실행 조사.
- 제거 변수, 수정 경계, 안전한 회귀 재현, linked worktree push 인수 절차.
- 승인된 가벼운 상시 회귀, 일회성 실행 증거 및 실행 에이전트별 소유 파일.

### Excluded

- production Java, DB, 인프라 변경. CI는 harness-policy.yml의 policy job에 승인된
  테스트 실행 단계 하나 추가만 허용하며 기존 단계 순서·권한·action 고정은 변경하지 않는다.
- .husky/*, CLAUDE.md, AGENTS.md, .claude/**, agents/**, scripts/guard-* 수정.
- build.gradle 변경 및 clone/하위 Gradle을 실행하는 상시 통합 테스트 추가.
- 실제 저장소 또는 그 linked worktree의 git dir을 환경에 주입하는 재현.
- 현재 단계의 테스트 실행, 커밋, push, PR, Issue 코멘트, Git 설정 변경.
- 기존 테스트의 무관한 정리와 이미 격리된 ChangedJavaTypes 계열의 일괄 리팩터링.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #386, TASK.md | CONFIRMED: 절대경로 GIT_DIR 상속 시 14개 실패와 core.bare 변경이 보고됨. 이번 계획 세션에서는 재현하지 않음 |
| TASK.md Completion criteria | CONFIRMED: 일회용 저장소로만 재현하고 core.bare 및 HEAD 불변을 확인. 사람 승인 후 실제 push와 공통 config 불변 확인 |
| 오케스트레이터 수정 인계 | CONFIRMED: @Byuntil의 2026-11-24 D1-D5 결정을 반영하고 이 파일만 APPROVED 처리. 실제 push는 별도 승인 |
| 현재 코드 | CONFIRMED: RepoMapToolTest.command는 cwd만 바꾸고 JAVA_HOME 외 부모 환경을 상속 |
| Git 공식 githooks(5), DESCRIPTION | CONFIRMED: Git은 hook에 GIT_DIR, GIT_WORK_TREE 등의 저장소 환경을 export하며 다른 저장소/다른 worktree 작업 전 제거를 안내 |
| Git 공식 git-rev-parse(1), Options for Files | CONFIRMED: --local-env-vars는 저장소 로컬 변수의 이름을 출력하며 실제 설정 여부/값이나 hook별 export 목록은 출력하지 않음 |
| Git 공식 git(1), Environment Variables / The Git Repository; git-config(1), ENVIRONMENT | CONFIRMED: 경로 변수, 탐색 변수, 사용자 설정 및 command-scope 설정의 의미 |
| API / schema / ADR | 해당 없음: 애플리케이션 계약 변경 없음 |

공식 문서는 2026-11-24 Context7의 Git 공식 문서 저장소 `/git/htmldocs`에서
위 절을 확인했다. 인계의 URL 기록 금지에 따라 문서명과 절로 출처를 남긴다.
문서 검색의 특정 Git 버전 고정 여부는 미확인이다. 로컬 Git은 2.56.0이다.
메인 checkout에서 상대경로 GIT_DIR 때문에 문제가 없다는 설명은 Issue에서도 추정이며
확인된 사실로 채택하지 않는다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 절대경로 GIT_DIR 상속 | 실제 공통 config를 bare로 변경, 모든 worktree 손상 | Issue에서 보고됨 | P0 | 일회용 sentinel의 config/HEAD/index/refs 전후 불변, 14개 성공 |
| GIT_WORK_TREE / INDEX / COMMON / OBJECT 경로 상속 | 다른 worktree, index, object store 접근 | 코드상 경로 지정 가능 | P0 | 변수별 및 조합 입력에서 작업 결과가 대상 fixture에만 존재 |
| git 실행만 격리하고 Python 실행은 상속 | repo-map의 내부 git이 다른 HEAD/파일 목록을 읽음 | run.py 호출 경계상 가능 | P0 | Python을 통한 generate/check/query의 실제 결과 검증 |
| hook의 모든 환경을 무조건 제거 | pre-commit의 대체 index를 무시하여 잘못된 staged 검사 | run-hook.py가 --cached 사용 | P0 | pre-push만 네 변수 제거, 나머지 hook과 GIT_CONFIG_* 보존 회귀 |
| GIT_* 전체 삭제 | 사용자 config 경로/인증/실행 정책 유실 | 기존 ChangedJavaTypes 방식의 잔여 위험 | P1 | 새 수정은 명시적 목록 적용; 사용자 설정 경로 보존 검증 |
| Gradle 캐시로 테스트가 실행되지 않음 | 일회성 증거가 거짓 통과 | 재실행 시 가능 | P0 | 일회용 clone에서 재실행 강제, 기존 14개 각각의 JUnit 결과와 skip 0 확인 |
| 독립 Python 회귀 실행 누락 | hook 정화의 퇴행 미검출 | 수정 전 공통 검사와 CI에 unittest 연결 없음 | P0 | harness check 및 pre-push 공통 검사와 CI policy job에서 scripts/test-run-hook.py 실행·성공 증거 확인 |
| 실패 정리 과정의 실제 저장소 접근 | 재현보다 복구가 더 큰 손상 유발 | 경계가 잘못되면 가능 | P0 | 모든 mutable Git 경로가 임시 최상위 아래임을 실행 전 검증 |

### 4.1 조사된 Java 실행 경계

| 파일 / 호출 | 임시 저장소 | GIT 환경 | 판단 |
| --- | --- | --- | --- |
| src/test/java/com/dnd/qello/harness/RepoMapToolTest.java: command/init/run | @TempDir에 init/add/commit, Python repo-map 실행 | 전부 상속, JAVA_HOME만 설정 | 같은 위험 있음. 직접 수정 대상 |
| src/test/java/com/dnd/qello/architecture/ChangedJavaTypesTest.java: git | @TempDir에 init/add/commit/update-ref | GIT_* 전체 제거 | 같은 파괴 경로는 이미 차단. 이번 수정 제외 |
| src/test/java/com/dnd/qello/architecture/ChangedJavaTypes.java: git | 호출자가 준 임시 또는 실제 저장소 | GIT_* 전체 제거 | diff/ls-files/rev-parse 읽기. 기존 격리 유지 |
| src/test/java/com/dnd/qello/architecture/ProductionConventionRatchetTest.java | 실제 저장소 또는 staged manifest | ChangedJavaTypes에 위임 | bare 변경의 2차 피해자. 자체 init 없음 |
| src/test/java/com/dnd/qello/convention/JavaConventionBaselineTest.java: run/Gradle 호출 | JSON fixture만 임시, Git은 실제 root | 상속 | validate-java-conventions.py의 git show에 영향 가능. init 변조와는 다름 |
| src/test/java/com/dnd/qello/convention/staticanalysis/JavaStaticAnalysisRuleTest.java | Java fixture 파일, Git 저장소 생성 없음 | Gradle에 상속 | baseline 검사까지 간접 도달. 실제 root를 읽는 경로 |
| src/test/java/com/dnd/qello/harness/PlatformCommands.java, PlatformCommandsTest.java | 없음 | Python probe에 상속 | Python 버전 확인만 수행, Git 실행 없음 |
| src/test/java/com/dnd/qello/infrastructure/PushSecretEncodingTest.java | 임시 파일만 사용 | env.clear 후 PATH/HOME만 설정 | Git 실행 없음, Git 환경도 상속하지 않음 |
| src/integrationTest/java/com/dnd/qello/infrastructure/PushSecretDeploymentIntegrationTest.java | 배포 sandbox, Git 저장소 아님 | env.clear 후 제한된 변수만 설정 | 같은 Git 상속 위험 없음 |
| src/integrationTest/java/com/dnd/qello/StructuredLoggingProfileIntegrationTest.java | JVM argument file만 임시 | QELLO_APP_VERSION 외 상속 | 로깅 probe JVM, Git 실행 없음 |

검색은 ignore 규칙 때문에 첫 검색이 파일을 누락하여 gitignore=false로 다시 수행했다.
ProcessBuilder, Runtime.getRuntime, subprocess, os.system, os.popen 및 Git 문자열을
검사했다. 변수 상속 여부와 실제로 저장소를 변경하는 명령의 존재를 구분한다.

### 4.2 조사된 스크립트 실행 경계

스크립트 조사에는 독립 읽기 전용 조사 에이전트의 파일 확인 결과를 통합했다.
아래의 상속은 별도 표시가 없으면 subprocess에 env를 지정하지 않아 부모 환경을
그대로 넘긴다는 뜻이다. 읽기 전용 Git도 잘못된 저장소를 읽을 위험은 있다.

| 파일 / helper | 임시 저장소 | GIT 환경 | 위험 / pre-push 도달 여부 |
| --- | --- | --- | --- |
| scripts/run-hook.py: run, staged_files, ensure_java_not_partially_staged | 없음 | 상속 | diff 및 하위 검사 전달. pre-push 진입점 |
| scripts/harness.py: run, git_text, refresh_local_branch | 없음 | 상속 | pr-ready에서 fetch, 경우에 따라 ff-only merge. 다른 명령은 switch/config/rebase도 수행 |
| scripts/preflight.py: git_paths, staged_paths, read_lines | 없음 | 상속 | ls-files/diff/show 조회. pre-push 도달; pre-commit은 대체 index의 staged blob을 읽음 |
| scripts/validate-husky.py: tracked_mode | 없음 | 상속 | index의 파일 mode 조회. pre-push 도달 |
| scripts/validate-conventions.py: current_branch | 없음 | 상속 | branch 조회. pre-push에서는 --self-test라 Git 조회 경로 미실행 |
| scripts/format-commit-msg.py: current_branch, rebase_branch | 메시지 파일만 임시 | 상속 | branch/rebase 문맥 조회. pre-push self-test에서는 Git 미호출; 일반 실행은 메시지 파일 수정 |
| scripts/validate-java-conventions.py: git_bytes, git_blob_sha256 | 없음 | 상속 | git show 조회, suppression XML 생성 가능. self-test가 아닌 Gradle 검사 경유로 Git 도달 |
| scripts/repo-map/run.py: execute, git, snapshot, output_path | 호출자가 전달; RepoMapToolTest에서는 임시 | 상속 | Git 조회, generate는 artifact 작성. Java에서 Python 경계도 정화해야 함 |
| scripts/test-dependency-scan.py: DependencyScanTest | 없음 | Git 호출 없음 | scripts/test-*.py의 유일한 파일. import한 순수 정책 함수만 검사 |
| scripts/check-dependency-scan.py | 없음 | OSV scanner subprocess에 상속 | Git 직접 호출 없음, 임시 저장소 init 없음 |
| scripts/experiments/codex-instruction-phase2.py: _run_read_only, _git_snapshot, _tracked_manifest_sha256, build_source_manifest, _tracked_changes | 기존 candidate/verification 저장소; self-test 임시 폴더는 Git 저장소 아님 | Git은 상속, Codex run_process만 allowlist | snapshot/manifest 조회 오염 가능, Codex workspace-write도 지원. pre-push 미도달 |
| scripts/experiments/codex-instruction-phase3.py: git | 기존 candidate 저장소 | phase2 Git helper에 상속 | Git 조회. self-test는 JSONL fixture만 사용. pre-push 미도달 |
| scripts/experiments/codex-instruction-supplement.py: verify, run_next | 기존 candidate 저장소 | phase2 검증 helper에 상속 | 간접 Git 조회, pre-push 미도달 |
| scripts/experiments/codex-agents-eval.zsh | 전달받은 worktree | Codex에 상속 | 고정 Git 호출 없음; read-only sandbox의 agent가 간접 실행 가능. pre-push 미도달 |
| scripts/python.mjs: runsPython3, spawnSync | 대상에 따름 | 상속 | npm Python wrapper. native hook은 이 wrapper 대신 Python 직접 실행 |
| scripts/find-python.sh: try_python | 없음 | probe에 상속 | interpreter 선택만 수행. hook 시작 시 도달하지만 Git 미호출 |
| scripts/install-shortcuts.zsh | 없음 | 생성 함수가 harness에 상속 | 설치는 shell 설정 작성, 이후 h/hpr 등이 Git에 도달. pre-push 미도달 |
| scripts/setup-macos.sh | 없음 | 설치 명령에 상속 | Git은 availability 확인; --install-hooks의 npm prepare/Husky가 설정 변경 가능. pre-push 미도달 |
| harness | 없음 | exec로 상속 | harness.py wrapper. native pre-push는 Python 파일 직접 호출 |

결론: 추가로 수정할 미격리 임시 저장소 writer는 조사에서 발견하지 못했다.
실험 스크립트의 조회 환경 상속은 별도 위험으로 기록하되 이번 fixture 수정 범위를
확대하지 않는다. repo-map/run.py 자체에 전역 제거를 넣으면 도구 호출자가 의도한
저장소 문맥까지 바꾸므로, 이를 호출하는 RepoMapToolTest.command에서 격리한다.

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-001 | 오염 없는 환경과 기존 RepoMap fixture | 기존 RepoMapToolTest 전체 실행 | 기존 14개 모두 성공, 기존 AST/metadata/query 의미 보존 | P0 | E1 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-002 | 기존 ChangedJavaTypes의 임시 저장소 fixture | 기존 ChangedJavaTypesTest 실행 | origin/main 비교와 missing-origin 실패 계약 유지, 추가 수정 불필요 | P1 | V1 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-003 | @TempDir의 대상 fixture와 다른 HEAD/config를 가진 sentinel; 부모 환경을 흉내 내는 D2 변수 주입 | RepoMapToolTest의 실제 command 경계로 git init/add/commit 실행 | 결과는 fixture에만 생김; sentinel의 core.bare=false, HEAD/refs/config hash 불변 | P0 | E1 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-004 | 같은 격리 fixture와 임시 사용자 config, 인증/비Git sentinel 환경 | 같은 command 경계로 Python repo-map generate/check/query 및 기존 parse 실패 경로 실행 | fixture의 소스/HEAD로 결과 생성, sentinel 불변; 사용자 설정 경로·인증·JAVA_HOME 보존, 의도된 실패는 숨기지 않음 | P0 | E1 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-005 | 독립 임시 저장소의 linked worktree를 ROOT로 하고 네 위치 변수는 별도 sentinel을 가리킴 | 실제 run-hook.py의 pre_push가 가벼운 probe 하위 프로세스 실행 | 네 변수만 제거, GIT_CONFIG_* 등은 보존; 실제 Git이 cwd와 .git 파일로 ROOT worktree/HEAD를 찾음; sentinel 불변 | P0 | E2 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-UNIT-006 | 임시 대체 index와 command-scope 설정; pre-commit/commit-msg/prepare-commit-msg 각 경로 | 실제 hook runner의 해당 경로에서 가벼운 검사 probe 실행 | 네 위치 변수와 GIT_CONFIG_*가 그대로 전달되고 pre-commit이 대체 index의 staged 내용을 검사 | P0 | E2 |

UNIT-001/002는 기존 테스트 재사용이다. UNIT-003/004는 기존 RepoMapToolTest 안에서,
UNIT-005/006은 scripts/test-run-hook.py에서 상시 실행 가능한 회귀로 구현한다.
기존 RepoMapToolTest 관례대로 실제 로컬 프로세스를 쓰되 새 clone, 하위 JVM 또는
하위 Gradle을 상시 테스트에 추가하지 않는다. 추가 회귀는 몇 초 안에 끝나는 규모로
fixture와 repo-map 실행 횟수를 최소화하고 실행 시간을 보고한다. 벽시계 임계값을
assert하거나 sleep으로 맞추지 않는다. 기존 14개와 새 테스트 결과 수를 구분한다.

## 6. Integration scenarios (일회성 실행 증거)

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-386-TEST-GIT-ENV-INT-001 | Gradle test JVM, RepoMapToolTest, Git/Python | 독립 clone A와 sentinel B; GIT_DIR은 B의 절대경로만 자식 환경에 설정 | A에서 기존 RepoMapToolTest 14개 강제 실행 | P0: 14개 성공, skip 0; B의 core.bare=false, config/HEAD/index/refs 불변 | 자식 종료 후 A/B만 정리 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-INT-004 | Git, linked worktree, hook/harness | A의 별도 linked worktree와 독립 로컬 bare remote, 필요한 origin/main 및 정책 fixture | 승인된 구현에서 정상 hook 경로로 로컬 push | P0: pre-push 통과, 공통 config/HEAD/index 불변. 원격 ref 변경은 기대 결과 | 실제 저장소와 연결 없는 임시 저장소만 정리 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-INT-006 | 상시 회귀와 격리 제거 mutant | 일회용 clone에서 Java 격리와 pre-push 격리를 각각 독립적으로 제거 | UNIT-003/004 및 UNIT-005를 각 mutant에서 실행하고 원복 후 다시 실행 | P0: 해당 방어를 제거한 경우 의도한 회귀가 red, 원복 후 green; 다른 방어가 결함을 가리지 않음 | mutant를 실제 저장소에 적용하지 않으며 임시 clone만 정리 |
| TEST-PLAN-GH-386-TEST-GIT-ENV-INT-007 | 실제 linked worktree의 PR branch push | 로컬 INT-004 완료와 별도 사람 push 승인 | --no-verify 없이 실제 push | P0 최종 인수: hook 성공, 실제 저장소 non-bare 및 공통 config 불변 | 실제 저장소 자동 복구/설정 변경 금지 |

최종 10개 시나리오: UNIT-001~006, INT-001/004/006/007.
이전 INT-002는 UNIT-003, INT-003/005는 UNIT-004로 통합하여 폐기한다.
위 INT 시나리오는 실행 보고서에 남기는 일회성 증거이며 상시 테스트 클래스가 아니다.
GitEnvironmentIsolationIntegrationTest는 만들지 않는다. 기존 14개 전체 실행,
clone, 하위 Gradle 및 실제 push를 상시 회귀 안에서 반복하지 않는다.
INT-007은 계획 승인만으로 실행하지 않으며 별도 승인 전에는 미실행으로 명시한다.

## 7. Cross-cutting scenarios

### Database and transactions

- 해당 없음. DB schema, transaction, 외부 저장소 데이터 변경 없음.

### Concurrency and idempotency

- 구현은 JVM 전역 환경이나 os.environ을 수정하지 않고 자식별 환경 사본만 사용한다.
  Python 테스트의 부모 환경 주입은 scoped patch로 복원하고 실제 자식 실행 결과를 검증한다.
- 매 실행마다 별도 임시 최상위 경로를 사용한다. 실제 worktree와 .git/common dir 공유 금지.
- 재실행해도 앞선 fixture를 재사용하지 않는다. 임의 sleep/polling 대신 프로세스 종료
  이벤트와 bounded timeout을 사용하고 timeout을 통과나 skip으로 취급하지 않는다.

### External APIs

- 상시 회귀와 일회용 재현은 GitHub/AWS에 연결하지 않는다. remote는 독립 임시 로컬 bare 저장소다.
- 실제 PR push는 별도 사람 승인 이후에만 수행한다. 인증 변수는 무차별 삭제하지 않는다.

### Failure recovery and reconciliation

- 실패 시 실제 저장소의 config를 자동 복구하거나 rewrite하지 않는다.
- 계획/실행 중 실제 저장소의 is-bare-repository가 true면 즉시 중단하고 보고한다.
- disposable sentinel은 실패로 손상될 수 있다. 전후 증거는 메모리에서 비교하고
  파일 내용 대신 일치 여부/해시만 보고한다. 실제 config 원문은 출력하지 않는다.
- 회귀의 red 증명은 수정 전 코드 또는 격리 제거 mutant를 일회용 clone에서만 실행한다.
  red/green 양쪽 모두 실제 저장소의 환경을 주입하지 않는다.

## 8. Test data and isolation

- 상시 Java fixture: @TempDir 아래 대상 저장소 A와 피해 탐지용 non-bare sentinel B.
  B는 첫 commit 및 A와 다른 HEAD/refs/config를 가진다. 빈 HEAD 비교로 만족하지 않는다.
- RepoMapToolTest 안의 helper에서 ProcessBuilder의 상속 환경 사본에 테스트 입력을
  먼저 주입하고, 그 다음 실제 command 경계의 D2 제거를 수행한 뒤 start한다.
  helper에 별도 제거 구현을 복사하거나 제거 후 오염을 주입하지 않는다.
  production seam, 전역 환경 변경, 테스트용 Gradle task는 추가하지 않는다.
- UNIT-003은 절대 GIT_DIR 단독 및 위치 변수 조합으로 fixture 쓰기를 검증하고,
  UNIT-004는 같은 경계의 Python 실행도 검증한다. config 경로/인증 변수/비Git 환경
  보존은 실제 자식 probe와 config 조회 결과로 확인한다. 인증에는 가짜 fixture 값만 쓴다.
- 상시 Python fixture: tempfile.TemporaryDirectory 아래 독립 저장소와 linked worktree.
  importlib로 실제 run-hook.py를 로드하고 ROOT만 fixture의 linked worktree로 한정한다.
  fixture의 가벼운 검사 probe를 실제 자식 프로세스로 실행하여 환경과 cwd를 관찰하고,
  실제 git rev-parse 및 config 조회로 worktree 재탐색과 설정 보존을 확인한다.
  subprocess 전체를 성공 mock으로 대체하거나 전달 argv만 assert하지 않는다.
- UNIT-006은 기본 index와 대체 index의 staged 내용을 다르게 준비하여 실제로 대체
  index를 검사했는지 확인한다. 나머지 두 메시지 hook도 같은 실제 실행 경계의
  환경 보존을 검증한다. 외부 full harness/Gradle 대신 fixture 검사 probe만 사용한다.
- 일회성 INT-001/004/006만 `mktemp -d` 및 `git clone --no-local`을 사용한다.
  Git object hardlink/alternates를 사용하지 않으며 실제 저장소에 worktree를 추가하지 않는다.
  clone에는 미커밋 수정이 없으므로 E1/E2 허용 파일을 현재 바이트로 반영하고 hash를 확인한다.
  INT-004의 branch/TASK 식별자는 #386에 맞추며 origin을 임시 로컬 bare remote로 연결한다.
  clone의 실제 원본 경로 origin을 그대로 둔 채 pr-ready를 실행하지 않는다.
- fixture 초기화/관찰/정리는 오염 주입 자식과 별개 환경으로 실행한다. 주입하는
  git/common/worktree/index/object/alternate 경로는 모두 임시 최상위의 실제 경로 아래여야 한다.
  실제 source 저장소는 clone의 읽기 입력일 뿐이며 그 git dir을 주입하지 않는다.
- sentinel의 core.bare, HEAD 파일 및 해석된 commit, refs, config hash를 전후 비교한다.
  index/object 변수 케이스는 해당 저장소의 index/object inventory도 비교한다.
  config 내용/실제 경로 값은 출력하지 않고 일치 여부만 보고한다.
- 부모에서 환경을 정화한 뒤 필요한 fixture 변수만 주입한다. 임시 HOME 및
  GIT_CONFIG_GLOBAL/SYSTEM을 사용해 개인 hook/서명 설정을 실행하지 않는다.
  테스트용 config에서도 실제 사용자 값/주소/자격 증명을 복사하지 않는다.
- Database isolation: 해당 없음.
- Clock/randomness: Git 작성 시각으로 성공 여부를 판단하지 않는다. 임시 경로의 유일성만 필요.
- External API doubles: 없음. 실제 로컬 Git을 사용하고 외부 remote는 사용하지 않는다.
- Cleanup: 자식 종료 이벤트를 기다린 뒤 프로세스 트리를 종료하고 해당 테스트가 만든
  임시 경로만 정리한다. 정리 실패도 보고하며 다른 테스트의 fixture를 삭제하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | E1: 테스트 환경 수정 | src/test/java/com/dnd/qello/harness/RepoMapToolTest.java | UNIT-001/003/004 | 실제 git/Python 공통 경계의 D2 제거, 가벼운 상시 회귀 |
| 2 | E2: pre-push 방어와 Python 회귀 | scripts/run-hook.py; scripts/test-run-hook.py (신규); scripts/harness.py (validation_commands 한 줄); .github/workflows/harness-policy.yml (policy job 단계 하나) | UNIT-005/006 | pre-push 네 변수만 제거, linked worktree 재탐색 및 나머지 hook/config 보존; 공통 검사·CI에서 회귀 실행 |
| 3 | V1: 독립 검증 및 증거 보고 | docs/reports/tests/gh-386-TEST-PLAN-GH-386-TEST-GIT-ENV.md | UNIT-002, INT-001/004/006/007, 전체 완료 조건 | 실제 변경/검증을 독립 확인하고 일회성 증거와 별도 push 승인 기록 |

E1/E2는 파일 소유가 겹치지 않아 병렬 수행할 수 있고, V1은 두 결과가 합쳐진 뒤 검증한다.
V1은 구현 파일을 수정하지 않으며 report 한 파일만 소유한다.
실제 PR push는 V1의 자동 권한이 아니며 별도 사람 승인 후 승인받은 실행자가 수행한다.

Python 회귀 위치 근거: scripts/test-dependency-scan.py는 unittest.TestCase,
importlib로 대상 스크립트 로딩, unittest.main()으로 직접 실행하는 기존 관례다.
이를 따라 scripts/test-run-hook.py를 추가한다. 추가 승인에 따라
scripts/harness.py의 validation_commands()에 `[python, "scripts/test-run-hook.py"]`
한 줄을 기존 self-test 항목들과 같은 방식으로 등록한다. 기존 항목은 유지하며
./harness check와 pre-push의 pr-ready -> command_check 경로에서 회귀를 실행한다.

.github/workflows/harness-policy.yml의 policy job에는
`python scripts/test-run-hook.py`를 실행하는 단계 하나를 기존 단계들 뒤에 추가한다.
기존 단계의 상대 순서, 권한, action 버전 고정 및 다른 job은 변경하지 않는다.
scripts/validate-workflows.py 정책을 통과해야 하며 검사 실패를 무시하지 않는다.
회귀는 fixture probe만 사용하므로 실제 harness check나 pre-push를 재귀 호출하지 않는다.

package.json과 validate-husky.py는 변경하지 않는다. hooks:validate는 계속
validate-husky.py만 실행하므로 이것만 통과한 결과를 회귀 실행 증거로 쓰지 않는다.
직접 실행 결과 외에 공통 검사와 CI policy job의 실제 회귀 실행 결과를 보고서에 기록한다.

최종 소유 파일은 위 여섯 파일뿐이다. .husky/*, scripts/guard-*, CLAUDE.md, AGENTS.md,
.claude/**, agents/**, build.gradle은 제외한다. 다른 파일이 필요하면 수정하지 않고 보고한다.

실행 전 harness-test-run/SKILL.md와 references/reporting.md를 읽고 사람 승인
증거를 확인한다. 보고서 경로는 위와 같이 승인 범위에 포함되지만 현재 계획 수정 세션은
이 계획 파일 외에는 수정하거나 생성하지 않는다.

## 10. Completion criteria

- [x] D1-D5 사람 결정 반영 및 실행 파일 소유권 확정.
- [ ] 가벼운 상시 UNIT-003~006 구현 및 실행 시간 기록.
- [ ] pre-push에서 네 위치 변수만 제거, 나머지 hook과 GIT_CONFIG_* 보존.
- [ ] 새 JUnit 메서드에 @DisplayName, 클래스 헤더에 실제 ISO 8601 생성 시각과 원본 계획 ID.
- [ ] 기존 RepoMapToolTest 14개가 깨끗한 환경 및 일회용 절대 GIT_DIR 환경에서 성공.
- [ ] 회귀 테스트의 격리 제거 mutant가 일회용 환경에서 의도한 이유로 실패.
- [ ] sentinel 및 실제 작업 저장소의 안전 상태 확인.
- [ ] 기존 ChangedJavaTypesTest 및 scripts/test-run-hook.py 직접 실행 성공.
- [ ] harness check와 pre-push 공통 검사에서 scripts/test-run-hook.py가 실행되고 실패를 전파함을 확인.
- [ ] CI harness-policy.yml의 policy job에서 python scripts/test-run-hook.py 단계 실행·성공 증거 확인.
- [ ] workflow 변경은 policy job의 단계 하나뿐이며 기존 단계 순서·권한·action 고정 유지,
  scripts/validate-workflows.py 정책 통과.
- [ ] 일회성 INT-001/004/006 증거를 보고서에 기록. clone/하위 Gradle 상시 테스트 없음.
- [ ] 별도 사람 승인 후 INT-007 실제 PR push 인수 완료; 승인 전 완료로 표시하지 않음.
- [ ] 아래 완료 명령 모두 성공 또는 실패/차단 사유를 독립 검증자가 명시.
- [ ] 애플리케이션/인프라/DB/동시성/트랜잭션/외부 API/장애 복구 잠재 문제 분석.
- [ ] 승인된 보고 경로에 실행 증거 작성. 실행하지 않은 검사를 PASS로 쓰지 않음.

실행 에이전트의 필수 명령 계약:

```bash
./gradlew test --tests com.dnd.qello.harness.RepoMapToolTest
./gradlew javaConventionArchitectureTest --tests com.dnd.qello.architecture.ChangedJavaTypesTest
node scripts/python.mjs scripts/test-run-hook.py
node scripts/python.mjs scripts/validate-workflows.py .github/workflows/harness-policy.yml
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

CI에서는 policy job의 `python scripts/test-run-hook.py` 실행 결과를 별도로 확인한다.
로컬 명령 성공으로 CI 성공을 추정하지 않으며 CI 미실행은 보고서에 미검증으로 기록한다.

상시 오염 환경 주입은 @TempDir/TemporaryDirectory fixture의 자식 환경에서만 한다.
셸 환경의 절대 GIT_DIR 주입은 일회성 INT-001의 disposable sentinel에만 한다.
INT-001은 `--rerun-tasks --no-daemon`으로 재실행을 강제하고 JUnit XML에서
기존 14개 각각의 성공/skip 0을 확인한다. 추가 테스트 수를 포함한 전체 개수가 14여야
한다고 잘못 assert하지 않는다. 실제 git dir을 셸 명령에 넣지 않는다.
pr-ready는 단순 읽기 검사가 아니라 base branch fetch/fast-forward를 시도하므로
현재 계획 단계에서는 실행하지 않는다. full check는 Testcontainers 환경도 필요하다.
하위 명령 실패, skip으로 14개 조건 미충족, 외부 sentinel 변경, 예상하지 않은
실제 저장소 변경은 FAIL이다. 승인/도구/컨테이너 부재는 BLOCKED이며 PASS와 구분한다.

## 11. Human approval

- Status: APPROVED.
- Reviewer: @Byuntil.
- Approved at: 2026-11-24 (사람 승인 날짜; 시각은 인계에 없어 추정하지 않음).
- Decision: D1·D3 (a)+(c) pre-push 한정 이중 방어; D2·D5 추천안;
  D4 가벼운 상시 테스트 + 일회성 증거.
- Approval evidence: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/386#issuecomment-7128148671
- Additional approval: 승인 시 scripts/harness.py의 validation_commands 등록 한 줄과
  harness-policy.yml의 policy job 테스트 단계 하나 추가를 함께 승인했다.
- Approval provenance: omo-plan-386-revise.md 및 omo-plan-386-amend.md의 사람 결정 전달에 근거.
  에이전트 자체 승인이 아니며 실제 push 승인은 포함하지 않는다.

### D1. 수정 위치와 방어 깊이

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| (a) 각 테스트 프로세스 경계에서 제거 | 직접 Gradle/IDE 실행도 보호, 변경 범위 작음 | 호출부가 늘면 누락 가능 |
| (b) 공통 테스트 유틸 | 여러 위험 호출부의 정책 일치 | 지금 취약한 writer는 한 경계이며 기존 안전 코드까지 변경하면 범위 확대 |
| (c) run-hook.py에서 하위 검사 환경 정화 | hook 경로 전체에 2차 방어 | 직접 테스트 실행은 보호 못 함. 공통 run에 적용하면 대체 index 및 config 전파 의미 변경 |
| (d) Gradle Test task에서 제거 | 모든 Test JVM에 중앙 적용 | Python 검사/IDE 직접 실행 제외, test만 적용하면 다른 Test task 누락. 회귀 주입 자체를 가릴 수 있음 |
| (a)+(c), pre-push만 적용 | 직접 실행과 pre-push 모두 방어 | Python 경계 추가 및 별도 hook 회귀가 필요 |

승인 결정: (a)+(c)의 pre-push 한정 이중 방어.
RepoMapToolTest.command는 git/Python 공통 경계에서 D2 전체 집합을 제거한다.
run-hook.py는 pre-push 하위 검사 시작 전에 GIT_DIR, GIT_WORK_TREE,
GIT_COMMON_DIR, GIT_INDEX_FILE 네 변수만 제거한다. 기존 ChangedJavaTypes,
공통 테스트 유틸, Gradle 설정 및 다른 hook 경로는 변경하지 않는다.

### D2. 제거 변수와 보존 변수

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| GIT_* 접두사 전체 제거 | 짧고 알려지지 않은 로컬 변수도 제거 | 사용자 설정/인증/추적/실행 정책도 유실 |
| 명시적 저장소 변수 집합 | 보존 계약이 분명하고 자식 프로세스마다 git 조회 불필요 | Git 새 변수 도입 시 목록 검토 필요 |
| 매번 git rev-parse --local-env-vars 조회 | Git 버전의 로컬 변수 정의를 따름 | 새 하위 프로세스/오류 경로 발생, ceiling/discovery는 추가 처리 필요 |

승인 결정: RepoMapToolTest에는 명시적 집합. 기준은 로컬 Git 2.56.0의 --local-env-vars 출력과
공식 문서에 있는 추가 탐색 변수다. 아래 목록을 부모 환경의 사본에서 제거한 뒤
fixture에 필요한 JAVA_HOME 등 비Git 환경을 유지한다.
hook pre-push에는 이 전체 목록을 적용하지 않으며 D1·D3의 네 변수만 제거한다.

| 변수 | 이유 / 공식 확인 수준 |
| --- | --- |
| GIT_DIR, GIT_WORK_TREE | 다른 저장소/작업 트리 지정. 공식 hook 설명에서 export 예시로 확인 |
| GIT_COMMON_DIR, GIT_INDEX_FILE | 공통 메타데이터/대체 index 경로. 의미는 공식 문서 확인; 해당 hook에서 항상 export되는지는 미확인 |
| GIT_OBJECT_DIRECTORY, GIT_ALTERNATE_OBJECT_DIRECTORIES | object 쓰기 위치/대체 읽기 위치. alternate 자체는 새 object 쓰기 대상이 아님. 의미 확인; hook별 실제 export는 미확인 |
| GIT_IMPLICIT_WORK_TREE, GIT_GRAFT_FILE, GIT_NO_REPLACE_OBJECTS, GIT_REPLACE_REF_BASE, GIT_PREFIX, GIT_SHALLOW_FILE | 로컬 --local-env-vars 출력에서 확인. 개별 hook export 조건은 미확인 |
| GIT_CONFIG, GIT_CONFIG_PARAMETERS, GIT_CONFIG_COUNT | 로컬 출력에 포함. 다른 저장소에 command-scope 설정 전달을 차단 |
| GIT_CONFIG_KEY_<n>, GIT_CONFIG_VALUE_<n> | COUNT와 함께 주입되는 설정 쌍. COUNT 제거와 함께 해당 숫자 suffix의 쌍도 제거하여 잔여 전달 방지 |
| GIT_CEILING_DIRECTORIES, GIT_DISCOVERY_ACROSS_FILESYSTEM | 탐색 범위 영향. 로컬 목록에는 없으므로 추가. 항상 hook이 설정한다는 뜻은 아님 |

보존: GIT_CONFIG_GLOBAL, GIT_CONFIG_SYSTEM, GIT_CONFIG_NOSYSTEM, HOME,
XDG_CONFIG_HOME, PATH, JAVA_HOME, QELLO_PYTHON. 사용자 설정 경로 제거로 실제
개인 config가 다시 로드되는 일을 피한다. GIT_SSH/GIT_SSH_COMMAND/GIT_ASKPASS,
GIT_TERMINAL_PROMPT 및 일반 trace/editor 변수도 저장소 위치 격리의 제거 대상이 아니다.
fixture에서는 보존할 config를 임시 파일로 명시하여 개인 hook/서명 영향을 배제한다.

주의: --local-env-vars는 hook의 실제 환경 dump가 아니다. Git 공식 문서는
GIT_DIR/GIT_WORK_TREE 등을 예시로 들 뿐 모든 hook/플랫폼의 export 목록을 보장하지 않는다.
값을 로깅하지 않고 변수 이름만 관찰하는 일회용 linked worktree 실험은 승인 후 가능하다.

### D3. hook에서 현재 저장소 환경이 필요한가

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| hook 환경 유지, foreign-repository 테스트에서 격리 | 기존 hook 의미 보존, 직접 실행도 보호 | hook 자체가 다음 취약 테스트를 예방하지는 못함 |
| pre-push의 harness 호출만 정화 | cwd=ROOT로 현재 worktree 재탐색, 방어 깊이 증가 | -c로 주입된 command config까지 제거하면 fetch/인증/정책 의미가 바뀔 수 있음 |
| 공통 run 및 hook 전체 정화 | 구현 위치 하나 | pre-commit 대체 index, staged 검사, commit 메시지 문맥을 바꿀 수 있어 비추천 |

승인 결정: pre-push 하위 검사에만 네 저장소 위치 변수를 제거한다.
GIT_CONFIG_* 전달과 pre-commit/commit-msg/prepare-commit-msg 환경은 유지한다.
현재 pre-push는 run-hook.py -> harness.py
pr-ready -> base 동기화 -> validators -> Gradle check -> git diff --check 순서다.
각 Python runner의 cwd는 ROOT이며 linked worktree의 .git 파일로 현재 저장소를 찾을 수
있으므로 일반 pre-push에서 경로 변수가 반드시 필요하다는 코드는 발견하지 못했다.
이 전제는 UNIT-005에서 실제 linked worktree의 Git 조회로 검증한다.
GIT_CONFIG_PARAMETERS/COUNT/KEY/VALUE와 사용자 설정 경로는 hook에서 제거하지 않는다.

pre-commit의 staged_files, diff --cached --check, preflight --staged와
staged Java manifest는 호출자의 index 선택이 의미를 가진다.
GIT_INDEX_FILE을 공통 run에서 지우면 staged_files와 실제 validator가 서로 다른 index를
볼 수 있다. 따라서 공통 run의 기본 동작을 정화로 바꾸거나 전역 os.environ에서
변수를 제거하지 않는다. pre-push 전용 자식 환경만 변경하며 UNIT-006으로 다른 경로를 검증한다.

### D4. 회귀 테스트의 실제 환경 주입 방식

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| 환경 map 제거만 단위 검증 | 빠름 | 실제 ProcessBuilder/Python 상속과 저장소 불변을 증명하지 못함 |
| 하위 JVM probe로 실제 command 호출 | 실제 환경 상속, 빠른 변수별 matrix | test classpath 및 probe 연결이 필요하며 구현 복사본은 거짓 통과 위험 |
| 통합 테스트가 독립 clone에서 기존 Gradle 테스트 실행 | 실제 14개와 다단계 상속을 그대로 검증, production seam 불필요 | 느림, 미커밋 수정 overlay 및 결과 XML 확인 필요 |
| 실제 작업 저장소의 GIT_DIR로 재현 | 사용하지 않음 | 실패 시 실제 저장소 손상. 인계에서 금지 |

승인 결정: 가벼운 상시 테스트 + 일회성 증거.
상시는 RepoMapToolTest 내부 helper의 부모 환경 모사 주입으로 실제 git/Python을 실행하고,
Python unittest에서 실제 pre-push 하위 환경과 linked worktree 재탐색을 검증한다.
새 production seam이나 환경 제거 로직의 테스트 복사본을 만들지 않는다.
독립 clone의 기존 14개 실행, 각 격리 제거 mutant의 red/원복 green,
로컬 linked worktree push는 일회성 보고 증거로만 수행한다.
GitEnvironmentIsolationIntegrationTest 및 상시 clone/하위 Gradle은 만들지 않는다.
Gradle에서 미리 환경을 제거하여 RepoMap의 결함을 가리는 구성도 금지한다.

### D5. linked worktree 실제 push 인수

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| 독립 로컬 remote에 disposable linked worktree push | 실제 hook 발동, 외부 변경 없음 | 실제 개발 환경/인증까지 동일하지 않음 |
| 사람 승인 후 실제 PR branch push | 사용자가 겪은 경로의 최종 인수 | 외부 쓰기이며 승인, 전체 검사 환경, credential 필요 |
| --no-verify 또는 hook 직접 호출만 사용 | 빠름 | 실제 push 환경 전달을 검증하지 못하므로 완료 증거로 불가 |

승인 결정: 로컬 인수 후 별도 사람 승인된 PR push를 최종 확인한다.
push 직전/직후 실제 저장소의 is-bare-repository=false 및 공통 config hash,
worktree config가 있으면 그 hash, HEAD/index 불변을 읽기 전용으로 비교한다.
실제 push는 Git이 필요한 환경을 설정하게 두며 테스트 명령에 실제 GIT_DIR을 수동 주입하지 않는다.
실제 push에서 원격 ref 갱신은 의도된 결과이고 config/bare 변경은 실패다.
현재 계획 승인은 실제 push 승인으로 간주하지 않는다.

## 12. Planning evidence and handoff

- status: PASS (계획 수정 범위). 문서 상태는 APPROVED; 실제 테스트 통과 및 push 승인이 아님.
- issue_number: 386.
- task_id: TASK.md의 #386 / fix/gh-386-test-git-env-isolation.
- design_id: 해당 없음. 테스트 계획 식별자는 TEST-PLAN-GH-386-TEST-GIT-ENV.
- changed_files: 이 계획 파일 하나. 기존 TASK.md는 사용자 소유로 보존.
- executed_checks: Issue 읽기, 저장소/소스 읽기, Git 공식 문서 조회,
  git status --short, git branch --show-current, git rev-parse --is-bare-repository,
  git rev-parse --local-env-vars, git --version, git diff --numstat -- TASK.md,
  git ls-files, git diff --check, 신규 계획 파일의 git diff --no-index --check.
- executed_scaffold: env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-plan --id TEST-PLAN-GH-386-TEST-GIT-ENV.
- executed_issue_read: env -u SSL_CERT_FILE -u SSL_CERT_DIR gh issue view 386 --json number,title,body,state --jq .body.
- passed_checks: 시작/조사/작성 후 is-bare-repository=false, branch/Issue/TASK 식별자 일치.
  TASK.md의 읽기 시점 원문과 작성 후 바이트 일치. 신규 계획 외 추가 변경 없음.
- document_check: git diff --check exit 0. 신규 파일을 /dev/null과 비교한
  git diff --no-index --check는 공백 오류 출력 없이 exit 1이며, no-index의 파일 차이
  상태로 기록한다. 이를 exit 0 성공으로 보고하지 않는다.
- failed_checks: 아직 실행한 행동 테스트 없음; 실패 없음으로 테스트 PASS를 의미하지 않음.
- blocked_checks: 현재 요청은 계획 수정만이므로 테스트, full harness, hook push 실행 안 함.
- assumptions: D1-D5 및 공통 검사·CI 등록은 사람 결정으로 승인됨. 승인 시각은 미제공이며 Issue 증거 링크는 §11에 기록.
- risks: 실제 hook별 export 목록, 플랫폼별 재현 및 full check 환경은 미검증.
- required_human_decisions: 실제 PR push의 별도 승인.
- revised_sections: 추가 승인으로 §2/§4/§9/§10/§11/§12를 보완. 공통 검사·CI 등록과 승인 근거 반영.
