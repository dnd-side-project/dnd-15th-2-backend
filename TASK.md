# MIR-83 Task Contract

> Generated at: `2026-07-24T19:19:07+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `CodeRabbit 코드 리뷰 설정`
- Jira: `MIR-83`
- Parent Jira: `MIR-66`
- GitHub Issue: `#3`
- Branch: `chore/MIR-83-gh-3-coderabbit-review-setup`

## Objective

- 기존 하네스와 GitHub Actions의 형식 및 정적 검증을 중복하지 않고,
  CodeRabbit이 의미 기반 코드 리뷰에 집중하도록 저장소 설정을 추가한다.

## Scope

- 저장소 루트에 버전 관리되는 `.coderabbit.yaml`을 추가한다.
- draft가 아닌 PR을 대상으로 자동 리뷰를 활성화한다.
- 운영 코드는 도메인 정확성, 트랜잭션과 동시성, 인증과 권한, API 호환성을
  중심으로 리뷰한다.
- 테스트 코드는 중요한 동작, 실패 경로, 경계 조건, 동시성 위험을 충분히
  보호하는지 중심으로 리뷰한다.
- 초기 설정은 짧게 유지하고 운영 결과에 따라 후속 PR로 조정할 수 있게 한다.

## Explicit exclusions

- branch, commit, PR 형식과 기존 하네스 정책을 CodeRabbit에서 재구현하지 않는다.
- Jira 연동과 CodeRabbit 유료 기능은 이번 작업에서 활성화하지 않는다.
- 제품 API, 데이터베이스 스키마, 애플리케이션 동작을 변경하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| CodeRabbit repository configuration | Backend | Backend owners |
| GitHub App installation and permissions | Repository admin | Backend owners |

## Existing user-owned changes

- `.gitignore`

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- `.coderabbit.yaml`이 CodeRabbit v2 스키마를 참조한다.
- 자동 리뷰는 활성화되고 draft PR은 제외된다.
- 리뷰 지침은 하네스가 검증하는 형식 규칙을 반복하지 않는다.
- 운영 코드와 테스트 코드의 의미 기반 리뷰 기준이 분리되어 있다.
- 기본 하네스와 프로젝트 테스트가 통과한다.
- GitHub App 설치 여부와 남은 관리자 작업이 명확히 보고된다.
