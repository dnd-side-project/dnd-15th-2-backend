# Test Report: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API

> Created at: `2026-08-19T16:55:47+09:00`
> GitHub Issue: `#170`
> Branch: `feat/gh-170-feed-read-interaction-api`
> Commit: `988dcc9`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 엔드포인트 7개(GET 3, PUT 4, DELETE 2)의 application/repository
  단위 테스트, MockMvc 계약 테스트, PostgreSQL/PostGIS 통합 테스트, `reactedByMe`
  전파, `toggle` 계약 보존, `openapi.json` 재생성.
- Unverified scope: 원격 CI/PR 체크 상태, 부하·동시성 스트레스(경쟁 시나리오는
  각 1회 실행만 확인), 실제 배포 환경 동작.
- Release recommendation: 로컬 검증은 PASS. 커밋·push·PR·원격 체크는 별도
  승인 단계로 남아 있다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (toolchain, 실행 JDK 25) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS 16-3.5-alpine (postgis/postgis) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Evidence |
| --- | --- | ---: | --- |
| 전체 단위 테스트(`./gradlew test --rerun-tasks`) | PASS | 710 | `build/reports/tests/test/index.html` |
| 전체 통합 테스트(`./gradlew integrationTest --rerun-tasks`) | PASS | 528 | `build/reports/tests/integrationTest/index.html` |
| `./harness pr-ready --project-tests`(unit+integration+check 포함) | PASS | 710+528 | `BUILD SUCCESSFUL in 4m 17s` |
| `./harness check`(secret preflight, JUnit 정책, convention, workflow, label, husky) | PASS | 정책 검사 7종 | 실패 없음 |
| `npm run hooks:validate` | PASS | Husky hook 구성 검증 | `Husky validation passed.` |
| `git diff --check` | PASS | 공백·충돌 마커 | 없음 |
| `OpenApiSpecificationIntegrationTest`(재생성+idempotency) | PASS | 9 | `build/test-results/integrationTest/TEST-com.dnd.qello.OpenApiSpecificationIntegrationTest.xml` |

task_id: `TASK-GH-170` · design_id: 없음(신규 인프라·아키텍처 설계가 필요 없는
이슈 — 기존 service를 web 계층으로 노출) · changed_files: 이 보고서 하단
`## 8. Artifacts`와 연결된 커밋 범위(`6dd6f54`~현재 HEAD)의 전체 diff.

## 4. Scenario results

계획(`docs/test-plans/gh-170-TEST-PLAN-GH-170-FEED-READ-INTERACTION-API.md`)의
전체 시나리오가 구현·실행됐다. 대표 클래스별 결과는 다음과 같다.

| Scenario ID | Result | Test class | Notes |
| --- | --- | --- | --- |
| UNIT-001~009 | PASS(10) | `FeedInteractionApplicationServiceTest` | 계정 게이트, limit·cursor 검증, 커서 조합 |
| UNIT-010~012 | PASS(5) | `FeedInteractionWebContractTest` | ApiSpec/Controller 경계, 매핑 선언, 민감정보 미노출 |
| UNIT-013,014 | PASS(6) | `SentPostApiMockMvcTest` | 목록·상세·답변 목록, 400/404 매핑 |
| UNIT-013,016 | PASS(3) | `AnswerReadApiMockMvcTest` | 질문자·수신자 읽음 처리 |
| UNIT-013,015 | PASS(4) | `PostReactionApiMockMvcTest` | 질문글 공감 PUT/DELETE, 403 매핑 |
| UNIT-013,015 | PASS(4) | `AnswerReactionApiMockMvcTest` | 답변 공감 PUT/DELETE, 403 매핑 |
| UNIT-017~019 | PASS(7) | `PostReactionServiceTest` | react/cancel 멱등성, 자격 검사, `toggle` 왕복 |
| UNIT-017~019 | PASS(7) | `AnswerReactionServiceTest` | 동일(답변 공감) |
| UNIT-020 | PASS(7) | `FeedPersistenceBoundaryTest` | `reactedByMe` SQL·record 검사 포함 |
| INT-001~019, 022, 024, 025, 027 | PASS(23) | `FeedReadInteractionApiIntegrationTest` | 목록·답변 목록·읽음·공감·계정 게이트·동시성·모더레이션 은닉 |
| INT-020, 021 | PASS(포함, 총 14) | `InboxApiIntegrationTest` | `reactedByMe` 뷰어 교차 검증 |
| INT-026 | PASS(9) | `OpenApiSpecificationIntegrationTest` | 스펙 재생성·검증 |

기존 회귀 대상(`InboxSentPostWriteIntegrationTest`의 `toggle` 계약 28건,
`ReactionPersistenceIntegrationTest` 7건 포함)도 전체 통합 테스트 528건에
포함돼 통과했다.

## 5. Failures and diagnostics

구현 중 발견하고 그 자리에서 고친 항목이다. 최종 실행에서는 실패가 없다.

- `PostReactionServiceTest`의 `toggle` 왕복 테스트가 처음에는 mock 응답 순서를
  실제 호출 순서(`toggle`이 `exists`를 한 번, 위임받은 `react`가 멱등성 확인으로
  한 번 더 조회)와 다르게 스텁해 실패했다. 스텁 순서를 맞춰 통과시켰다.
- `FeedReadInteractionApiIntegrationTest`의 초기 fixture가 `post_recipient`의
  `ck_post_recipient_status_timestamps`, `ck_post_recipient_skip_pending`,
  `ct_post_recipient_capacity_release` 제약과 `answer`의 `ck_answer_deleted_at`,
  `uq_answer_one_per_recipient` 제약을 충족하지 못해 5개 테스트가 실패했다.
  SKIPPED/EXPIRED/BLOCKED 상태에 필요한 시각 컬럼과 `capacity_released_at`을
  채우고, 답변 1건당 수신자 1명 제약에 맞춰 fixture를 수정해 통과시켰다.
- `AnswerReactionServiceTest`의 자기 답변/열람 자격 실패 사유 검증이
  `getMessage()`를 비교하도록 짜여 있어 실패했다 — `DomainException.getMessage()`는
  항상 `ErrorCode`의 기본 메시지를 반환하고, 호출별 구체 사유는 `getReason()`에
  실린다. `getReason()` 비교로 고쳤다.
- `git rebase`로 `origin/main`(신고·차단 API #154, #167 포함)을 반영하는 과정에서
  `docs/api/openapi.json`과 `TASK.md`에 병합 충돌이 났다. `openapi.json`은
  사람 확인 후 재생성(`OpenApiSpecificationIntegrationTest` 재실행)으로,
  `TASK.md`는 사람 확인 후 이 브랜치(#170) 쪽 내용 채택으로 해결했다.

## 6. Potential issues

### Application code

- `FeedInteractionApplicationService`가 계정 자격 게이트, limit·cursor 검증,
  7개 하위 service 위임을 모두 소유한다. 향후 새 읽기·상호작용 경로가 추가되면
  이 클래스가 계속 커질 수 있다 — 지금은 메서드 9개로 관리 가능한 범위다.
- `direction.web.PostReactionController`와 `answer.web.AnswerReactionController`가
  `feed.service.FeedInteractionApplicationService`를 직접 호출한다. 저장소의
  다른 컨트롤러는 자기 feature 소유 application service만 부르는 것과 다른
  예외 경로다(`TASK.md` design decision 7에서 의도적으로 승인됨).

### Infrastructure and resource limits

- 신규 항목 없음. 이 이슈는 애플리케이션 계층만 다룬다.

### Database and migrations

- 스키마 변경 없음. 기존 `post_reaction`, `answer_reaction`, `post_recipient`,
  `answer`, `direction_post` 테이블과 제약을 그대로 사용한다.

### Concurrency and idempotency

- INT-024(질문글 공감 동시 PUT)와 INT-025(같은 답변의 동시 PUT/DELETE)를 각각
  1회씩 실행해 예외 없이 최종 상태로 수렴함을 확인했다. 반복 실행이나 더 많은
  동시 스레드, connection pool 고갈 상황은 검증하지 않았다.
- `PostReactionService.cancel`/`AnswerReactionService.cancel`은 자격 검사를
  생략한다(design decision 8). 삭제 조건이 `(postId, reactorId)`로 좁혀져
  있어 남의 공감에는 닿지 않는다.

### Transactions and event ordering

- 답변 읽음 처리(`markSenderAnswersRead`, `markRecipientAnswersRead`)는 각각
  `DirectionPostService`/`PostRecipientService`가 소유한 단일 UPDATE의
  `GREATEST` 비교로 순서 역전을 방어한다. INT-011에서 늦은 시각 기록 후 이른
  시각으로 재호출해도 값이 후퇴하지 않음을 직접 DB 값으로 확인했다.

### External APIs

- 이 이슈는 외부 API를 호출하지 않는다.

### Failure recovery and reconciliation

- 자격 상실(만료·넘김 확정) 이후에도 공감 취소가 가능함을 INT-017로 확인했다.
- 부분 실패 시 보상 트랜잭션이나 outbox는 사용하지 않는다. 재시도는 전적으로
  각 연산의 멱등성에 의존한다.

## 7. Regression and residual risk

- 기존 `InboxSentPostWriteIntegrationTest`의 `toggle` 계약(28건)과
  `ReactionPersistenceIntegrationTest`(7건)가 무수정 상태로 통과했다 — 공감
  service 분해가 기존 계약을 깨지 않았다.
- `openapi.json`은 `origin/main`의 신고·차단 API(#154, #167)와 이 이슈의
  엔드포인트 7개를 모두 반영해 재생성했다(SHA-256:
  `ae39089757a4420d391bae9d0319d4b024c3ffc3680767e0a54fe3023b467fe5`).
- 부하·스트레스 특성은 검증 범위 밖이다.
- 원격 CI, PR 승인, 배포 후 동작은 별도 게이트로 남아 있다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-170-TEST-PLAN-GH-170-FEED-READ-INTERACTION-API.md`
- CI run: 아직 생성되지 않음(PR 생성 전).
- Related ADR: 없음(이 이슈는 이미 구현된 service를 web 계층으로 노출하는
  작업이라 새 ADR을 필요로 하지 않는다).
- PR: 아직 생성되지 않음.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(부하·스트레스, 원격 CI)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨(해당 없음 — 새 이슈가 필요한
      항목 없음, `FeedInteractionApplicationService` 크기 관찰은 후속 조치가
      필요할 정도는 아니라고 판단)
- [x] 실행 결과와 PR 설명이 일치함
