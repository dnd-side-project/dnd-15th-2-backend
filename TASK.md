# GitHub Issue #343 Task Contract

> Generated at: `2026-10-09T22:15:51+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `DeviceTokenService baseline 해시 불일치로 main 단위 테스트 실패`
- GitHub Issue: `#343`
- Branch: `fix/gh-343-device-token-baseline-hash`
- Base branch: `main`

## Objective

#341이 `DeviceTokenService`를 고쳤지만 `config/java-conventions/baseline.json`의 LEGACY 항목 JAVA-CONV-0042
(QELLO-JAVA-CTOR-001) 해시는 그대로다. 검증 스크립트가 `origin/main` blob과 해시를 비교하므로 #341 병합 뒤 main을
포함한 모든 브랜치에서 `JavaConventionBaselineTest`가 `QELLO-JAVA-BASELINE-006`으로 실패한다.

## Scope

- `src/main/java/com/dnd/qello/auth/service/DeviceTokenService.java`: 필드 대입만 하는 명시적 생성자를
  `@RequiredArgsConstructor`로 바꿔 QELLO-JAVA-CTOR-001을 해소한다. 다른 동작은 바꾸지 않는다.
- `config/java-conventions/baseline.json`: JAVA-CONV-0042 항목을 지운다. 해시만 갱신하지 않는다
  (`docs/harness/JAVA_CONVENTIONS.md` "Baseline과 예외").

## Explicit exclusions

- 다른 LEGACY 항목 정리
- LEGACY 대상을 고친 PR이 병합 전 CI를 통과하는 구조 자체(별도 이슈 후보)
- 테스트 코드 변경. 기존 테스트로 검증하므로 테스트 계획을 만들지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `DeviceTokenService` 생성자, baseline 항목 삭제 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`8461288c`)에서 분기했다.
- #342 브랜치(`feat/gh-342-operator-session-lifetime`)는 커밋을 마친 상태로 두고 왔다. 이 수정이 병합되면 다시 sync한다.

## Validation

```bash
python3 scripts/validate-java-conventions.py
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- `python3 scripts/validate-java-conventions.py`가 위반 없이 끝난다.
- `JavaConventionBaselineTest`와 `./gradlew javaConventionCheck`가 통과한다.
- `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-09 사용자 결정: main baseline 오류는 #342에 넣지 않고 별도 이슈로 먼저 고친다. type bug, Sprint Week 10,
  Priority P0, Status In Progress.
