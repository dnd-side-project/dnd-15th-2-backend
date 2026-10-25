# GitHub Issue #359 Task Contract

> Generated at: `2026-10-25T23:39:23+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `2026-10 Java CPU·CVE-2026-91403 런타임 JDK 대응`
- GitHub Issue: `#359`
- Branch: `fix/gh-359-runtime-jdk-cpu-oct2026`
- Base branch: `main`

## Objective

- 운영 런타임 이미지(Temurin 21.0.12.1)가 2026-10 Oracle Java CPU 영향 버전이고 CVE-2026-91403의 유출된 eMudhra 중간 CA를 신뢰하는 상태를 해소한다.

## Scope

- 1단계: `Dockerfile` runtime 단계에서 `keytool -delete`로 emSign 루트 4개(`emsign_root_ca__g1`, `emsign_root_ca__c1`, `emsign_ecc_root_ca__g3`, `emsign_ecc_root_ca__c3`)를 cacerts에서 제거하고 CVE 번호와 제거 조건을 주석으로 남긴다.
- 2단계(Temurin 21.0.13 이미지 출시 후): `Dockerfile` build/runtime base 이미지를 21.0.13 이상 버전 태그로 고정하고 `.github/workflows/*.yml`의 `setup-java`가 21.0.13 이상을 받는지 확인한다.
- 3단계: 제거 단계 없이 빌드한 이미지의 `blocked.certs`에 eMudhra가 반영돼 있으면 1단계 제거 단계를 삭제하고, 없으면 유지한다.

## Explicit exclusions

- 라이브러리 의존성 취약점(Tomcat, Jackson, pgjdbc, Netty)은 #360에서 다룬다.
- 로컬 개발 JDK 갱신, base 이미지 다이제스트 고정과 자동 갱신 도구 도입.
- `infra/**` Terraform 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `Dockerfile` 런타임 이미지 | 실행 에이전트 | 사용자 PR 리뷰 |
| `.github/workflows/*.yml` `setup-java` 버전 확인(2단계, 변경 시) | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`6aeccd03`)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 1단계: 빌드 이미지에서 `keytool -list -cacerts -storepass changeit | grep -ci emsign` 결과가 0이다.
- 1단계: 테스트 서버 배포 후 S3 presigned URL 발급과 FCM 토큰 발급이 TLS 오류 없이 성공한다.
- 2단계: 빌드 이미지의 `java -version`이 21.0.13 이상이다.
- 3단계: 최종 이미지가 eMudhra 중간 CA 체인을 거부한다(cacerts 제거 또는 `blocked.certs` 반영).
- `./harness check`와 `git diff --check`가 통과한다.
