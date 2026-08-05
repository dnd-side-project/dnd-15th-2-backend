# GitHub Issue #63 Task Contract

> Generated at: `2026-08-05T16:30:32+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `게시물 이미지 업로드용 테스트 S3 버킷 구축`
- GitHub Issue: `#63`
- Branch: `infra/gh-63-s3-image-upload-bucket`
- DESIGN-ID: `D-1`
- Design report: `docs/reports/infrastructure/gh-63-D-1.md`
- Design status: `APPROVED_FOR_BUILD` — 사람 승인(tkv00, 2026-08-05).
  확정된 결정: Region `ap-northeast-2`, 예산 상한 10 USD, `infra/bootstrap`
  포함, Bootstrap 로컬 State 1회 예외 승인, SSE-KMS, S3 서버 접근 로그
  이벤트만 수집, lifecycle 6개월, `dev-s3-tester` Role(고정 키 없음),
  계정 전체 권한 IAM은 범위 제외. `/harness-infra-build` 진행 가능. PR
  코드 승인(`@Byuntil`, `@tkv00`)과 `infrastructure-apply` Environment
  승인은 별개로 필요.

## Objective

- 게시물 작성 시 이미지를 함께 업로드하는 기능을 지원하기 위해, 개발/테스트
  환경에서 사용할 S3 버킷을 Terraform으로 구축한다.

## Scope

- 개발/테스트(dev) 환경용 S3 버킷 Terraform 코드 작성
- 버킷 정책: 기본 private, public access block 설정
- 버전 관리(versioning), SSE-KMS 서버 측 암호화 설정(전용 Customer-managed Key)
- S3 서버 접근 로그(전용 로그 버킷), 6개월 lifecycle 만료 규칙
- 애플리케이션이 사용할 최소 권한 IAM 정책/역할 설계
- **`infra/bootstrap`: Terraform State Backend(S3) + GitHub OIDC Provider +
  `infra-plan`/`infra-apply` Role** — 설계 단계에서 사람이 이 이슈 범위에
  포함하기로 확정(2026-08-05). 저장소 최초의 Terraform 리소스이므로
  Bootstrap 자체는 최초 1회 로컬 State로 적용하는 예외가 승인되었다.
- **`dev-s3-tester` IAM Role**: 팀원(ksj)이 이 버킷·KMS Key만 단기
  자격증명(MFA 필수, 고정 Access Key 없음)으로 테스트할 수 있도록 설계
  (사람 결정, 2026-08-05).
- Infrastructure Design Report 작성 및 검토 요청 (`/harness-infra-design`,
  `docs/reports/infrastructure/gh-63-D-1.md`)

## Explicit exclusions

- 이미지 업로드 API 엔드포인트 구현 (애플리케이션 코드)
- 운영(production) 환경 S3 버킷 구축
- CDN/CloudFront 연동
- 계정 전체 권한(Administrator급) IAM 생성 — 최소 권한 원칙과 충돌해 이
  이슈에서 제외, 필요 시 별도 이슈·ADR로 분리(사람 결정, 2026-08-05)
- 고정 Access Key를 발급하는 IAM User 생성 — AGENTS.md 4.9·12절이 금지
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| infra/** (Terraform, S3) | 인프라 실행 에이전트 | `@Byuntil`, `@tkv00` |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인했다: `TASK.md`만 변경(이번
  `task-init` 실행 결과)되어 있고 다른 미커밋 변경은 없었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- Infrastructure Design Report가 `APPROVED_FOR_BUILD` 상태로 승인됨
- `terraform fmt`, `terraform validate` 등 정적 검사 통과
- `terraform plan` 증거 생성 (apply는 실행하지 않음)
- least-privilege IAM 정책 적용
- `@Byuntil`, `@tkv00` 리뷰 요청
- apply는 기본 비활성 상태 유지, 보호된 GitHub Actions workflow에서만 실행
