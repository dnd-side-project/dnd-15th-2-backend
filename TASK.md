# GitHub Issue #55 Task Contract

> Generated at: `2026-08-05T11:45:46+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[C] 공감(reaction) persistence`
- GitHub Issue: `#55`
- Branch: `feat/gh-55-reaction-persistence`

## Objective

- 질문글 공감과 답변 공감을 영속화한다.
- 누를 수 있는 사람이 서로 다르므로 두 테이블로 나누고, 그 차이를 키와 트리거로
  강제한다.

## Scope

- `PostReaction` 도메인·포트와 복합 PK JPA adapter
- `AnswerReaction` 도메인·포트와 JPA adapter
- 취소를 행 삭제로 처리하는 경계
- PostgreSQL/Testcontainers 기반 통합 테스트

## Explicit exclusions

- 공감 API와 `ANSWER_REACTED` 알림 발행은 이번 범위 밖이다.
- 조회 계층의 열람 격리는 다루지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `src/main/java/com/dnd/qello/direction/`, `src/main/java/com/dnd/qello/answer/`, `src/integrationTest/java/com/dnd/qello/` | @Byuntil | @Byuntil |

## Existing user-owned changes

- 브랜치 생성 시점(2026-08-05)에 `git status --short`가 비어 있었다. 정리한 타인의
  변경은 없다.
- 처음에는 아직 병합되지 않은 `feat/gh-54-v2-schema-migration`(PR #59) 위에
  쌓았다. #54가 `main`에 병합되면서 `main`에 PR #57(전역 예외 처리, #51)과의
  충돌 해소 커밋 2개가 함께 들어왔고, 이 브랜치는 그 이후 `main` 기준으로
  다시 정렬했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [ ] 수신 자격이 없는 사용자의 질문글 공감이 거부된다.
- [ ] 질문글 작성자가 자기 글에 공감할 수 없다.
- [ ] 질문자가 아닌 사용자의 답변 공감이 commit 시점에 거부된다.
- [ ] 답변당 공감이 최대 1건이다.
- [ ] 공감 취소가 행 삭제로 동작하고 다시 누르면 되살아난다.
- [ ] feature 간 Entity/Repository 직접 참조가 없다.
- [ ] `./gradlew check`, `./harness check`, `npm run hooks:validate`가 통과한다.
