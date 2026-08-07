---
id: ADR-0006
title: 운영자는 서버 세션, 앱 사용자는 기기 자격증명 기반 토큰으로 인증한다
status: proposed
category: SECURITY
date: 2026-08-06
tags:
  - authentication
  - session
  - jwt
  - device
related:
  - "#48"
  - "#71"
  - "#72"
  - "#73"
---

# ADR-0006. 운영자는 서버 세션, 앱 사용자는 기기 자격증명 기반 토큰으로 인증한다

## 배경

Qello는 성격이 다른 두 사용자군에게 인증을 제공해야 한다.

- **운영자(OPERATOR)**: 팀원 5명 내외. 브라우저 백오피스에서 `login_id`와
  비밀번호로 로그인한다. 신고 처리, 계정 차단, 질문 검토 권한을 가진다.
- **앱 사용자(USER)**: 익명 대중. iOS/Android 네이티브 앱에서 별도 가입 절차
  없이 기기로 식별되고 세션을 장기간 유지한다(F01 익명 계정).

두 군은 클라이언트 종류, 인증 수단, 세션 수명, 위협 모델이 모두 다르다. 하나의
인증 체계로 묶으면 한쪽 요구사항이 다른 쪽의 보안 설정을 훼손한다. 예를 들어 앱
API를 위해 CSRF를 끄면 백오피스가 노출되고, 백오피스를 위해 세션을 만들면 앱
API가 상태를 갖게 된다.

현재 `user_account`에는 `role`과 `status`만 있고 로그인 식별자, 기기 자격증명,
세션 저장소가 모두 없다. 인증 기능을 시작하기 전에 방식을 확정해야 한다.

## 고려한 선택지

1. 두 사용자군 모두 JWT 기반 stateless 인증을 사용한다.
2. 두 사용자군 모두 서버 세션과 쿠키를 사용한다.
3. 사용자군별로 인증 방식을 분리한다. 운영자는 서버 세션, 앱 사용자는 기기
   자격증명 기반 단기 토큰을 사용한다.

## 결정

선택지 3을 채택한다.

**운영자**는 `login_id` + bcrypt 비밀번호로 로그인하고, 서버 세션(Spring Session
JDBC)과 HttpOnly 쿠키로 인증 상태를 유지한다. CSRF 토큰을 적용한다.

**앱 사용자**는 최초 실행 시 서버가 발급한 256bit `device_secret`을 기기 보안
저장소에 보관하고, 이를 제시해 30분 수명의 access token(JWT)을 재발급받는다.
클라이언트가 생성한 `installation_id`는 식별 힌트일 뿐 인증 수단으로 쓰지
않는다. 별도의 refresh token을 두지 않고 기기 자격증명이 재발급 자격증명을
겸한다.

Spring Security는 경로 기준으로 `SecurityFilterChain`을 분리한다. `/admin/**`은 세션·CSRF
적용, `/api/**`은 stateless·Bearer 토큰을 적용하고, 두 경로 밖을 `denyAll`로 막는 fallback
체인을 마지막에 둔다. 매칭되는 체인이 없는 요청은 Spring Security가 그대로 통과시키기 때문이다.

로그인 POST에도 CSRF를 적용하므로(login CSRF 방어) 토큰을 받아갈 `GET /admin/csrf`를 인증
없이 연다. 이 경로가 없으면 `/admin/**`이 전부 인증 필요라 토큰을 얻을 방법이 없어 로그인
자체가 불가능하다.

운영자 계정은 자체 가입 API 없이 기동 시 시더로만 만든다. 자격증명은 환경변수로 주입하고
저장소에 남기지 않는다.

상세 스키마, API 계약, 패키지 구조는 `docs/product/AUTH_DESIGN.md`를 따른다.

## 선택 이유

- 운영자는 차단·삭제 권한을 가지므로 **즉시 해지**가 필요하다. 서버 세션은 행을
  지우면 끝나지만 JWT는 만료까지 살아 있다.
- 브라우저에는 토큰을 안전하게 둘 곳이 없다. `localStorage`는 XSS에 그대로
  노출되고, 결국 쿠키에 담을 것이라면 처음부터 세션이 단순하다.
- 앱 API는 요청량이 많고 스케일 아웃 대상이라 요청마다 세션 조회 DB 히트가
  발생하는 구조를 피해야 한다.
- 네이티브 앱은 쿠키 저장소가 불안정한 반면 Keychain/Keystore는 안정적이다.
  기기 자격증명은 앱 환경에 맞는 저장 수단이 이미 존재한다.
- 비밀번호가 없는 익명 계정에서는 refresh token 회전의 이득이 작다. 회전의
  목적은 탈취 탐지 후 재로그인 유도인데 돌아갈 비밀번호가 없다. 회전 실패 시
  계정 영구 상실 위험만 늘어난다.
- 운영자 5명 규모에 세션 저장소로 Redis를 새로 도입할 근거가 없다. 기존
  PostgreSQL에 Spring Session JDBC로 충분하다.

## 결과

### 장점

- 두 체인이 서로의 보안 설정을 오염시키지 않는다. 백오피스에서 CSRF가 꺼지거나
  앱 API에 세션이 생기는 사고가 구조적으로 차단된다.
- 운영자 권한 회수가 즉시 반영된다.
- 앱 API는 stateless라 수평 확장에 제약이 없다.
- 새 인프라 구성 요소를 추가하지 않는다.

### 단점

- 인증 코드 경로가 두 벌이 되어 구현·테스트 대상이 늘어난다.
- 앱 사용자 차단은 access token TTL(최대 30분)만큼 반영이 지연된다. 즉시 차단이
  필요하면 차단 사용자 캐시를 추가해야 한다.
- 기기 자격증명을 잃으면 익명 계정을 복구할 수 없다. 복구 코드 도입 여부는 제품
  결정으로 남아 있다.
- `spring-boot-starter-security`, `oauth2-resource-server`,
  `spring-session-jdbc` 의존성이 추가된다.

## 관련 자료

- GitHub Issue: #71 (인증·인가 기반 구축), #72 (운영자 로그인), #73 (기기 자격증명)
- 선행 Issue: #48 (`user_account.password_hash` 추가. V5에서 `operator_credential`로 이동)
- PR:
- 문서: `docs/product/AUTH_DESIGN.md`, `docs/product/BACKEND_ROADMAP.md` (F01)
- 관련 결정: `docs/adr/0005-api-success-response-contract.md` (응답 본문 형식),
  `docs/adr/0002-jpa-jdbc-boundary.md` (JPA·JDBC 경계)
