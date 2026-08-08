# GitHub Issue #73 Task Contract

> Generated at: `2026-08-07T20:52:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[B] 앱 사용자 기기 자격증명과 액세스 토큰 발급`
- GitHub Issue: `#73`
- Branch: `feat/gh-73-device-credential-token`
- Base branch: `main`
- 상위 Issue: `#71` `[Foundation] 인증·인가 기반 구축`
- 선행 Issue: `#72` `[A] 백오피스 운영자 로그인과 Spring Security 골격` — PR #77로
  이미 `main`에 merge됨(`7c8ea8c`). 이 브랜치는 그 시점의 `origin/main`에서
  새로 분기했다.
- 설계 근거: `docs/product/AUTH_DESIGN.md` 3.2절(`device_credential` 스키마),
  4절(앱 사용자 인증 전체 흐름), 7절(패키지 구조), 9절(작업 분해).
  `docs/adr/0006-split-operator-and-device-authentication.md`가 운영자/기기
  분리 결정을 이미 기록했으므로 이번 이슈에서 신규 ADR은 만들지 않는다.

## Objective

익명 사용자를 식별한다. 클라이언트가 만든 `installationId`를 인증 수단으로 쓰지
않고, 서버가 발급한 `device_secret`으로만 인증한다. #72에서 세운 `/api/**` 체인
(`STATELESS`, CSRF 비활성, 현재는 `anyRequest().authenticated()`뿐이라 진입 불가)에
기기 등록·토큰 재발급 엔드포인트와 JWT 검증을 실제로 연결해 앱 API 인증 기반을
완성한다.

## Scope

### V7 마이그레이션

- 적용된 `V1`~`V6`는 수정하지 않고 `V7`을 신규 추가한다. `V6`은 #72에서 이미
  Spring Session 테이블에 쓰였으므로 설계 문서의 예시 번호와 무관하게 `V7`로
  잡는다.
- `device_credential` 테이블을 `docs/product/AUTH_DESIGN.md` 3.2절 DDL대로
  만든다: `id`, `user_id`, `installation_id`, `secret_hash CHAR(64)`,
  `platform`, `credential_status DEFAULT 'ACTIVE'`, `last_used_at`,
  `created_at`, `revoked_at`.
- 제약: `fk_device_credential_user`(`user_id → user_account.id`,
  `ON DELETE CASCADE`), `ck_device_credential_platform`(`IOS`/`ANDROID`),
  `ck_device_credential_status`(`ACTIVE`/`REVOKED`),
  `ck_device_credential_revoked_at`(`(status='REVOKED') = (revoked_at IS NOT NULL)`),
  `ck_device_credential_installation_id`(공백 아님).
- 인덱스: `secret_hash` unique(해시 조회용), `installation_id`는
  `credential_status = 'ACTIVE'`인 행만 unique(재등록 충돌 판정),
  `user_id`는 ACTIVE 행만 부분 인덱스.
- `push_device`와 스키마·명명 관례(`credential_status` vs `device_status`,
  revoked 쌍 체크, partial unique index)를 맞추되 별도 테이블로 둔다. 이유는
  설계 문서 3.3절(자격증명은 우리가 발급·해지, 푸시 토큰은 외부 소유이며
  생명주기가 다름)을 따른다.

### auth 패키지 확장

`com.dnd.qello.auth`에 기기 인증 하위 구성을 추가한다(#72가 이미 만든
`config`/`domain`/`error`/`repository`/`security`/`service`/`web`은 유지).

- `domain`: `DeviceCredential`, `CredentialStatus`(기존 `OperatorCredential`
  패턴을 따르는 불변 도메인, DB CHECK와 동일한 불변식을 도메인에서도 검증).
- `security`: `DeviceSecret`(record, `SecureRandom` 32바이트, `toString`
  REDACTED — 기존 `RawPassword`와 동일 패턴), `DeviceSecretGenerator`,
  `DeviceSecretHasher`(SHA-256, bcrypt 아님 — 이유는 AUTH_DESIGN.md 4.2절:
  256bit 랜덤 시크릿은 무차별 대입이 불가능하고 인덱스 조회가 필요하므로
  고정 해시가 맞다).
- `repository`: `DeviceCredentialRepository`(port) + `jpa` 어댑터
  (Entity/Mapper/JpaRepository). ADR-0002 기준 JPA. `installation_id` 유니크
  위반은 `DataIntegrityViolationException` → `AuthErrorCode` 409로 매핑한다
  (`JpaOperatorCredentialRepository`의 기존 매핑 패턴을 재사용).
- `service`: `DeviceRegistrationService`(계정 생성 + 자격증명 발급 단일
  트랜잭션), `DeviceTokenService`(재발급: secret 해시 조회 → 상태 확인 →
  `user_account.status` 확인 → `last_used_at` 갱신 → 토큰 발급).
- `token`: `AccessTokenIssuer`(`NimbusJwtEncoder` 래핑), `AccessTokenProperties`
  (`@ConfigurationProperties(prefix = "qello.auth.access-token")` — `issuer`,
  `audience`, `ttlSeconds`(기본 1800), 서명 키는 프로퍼티 기본값에 두지 않고
  환경변수로만 주입. `OperatorSeedProperties`가 이미 쓰는 패턴을 따른다).
- `web`: `DeviceAuthController`(`POST /api/v1/auth/devices`,
  `POST /api/v1/auth/token`), 요청/응답 DTO.
- `AuthErrorCode`에 기기 인증 실패 코드를 추가한다(기존 `AUT-*` 번호 이어서):
  등록 시 `installation_id` 충돌(409), 재발급 시 자격증명 불일치(401), 재발급
  시 `credential_status != ACTIVE`(401), 계정이 `ACTIVE`가 아님(403 — 기존
  `ACCOUNT_NOT_ACTIVE` 재사용 가능 여부를 구현 중 판단).

### Spring Security 구성 갱신

- `/api/**` 체인에 `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`를 연결해
  `NimbusJwtDecoder`로 액세스 토큰을 검증한다. 서명 키(HS256)는 환경변수로만
  주입하고 저장소에 기본값을 두지 않는다.
- `POST /api/v1/auth/devices`, `POST /api/v1/auth/token`만 `permitAll`,
  나머지 `/api/**`는 인증 필요. 이번 이슈에서는 `role` 클레임 기반 세분화된
  인가까지는 만들지 않는다(다음 앱 API 이슈에서 사용).
- JWT 클레임: `iss=qello`, `sub=userId`, `aud=qello-app`, `role`, `did`(기기
  자격증명 id), `jti`, `iat`, `exp`(AUTH_DESIGN.md 4.5절).

### 문서와 테스트 동기화

- `FlywayMigrationContractTest`의 migration 이름 목록에 `V7`을 추가한다.
- `FlywayMigrationIntegrationTest`의 적용 개수(6→7)와 catalog 카탈로그 개수를
  `V7` 기준으로 갱신한다.
- `AccountPersistenceIntegrationTest`의 migration 버전 조건(`V1~V6` →
  `V1~V7`)을 갱신한다.
- `docs/product/data-model/direction_communication.dbml`과
  `schema-manifest.md`에 `device_credential`을 반영한다.
- `docs/error-codes.md` 12절(`AUT`)에 신규 코드를 추가한다.

## Explicit exclusions

- 다기기 지원 (설계 문서 8.3절, 제품 결정 후 별도 Issue)
- 등록 rate limit, 기기 무결성 검증(Play Integrity/App Attest) (설계 문서
  8.2절, 제품 결정 후 별도 Issue)
- 차단 사용자 캐시(즉시 차단 2단계, 설계 문서 4.6절) — 기본 TTL 기반 대응만
  이번 이슈 범위이며, 캐시는 F08 정책 확정 후 도입한다.
- 계정 복구 코드(설계 문서 8.1절, 제품 결정 후 별도 Issue)
- `role` 클레임 기반 세분화된 `/api/**` 인가 정책 (다음 앱 API 이슈)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| V7 migration과 스키마 문서 | Auth executor | unique index·FK CASCADE·되돌리기 영향 리뷰 |
| auth 기기 도메인·서비스·오류 코드 | Auth executor | `device_secret` 평문 비노출·경계 리뷰 |
| Spring Security `/api/**` JWT 연결 | Auth executor | 서명 키 관리·permitAll 범위 리뷰 |
| 단위/통합 테스트 | Test orchestrator | 재등록 충돌·차단 반영·해시 조회 회귀 리뷰 |

## Existing user-owned changes

- 세션 시작 시 작업 트리는 `M TASK.md` 하나였다. 그 내용은 이전(#72) 브랜치에서
  `h task-init`이 만들었으나 커밋되지 않은 `#72` task 계약 스캐폴드였고, 같은
  내용의 작업은 이미 PR #77로 `origin/main`에 merge되어 있어 더 이상 유효하지
  않았다. `git stash push -u -- TASK.md`로 보존한 뒤(스택 메시지: "stale gh-72
  TASK.md scaffold (superseded by merged PR #77)") `./harness start`로 최신
  `origin/main`(`7c8ea8c`)에서 이 브랜치를 새로 만들었다. 필요하면
  `git stash list`에서 복원할 수 있다.
- 그 외 다른 사람의 미커밋 변경은 없다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

- Docker가 사용 가능하므로 Testcontainers 기반 통합 테스트를 로컬에서 실행한다.
  `./gradlew test`와 `./gradlew integrationTest`를 모두 통과시킨다.

## Completion criteria

- [x] `V7`이 `device_credential`을 만든다. `V1`~`V6`는 수정하지 않는다.
- [x] `device_secret`은 등록 응답에서만 평문으로 나오고 이후 로그·응답·예외
      어디에도 남지 않는다(`DeviceSecret.toString()` REDACTED).
- [x] 같은 `installation_id`로 재등록하면 409를 받는다.
- [x] BLOCKED 또는 DELETED 계정은 토큰 재발급 시 403을 받는다.
- [x] 액세스 토큰 만료가 30분이고 `role` 클레임이 포함된다.
- [x] `device_secret` 해시에 bcrypt를 쓰지 않는다(SHA-256, 인덱스 조회 가능).
- [x] `/api/**` 체인이 `NimbusJwtDecoder`로 액세스 토큰을 검증하고, 등록·재발급
      경로만 인증 없이 열려 있다.
- [x] Flyway 계약·통합 테스트와 스키마 문서가 `V7` 기준으로 갱신된다.
- [x] `./gradlew test`와 `./gradlew integrationTest`가 통과한다.
- [x] `./harness pr-ready --project-tests`가 통과한다.
