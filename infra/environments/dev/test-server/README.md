# Dev Test Server

프론트엔드가 실제 백엔드에 붙어 테스트할 dev 서버 한 대를 만든다.
Infrastructure Design Report `docs/reports/infrastructure/gh-229-D-3.md`
(DESIGN-ID `D-3`, GitHub Issue #229 설계, #233 구현)를 따른다.

## 리소스

| 리소스 | 설명 |
| --- | --- |
| VPC + 퍼블릭 서브넷 1개 | 단일 AZ. NAT Gateway 없음(예산 제약) |
| EC2 `t3.small`(x86_64) | IMDSv2 필수, Docker Compose로 백엔드+PostGIS 컨테이너 구동 |
| Elastic IP | 프론트엔드가 접근하는 고정 공인 IP |
| 데이터 EBS 볼륨(gp3, 10 GiB) | PostGIS 데이터 전용. 인스턴스와 분리, `prevent_destroy` |
| ECR 리포지토리 | 백엔드 이미지. 최근 `ecr_max_image_count`개만 보존 |
| SSM SecureString 파라미터 2개 | DB 비밀번호, `QELLO_AUTH_ACCESS_TOKEN_SECRET`. `value_wo`로 State에 값이 남지 않는다 |
| EventBridge Scheduler | 매일 새벽 3시 EC2 자동 정지(H-1). 자동 시작은 없다 |

## 예산

월 약 9.55 USD(평일 업무 시간 160시간 구동 가정). 예산 상한 10 USD 대비
여유 4.5%. 인스턴스를 끄지 않고 방치하면 초과한다(설계 보고서 §6, §8).

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
