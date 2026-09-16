# GitHub Issue #231 Task Contract

> Generated at: `2026-09-17T02:55:37+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `백엔드 이미지 ECR 푸시와 테스트 서버 배포 workflow`
- GitHub Issue: `#231`
- Branch: `chore/gh-231-deploy-test-server-workflow`
- Base branch: `main`

## Objective

- `.github/workflows/deploy-test-server.yml`을 신설해 `main` push와
  `workflow_dispatch`를 트리거로 백엔드 이미지를 ECR에 push하고 dev
  테스트 서버(#229)에 배포한다.
- GitHub OIDC로 #240이 만든 배포 Role을 assume해 장기 Access Key 없이
  ECR push와 SSM SendCommand를 수행한다.

## Scope

- `.github/workflows/deploy-test-server.yml` 신설.
  - 트리거: `push`(`main`), `workflow_dispatch`.
  - `Dockerfile`의 runtime 스테이지를 빌드해 commit SHA와 `latest` 두
    태그로 ECR에 push한다.
  - SSM `SendCommand`(`AWS-RunShellScript`)로 EC2에서
    `docker compose --env-file .env pull`과 `up -d`를 실행한다. SSH
    접속 경로는 만들지 않는다.
  - 같은 SSM 명령 안에서 `GET /actuator/health`를 재시도 루프로 확인하고,
    실패하면 명령 자체가 실패해 workflow가 실패한다.
  - `aws-actions/configure-aws-credentials`를 OIDC 모드로만 사용하고
    `secrets`에 AWS Access Key를 두지 않는다.
- 필요한 GitHub repository secret/variable 이름을 정하고 workflow에서
  참조한다(실제 값은 #229/#233/#237 EC2 스택이 apply된 뒤 사람이 넣는다):
  - `secrets.AWS_TEST_SERVER_DEPLOY_ROLE_ARN`(#240이 출력하는 Role ARN)
  - `vars.AWS_REGION`
  - `vars.ECR_REPOSITORY_URL`
  - `vars.TEST_SERVER_INSTANCE_ID`
- 이전 태그로 재배포하는 수동 절차를 문서화한다(자동화하지 않는다).

## Explicit exclusions

- production 배포와 무중단 배포 전략.
- Terraform 코드 변경(`infra/**`).
- 롤백 자동화. 이전 태그로 재배포하는 수동 절차만 문서화한다.
- `infrastructure-apply.yml`, `CODEOWNERS`, GitHub Ruleset 수정.
- `terraform apply` 실행.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Workflow 구현 (`.github/workflows/**`) | `tkv00` | PR 승인 |
| GitHub repository secret/variable 값 입력 | 사람 | EC2 스택 apply 이후 |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
npm run hooks:validate
python scripts/validate-workflows.py
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `npm run hooks:validate`와 `python scripts/validate-workflows.py`가
      통과한다.
- [x] workflow가 `aws-actions/configure-aws-credentials`를 OIDC 모드로만
      사용하고 `secrets`에 AWS Access Key를 참조하지 않는다.
- [x] 이미지 태그에 commit SHA가 포함되어 배포된 이미지를 commit으로
      추적할 수 있다.
- [x] 배포 후 헬스체크 단계가 존재하고 실패 시 exit code가 0이 아니다.
- [x] `infrastructure-apply.yml`, `CODEOWNERS`, GitHub Ruleset을
      수정하지 않는다.
- [x] workflow가 `terraform apply`를 실행하지 않는다.

## 참고

- 선행 이슈: #229/#233/#237(EC2·ECR·네트워크), #240(배포 IAM Role,
  `docs/reports/infrastructure/gh-240-D-4.md`)
- 이 workflow는 실제 AWS 리소스가 apply되고 GitHub repository
  secret/variable 값이 채워지기 전까지는 동작하지 않는다(Scope 참고).
