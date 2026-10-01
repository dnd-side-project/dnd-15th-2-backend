# GitHub Issue #287 Task Contract

> Generated at: `2026-10-01T14:25:25+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `닉네임·답변 moderation placeholder를 실제 구현으로 교체`
- GitHub Issue: `#287`
- Branch: `feat/gh-287-moderation-placeholder-replacement`
- Base branch: `main`

## Objective

닉네임·답변 moderation 경로의 OpenAI 주 판정기와 config 조립은 구현돼 있으나(#168),
`PassthroughTextNormalizer`(trim만 수행), `NoMatchLocalRuleEngine`(항상 noMatch),
`UnavailableSecondaryModerationClient`(즉시 예외)가 placeholder다.
이 세 placeholder를 실제 구현으로 교체하고 `qello.filtering.production.enabled=true`에서
두 경로가 실제 판정으로 끝까지 동작하는지 검증한다.

## Scope

- `SecondaryModerationClient` 실제 구현체를 추가하고 `NicknameModerationGateConfig`의
  `UnavailableSecondaryModerationClient` 조립을 교체한다. 공급자는 미결정(UNKNOWN)이며
  선정 결과가 선행 조건이다. 주 판정기와 실행 자원·장애 영역을 공유하지 않는다(`INV-NICK-004`).
- `TextNormalizer` 실제 구현체로 `PassthroughTextNormalizer`를 교체한다. 정규화 규칙은
  구현 전에 설계로 확정한다(미결정).
- `LocalRuleEngine` 실제 구현체로 `NoMatchLocalRuleEngine`을 교체한다. 규칙 출처와 갱신
  방식은 미결정이며 규칙 원문을 로그·metric tag에 남기지 않는다(`INV-CMP-001`, `INV-CMP-002`).
- `NicknameModerationGateConfig`와 `AnswerModerationExecutionConfig`의 placeholder 조립을
  새 구현체로 바꾼다.
- `production.enabled=true`에서 닉네임 게이트와 답변 pipeline의 기동·판정 경로를 점검하고,
  `docs/filtering-production-gate.md` 3절 미배선 항목 중 이번 교체로 영향받는 것만 목록화한다.

## Design decisions

- 정규화 `v1`(확정 2026-10-01): NFKC, zero-width·제어 문자 제거, 연속 공백 축소, trim. 구분자 제거와
  동형 문자 처리는 정규화가 아니라 로컬 규칙 엔진 내부 비교용 접기에서만 한다. null·정규화 후 빈 문자열과
  알 수 없는 `normalizationRef`는 `FilteringException`으로 fail-closed 한다.
- 로컬 규칙(확정 2026-10-01): `localRulesetRef`로 classpath 리소스를 고르는 엔진만 구현한다. 운영 규칙 목록은
  비워 두고(매칭 없음), 목록 추가는 OpenAI 판정 결과를 본 뒤 후속 작업으로 한다. 로그·metric에는 `ruleId`만 남긴다.
- 보조 판정기(확정 2026-10-01): #287에 포함하고 공급자 선정을 기다린다. 공급자가 정해지기 전에는
  `UnavailableSecondaryModerationClient` 교체 항목이 완료되지 않는다.

## Explicit exclusions

- 공급자 DPA·데이터 거주지·보관 정책 등 `filtering-production-gate.md` 2절의 사람 확인 항목
- 스케줄러 배선, `SlackNotifier` 실제 구현체, metric exporter와 경보 규칙
- `OpenAiModerationProviderClient`의 호출·재시도·실패 분류 로직 변경
- `FlaggedCategoryPolicyEngine`의 정책 변경
- 이미지·미디어 moderation
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| TextNormalizer·LocalRuleEngine 구현과 config 교체 | 실행 에이전트 | 설계 확정 후 사용자 PR 리뷰 |
| SecondaryModerationClient 구현 | 실행 에이전트 | 공급자 선정(사람 결정) 후 사용자 PR 리뷰 |
| 신규 단위 테스트 | 실행 에이전트 | `/harness-test-plan` 승인 후 작성 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했고 `TASK.md`만 `task-init`으로 수정되었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 보조 판정기가 주 판정기 timeout/error에만 호출되고 명시적 BLOCK을 뒤집지 못한다(`NicknameSyncModerationGateTest` 통과).
- 보조 판정기 자체 timeout/error 시 `REJECTED(UNAVAILABLE)`로 fail-closed 한다(신규 단위 테스트).
- `TextNormalizer`·`LocalRuleEngine` 신규 단위 테스트가 통과하고 빈 입력·null·비정상 유니코드에서 ALLOW로 새지 않는다.
- 세 placeholder가 프로덕션 조립에서 참조되지 않는다(`grep` 확인).
- API 키와 사용자 콘텐츠 원문이 로그·예외 메시지·metric tag에 없다.
- `NicknameModerationGateConfigTest`, `AnswerModerationExecutionConfigTest`, `ModerationPipelineIntegrationTest`가 통과한다.
- `./harness check`와 `./harness pr-ready --project-tests`가 통과한다.
