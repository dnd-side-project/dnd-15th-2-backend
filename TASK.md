# GitHub Issue #304 Task Contract

> Generated at: `2026-10-04T18:26:47+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `ClickUp 작업 연결 Issue·PR 템플릿 정리`
- GitHub Issue: `#304`
- Branch: `chore/gh-304-clickup-work-templates`
- Base branch: `main`

## Objective

- ClickUp 명세·스프린트 산출물과 GitHub 구현·검증 증거를 양방향으로 연결한다.

## Scope

- .github/ISSUE_TEMPLATE/backend_work.yml
- .github/PULL_REQUEST_TEMPLATE.md
- docs/harness/CLICKUP_LINKAGE.md
- 현재 작업용 TASK.md

## Explicit exclusions

- 앱 코드·DB·인프라·배포·보호 규칙 변경.
- ClickUp 작업 생성·상태 변경·담당자 배정 및 팀 보드 복제.
- 다른 전용 Issue Form과 전체 하네스 정책 마이그레이션.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 템플릿·연계 문서 | 현재 Codex 작업 | 저장소 PR 리뷰 |

## Existing user-owned changes

- 별도 clone의 최신 origin/main에서 시작했으며 시작 시 변경 없음.
- 원래 사용자 체크아웃은 수정하지 않고, 기존 main의 이전 TASK.md는 현재 브랜치에 한해 갱신한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- ClickUp 실제 작업 ID·URL과 기능명세서 ID·링크, 가변 스프린트 ID가 폼에 포함된다.
- PR은 관찰 결과·검증 방법·실제 결과·증빙과 양방향 링크 확인을 기록한다.
- 기존 canonical 라벨·Issue 번호·커밋 규칙을 보존한다.
- 특정 Sprint 주차·목록 ID와 테스트 성공을 기본값으로 고정하지 않는다.
- YAML·하네스 검증 결과와 실행하지 못한 항목을 보고한다.

## Decisions

- 2026-10-04 사용자 제공 ClickUp 규칙과 템플릿 수정 요청을 범위 근거로 사용한다. 일정·스프린트는 해당 최신 규칙을 적용한다.
- 이 템플릿 정리 요청의 ClickUp 작업·기능명세서·스프린트 ID는 제공되지 않았다. 임의 생성하지 않는다.
