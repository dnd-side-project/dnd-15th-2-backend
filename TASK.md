# GitHub Issue #95 Task Contract

> Generated at: `2026-08-10T19:56:27+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신자별 거리 band 파생`
- GitHub Issue: `#95`
- Branch: `fix/gh-95-distance-band-per-recipient`
- Base branch: `main`

## Objective

- DirectionPostService.send()가 SendCommand.distanceBand 하나를 모든 수신자에게
  복사하지 않고, 각 DirectionCandidate.distanceMeters에서 수신자별
  distance_band를 파생하도록 수정한다. 수신함 카드와 답변 목록은 현재 보는 사람의
  질문 원점까지 거리 snapshot을 기준으로 10km 하한 노출 규칙을 적용한다.

## Scope

- 거리 기반 band 파생 정책과 후보별 PostRecipient 저장
- SendCommand의 호출자 band 주입 차단 또는 호환 필드 무시
- 3km·900km 후보의 개별 band 저장 통합 테스트
- 9,999m·10,000m·10,001m 카드 노출 회귀 확인
- 답변 작성자와 다른 조회자의 질문 원점 거리로 답변 표시되는지 통합 테스트
- 기존 DB schema 변경 없이 동작하는지 확인

## Explicit exclusions

- 10km 미만의 제품 문구를 재설계하거나 다른 거리 구간을 추가하지 않는다.
- 기존 운영 행 백필과 Flyway migration은 별도 결정 없이는 수행하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 방향 발송·거리 band·수신함 카드 | Feature executor | 후보별 snapshot, 개인정보 노출 경계, idempotency |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과를 확인하고 여기에 기록한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 같은 발송의 후보별 distance_band가 실제 거리로 분리된다.
- [ ] 호출자가 전달한 임의 band 문자열이 저장 결과를 결정하지 않는다.
- [ ] 10km 하한 경계 카드 노출이 정확히 상호 배타적이다.
- [ ] 테스트 클래스에 @DisplayName과 ISO 8601 생성 시각/source scenario가 있다.
- [ ] 테스트 실행 결과와 미검증 범위를 보고서에 기록한다.
