# 이슈 유형 매핑

`.github/ISSUE_TEMPLATE/*.yml`과 `docs/harness/LABELS.md`에서 파생한 표다.
템플릿이나 라벨 정책이 바뀌면 이 문서를 함께 고친다.

## 유형 → 라벨 → 브랜치 type

| 질문 선택 | 템플릿 | `type: *` 라벨 | 기본 `area: *` | 브랜치 prefix | Work type |
| --- | --- | --- | --- | --- | --- |
| 기능 작업 | `feature.yml` | `type: feature` | 추론 | `feat` | Feature |
| 기능 작업 → 리팩터링 | `feature.yml` | `type: refactor` | 추론 | `refactor` | Refactor |
| 기능 작업 → 문서 | `feature.yml` | `type: docs` | 추론 | `docs` | Docs |
| 버그·장애 복구 | `bug.yml` | `type: bug` | 추론 | `fix` | Bug |
| 백엔드 유지보수 | `backend_work.yml` | 아래 표로 결정 | `area: api` | 아래 표 | 아래 표 |
| 기술 설계·ADR | `design.yml` | `type: docs` | 추론 | `docs` | Docs |
| 테스트 시나리오 | `test-scenario.yml` | `type: test` | 추론 | `test` | Test |
| AWS 인프라 | `infrastructure.yml` | `type: infrastructure` | `area: operations` | `infra` | Infrastructure |

### 백엔드 유지보수 세부 (`backend_work.yml`의 작업 유형)

| 작업 유형 | 라벨 | 브랜치 prefix | Work type |
| --- | --- | --- | --- |
| feat | `type: feature` | `feat` | Feature |
| fix | `type: bug` | `fix` | Bug |
| refactor | `type: refactor` | `refactor` | Refactor |
| test | `type: test` | `test` | Test |
| docs | `type: docs` | `docs` | Docs |
| chore | `type: chore` | `chore` | Chore |
| config | `type: chore` | `chore` | Chore |
| infrastructure | `type: infrastructure` | `infra` | Infrastructure |
| performance | `type: performance` | `perf` | Performance |

CI 워크플로 변경만 다루면 `ci`, 빌드 스크립트·의존성만 다루면 `build` prefix도
쓸 수 있다. 둘 다 PR 라벨은 `type: chore`로 산출된다.

`area: *` 추론 기준: API·애플리케이션 계층은 `area: api`, 스키마·마이그레이션·쿼리는
`area: database`, 인증·권한·비밀정보는 `area: security`, AWS·배포·관측은
`area: operations`. 확실하지 않으면 붙이지 않는다.

## 본문 구조

기존 이슈(#34~#48)의 실제 관행과 Issue Form 필수 항목을 합친 형식이다.

```markdown
## 목적

## 범위
-

## 완료 조건
- [ ]

## 백엔드 영향
API:
DB:
권한:
외부 연동:

## 선행 관계
-

## 제외
-
```

유형별 추가 섹션:

- **버그**: `## 재현 절차`를 `## 범위` 앞에 넣는다. 기대 결과와 실제 결과를 함께 쓴다.
  운영 데이터와 비밀정보는 제거한다.
- **기술 설계**: `## 검토할 대안`을 넣고 최소 두 가지와 장단점을 쓴다.
- **테스트 시나리오**: `## 테스트 계획 식별자`(예: `TEST-PLAN-GH-42-FEED`)와
  `## 테스트 범위`(단위/통합/동시성·트랜잭션/외부 API·장애 복구) 체크리스트를 넣는다.
  완료 조건에 `@DisplayName`, 클래스 헤더 timestamp·source scenario,
  `templates/test-report.md` 기반 보고서를 포함한다.
- **인프라**: `## IaC`(Terraform 또는 AWS CDK), `## 대안과 비용 가정`(EC2/ECS,
  RDS/자체 운영, 공식 AWS 가격 근거)을 넣는다. 완료 조건에 least-privilege IAM,
  `fmt`/`validate`/static/`plan`, `@Byuntil`·`@tkv00` 리뷰 요청, apply 기본 비활성을
  포함한다. 본문 끝에 다음 안전 확인을 넣는다.

  ```markdown
  ## 안전 확인
  - [x] 비밀 키, 주소, IAM/계정 ID, 토큰, .env 값을 기록하지 않습니다.
  - [x] 두 백엔드 승인과 명시적 사람 확인 전에는 apply/deploy 하지 않습니다.
  ```
