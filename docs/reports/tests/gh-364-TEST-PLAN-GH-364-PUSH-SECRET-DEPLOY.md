# Test Report: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY

> Created at: `2026-11-20T20:15:10+09:00`
> GitHub Issue: `#364`
> Branch: `infra/gh-364-test-server-push-secrets`
> Commit: `d9089022` (PR #367 리뷰 반영 기준)
> Working tree: 위 commit을 기준으로 한 미커밋 변경을 검증했다.
> Clean revalidation: `2026-11-20`, 승인 코멘트 `7039501730`에 따른 환경 의존 제거 후 새 실행 결과.
> PR review revalidation: `2026-11-20T16:18:42Z`, `t364-review.md`의 승인 네 항목에 대한 로컬 재검증.

## 1. Executive summary

- Result: `PASS` — PR #367 리뷰 반영 범위의 로컬 fixture·전체 프로젝트·정적 검증 통과.
- Tested scope: 단위 표적 4개·통합 표적 13개, 전체 단위 1,403개·통합 869개·아키텍처 23개. 실패·오류·skip 0.
- Unverified scope: 이번 변경의 GitHub Actions 실행, 실제 AWS/GCP, IAM 실효 권한, EC2 root 소유권, 실서버 배포·FCM·사람의 plan.
- Release recommendation: 오케스트레이터의 독립 검증으로 인계한다. 배포 승인이나 live 인수 완료를 뜻하지 않는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 프로젝트 Java 21 toolchain |
| Spring Boot | 3.5.16 |
| Python / YAML | Python 3.14.10 / PyYAML 6.0.3 |
| Compose | PATH의 docker compose plugin 5.3.1, 실제 config/interpolation |
| Terraform | 1.17.1, fixture 디렉터리의 console/templatefile/base64gzip만 사용 |
| Database | 로컬 Testcontainers PostGIS, dev/prod DB 접근 없음 |
| Test runner | JUnit 5 |

단위 테스트에는 Compose가 필요하지 않다. 실제 production Python 변환 함수를 재구현 없이
실행하기 위한 PATH의 python3만 사용한다. 통합은 optional override가 없으면 PATH의
docker compose plugin을 선택한다. 정상 사용자 Docker 설정에서 plugin을 탐색하고 실제 호스트
효과는 임시 HOME과 가짜 명령으로 격리한다. fixture는 config/version만 실제 호출하므로 daemon은
필요하지 않다. 전체 저장소의 기존 PostGIS 통합 테스트에는 로컬 daemon이 필요하다.
이번 명령에는 JAVA_HOME·QELLO_TEST_COMPOSE·DOCKER_HOST를 수동 설정하지 않았다.

## 3. 이전 로컬 실행 결과 (CI 설치 변경 당시 기록)

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 단위 표적 --rerun | PASS, exit 0 | 4 / 실패 0 / skip 0 | 2s | 단위 표적 실행 직후 JUnit XML |
| 통합 표적 --rerun | PASS, exit 0 | 9 / 실패 0 / skip 0 | 39s | 통합 표적 실행 직후 JUnit XML |
| ./gradlew check --rerun | PASS, exit 0 | 단위 1,403 / 통합 865 / 아키텍처 23; 실패·오류·skip 0 | 3m 2s | build/test-results의 구조화된 testsuite 전체 집계 |
| ./harness pr-ready --project-tests | PASS, exit 0 | 위 강제 재실행 결과에 대한 readiness·check 통과 | Gradle 1s | Local PR readiness checks passed |
| ./harness check | PASS, exit 0 | JUnit 정책 347파일 등 정책 검사 | 미집계 | Harness checks passed |
| git diff --check | PASS, exit 0 | 공백 오류 없음 | 미집계 | 실제 종료 코드 0 |

실행 명령은 다음과 같다. 마지막 pr-ready의 캐시 결과를 테스트 재실행 증거로 사용하지 않으며,
직전 `check --rerun`이 실제 전체 재실행 증거다. 수동 환경변수에 의존한 이전 실행 결과는 대체했다.

```bash
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew test --tests 'com.dnd.qello.infrastructure.PushSecretEncodingTest' --rerun
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew integrationTest --tests 'com.dnd.qello.infrastructure.PushSecretDeploymentIntegrationTest' --rerun
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew check --rerun
GIT_MASTER=1 env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
GIT_MASTER=1 git diff --check
```

전체 XML은 단위 202개 suite, 통합 112개 suite, 아키텍처 4개 suite다.
fixture 13개는 전체 수치에 포함되므로 별도로 더하지 않는다.

### PR #367 리뷰 반영 재검증

승인은 `TASK.md`와 오케스트레이터의 `t364-review.md`에 @Byuntil, 2026-11-21로 기록되어 있다.
테스트 경로 배포 제외, 동일 SSM 명령 조회 재시도·UNKNOWN, 잔여 stage 정리, 전체 deploy Bash 실행 및
INT-010~013만 반영했다. 위 이전 실행 수치는 보존하고 이번 결과와 구분한다.

| Command / suite | Result | Tests / evidence |
| --- | --- | --- |
| 지정 두 클래스 Gradle 표적 실행 | PASS, exit 0, 1m 24s | 단위 4개·통합 13개, 실패·오류·skip 0; 각 XML 1 suite |
| ./harness pr-ready --project-tests | PASS, exit 0, Gradle 3m 24s | 단위 1,403개/202 suites·통합 869개/112 suites·아키텍처 23개/4 suites, 실패·오류·skip 0 |
| ./harness check | PASS, exit 0 | JUnit 정책 347파일, workflow 10파일 등 통과 |
| python3 scripts/validate-workflows.py | PASS, exit 0 | workflow 정책 통과 |
| terraform fmt -check -recursive infra | PASS, exit 0 | 형식 변경 없음 |
| terraform -chdir=infra/environments/dev/test-server validate | PASS, exit 0 | configuration valid |
| 실제 pre-commit Java gate | PASS, exit 0, Gradle 18s | javaConventionStagedCheck 및 checkstyleStagedJava 실행; XML에 변경 통합 Java 1파일, 오류 0; 이후 unstage exit 0 |

```bash
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew test --tests 'com.dnd.qello.infrastructure.PushSecretEncodingTest' integrationTest --tests 'com.dnd.qello.infrastructure.PushSecretDeploymentIntegrationTest' --rerun
GIT_MASTER=1 env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
python3 scripts/validate-workflows.py
env -u SSL_CERT_FILE -u SSL_CERT_DIR terraform fmt -check -recursive infra
env -u SSL_CERT_FILE -u SSL_CERT_DIR terraform -chdir=infra/environments/dev/test-server validate
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR python3 scripts/run-hook.py pre-commit
```

통합 Java 파일만 임시 stage한 상태에서 실제 hook을 실행했다. manifest를 전달하지 않는
checkstyleStagedJava 단독 호출의 NO-SOURCE를 통과 근거로 사용하지 않았다.
단위·통합 표적은 모두 실제 실행됐고, 이후 pr-ready도 전체 단위·통합·아키텍처를 실행했다.
fixture 17개는 전체 수치에 포함되므로 더하지 않는다. 고정 sleep 없이 가상 clock으로
조회 오류·timeout·백오프·전체 deadline을 검증했다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | PushSecretEncodingTest.credentialRoundTrip | 순수 JSON 정규화·물리 한 줄 |
| UNIT-002 | PASS | PushSecretEncodingTest.literalDotenvEncoding | literal dollar·따옴표·빈 문자열 |
| UNIT-003 | PASS | PushSecretEncodingTest.escapedDotenvEncoding | backslash JSON escape |
| UNIT-004 | PASS | PushSecretEncodingTest.multilinePreservation | CRLF·중복 제거·멱등성 |
| INT-001 | PASS | PushSecretDeploymentIntegrationTest.existingHostDeployment | DB·설정 보존, 파일 mode, app만 재생성 |
| INT-002 | PASS | PushSecretDeploymentIntegrationTest.prepublicationAndRenameFailures | 조회·파싱·Compose·login/pull/up·rename 실패와 잠금 |
| INT-003 | PASS | PushSecretDeploymentIntegrationTest.healthFailureAndManualRollback | health 실패와 SSM 재조회 없는 복구 |
| INT-004 | PASS | PushSecretDeploymentIntegrationTest.previousSuccessfulPairLifecycle | 연속 성공·실패·재실행과 단일 백업 |
| INT-005 | PASS | PushSecretDeploymentIntegrationTest.bootAndWorkflowConverge | 실제 부팅과 workflow 전달 경로 |
| INT-006 | PASS | PushSecretDeploymentIntegrationTest.diagnosticAndPayloadRedaction | 출력·진단·SSM payload의 가짜 누출 sentinel 부재 |
| INT-007 | PASS | PushSecretDeploymentIntegrationTest.sameImageRollbackAndInterruptedRecovery | override 부재·같은 SHA·pending 우선·복구 실패 |
| INT-008 | PASS | PushSecretDeploymentIntegrationTest.terraformGzipAndBothDeliveryPaths | Terraform 렌더·gzip 크기·전달된 runtime AST 일치 |
| INT-009 | PASS | PushSecretDeploymentIntegrationTest.validationAndComposeRoundTrip | 단위에서 옮긴 입력 거부·실제 Compose 파싱·부모 환경 오염 |
| INT-010 | PASS | PushSecretDeploymentIntegrationTest.wholeShellDeliveryAndInvalidPrefix | 실제 Bash의 유효 입력 전달·잘못된 prefix 전송 전 거부 |
| INT-011 | PASS | PushSecretDeploymentIntegrationTest.transientInvocationFailuresThenSuccess | InvocationDoesNotExist·timeout 뒤 같은 명령 재조회, 전송 1회 |
| INT-012 | PASS | PushSecretDeploymentIntegrationTest.unknownInvocationHasBoundedDeadline | 진행 중·조회 오류·timeout을 가상 deadline에서 UNKNOWN으로 구분 |
| INT-013 | PASS | PushSecretDeploymentIntegrationTest.staleStagesPreserveRecoveryAndLock | lock 전 정리 금지, lock 후 잔여 stage만 정리, 복구 JSON·lock 보존 |

Scenario ID의 공통 접두사는 `TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-`다.

## 5. Failures and diagnostics

이전 환경 의존 제거 단계에서 독립 검증이 발견한 필수 환경변수 참조를 제거했다. 당시 첫 깨끗한 통합 표적 실행은 9개가
`RuntimeError: Compose unavailable: QELLO_TEST_COMPOSE or PATH docker compose must run`으로 실패했다.
sandbox HOME 때문에 정상 설치된 plugin을 찾지 못한 원인이었다. Docker의 기본 사용자 설정 경로를
명령에 보존한 뒤 같은 깨끗한 명령을 강제 재실행해 9개 모두 통과했다. skip·suppress는 추가하지 않았다.
위 로컬 환경 의존 제거 단계의 최종 명령 실패는 없다. 당시 CI 미실행 기록은 §7에 보존한다.
이번 PR 리뷰 반영의 대상·전체 테스트와 필수 정적 검사는 모두 첫 실행에 통과했고 실패·skip은 없었다.

## 6. Potential issues

### Application code

- 실제 production helper를 추가하지 않고 배포 코드와 workflow의 전체 Bash run을 실행했다.
  fixture 성공이 #365 애플리케이션의 FCM 발송 성공이나 provider 활성화를 증명하지 않는다.

### Infrastructure and resource limits

- gzip payload의 16 KiB 상한은 가짜 메타데이터 렌더로 확인했다. 실제 값과 IAM·SSM Agent 영향은
  #364의 사람 plan·live 인수 대상이다. root 소유권 호출은 대체했고 실제 mode만 검증했다.

### Database and migrations

- D-5 변경에는 DB schema 수정이 없으며 전체 통합 회귀는 로컬 PostGIS에서 통과했다.
  실제 데이터 볼륨 보존·호스트 DB 상태는 fixture로 검증하지 않았다.

### Concurrency and idempotency

- flock을 선점한 뒤 경쟁 배포를 실행해 즉시 거부와 기존 파일 보존을 확인했다.
  고정 sleep이나 polling으로 테스트의 성공을 만들지 않았다. 같은 구성 재실행은 백업을 교체하지 않는다.

### Transactions and event ordering

- 파일 둘의 게시는 단일 filesystem transaction이 아니다. 각 rename 실패와 복원 실패에서
  active 조합·성공 백업·pending 복구 정보를 확인했다. 실제 호스트 강제 종료는 미실행이다.

### External APIs

- AWS CLI·Docker daemon 작업·health는 경계 double로 대체하고 Compose 해석만 실제 바이너리로 실행했다.
  실제 AWS/GCP 호출과 실제 credential은 사용하지 않았다. 통합에는 사용 가능한 Compose·Python/YAML이 필요하다.
  Compose가 없으면 이유를 남기고 실패하며, 단위에는 Compose 탐색·실행 자체가 없다.
- SSM 조회는 실제 workflow Python과 AWS 프로세스 double을 연결하고 clock·timeout 효과만 치환했다.
  한 번 보낸 command ID를 유지하며 백오프 5·10·20·30초와 남은 한도를 적용한다.
  UNKNOWN은 원격 작업 실패를 뜻하지 않으며 실제 호스트의 종료 상태는 별도 확인해야 한다.

### Failure recovery and reconciliation

- 이전 override가 없던 상태와 같은 SHA의 secret 변경, health 실패, pending 우선 복구를 검증했다.
  실패한 배포가 직전 성공 백업을 덮어쓰지 않으며 수동 복구는 SSM을 재조회하지 않는다.
- 강제 종료를 가정한 잔여 stage 디렉터리는 다음 lock 획득 뒤 제거한다.
  pending이 있어 새 배포가 거부되는 경로에서도 pending·previous 원문과 lock inode를 보존한다.

## 7. Regression and residual risk

- 로컬 fixture 검증과 실제 클라우드 인수는 분리한다. 잔여 인수 항목은 #364에서,
  애플리케이션 적용 순서는 #365, provider/worker 활성화는 #366에서 추적한다. 새 Issue는 생성하지 않았다.

### CI 도구 설치 변경 당시 기록 (2026-11-20)

- 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7039900124
- Harness Policy의 `test` job과 CI Benchmark의 `measure` job에 동일한 세 step을 추가했다.
  `Set up Python`: actions/setup-python@v6, Python `"3.13"`.
  `Install PyYAML`: `python -m pip install PyYAML==6.0.3`.
  `Set up Terraform`: hashicorp/setup-terraform@v3, Terraform `"1.17.1"`, `terraform_wrapper: false`.
- PyPI의 6.0.3 배포 metadata에서 Python 3.13 지원을 확인했다. wrapper는 console stdout의 JSON 파싱을 보존하기 위해 끈다.
- 다른 job·트리거·permissions·concurrency·Environment·apply 게이트는 변경하지 않았다.
- 이번에는 Gradle 테스트를 재실행하지 않는다. 위 로컬 재실행 수치는 이전 검증 증거이며 CI 성공의 근거로 사용하지 않는다.
- 실제 CI 실행: push 전이라 미실행(`BLOCKED`). 로컬 정적 검증 결과는 최종 보고에 구분해 기록한다.
- 이번 로컬 정적 검증: `./harness check`, `python3 scripts/validate-workflows.py`,
  두 workflow의 `yaml.safe_load` 파싱, `git diff --check` 모두 exit 0.
  기존 YAML snapshot과 비교해 대상 job의 세 step 외 구조 변경이 없고 두 설치 설정이 동일함도 확인했다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-364-TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY.md` (APPROVED)
- Design: `docs/reports/infrastructure/gh-364-D-5.md`
- CI run: 이번 로컬 변경은 push하지 않았으며 GitHub Actions를 실행하지 않음. 이전 CI 설치 단계의 미실행 기록은 §7 참조.
- Related ADR: D-5 및 ADR-0008
- PR: #367 (기존 PR 읽기만 수행, 생성·push·댓글 없음)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잔여 인수 항목의 기존 GitHub Issue가 명시됨
- PR #367의 승인된 리뷰 세 건과 추가 경로 제외만 반영했다. 독립 검증은 오케스트레이터가 수행한다.
