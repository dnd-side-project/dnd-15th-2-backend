# GitHub Issue #292 Task Contract

> Generated at: `2026-09-30T23:18:50+09:00`
>
> 현재 작업 브랜치의 계약이며 저장소 전역 정책은 `AGENTS.md`를 따른다.

## Work gate

- Title: `Repo Map에서 실험용 Codex 실행기 책임 분리`
- GitHub Issue: `#292` (GitHub Project draft에서 변환)
- Task ID: `GH-292`
- Branch: `chore/gh-292-repo-map-runner-separation`
- Base branch: `main` (`origin/main`에서 분기)
- Design ID: 해당 없음 (인프라 변경 없음)
- 승인 근거: 현재 사용자 요청의 최소 실행기 제거, 관련 테스트·필수 검사 및 한국어 PR 생성 지시. 이전 작업의 병합 승인을 이 작업의 승인으로 사용하지 않는다.

## Objective

backend에는 Repo Map 생성·검사·조회와 선택형 B 검색 지침만 유지한다.
실험 작업 등록·조건 배정·세션 연결·측정은 harness-delta의 책임으로 구분한다.

## Scope

- `scripts/repo-map/session.py`와 전용 `HarnessSessionToolTest.java`를 제거한다.
- `docs/harness/REPO_MAP_SEARCH.md`를 실행기와 독립된 선택형 B 검색 지침으로 정리한다.
- `scripts/repo-map/README.md`에서 선택형 지침을 연결한다.
- `run.py`, `RepoMap.java`, `RepoMapToolTest.java`와 기존 테스트 계획·보고서는 유지한다.
- 공통 정책에 B가 자동 적용된 변경이 있는지 Git diff와 현재 참조를 확인한다.

## Explicit exclusions

- harness-delta로 실행기 이전, 새 배정·수집·범용 실행기·Docker 기능·버전 지원 개발.
- live 모델 호출, 새 A/B 실험, 애플리케이션·DB·인프라 변경과 배포.
- 기존 실험 기록·원본 이력의 수정·삭제 또는 소스 파생 자료의 PR 첨부.
- 병합, auto-merge, main 직접 수정, 작업 worktree 조기 보관.
- Secret, 계정 식별자, 토큰, `.env` 값 기록.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 실행기·전용 테스트 제거, 선택형 지침, 작업 계약 | 실행 에이전트 | 정확한 최종 diff 독립 리뷰 |
| 필수 검사·검증 보고·한국어 PR | 오케스트레이터 | 사용자 PR 리뷰 |
| 병합 결정 | 사용자 | 명시적 병합 지시 필요 |

## Existing user-owned changes

- 지정 worktree 시작 시 `git status --short`는 오케스트레이터가 생성한 `TASK.md`만 수정 상태였다.
- 원래 checkout과 다른 사용자의 변경은 수정하지 않는다.

## Validation

현재 요청에서 승인한 범위는 기존 테스트 실행과 필수 검사다. 새 동작이나 테스트는 추가하지 않는다.
기존 Repo Map 테스트는 생성·freshness·조회·클래스 그룹·구문 한계와 실패 처리를 검증한다.
실행기 전용 테스트는 기능 제거와 함께 삭제하고 원본 테스트 계획·보고서는 역사적 증거로 보존한다.
애플리케이션, DB, 트랜잭션, 동시 요청, 외부 API, 인프라 동작 변경은 없다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests com.dnd.qello.harness.RepoMapToolTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

필수 검사의 실제 결과와 실행할 수 없는 항목은 PR에 구분해서 보고한다.

## Common policy audit

- `git diff --name-status ac38168..origin/main`에서 기존 Repo Map 변경은 도구·선택형 문서·전용 테스트·당시 TASK·테스트 계획/보고서에 한정되어 있었다.
- `AGENTS.md`, `CLAUDE.md`, `.agents/`, `agents/` 및 그 외 공통 파일 변경은 없었다. 공통 정책에 B를 자동 적용한 변경은 발견되지 않았다.
- Repo Map 실행기 참조는 삭제 대상과 해당 사용 문서 및 기존 테스트 계획/보고서에 한정되어 있었다. 역사적 계획/보고서의 참조는 수정하지 않는다.
- 기존 `scripts/experiments/codex-agents-eval.zsh`와 관련 2026-09-10 계획은 Repo Map 도입 전 별개 실험으로 이번 범위 밖에 두고 보존한다.
- 기존 A/common 정책은 변경하지 않고 B의 명시적 선택 조건을 문서에 유지한다.

## Completion criteria

- backend에서 실행기·제품 버전 정책·세션 실행 예시가 제거되고 Repo Map 도구가 유지된다.
- 기존 overlay의 검색 우선·필요 원문 확인·검색 확대·LSP 선택 사용·구문 한계·전체 인덱스 미주입 원칙이 선택형 문서에 보존된다.
- 관련 테스트, 필수 검사와 정확한 최종 diff의 독립 리뷰를 완료하고 한국어 PR을 사용자에게 제공한다.
- 독립 리뷰를 사용자 리뷰로 취급하지 않으며 사용자 리뷰 및 명시적인 병합 지시 전에는 병합하지 않는다.

## Risks and recovery

기존 실행기 CLI는 더 이상 사용할 수 없다. 실행기 원본은 Git 이력에 남고 실험 자료는 기존 로컬 기록에 보존된다.
이 변경은 harness-delta 실행기의 구현이나 실험 운영 준비 완료를 의미하지 않는다.
필요한 복구는 별도 검토를 거친 이 PR의 revert로 수행하며 기존 이력을 재작성하지 않는다.
기존 Docker 운영 점검은 부분 사용량·4세션 실행 증거이며 사람 품질 평가는 미완료다. B 채택 근거로 사용하지 않는다.
