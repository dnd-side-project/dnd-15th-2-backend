# Test Plan: TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY

> Created at: `2026-11-25T04:43:43Z`
> GitHub Issue: `#390`
> DESIGN-ID: `D-6`
> Status: `APPROVED` — 2026-11-25, @Byuntil (D1~D5 추천안 승인). 테스트 코드 작성·실행 승인.
> Design approval: @Byuntil, 2026-11-25, Issue #390 comment 7153016641.
> H-7은 두 fixture 파일 범위만 승인했다. 이 테스트 계획의 승인은 별도다.

## 1. Objective

좌표 current key ID와 key가 가짜 SSM에서 기존 배포 runtime을 거쳐 `.env`와 app Compose
environment까지 정확히 전달되는지 검증한다. 기존 push 5개·DB/auth 설정을 보존하고,
새 필수값이 누락·손상되면 게시 전에 실패하며, rollback은 현재 SSM이 아니라 요청 이미지에
맞는 보관 세트를 사용해야 한다. 비밀값이 payload·출력·비허용 파일로 새지 않는 것도 계약이다.

실제 `user_data.sh.tftpl`의 Python runtime과 `deploy-test-server.yml`의 deploy step을
실행 대상으로 삼고 JUnit 5가 격리된 Python subprocess를 구동하는 D-5 구조를 재사용한다.
현재 코드는 push-only이며 아래 좌표 동작은 승인된 D-6 구현의 목표이지 이미 통과한 결과가 아니다.

## 2. Scope

### Included

- 기존 `PushSecretEncodingTest`와 `PushSecretDeploymentIntegrationTest` 확장.
- 새 두 파라미터 조회, 필수값·ID·Base64·목적별 key 분리 검증.
- 기존 push 5개 정규화/검증 보존과 DB/auth·무관한 env·DB 서비스 정의 보존.
- `.env`와 `compose.push.yaml`의 같은 세트 게시, 실제 Compose config의 새 변수 값 일치.
- 기존 호스트 raw runtime 추출과 새 호스트 Terraform template/gzip 렌더 경로의 일치.
- `image_sha` 공란의 정상 workflow와 비공란의 rollback workflow를 모두 실제 deploy shell로 검증.
- 실패·반복·pending/previous·잠금·환경 오염·출력 비노출의 좌표 확장 회귀.

### Excluded

- 이번 계획 작성 중 모든 테스트/production/스크립트/workflow 수정과 테스트 실행.
- 승인 후에도 실제 AWS·SSM·ECR·서버·실제 key 접근, Terraform init/plan/apply와 State 조작은 fixture 범위 밖.
- 신규 인스턴스의 dnf·볼륨 포맷·mount·Docker 설치 등 shell bootstrap 전체 실행.
- 애플리케이션 암복호화·매칭·Flyway migration·좌표 DB 저장 검증(#388), previous 슬롯과 실제 회전.
- IAM 실효성·MFA·Environment scope·승인 게이트 검증과 실제 fingerprint 대조.
  D-6 §7의 사람 로컬 MFA/Run Command 절차로 수행하며 fixture 성공으로 대체하지 않는다.
- Terraform version validation/prevent_destroy/9개 IAM 목록은 인프라 실행·독립 정적 검증 경계다.
  이 두 Java fixture에 Terraform source-grep 테스트를 추가하지 않는다.
- 새로운 helper 파일, 테스트 전용 production API, 의존성/CI 설치 단계 변경, 별도 테스트 클래스 분리.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| Issue #390 및 TASK | 좌표 current 2개 전달, 기존 값 보존, #389 선행 |
| D-6 §2·4·5 | current-only, push와 독립 key, strict canonical Base64 32 bytes, ID UTF-8 255 bytes 이하, 비공백·NUL 없음 |
| D-6 §4·7 및 H-2 | 부팅 map과 공용 runtime 모두 갱신; deploy workflow는 수정하지 않음 |
| D-6 §7/H-6/SEC-D6-06 | 초기 image_sha 공란, 실제 fingerprint는 사람이 별도 검증; fixture는 live 증거 아님 |
| D-6 §9/H-7 | 아래 두 테스트 파일만 변경; 계획의 별도 승인 필요 |
| PR #389 application-dev.yml | `QELLO_COORDINATE_CURRENT_KEY_ID`, `QELLO_COORDINATE_CURRENT_KEY_BASE64` 필수, previous는 빈 값 허용 |
| D-5 테스트 계획 | 기존 UNIT-001~004와 INT-001~013 보존. 승인·실행 이력을 D-6으로 전용하지 않음 |
| 실제 runtime·workflow | BEGIN/END QELLO RUNTIME 추출, 원시 Python과 Terraform 렌더본 일치, REQUESTED_IMAGE_SHA에 의한 rollback 분기 |

좌표 기대 환경변수 두 이름과 논리 suffix `coordinate-current-key-id`, `coordinate-current-key`는
외부 앱/SSM 매핑 계약이다. 입력 fixture와 기대값은 이 계약에서 독립적으로 정의한다.
현재 production `PUSH_KEYS`/`SUFFIXES`를 그대로 기대 목록으로 삼아 두 변수가 빠져도 통과하게 만들지 않는다.
기존 push의 생산 코드 검증·JSON 직렬화는 실제 구현을 실행하고 테스트에 복제하지 않는다.

### 기존 테스트 소유 경계

| 현재 테스트 | D-6 확장 지점 |
| --- | --- |
| UNIT credentialRoundTrip / literalDotenvEncoding / escapedDotenvEncoding / multilinePreservation | 기존 push·encoding 보장을 유지하고 좌표 입력/중복 갱신은 필요한 행만 보강 |
| INT existingHostDeployment / validationAndComposeRoundTrip | 새 두 변수 전달·보존 및 필수값 실패 |
| INT bootAndWorkflowConverge / terraformGzipAndBothDeliveryPaths | 부팅 map·raw 추출·AST·gzip·effective 좌표값 |
| INT wholeShellDeliveryAndInvalidPrefix | 정상 dispatch와 image_sha rollback의 실제 workflow 연결 |
| INT healthFailureAndManualRollback / sameImageRollbackAndInterruptedRecovery | 좌표가 있는 보관 세트, legacy 세트, pending·일치 이미지 선택 |
| INT previousSuccessfulPairLifecycle / prepublicationAndRenameFailures | 두 번째 좌표 갱신, 멱등성, 게시 실패와 lock |
| INT diagnosticAndPayloadRedaction | 좌표 ID/key 합성 sentinel까지 누출 검사 확장 |
| INT transientInvocationFailuresThenSuccess / unknownInvocationHasBoundedDeadline / staleStagesPreserveRecoveryAndLock | 기존 동작 그대로 회귀 실행; 별도 중복 케이스를 복제하지 않음 |

아래 5개 단위·9개 통합 ID는 커버할 계약 수이며 새 테스트 메서드 14개를 강제하지 않는다.
기존 primary owner에 입력 행·assertion을 추가할 수 있으면 먼저 확장한다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| runtime 목록만 늘리고 부팅 map/Compose를 빠뜨림 | 새 호스트 또는 기존 호스트 앱 기동 실패 | 중간 | P0 | 두 실제 전달 경로에서 새 두 값의 exact equality |
| zip 기반 fixture가 누락을 함께 숨김 | 필수 key 미주입인데 테스트 녹색 | 중간 | P0 | 앱 계약에 따른 독립 기대 이름과 값, 두 항목 각각 확인 |
| 키 ID 문자 수와 byte 수 혼동·Base64 느슨한 검증 | 앱 기동 실패·잘못된 key 주입 | 중간 | P0 | UTF-8 255/256 bytes와 canonical/길이 경계 |
| push 검증 우회 또는 key 재사용 | 기존 기능 손상·목적별 격리 상실 | 중간 | P0 | 기존 push negative matrix 보존, 두 push key 각각 중복 거부 |
| 새 관리 key가 이전 값 보존 검사에 포함 | 두 번째 정상 배포 실패 | 중간 | P0 | 서로 다른 좌표 세트로 갱신 후 무관한 값 보존 |
| image_sha를 일반 이미지 선택으로 오인 | rollback에서 SSM 최신값 주입·복구 손상 | 중간 | P0 | 실제 shell rollback, GetParameter 0건 증가와 보관 세트 exact equality |
| raw 구간에 Terraform 보간식 삽입 | workflow 추출은 실패하고 boot만 성공 | 중간 | P0 | raw Python 실행 + 실제 Terraform 렌더본 AST/동작 비교 |
| 게시 실패·health 실패·잠금 충돌 | env/overlay 혼합·잘못된 backup | 중간 | P0 | 실패 stage별 active/previous/pending 상태와 반환 코드 |
| stderr/config/payload에 좌표 key 노출 | 복호화 key 유출 | 중간 | P0 | 성공·실패 모두 합성 key/ID sentinel 미노출 |

## 5. Unit scenarios

Owner U는 단위 테스트 실행 에이전트이며 파일은 §9에 고정한다.
순수 검증은 실제 runtime 정의를 실행한다. 새 검증 함수 이름을 계획에서 강제하거나
테스트를 위해 production helper를 추가하지 않는다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-001 | 유효한 독립 32-byte 좌표 key와 ID, ASCII·다중 byte ID의 255-byte/256-byte 경계 | 실제 좌표 입력 검증 | 255 bytes 이하 유효값은 수용, 256 bytes는 거부; 문자 수로 잘못 판정하지 않음 | P0 | U |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-002 | canonical Base64 32 bytes 정상 및 decoded 0/16/31/33/64 bytes, 잘못된 alphabet/padding/noncanonical pad bits·공백/개행 | 실제 좌표 key 형식 검증 | 정상만 수용, 오류는 원문 없는 실패; 문자열 길이만 검사하는 회귀 검출 | P0 | U |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-003 | 좌표 key가 push AES 또는 push fingerprint key와 같은 경우 및 모두 다른 대조군 | 실제 목적별 key 검증 | 두 동일 경우 모두 거부, 독립 세트는 수용; 기존 validate_push 보장은 보존 | P0 | U |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-004 | 두 필수 좌표 필드 각각의 누락·빈 문자열·whitespace-only·NUL·비문자열 입력 | 실제 입력 검증 | 어느 필드든 거부, 이전 값/가짜 fallback으로 채우지 않음; 두 정상 필드는 수용 | P0 | U |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-UNIT-005 | 가짜 기존 env의 두 좌표 중복 할당·CRLF·무관한 multiline와 서로 다른 새 값 | 실제 update_env와 dotenv 함수 실행·동일 입력 재실행 | 두 좌표 할당만 교체하고 무관한 값·push/DB/auth 보존, 재실행 결과 동일; 일반 escape 기존 케이스 유지 | P0 | U |

UNIT-002의 잘못된 입력은 고정 합성 byte 배열에서 만든다.
UNIT-004는 순수 입력 경계, INT-002는 외부 SSM 응답과 파일 게시 중단이라는 다른 책임이다.
UNIT-005는 텍스트 갱신의 소유 경계이며 실제 Compose 해석 보장은 INT에서 수행한다.
기존 push 4개 단위 테스트를 지우거나 새 좌표 검증으로 대체하지 않는다.

## 6. Integration scenarios

Owner I는 통합 테스트 실행 에이전트다. 전부 P0이며 각 행의 Setup/Action/Expected가
Given/When/Then이다. 실제 Compose `config`는 사용하고 pull/up/health/AWS의 외부 효과만 대체한다.

| Scenario ID | Components | Setup (Given) | Action (When) | Expected result (Then) | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-001 | 실제 runtime·SSM double·실제 Compose | 기존 env/DB/auth와 push 5개, 유효 좌표 2개; previous 파라미터 없음 | 기존 호스트 정상 배포 | .env/overlay/effective app 환경에 두 좌표 이름·값 정확 일치, push 5개/DB 서비스/무관한 env 보존, app만 재생성, previous 조회 없음 | 전용 임시 디렉터리·자식 프로세스 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-002 | 실제 조회·검증·파일 경계 | 정상 성공 세트 뒤 각 좌표 응답의 ParameterNotFound/AccessDenied·Value 누락·빈 값·whitespace·NUL·비문자열·잘못된 Base64; 나머지는 정상 | 정상 배포 시도 | 실패로 반환, active/backup byte 보존, 부분 좌표 게시·app 재생성 없음. 두 필드를 독립 변경해 검사 | 동일 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-003 | 실제 Terraform console·gzip·raw workflow 전달·Compose | 신선한 sandbox와 기존 host, 같은 합성 SSM map, boot template 변수에 새 이름 2개 | base64gzip(templatefile(...)) 렌더 및 두 전달 경로 실행 | raw/렌더/전달 Python 정의 AST 일치, 렌더 payload 16 KiB 이하, 새 두 값과 push 5개 effective 결과 일치, 보간식 미해결·치환 의존 없음 | 동일; backend 없는 console 디렉터리 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-004 | 실제 workflow deploy shell·SSM 전달·rollback | 좌표 포함 이전/현재 이미지 세트, 현재 SSM 응답은 변경 또는 모든 get-parameter 실패; 별도 legacy 이전 세트 | REQUESTED_IMAGE_SHA 비공란으로 보관 이미지 rollback; 보관되지 않은 이미지도 시도 | 요청 이미지와 일치한 보관 env/overlay를 정확히 복원, get-parameter 수 증가 0; 이미지 불일치면 실패·기존 세트 보존. legacy는 이전 overlay 부재도 복원 | 동일 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-005 | 실제 publish/pending/previous/health 경계 | 좌표 포함 정상 세트 및 직전 backup | 각 rename·게시 후 검증·health 실패 후 승인된 수동 rollback 분기 | 이전 파일/backup 또는 pending 복구 근거 유지, 실패를 성공으로 숨기지 않음, rollback에서 좌표 쌍 복원·SSM 재조회 없음 | 동일 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-006 | 실제 runtime·Compose·보존 검사 | A 좌표 세트로 정상 배포 후 B 합성 좌표 세트, 같은/다른 이미지 조합 | 정상 갱신과 동일 입력 재배포 | B 두 값으로 갱신 성공, push·무관한 값 보존, 중복 할당 없음, 직전 성공 세트만 유지; 기존 key not in PUSH_KEYS 회귀를 검출 | 동일 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-007 | 실제 오류 처리·payload·출력 경계 | 독립 좌표 ID/key 및 기존 credential 누출 sentinel, AWS/JSON/Compose 오류 double | 정상 workflow와 각 오류 경로 실행 | stdout/stderr·원격 출력·SSM payload·진단 파일에 가짜 key/ID/credential 원문 없음, 단계 실패와 종료 코드만 관측 | 동일; 허용 저장 파일은 §8 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-008 | 실제 lock·pending 복구·환경 격리 | 두 좌표와 APP_IMAGE가 부모 환경에서 오염됨; 별도 lock 획득 또는 pending이 있는 상태 | 정상 배포·잠금 충돌·동일 SHA rollback을 각각 실행 | 정상은 지정 env 값 사용, 충돌은 파일 보존하며 실패, pending 우선 복구와 lock/backup 보존, 부모 값으로 복구되지 않음 | 동일; lock은 컨텍스트 종료로 해제 |
| TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY-INT-009 | 실제 push 검증과 좌표 주입 연결 | 유효 좌표와 기존 push negative matrix(누락/빈 값/잘못된 AES·HMAC/서로 같은 key/JSON 구조·project 불일치) | 실제 정상 배포 분기를 실행 | 기존 validate_push 오류 거부·게시 차단이 유지됨, 유효 대조군에서 push 5개와 좌표 2개 모두 전달 | 동일 |

INT-004는 image_sha가 설정된 실제 workflow 경계를 반드시 한 번 통과시킨다.
`deploy(..., rollback=True)` 직접 호출만으로는 workflow 연결 회귀를 잡을 수 없다.
image_sha 공란의 실제 정상 workflow는 INT-003의 기존 호스트 경로가 소유한다.
초기 주입에 rollback을 쓰면 안 된다는 계약을 별도 prose assertion으로 만들지 않는다.
SSM 재조회 없음은 get-parameter 비밀 조회에 한정하며 ECR login/pull 같은 요청까지 0이라고 주장하지 않는다.

INT-003의 AST 비교는 하나의 runtime이 두 실제 전달 경로에서 같은 정의를 제공한다는
machine-consumed 계약이다. 단순 `${...}` 문자열 grep이나 문서 문구·행 수 검사를 만들지 않는다.
기존 boot_source의 변수 map에도 새 이름을 넣고, raw 추출 구간은 Terraform 렌더 없이 실제 실행한다.

INT-006의 key 변경은 합성 fixture로 파일 갱신·보존 분기를 구별하기 위한 것이다.
실제 dev current-only key 회전을 허가하지 않으며 H-1 version=1 제한을 우회하는 계획이 아니다.
INT-004의 legacy rollback 역시 pre-#389 스키마 호환 이미지를 가정한 파일 복구 경계이며
#389 migration 이후 구 앱 rollback을 정당화하지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- 이 fixture는 DB에 접속하지 않고 SQL·migration·Flyway를 실행하지 않는다.
- 기존 host에서 DB Compose 정의와 volume을 보존하고 app만 재생성함을 확인한다.
  fresh 부팅 경로의 db 시작은 기존 계약이므로 기존 host와 구분한다.
- env/override 두 rename은 단일 transaction이 아니다. INT-005가 각 중간 실패와
  pending/previous 복구를 검사하며 파일 복구 성공을 컨테이너 자동 복구 성공으로 오인하지 않는다.

### Concurrency and idempotency

- INT-008의 충돌은 부모가 실제 file lock을 먼저 획득한 뒤 child를 실행해 결정적으로 만든다.
  동시 스케줄링 운이나 sleep을 쓰지 않고 child의 완료 이벤트를 bounded timeout으로 기다린다.
- INT-006의 반복 실행에서 두 좌표 항목이 중복되지 않고 backup이 무관하게 늘지 않아야 한다.
- 기존 INT-011/012의 monotonic/sleep은 가상 clock으로 유지한다.
  새 wall-clock sleep·polling·flaky retry·test 순서 의존을 추가하지 않는다.

### External APIs

- aws double은 로컬 실행 경계에서 argv·응답 JSON·stderr·exit code를 보존한다.
  운영 자격 증명·metadata 접근과 실제 네트워크 요청은 하지 않는다.
- 새 두 파라미터의 이름별 실패/누락 제어를 fixture 안에서 추가할 수 있으나 검증/갱신 결과를
  double이 대신 만들어서는 안 된다. 실제 runtime이 파일을 생성해야 한다.
- 실제 Compose의 env/overlay 해석을 사용한다. AWS 조회·ECR login/pull·컨테이너 up·health만
  기존 효과 double로 격리한다. 효과 기록은 실제 runtime이 발생시킨 결과여야 한다.

### Failure recovery and reconciliation

- 조회·형식 오류는 게시 전 실패, rename/health 오류는 게시 이후 복구 경계로 구분한다.
- 저장된 이미지와 맞는 previous 또는 pending을 골라야 하며 임의 이미지와 env를 조합하지 않는다.
- helper별 단위 테스트 성공만으로 배포 복구를 증명하지 않는다. 실제 shell→payload→host→파일까지
  한 번 연결하고 파일/실효 Compose/종료 상태를 관측한다.
- 실효 IAM·권한·SSM Agent·실행 중 컨테이너 key 동일성은 사람의 별도 live 인수 검증으로 남는다.

## 8. Test data and isolation

- 고정 합성 byte 배열에서 생성한 서로 다른 32-byte 좌표/push AES/push HMAC만 사용한다.
  실제 key·Environment secret·프로덕션 ID/주소·계정·ARN을 문서나 fixture로 복사하지 않는다.
- ID 경계는 ASCII 및 다중 byte 문자를 사용해 UTF-8 byte 길이로 만든다.
  dotenv 경계는 dollar·quote·backslash·CRLF와 무관한 multiline을 사용한다.
- Base64 key 원문에는 고정 길이 합성 데이터를 사용하고 유효 데이터·손상 데이터·부모 오염값을
  서로 다르게 둔다. 입력 자체를 테스트 문서에 값으로 나열하지 않는다.
- namespace의 PUSH_KEYS와 고정 5개 값 zip을 기계적으로 7개로 늘리지 않는다.
  push map과 두 좌표 map을 명시적으로 구성해 누락/순서 교환이 실제 실패가 되게 한다.
- 시나리오별 JUnit `@TempDir` 하위 sandbox와 격리 child 환경을 사용한다.
  real HOME은 Compose plugin 탐색에만 사용하고 fake host HOME/ROOT/STATE는 sandbox다.
- `AWS_CONFIG_FILE`/`AWS_SHARED_CREDENTIALS_FILE`은 기존 격리를 유지하며 실제 AWS 실행 파일을
  호출하지 않는다. container image pull·daemon 동작도 double로 대체한다.
- 합성 비밀 저장을 허용하는 파일은 기존 `.env`, fixture 응답 저장소, previous/pending JSON뿐이다.
  overlay/payload/remote-output/진단 파일에는 포함되면 실패다.
  실패 시 assertion이 실제 secret은 물론 합성 누출 원문도 불필요하게 재출력하지 않도록 결과를 요약한다.
- 완료·실패 모두 자식 프로세스/descendant를 종료하고 자신이 만든 임시 자원만 정리한다.
  저장소·공유 workspace·사용자 파일을 cleanup 대상으로 삼지 않는다.
- 호스트 root 관련 호출은 기존 sandbox 대체를 유지한다. mode 0600/0700은 검사할 수 있지만
  실제 EC2 root 소유권·OS 권한 강제를 증명한 것으로 보고하지 않는다.

### 실행 의존성과 시간 한도

- 단위: Java 21/JUnit 5와 Python 3 표준 라이브러리. Docker/Compose/Terraform에 의존시키지 않는다.
- 표적 통합: Python 3/PyYAML, Terraform console, 실제 Compose version/config.
  `QELLO_TEST_COMPOSE`가 있으면 해당 standalone, 없으면 PATH의 Docker Compose를 사용한다.
  미설치/실행 불가 시 이유와 함께 실패하고 skip하지 않는다.
- 현재 CI의 Python 3.13, PyYAML 6.0.3, Terraform 1.17.1과 wrapper=false를 읽기 확인했다.
  이번 계획은 CI 도구 버전/설치 단계 변경을 포함하지 않는다.
- Terraform console은 backend 없는 sandbox에서 template/gzip 표현식만 평가한다.
  init/provider 설치/AWS 호출/State 접근은 하지 않는다.
- 기존 unit child 90초, integration child 180초, 내부 subprocess의 더 짧은 timeout은
  hang 방지 한도로 유지한다. 통과를 위해 한도를 늘리거나 실패를 재시도로 숨기지 않는다.
- 표적 통합 fixture 자체는 daemon 불필요다. 전체 Gradle check의 기존 DB/Testcontainers에는
  별도의 로컬 Docker daemon이 필요하며 이 둘의 환경 요구를 혼동하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 0 | 오케스트레이터 | 이 계획과 D1~D5 승인 증거 | 전체 | 사람 승인 및 D-6 구현 handoff 확인; 이번에는 계획만 |
| 1 | 단위 실행 에이전트 U | `src/test/java/com/dnd/qello/infrastructure/PushSecretEncodingTest.java` | UNIT-001~005 및 기존 4개 회귀 | 표적 JUnit 5 단위 실행 |
| 2 | 통합 실행 에이전트 I | `src/integrationTest/java/com/dnd/qello/infrastructure/PushSecretDeploymentIntegrationTest.java` | INT-001~009 및 기존 13개 회귀 | 실제 shell/Python/Compose/template 렌더 표적 통합 실행 |
| 3 | 독립 검증 에이전트 | 구현/테스트 파일은 읽기 전용, 별도 실행 보고서 | 전체 | 실제 diff·표적/전체 결과·미실행 항목 대조 |

실행 전에 `.agents/skills/harness-test-run/SKILL.md`와 보고 계약을 읽는다.
테스트 파일 2개는 서로 소유권이 겹치지 않는다. template/runtime 수정은 D-6 인프라 실행
에이전트의 별도 승인 범위이며 테스트 실행자가 고쳐서 테스트를 통과시키지 않는다.
수정이 필요하면 원인·실패 증거를 오케스트레이터에 반환한다.
기존 test header의 Created at과 D-5 source scenario 이력은 보존하고 새 D-6 source scenario를 추가한다.
새 메서드에는 `@DisplayName`을 붙인다. 기존 timestamp를 새 작업일로 덮어쓰지 않는다.

승인 후 실행 명령:

```bash
./gradlew test --tests com.dnd.qello.infrastructure.PushSecretEncodingTest --rerun
./gradlew integrationTest --tests com.dnd.qello.infrastructure.PushSecretDeploymentIntegrationTest --rerun
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

`--rerun`은 표적 task의 캐시 우회를 위한 것이며 실패 자동 재시도가 아니다.
전체 check는 `pr-ready --project-tests`에서 실행하고 동일 변경에 별도 `./gradlew check`를
불필요하게 중복 실행하지 않는다. 도구 버전·실행한 task·JUnit 합계·실패/오류/skip 수를 기록한다.
새 후보 케이스가 원래 의도한 회귀에서 실패하는지 실행자가 확인하고 증거를 남긴다.
테스트용 mutation이 필요하면 소유자와 조정된 격리 환경에서만 시행하고 production 바이트를
원상 복원한다. 공유 workspace의 타인 변경은 건드리지 않는다.

보고 경로는 승인 후 `docs/reports/tests/gh-390-TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY.md`다.
이 계획 작성 단계에서 실행 보고서를 생성하거나 실행 성공으로 채우지 않는다.

## 10. Completion criteria

- [ ] 이 계획의 D1~D5와 전체 테스트 작성·실행을 사람이 승인함
- [ ] 5개 UNIT·9개 INT 계약을 기존 primary owner에 구현하거나 확장함
- [ ] 기존 push 단위 4개/통합 13개 보장을 삭제·skip·약화하지 않음
- [ ] raw runtime 추출·실제 Terraform 렌더·실제 Compose·workflow image_sha rollback 증거 확보
- [ ] 두 좌표 필수값·push 보존·실패 복구·합성 비밀 비노출 검증
- [ ] 기존 헤더 timestamp/source 이력 보존 및 새 source scenario·DisplayName 확인
- [ ] 단위·통합·전체 저장소 검사 통과, 실패/오류/skip 수를 실제 결과로 보고
- [ ] 앱/인프라/DB/동시성/파일 transaction/외부 API/복구 관점 잠재 문제와 live 잔여 위험 분석
- [ ] 별도 실행 보고서와 독립 검증 완료

테스트 계획의 완성은 위 실행 완료 체크와 다르다. 현재 체크 항목은 전부 미실행/미승인이다.
도구 부족·승인 부족은 BLOCKED, 재현 가능한 코드/검증 실패는 FAIL로 보고한다.
기존 실패는 원인을 구분하고 삭제·skip·assertion 약화로 녹색을 만들지 않는다.

## 11. Human approval

- Reviewer: @Byuntil
- Decision: `APPROVED` — D1~D5 추천안대로 승인
- Approved at: 2026-11-25
- Approval evidence: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/390#issuecomment-7155537194

| ID | 사람 결정 | 선택지 | 추천과 이유 |
| --- | --- | --- | --- |
| D1 | 테스트 소유 범위 | 기존 두 fixture 파일 확장 / 별도 클래스 분리 | 기존 두 파일 확장. D-6 §9/H-7 범위를 유지하고 D-5 검증을 재사용 |
| D2 | 필수 시나리오 | UNIT-001~005·INT-001~009 전부 P0 / 범위 재조정 | 전부 P0 승인. 필수값·목적 분리·두 전달 경로·rollback·복구·비노출을 빠뜨리지 않음 |
| D3 | 격리 경계 | 실제 Compose/template/shell + 외부 효과 double / 실제 AWS·서버 검증 | 전자. 실제 전달 로직은 실행하되 AWS 접근 없이 재현 가능, live 검증은 별도 사람 경계 |
| D4 | fingerprint·IAM 검증 구분 | D-6의 사람 live 절차로 분리 / 추가 자동화 별도 설계 | 분리. 이 fixture 성공으로 실제 키 일치·MFA·IAM을 승인하지 않고 임의 script/권한 확대 방지 |
| D5 | 실행·완료 기준 | 표적 2개와 pr-ready --project-tests / 표적만 제한 승인 | 전체 명령 승인 권고. 공용 runtime 변경의 기존 회귀를 확인하며 도구 부족은 skip 대신 BLOCKED |

추천은 사람의 결정이 아니다. 승인 후에도 실제 key 생성·등록, AWS 조회·배포,
Terraform plan/apply, 커밋·push는 이 테스트 계획 승인 범위에 포함되지 않는다.

### 계획 작성 시 확인한 근거

2026-11-25 저장소 지침·템플릿, D-5 승인 계획, 기존 unit 4개/integration 13개,
현재 runtime과 deploy shell, Gradle source set·JUnit 설정, CI 도구 설치 설정을 읽었다.
파일 편집 도구 전용 지침에 따라 `harness test-plan` scaffold 대신 동일 11개 절을 직접 작성했다.
이번에는 JUnit/Terraform/실제 Compose를 실행하지 않았고 테스트/production 파일도 수정하지 않았다.
