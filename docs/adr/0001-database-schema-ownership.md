---
id: ADR-0001
title: Flyway가 실행 데이터베이스 스키마 변경을 소유한다
status: proposed
category: DATA
date: 2026-08-03
tags: [postgresql, postgis, flyway, schema]
related: [GH-34, GH-35, GH-36]
---

# ADR-0001. Flyway가 실행 데이터베이스 스키마 변경을 소유한다

## 배경

Qello 방향 소통 데이터 모델에는 26개 테이블, PostGIS, 복합 FK, partial
index, check constraint, trigger가 포함된다. Hibernate 자동 DDL이나 여러 SQL
진입점을 함께 사용하면 설계 문서와 실행 schema가 쉽게 달라진다. 기존
`sql/001~004`는 현재 26개 테이블 기준 이전의 중간 산출물이므로 새 백엔드의
migration history로 재사용할 수 없다.

## 고려한 선택지

1. Hibernate `ddl-auto=update`로 Entity에서 schema를 생성한다.
2. 시작 시 `schema.sql`을 매번 실행한다.
3. 폐기된 `001~004`를 이어서 Flyway migration으로 등록한다.
4. 현재 DBML에서 새 Flyway baseline을 만들고 이후 변경도 Flyway로만 적용한다.

## 결정

- 실행 데이터베이스 schema 변경의 유일한 경로는 Flyway versioned migration이다.
- Issue #36은 **빈 PostgreSQL/PostGIS DB 전용** 최초 migration을 만든다. 기존
  운영 DB에 `baselineOnMigrate`를 적용하거나 현재 상태를 추정하지 않는다.
- 적용된 migration은 수정·삭제하지 않는다. 변경은 DBML과 정책 근거를 먼저
  리뷰하고 새 migration으로 추가한다.
- Hibernate는 schema를 생성하거나 수정하지 않는다. Entity 도입 후에는
  `ddl-auto=validate` 또는 동등한 검증 모드만 사용한다.
- `direction_communication.dbml`은 논리 설계 source이고 Flyway history는 실행
  schema의 권위다. ERD는 두 계약을 설명한다.
- 독립 실행형 DDL은 migration 작성 참고 자료일 뿐 애플리케이션 시작 경로에서
  실행하지 않는다.
- local/test에서는 Flyway가 `CREATE EXTENSION IF NOT EXISTS postgis`를 검증한다.
  production에서는 플랫폼이 extension 활성화와 migration role 권한을 사전
  준비하며, 이 ADR은 production 변경 권한을 부여하지 않는다.
- schema manifest에 원본 checksum과 오브젝트 inventory를 기록한다.

## 선택 이유

- schema 적용 순서와 이력을 재현할 수 있다.
- JPA가 표현하기 어려운 PostGIS, trigger, partial index를 그대로 관리할 수 있다.
- 빈 DB migration 테스트로 문서와 실행 schema의 drift를 검출할 수 있다.
- production 권한과 애플리케이션 runtime 권한을 분리할 수 있다.

## 결과

### 장점

- 모든 환경이 동일한 versioned history를 따른다.
- Entity 변경이 실수로 DDL을 발생시키지 않는다.
- rollback은 과거 파일 수정이 아니라 forward-fix migration 또는 환경 복구
  절차로 명시된다.

### 단점

- DBML과 migration을 함께 리뷰해야 한다.
- destructive migration은 별도 데이터 이행·복구 계획이 필요하다.
- production PostGIS 권한 준비가 누락되면 배포 전에 migration이 실패한다.

## 금지 사항

- `ddl-auto=create`, `create-drop`, `update`
- 폐기된 `sql/001~004` 재생 또는 이름만 바꾼 복사
- 적용된 migration checksum 변경
- 사람 승인 없는 production baseline, repair, clean, apply

## 관련 자료

- GitHub Issue: #35, #36
- Schema manifest: `docs/product/data-model/schema-manifest.md`
- DBML: `docs/product/data-model/direction_communication.dbml`
