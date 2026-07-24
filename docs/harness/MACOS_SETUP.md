# MacBook Setup

## 1. 전제

- macOS
- 저장소를 로컬에 clone할 권한
- GitHub 조직과 Jira 프로젝트 접근
- Java 21 기반 Spring Boot 개발

먼저 Xcode Command Line Tools를 설치한다.

```bash
xcode-select --install
```

[Homebrew 공식 안내](https://docs.brew.sh/Installation)에 따라 Homebrew를
설치한 뒤 저장소 루트에서 점검한다.

```bash
./scripts/setup-macos.sh
```

이 명령은 기본적으로 아무것도 설치하지 않는다.

## 2. 개발 도구 설치

```bash
./scripts/setup-macos.sh --install
```

설치 범위는 `Brewfile`에서 검토할 수 있다.

- Git, GitHub CLI
- Java 21
- Node.js, Python
- jq, ShellCheck
- AWS CLI, Terraform

저장소 의존성과 Git Hook을 설치한다.

```bash
npm ci
npm run hooks:validate
```

`npm ci`의 `prepare` 단계가 Husky를 설치한다. Hook 파일은 저장소의
`.husky/`에서 관리한다.

Claude Code와 Codex CLI까지 설치하려면 명시적으로 선택한다.

```bash
./scripts/setup-macos.sh --install --install-agents --install-hooks
```

이 명령은 공식 npm 패키지를 설치하지만 로그인하지 않는다. 설치 전 팀 정책과
패키지 출처를 검토한다.

## 3. 인증

인증은 사람이 직접 진행한다.

```bash
gh auth login
claude doctor
codex --login
```

인증 출력과 토큰을 문서, PR, 채팅에 붙이지 않는다.

## 4. 단축키 설치

먼저 변경 내용을 미리 본다.

```bash
./scripts/install-shortcuts.zsh
```

확인 후 설치한다.

```bash
./scripts/install-shortcuts.zsh --install
exec zsh
```

스크립트는 `~/.config/miri-harness/env.zsh`를 만들고 `.zshrc`를 timestamp가
붙은 파일로 백업한다. 같은 관리 블록을 중복 추가하지 않는다.

점검:

```bash
hd
hs
hctx
hcheat
npm run hooks:validate
```

새 작업에서는 Jira 키를 고정 설정하지 않고 시작할 때 전달한다.

```bash
export JIRA_KEY=PAY-314
export GITHUB_ISSUE=42
h start --jira "$JIRA_KEY" --issue "$GITHUB_ISSUE" \
  --type feat --slug refund-policy --confirm-jira-linked
h task-init --title "환불 정책 구현" --replace
```

이후 `hctx`는 현재 branch에서 Jira 키와 Issue 번호를 다시 계산해 보여준다.

## 5. 제거

1. `~/.zshrc`에서 `miri harness` 시작/끝 사이 블록을 제거한다.
2. `~/.config/miri-harness`를 삭제한다.
3. 필요하면 생성된 `.zshrc.miri-harness.*.bak`으로 복구한다.

패키지 제거는 다른 프로젝트가 사용할 수 있으므로 자동화하지 않는다.

Husky만 비활성화해야 하는 긴급 상황에서는 공식 `HUSKY=0` 환경 변수를 사용할
수 있지만 일상 개발 기본값으로 설정하지 않는다. 우회한 작업은 PR에 사유와
수동 검증 결과를 남긴다.

## 6. Apple Silicon과 Intel

스크립트는 Homebrew가 노출한 PATH를 사용하며 `/opt/homebrew` 또는
`/usr/local`을 하드코딩하지 않는다. `java -version`, `terraform version`,
`gh auth status` 결과가 예상과 다르면 `hd` 결과를 기준으로 PATH를 먼저
점검한다.
