# GitHub Issue #190 Task Contract

> Generated at: `2026-08-22T23:31:21+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Codex·Claude OpenAPI 설명 가이드`
- GitHub Issue: `#190`
- Branch: `docs/gh-190-openapi-writing-guide`
- Base branch: `main`
- 선행 이슈: `#189` (OpenAPI 설명 개선 및 GitHub Pages 문서 제공). 이 이슈는 `#189`가
  쓸 기준·절차·양식만 만든다. `*ApiSpec` 문장 자체는 고치지 않는다.
- Planning approval: 2026-08-22 사용자 대화에서 계획 확정. `FilterReleaseApiSpec`을
  예시로 3라운드 반복 검토(추상화 과다 → 괄호로 맥락 보강)를 거쳐 문장 기준에 합의했다.

## Objective

- Codex와 Claude 양쪽에서 도메인 담당자가 같은 기준으로 `*ApiSpec` 설명을 작성·검토할
  수 있도록 공통 가이드, 체크리스트, 실행 절차를 만든다.
- 문장 톤, 용어 선택, 누락된 인증·오류 응답·필드 설명을 점검하는 절차를 표준화해
  `#189`의 도메인별 검토 편차를 줄인다.

## Scope

1. `docs/api/OPENAPI_WRITING_GUIDE.md`를 신설한다. 단일 원본이며 Codex·Claude 스킬은
   본문을 복제하지 않고 이 문서를 참조한다. 최소 다음을 담는다.
   - 적용 범위와 `docs/api-response.md` §5(애노테이션 배치 규칙)와의 경계.
   - 문장 종결 기준: `합니다`체로 통일 (24개 기존 `*ApiSpec` 중 18개가 이미 준수).
   - `summary` 작성 기준: 기본은 쉬운 명사구. 팀 용어·상태 전이처럼 명사구로 뭉치면
     오히려 낯선 단어가 쌓이는 경우에는 `-하기` 동사구를 허용한다.
   - `description` 문단 순서: 무엇을 하는가 → 선행 조건·인증 → 성공 시 결과 →
     주요 실패 조건 → 주의점.
   - 낯선 단어를 쌓지 않는 규칙과 분류 이름 대신 하는 일로 부르는 규칙.
   - 괄호 표기 규칙: 상태를 바꾸는 API는 바뀐 뒤 `status` 값을 `(→ PROMOTED)`처럼,
     팀이 영어로 부르는 개념은 `(offline evaluation)`처럼 처음 나오는 곳에 한 번만,
     오류 응답에는 오류 코드를 `(FLT-DOM-004)`로 적는다. 붙일 값이 없으면 괄호를
     비워서라도 채우지 않는다. 한 줄에 괄호는 하나만 쓴다.
   - 금지 문장: 내부 정책 코드·상태값만으로 의미 설명, 미번역 내부 불변식 ID
     노출(`INV-REL-007` 등), 요청·응답 DTO에 없는 사실을 지어내는 표현,
     `@Schema(example)`에 비밀값.
   - 6점 대조 체크리스트: Controller↔ApiSpec, ApiSpec↔DTO, ApiSpec↔Service(실제
     `throw` 근거), ApiSpec↔`docs/error-codes.md`, ApiSpec↔SecurityConfiguration,
     ApiSpec↔`docs/api/openapi.json`(재생성 후 diff).
2. `templates/api-docs-review.md`를 신설한다. 엔드포인트별 행에 6점 대조 결과와
   before/after 제안 문장을 기록하는 양식이며 `#189` 담당자에게 그대로 넘긴다.
3. `.claude/skills/harness-api-docs/SKILL.md`와 `.agents/skills/harness-api-docs/SKILL.md`에
   `review` 모드를 추가한다. 기존 모드(누락 보강, 코드 수정)와 분리하고, `review`
   모드는 `*ApiSpec`을 수정하지 않고 `templates/api-docs-review.md` 산출물만
   만든다. 문장 기준은 본문에 복제하지 않고 `docs/api/OPENAPI_WRITING_GUIDE.md`를
   참조한다. 두 스킬 파일은 frontmatter만 다르고 본문은 동일하게 유지한다.
4. `agents/api-docs-executor.md`와 `.claude/agents/api-docs-executor.md`를 갱신한다.
   Enrichment targets에 문장 품질·용어 일관성 항목을 추가하고, `review` 모드의
   allowed scope를 `docs/reports/**`로 한정한다(`*ApiSpec` 수정 금지).
5. `FilterReleaseApiSpec` 1건으로 6점 대조·문장 재작성 절차 전체를 시험 적용해
   점검 흐름이 실제로 작동하는지 확인한다(404 누락 5건, 401/403 누락 8건, DTO
   `@Schema` 누락 12건, 내부 불변식 ID 3곳, `findAll()`의 `@ApiResponses` 누락
   1건 등을 실제 코드 대조로 발견함). 결과는 이 대화 안에서 검증하는 데 쓰고
   저장소에 영구 산출물로 커밋하지 않는다. `docs/reports/**`는 `#189`에서 각
   도메인 담당자가 `review` 모드를 직접 실행해 만드는 산출물이 쌓이는 자리이며,
   `#190`(가이드·절차 제작)이 그 자리를 먼저 채우면 담당자가 자기 도메인의 실제
   리뷰와 `#190`이 만든 데모 중 무엇이 유효한지 혼동한다. `*ApiSpec` 원본 파일도
   수정하지 않는다.
6. 진입점을 연결한다.
   - `docs/api-response.md` §5 말미에 가이드 링크를 추가한다.
   - `docs/harness/WORKFLOW_SKILLS.md`의 역할 스킬 목록(13~15행)에 빠져 있는
     `harness-api-docs`를 추가한다. 이 문서는 이슈·커밋·PR 3종 스킬 전용이고
     역할 스킬 내용은 각 스킬 문서가 원본이므로, 새 절을 만들어 `review` 모드
     절차를 중복 설명하지 않는다.
   - `CODEX.md` 스킬 목록에 빠져 있는 `$harness-api-docs`를 추가한다.

## Approved design decisions

- 문장 종결: `합니다`체로 통일. 기존 24개 중 18개가 이미 준수해 재작업량이 가장 적다.
- `summary` 규칙: 기본은 명사구, 낯선 개념이 몰릴 때만 `-하기` 동사구 허용 (전면
  동사구 통일은 하지 않는다).
- 괄호는 상태값·팀 용어·오류 코드 세 경우에만 쓰고, 빈 괄호를 채우려고 말을
  지어내지 않는다.
- 쉬운 말로 바꾸며 DTO에 없는 사실을 만들지 않는다. `markOfflineEvaluated`가
  실제로는 사유(reasonCode·reasonText)만 받고 평가 점수 필드를 받지 않는다는
  사실을 확인한 뒤 "성능 검사 결과 등록" 같은 표현을 "평가를 마쳤다고 표시"로
  정정했다 — 이 정정 과정 자체가 가이드의 "쉽게 쓰다가 사실을 바꾸지 않는다"
  규칙의 근거다.
- 문장 기준의 단일 원본은 `docs/api/OPENAPI_WRITING_GUIDE.md` 하나이며 두 스킬
  파일에 복제하지 않는다. Codex·Claude가 다른 기준으로 검토하는 드리프트를 막는다.
- 자동 검사 스크립트(lint)는 이번 범위에서 제외한다. 별도 이슈로 미룬다.

## Explicit exclusions

- 실제 `*ApiSpec` 문장 수정. `#189` 담당자(도메인별)의 몫이다.
- GitHub Pages 정적 문서 제공. `#189`의 별도 항목이다.
- OpenAPI 산출물 생성 workflow 변경.
- API 동작이나 비즈니스 로직 변경.
- `docs/reports/**`에 도메인 리뷰 결과를 커밋하는 것. 그 자리는 `#189`에서 각
  도메인 담당자가 `review` 모드를 실행해 만드는 산출물의 몫이다. `#190`은
  절차가 작동함을 확인만 하고 결과물을 저장소에 남기지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 문장 기준·체크리스트 (`docs/api/OPENAPI_WRITING_GUIDE.md`) | API docs 작업자 | 3라운드 대화에서 합의한 괄호·용어·종결 규칙과 일치 |
| 검토 양식 (`templates/api-docs-review.md`) | API docs 작업자 | 6점 대조 항목 누락 없음 |
| 스킬·역할 문서 (`.claude/**`, `agents/**`) | API docs 작업자 | `CLAUDE.md` 파일 수정 범위상 별도 승인 대상 — 사용자 승인 완료 |
| 절차 시험 적용 (`FilterReleaseApiSpec`, 비산출물) | API docs 작업자 | `*ApiSpec` 원본 미수정, 코드 근거(서비스 throw, DTO 필드) 재확인, 결과를 저장소에 커밋하지 않음 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다. 범위 밖 변경은 없다.

## Validation

```bash
./harness check
npm run hooks:validate
git diff --check
./harness pr-ready --project-tests
```

Java 코드 변경이 없으므로 Gradle 테스트는 대상이 아니다.

### Validation evidence (2026-08-22)

- `git diff --check`: 통과.
- `./harness check`: 통과 (secret preflight 1156개 파일, JUnit 정책 222개 파일,
  convention·workflow·label·husky 검증 모두 통과).
- `npm run hooks:validate`: 통과.
- `./harness pr-ready --project-tests`: **FAIL.** 원인은 이 브랜치의 변경이 아니라
  테스트 환경 문제다 — 이 워크트리에 Docker 데몬 자체가 없다(`docker: command not
  found`). Testcontainers 기반 통합 테스트 76개가 `DockerClientProviderStrategy`
  초기화 단계에서 전부 `initializationError`로 실패했다. 이 브랜치는 Java 소스를
  전혀 변경하지 않았고(`docs/`, `templates/`, `.claude/`, `.agents/`, `agents/`,
  `CODEX.md`만 변경) 실패한 테스트 76개는 모두 이 변경과 무관한 기존 통합 테스트다.
  - 실행 못한 범위: 통합 테스트 전체(76개), 그리고 그 안에 포함되는
    `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest"`
    (`docs/api/openapi.json` 재생성·diff 확인). 이 검증은 `#189`에서 실제
    `*ApiSpec` 문장을 반영하는 PR이 실행해야 한다.
  - 남은 위험: 이번 변경 자체는 코드 동작에 영향이 없어 위험은 낮다. 다만 Docker가
    구성된 환경에서 재검증하지 않았으므로 "통합 테스트가 실제로 통과한다"는 것을
    확인했다고 보고하지 않는다.

## Completion criteria

- [x] `docs/api/OPENAPI_WRITING_GUIDE.md`가 문장 기준·용어 규칙·괄호 규칙·6점
      체크리스트를 모두 포함한다.
- [x] `templates/api-docs-review.md`가 엔드포인트별 6점 대조와 before/after 제안을
      기록할 수 있는 양식이다.
- [x] `.claude/skills/harness-api-docs/SKILL.md`와
      `.agents/skills/harness-api-docs/SKILL.md`에 `review` 모드가 추가되고 두 파일의
      본문이 동일하다(frontmatter만 다르다). `diff`로 본문 동일성 확인함.
- [x] `agents/api-docs-executor.md`와 `.claude/agents/api-docs-executor.md`가 `review`
      모드의 allowed scope와 enrichment target을 반영한다.
- [x] `FilterReleaseApiSpec` 8개 엔드포인트 전체로 6점 대조·문장 재작성 절차를
      시험 적용해 실제로 작동함을 확인했다(대화 기록에 남김). `*ApiSpec` 원본은
      변경되지 않았고(`git status`로 확인) 결과를 `docs/reports/**`에 커밋하지
      않았다 — 그 자리는 `#189` 도메인 담당자의 실제 산출물 몫이다.
- [x] `docs/api-response.md`, `docs/harness/WORKFLOW_SKILLS.md`, `CODEX.md`에서
      새 가이드와 review 모드를 찾아갈 수 있다.
- [x] 완료 전 검증을 모두 실행하고 실패·미실행 범위를 구분해 기록한다.
      `./harness pr-ready --project-tests`는 Docker 미가용으로 통합 테스트 단계에서
      FAIL했다 — 위 Validation evidence 절에 원인·미실행 범위·남은 위험을 기록함.
