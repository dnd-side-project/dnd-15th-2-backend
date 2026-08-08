# 커밋 분할 기준

`AGENTS.md` 6절 "하나의 커밋에는 하나의 검토 목적만 담는다"의 실행 규칙이다.

## 하나의 검토 목적이란

리뷰어가 **한 번에 한 가지 질문만** 던지게 되는 단위다.

- "이 도메인 모델이 맞나?" — 모델 정의 커밋
- "이 영속화 경계가 맞나?" — 포트·어댑터 커밋
- "이 테스트가 계약을 제대로 검증하나?" — 테스트 커밋

한 커밋을 보며 두 가지 이상을 판단해야 하면 나눈다.

## 분할 축

우선순위 순으로 적용한다.

1. **계층·경계** — 도메인 / 포트 / 어댑터 / 서비스 / API / 마이그레이션 / 테스트
2. **타입** — 기능 추가와 리팩터링을 섞지 않는다. 포맷팅·이름 변경만 있는 파일은
   별도 커밋으로 뺀다.
3. **범위 단위** — 서로 다른 애그리거트·도메인(account / question / direction)은
   나눈다.
4. **부수 작업** — 의존성 추가, 설정 변경, 문서·보고서는 구현과 분리한다.

## 순서

앞 커밋만으로 빌드가 깨지지 않도록 의존성의 아래에서 위로 쌓는다.

```text
계약·계획 문서  →  스키마·마이그레이션  →  도메인 모델  →  포트  →  어댑터
              →  서비스  →  API  →  테스트  →  보고서·문서
```

실제 이 저장소의 `feat/gh-39-direction-postgis` 브랜치가 따른 순서다.

```text
feat(direction): PostGIS 영속화 검증 계약을 고정한다 (#39)
feat(direction): mark PostGIS persistence test plan approved (#39)
feat(direction): model direction sector, presence, and post aggregates (#39)
feat(direction): add repository ports and JDBC PostGIS adapters (#39)
feat(direction): add send transaction service (#39)
feat(direction): add domain and persistence boundary unit tests (#39)
feat(direction): isolate Testcontainers Spring context per class (#39)
feat(direction): verify PostGIS persistence with Testcontainers (#39)
feat(direction): report PostGIS persistence test results (#39)
```

테스트가 구현과 같은 커밋이어야 검토가 쉬운 경우(작은 버그 수정 + 회귀 테스트)는
합쳐도 된다. 그때는 초안에 이유를 적는다.

## scope 선정

소문자·숫자·하이픈만 쓴다. 이 저장소에서 실제로 쓰인 scope:

`direction` `account` `question` `persistence` `database` `feed` `package`
`harness` `workflow` `review` `test` `labels`

기능 도메인이 뚜렷하면 도메인명, 저장소 운영이면 `harness`·`workflow`를 쓴다.
적당한 scope가 없으면 생략한다 — scope는 선택이다.

## 타입 선택

브랜치 prefix와 **같아야 한다**. 브랜치 타입을 벗어나는 변경이 필요하면 그 변경은
다른 이슈·브랜치의 것이다.

| type | 사용 |
| --- | --- |
| `feat` | 새 동작·기능 |
| `fix` | 결함 수정 |
| `refactor` | 외부 동작을 유지하는 구조 변경 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서·설계 산출물 |
| `chore` | 도구, 자동화, 의존성 |
| `ci` | GitHub Actions 워크플로 |
| `build` | Gradle, Docker, 빌드 스크립트 |
| `perf` | 성능·비용 개선 |
| `infra` | Terraform·CDK 등 IaC |

## 커밋하면 안 되는 것

- `.env`, 로컬 설정, 자격증명, 토큰
- `build/`, `.gradle/`, `.idea/` 등 산출물·IDE 파일
- 스크래치 파일, 실험 코드, 주석 처리된 디버그 코드
- 다른 사람이 작업 중인 파일의 무관한 변경

발견하면 커밋에서 제외하고 사용자에게 보고한다.

## 나누지 못하는 경우

한 파일 안에 여러 목적이 섞여 있으면 hunk를 자동으로 쪼개지 않는다.
`git add -p`는 대화형이라 에이전트가 실행할 수 없다. 두 가지를 제안한다.

1. 한 커밋으로 합치고 커밋 본문에 두 목적을 나눠 적는다.
2. 사용자가 `! git add -p <파일>`로 직접 분리한 뒤 스킬을 다시 실행한다.
