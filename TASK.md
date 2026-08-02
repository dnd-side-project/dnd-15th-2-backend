# GitHub Issue #6 Task Contract

> Created at: `2026-08-03T00:00:00+09:00`
>
> 저장소 전역 정책은 `AGENTS.md`를 따른다.

## Work gate

- Title: `Qello 프로젝트 전환 및 GitHub 기반 개발 워크플로 구성`
- GitHub Issue: `#6`
- Branch: `chore/gh-6-qello-project-migration`

## Objective

- 기존 프로젝트명과 Jira 기반 저장소를 Qello/GitHub Issues·Projects 기반으로
  전환한다.
- 첨부 제품 기능과 콘텐츠 안전 기능을 5~8주차 백엔드·인프라 실행 계획으로
  구성한다.

## Scope

- 애플리케이션, 빌드, 로컬 도구의 기존 프로젝트명을 Qello로 교체
- Jira workflow, 필드, 검증, 템플릿 제거
- GitHub Issue 기반 branch/commit/PR 규칙과 Husky·CI 동기화
- README 및 하네스 문서 갱신
- canonical label policy 검증
- 기능 F01~F09와 텍스트·이미지 안전 필터를 반영한 로드맵 작성
- GitHub Project에 5~8주차 스프린트와 draft item 백로그 설정

## Explicit exclusions

- 제품 API 및 DB 스키마 구현
- AWS 리소스 생성·변경, Terraform apply, 배포, 프로덕션 변경
- GitHub Ruleset 또는 보호 Environment가 자동 활성화되었다는 주장
- Secret, 계정 식별자, 토큰, `.env` 값 기록

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 저장소 규칙·문서·GitHub Project | 현재 작업 에이전트 | Backend owners |
| 제품 백로그 | PM/reviewer | Backend owners |
| 인프라 apply | Human operator only | `@Byuntil`, `@tkv00` |

## Existing user-owned changes

- 작업 시작 시 worktree는 clean 상태였다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 기존 프로젝트명과 Jira 런타임 의존이 저장소에서 제거된다.
- [x] 새 branch/commit/PR 규칙을 Husky와 CI가 검사한다.
- [x] README에 팀 규칙이 명시된다.
- [x] Qello 5~8주차 백엔드·인프라 draft item이 GitHub Project에 배치된다.
- [x] 비속어·선정성·폭력성 필터 요구사항이 계획에 포함된다.
- [x] 모든 기본 검증이 통과한다.
