# GitHub Issue #268 Task Contract

> Generated at: `2026-09-17T20:51:23+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `보안 그룹 규칙 설명과 AMI 필터 결함 수정`
- GitHub Issue: `#268`
- Branch: `fix/gh-268-sg-rule-description-and-ami`
- Base branch: `main`

## Objective

- #266 머지로 IAM 인가가 모두 통과한 뒤, 남은 세 리소스가 모듈 코드 결함으로
  거부된 원인을 없앤다. 보안 그룹 규칙 두 개의 `description`이 AWS가 허용하지
  않는 문자를 담고 있었고, AMI 필터가 표준 AL2023 기본 이미지가 아닌 파생
  변종(루트 스냅샷 30GiB)을 선택했다.

## Scope

- `infra/modules/app-server/main.tf`만 수정한다.
  - `aws_vpc_security_group_ingress_rule.app_port`와
    `aws_vpc_security_group_egress_rule.https`의 `description`을 AWS 허용
    문자 집합의 단문으로 바꾼다.
  - `SECURITY-EXCEPTION` 전문을 주석 정책 §5.7 형식의 주석으로 옮긴다.
    승인된 설계(D-3 결정 H-7)도 주석을 요구했다.
  - `data.aws_ami.al2023`의 `name` 필터를
    `al2023-ami-2023.*-kernel-6.1-x86_64`로 한정한다.

## Explicit exclusions

- 루트 볼륨 크기는 바꾸지 않는다. 30GiB로 키우는 대안은 월 약 2 USD가 늘어
  D-3 §6 예산(월 10 USD, 여유 4.5%)을 초과한다.
- 보안 그룹의 허용 포트·CIDR 등 실제 네트워크 공개 범위는 바꾸지 않는다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Terraform 모듈 | `tkv00` | PR 승인(1인, #251 기준) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
terraform fmt -check -recursive infra
terraform init -backend=false && terraform validate   # 격리된 복사본
tflint --recursive
uvx checkov -d infra --framework terraform
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 두 규칙의 `description`이 AWS 허용 문자 집합과 길이 제한을 만족한다
      (92자·24자, 허용 외 문자 없음을 스크립트로 검증)
- [x] `SECURITY-EXCEPTION`이 주석 정책 §5.7 형식(이유·범위·보완 통제·소유자·
      만료일·추적)으로 주석에 남는다
- [x] AMI 필터가 표준 AL2023 기본 이미지만 선택하고, plan에서 루트 볼륨
      8GiB로 충분함을 확인했다
- [x] `terraform fmt`/`validate`/`tflint`/`checkov`/`harness pr-ready` 통과

## 참고

- 발견 경위: #266 머지 후 실제 apply가 서브넷·라우트 테이블·보안 그룹·SSM
  파라미터까지 생성한 뒤 이 세 리소스에서 멈췄다(2026-09-17).
- 관련 이슈: #229/#233/#266.
- 남은 변경: 이 수정 후 plan은 create 8건, 삭제·교체 0건이다.
