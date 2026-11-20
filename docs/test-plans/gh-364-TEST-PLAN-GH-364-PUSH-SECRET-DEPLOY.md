# Test Plan: TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY

> Created at: `2026-11-20T18:46:19+09:00`
> GitHub Issue: `#364`
> Status: APPROVED — @Byuntil, 2026-11-20.
> Approval evidence: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7037762767
> Environment correction approval: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7039501730
> CI dependency approval: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7039900124

## 1. Objective

실제 부팅·배포 코드가 가짜 SSM 입력을 손상 없이 app 환경에 전달하고, 부분 실패 시
기존 파일·DB·직전 성공 백업을 보존하며 비밀이나 식별자를 출력하지 않는지 검증한다.
D-5 구현 승인과 별도로 위 코멘트에서 이 테스트 계획의 승인을 확인했다.

## 2. Scope

### Included

- 승인된 user_data 템플릿과 deploy workflow의 실제 Python·shell 경로를 실행 대상으로 한다.
- JSON/dotenv round-trip, Compose 파일·환경 우선순위, staging/게시/복구, H-4a 백업 수명주기.
- 신규 다섯 값의 필수·형식 계약과 오류 출력 비노출.

### Excluded

- 실제 AWS/GCP 호출·plan/apply·서버 배포, 서비스 계정 key 발급.
- 애플리케이션 코드·DB schema·provider/worker 활성화.
- 실제 IAM 정책 효과와 SSM Agent Run Command 검증은 별도 승인된 live 환경의 인수 검증이다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #364 | 기존 2개와 신규 5개를 보존·주입하고 기존 인스턴스를 교체하지 않음 |
| D-5 §4·5·10 | 정확한 Compose 입력, root-only staging, 오류 비노출, 실패 복구 |
| D-5 H-4a | 직전 성공 env/override 1세트만 보관, 다음 성공 배포에서 교체, 기간 보존 없음 |
| 실행 코드 | user_data.sh.tftpl과 deploy-test-server.yml의 실제 생성·실행 경로를 사용하고 로직을 테스트에 복제하지 않음 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| dotenv 보간 또는 JSON escape 손상 | FCM 인증 실패·다른 값 주입 | 중간 | P0 | effective config의 값과 원본 JSON을 메모리에서 비교 |
| 실패 중 env/override 혼합 | 기동 실패·복구 불가능 | 중간 | P0 | 각 게시 단계 실패 주입 후 이전 일치 세트 보존 |
| shell APP_IMAGE 우선 | 다른 버전 배포·롤백 실패 | 중간 | P0 | 오염된 부모 환경에서도 요청/복구 이미지가 선택됨 |
| 오류·진단 로그 유출 | 비밀·식별자 노출 | 중간 | P0 | 가짜 누출 sentinel이 모든 출력·허용되지 않은 파일에서 없음 |
| 백업 교체 순서 오류 | 마지막 정상 구성 소실 | 중간 | P0 | 성공·실패·재실행 순서별 보관 세트 검증 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-001 | 특수문자·PEM 개행이 있는 가짜 JSON | 실제 순수 정규화 함수 실행 | JSON 필드 보존과 물리 한 줄 계약 | P0 | 테스트 실행 에이전트 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-002 | dollar·따옴표·빈 문자열 | 순수 dotenv 인코딩 | literal 표현 보존 | P0 | 테스트 실행 에이전트 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-003 | 닫는 따옴표 앞 backslash | 순수 dotenv 인코딩 | JSON escape 표현의 값 보존 | P0 | 테스트 실행 에이전트 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-UNIT-004 | CRLF·multiline·중복 할당 | 순수 env 갱신 | 무관한 설정 보존·중복 제거·멱등성 | P0 | 테스트 실행 에이전트 |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-001 | 실제 배포 코드·가짜 aws·실제 Compose config | 가짜 기존 env와 override 없는 디렉터리 | 가짜 5개 값 주입과 성공 배포 | 기존 DB·다른 설정 보존, 5개 매핑·0600/0700·app만 갱신 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-002 | 실제 코드·실패 주입 프로세스 | 기존 env/override·직전 성공 백업 세트 | SSM 조회/JSON/Compose 검사/각 rename 실패 | 기존 일치 세트 유지 또는 복구, 실패 성공으로 숨기지 않음 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-003 | 실제 코드·가짜 health 실패 | 기존 성공 구성과 백업 | 게시 후 app health 실패·수동 복구 | 직전 성공 백업 보존, 최신 SSM을 다시 읽지 않고 이전 이미지·파일로 복구 가능 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-004 | 실제 코드·가짜 성공/실패 순서 | 성공 A→성공 B→실패 C→성공 D | 실제 배포 경로 반복 | H-4a대로 이전 성공 1세트만 교체, 실패가 보관 세트를 덮어쓰지 않음 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-005 | 부팅 템플릿·기존 호스트 배포 코드 | 동일한 가짜 SSM 응답 | 두 경로를 각각 실행 | 같은 5개 effective 값, 설정 원문·누출 sentinel 미출력 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-006 | 실제 오류 처리·가짜 AWS stderr | 식별자·credential을 뜻하는 고정 가짜 sentinel | 접근 거부·파싱·Compose 실패 | stdout/stderr·진단 파일·SSM payload에 sentinel 없음, 단계/코드만 출력 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-007 | 실제 복구 코드·pending·동일 SHA | 같은 이미지에서 secret만 바뀐 이전 성공 세트 | 수동 롤백과 복구 실패 주입 | pending 우선·이전 성공 세트 선택, SSM 재조회 없음, 실패 시 복구 정보 보존 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-008 | Terraform 렌더·gzip·신규/기존 호스트 전달 | 비밀 없는 메타데이터와 가짜 SSM 응답 | 렌더링 후 gzip payload 확인 및 두 전달 경로 실행 | EC2 한도 이하, 복원 정의와 effective 5개 값 일치 | 임시 디렉터리 정리 |
| TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY-INT-009 | 기존 단위의 검증·Compose 경계 | 잘못된 키/JSON, 부모 환경 오염, 특수문자 JSON | 입력 거부와 실제 Compose config 실행 | 게시 전 거부·기존 파일 보존·요청 이미지·JSON 필드 일치 | 임시 디렉터리 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- DB에 연결하지 않는다. 파일 게시를 경계로 부분 실패·일치 세트 복구를 검증한다.
- app만 재생성하고 PostGIS 명령·볼륨 변경이 발생하지 않는지 실제 명령 기록으로 확인한다.

### Concurrency and idempotency

- 같은 가짜 입력의 반복 배포에서 중복 env 항목·추가 백업 세트가 쌓이지 않아야 한다.
- 동시 실행 제어가 구현되어 있으면 잠금 획득·해제 이벤트를 구독한 뒤 경쟁 실행한다.
  고정 sleep이나 polling으로 성공을 만들지 않는다.

### External APIs

- aws 실행 파일 double은 실제 argv/exit code/stdout/stderr 계약을 보존하며 네트워크 호출은 차단한다.
- Docker Compose의 config/interpolation은 실제 설치된 Compose를 사용한다.
  pull/up/health의 효과만 경계에서 대체해 파일·매핑 통합 자체는 실패할 수 있어야 한다.

### Failure recovery and reconciliation

- 모든 stage와 게시 중간, health 실패를 구분해 기존 active 파일·백업·종료 코드를 검사한다.
- 롤백은 부모 환경을 상속하지 않고 이전 env/override와 이전 image를 사용한다.

## 8. Test data and isolation

- Fixtures: 값은 테스트에서 생성하는 비밀이 아닌 고정 데이터만 사용한다. 실제 credential·주소·ARN은 사용하지 않는다.
- Database isolation: DB 없음. 각 시나리오는 독립 임시 디렉터리와 자식 프로세스 환경을 사용한다.
- Clock/randomness: 시각·난수·sleep에 의존하지 않는다. 파일 단계 완료 이벤트를 등록한 뒤 동작을 촉발한다.
- External API doubles: 실제 실행 경계만 치환하며 파일 게시·env 인코딩·Compose config는 실제 코드로 수행한다.
- Cleanup: 생성한 임시 자원만 정리하고 저장소·사용자 파일은 건드리지 않는다.
- 단위 Dependencies: Java 21/JUnit 5와 PATH의 python3만 필요하다. Docker·Compose·추가 환경변수는 필요하지 않다.
  Java만의 변환 재구현은 production Python 코드의 회귀를 잡지 못하므로, 실제 템플릿 함수 정의를
  Python 표준 라이브러리로 직접 실행한다. 이 interpreter 실행만 허용하며 다른 외부 프로그램은 호출하지 않는다.
  CI Ubuntu runner와 로컬에 python3가 있다는 전제다.
- 통합 Dependencies: Python 3/PyYAML, Terraform, Compose. `QELLO_TEST_COMPOSE`가 있으면 해당 standalone을,
  없으면 PATH의 `docker compose` plugin을 사용한다. 실행 불가 시 이유를 남기고 실패하며 skip하지 않는다.
  plugin 탐색에는 정상 사용자 HOME의 Docker 설정 경로를 사용하고, 호스트 효과 double의 HOME은 임시 디렉터리로 격리한다.
  fixture는 version/config만 실제 실행하고 login/pull/up/health는 double로 대체하므로 daemon이 필요 없다.
  전체 저장소 `check`의 기존 PostGIS 통합 테스트에는 별도로 로컬 Docker daemon이 필요하다.
- CI 설치: Harness Policy의 `test` job과 CI Benchmark의 `measure` job에서 같은 순서로
  `actions/setup-python@v6`(Python `"3.13"`), `python -m pip install PyYAML==6.0.3`,
  `hashicorp/setup-terraform@v3`(Terraform `"1.17.1"`, `terraform_wrapper: false`)를 실행한다.
  PyYAML 6.0.3은 PyPI의 Python 3.13 classifier와 배포 wheel로 지원을 확인했다.
  Terraform console stdout을 그대로 JSON 파싱하므로 wrapper는 사용하지 않는다.
- 로컬 pre-push의 `./gradlew check`에는 PATH의 python3, PyYAML, Terraform >= 1.11,
  docker compose plugin과 전체 check용 Docker daemon이 필요하다. CI의 설치 step이 로컬 도구까지 설치하지는 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 별도 테스트 실행 에이전트 | src/test/java/com/dnd/qello/infrastructure/PushSecretEncodingTest.java (승인 후 신규) | UNIT-001~004 | JUnit 5 표적 실행 |
| 2 | 별도 테스트 실행 에이전트 | src/integrationTest/java/com/dnd/qello/infrastructure/PushSecretDeploymentIntegrationTest.java (승인 후 신규) | INT-001~009 | JUnit 5 표적 실행, 실제 subprocess/Compose config |

위 두 신규 테스트 경로의 작성·실행은 위 코멘트에서 승인되었다.
production helper를 추가하지 않는다.
실제 코드 추출은 YAML/AST의 machine-consumed 경계를 이용하고 문구 pin·소스 grep 테스트를 만들지 않는다.
모든 프로세스는 bounded timeout으로 종료하며 명확한 완료 이벤트를 사용한다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성

실행 결과: `docs/reports/tests/gh-364-TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY.md`.
로컬 fixture와 전체 회귀 검증의 통과이며 live 인수나 독립 승인을 뜻하지 않는다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: APPROVED — 위 GitHub 코멘트, D-5 구현 승인과 별도
- Approved at: 2026-11-20T10:56:45Z
- 환경 의존 제거·시나리오 재분류 승인: @Byuntil, 2026-11-20, 위 추가 코멘트 7039501730.
- CI 도구 설치 승인: @Byuntil, 2026-11-20, 위 추가 코멘트 7039900124.

테스트 작성·실행 결과는 별도 테스트 보고서에 기록한다. 이 승인은 live 검증 승인이 아니다.
