## 5. Terraform 주석 규칙

Terraform 주석은 코드가 무엇을 하는지 번역하지 않고, 코드만으로 알 수 없는 설계 의도와 제약을 설명한다.

### 5.1 주석이 필요한 경우

다음 경우에는 주석을 작성한다.

* 일반적인 기본값과 다른 설정
* AWS 서비스 제약으로 인한 우회 구현
* 보안 또는 컴플라이언스 요구사항
* 비용 절감을 위해 가용성이나 기능을 제한한 결정
* 명시적 `depends_on`
* `lifecycle`
* `prevent_destroy`
* `ignore_changes`
* 조건부 리소스 생성
* 환경별 동작 차이
* 외부 시스템이 관리하는 속성
* eventual consistency 대응
* 보안 검사 예외
* 임시 호환성 설정

### 5.2 작성하지 않는 주석

코드에서 직접 알 수 있는 내용을 반복하지 않는다.

잘못된 예:

```hcl
# VPC를 생성한다.
resource "aws_vpc" "main" {
}

# 버킷 이름
bucket = var.bucket_name

# Claude가 생성한 코드
# 사용자 요청에 따라 수정
```

에이전트의 추론 과정, 프롬프트, 대화 내용과 작업 과정을 주석에 기록하지 않는다.

### 5.3 설계 의도 주석

짧은 설계 의도는 대상 블록 바로 위에 작성한다.

```hcl
# 개발 환경의 고정 비용을 줄이기 위해 NAT Gateway를 단일 AZ에만 생성한다.
# 운영 환경에서는 가용성 요구사항에 따라 AZ별로 생성한다.
resource "aws_nat_gateway" "this" {
}
```

복잡한 제약은 원인, 결정과 영향 순서로 작성한다.

```hcl
# 원인: ECS의 기존 Task가 배포 중 연결을 최대 10분 유지할 수 있다.
# 결정: ALB deregistration delay를 600초로 유지한다.
# 영향: 배포 완료 시간은 증가하지만 진행 중 요청의 강제 종료를 방지한다.
deregistration_delay = 600
```

상세 근거가 ADR 또는 설계 문서에 있으면 문서 식별자를 기록한다.

```hcl
# ADR-INFRA-004: 운영 데이터베이스의 우발적 삭제를 방지한다.
lifecycle {
  prevent_destroy = true
}
```

### 5.4 `depends_on`

Terraform이 참조를 통해 추론할 수 있는 의존성에는 `depends_on`을 사용하지 않는다.

명시적 의존성이 필요한 경우 이유를 작성한다.

```hcl
# IAM 정책 연결 직후 발생할 수 있는 권한 전파 지연으로
# ECS 최초 배포가 실패하지 않도록 명시적인 선행 조건을 둔다.
depends_on = [
  aws_iam_role_policy_attachment.ecs_execution
]
```

이유를 설명할 수 없는 `depends_on`은 추가하지 않는다.

### 5.5 `ignore_changes`

`ignore_changes`는 외부 시스템이 실제로 관리하는 속성에만 사용한다.

다음을 주석으로 기록한다.

* 외부 관리 주체
* Terraform이 변경을 무시해야 하는 이유
* 무시하는 속성
* 제거 조건

```hcl
lifecycle {
  # 배포 workflow가 Task Definition revision을 갱신한다.
  # 배포 주체가 Terraform으로 전환되면 이 예외를 제거한다.
  ignore_changes = [
    task_definition
  ]
}
```

다음 설정은 기본적으로 금지한다.

```hcl
lifecycle {
  ignore_changes = all
}
```

사용이 필요한 경우 ADR, 추적 Issue와 사람의 승인이 필요하다.

### 5.6 TODO와 임시 예외

`TODO`, `FIXME`, `TEMP`, `HACK`만 단독으로 작성하지 않는다.

TODO에는 다음을 포함한다.

* 추적 Issue
* 재검토 또는 만료 날짜
* 완료 조건

```hcl
# TODO(INFRA-142, 2026-10-31): Multi-AZ 전환 후 단일 NAT Gateway 예외를 제거한다.
```

금지 예:

```hcl
# TODO: 나중에 수정
# FIXME
# 임시
```

### 5.7 보안 예외

보안 예외에는 다음을 기록한다.

* 예외 이유
* 영향 범위
* 보완 통제
* 담당 팀
* 만료일
* 추적 Issue 또는 ADR

```hcl
# SECURITY-EXCEPTION
# 이유: 외부 시스템의 송신 IP가 고정되어 있지 않다.
# 범위: staging webhook listener의 443 포트
# 보완 통제: 요청 서명 검증과 WAF rate limit
# 소유자: platform-team
# 만료일: 2026-10-31
# 추적: INFRA-142
```

만료일과 추적 항목이 없는 보안 예외는 추가하지 않는다.

### 5.8 변수와 출력 설명

변수와 output의 설명은 별도 주석보다 `description`을 사용한다.

```hcl
variable "backup_retention_days" {
  description = "RDS 자동 백업 보존 기간. 운영 환경은 최소 7일이어야 한다."
  type        = number
}
```

민감한 output에는 `sensitive = true`를 지정한다.

### 5.9 주석 언어

* 기본 주석 언어는 한국어로 통일한다.
* AWS 서비스명, Terraform 속성명과 프로토콜명은 원문을 유지한다.
* 짧고 단정한 문장으로 작성한다.
* 추측성 표현을 사용하지 않는다.
* 장문 설명은 ADR 또는 Infrastructure Design Report로 이동한다.
* 주석과 구현이 달라지면 같은 변경에서 함께 수정한다.
