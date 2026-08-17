# GitHub Issue #124 Task Contract

> Generated at: `2026-08-16T14:09:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신함·열람·넘김 API`
- GitHub Issue: `#124`
- Branch: `feat/gh-124-direction-inbox-api`
- Base branch: `main`

## Objective

- 수신자가 확정된 방향 질문글을 인증된 수신함 API에서 목록·상세로 조회하고,
  열람과 넘김 요청·되돌리기 상태를 수신 자격 및 동시 전이 규칙을 지키며
  안전하게 변경할 수 있게 한다.

## Scope

1. `GET /api/v1/direction/inbox` 수신함 목록·방향 칩 조회 API.
2. `GET /api/v1/direction/inbox/{postRecipientId}` 상세 조회와 최초 열람
   `OPENED` 전이.
3. `PUT /api/v1/direction/inbox/{postRecipientId}/skip` 넘김 요청 API.
4. `DELETE /api/v1/direction/inbox/{postRecipientId}/skip` 유예 내 되돌리기 API.
5. ACTIVE USER 계정, 수신자 소유권, 질문글 상태·삭제·만료, 양방향 활성
   차단과 `PostRecipient` 상태 스코프 검증.
6. 열람·넘김·되돌리기와 답변·차단·만료 전이 사이의 stale write 방지를 위한
   행 잠금 또는 이전 상태 조건부 전이.
7. 반복 넘김 요청이 최초 `skip_requested_at`과 유예 종료 시각을 연장하지 않는
   멱등 계약.
8. `InboxQueryService`, 방향 칩 집계와 기존 `PostRecipient` 도메인 전이 재사용.
9. `docs/api/openapi.json` 갱신.
10. 정식 테스트 계획
    `TEST-PLAN-GH-124-INBOX-READ-SKIP-API`에 따른 JUnit 5 단위·MockMvc·
    PostgreSQL/PostGIS 통합·동시성 테스트와 테스트 보고서.

## Explicit exclusions

- 답변 작성·공개 endpoint와 답변 moderation 흐름.
- 방향 칩 집계 알고리즘 또는 지도·방향 구간 정책 변경.
- 만료·넘김 확정 sweep 실행기와 슬롯 해제(`#126`).
- `SKIP_CONFIRMATION_DUE` Outbox 생산·취소·소비. #124는 기존 batch 조회 기반의
  후속 #126 실행 계약을 변경하지 않는다.
- FCM/APNs 등 외부 푸시 Provider와 알림 fan-out 변경.
- Flyway migration과 운영 데이터 백필. 구현 중 스키마 변경 필요성이 발견되면
  범위를 임의로 넓히지 않고 별도 승인을 받는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 수신함 권한·상태 전이 application/persistence 경계 | Feature executor | 양방향 차단, 시간 경계, 행 잠금·조건부 전이, 슬롯 불변식 리뷰 |
| Controller·ApiSpec·응답 DTO·OpenAPI | API executor | 인증, HTTP 상태·오류 코드, 응답 privacy와 API 문서 리뷰 |
| 단위·MockMvc·PostgreSQL/PostGIS·동시성 테스트 | Test executor | 계획 시나리오 추적성, 픽스처 격리, 실패 판정 리뷰 |
| 전체 변경 및 검증 증거 | Independent verifier | 구현 설명이 아닌 diff·실행 결과 기반 독립 검증 |

## Existing user-owned changes

- 작업 시작 시 `main`의 `git status --short`는 비어 있었다.
- `main`과 `origin/main`은 `dcfec08`로 일치했고, 최신 `origin/main`에서
  `feat/gh-124-direction-inbox-api`를 생성했다.
- #124 브랜치 생성 전에 보존해야 할 기존 사용자 변경은 없었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- Approved test plan: `TEST-PLAN-GH-124-INBOX-READ-SKIP-API`
- Approval evidence: user approval in Codex conversation at
  `2026-08-16T14:20:01+09:00`

## Completion criteria

- [x] 인증된 수신자는 카테고리와 선택적 방향 필터로 자신의 수신함 목록·칩을
      조회할 수 있다.
- [x] 자격 있는 상세 조회는 최초 한 번만 `OPENED`를 기록하고 반복 조회가 최초
      `opened_at`을 바꾸지 않는다.
- [x] 존재하지 않는 항목, 타인의 항목과 차단·만료·종결로 자격을 잃은 항목은
      존재 여부를 구분할 수 없는 동일한 404 계약을 따른다.
- [x] `ANSWERED`는 상세 열람 자격을 유지하고, `SKIP_PENDING`은 유예 중 목록·상세
      자격과 슬롯을 유지하며, `SKIPPED`·미답변 `EXPIRED`·`BLOCKED`는 다시 열리지
      않는다.
- [x] 양방향 활성 차단이 목록·상세·상태 변경에 일관되게 적용되고 해제된 차단은
      접근을 막지 않는다.
- [x] 반복 넘김 요청은 최초 요청 시각과 유예 종료 시각을 연장하지 않는다.
- [x] 유예 종료 전 되돌리기는 이전 상태로 복원하고, 유예 종료 시각 이상에서는
      되돌리지 않는다.
- [x] 열람·넘김·되돌리기가 동시 답변·차단·만료 전이를 stale write로 덮어쓰지
      않으며 슬롯 카운터를 직접 변경하지 않는다.
- [x] 정확 좌표, 내부 사용자 식별자와 저장소 내부 값이 API 응답에 노출되지 않는다.
- [x] 승인된 테스트 계획의 모든 P0 시나리오와 필수 회귀 검증이 통과하고
      `templates/test-report.md` 기반 보고서가 남는다.
