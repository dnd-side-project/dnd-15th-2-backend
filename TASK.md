# GitHub Issue #196 Task Contract

> Generated at: `2026-08-23T21:59:50+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `OpenAPI 문서 GitHub Pages 자동 배포`
- GitHub Issue: `#196`
- Branch: `ci/gh-196-openapi-pages`
- Base branch: `main`
- 선행 확인: `.github/workflows/harness-policy.yml`의 `sync-api-docs` job이
  이미 PR마다 `docs/api/openapi.json`을 재생성·재커밋하고 있음을 코드로
  확인했다 — 이 이슈는 그 산출물을 GitHub Pages로 "배포"하는 부분만
  담당한다.
- 저장소는 public이라 GitHub Pages(Actions 소스)를 별도 유료 플랜 없이
  쓸 수 있다(`gh repo view --json isPrivate` 확인 완료).

## Objective

`docs/api/openapi.json`은 항상 최신이지만 JSON 파일로만 존재해 팀원이
브라우저에서 API 구조를 훑어볼 방법이 없다. `main`에 그 파일이 바뀌어
push될 때 Swagger UI 정적 페이지를 GitHub Pages에 자동 배포해 팀원이
링크 하나로 API 문서를 볼 수 있게 한다.

## Scope

1. **`docs/api/index.html`** — CDN(`swagger-ui-dist`)으로 로드하는 정적
   Swagger UI 한 장. 같은 디렉터리의 `openapi.json`을 상대 경로로
   가리킨다. 별도 빌드 도구·의존성을 추가하지 않는다.
2. **`.github/workflows/deploy-api-docs.yml`** 신규:
   - 트리거: `push` to `main`(`paths: docs/api/**`) + `workflow_dispatch`.
   - `actions/configure-pages` → `actions/upload-pages-artifact`(경로
     `docs/api`) → `actions/deploy-pages` 표준 3단계 패턴.
   - `permissions: contents: read, pages: write, id-token: write`.
   - `concurrency` 그룹으로 동시 배포 경합 방지.
   - `scripts/validate-workflows.py`가 요구하는 `name:`/`on:`/
     `permissions:`/`jobs:` 마커, 탭 문자 금지, `continue-on-error: true`
     금지를 모두 만족해야 한다(기존 workflow와 동일 제약).
3. **저장소 Settings → Pages → Source를 "GitHub Actions"로 전환** — 코드
   변경이 아닌 저장소 설정이라 PR 밖에서 사람이 직접 수행해야 한다. 이
   설정 전에는 workflow가 실행돼도 배포가 완료되지 않는다는 점을 PR
   본문에 명시한다.

## Explicit exclusions

- `docs/api/openapi.json` 내용 자체의 설명·오류 응답 보강 —
  `harness-api-docs` 스킬의 별도 작업 범위.
- 비공개 접근이 필요한 문서 호스팅 — public GitHub Pages만 다룬다.
- `docs/api/**` 외 다른 정적 사이트(예: 전체 프로젝트 문서 사이트) —
  이번 이슈는 API 문서 한 페이지로 범위를 좁힌다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `docs/api/index.html`, `.github/workflows/deploy-api-docs.yml` | Feature executor | `scripts/validate-workflows.py` 통과 확인, `docs/api/**` 외 경로에서는 workflow가 트리거되지 않는지 `paths` 필터 리뷰, Pages Source 전환이 PR 본문에 명시됐는지 확인 |

## Existing user-owned changes

- `git status --short` 결과 없음(clean). `origin/main`에서 `./harness
  start`로 새로 분기했다.

## Validation

```bash
python3 scripts/validate-workflows.py
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] `docs/api/index.html`이 `docs/api/openapi.json`을 상대 경로로
      로드하는 Swagger UI 페이지를 렌더링한다(로컬에서 정적 서버로 열어
      확인).
- [ ] `.github/workflows/deploy-api-docs.yml`이
      `python3 scripts/validate-workflows.py`를 통과한다.
- [ ] workflow의 `on.push.paths`가 `docs/api/**`로 좁혀져 있어 무관한
      `main` push에서는 실행되지 않는다.
- [ ] `./harness check`, `./harness pr-ready --project-tests` 통과.
- [ ] PR 본문에 "머지 후 Settings → Pages → Source를 GitHub Actions로
      전환해야 실제 배포가 완료된다"는 후속 조치를 명시한다.
