# Claude Code Entry Point

Claude Code는 작업 시작 시 `AGENTS.md`, `TASK.md`, 대상 역할 문서를 순서대로
읽는다. 이 파일은 Claude Code 전용 진입점이며 공통 정책을 재정의하지 않는다.

## 빠른 실행

```bash
./harness doctor
source ~/.config/qello-harness/env.zsh
h status
h context
```

GitHub Issue 번호는 저장소 설정에 고정하지 않고 현재 branch에서 파생한다.
새 작업마다 `h start` 후 `h task-init`으로 `TASK.md`를 갱신한다.

프로젝트 스킬:

워크플로 (Issue → 커밋 → PR, 질문형 진행. `docs/harness/WORKFLOW_SKILLS.md`)

- `/harness-issue`: 이슈 생성, Project 필드 연결, 작업 브랜치 생성
- `/harness-commit`: 검토 목적 단위 커밋 분할
- `/harness-pr`: 로컬 검증 후 PR 생성

역할

- `/harness-test-plan`: 테스트 오케스트레이터
- `/harness-test-run`: 테스트 실행 에이전트
- `/harness-infra-design`: 인프라 오케스트레이터
- `/harness-infra-build`: 승인된 IaC 실행 에이전트
- `/harness-review`: PM/리뷰어

## 모델 선택

역할 문서의 `model_profile`은 논리 이름이다. 실제 Claude 모델과 사용량은
`agents/model-profiles.local.yml`에서 개인 또는 CI 환경에 맞게 연결한다.
저장소는 특정 API 모델 ID를 강제하지 않는다.

## 금지

- GitHub Issue 게이트 없이 구현 시작
- 승인 없이 인프라 적용 또는 프로덕션 변경
- `.env`, 토큰, 계정/서버 식별자를 대화·파일·로그에 복사
- 다른 사용자의 변경을 자동 정리하거나 되돌리기
- 테스트 실패를 숨기거나 실행하지 않은 테스트를 통과로 보고하기
