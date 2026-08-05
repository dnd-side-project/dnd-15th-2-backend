# GitHub Issue #54 Task Contract

> Generated at: `2026-08-05T03:56:58+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[B] V2 마이그레이션과 매핑 갱신`
- GitHub Issue: `#54`
- Branch: `feat/gh-54-v2-schema-migration`

## Objective

- 승인된 스키마 계약(#53)을 V2 delta migration으로 구현하고, 같은 변경을
  enum·도메인 불변식·JDBC 매퍼에 반영한다.
- DB 값 집합과 Java enum이 어긋난 상태를 main에 남기지 않기 위해 마이그레이션과
  매핑 갱신을 한 단위로 처리한다.

## Scope

- V2 migration — 공감 테이블 2종(`post_reaction`, `answer_reaction`),
  `skip_requested_at`, `answers_read_at`, `SKIP_PENDING`,
  `uq_answer_one_per_recipient`, CHECK 교체, 작성자 검증 트리거
- 신규 CHECK가 기존 행을 거부하는 경로의 백필
- Flyway 계약·카탈로그 테스트 갱신과 백필·값 집합 회귀 테스트 신설
- `NotificationType`·`OutboxEventType`·`PostRecipientStatus` 갱신
- `PostRecipient` 넘김 전이(요청·되돌리기·확정)와 `DirectionPost` 읽음 기준선
- 수신 상한을 `@ConfigurationProperties`로 분리

## Explicit exclusions

- 공감(reaction) persistence 구현은 #55에서 한다. 이 브랜치에서 JPA
  reaction Entity·Repository를 만들지 않는다.
- `SKIP_CONFIRMATION_DUE` 워커와 스케줄러는 이번 범위 밖이다.
- 적용 완료된 `V1__create_direction_communication_schema.sql`은 수정하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `src/main/resources/db/migration/`, `src/main/java/com/dnd/qello/direction/`, `src/main/java/com/dnd/qello/notification/domain/`, `src/integrationTest/java/com/dnd/qello/` | @Byuntil | @Byuntil |

## Existing user-owned changes

- 브랜치 생성 시점(2026-08-05)에 `git status --short`가 비어 있었다(`TASK.md` 자체
  갱신만 존재). 정리한 타인의 변경은 없다.
- `main`에 PR #57(전역 예외 처리, #51)이 먼저 병합돼 `PostRecipient`·`DirectionPost`·
  `RecipientReceiveState`·`DirectionPostService`·`DirectionDomainTest`가 겹쳐서
  `git merge main`으로 충돌을 해소했다. 병합 커밋에 상세 내역을 남긴다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 빈 DB에 V1 → V2가 적용되고 두 번째 실행이 변경 없이 성공한다.
- [ ] 옛 형태 데이터가 있는 DB에서 백필 후 새 제약이 유효하다.
- [ ] `SKIP_PENDING`이 수신 용량을 해제하지 않는다.
- [ ] 되돌린 넘김의 이전 상태가 timestamp 유무로 도출된다.
- [ ] 한 질문글에 답변은 1회이며 거절된 답변은 재작성을 허용한다.
- [ ] 수신 상한이 DB CHECK가 아니라 설정값으로 결정된다.
- [ ] Hibernate가 schema를 생성하거나 수정하지 않는다.
- [ ] `./gradlew check`, `./harness check`, `npm run hooks:validate`가 통과한다.
