# Test Report: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY

> Created at: `2026-11-25T07:22:21Z`
> GitHub Issue: `#390`
> DESIGN-ID: `D-6`
> Branch: `infra/gh-390-dev-coordinate-key-ssm`
> Baseline commit: `4905f890`
> Working tree: 위 commit 이후 승인된 구현·fixture 변경을 검증했다.

## 1. Executive summary

- Result: `PASS` — 승인된 로컬 fixture·전체 프로젝트 검사와 독립 정적 감사 통과.
- Tested scope: 실제 runtime, Terraform template/gzip, raw workflow shell, 실제 Compose config,
  합성 SSM과 효과 double. UNIT 5·INT 9 계약을 단위 8개·통합 13개 메서드로 검증했다.
  전체 단위 1,609·통합 902·아키텍처 23·소스 관례 3개, 실패·오류·skip 0.
- Unverified scope: GitHub Actions, 실제 AWS·실효 IAM/MFA/secret scope·EC2 root 소유권,
  실제 실행 중 컨테이너의 key 동일성·배포·앱 암복호화·migration·사람 plan.
- Release recommendation: 표적 통과를 실제 운영 인수나 보호 workflow 실행 승인으로 해석하지 않는다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle Java 21 toolchain; shell Java 25.0.3 |
| Spring Boot | 3.5.16 |
| Python | 3.14.10 |
| PyYAML | 6.0.3, 통합 fixture에서 실제 import |
| Docker Compose | 5.3.1, 실제 config/interpolation |
| Docker engine | 29.6.2, 전체 기존 Testcontainers 검증용 로컬 daemon |
| Terraform | 1.17.1, 통합 fixture는 backend 없는 console/templatefile/base64gzip |
| Test runner | JUnit 5 |
| Database | 전체 검사는 로컬 Testcontainers, 표적 fixture는 DB에 접속하지 않음 |

합성 byte 배열만 사용했고 실제 AWS 실행 파일·metadata·SSM·ECR·서버에 접근하지 않았다.
HOME/ROOT/STATE와 자식 프로세스는 sandbox로 격리한다.
부모 APP_IMAGE 및 좌표 값을 오염시켜도 실제 env/overlay 선택이 유지되는지 검증한다.

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests com.dnd.qello.infrastructure.PushSecretEncodingTest --rerun` | PASS, exit 0 | 8, failures/errors/skipped 0 | Gradle 13초, suite 0.378초 | 표적 실행 직후 JUnit XML |
| `./gradlew integrationTest --tests com.dnd.qello.infrastructure.PushSecretDeploymentIntegrationTest --rerun` | PASS, exit 0 | 13, failures/errors/skipped 0 | Gradle 2분 4초, suite 118.07초 | 표적 실행 직후 JUnit XML |
| `./harness pr-ready --project-tests` | PASS, exit 0 | 단위 1,609·통합 902·아키텍처 23·소스 관례 3, 실패·오류·skip 0 | Gradle 7분 32초 | 전체 실행 후 JUnit XML, Local PR readiness checks passed |
| `./harness check` | PASS, exit 0 | 정책 검사 | 미집계 | Harness checks passed |
| `npm run hooks:validate` | PASS, exit 0 | hook 계약 | 미집계 | Husky validation passed |
| `git diff --check` | PASS, exit 0 | 공백 검사 | 미집계 | 실제 exit 0 |

명령은 시스템 CA를 사용하도록 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 실행했다.
Gradle `--rerun`은 표적 task 캐시 우회이며 실패 자동 재시도가 아니다.
전체 Gradle check는 pr-ready 경로가 소유하며 별도 중복 check를 실행하지 않았다.
전체 합계에 표적 8/13을 다시 더하지 않는다.
전체 XML은 단위 211 suites, 통합 116 suites, 아키텍처 4 suites, 소스 관례 1 suite다.
단위/통합/아키텍처/소스 관례 합계는 2,537개다.

## 4. Scenario results

아래 ID 접두사는 `TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY`다.

| Scenario ID | Result | Primary owner | 관찰한 계약 |
| --- | --- | --- | --- |
| UNIT-001 | PASS | coordinateIdUtf8Boundary | ASCII/다중 byte 255 bytes 수용, 256 bytes 거부 |
| UNIT-002 | PASS | coordinateCanonicalBase64 | decoded 0/16/31/33/64, alphabet/padding/pad bits/공백·개행 오류 거부 |
| UNIT-003 | PASS | coordinateKeySeparation | push AES 및 fingerprint와 같은 material 각각 거부 |
| UNIT-004 | PASS | coordinateRequiredFields | 두 필드 각각 누락·빈 값·공백·NUL·비문자열 거부 |
| UNIT-005 | PASS | multilinePreservation | 좌표 중복 할당/CRLF 갱신, 무관한 multiline·push/DB/auth 보존, 멱등성 |
| INT-001 | PASS | existingHostDeployment | push 5+좌표 2의 exact equality, DB/무관 설정·파일 mode 보존, app만 재생성 |
| INT-002 | PASS | prepublicationAndRenameFailures | 두 좌표 필드별 NotFound/Denied/응답·형식 오류 시 active/backup 보존·게시 차단 |
| INT-003 | PASS | bootAndWorkflowConverge / terraformGzipAndBothDeliveryPaths | 부팅 map·raw/렌더/전달 AST·16 KiB gzip·실제 Compose 두 경로 일치 |
| INT-004 | PASS | wholeShellDeliveryAndInvalidPrefix / legacyRollbackScenario | 실제 workflow image_sha rollback, 보관 세트 exact equality·secret 조회 증가 0, 미보관 이미지 실패·legacy overlay 부재 복원 |
| INT-005 | PASS | prepublicationAndRenameFailures / healthFailureAndManualRollback / sameImageRollbackAndInterruptedRecovery | rename/게시 검증/health 실패와 pending/previous 복구 경계 |
| INT-006 | PASS | validationAndComposeRoundTrip / previousSuccessfulPairLifecycle | 좌표만 변경해 정상 갱신, push·무관 값 보존, 중복 없음·재실행 멱등성 |
| INT-007 | PASS | diagnosticAndPayloadRedaction 및 공통 safe_output/safe_files | 좌표 ID/key·기존 credential sentinel이 출력/payload/비허용 파일에 없음 |
| INT-008 | PASS | 기존 lock/pending/same-SHA owners 및 오염 부모 환경 | 실제 lock 선점 충돌, pending 우선 복구·backup 보존, 부모 값 비상속 |
| INT-009 | PASS | validationAndComposeRoundTrip | 기존 push negative matrix를 실제 배포 분기에서도 거부·게시 차단 |

기존 unit 4개와 integration 13개를 삭제·skip·약화하지 않았다.
UNIT-005는 기존 multiline owner에 통합했고 통합 계약은 기존 13개 메서드에 확장했다.
원래 Created at과 D-5 source 이력은 보존하고 D-6 source 14개를 추가했다.
좌표 기대 이름/값은 외부 앱 매핑 계약에서 독립 정의해 production 목록과 함께 누락되는
zip fixture 회귀를 피했다. 기존 push map과 좌표 map도 분리했다.

단위 실행자는 모든 embedded Python 시나리오와 메모리 내 다섯 회귀 변형 검출을 보고했다.
이는 production 파일 mutation이나 별도 Gradle 실행 결과가 아니라 실행자의 보조 증거다.
리드는 표적 JUnit 두 명령을 직접 실행하고 XML을 확인했다.
독립 검증자 `st_01a21192`도 실제 diff와 두 XML을 읽고 actionable finding 없음으로 반환했다.

## 5. Failures and diagnostics

표적 JUnit 실패·오류·skip은 없다.
TFLint 초기 다운로드의 CA 경로 오류는 시스템 CA 재실행으로 해소했다.
Checkov 외부 guideline mapping 조회 인증서 경고는 남았으며 Terraform 정책 검사는 494/0으로 완료했다.
기존 승인 제외 9건 외 새 suppression은 없다.
LSP 서버 미설치 진단은 Java compile·실제 JUnit·workflow 정책·Terraform validate와 구분해 기록한다.
민감값이 포함될 수 있는 원격 오류 원문·전체 config·inspect 출력은 보고서에 첨부하지 않는다.

## 6. Potential issues

### Application code

실제 앱 암복호화는 #388/#389 범위다. 이번 검증은 환경값 전달·형식/목적 분리 경계이며,
애플리케이션의 성능·이전 ciphertext 읽기·Flyway 호환성을 증명하지 않는다.

### Infrastructure and resource limits

gzip 렌더 16 KiB 제한과 두 전달 경로는 fixture로 확인했다. IAM/MFA/Environment scope,
root 소유권·Agent·SSM/KMS 가용성·throughput은 D-6/#390의 사람 live 인수로 남는다.
current-only 키 유실은 DB 백업만으로 복구할 수 없으므로 보호 원본과 복구본이 필요하다.

### Database and migrations

표적 fixture는 DB 서비스 정의/볼륨 보존과 app만 재생성함을 검사하지만 DB·SQL을 실행하지 않는다.
#389 migration 이후 구 이미지 단독 rollback을 이 파일 복구 검증으로 정당화하지 않는다.

### Concurrency and idempotency

lock을 부모가 먼저 획득한 뒤 child를 실행해 충돌을 결정적으로 만든다.
좌표 A→B 및 동일 입력 반복으로 중복 할당·이전 값 보존 충돌을 검사한다.
새 wall-clock sleep/polling/retry는 없고 기존 invocation 대기는 가상 clock을 사용한다.

### Transactions and event ordering

env/overlay 두 rename은 단일 transaction이 아니다.
각 실패와 pending 복구를 실제 파일 경계에서 검사하지만 파일 복구가 이전 컨테이너 자동 복구를 뜻하지 않는다.
reader-first·기술적 배포/부팅 동결·실제 회전은 별도 승인 전까지 실행하지 않는다.

### External APIs

실제 Compose config를 사용하고 AWS·ECR·pull/up/health 효과만 double로 격리한다.
SSM JSON·stderr·종료 코드를 전달해 runtime 자체의 검증·갱신이 실행되게 했다.
실효 IAM과 실제 AWS 오류/지연까지 포괄하지 않으며 #390의 별도 인수 사항이다.

### Failure recovery and reconciliation

실제 workflow의 비공란 image_sha 분기를 통과해 보관 이미지와 env/overlay를 결합하고
secret get-parameter 증가 0을 확인한다. ECR 요청까지 0이라는 주장은 아니다.
원본/복구본/SSM/실행 중 컨테이너 fingerprint는 승인된 사람 경로로 실제 MATCH 증거를 남겨야 한다.

## 7. Regression and residual risk

기존 unit 4개와 integration 13개 회귀 보장은 유지했고 신규 계약도 같은 실제 owner에서 실행했다.
전체 pr-ready는 exit 0으로 완료했고 위 합계는 실행 후 XML에서 집계했다.
실제 key·Environment secret·계정/서버 식별자는 fixture와 보고서에 사용하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-390-TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY.md`
- Build report: `docs/reports/infrastructure/gh-390-D-6-build.md`
- Unit XML: `build/test-results/test/TEST-com.dnd.qello.infrastructure.PushSecretEncodingTest.xml`
- Integration XML: `build/test-results/integrationTest/TEST-com.dnd.qello.infrastructure.PushSecretDeploymentIntegrationTest.xml`
- Design/approval: D-6, Issue #390 comments `7153016641`, `7155537194`
- CI run / PR: 이번 세션에서 실행·생성하지 않음

## 9. Reviewer checklist

- [x] 실제 `.env` 값과 key·계정·서버 식별자 없이 작성
- [x] 표적 XML과 독립 정적 감사 결과를 실행자 보고와 구분
- [x] 미실행 live 범위와 잠재 문제를 #390/D-6 및 #388/#389 경계에 연결
- [x] 전체 프로젝트 결과 최종 집계
- [ ] 사람이 실제 plan·운영 인수를 완료함
