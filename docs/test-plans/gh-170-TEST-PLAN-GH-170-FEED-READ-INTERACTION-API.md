# Test Plan: TEST-PLAN-GH-170-FEED-READ-INTERACTION-API

> Created at: `2026-08-19T14:59:55+09:00`
> GitHub Issue: `#170`
> Status: Approved

## 1. Objective

이미 구현·검증된 service 계층(`SentPostQueryService`, `PostAnswerQueryService`,
`DirectionPostService.markAnswersRead`, `PostRecipientService.markAnswersRead`,
`PostReactionService`, `AnswerReactionService`)을 HTTP 엔드포인트 7개로 노출한다.
검증할 사용자 가치는 `내가 보낸 질문` 목록·상세, 수신 질문 상세의 답변 목록과 하트,
`새로운 답변 N개` 배지 해제다. 제품 ADR 0002가 다른 수신자에게는 푸시 대신 인앱 배지만
주기로 했으므로, 수신자용 읽음 처리 경로가 그 배지를 해제하는 유일한 수단이다.

실패 시 위험:

- **자격 우회.** 남의 `내가 보낸 질문`, 남의 답변, 남의 수신 항목에 새 경로로 닿으면
  ADR 0002가 정한 답변 열람 자격과 `#67`의 소유권 계약이 web 계층에서만 무너진다.
- **공감 상태 뒤집힘.** `toggle`을 그대로 노출하면 앱 재시도 한 번이 하트를 끈다.
- **읽음 지점 후퇴.** 읽음 처리가 `answers_read_at`을 과거로 되돌리면 이미 읽은 답변이
  다시 미읽음으로 잡힌다.
- **위치·식별자 유출.** 기존 수신함이 지켜온 "정확 좌표와 내부 사용자 식별자 비노출"이
  새 응답 4종에서 깨질 수 있다.
- **집계 어긋남.** 클라이언트가 공감 수를 자체 증감하면 서버 값과 갈라진다.

## 2. Scope

### Included

- `GET /api/v1/direction/posts` (`filter`, `cursorSubmittedAt`·`cursorPostId`, `limit`)
- `GET /api/v1/direction/posts/{postId}`
- `GET /api/v1/direction/posts/{postId}/answers` (`cursorPublishedAt`·`cursorAnswerId`, `limit`)
- `PUT /api/v1/direction/posts/{postId}/answers/read`
- `PUT /api/v1/direction/inbox/{postRecipientId}/answers/read`
- `PUT`·`DELETE /api/v1/direction/posts/{postId}/reaction`
- `PUT`·`DELETE /api/v1/direction/answers/{answerId}/reaction`
- `PostReactionService`·`AnswerReactionService`의 `react`/`cancel` 분해와 `toggle` 보존
- `AnswerReactionRepository.countByAnswerId`
- `InboxCard`·`InboxQuerySql.SELECT_CARD`의 `reactedByMe` 보강과 그 파급
- 계정 자격 게이트(ACTIVE USER)의 새 경로 7개 적용
- `docs/api/openapi.json` 재생성 결과 검증

### Excluded

- 알림 목록 조회 API (`notification`에 service·web 계층이 없다)
- 답변 수정·삭제 API
- 신고·차단 진입점 (`#154`~`#157`)
- 닉네임·프로필 이미지 (`#168`, `#166`)
- 지도 활동 마커 집계
- 기존 `/inbox` 목록·상세·넘김의 동작 변경 (`reactedByMe` 필드 추가는 제외하지 않는다)
- 성능·부하 측정 (`performanceTest` 태스크 소관)
- 인프라 apply, 배포, 프로덕션 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #170 범위 1~7 | 엔드포인트 7개 노출, `countByAnswerId` 추가, `reactedByMe` 보강, 계정 자격 게이트 적용 |
| GitHub Issue #170 완료 조건 | 자격 없는 사용자 차단, 공감 반복 호출 불변, 읽음 시각 비후퇴, `reactedByMe` 정확성, 위치·식별자 비노출 |
| `TASK.md` Design decision 1 | 공감은 idempotent `PUT`/`DELETE` 쌍 |
| `TASK.md` Design decision 2 | cursor는 명시적 두 파라미터 |
| `TASK.md` Design decision 4 | 읽음 처리는 `PUT`, `advanceAnswersReadAt`의 `GREATEST` 보장에 의존 |
| `TASK.md` Design decision 5 | 자격 없는 뷰어의 답변 목록은 403이 아니라 빈 목록 |
| `TASK.md` Design decision 6 | `limit` 기본 20 상한 50, `nextCursor`는 반환 건수 == `limit`일 때만 |
| `TASK.md` Design decision 7 | application 계층은 `feed.service` 한 곳, 오류는 `FED-APP-001`·`FED-APP-002` |
| `TASK.md` Design decision 8 | `DELETE /reaction`은 자격 검사 없이 본인 행만 삭제 |
| 제품 ADR 0002 `답변은 질문글을 받은 사람 모두에게 공개된다` (2026-08-07, ADR 0001을 supersede) | 답변은 질문글 작성자와 그 질문글의 수신 자격자 전원이 읽고 공감한다. 열람은 수신 자격자로만 제한하고 정렬은 최신순을 기본으로 둔다 |
| 같은 ADR의 알림 결정 | 새 답변 푸시는 질문자에게만 보내고 다른 수신자에게는 인앱 배지만 준다. 수신자용 읽음 처리 경로가 그 배지를 해제하는 유일한 수단이다 |
| `PostAnswerQueryRepository.findAnswers` javadoc | 자격 없는 뷰어에게 질문글 존재 여부를 흘리지 않는다 |
| `AnswerReactionRepository.react` javadoc | 같은 transaction 안에서 `cancel` 직후 같은 key로 `react`는 안전하지 않다 |
| `DirectionPostRepository.advanceAnswersReadAt`, `PostRecipientRepository.advanceAnswersReadAt` | `GREATEST(현재값, at)`로만 전진 |
| `SecurityConfiguration.appApiSecurityFilterChain` | `/api/**`는 device 등록·토큰 재발급을 뺀 전부가 인증 필요 |
| `docs/api-response.md`, `OpenApiSpecificationIntegrationTest` | 스펙 산출물은 커밋된 `docs/api/openapi.json`과 일치해야 한다 |
| `scripts/validate-java-tests.py` | 테스트 클래스 헤더의 `Created at`·`Source scenario`와 전 메서드 `@DisplayName` |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 새 읽기 경로가 계정 자격 게이트를 빠뜨려 SUSPENDED·OPERATOR 계정이 피드를 읽는다 | High | Medium | P0 | 7개 경로 전부에 대한 403·404 통합 시나리오 |
| `GET /posts/{postId}`가 남의 질문글을 404 대신 200으로 준다 | High | Low | P0 | 타 사용자 소유·삭제 질문글 통합 시나리오 |
| 답변 목록이 자격 없는 뷰어에게 403을 줘 질문글 존재 여부를 흘린다 | Medium | Medium | P0 | outsider·SKIPPED·만료 수신자의 200 빈 목록 확인 |
| 공감 `PUT` 재시도가 unique 위반으로 500이 되거나 상태를 뒤집는다 | High | Medium | P0 | 같은 `PUT` 3회 후 행 1건·count 1 확인 |
| 공감 `DELETE` 재시도가 404/500이 된다 | Medium | Medium | P0 | 같은 `DELETE` 2회 후 행 0건·count 0 확인 |
| 자격을 잃은 사용자가 자기 공감을 회수하지 못해 하트가 켜진 채 고정된다 | Medium | Medium | P1 | 만료 후 `DELETE` 성공 시나리오 |
| 순서가 뒤바뀐 읽음 요청이 `answers_read_at`을 과거로 되돌린다 | High | Medium | P0 | 늦은 시각 → 이른 시각 순서 호출 후 값 비교 |
| `toggle` 분해가 기존 계약을 깬다 (`InboxSentPostWriteIntegrationTest` 6건이 의존) | High | Medium | P0 | 기존 통합 테스트 무수정 통과 |
| 같은 transaction 안 `cancel` 직후 `react`로 Hibernate flush 충돌이 난다 | High | Low | P0 | `toggle` 합성 경로가 한 요청에서 두 분기를 동시에 타지 않음을 단위로 확인 |
| `reactedByMe` 추가가 `InboxCard` 생성자 호출부 6개 테스트를 깨뜨린 채 병합된다 | Medium | High | P1 | 기존 inbox 단위·통합 테스트 전부 통과 |
| `reactedByMe`가 뷰어가 아닌 다른 사용자의 공감으로 채워진다 | Medium | Medium | P0 | 두 사용자 교차 공감 후 뷰어별 값 확인 |
| 새 응답 DTO에 좌표·내부 사용자 식별자가 실린다 | High | Low | P0 | record component 이름 검사 + 스펙 금칙어 검사 |
| `limit` 상한이 없어 한 요청이 전체 목록을 긁는다 | Medium | Medium | P1 | 상한 초과 요청의 400 확인 |
| `nextCursor`가 마지막 페이지에서도 채워져 무한 페이징이 된다 | Medium | Medium | P1 | 반환 건수 < `limit`인 페이지에서 `null` 확인 |
| 커서 두 값 중 하나만 온 요청이 정렬 키를 반만 써 중복·누락을 만든다 | Medium | Medium | P1 | 단일 커서 파라미터 요청의 400 확인 |
| 동시 `PUT` 두 건이 PK 충돌로 한쪽이 500이 된다 | Medium | Low | P1 | 동시 실행 후 행 1건과 예외 부재 확인 |
| `openapi.json`을 갱신하지 않은 채 컨트롤러만 바뀐다 | Medium | Medium | P0 | `OpenApiSpecificationIntegrationTest` 재생성 일치 |
| feed가 다른 feature의 JDBC·JPA 구현을 직접 참조해 경계가 무너진다 | Medium | Low | P1 | `FeedPersistenceBoundaryTest` 통과 |

## 5. Unit scenarios

`src/test` (Docker 불필요, Mockito·MockMvc standalone).

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| …-UNIT-001 | `AccountRepository`가 빈 Optional | 목록·상세·답변·읽음·공감 진입점 호출 | `FeedException(INBOX_ACCOUNT_NOT_FOUND)`, 하위 service 미호출 | P0 | Executor 3 |
| …-UNIT-002 | 계정이 OPERATOR 또는 SUSPENDED | 같은 진입점 호출 | `FeedException(INBOX_ACCOUNT_NOT_ELIGIBLE)`, 하위 service 미호출 | P0 | Executor 3 |
| …-UNIT-003 | `limit` 미지정 / 50 / 51 / 0 | 목록·답변 목록 호출 | 미지정은 20으로 위임, 50은 그대로, 51과 0은 400 계열 검증 오류 | P1 | Executor 3 |
| …-UNIT-004 | 커서 두 파라미터 중 하나만 전달 | 목록·답변 목록 호출 | 400 계열 검증 오류, query service 미호출 | P1 | Executor 3 |
| …-UNIT-005 | query service가 `limit`과 같은 건수 / 더 적은 건수 반환 | 목록 응답 조립 | 같으면 마지막 카드의 정렬 키로 `nextCursor` 채움, 적으면 `null` | P1 | Executor 3 |
| …-UNIT-006 | `SentPostQueryService.detail`이 `Optional.empty` | 상세 호출 | 404 계열 예외, "없음"과 "남의 것"을 구분하지 않음 | P0 | Executor 3 |
| …-UNIT-007 | `PostAnswerQueryService.answers`가 빈 목록 반환 | 답변 목록 호출 | 예외 없이 빈 목록을 그대로 반환 (403으로 바꾸지 않음) | P0 | Executor 3 |
| …-UNIT-008 | 고정 `Clock` | 읽음 처리 2종 호출 | 두 하위 service에 `clock.instant()` 한 값만 전달 | P0 | Executor 3 |
| …-UNIT-009 | 공감 진입점 | `PUT`·`DELETE` 호출 | 자격 게이트 통과 후 `react`/`cancel`에 위임하고 최신 공감 수를 응답에 실음 | P0 | Executor 3 |
| …-UNIT-010 | 신규 web 타입 | 리플렉션 검사 | ApiSpec/Controller 분리, `@RestController`, base path `/api/v1/direction`, 승인된 생성자 의존성만 | P1 | Executor 3 |
| …-UNIT-011 | 신규 ApiSpec | 리플렉션 검사 | 7개 매핑의 HTTP 메서드·경로·`@RequestParam` 기본값·`@Parameter(hidden)` 인증 인자 선언 | P0 | Executor 3 |
| …-UNIT-012 | 신규 응답 record 4종 | record component 이름 검사 | `latitude`·`longitude`·`senderId`·`recipientId`·`reactorId`·`authorId` 등 금칙 토큰 없음 | P0 | Executor 3 |
| …-UNIT-013 | standalone MockMvc + mock application service | `GET /posts`, `GET /posts/{id}`, `GET /posts/{id}/answers` | 200 JSON 형태와 기본 파라미터가 application service 인자로 도달 | P1 | Executor 3 |
| …-UNIT-014 | application service가 `FeedException` 던짐 | 같은 경로 호출 | `FED-APP-001`→404, `FED-APP-002`→403, 상세 없음→404로 매핑 | P0 | Executor 3 |
| …-UNIT-015 | standalone MockMvc | 질문글·답변 공감 `PUT`·`DELETE` | 200과 `reacted`·`reactionCount` 필드 | P1 | Executor 3 |
| …-UNIT-016 | standalone MockMvc | 읽음 처리 `PUT` 2종 | 200과 `answersReadAt` 필드, 경로 변수 전달 | P1 | Executor 3 |
| …-UNIT-017 | 이미 공감 행이 있는 mock repository | `react` 호출 | `repository.react` 미호출, 최신 count 반환 (멱등) | P0 | Executor 1 |
| …-UNIT-018 | 공감 행이 없는 mock repository | `cancel` 호출 | 예외 없이 종료, 자격 조회 repository 미호출 | P0 | Executor 1 |
| …-UNIT-019 | 자격 있는 사용자 / 자격 없는 사용자 | `toggle` 호출 | 남김↔취소 왕복이 종전과 같고, 자격 없으면 `INELIGIBLE_REACTOR` | P0 | Executor 1 |
| …-UNIT-020 | `InboxCard` | 소스·리플렉션 검사 | `reactedByMe` 컴포넌트 존재, `InboxQuerySql.SELECT_CARD`에 `post_reaction` 뷰어 조건 존재 | P1 | Executor 2 |
| …-UNIT-021 | 기존 inbox 단위 테스트 | 재실행 | `reactedByMe` 추가 후에도 매핑·응답·민감정보 검사 전부 통과 | P0 | Executor 2 |

## 6. Integration scenarios

`src/integrationTest`, PostgreSQL(PostGIS) Testcontainers. 별도 표기가 없으면
`FeedReadInteractionApiIntegrationTest`가 application service 경계로 호출한다
(`InboxApiIntegrationTest`가 쓰는 것과 같은 방식).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | feed app service + `JdbcSentPostQueryRepository` | 만료 전 2건, 만료 후 2건 | `filter` = ALL·IN_PROGRESS·EXPIRED | 4·2·2건, `submitted_at` 내림차순 | 지역 코드 기준 삭제 |
| …-INT-002 | 동일 | 5건, `limit` 2 | 커서로 3페이지 순회 | 중복·누락 없음, 3페이지 `nextCursor`는 `null` | 동일 |
| …-INT-003 | 동일 | 남의 질문글 1건, 삭제된 본인 질문글 1건 | 상세 조회 | 둘 다 404, 오류 코드 동일 | 동일 |
| …-INT-004 | 동일 | — | `limit` 51 | 400 계열 오류 | 동일 |
| …-INT-005 | 동일 | 두 사용자가 각각 질문글 보유 | 목록 조회 | 자기 질문글만 반환 | 동일 |
| …-INT-006 | feed app service + `JdbcPostAnswerQueryRepository` | 답변 3건 | 질문자·수신 자격자로 답변 목록 | 3건 내용과 `reactedByMe`·`reactionCount` | 동일 |
| …-INT-007 | 동일 | 무관한 계정 | 답변 목록 | 200 빈 목록 (403·404 아님) | 동일 |
| …-INT-008 | 동일 | SKIPPED 수신자, 만료된 미답변 수신자 | 답변 목록 | 둘 다 200 빈 목록 | 동일 |
| …-INT-009 | 동일 | 답변 5건, `limit` 2 | 커서로 3페이지 순회 | 중복·누락 없음, 마지막 `nextCursor` `null` | 동일 |
| …-INT-010 | feed app service + `DirectionPostService` | 답변 2건, `answers_read_at` NULL | 질문자 읽음 처리 | `answers_read_at` 기록, `unreadAnswerCount` 0 | 동일 |
| …-INT-011 | 동일 | 읽음 시각 T+60 기록 | T+10으로 읽음 처리, 다시 T+60 | 값이 T+60에서 후퇴하지 않음 | 동일 |
| …-INT-012 | feed app service + `PostRecipientService` | 다른 수신자의 답변 2건 | 수신자 읽음 처리 | 수신함 카드 `unreadAnswerCount` 0 | 동일 |
| …-INT-013 | 동일 | 남의 수신 항목 | 수신자 읽음 처리 | 404, 존재 여부 비노출 | 동일 |
| …-INT-014 | feed app service + `PostReactionService` | 자격 있는 수신자 | 질문글 공감 `PUT` 3회 | `post_reaction` 1행, `countByPostId` 1, 3회 응답 동일 | 동일 |
| …-INT-015 | 동일 | 공감 1건 존재 | `DELETE` 2회 | 0행, count 0, 두 응답 동일 | 동일 |
| …-INT-016 | 동일 | 수신자가 아닌 계정 | 질문글 공감 `PUT` | 403 (`DIR-DOM-007` 계열), 행 미생성 | 동일 |
| …-INT-017 | 동일 | 공감 후 질문글 만료 | `DELETE` | 200, 행 삭제 (Design decision 8) | 동일 |
| …-INT-018 | feed app service + `AnswerReactionService` | 자격 있는 뷰어 | 답변 공감 `PUT` 3회 → `DELETE` 2회 | 1행 → 0행, `countByAnswerId`가 1 → 0 | 동일 |
| …-INT-019 | 동일 | 답변 작성자 본인 / 무관한 계정 | 답변 공감 `PUT` | 둘 다 403, 사유 구분(`자기 답변` vs `열람 자격`) | 동일 |
| …-INT-020 | feed app service + `JdbcInboxQueryRepository` | 뷰어가 질문글에 공감 | 수신함 목록·상세 조회 | 두 경로 모두 `reactedByMe` true | 동일 |
| …-INT-021 | 동일 | 다른 수신자만 공감 | 뷰어 기준 조회 | `reactedByMe` false, `reactionCount` 1 | 동일 |
| …-INT-022 | 계정 게이트 | SUSPENDED USER, ACTIVE OPERATOR | 7개 경로 전부 호출 | 전부 403 (`FED-APP-002`) | 동일 |
| …-INT-023 | 계정 게이트 | 존재하지 않는 계정 id | 7개 경로 전부 호출 | 전부 404 (`FED-APP-001`) | 동일 |
| …-INT-024 | 동시성 | 같은 사용자·같은 질문글 | 공감 `PUT` 2건 동시 실행 | 예외 없이 최종 1행, count 1 | 동일 |
| …-INT-025 | 동시성 | 같은 사용자·같은 답변 | `PUT`과 `DELETE` 동시 실행 | 최종 상태가 0행 또는 1행 중 하나로 수렴, count 음수 없음, 미처리 예외 없음 | 동일 |
| …-INT-026 | `OpenApiSpecificationIntegrationTest` | 애플리케이션 컨텍스트 | 스펙 재생성 | 새 경로 7개 존재, `appAccessToken` security 선언, 좌표·내부 식별자 필드 부재, 커밋된 파일과 동일 | 없음 |
| …-INT-027 | feed app service + `JdbcPostAnswerQueryRepository` | PUBLISHED 답변 1건, 검토 대기 답변 1건, 삭제된 답변 1건 | 답변 목록 조회 | PUBLISHED 1건만 반환, `answerCount`도 1 — 제품 ADR 0002의 `검토 중인 답변은 다른 사람에게 감춘다`를 노출 경로에서 고정 | 지역 코드 기준 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-011은 `direction_post.answers_read_at`을, INT-012는 `post_recipient.answers_read_at`을
  직접 조회해 `GREATEST` 전진 규칙을 값으로 확인한다. 서비스 반환값만 믿지 않는다.
- INT-014~019는 서비스 반환값과 별도로 `post_reaction`·`answer_reaction` 행 수를
  `JdbcTemplate`으로 직접 센다. `countByPostId`·`countByAnswerId`가 같은 값을 주는지도 본다.
- INT-016·INT-019는 자격 위반이 각각 즉시 FK(`fk_post_reaction_recipient`)와 지연
  constraint trigger(`ct_answer_reaction_reactor_can_view`)에 닿기 전에 서비스가 먼저
  거르는지를 본다. `DataIntegrityViolationException`이 호출자에게 새어나가면 실패다.
- `react`/`cancel`이 별도 요청이므로 같은 transaction 안에서 `cancel` 직후 `react`가
  일어나지 않는다. UNIT-019가 `toggle` 합성 경로에서도 한 호출이 한 분기만 타는지 본다.
- 목록 조회는 읽기 전용 transaction으로 돌아야 한다. INT-001·INT-006은 조회 중
  쓰기가 일어나지 않음을 전제로 하며, 별도 격리 수준 요구는 없다
  (`InboxQueryService.list`의 `REPEATABLE_READ`는 칩 집계 때문이며 이 계획의 범위 밖이다).

### ADR 참조의 저장소 내 모호성

`feed/view/InboxCard.java`와 `feed/view/AnswerCard.java`가 인용하는 "ADR 0002"는 답변
공개 범위를 정한 제품 ADR이며 실재한다. 다만 이 저장소의 `docs/adr/0002`는 JPA와 JDBC의
사용 경계를 정한 기술 ADR이라 번호가 겹친다. 저장소만 읽는 사람에게는 어느 쪽인지
분간되지 않는다. 이 계획은 제품 ADR을 근거로 삼되 그 문서가 저장소 밖에 있으므로 경로를
적지 않는다. 주석의 번호 표기 정리는 이 이슈의 범위 밖이다.

## 8. Test data and isolation

- **Fixtures**: `Inbox124IntegrationFixtures` 패턴을 따라 `Feed170IntegrationFixtures`를
  새로 만든다. `coarse_region_code`를 `TEST-FEED170`으로 고정하고 `reset()`이 그 지역의
  `outbox_event`→`answer`→`post_recipient`→`post_audience`→`direction_post`→
  `recipient_receive_state`→`user_block`→`approved_question`→`user_account`→`region_code`
  순으로 지운다. `post_reaction`·`answer_reaction`은 FK cascade 대상이 아니면 명시적으로
  먼저 지운다.
- **Database isolation**: `PostgisContainerIntegrationTestSupport`를 상속한다. 지역 코드
  격리로 다른 통합 테스트와 행이 섞이지 않게 한다.
- **Clock/randomness**: `Inbox124MutableClock`과 같은 mutable `Clock`을 `@Primary` 빈으로
  주입해 `NOW`를 고정하고, 만료·읽음 역전 시나리오에서만 시각을 옮긴다. `Instant.now()`와
  `CURRENT_TIMESTAMP`를 테스트에서 쓰지 않는다.
- **External API doubles**: 없다. LocalStack을 띄우지 않는다.
- **Cleanup**: `@BeforeEach`에서 `reset()`을 호출한다. `@DirtiesContext`는 상위 support가
  이미 선언한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

각 파일의 소유자는 정확히 하나다. 순서대로 실행한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Reaction 계층 | `answer/repository/AnswerReactionRepository.java`, `answer/repository/jpa/SpringDataAnswerReactionRepository.java`, `answer/repository/jpa/JpaAnswerReactionRepository.java`, `direction/service/PostReactionService.java`, `answer/service/AnswerReactionService.java`, `src/test/java/com/dnd/qello/direction/service/PostReactionServiceTest.java`(신규), `src/test/java/com/dnd/qello/answer/service/AnswerReactionServiceTest.java`(신규) | UNIT-017~019 | `./gradlew test` |
| 2 | 수신함 `reactedByMe` | `feed/view/InboxCard.java`, `feed/repository/jdbc/sql/InboxQuerySql.java`, `feed/repository/jdbc/JdbcInboxQueryRepository.java`, `feed/web/response/InboxListingResponse.java`, `src/test/java/com/dnd/qello/feed/web/InboxWebContractTest.java`, `src/test/java/com/dnd/qello/feed/web/InboxApiMockMvcTest.java`, `src/test/java/com/dnd/qello/feed/FeedPersistenceBoundaryTest.java`, `src/integrationTest/.../InboxQueryIntegrationTest.java`, `InboxApiIntegrationTest.java`, `InboxDirectionChipIntegrationTest.java`, `InboxDetailScopeIntegrationTest.java` | UNIT-020~021, INT-020~021 | `./gradlew test`, `./gradlew integrationTest` |
| 3 | Application + web | `feed/service/FeedInteractionApplicationService.java`(신규), `feed/error/FeedErrorCode.java`, `feed/web/SentPostApiSpec.java`·`SentPostController.java`(신규), `feed/web/AnswerReadApiSpec.java`·`AnswerReadController.java`(신규), `direction/web/PostReactionApiSpec.java`·`PostReactionController.java`(신규), `answer/web/AnswerReactionApiSpec.java`·`AnswerReactionController.java`(신규), `feed/web/response/*`(신규 4종), 대응 `src/test` 신규 파일 | UNIT-001~016 | `./gradlew test` |
| 4 | 통합 검증 | `src/integrationTest/java/com/dnd/qello/FeedReadInteractionApiIntegrationTest.java`(신규, fixtures·clock 포함) | INT-001~025, INT-027 | `./gradlew integrationTest` |
| 5 | 스펙 산출물 | `docs/api/openapi.json`, `src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java` | INT-026 | `./gradlew integrationTest` |

Order 3은 `PUT /inbox/{postRecipientId}/answers/read`를 기존 `InboxApiSpec`이 아니라 신규
`AnswerReadApiSpec`에 둔다. Order 2가 소유한 파일을 Order 3이 다시 건드리지 않게 하기
위해서다. 두 읽음 경로가 한 컨트롤러에 모이는 편이 응집도도 낫다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과 (`./gradlew test`)
- [ ] 통합 테스트 통과 (`./gradlew integrationTest`)
- [ ] 기존 `InboxSentPostWriteIntegrationTest`의 `toggle` 시나리오 6건이 무수정 통과
- [ ] `docs/api/openapi.json` 재생성 결과가 커밋된 파일과 일치
- [ ] `npm run hooks:validate`와 `git diff --check` 통과
- [ ] 잠재 문제 분석 (애플리케이션·DB·동시성·트랜잭션·외부 API·장애 복구)
- [ ] `templates/test-report.md`로 테스트 보고서 생성

실패 판단 기준: P0 시나리오가 하나라도 실패하거나, 기존 테스트가 깨진 채 남거나,
스펙 재생성 결과가 커밋된 파일과 다르면 `FAIL`. Docker를 쓸 수 없어 `integrationTest`를
실행하지 못하면 `BLOCKED`로 보고하고 미검증 범위를 명시한다.

## 11. Human approval

- Reviewer: `@Byuntil`
- Decision: `APPROVED_FOR_TEST_RUN`
- Approved at: `2026-08-19T15:13:23+09:00`
