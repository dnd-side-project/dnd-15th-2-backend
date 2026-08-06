---
name: infra-architecture
description: 승인된 요구사항을 기준으로 AWS 아키텍처 후보를 비교하고 선택안과 탈락 이유를 작성한다.
user-invocable: false
---

# AWS Architecture Design

`infra-intake` 결과를 입력으로 사용한다.

## 설계 원칙

- 가장 낮은 실용 비용을 우선한다.
- 특정 AWS 서비스를 사전에 정답으로 고정하지 않는다.
- 운영 인력이 없는 상황을 주요 제약으로 반영한다.
- 관리형 서비스의 추가 비용과 운영 부담 감소를 함께 평가한다.
- 현재 규모뿐 아니라 예상되는 첫 번째 확장 지점을 기록한다.
- Terraform으로 선언할 수 있는 구성을 우선한다.

## 후보 선정

요구사항에 적합한 후보만 비교한다.

컴퓨팅 후보 예:

- EC2
- ECS on Fargate
- ECS on EC2
- Lambda
- App Runner
- EKS

데이터베이스 후보 예:

- RDS
- Aurora
- DynamoDB
- 자체 운영 데이터베이스

모든 후보를 기계적으로 비교하지 않는다.

## 비교 기준

- 요구사항 적합성
- 월 예상 비용
- 운영 부담
- 배포 복잡도
- 가용성
- 확장 방식
- 장애 복구
- 보안 경계
- 관측성
- 서비스 종속성
- 데이터 이전 난이도
- 향후 전환 비용

## 필수 설계 영역

- Account와 Environment 경계
- VPC와 Subnet
- Internet ingress와 egress
- Compute
- Database
- Object storage
- DNS와 TLS
- Secret 관리
- Logging
- Metrics와 Alarm
- Backup
- Recovery
- Terraform State
- GitHub OIDC
- 배포와 롤백

## 출력

다음을 반환한다.

```text
status:
selected_option:
decision_summary:
alternatives:
rejected_reasons:
network:
compute:
database:
storage:
observability:
backup_recovery:
deployment:
terraform_ownership:
open_decisions:
```
UNKNOWN 또는 BLOCKED 입력에 의존하는 결정은 확정안으로 표현하지 않는다.