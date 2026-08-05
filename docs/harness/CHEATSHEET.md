# Qello Harness Cheat Sheet

## 상태와 진단

```bash
./harness doctor
./harness status
./harness context
```

## 작업 시작

```bash
export GITHUB_ISSUE=42
h start --issue "$GITHUB_ISSUE" --type feat --slug direction-post
h task-init --title "방향 글 API" --replace
```

브랜치:

```text
<type>/gh-<issue>-<slug>
```

stacked 작업(다른 브랜치 위에 쌓기)은 `--base <브랜치>`를 추가한다. 기준
브랜치 확인은 `./harness base`.

## 산출물

```bash
htp DIRECTION-POST
htr DIRECTION-POST
hid AWS-BASELINE
```

생성 파일은 `gh-<issue>-<id>.md` 형식을 사용한다. 테스트 클래스 헤더의 원본
시나리오는 `TEST-PLAN-GH-<issue>-<id>` 형식을 권장한다.

## 커밋과 PR

```bash
git commit -m "방향 글 API 추가"
# feat: 방향 글 API 추가 (#42)

git commit -m "test(feed): 만료 테스트 추가"
# test(feed): 만료 테스트 추가 (#42)
```

```text
PR title: feat: add direction post API
PR body: Closes #42
```

## 검증

```bash
./harness check
./harness base
./harness sync
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## 인프라 apply

일반 작업에서는 실행하지 않는다. 보호된 수동 workflow에서 `APPLY PR-<번호>`를
입력하고 두 명의 exact-head 승인, 보호 Environment, OIDC를 모두 확인한다.
