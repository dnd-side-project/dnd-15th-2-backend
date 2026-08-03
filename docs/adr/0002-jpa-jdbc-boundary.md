---
id: ADR-0002
title: Aggregate CRUD는 JPA, 데이터베이스 특화 연산은 JDBC를 사용한다
status: proposed
category: DATA
date: 2026-08-03
tags: [jpa, jdbc, postgis, transaction]
related: [GH-35, GH-37, GH-38, GH-39, GH-40]
---

# ADR-0002. Aggregate CRUD는 JPA, 데이터베이스 특화 연산은 JDBC를 사용한다

## 배경

일반 aggregate의 생명주기와 변경 감지는 JPA에 잘 맞지만, 방향 소통의 핵심
연산에는 PostGIS 거리·방향 계산, 수신 용량 조건부 갱신, 행 잠금, 수신자
snapshot bulk insert, Outbox claim이 포함된다. 이를 모두 Entity 관계로 감싸면
SQL 의도와 lock 대상이 가려지고 feature 간 결합이 커진다. 반대로 모든 CRUD를
JDBC로 작성하면 단순 저장 코드와 매핑 중복이 늘어난다.

## 고려한 선택지

1. 모든 persistence를 JPA/Hibernate Spatial로 구현한다.
2. 모든 persistence를 Spring JDBC로 구현한다.
3. aggregate CRUD와 DB 특화 연산의 책임을 나눈다.

## 결정

### JPA가 담당하는 작업

- 하나의 aggregate 경계 안에서 발생하는 일반 생성·단건 조회·상태 변경
- Account, Question, Answer, Safety, Notification의 단순 CRUD
- enum, ID, timestamp 등 반복되는 ORM 매핑
- aggregate 내부에서만 필요한 객체 관계

### JDBC가 담당하는 작업

- PostGIS 거리·방위·sector 후보 조회
- `SELECT ... FOR UPDATE`, `SKIP LOCKED`, 조건부 `UPDATE`처럼 lock과 성공 행
  수가 계약인 연산
- 발송 시점 수신자 snapshot bulk insert
- 집계·projection·대량 갱신
- Outbox claim과 재시도 상태 전이
- trigger/partial index 동작에 의존하는 SQL

### 경계 규칙

- domain/application 계층은 repository port에 의존하고 Spring Data interface나
  JDBC 구현을 직접 알지 않는다.
- 다른 feature의 Entity 또는 Spring Data Repository를 직접 참조하지 않는다.
  외부 aggregate 연결은 FK ID 값으로 보관한다.
- JPA와 JDBC를 함께 쓰는 command는 같은 Spring transaction과 DataSource를
  사용한다. JDBC가 JPA 변경을 읽어야 하면 명시적으로 flush한다.
- JPA write의 `updated_at`은 auditing으로, JDBC write는 SQL에서 명시적으로
  갱신한다. 범용 DB update trigger는 두지 않는다.
- DB constraint는 최종 무결성 경계이며 annotation은 빠른 애플리케이션 검증을
  위한 보조 수단이다.
- lazy collection을 API 계층까지 전달하지 않고 use case가 필요한 projection을
  명시적으로 조회한다.

## 선택 이유

- aggregate 코드의 가독성을 유지하면서 SQL, lock, 공간 연산을 숨기지 않는다.
- feature 간 ORM graph와 N+1을 예방한다.
- 동시성 불변식을 영향받은 row count와 실제 PostgreSQL 통합 테스트로 검증할
  수 있다.

## 결과

### 장점

- 단순 CRUD와 복잡 SQL이 각자 적합한 도구를 사용한다.
- PostGIS와 동시성 쿼리의 실행 계획을 직접 검토할 수 있다.
- 단계별 Issue에서 aggregate 단위로 JPA 도입 범위를 제한할 수 있다.

### 단점

- 한 transaction에서 flush 순서를 명시해야 하는 경우가 있다.
- 같은 row를 JPA와 JDBC가 동시에 수정하면 persistence context가 stale할 수
  있으므로 소유자를 한쪽으로 정하거나 refresh가 필요하다.
- JPA/JDBC adapter 모두에 대한 Testcontainers 통합 테스트가 필요하다.

## 검증 원칙

- 일반 repository contract는 JPA 통합 테스트로 검증한다.
- PostGIS, lock, 조건부 갱신, trigger는 실제 PostgreSQL/PostGIS 컨테이너에서
  검증한다.
- 모든 JUnit 5 테스트는 저장소의 `@DisplayName`, 생성 시각, source scenario
  규칙을 따른다.

## 관련 자료

- GitHub Issue: #35, #37, #38, #39, #40
- ADR-0001: `docs/adr/0001-database-schema-ownership.md`
- Schema manifest: `docs/product/data-model/schema-manifest.md`
