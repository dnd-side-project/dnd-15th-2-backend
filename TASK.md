# GitHub Issue #70 Task Contract

> Generated at: `2026-08-06T20:19:43+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `질문글·답변 이미지 첨부 MediaAsset 업로드 서비스 구현`
- GitHub Issue: `#70`
- Branch: `feat/gh-70-media-asset-service`
- Base branch: `feat/gh-67-inbox-sent-post-service`

## Objective

- 방향글(`DirectionPost`)과 답변(`Answer`) 작성 시 이미지를 첨부할 수 있도록,
  S3 presigned URL 기반 업로드 흐름과 `MediaAsset` 도메인 서비스 계층을
  구현한다.
- Terraform으로 이미 준비된 dev S3 버킷(#63/PR #68)과 이미 존재하는
  `media_asset`/`media_attachment` DB 스키마(V1 마이그레이션)를 활용해,
  지금은 비어 있는 애플리케이션 계층을 채운다.
- Issue: `#70`

## Scope

- `MediaAsset` 도메인 + JDBC 리포지토리 (상태 전이:
  UPLOADING→READY/REJECTED/DELETED)
- presigned URL 발급 서비스 (AWS SDK v2 `S3Presigner`, `storage_key` 채번
  규칙, mime/size 화이트리스트 검증)
- 업로드 완료 확인(confirm) 서비스 — `HeadObject`로 실제 업로드 검증 후
  READY 전환
- `MediaAttachmentService` — 질문글/답변 작성 시 `media_asset` attach,
  소유권·`display_order` 검증(기존 `answer.domain.MediaAttachment` record와
  `MediaAttachmentRepository`가 이미 존재 — `save()`만 있고 조회는 없음)
- Testcontainers + LocalStack 기반 통합 테스트

## Explicit exclusions

- HTTP controller, API 문서, DTO
- 고아 UPLOADING 미디어 정리 배치
- 실제 이미지 모더레이션 파이프라인 연동(`moderation_status`는 스키마상
  PENDING 유지)
- feed(Inbox/SentPost) 조회 응답에 미디어 노출
- 애플리케이션 런타임 IAM Role 생성/부착 — 컴퓨팅 인프라가 아직 없어 별도
  이슈로 유예(Infra Design Report `docs/reports/infrastructure/gh-63-D-1.md`
  §5)
- 새 Flyway migration(`media_asset`/`media_attachment`는 V1에 이미 존재,
  컬럼·인덱스만 사용)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `answer/**`(media 관련), `direction/**`(attach 통합 지점), `build.gradle`(AWS SDK 의존성 추가) | 본인(Claude Code 세션) | 사용자 |

## Existing user-owned changes

- 브랜치 생성 시점(`git status --short`) 결과: worktree에 untracked
  `src/main/java/com/dnd/.DS_Store`(macOS 자동 생성 파일)만 있었음 — 사용자
  승인 후 삭제하고 `.gitignore`에 추가(`feat(harness): ignore macOS
  .DS_Store files (#70)`). 그 외 보존해야 할 기존 변경 없음(clean 상태에서
  `feat/gh-67-inbox-sent-post-service` 위에 stacked로 분기).

## Validation

```bash
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [ ] `MediaAsset` 상태 전이 단위 테스트 통과
- [ ] presigned URL 발급이 소유자 검증과 mime/size 화이트리스트를 통과한
      요청에만 응답함을 테스트로 확인
- [ ] 업로드 완료 확인 시 실제 S3 객체가 없거나 크기/타입이 다르면
      REJECTED로 전이됨을 확인
- [ ] 다른 사용자의 `media_asset`을 attach 시도하면 실패함을 통합 테스트로
      확인(소유권 미검증 시 남의 자산이 첨부되지 않음)
- [ ] 본문 없는 질문글/답변에 READY 미디어가 없으면 attach가 거부됨을 확인
      (DB 트리거와 서비스 레벨 사전 검증 모두)
- [ ] LocalStack 기반 통합 테스트로 presigned URL 발급 → PUT → confirm
      흐름 검증
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
      `npm run hooks:validate`, `git diff --check` 통과
