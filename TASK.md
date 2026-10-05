# GitHub Issue #310 Task Contract

> Generated at: `2026-10-05T17:51:33+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `질문 제안 삭제·제안별 알림 끄기 API`
- GitHub Issue: `#310`
- Branch: `feat/gh-310-question-proposal-delete-mute`
- Base branch: `main`

## Objective

- "제안한 질문" 화면의 삭제하기·알림 받지 않기에 대응하는 제안 1건 단위 소프트 삭제와 알림 끄기 API를 추가한다.

## Scope

- `question_proposal` 소프트 삭제 시각·알림 끄기 컬럼 Flyway 마이그레이션
- `QuestionProposal` 도메인 삭제(모든 상태 허용, 검토 전·중 삭제는 철회)·알림 끄기/켜기
- `DELETE /api/v1/questions/proposals/{proposalId}`와 제안별 알림 끄기·켜기 endpoint
- `GET /proposals/me`에서 삭제 제안 제외, 응답에 알림 끄기 여부 추가
- `QuestionReviewService`가 삭제된 제안의 검수 시작·승인·반려를 거부
- `NotificationFanOutWorker`가 알림 꺼진 제안의 `QUESTION_PROPOSAL_REVIEWED` push delivery를 만들지 않음(인박스 기록 유지)
- `QuestionProposalApiSpec` OpenAPI 갱신과 단위·통합 테스트
- 변경한 `@Service`의 convention ratchet 충족: `QuestionReviewService`·`QuestionProposalApplicationService`·`QuestionProposalReviewedNotificationResolver`·`NotificationFanOutWorker`와 `config/java-conventions/baseline.json`의 해소된 LEGACY 항목 삭제
- `docs/harness/JAVA_CONVENTIONS.md`에 직접 트랜잭션을 여는 Service 규칙 추가

## Explicit exclusions

- 알림 종류 단위 설정(`PUT /notifications/preferences`) 동작 변경
- `GET /proposals/me` 상태 필터·페이지네이션, 승인 질문 ID 응답 추가, 삭제 복구 API
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| question·notification 모듈, Flyway 마이그레이션 | 현재 Claude Code 작업 | 저장소 PR 리뷰 |

## Existing user-owned changes

- 최신 origin/main에서 분기했으며 시작 시 변경 없음.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- Issue #310 완료 조건 7개(삭제 후 목록 제외, 타인 제안 404, 중복 삭제 멱등, 삭제된 UNDER_REVIEW 판정 409, 알림 꺼진 제안 delivery 미생성, 도메인 단위 테스트, 하네스 검증 통과)를 충족한다.

## Decisions

- 2026-10-05 사용자 결정: 삭제는 모든 상태에서 허용하고 물리 삭제 대신 소프트 삭제로 한다. 알림 끄기는 제안 1건 단위다.
- 2026-10-05 사용자 결정: 테스트 계획 D1~D3 권장안 승인(삭제 제안은 push 없음·알림함 유지, 삭제 제안 알림 설정 404, 삭제 204).
- 2026-10-05 사용자 결정: `TransactionTemplate`을 쓰는 worker의 TX-001 처리 방식을 `docs/harness/JAVA_CONVENTIONS.md`에 규칙으로 추가한다(클래스 read-only + 진입 메서드 `NOT_SUPPORTED`, baseline 예외 없음).
