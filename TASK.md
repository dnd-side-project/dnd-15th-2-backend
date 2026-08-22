# GitHub Issue #189 Task Contract

> Generated at: `2026-08-23T02:07:06+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Notification 도메인 OpenAPI 설명 개선`
- GitHub Issue: `#189`
- Branch: `docs/gh-189-notification-api-description`
- Base branch: `main`

## Objective

- `#189`의 담당 도메인 중 Notification(`NotificationApiSpec`, 7개 엔드포인트)
  설명을 `docs/api/OPENAPI_WRITING_GUIDE.md` 기준으로 검토·개선한다.
- 이 브랜치는 Notification 도메인만 다룬다. Feed는 별도 브랜치
  (`docs/gh-189-feed-api-description`)에서 완료했고, Answer/Media·Direction/
  Post Reaction은 또 다른 브랜치·PR로 진행한다.

## Scope

1. `/harness-api-docs` review 모드로 `NotificationApiSpec`과 요청·응답 DTO를
   6점 대조하고 `docs/reports/gh-189-API-DOCS-REVIEW-NOTIFICATION.md`를 만든다.
2. 제안을 검토해 승인한 문장만 `NotificationApiSpec`과 응답 DTO에 직접 반영한다.
3. 반영 후 `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`로
   `docs/api/openapi.json` 재생성 여부를 로컬에서 확인한다(최종 동기화는 CI의
   `sync-api-docs` job이 담당).
4. 6점 대조 중 "API 계약이 이상하다"고 판단되는 항목은 이 브랜치에서 고치지
   않고 별도 GitHub Issue로 분리해 기록만 남긴다.

## Explicit exclusions

- Feed, Answer/Media, Direction/Post Reaction 도메인 설명 개선. 각각 별도 브랜치.
- API 동작·비즈니스 로직 변경. 문서가 코드와 다르면 문서를 코드에 맞춘다.
- API 계약 변경(오류 코드·상태 코드 변경 등)은 이 브랜치에서 하지 않고 별도
  이슈로 분리한다.
- GitHub Pages 정적 문서 제공. `#189`에서 보류 처리됨(저장소 admin 권한 없음).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Notification `NotificationApiSpec` 문장 (`docs/api/OPENAPI_WRITING_GUIDE.md` 기준) | Byuntil | 6점 대조 결과와 문장 기준 일치 |
| 검토 산출물 (`docs/reports/gh-189-API-DOCS-REVIEW-NOTIFICATION.md`) | Byuntil | 임시 산출물, PR에는 요약만 남기고 커밋 여부는 도메인 담당자 판단 |

## Existing user-owned changes

- 브랜치 생성 시 `git status --short`는 clean이었다. 범위 밖 변경 없음.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] `NotificationApiSpec` 7개 엔드포인트 전체가 6점 대조를 거쳤다.
      `docs/reports/gh-189-API-DOCS-REVIEW-NOTIFICATION.md`에 기록.
- [x] 승인된 문장 개선이 반영됐고 DTO에 없는 사실을 지어내지 않았다. 반영 내역:
      - `@Tag` 설명 교체 1건(이슈 번호 `(#176)` 제거)
      - `@Operation` `summary`·`description` 7건 전면 재작성
      - `@ApiResponse` 문장 개선 4건(404 계정 경로 보강 2건, 409 괄호 정리 1건,
        200 문구 정리 1건)
      - query parameter `@Parameter(description)` 3건 추가
      - 응답 필드 `@Schema(description)` 31건 추가, enum 문자열 필드
        `allowableValues` 4건 추가
      - 근거를 댈 수 없는 문장 1건 삭제(`REVOKED·DISMISSED 줄은 제외됩니다`)
- [x] `docs/api/openapi.json`이 최신 코드에서 재생성됐다(로컬 재생성 확인,
      최종 동기화는 PR 시 CI `sync-api-docs` job).
- [x] 계약이 이상해 보이는 항목은 코드를 고치지 않고 별도로 기록했다.
      이 도메인은 6점 대조 3·4번에서 오류 응답 불일치가 **없었다.** 대신
      `docs/api/OPENAPI_WRITING_GUIDE.md` §8 예시가 저장소 코드와 어긋나는 건을
      보고서 §4.2에 남겼다(가이드는 `#191` 소유라 이 브랜치에서 고치지 않음).
- [x] 완료 전 검증을 모두 실행하고 실패·미실행 범위를 구분해 기록했다.
      아래 Validation evidence 참고. 미실행 범위 없음.

### Validation evidence (2026-08-23)

- `./gradlew compileJava`: 통과.
- `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`:
  반영 전 기준선 확인(diff 없음)과 반영 후 두 번 실행, 모두 통과. 반영 후
  `docs/api/openapi.json` diff 122줄(95 추가/27 삭제).
- 산출물 재파싱 검증: 응답 필드 31개 중 description 없는 필드 **0개**,
  `list`의 query parameter 3개 전부 채워짐.
- 변경 범위 검증: 산출물 diff의 변경 키 91개 중 notification 밖은 `tags` 배열
  14개뿐이고, 태그 집합·개수는 동일하며 "알림함" 항목의 배열 위치만 이동했다.
  다른 도메인 태그의 내용 변경은 없다.
- `./harness pr-ready --project-tests`: **통과.** `BUILD SUCCESSFUL in 5m`,
  종료 코드 0. convention·commit-msg formatter·workflow(4개 파일)·label·husky
  검증과 `:test`·`:integrationTest`·`:check` 전부 통과.
- 미실행 범위 없음.

#### 테스트 환경 문제 1건 (구현 문제 아님)

- 실패한 명령: `./harness pr-ready --project-tests` (1차 시도)
- 증상: `:integrationTest`가 15분 넘게 진행되지 않고 멈춤. 종료되지 않음.
- 원인: Testcontainers가 PostgreSQL 컨테이너를 호스트 포트 53961에 퍼블리시했는데
  그 포트의 IPv4 주소를 편집기의 `java-lsp-proxy`가 이미 점유하고 있었다. JDBC가
  PostgreSQL이 아니라 그 프로세스에 연결돼 소켓 read에서 무기한 대기했다.
- 근거: 테스트 워커 스택이 `VisibleBufferedInputStream.readMore`에서 정지,
  컨테이너의 `pg_stat_activity`에 클라이언트 연결 0건, `lsof`에서 해당
  ESTABLISHED 연결의 상대가 컨테이너가 아닌 LSP 프로세스로 확인됨.
- 조치: 1차 실행을 중단하고 재실행. 재실행에서 다른 포트를 배정받아 5분 만에 통과.
- 미검증 범위: 없음. 재실행이 전 범위를 통과했다.
- 남은 위험: 로컬 환경 한정이며 CI에는 해당하지 않는다. 같은 포트가 다시 겹치면
  재발할 수 있으나 확률이 낮고 재실행으로 해소된다.
