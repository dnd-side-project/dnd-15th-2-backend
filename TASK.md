# GitHub Issue #368 Task Contract

> Generated at: `2026-11-20T23:38:40+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `런타임 이미지 Temurin 21.0.13에서 emSign 루트 삭제 실패로 이미지 빌드 중단`
- GitHub Issue: `#368`
- Branch: `fix/gh-368-runtime-emsign-cacerts`
- Base branch: `main`

## Objective

- `eclipse-temurin:21-jre-alpine` 태그가 Temurin 21.0.13으로 바뀐 뒤 `Dockerfile` runtime 단계의 emSign 루트 삭제(#359)가 없는 alias에서 실패해 이미지 빌드와 test-server 배포가 막힌 상태를 해소한다.
- 21.0.13 cacerts에는 `cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]` 하나만 남아 있다(2026-11-20 로컬 확인). #359의 목표(emSign 루트 4개 미신뢰)는 유지한다.

## Scope

- `Dockerfile` runtime 단계: 4개 alias마다 cacerts에 있을 때만 삭제하고, 마지막에 4개 alias가 하나도 남지 않았음을 검사해 남아 있으면 빌드를 실패시킨다.
- `Dockerfile` 주석: CVE-2026-91403과 #359 근거를 유지하고, 삭제 조건을 21.0.13 이후 실제 상태(3개는 JDK에서 제거, g1은 남음)와 재검토 조건에 맞게 고친다.
- 검증 증거는 PR 본문에 기록한다.

## Explicit exclusions

- base 이미지 버전·digest 고정, PR CI의 Docker 빌드 검사 추가, 다른 CA·JDK 보안 설정 변경.
- `Dockerfile` 밖의 파일 변경(`TASK.md` 제외), `infra/**`, `.github/workflows/**`.
- 커밋·push·PR은 사용자 승인 후에만 한다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md` | 오케스트레이터 | 사용자 확인 |
| `Dockerfile` runtime 단계와 주석 | 실행 에이전트 | 오케스트레이터 독립 검증 후 사용자 PR 리뷰 |

## Existing user-owned changes

- 별도 worktree `dnd-15th-2-backend-gh368`에서 최신 `origin/main`으로 시작했고, 시작 시 `git status --short` 결과는 비어 있었다.
- #364 작업 폴더(`infra/gh-364-test-server-push-secrets`)는 수정하지 않는다.

## Validation

```bash
docker build -t qello-gh368-check .
docker run --rm --entrypoint sh qello-gh368-check -c 'java -version; keytool -list -cacerts -storepass changeit | grep -ci emsign'
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- Temurin 21.0.13 base 이미지로 `docker build`가 성공한다.
- 빌드된 이미지의 `keytool -list -cacerts -storepass changeit | grep -ci emsign` 결과가 0이다.
- 4개 중 하나라도 남으면 빌드가 실패함을 재현하거나 검사 로직을 PR에 기록한다.
- 머지 후 main push의 `deploy-test-server` 실행에서 `build-and-push`가 통과한다.
- `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`가 통과한다.
