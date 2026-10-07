# GitHub Issue #325 Task Contract

> Generated at: `2026-10-07T23:02:27+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `업로드 이미지의 EXIF 위치정보 제거`
- GitHub Issue: `#325`
- Branch: `feat/gh-325-exif-strip-on-confirm`
- Base branch: `main`

## Objective

- TASK-ID: GH-325-EXIF-STRIP
- 업로드 이미지가 EXIF GPS를 담은 채 다른 사용자에게 서빙된다. 질문글, 답변, 프로필이 모두 같은 confirm 경로를 쓴다.
- D8 결정(A안, 2026-10-07)에 따라 confirm에서 API 서버가 EXIF를 지운다.
- 테스트 계획(`TEST-PLAN-GH-325-EXIF-STRIP`)을 사람이 승인하기 전에는 구현을 시작하지 않는다.

## Scope

| 항목 | 내용 |
| --- | --- |
| EXIF 제거 모듈 | JPEG·PNG 무손실 제거, 허용 목록 방식. Orientation과 ICC 프로파일만 남긴다. S3·Spring에 의존하지 않는다 |
| confirm | 원본 읽기 → 제거 → serving key에 저장 → READY와 `exif_stripped = true`. 실패하면 REJECTED |
| key 분리 | upload key와 serving key를 나누고, 조회 URL은 serving key로만 발급한다 |
| 저장소 포트 | `ObjectStoragePort`에 크기 상한이 있는 전체 읽기와 쓰기를 추가한다 |
| 도메인 | `MediaAsset`에 `exifStripped`를 추가하고, READY면 true라는 불변식을 건다 |

정해진 세부 사항(2026-10-07 사람 승인):

- `byte_size`, `checksum`은 원본 값을 유지한다.
- 기존 READY 이미지는 별도로 처리하지 않는다(출시 전 테스트 데이터로 본다).
- 원본 이전 버전 180일 보존은 이번에 바꾸지 않는다.

## Explicit exclusions

- Lambda 전환(B안), 이미지 moderation, 썸네일·리사이즈
- 원본 이전 버전 삭제와 lifecycle 변경(인프라 변경, 별도 Issue)
- API 요청·응답 스키마 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 테스트 계획 | Test orchestrator | 사람의 계획 승인 |
| production 구현(`src/main/**/answer/**`, `src/main/**/account/**`, `src/main/**/feed/**`) | Feature executor | 승인된 계획 범위 안의 변경인지 독립 검토 |
| 단위 테스트(`src/test/**`) | Test executor | 실제 변경과 실행 결과의 독립 검토 |
| 통합 테스트(`src/integrationTest/**`) | Test executor | 실제 변경과 실행 결과의 독립 검토 |

## Existing user-owned changes

- 새 worktree를 `origin/main`(`1a3b1254`)에서 만들었다. 작업 시작 시 `git status --short`는 `TASK.md`만 수정 상태로 표시했다(`h task-init` 결과).

## Validation

```bash
./harness check
./harness pr-ready --project-tests
./gradlew test --tests "com.dnd.qello.answer.*"
./gradlew integrationTest --tests "*ExifStripIntegrationTest" --tests "*MediaAssetStorageIntegrationTest" --tests "*MediaAttachmentIntegrationTest" --tests "*FeedMediaViewUrlIntegrationTest" --tests "*ProfileImageIntegrationTest"
git diff --check
```

## Completion criteria

- [x] 테스트 계획을 작성했고 사람이 승인했다(2026-10-07T23:13:07+09:00, H1·H2·H3 권장안).
- [x] 테스트 계획 Revision 1(계획 밖 테스트 파일 3개 수정, 독립 검토 반영, UNIT-023~027 추가)을 사람이 확인했다(2026-10-08T00:23:58+09:00).
- [x] READY 이미지에 위치정보가 없고 `exif_stripped = true`다(UNIT-016, INT-001·002·004·008).
- [x] READY 뒤 같은 presigned URL로 다시 올려도 조회 이미지는 바뀌지 않는다(INT-003).
- [x] 처리에 실패한 이미지는 REJECTED다. 저장소 장애는 503과 UPLOADING 유지다(UNIT-017·018, INT-005).
- [x] Orientation이 유지된다(UNIT-001·007, INT-001).
- [ ] 로그에 storage key와 좌표가 나오지 않는다. 서비스 경로는 확인했다(INT-007). 저장소 장애가 HTTP 응답으로 바뀔 때 `GlobalExceptionHandler`가 남기는 SDK 예외 원인 로그는 확인하지 못했다(보고서 6절).
- [ ] 필수 검증을 실행했다. `./harness check`, `./gradlew check`(단위 1,264건·통합 796건), `npm run hooks:validate`, `git diff --check`는 통과했다. `./harness pr-ready`는 `origin/main`이 앞서가 sync 게이트에서 멈췄고, sync에는 커밋이 필요하다.
