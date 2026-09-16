# GH-227 Phase 4 독립 검증

status: BLOCKED
issue_number: 227
task_id: GH-227-INSTRUCTION-ROLLOUT-PHASE4
design_id: HARNESS-DESIGN-GH-227-001

## 판정과 검증 범위

독립 검증자는 실제 7개 지침 파일, HEAD 원문, Phase 2 source map, 신규 mapping,
승인 설계·계획, 비공개 runner·manifest 및 첫 회귀의 원시 실행 증거를 직접 확인했다.
애플리케이션·지침·평가 도구를 수정하지 않았으며 평가 호출·하위 평가를 실행하지 않았다.
이 검증 문서만 작성했다.

정책 원문 보존과 저장소 필수 검사는 PASS다. 첫 회귀는 실행 후 환경 fingerprint 불일치로
ERROR이며, 승인된 중단 조건에 따라 나머지 3회는 실행하지 않았다. 따라서 제한 회귀 전체와
최종 도입은 BLOCKED다. 테스트 통과나 정적 보존으로 행동 검증 통과를 대체하지 않는다.

## 정책 보존: PASS

- `AGENTS.md`의 §3 이전·이후 본문은 HEAD와 byte-identical이다. 최종 root는 25,006 bytes다.
- 기존 §3의 일곱 규칙, Java 헤더 예시, 실패 분류와 환경 실패 보고 다섯 항목은
  `test-policy.md`와 `reporting.md` 본문 합집합에 원문 그대로 보존된다.
- 두 Skill의 frontmatter와 기존 2번 이후 단계는 원문 동일하다. 1번 단계에 reference 읽기를 추가했으며
  기존 승인·소유권·보고·커밋 조건을 제거하지 않았다.
- 7개 지침 파일의 Markdown 상대 링크·anchor 19개가 실제 파일과 제목에 연결된다.
- root 시작과 단위·통합 혼합 작업은 해당 하위 지침을 명시적으로 읽도록 연결된다.
  `cd`만으로 instruction chain이 재로딩된다고 가정하지 않는 조건도 명시되어 있다.
- [정책 mapping](gh-227-policy-map.csv)의 447개 source ID·section·줄 범위는 기존 source map과 일치한다.
  각 HEAD source 범위의 SHA-256과 최종 target 전체 블록을 독립 비교해 447개 모두 일치했다.
  초기 접두사 매칭 오류 5개는 수정 후 재검증했다. 447은 독립 정책 수가 아니라 원문 단위 수다.
- 원본 원장·마이그레이션 이력·운영 감사 이력의 수정/삭제 여섯 금지 경우, 승인 없는 구현 금지,
  Terraform build 승인 조건, 보호된 GitHub Actions Apply 주체와 AI 직접 Apply 금지는 원문 그대로다.
  해당 금지 행동은 실제 실행하지 않았다. 원격 강제나 모든 상황의 행동 준수를 증명하지 않는다.

7개 지침 파일은 root, 단위/통합 AGENTS, 두 테스트 Skill, 두 공통 reference다.
나머지 공개 변경은 GH-227 계약·설계·계획·mapping·보고 문서다. 제품 Java 파일이나 과거 원장은 변경하지 않았다.

## 평가 실행 전 검토

runner의 예산 경계 및 순서 거부를 모델 호출 없이 확인했다. 입력 2M·출력 40k·누적 1,800초의
정확한 한도에서도 다음 실행을 차단하며, 개별 480초 제한과 이전 결과/검토 gate 조건이 있다.
잘못된 index와 이전 결과가 없는 후속 실행도 거부했다. 원시 예외는 비공개 결과에만 보존하고
공개 출력은 오류 종류로 제한한다.

네 개 사본 각각의 1,445개 snapshot 파일, prompt, runner, imported collector, 환경 hash를 확인했다.
승인된 시나리오 fixture가 사본에 존재한다. 초기 manifest는 호출 전 수정된 v2로 대체되었고
supersession hash와 원본이 보존된다. 비공개 자료 디렉터리 권한은 0700이었다.
network 허용은 workspace-write 평가 프로세스에 한정되며 계정 전역 설정 변경 명령은 없다.

사용량은 관측 이벤트를 통해 제한한다. 서버 내부의 단일 요청에 대한 실시간 hard cap이나
도구 catalog 전체의 동등성은 증명하지 못한다. 정확한 instruction tokens는 UNAVAILABLE이다.

## 실제 회귀 결과

| 항목 | 독립 확인 결과 |
| --- | --- |
| 실행 수 | 1회; replacement 0회; 관측된 하위 세션 0회 |
| 실행 구성 | Sol/high, CLI 0.154.0-alpha.6.2, approval never |
| 관측 sandbox | workspace-write, network access true, 요청한 root cwd |
| 프로세스 결과 | 종료 코드 0; tool call 9개 |
| 평가 gate | ERROR / POST_ENVIRONMENT_DRIFT |
| 누적 입력 / cached 입력 | 421,601 / 363,264 tokens |
| 누적 출력 / reasoning 출력 | 8,214 / 2,667 tokens |
| 평가 소요 | 185.64초 |
| 원문 rollout SHA-256 | `632a5646947c3dce4ec935a25b4c22347fd5c1c51b92b21cb0bc10d6675b6388` |
| 후속 3회 | NOT_RUN; 승인된 환경 실패 중단 조건 적용 |

cached 입력과 reasoning 출력은 각각 전체 입력·출력의 부분집합이며 별도로 더하지 않는다.
첫 입력은 23,894 tokens였지만 자동 지침만의 토큰으로 해석하지 않는다.

환경 차이는 aggregate inventory digest에 한정되며 CLI 파일·버전과 imported helper hash는 동일했다.
실행 명령 기록에서 계정 설정·플러그인 변경을 수행한 호출은 확인되지 않았다. manifest가 inventory의
파일별 이전 hash를 보존하지 않아 정확히 변경된 파일·행위 주체·원인은 UNKNOWN이다.
추가 읽기 확인에서 inventory 21개 파일 중 실행 시간대에 mtime이 해당하는 파일은
`config.toml` 하나였고, 실행 시작 직후였으며 현재 설정에는 평가 사본의 trusted 프로젝트 항목이 있다.
이는 CLI 시작 시 trust 등록과 일관되는 정황이다. 그러나 이전 파일별 hash·내용이 없어 해당 항목이
이번에 추가되었는지와 aggregate digest 변화의 직접 원인·행위 주체는 입증되지 않는다.
외부 자동 갱신이나 모델의 변경으로 단정하지 않는다. 이 환경 차이를 무시하거나 새 기준으로
실행을 계속하지 않았고, 해당 설정을 수정·제거하지 않았다.

### 첫 계획 산출물의 품질과 한계

모든 기존 snapshot 파일이 그대로였으며, 요청한 계획 문서 하나만 추가됐다.
root AGENTS, TASK, 승인 fixture, 단위·통합 하위 AGENTS, 계획 Skill, test-policy,
오케스트레이터 역할, 계획/보고 템플릿과 두 대상 테스트 본문을 실제 읽은 tool evidence가 있다.
계획은 템플릿 11개 절, 두 시나리오 ID, 소유 파일, 선택 명령, 원본 메타데이터 불변조건,
환경 실패 분류·다섯 보고 항목과 해당하지 않는 위험 근거를 포함한다.
Java 수정·테스트 실행·하위 세션·커밋·push·PR·인프라 명령은 수행하지 않았다.

`reporting.md`를 명시적으로 읽은 증거는 없다. 계획 단계에 필요한 test-policy는 읽었고,
실행/보고 reference 로딩은 실행되지 않은 후속 회귀의 미검증 항목으로 남는다.
프롬프트와 fixture가 보고 조건을 제공하므로 산출물에 조건이 있다는 사실만으로 reference를
발견해 읽었다고 주장하지 않는다.

생성 계획은 두 시나리오를 같은 단일 실행 세션으로 기술했다. 승인 설계의 별도 회귀 2/3 구조와
다르지만 평가 fixture 자체도 두 시나리오에 대해 “this single evaluation session”이라고 명시하고,
root 요청은 별도 세션 배치를 명시하지 않았다. 따라서 이는 상충하는 평가 입력에 영향을 받은
산출물 모호함이며 지침 분리로 발생한 행동 실패라고 단독 귀속할 수 없다. 후속 실행 계약으로
그대로 채택할 수는 없다.

계획의 Draft·후속 승인 문구는 실제로 요청한 계획 생성을 막지 않았다. 계획은 작성되었고 실행도
시도하지 않았다. 이미 승인된 fixture와 새 Draft의 승인을 구분하는 문구는 불필요한 재승인을
유도할 여지가 있지만, 이 기록을 계획 작성 거부나 승인 gate 실패로 분류하지 않는다.

요청한 계획 작성과 범위 보존은 확인했다. 전체 run gate는 환경 ERROR이며 unit 실제 변경,
integration 실제 변경·환경 실패 보고, 혼합/인프라/원장 경계의 읽기 회귀는 미실행이다.
안내가 포함된 1회 관측으로 무안내 발견 능력·비용 효과·통계적 우열을 일반화하지 않는다.

## 저장소 필수 검사: PASS

독립 검증자가 `./harness check`, `npm run hooks:validate`, `git diff --check`를 실행해
종료 코드 0과 통과 출력을 확인했다. 부모가 실행한 `./harness pr-ready --project-tests`는
비공개 원문 로그 hash와 현재 XML을 직접 대조했다. 같은 장시간 테스트를 불필요하게 재실행하지 않았다.

| 검사 | 확인 결과 |
| --- | --- |
| `./harness check` | PASS; 민감정보·JUnit·convention·commit formatter·workflow·label·Husky·baseline 검사 통과 |
| `npm run hooks:validate` | PASS |
| `git diff --check` | PASS |
| `./harness pr-ready --project-tests` | BUILD SUCCESSFUL, 6분 50초 |
| unit XML | 168개 클래스, 1,064 tests, failures/errors/skipped 모두 0 |
| integration XML | 93개 클래스, 735 tests, failures/errors/skipped 모두 0 |

로그의 `:test`와 `:integrationTest`는 실제 실행이며 UP-TO-DATE/FROM-CACHE가 아니다.
`checkstyleTest`와 `checkstyleIntegrationTest`는 기존 구성에서 SKIPPED로 표시된다.
이를 실행 통과로 바꾸어 보고하지 않는다. 기본 branch의 로컬 fast-forward 불가 안내는 그대로 남고
로컬 부모 branch를 강제로 변경하지 않았다.

검증한 pr-ready 원문 로그 SHA-256:
`7e19849688b73b8c79b5517689494ee6eade329a4ce78c1eba716dce40fd3314`.
필수 테스트는 현재 공개 worktree의 제품 회귀 확인이며, 실행되지 않은 평가 사본의 실제 설명 수정
회귀를 대신하지 않는다. Terraform 파일 변경이 없어 Terraform 검증은 해당하지 않는다.

## 최종 계약

- changed_files: 승인된 7개 지침 파일과 GH-227 추적 문서; 이 검증자의 수정은 본 문서 하나.
- executed_checks: 원문·mapping·링크·승인 조건·runner/manifest 감사, 첫 회귀 원시 증거 감사,
  harness/hooks/diff 검사, pr-ready 로그/hash 및 XML 대조.
- passed_checks: 정적 정책 보존, 링크 19개, mapping 447개, 필수 저장소 검사.
- failed_checks: 첫 회귀의 POST_ENVIRONMENT_DRIFT; 코드/테스트 실패는 관측되지 않음.
- blocked_checks: 후속 회귀 3회, 회귀 전체 통과, 정확 instruction tokens, Before/After 기간 측정과 최종 도입 판단.
- assumptions: 프롬프트가 제공한 조건과 실제 읽은 지침을 구분하며, 정적 bytes와 전체 runtime input을 구분함.
- risks: 제한 회귀 미완료, inventory 변경 원인 미확정, 평가 입력의 세션 소유권 모호함, 비용 효과 미입증.
- required_human_decisions: 추가 평가는 기존 상한의 잔여량만으로 자동 실행하지 않음.
  재평가가 필요하면 환경 원인과 fixture를 정리한 구체적 계획·예산의 사람 판단이 필요함.
  커밋/push/stacked Draft PR은 구체적 초안 승인 절차를 따르고 main 도입은 별도 gate를 유지함.

원시 prompt·code·실행 로그·환경 식별자는 이 문서에 포함하지 않았다. 기존 원장을 수정하거나
평가 ERROR를 지우는 replacement를 수행하지 않았다.
