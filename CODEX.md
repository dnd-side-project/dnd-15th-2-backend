# Codex Entry Point

Codex는 먼저 `AGENTS.md`, `TASK.md`, 해당 `agents/` 역할 문서를 읽는다.
저장소의 Codex 스킬은 `.agents/skills/`에 있다.

- `$harness-test-plan`
- `$harness-test-run`
- `$harness-infra-design`
- `$harness-infra-build`
- `$harness-review`

## 시작 명령

```bash
export GITHUB_ISSUE=42
./harness doctor
./harness status
./harness start --issue "$GITHUB_ISSUE" --type test --slug direction-feed
./harness task-init --title "방향 피드 테스트" --replace
```

GitHub Issue 번호는 작업마다 입력하는 예시 변수다. 이후 명령은 생성된
`<type>/gh-<issue>-<slug>` branch에서 현재 문맥을 파생한다.

## 운용 원칙

- 오케스트레이터는 계획과 계약만 확정한다.
- 실행 에이전트는 `TASK.md`의 소유 파일만 수정한다.
- 모델은 `agents/model-profiles.local.yml`에 정의된 논리 프로필을 사용한다.
- 인프라 적용, 프로덕션 변경, 광범위한 파일 변경은 사람의 명시적 확인이
  있어야 한다.
- 작업 결과는 테스트/설계 보고서와 함께 PR에 남긴다.
