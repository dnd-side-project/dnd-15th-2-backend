# Architecture Decision Records

이 디렉터리는 프로젝트에서 내린 주요 기술적 결정과 그 이유를 기록하는 공간입니다.

ADR은 **Architecture Decision Record**의 약자입니다.  
쉽게 말하면 다음 질문에 답하기 위한 문서입니다.

> 왜 우리는 이 기술과 구조를 선택했는가?

코드만 보면 현재 구현은 알 수 있지만 당시 어떤 선택지가 있었고 왜 지금의 방식을 선택했는지는 알기 어렵습니다. ADR은 이러한 결정의 배경과 근거를 코드와 함께 남깁니다.

---

## 1. ADR을 작성하는 이유

프로젝트를 진행하다 보면 다음과 같은 질문이 반복해서 발생합니다.

- 왜 JPA 대신 JdbcTemplate을 사용했나요?
- 왜 REST API로 구현했나요?
- 왜 Redis에 이 데이터를 저장하나요?
- 왜 비관적 락을 선택했나요?
- 왜 Kafka가 아니라 SQS를 사용했나요?
- 왜 이 기능을 비동기로 처리하나요?

이러한 결정이 GitHub Issue 댓글, Slack 대화, 회의록, PR 본문에 흩어져 있으면 나중에 찾기 어렵습니다.

이 프로젝트에서는 중요한 기술적 결정을 `docs/adr` 디렉터리에 모아 관리합니다.

## 현재 ADR

| ID | 제목 | 상태 |
|---|---|---|
| [ADR-0001](0001-database-schema-ownership.md) | Flyway가 실행 데이터베이스 스키마 변경을 소유한다 | accepted |
| [ADR-0002](0002-jpa-jdbc-boundary.md) | Aggregate CRUD는 JPA, 데이터베이스 특화 연산은 JDBC를 사용한다 | accepted |

역할은 다음과 같이 구분합니다.

```text
GitHub Issue
→ 무엇을 개발할 것인지 관리

Pull Request
→ 어떤 코드가 변경됐는지 리뷰

ADR
→ 왜 해당 기술과 구조를 선택했는지 기록
```

---

## 2. 디렉터리 구조

```text
docs/
└── adr/
    ├── README.md
    ├── template.md
    ├── 0001-use-postgresql.md
    ├── 0002-use-jpa-for-general-crud.md
    └── 0003-use-jdbc-for-settlement-batch.md
```

각 파일의 역할은 다음과 같습니다.

| 파일 | 역할 |
|---|---|
| `README.md` | ADR 사용 방법과 전체 목록 |
| `template.md` | 새로운 ADR을 작성할 때 복사하는 템플릿 |
| `0001-*.md` | 실제 기술적 결정 기록 |

---

## 3. 언제 ADR을 작성하나요?

모든 코드 변경에 ADR을 작성할 필요는 없습니다.

다음 질문 중 하나라도 `예`라면 ADR 작성을 고려합니다.

- 결정을 나중에 되돌리기 어려운가?
- 여러 기술이나 구조를 비교했는가?
- 팀원 간 논의가 필요했던 결정인가?
- 다른 개발자가 나중에 이유를 궁금해할 가능성이 높은가?
- 성능, 보안, 정합성, 운영 방식에 영향을 주는가?
- 프로젝트 전체 또는 여러 기능에 영향을 주는가?
- 특정 기술이나 인프라에 대한 의존성이 생기는가?

### ADR을 작성하는 예시

- PostgreSQL과 MongoDB 중 어떤 저장소를 사용할지
- JPA와 JdbcTemplate의 적용 범위를 어떻게 나눌지
- REST와 GraphQL 중 무엇을 사용할지
- JWT와 세션 중 어떤 인증 방식을 사용할지
- 동시성 제어에 낙관적 락과 비관적 락 중 무엇을 사용할지
- 이벤트 발행에 Transactional Outbox를 적용할지
- Kafka, RabbitMQ, SQS 중 무엇을 사용할지
- 배치 작업을 어떤 단위로 나누고 재실행할지
- 로그와 모니터링 구조를 어떻게 구성할지
- AWS 인프라를 CDK와 Terraform 중 무엇으로 관리할지

### ADR을 작성하지 않아도 되는 예시

- 변수명 변경
- 메서드명 변경
- 단순 리팩터링
- DTO 필드 추가
- 작은 버그 수정
- 테스트 코드 추가
- 영향 범위가 작은 구현 변경
- 라이브러리의 단순 마이너 버전 업데이트

판단이 애매하면 다음 기준을 사용합니다.

> 나중에 다른 개발자가 코드를 보고  
> “왜 굳이 이렇게 했지?”라고 물어볼 가능성이 있는가?

가능성이 높다면 ADR을 남기는 편이 좋습니다.

---

## 4. ADR 작성 방법

### 4.1 다음 번호 확인

현재 `docs/adr` 디렉터리에서 가장 마지막 ADR 번호를 확인합니다.

```text
0001-use-postgresql.md
0002-use-jpa-for-general-crud.md
```

다음 ADR 번호는 `0003`입니다.

번호는 네 자리로 작성합니다.

```text
0001
0002
0003
...
```

---

### 4.2 템플릿 복사

macOS 또는 Linux:

```bash
cp docs/adr/template.md \
   docs/adr/0003-use-jdbc-for-settlement-batch.md
```

Windows PowerShell:

```powershell
Copy-Item docs/adr/template.md `
  docs/adr/0003-use-jdbc-for-settlement-batch.md
```

직접 파일을 복사해도 됩니다.

---

### 4.3 파일명 작성

파일명은 다음 형식을 사용합니다.

```text
ADR 번호-결정 내용.md
```

예시:

```text
0003-use-jdbc-for-settlement-batch.md
0004-adopt-transactional-outbox.md
0005-use-pessimistic-lock-for-balance.md
```

파일명은 영어 소문자와 하이픈을 사용합니다.

좋은 예시:

```text
0003-use-jdbc-for-settlement-batch.md
0004-use-sse-for-progress-notification.md
```

피해야 하는 예시:

```text
0003-decision.md
0004-database.md
회의결과.md
최종결정.md
```

파일명만 보고도 어떤 결정을 내렸는지 알 수 있어야 합니다.

---

## 5. ADR 템플릿

새로운 ADR은 `template.md`를 복사해서 작성합니다.

```markdown
---
id: ADR-0000
title: 결정 내용을 작성한다
status: proposed
category: ARCHITECTURE
date: YYYY-MM-DD
tags: []
related: []
---

# ADR-0000. 결정 내용을 작성한다

## 배경

왜 이 결정이 필요한지 작성한다.

## 고려한 선택지

1. 선택지 A
2. 선택지 B
3. 선택지 C

## 결정

무엇을 선택했는지 작성한다.

## 선택 이유

- 이유 1
- 이유 2
- 이유 3

## 결과

### 장점

- 장점

### 단점

- 단점

## 관련 자료

- GitHub Issue:
- PR:
- 문서:
```

ADR은 가능한 한 한 페이지 이내로 작성합니다.

완벽하고 긴 문서보다 팀원이 실제로 계속 작성할 수 있는 짧은 문서가 더 중요합니다.

---

## 6. 각 항목 작성 방법

### 배경

현재 상황과 해결해야 하는 문제를 작성합니다.

기술 설명보다 다음 내용을 중심으로 작성합니다.

- 현재 어떤 문제가 있는가
- 왜 지금 결정해야 하는가
- 변경하지 않으면 어떤 문제가 생기는가
- 이번 결정이 어느 범위에 적용되는가

예시:

```markdown
## 배경

월별 정산 작업에서 약 500만 건의 데이터를 읽고 집계해야 한다.

현재 일반 서비스 로직에서는 JPA를 사용하고 있다. 하지만 대량 처리에서도
엔티티 단위로 데이터를 읽으면 영속성 컨텍스트 관리 비용이 발생하고
실행 SQL을 세밀하게 제어하기 어렵다.
```

---

### 고려한 선택지

실제로 검토한 후보를 작성합니다.

```markdown
## 고려한 선택지

1. JPA
2. JdbcTemplate
3. MyBatis
```

처음부터 선택지가 하나뿐이었다면 억지로 후보를 만들 필요는 없습니다. 다만 왜 다른 일반적인 방법을 사용하지 않았는지는 `선택 이유`에 간단히 작성합니다.

---

### 결정

최종적으로 무엇을 선택했는지 명확하게 작성합니다.

```markdown
## 결정

정산 배치의 대량 조회와 저장에는 JdbcTemplate을 사용한다.

일반적인 서비스 CRUD에는 기존 JPA를 유지한다.
```

결정에는 적용 범위도 함께 작성하는 것이 좋습니다.

나쁜 예시:

```text
JdbcTemplate을 사용한다.
```

좋은 예시:

```text
정산 배치의 대량 조회와 일괄 저장에 JdbcTemplate을 사용한다.
일반 CRUD에는 기존 JPA를 유지한다.
```

---

### 선택 이유

프로젝트 상황을 기준으로 선택한 이유를 작성합니다.

```markdown
## 선택 이유

- SQL과 실행 계획을 직접 제어할 수 있다.
- 대량 데이터를 엔티티로 관리할 필요가 없다.
- Spring 환경에 별도 프레임워크를 추가하지 않아도 된다.
- 팀원이 JDBC 기반 데이터 처리 경험을 가지고 있다.
```

단순히 다음과 같이 작성하지 않습니다.

```text
많이 사용하기 때문이다.
편하기 때문이다.
유명한 기술이기 때문이다.
```

기술 자체의 장점보다 현재 프로젝트의 요구사항과 연결해서 작성합니다.

---

### 결과

결정으로 인해 생기는 장점과 단점을 함께 작성합니다.

```markdown
## 결과

### 장점

- 대량 처리 SQL을 직접 최적화할 수 있다.
- 실제 실행되는 SQL이 명확하다.

### 단점

- SQL과 RowMapper를 직접 관리해야 한다.
- JPA와 JDBC를 함께 운영해야 한다.
```

ADR은 선택한 기술을 홍보하는 문서가 아닙니다. 선택에 따른 비용과 단점도 반드시 기록합니다.

---

### 관련 자료

관련 GitHub Issue 이슈, PR, 테스트 결과, 벤치마크 문서를 연결합니다.

```markdown
## 관련 자료

- GitHub Issue: #123
- PR: #84
- 문서: `docs/benchmark/settlement-jdbc.md`
```

PR 번호는 ADR을 처음 작성할 때 비어 있을 수 있습니다. PR 생성 후 번호를 추가하거나 PR 본문에서 ADR 파일을 연결해도 됩니다.

---

## 7. 카테고리

ADR에는 하나의 대표 카테고리를 지정합니다.

| 카테고리 | 사용 기준 |
|---|---|
| `ARCHITECTURE` | 시스템 구조와 모듈 책임 |
| `DATA` | 데이터베이스와 데이터 접근 |
| `API` | 외부 통신과 API 계약 |
| `CONSISTENCY` | 트랜잭션, 동시성, 멱등성 |
| `ASYNC` | 메시징과 비동기 처리 |
| `SECURITY` | 인증, 인가, 개인정보 보호 |
| `RELIABILITY` | 장애 대응과 복구 |
| `OPERATIONS` | 배포, 로그, 모니터링 |
| `BATCH` | 대량 처리와 배치 작업 |
| `INFRASTRUCTURE` | 클라우드와 실행 환경 |

예시:

```yaml
category: DATA
```

여러 영역과 관련되어도 대표 카테고리는 하나만 선택합니다. 나머지는 태그로 표현합니다.

```yaml
category: CONSISTENCY
tags:
  - kafka
  - outbox
  - idempotency
```

---

## 8. ADR 상태

ADR의 상태는 다음 값을 사용합니다.

| 상태 | 의미 |
|---|---|
| `proposed` | 제안된 상태로 현재 리뷰 중 |
| `accepted` | 팀에서 채택한 결정 |
| `rejected` | 검토했지만 채택하지 않은 결정 |
| `deprecated` | 더 이상 권장하지 않는 결정 |
| `superseded` | 새로운 ADR로 대체된 결정 |

ADR을 처음 PR에 올릴 때는 일반적으로 다음 상태를 사용합니다.

```yaml
status: proposed
```

팀 리뷰 후 결정이 확정되면 다음과 같이 변경합니다.

```yaml
status: accepted
```

---

## 9. GitHub에서 사용하는 전체 흐름

### 1단계. 기술적 결정 발생

기능 개발 중 다음과 같은 결정이 필요해집니다.

```text
정산 배치에 JPA와 JdbcTemplate 중 무엇을 사용할 것인가?
```

---

### 2단계. ADR 작성

`template.md`를 복사해 새로운 ADR을 작성합니다.

```text
docs/adr/0003-use-jdbc-for-settlement-batch.md
```

처음 상태는 `proposed`로 설정합니다.

```yaml
status: proposed
```

---

### 3단계. 코드와 ADR을 같은 브랜치에서 작성

```bash
git checkout -b feature/gh-123-settlement-jdbc
```

같은 브랜치에 다음 변경을 포함합니다.

```text
코드 변경
ADR 문서
테스트 코드
```

ADR을 따로 작성하는 업무로 만들지 않고 코드 변경의 일부로 처리합니다.

---

### 4단계. 같은 PR에서 리뷰

PR에는 코드와 ADR을 함께 올립니다.

PR 본문에는 ADR 내용을 다시 복사하지 않고 파일 경로만 작성합니다.

```markdown
## 관련 ADR

- `docs/adr/0003-use-jdbc-for-settlement-batch.md`
```

리뷰어는 다음 내용을 확인합니다.

- 문제 상황이 정확한가
- 실제로 검토한 선택지가 맞는가
- 선택 이유가 프로젝트 요구사항과 연결되는가
- 적용 범위가 명확한가
- 장점뿐 아니라 단점도 작성했는가

---

### 5단계. 상태 변경

팀에서 결정에 동의하면 다음과 같이 변경합니다.

```yaml
status: accepted
```

합의하지 못했거나 다른 방식을 선택하면 다음과 같이 변경합니다.

```yaml
status: rejected
```

---

### 6단계. 인덱스 등록

새로운 ADR을 이 README의 ADR 목록에 추가합니다.

```markdown
| [ADR-0003](./0003-use-jdbc-for-settlement-batch.md) | 정산 배치에 JdbcTemplate을 사용한다 | accepted | DATA |
```

---

### 7단계. PR 병합

리뷰가 완료되면 코드와 ADR을 함께 병합합니다.

최종적으로 코드와 결정 근거가 동일한 Git 이력에 남습니다.

---

## 10. 기존 결정이 변경되는 경우

기존 ADR의 내용을 새로운 결정으로 덮어쓰지 않습니다.

예를 들어 기존 결정이 다음과 같다고 가정합니다.

```text
ADR-0003 정산 배치에 JdbcTemplate을 사용한다
```

나중에 MyBatis로 변경한다면 새로운 ADR을 작성합니다.

```text
ADR-0008 정산 배치에 MyBatis를 사용한다
```

기존 ADR의 상태를 변경합니다.

```yaml
status: superseded
```

기존 ADR에 새 ADR을 연결합니다.

```yaml
superseded-by: ADR-0008
```

새 ADR에는 기존 ADR을 연결합니다.

```yaml
supersedes: ADR-0003
```

이 방식으로 당시에는 왜 JdbcTemplate을 선택했고 이후에는 왜 MyBatis로 변경했는지 확인할 수 있습니다.

---

## 11. PR 템플릿 적용

`.github/pull_request_template.md`에 다음 항목을 추가하는 것을 권장합니다.

```markdown
## ADR

- [ ] ADR이 필요하지 않은 변경입니다.
- [ ] 새로운 ADR을 추가했습니다.
- [ ] 기존 ADR을 대체하거나 상태를 변경했습니다.

관련 ADR:

- `docs/adr/`
```

모든 PR에 ADR 작성을 강제하지 않습니다.

PR을 작성할 때 이번 변경이 기술적 결정에 해당하는지만 한 번 확인하도록 합니다.

---

## 12. GitHub에서 ADR 검색하기

GitHub 저장소 검색창에서 다음 검색식을 사용할 수 있습니다.

### 특정 기술 검색

```text
path:docs/adr JdbcTemplate
```

### 카테고리 검색

```text
path:docs/adr "category: DATA"
```

### 상태 검색

```text
path:docs/adr "status: accepted"
```

### 태그 검색

```text
path:docs/adr spring-batch
```

파일명을 명확하게 작성하고 태그를 일관되게 사용하면 별도 문서 도구 없이도 쉽게 찾을 수 있습니다.

---

## 13. 작성 예시

```markdown
---
id: ADR-0003
title: 정산 배치에 JdbcTemplate을 사용한다
status: accepted
category: DATA
date: 2026-07-24
tags:
  - jdbc
  - spring-batch
  - settlement
related:
  - "#123"
---

# ADR-0003. 정산 배치에 JdbcTemplate을 사용한다

## 배경

월별 정산 작업에서 약 500만 건의 데이터를 읽고 집계해야 한다.

현재 일반 서비스 로직에서는 JPA를 사용하고 있다. 하지만 대량 처리에서도
엔티티 단위로 데이터를 읽으면 영속성 컨텍스트 관리 비용이 발생하고
실행 SQL을 세밀하게 제어하기 어렵다.

## 고려한 선택지

1. JPA
2. JdbcTemplate
3. MyBatis

## 결정

정산 배치의 대량 조회와 저장에는 JdbcTemplate을 사용한다.

일반적인 서비스 CRUD에는 기존 JPA를 유지한다.

## 선택 이유

- SQL과 실행 계획을 직접 통제할 수 있다.
- 대량 데이터를 엔티티로 관리할 필요가 없다.
- 별도의 데이터 접근 프레임워크를 추가하지 않아도 된다.
- 팀원이 JDBC 기반 처리 경험을 가지고 있다.

## 결과

### 장점

- 대량 처리 성능을 직접 최적화할 수 있다.
- 실제 실행되는 SQL이 명확하다.

### 단점

- SQL과 RowMapper를 직접 관리해야 한다.
- JPA와 JDBC를 함께 운영해야 한다.

## 관련 자료

- GitHub Issue: #123
- PR: #84
- 문서: `docs/benchmark/settlement-jdbc.md`
```

---

## 14. ADR 목록

새로운 ADR을 추가할 때 아래 표에도 등록합니다.

| ADR | 결정 | 상태 | 카테고리 |
|---|---|---|---|
| [ADR-0001](./0001-use-postgresql.md) | PostgreSQL을 주 데이터베이스로 사용한다 | accepted | DATA |
| [ADR-0002](./0002-use-jpa-for-general-crud.md) | 일반 CRUD에 JPA를 사용한다 | accepted | DATA |
| [ADR-0003](./0003-use-jdbc-for-settlement-batch.md) | 정산 배치에 JdbcTemplate을 사용한다 | accepted | DATA |

---

## 15. 핵심 규칙

이 프로젝트에서는 다음 규칙만 기억하면 됩니다.

1. 모든 변경에 ADR을 작성하지 않는다.
2. 되돌리기 어렵거나 영향 범위가 큰 결정만 기록한다.
3. 한 ADR에는 하나의 결정만 작성한다.
4. 최대한 한 페이지 이내로 작성한다.
5. 코드와 ADR을 같은 PR에서 리뷰한다.
6. PR 본문에는 ADR 내용을 복사하지 않고 파일 경로만 연결한다.
7. 장점뿐 아니라 단점도 작성한다.
8. 기존 결정을 삭제하지 않는다.
9. 결정이 바뀌면 새로운 ADR로 대체한다.
10. 완벽한 문서보다 계속 작성할 수 있는 문서를 우선한다.

---

## 빠르게 시작하기

```text
1. docs/adr/template.md를 복사한다.
2. 다음 ADR 번호로 파일명을 작성한다.
3. 상태를 proposed로 설정한다.
4. 배경, 선택지, 결정, 이유, 결과를 작성한다.
5. 코드와 같은 PR에 올린다.
6. 팀 합의 후 accepted로 변경한다.
7. README의 ADR 목록에 추가한다.
```

ADR의 목적은 문서를 많이 만드는 것이 아닙니다.

> 중요한 기술적 결정이 코드 속에서 이유 없이 남지 않도록 하는 것이 목적입니다.
