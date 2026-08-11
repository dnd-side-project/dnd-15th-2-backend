# GitHub Issue #104 Task Contract

> Generated at: `2026-08-11T16:04:02+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[A] 필터링 시스템 — Moderation release registry와 승격 (F01)`
- GitHub Issue: `#104`
- Branch: `feat/gh-104-moderation-release-registry`
- Base branch: `main`

## Objective

- 필터링 정책 구성요소(정규화 규칙, 로컬 규칙·사전, category mapping·threshold,
  고정 OpenAI snapshot)를 하나의 `moderation_release_id`로 묶어 관리하고,
  검증된 candidate만 명시적 승인으로 승격하는 registry를 만든다.

## Scope

- `filter_release`(#103) 테이블에 registry 컬럼(참조 4종·`status`·`promoted_at`)을
  추가하고 `release_promotion_history` 감사 테이블을 만드는 `V11` 마이그레이션.
- `FilterRelease` 도메인에 CANDIDATE → OFFLINE_EVALUATED → SHADOW → CANARY →
  PROMOTED 순방향 파이프라인과 PROMOTED ↔ ROLLED_BACK 전이를 추가.
- `FilterReleaseRegistryService` — candidate 생성, 단계 전환, 승격·rollback의
  유일한 쓰기 진입점. 승격 시 기존 PROMOTED release를 같은 트랜잭션에서
  ROLLED_BACK으로 내린다.
- `/admin/filtering/releases` 운영자 전용 API(조회 2종, 생성 1종, 단계 전환
  3종, 승격·rollback 각 1종). 기존 `backofficeSecurityFilterChain`
  (`hasRole("OPERATOR")`)이 인가를 맡는다.
- DB 유일성 인덱스(`uq_filter_release_single_promoted`)로 동시에 두 release가
  PROMOTED가 될 수 없음을 강제.

## Explicit exclusions

- offline evaluation dataset, 합격 기준, shadow·canary 실제 트래픽 비율,
  rollback 판단 수치 — 미결정, 별도 설계 게이트.
- production snapshot 가용성, OpenAI 계약·quota·데이터 처리 조건 재검증.
- 공통 moderation pipeline 실행(#105), 닉네임/답변 실제 연동(#106, #107) —
  이 이슈는 registry 자체만 만든다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| release registry 도메인·서비스·운영자 API·마이그레이션 | Feature executor | 상태 전이 가드, 단일 PROMOTED 불변식, 운영자 인가 경계 |

## Existing user-owned changes

- 브랜치는 F00(#103) 병합 직후 `origin/main`에서 새로 분기했다
  (`./harness start --issue 104 ...`). 분기 시점 `git status --short`는
  비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- `./gradlew test --tests "com.dnd.qello.filtering.*"` — 도메인 단위 테스트 통과.
- `./gradlew integrationTest --tests "com.dnd.qello.FilterReleaseRegistryIntegrationTest"` — 5개 통과.
- `./gradlew check` 전체 — 423개 테스트, 실패 0, 에러 0.
- `docs/api/openapi.json`은 `OpenApiSpecificationIntegrationTest`가 자동
  재생성했다(직접 편집하지 않음).

## Completion criteria

- [x] 같은 release와 입력으로 사용한 정책 구성요소를 추적할 수 있다 —
      `normalizationRef`/`localRulesetRef`/`categoryMappingRef`/`modelSnapshot`이
      release id 하나에 고정되고 변경 메서드가 없다(record, immutable).
- [x] shadow와 canary가 사용자 상태, 닉네임 지연, 예약 용량에 영향을 주지
      않는다 (`INV-REL-007`) — 이 이슈는 상태 bookkeeping만 하고 실제 트래픽
      라우팅·판정 실행에는 관여하지 않는다(그 경로는 #105/#106이 소유).
- [x] 승인 없는 alias 변경이나 자동 release 교체 경로가 존재하지 않는다
      (`INV-REL-001`, `INV-REL-008`) — "latest" alias는 참조 값 검증에서
      거절되고, 승격·rollback은 운영자가 호출하는 명시적 endpoint로만 가능하다.
      DB 유일성 인덱스가 동시 PROMOTED 두 개를 물리적으로 막는다.
- [x] `INV-REL-002`~`006`, `009`, `010`을 위반하지 않는다 — release는 정책
      구성요소를 원자적으로 묶고(002), job 생성 시점에 release를 고정하는
      구조는 F00이 이미 만들었으며(003, INV-GEN 계열), 승격은 기존 job의
      `filter_release_id`를 재귀속하지 않고(004), 요청 release와 실제 model은
      `FilterDecision.actualModel`로 별도 기록되며(005), candidate는 순방향
      파이프라인을 강제로 거친다(006). emergency migration(009, 010)은 F06
      소유이며 이 이슈는 그 전제를 깨지 않는다.
