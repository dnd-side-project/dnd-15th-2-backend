# GitHub Issue #166 Task Contract

> Generated at: `2026-08-18T23:30:13+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `프로필 이미지 업로드와 기본 이미지 제공`
- GitHub Issue: `#166`
- Branch: `feat/gh-166-profile-image-upload`
- Base branch: `main`
- 분기 시점 `origin/main`은 `e7086dc`(PR `#165` 병합 커밋)다.

## Objective

- 회원가입한 일반 사용자가 프로필 이미지를 가질 수 있어야 한다. 지금은
  `user_account`에 프로필 이미지 컬럼이 없고 `account` 도메인에 web 계층이
  없어 프로필을 조회하거나 변경할 방법 자체가 없다.
- 업로드 인프라는 이미 있다. `media_asset`은 `owner_id` 기반 범용 자산이고
  `POST /api/v1/media-assets`가 presigned PUT을 발급한 뒤 `confirm`에서
  HeadObject와 시그니처로 실제 객체를 검증한다(`MediaUploadService`).
- 빠진 것은 **조회(서빙) 경로**다. `ObjectStoragePort`에는 `issuePutUrl`,
  `headObject`, `readObjectPrefix`만 있고 presigned GET이 없다. 버킷은
  private(`infra/modules/s3-private-bucket`)이라 URL 없이는 이미지를 내려줄
  방법이 없다.
- 프로필 이미지는 가입 시 필수가 아니다. 설정하지 않은 사용자에게는 S3에 미리
  올려 둔 기본 이미지를 같은 경로로 제공한다.

## Scope

1. **스키마와 도메인**
   - `user_account.profile_image_media_id BIGINT NULL` 추가(V21).
     `media_asset (id, owner_id)`를 참조하는 복합 FK로, 남의 자산을 자기
     프로필로 지정하는 것을 DB 수준에서 막는다
     (`uq_media_asset_id_owner`가 이미 있다).
   - `Account`에 프로필 이미지 설정·해제 동작 추가. `NULL`이 "기본 이미지
     사용"을 뜻하며 별도 sentinel 값을 두지 않는다.
   - `AccountJpaEntity`·`AccountJpaMapper`·`AccountRepository`에 반영한다.
     `updateProfile`은 현재 지역·로케일·타임존·닉네임만 바꾸므로 프로필
     이미지 변경 경로를 어디에 둘지 구현 시 확정한다.
2. **프로필 API 신설**
   - `account` 도메인에 web 계층을 만든다. 경로는 `/api/v1/me/profile`이다.
   - 프로필 조회, 프로필 이미지 변경, 프로필 이미지 삭제.
   - 소유자와 요청자는 `AuthenticatedUserId`로 JWT subject에서 결정한다.
     `MediaAssetController`와 같은 방식이다.
3. **가입 경로 연결 — 구현 불가로 제외했다.**
   `media_asset.owner_id`가 `user_account.id`를 FK로 참조하므로 자산은 소유자
   계정 행이 있어야 존재할 수 있고, 업로드 endpoint는 `/api/**` 체인의
   `anyRequest().authenticated()` 아래에 있어 토큰이 필요하다. 가입 전에는
   토큰이 없으니 가입 시점에 넘길 수 있는 유효한 media id가 원리적으로 없다.
   실제 순서는 기기 등록 → 업로드 → confirm → 프로필 지정이며, 사용자 관점의
   "가입할 때 사진 올리기"는 온보딩 화면에서 그대로 성립한다.
4. **조회 경로**
   - `ObjectStoragePort`에 presigned GET 발급을 추가하고 `S3ObjectStoragePort`에
     구현한다.
   - 프로필 응답이 `qello.media.view-url-ttl`(`PT5M`) 만료의 조회 URL을 반환한다.
     기본 이미지도 같은 경로로 내려준다.
   - 기본 이미지 객체 키를 설정으로 주입한다(`qello.media` 계열). 버킷 이름과
     객체 키를 코드 상수로 박지 않는다. 값이 비면 기동이 실패해야 한다
     (`MediaStorageProperties`의 기존 compact constructor 검증과 같은 방식).
5. 단위 테스트, LocalStack + PostgreSQL 통합 테스트와 테스트 계획·보고서.

## Design decisions (구현 전 확정, 리뷰 필요)

1. **`media_asset`을 재사용하고 프로필 전용 테이블을 만들지 않는다.**
   `media_asset`은 스키마상 답변 전용이 아니라 `owner_id` 기반 범용 자산이다.
   코드가 `answer` 패키지에 있는 것은 최초 도입 맥락 때문이고 스키마 제약은
   없다. 별도 테이블을 만들면 presigned 발급·confirm·시그니처 검증을 통째로
   복제해야 한다.
2. **`NULL`이 곧 기본 이미지다.** 기본 이미지를 가리키는 `media_asset` 행을
   만들어 모든 신규 계정이 그것을 참조하게 하면, 소유자 없는 자산이라는 예외를
   `owner_id NOT NULL`과 소유권 검사 전반에 뚫어야 한다. `NULL`을 읽는 쪽에서
   기본 키로 해석하는 편이 제약을 지킨다.
3. **조회는 presigned GET으로 한다.** 버킷이 private이므로 URL을 만들지 않으면
   내려줄 수 없다. 객체를 public-read로 바꾸는 것은 공개 접근 범위 확대라
   `AGENTS.md` 6절의 고위험 변경이고, CDN 도입은 이 이슈 범위를 넘는다.
4. **기본 이미지 객체는 이미 버킷에 있다고 전제한다.** 적재와 그에 필요한
   Terraform 변경은 이 이슈에서 하지 않는다. 설정 키가 비면 기동을 실패시켜
   누락을 조용히 넘기지 않는다.
5. **이미지 moderation은 하지 않는다.** `FilterTargetType`은 `ANSWER`와
   `NICKNAME`뿐이고 이미지 판정기가 없다. `media_asset.moderation_status`는
   기존 기본값을 그대로 둔다.
6. **프로필에 붙은 자산이 나중에 `DELETED`가 되면 기본 이미지로 폴백한다.**
   프로필 참조를 그때 지우지는 않는다. 읽기 경로가 쓰기를 하면 조회가 낙관적
   락 충돌을 일으킬 수 있고, 자산이 되살아나는 경로가 생겼을 때 원래 참조를
   잃는다. 폴백은 읽는 쪽의 해석으로만 처리한다. 설정 시점의 `READY` 검증은
   그대로다 — `DELETED` 자산을 새로 지정하는 것은 여전히 거부한다.
7. **조회 URL TTL은 업로드 TTL과 분리한다.** `qello.media.upload-url-ttl`이
   `PT10M`인 것은 최대 10MB PUT 하나가 느린 회선에서 끝날 시간을 재기 때문이고,
   조회 URL이 살아 있어야 하는 시간은 응답 수신부터 렌더링까지다. private 버킷의
   presigned GET은 그 객체에 대한 bearer 자격증명이라 수명이 곧 노출 창이다.
   두 값을 묶으면 나중에 업로드 TTL을 늘릴 때 조회 URL 수명이 함께 늘어나
   성능 튜닝의 부수효과로 보안 속성이 바뀐다. `qello.media.view-url-ttl`을
   따로 두고 `PT5M`으로 한다.

## Explicit exclusions

- 이미지 내용 moderation과 `FilterTargetType` 확장.
- 이미지 리사이즈, 썸네일 생성, EXIF 제거.
- CDN(CloudFront) 도입과 캐시 정책.
- 기본 이미지 객체를 버킷에 적재하는 작업과 그에 필요한 Terraform 변경.
- 로그인(`POST /api/v1/auth/token`) 경로 변경. 로그인은 자격증명 교환이라
  프로필 업로드를 붙이지 않는다.
- 프로필의 나머지 필드(닉네임·지역·로케일·타임존) 변경 API. 이 이슈는 프로필
  이미지만 다룬다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `user_account` 스키마 변경, `Account` 도메인, 프로필 API, presigned GET, 기본 이미지 설정, 테스트 | Feature executor | 남의 자산이나 `READY`가 아닌 자산을 프로필로 지정할 수 있는 경로가 없는지, 버킷 이름·객체 키가 API 응답이나 로그에 노출되지 않는지, 기본 이미지 설정 누락이 조용히 통과하지 않는지 |

## Existing user-owned changes

- `origin/main`(e7086dc)에서 새로 분기했다. 분기 시점 `git status --short`는
  비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 프로필 이미지 없이 회원가입할 수 있고, 그 사용자의 프로필 조회 응답이
      기본 이미지 URL을 반환한다. (INT-004가 발급 URL로 실제 200을 받는다)
- [x] 업로드 → confirm → 프로필 지정 흐름으로 프로필 이미지를 설정할 수 있고,
      삭제하면 기본 이미지로 되돌아간다. (INT-003, INT-007)
- [x] 다른 사용자가 소유한 `media_asset`을 자기 프로필로 지정하면 거부된다.
      애플리케이션 검증(UNIT-005, INT-010)과 DB 복합 FK(INT-002) 양쪽에서
      막힌다. 남의 자산과 없는 자산을 같은 404로 응답해 열거 오라클을 만들지
      않는다.
- [x] `READY`가 아닌 자산(`UPLOADING`, `REJECTED`, `DELETED`)은 프로필로
      지정할 수 없다. (UNIT-006~008)
- [x] 프로필에 붙은 자산이 나중에 `DELETED`가 되면 조회가 오류가 아니라 기본
      이미지로 폴백하고 참조는 남는다. (UNIT-018, INT-013)
- [x] 프로필 이미지 URL은 만료가 있는 presigned URL이며, 버킷 이름과 객체 키를
      응답에 그대로 노출하지 않는다. (UNIT-012가 응답 필드를 전수 확인한다)
- [x] 기본 이미지 키 설정이 비어 있으면 기동이 실패한다. (UNIT-014)
- [x] 승인된 테스트 계획과 실행 보고서가 존재한다.
      계획 `docs/test-plans/gh-166-TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD.md`
      (Status: Approved), 보고서
      `docs/reports/tests/gh-166-TEST-PLAN-GH-166-PROFILE-IMAGE-UPLOAD.md`.
- [x] 단위·통합 테스트가 `@DisplayName`과 클래스 헤더(생성 시각, 테스트 계획
      식별자)를 갖추고 통과한다. 단위 664건·통합 492건 전체 통과했다
      (신규 단위 16, 통합 8).

## Delivered vs deferred

- **가입 요청의 프로필 이미지 필드 없음.** 위 Scope 3번의 이유로 성립하지 않는다.
- **INT-008(동시 변경 낙관적 락) 미실행.** `@Version`은 이미 있고 이번 변경이 그
  경로를 바꾸지 않았지만 실제 동시 실행으로 확인하지는 않았다. 남은 위험은
  보고서 7절에 있다.
- **INT-011 부분 실행.** 발급 URL로 실제 객체를 받는 것은 확인했고, TTL 경과 후
  실패는 5분 대기가 필요해 스위트에 넣지 않았다.
- **기본 이미지 객체 적재는 배포 전제 조건이다.** presigned 발급은 객체 존재를
  확인하지 않아, 객체가 없으면 URL은 정상 발급되고 404를 가리킨다. 테스트로
  잡히지 않는 종류라 보고서 6절에 남겼다.
