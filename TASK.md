# GitHub Issue #336 Task Contract

> Generated at: `2026-10-09T17:25:41+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[L] 필터링 시스템 — 한국어 욕설 판정 실측`
- GitHub Issue: `#336`
- Branch: `docs/gh-336-korean-profanity-measurement`
- Base branch: `main`

## Objective

판정은 `FlaggedCategoryPolicyEngine`이 OpenAI 응답의 `flagged` 값 하나로 정하고, `local-rules-v1.rules`에는 등록된
단어가 없다. OpenAI 분류에는 욕설 항목이 따로 없어 특정인을 겨냥하지 않은 한국어 욕설과 우회 표기가 ALLOW로 통과하는지
측정한 기록이 없다. 직접 만든 샘플로 실제 판정을 측정해 규칙 v2 범위를 정할 근거를 만든다.

## Scope

- `docs/experiments/korean-profanity/gh-336-samples.csv`: 특정인 겨냥 욕설, 겨냥하지 않은 욕설, 우회 표기, 오탐 후보,
  정상 문장의 다섯 묶음 샘플. 묶음마다 20개 이상이며 전부 직접 만든 문장이다. 샘플 원문도 커밋한다.
- `scripts/experiments/korean-profanity-probe.py`:
  - `UnicodeTextNormalizer`와 같은 정규화(NFKC, 제어·서식 문자 제거, 연속 공백 축소, trim) 후
    `/v1/moderations`에 `omni-moderation-latest`로 요청한다.
  - 샘플마다 `flagged`, true인 카테고리, 카테고리별 점수, 응답 `model` 값을 기록한다.
  - API 키는 환경 변수 `OPENAI_API_KEY`에서만 읽고 출력이나 파일에 남기지 않는다.
- `docs/experiments/korean-profanity/gh-336-results.csv`, `gh-336-report.md`: 묶음별 놓친 비율(욕설 묶음의
  flagged=false 비율)과 오탐 비율(정상 묶음의 flagged=true 비율), 규칙 v2 후보 목록.

## Explicit exclusions

- `local-rules-v2.rules` 작성과 `FlaggedCategoryPolicyEngine` 변경. 측정 결과를 보고 별도 이슈로 다룬다.
- `qello.filtering.production.enabled` 활성화와 배포 환경의 키 주입
- 이미지·영상 판정, 다른 공급자 비교
- 사용자 콘텐츠 사용
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 샘플 세트·측정 스크립트·결과·보고서 | 실행 에이전트 | 사용자 PR 리뷰 |
| OpenAI API 키 발급과 로컬 환경 변수 설정 | 사용자 | 해당 없음 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`4c7bcef8`)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 묶음마다 샘플이 20개 이상이고, 모든 샘플은 직접 만든 문장이다.
- `gh-336-results.csv`에 모든 샘플의 `flagged`, 카테고리별 점수, 응답 `model` 값이 있다.
- 보고서에 묶음별 놓친 비율과 오탐 비율, 측정 일시, 모델 값이 있다.
- 보고서에서 규칙 v2에 넣을 표현 후보와 OpenAI 판정에 맡길 범위가 나뉘어 있다.
- 커밋한 파일에 API 키 값이 없다(pre-commit 민감정보 검사 통과).
- `./harness check`와 `git diff --check`가 통과한다.

## Decisions

- 2026-10-09 사용자 결정: 이슈 유형은 docs, Sprint Week 10, Priority P1로 한다.
- 2026-10-09 사용자 승인: 샘플 원문을 커밋한다. 규칙 v2 파일에도 같은 단어가 들어가므로 저장소에 남는 범위는 늘지 않는다.
  직접 만든 샘플 132개에만 적용한다.
- 2026-10-09 사용자 요청: 공개 데이터로 측정을 보강한다. 사용자가 AI Hub 텍스트 윤리검증 데이터를 신청해 저장소 밖에
  내려받았다. 재배포 가능 여부를 확인하지 못했으므로 원문과 문장별 결과는 저장소 밖에 두고, 집계와 스크립트
  (`scripts/experiments/korean-profanity-aihub.py`)만 커밋한다.
