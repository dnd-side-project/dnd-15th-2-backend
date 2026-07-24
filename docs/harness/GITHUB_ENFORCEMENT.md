# GitHub Enforcement Setup

저장소 파일만 추가해도 GitHub 설정이 자동으로 활성화되지는 않는다. 저장소
관리자가 아래 항목을 한 번 설정해야 한다.

## 1. Default branch Ruleset

GitHub 저장소에서 `Settings → Rules → Rulesets → New branch ruleset`:

1. Target branch: default branch
2. Require a pull request before merging
3. Require approvals
4. Require review from Code Owners
5. Dismiss stale approvals when new commits are pushed
6. Require status checks:
   - `Harness Policy / policy`
   - `Harness Policy / test`
   - `Label Policy / classify-pull-request`
   - 인프라 변경 시 `Infrastructure Static Checks / safety`
7. Block force pushes and deletions

두 백엔드의 승인을 모든 PR에 적용해도 된다면 required approvals를 `2`로
설정한다. 경로별 승인 수를 기본 Ruleset만으로 다르게 강제하기 어렵다.

## 2. Infrastructure approval reality

`.github/CODEOWNERS`는 `@Byuntil`, `@tkv00`에게 리뷰를 요청하지만 두 사람의
승인을 모두 보장하지 않는다.

이 저장소의 apply workflow는 GitHub Review API를 확인해 두 사람이 현재 PR
head를 승인했는지 검증한다. 새 커밋이 push되면 예전 승인은 인정하지 않는다.

## 3. Protected Environment

`Settings → Environments → New environment`:

- Name: `infrastructure-apply`
- Required reviewers: 백엔드 승인 책임자
- Prevent self-review: 활성화
- Deployment branches: 보호된 정책에 맞게 제한

Environment 승인 하나만으로 두 명 승인을 대체하지 않는다. workflow 내부의
exact-head 두 명 승인 검사가 함께 필요하다.

## 4. Repository variables

`Settings → Secrets and variables → Actions → Variables`:

| Variable | Purpose |
| --- | --- |
| `INFRA_APPLY_ENABLED` | 평소에는 만들지 않거나 `false`; 적용 창에서만 `true` |
| `AWS_REGION` | 승인된 AWS region |
| `AWS_INFRA_DEPLOY_ROLE_ARN` | GitHub OIDC가 assume할 배포 role |

실제 값은 이 문서, Issue, PR, 로그에 복사하지 않는다.

## 5. AWS OIDC

- GitHub Actions OIDC provider를 사용한다.
- 장기 access key를 GitHub Secret으로 저장하지 않는다.
- 신뢰 정책은 저장소, branch/environment, workflow 조건으로 제한한다.
- 배포 role은 IaC에 필요한 최소 action/resource만 허용한다.
- plan과 runtime role을 가능하면 분리한다.

## 6. Apply 실행

`Actions → Infrastructure Apply → Run workflow`에서 입력한다.

```text
Jira: <JIRA-KEY>
PR: 123
Terraform dir: infra/environments/dev
Confirmation: APPLY <JIRA-KEY> PR-<PR-NUMBER>
```

다음 중 하나라도 없으면 workflow가 실패해야 한다.

- `INFRA_APPLY_ENABLED == true`
- 열린 non-draft PR
- 두 명의 정확한 head 승인
- `infra/` 아래 유효한 Terraform root
- 정확한 확인 문구
- protected Environment 승인
- OIDC role

## 7. Jira sync

Jira 연동 workflow가 사용하는 `JIRA_BASE_URL`, `JIRA_EMAIL`,
`JIRA_API_TOKEN`은 GitHub Actions Secret으로만 관리한다. 이 하네스의
preflight는 값을 출력하지 않고 명백한 노출 패턴만 보고한다.

## 8. Label policy

라벨 정의와 자동 분류 규칙은
[`LABELS.md`](./LABELS.md)와 `.github/label-catalog.json`을 기준으로 한다.

- 모든 Issue와 PR은 `type: *` 라벨을 정확히 하나 가져야 한다.
- PR의 type은 branch 접두사에서 자동 산출한다.
- Jira가 우선순위와 스프린트의 원본이므로 GitHub 우선순위 라벨은 만들지
  않는다.
- label sync는 기존 라벨을 삭제하지 않고 canonical 라벨만 생성·갱신한다.

저장소 파일만 추가해서는 병합 차단이 활성화되지 않는다. Ruleset의 required
status checks에 `Label Policy / classify-pull-request`를 추가해야 한다.
