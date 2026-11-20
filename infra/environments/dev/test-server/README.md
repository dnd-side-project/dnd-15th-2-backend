# Dev Test Server

프론트엔드가 실제 백엔드에 붙어 테스트할 dev 서버 한 대를 만든다.
Infrastructure Design Report `docs/reports/infrastructure/gh-229-D-3.md`
(DESIGN-ID `D-3`, GitHub Issue #229 설계, #233 구현)를 따른다.
Push secret 주입은 승인된 `docs/reports/infrastructure/gh-364-D-5.md`(D-5, #364)를 따른다.

## 리소스

| 리소스 | 설명 |
| --- | --- |
| VPC + 퍼블릭 서브넷 1개 | 단일 AZ. NAT Gateway 없음(예산 제약) |
| EC2 `t3.small`(x86_64) | IMDSv2 필수, Docker Compose로 백엔드+PostGIS 컨테이너 구동 |
| Elastic IP | 프론트엔드가 접근하는 고정 공인 IP |
| 데이터 EBS 볼륨(gp3, 10 GiB) | PostGIS 데이터 전용. 인스턴스와 분리, `prevent_destroy` |
| ECR 리포지토리 | 백엔드 이미지. 최근 `ecr_max_image_count`개만 보존 |
| SSM SecureString 파라미터 7개 | 기존 DB·access token 2개와 D-5 push 설정 5개. `value_wo`로 값을 전달한다 |
| EventBridge Scheduler | 매일 새벽 3시 EC2 자동 정지(H-1). 자동 시작은 없다 |

## 예산

월 약 9.55 USD(평일 업무 시간 160시간 구동 가정). 예산 상한 10 USD 대비
여유 4.5%. 인스턴스를 끄지 않고 방치하면 초과한다(설계 보고서 §6, §8).
이 수치는 D-3의 과거 스케줄 구동 가정이다. 상시 구동 이후의 전체 서버 예산 재확인은
D-5 범위 밖 후속 항목이며, 이번 변경으로 예산 준수를 확인한 것은 아니다.

## 적용 절차

### 1. 사전 준비

`infra/environments/dev/storage`(#63)가 먼저 적용되어 있어야 한다. 그
스택의 `post_image_kms_key_arn` output 값을 이 스택의
`media_bucket_kms_key_arn` 변수에 넣는다.

### 2. terraform.tfvars 작성

```bash
cp terraform.tfvars.example terraform.tfvars
# media_bucket_kms_key_arn만 채운다. db_password/auth_token_secret은
# 이 파일에 적지 않는다.
```

### 3. 비밀값 준비

Terraform은 DB 비밀번호와 `QELLO_AUTH_ACCESS_TOKEN_SECRET` 값을 생성하지
않는다(D-3 §5 S-5). apply 시점에 별도로 제공한다.

```bash
export TF_VAR_db_password="<DB 비밀번호>"
export TF_VAR_auth_token_secret="<QELLO_AUTH_ACCESS_TOKEN_SECRET 값>"
```

### 4. init과 plan

```bash
terraform init \
  -backend-config="bucket=<bootstrap state_bucket_name output>" \
  -backend-config="region=ap-northeast-2"
terraform plan -lock-timeout=5m -out=tfplan
```

`apply`는 이 저장소의 정책상 보호된 `infrastructure-apply` GitHub
Environment workflow에서만 실행한다(AGENTS.md 4.8). 로컬이나 Claude Code
세션에서 apply하지 않는다.

## Push secret 준비와 입력

Secret owner·장애 담당은 `@Byuntil`, 백업 담당은 `@tkv00`다. dev 전용 Firebase 프로젝트와
발송 전용 서비스 계정만 사용하며 prod 프로젝트·기본 관리자 계정 credential을 넣지 않는다.
key 발급과 AES/HMAC 난수 생성·입력은 사람이 수행한다. secret 원문, 실제 프로젝트·계정 ID,
ARN과 `.env` 값은 저장소·Issue·로그·CLI 인자에 기록하지 않는다.

| Terraform 입력 | 보호된 workflow secret | 의미 |
| --- | --- | --- |
| `push_token_current_key_id` | `TF_VAR_PUSH_TOKEN_CURRENT_KEY_ID` | 현재 암호화 key ID |
| `push_token_current_encryption_key_base64` | `TF_VAR_PUSH_TOKEN_CURRENT_ENCRYPTION_KEY_BASE64` | standard Base64의 32-byte AES key |
| `push_token_fingerprint_key_base64` | `TF_VAR_PUSH_TOKEN_FINGERPRINT_KEY_BASE64` | AES와 독립적인 standard Base64의 32-byte HMAC key |
| `push_fcm_project_id` | `TF_VAR_PUSH_FCM_PROJECT_ID` | dev Firebase 프로젝트 식별자 |
| `push_fcm_credential_json` | `TF_VAR_PUSH_FCM_CREDENTIAL_JSON` | dev 발송 서비스 계정 JSON |

위 다섯 입력은 `sensitive`와 `ephemeral`이며 `value_wo`에만 전달한다. secret을 담은
Terraform data source나 output을 추가하지 않는다. saved plan의 write-only 입력은 apply 때도
동일한 보호된 입력 경로로 제공해야 한다. 기존 두 입력의 plan 취급이 바뀌었다는 뜻은 아니다.
값은 `infrastructure-apply`의 보호된 secret 입력으로 등록하며 SSM을 콘솔·CLI로 선생성하지 않는다.
다른 GitHub gate, Environment, dispatch 확인 문구와 트리거는 변경하지 않는다.

발급 후 compact UTF-8 JSON의 바이트 수만 기록한다. 기본은 Standard(4 KiB 이하)이며,
4 KiB 초과~8 KiB 이하일 때 credential 하나만 Advanced 비용이 승인되어 있다.
`push_fcm_credential_json_tier` 설정은 사람의 plan과 보호 workflow가 같은 값으로 실행되도록
검토된 Terraform 설정에 반영한 뒤 진행한다. ephemeral JSON에서 tier를 자동 계산하지 않는다.
8 KiB도 넘으면 저장 방식을 재설계할 때까지 중단한다.

새 값에는 각각 대응하는 `_version` 입력이 있다. 새 값 갱신 시 해당 version을 증가시키며
검토된 설정과 사람의 plan 증거에 반영한다. workflow에는 다섯 secret binding만 추가되어
version이나 tier가 secret 등록만으로 자동 변경되지는 않는다.
key ID·AES·HMAC을 배포마다 재생성하지 않는다. 암호화·fingerprint key 회전은 기존 기기
데이터의 복호화·매칭을 손상할 수 있어 별도 계획이 필요하다. 회전 재검토일은 `2027-05-20`이다.

## Push 적용 순서와 권한

1. 사람이 기존 MFA 인프라 역할로 정상 refresh를 사용하는 plan을 실행하고 plan SHA-256,
   normalized resource_changes SHA-256, 리소스 수를 구분해 검토 증거를 만든다.
   현재 plan 역할의 권한은 늘리지 않으며 `-refresh=false` 우회는 금지한다.
2. 인프라 PR·Environment 게이트를 통과한 보호 workflow에서 사람이 apply한다.
   AI 세션은 plan·apply·State/import/taint/force-unlock과 AWS 리소스 변경을 실행하지 않는다.
3. 기존 호스트는 user_data 변경만으로 갱신되지 않으므로 승인된 애플리케이션 배포의 SSM
   명령으로 `.env`와 `compose.push.yaml`을 staging·검증·게시한다. 인스턴스를 교체하거나
   user_data 전체를 재실행하지 않는다.
4. 새 다섯 설정의 존재·형식·JSON 파싱, app health, 기존 DB·access token과 데이터 볼륨 보존,
   SSM Agent Run Command 배포 경로, 허용 7개 밖 read/decrypt 거부 및 기존 S3 key 접근을
   승인된 환경에서 검증한다. 값·실제 식별자·전체 Compose config를 로그에 출력하지 않는다.
5. #364의 apply와 기존 인스턴스 다섯 설정 주입 검증이 끝난 뒤에만 #365를 main에 병합한다.
   provider와 worker 활성화는 #366의 별도 범위다.

`AmazonSSMManagedInstanceCore`는 유지한다. 그 정책의 광역 파라미터 read를 좁은 Allow만으로
제한할 수 없어 runtime role에 허용 7개 밖 `GetParameter`·`GetParameters`의 명시적 Deny를
추가한다. 복호화 Deny는 SSM 기본 key와 그 7개 encryption context에만 적용하며 S3 key는
건드리지 않는다. 실제 IAM·Run Command 동작은 정적 validate만으로 증명되지 않는다.

부팅 정의는 EC2 user data의 16 KiB 제한 때문에 `base64gzip`으로 압축해
`user_data_base64`로 전달한다. secret 값은 포함하지 않는다.
`user_data_replace_on_change = false`와 기존 AMI 무시 정책을 유지하며,
`lifecycle.ignore_changes`에 `user_data`와 `user_data_base64`를 추가한다.
최초 부팅에만 실행되는 user data의 gzip 전환 때문에 기존 인스턴스를 stop/start하지 않기
위한 예외다. 기존 호스트 설정은 D-5 H-2의 deploy workflow가 갱신한다.
추가 승인 증거는 https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7037762767 이다.
이 예외로 기존 인스턴스의 부팅 정의 변경은 Terraform에서 반영되지 않으므로,
인스턴스 교체를 다루는 별도 Issue에서 예외 제거를 재검토한다. 실제 변경 효과는 사람의 plan으로 확인한다.

애플리케이션 배포에는 기존 설정에 더해 GitHub variable
`TEST_SERVER_SSM_PARAMETER_PATH_PREFIX`가 필요하다. 기존 root의 파라미터 경로 prefix를
`ssm_parameter_path_prefix` output으로 secret owner가 확인해 설정하며, 실제 값은 문서나 로그에 복사하지 않는다.
runner는 파라미터 이름만 SSM 명령에 넣고 값은 EC2 identity로 호스트 안에서 조회한다.

배포 fixture 테스트는 `docs/test-plans/gh-364-TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY.md`에
추가 승인을 받아 가짜 fixture로만 작성·실행한다. 실제 AWS/GCP 호출과 live 검증은 제외한다.

## Push 배포 실패와 롤백

기존 호스트의 배포는 `.env`와 `compose.push.yaml`을 동일한 staging 작업에서 준비한다.
입력과 effective Compose config의 5개 값·이미지·기존 DB 설정을 메모리에서 비교한 후
파일별 rename으로 게시한다. shell의 `APP_IMAGE`를 상속하지 않으며 app만 재생성한다.
실패 메시지는 단계와 고정 코드만 반환하고 AWS·JSON·Compose 오류 원문은 출력하지 않는다.

호스트의 `/opt/qello/.runtime/previous.json`은 이전 `.env`와 override의 일치하는 한 세트를
담은 root-only 파일이다. override가 없었던 경우도 함께 기록한다.
H-4a에 따라 다음 성공 배포에서만 교체하고 날짜별 사본이나 기간별 보존은 만들지 않는다.
동일 구성 재실행은 기존 백업을 그대로 유지한다.
실패한 배포는 기존 성공 백업을 덮어쓰지 않는다.
파일 게시 중 강제 중단의 복구 정보는 별도 `pending.json`에 남으며 정상 배포는 이 상태를
조용히 무시하지 않는다. 이 파일도 secret이므로 출력하거나 일반 로그에 복사하지 않는다.

게시 이후 app 시작·health가 실패하면 workflow는 실패하고 active env/override를
이전 조합으로 복구한다. 이전 app 컨테이너의 재기동까지 자동으로 성공했다고 보장하지 않는다.
운영자는 기존 배포 workflow의 `image_sha`에 복구할 성공 이미지의 SHA를 지정해 수동 재배포한다.
이 경로는 현재 복구 구성 또는 보관된 직전 성공 세트와 SHA가 일치해야 하며, 최신 SSM을
재조회해 복구본을 덮어쓰지 않는다. 해당 세트가 없는 임의의 과거 이미지로 롤백하면 실패한다.
중단된 게시의 pending 복구본이 있으면 이를 우선하고, 없으면 보관된 이전 성공 세트를 먼저
확인하므로 같은 이미지 SHA에서 secret만 바꾼 배포도 이전 구성으로 복구할 수 있다.
health와 설정 존재/형식을 확인한 뒤 복구 완료로 판단한다. 실제 배포·복구는 사람이 수행한다.

## 값 갱신

`db_password`나 `auth_token_secret`을 바꿀 때는 같은 값을 다시 넣어도
`value_wo_version`(`db_password_version`, `auth_token_secret_version`)을
반드시 증가시켜야 Terraform이 갱신을 인식한다(`value_wo` 인자의 요구사항).

## 운영

- 접속은 SSM Session Manager로만 한다. 22번 포트는 열려 있지 않다.

  ```bash
  aws ssm start-session --target <instance_id output 값>
  ```

- 인스턴스가 꺼져 있으면 콘솔 또는 CLI로 수동 시작한다. 자동 시작은
  구현하지 않았다(자동 정지만 H-1 범위).
- 컨테이너 로그는 인스턴스 안에서 `docker compose -f /opt/qello/compose.yaml logs`로 확인한다.
- 데이터 초기화가 필요하면 데이터 볼륨을 재생성한다. Flyway가 빈 DB에
  스키마를 다시 적용한다(V1~V27).

## 알려진 제약

- 8080은 TLS 없이 평문 HTTP로 전체 공개된다(`SECURITY-EXCEPTION`,
  설계 보고서 §5 S-1). 테스트 계정과 테스트 데이터만 사용한다.
- 이 스택만으로는 애플리케이션이 정상 기동하지 않을 수 있다.
  `application.yml`의 알림(FCM) 관련 설정 등 기본값이 없는 속성이
  남아 있으면 컨테이너가 크래시 루프에 빠진다. dev 프로파일로 이 속성들을
  완화하는 작업은 #230 범위다.
