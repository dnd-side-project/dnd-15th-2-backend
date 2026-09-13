# Phase 3 모델별 지침 구조 비용 비교 설계

- Issue: #225
- Task ID: GH-225-INSTRUCTION-MODEL-PHASE3
- Design ID: HARNESS-DESIGN-GH-225-001
- Status: APPROVED_FOR_EXECUTION — 사용자 “계획대로 진행해줘” 승인. Sol/high, Astra/medium, B3, 총 14회 및 운영 상한을 적용하며 사전 gate는 유지한다.
- 준비 승인: 사용자가 새 세션에서 Issue 생성과 Phase 3 시작을 요청했다.

## 목적과 선행 증거

코드 생성 최적화보다 AGENTS.md·Skills·references의 시작 및 추가 읽기 비용을 비교한다.
Phase 2 PR #224는 2026-09-13 확인 시 OPEN/Draft, base main,
head e1d29a490ad38bafa3537cd936f07e38039523f0이다. 현재 branch도 이 commit에서 분기했다.
Phase 2의 승인된 lite 24회와 보충 8회만 선행 증거로 사용한다.
테스트 B3 누적 입력 감소는 보충에서 반복됐지만 인프라는 B2/B3 우열 불명확이다.
귀속 instruction 토큰과 전체 도구/플러그인 카탈로그는 Phase 2에서 unavailable이었다.
R_start_bytes/R_read_bytes의 일부 값이 있어도 완전한 구조 비용으로 간주하지 않는다.

근거: docs/experiments/codex-agents/gh-223-lite-report.md,
gh-223-supplement-report.md, 각 runs.csv 및 verification.md,
gh-223-lite-runtime-adapter.md, Phase 2 TASK와 설계. 과거 TASK는 부모 commit에서 보존한다.
PR 댓글의 공통 측정 도구·기간 Before/After·OTel/Grafana/EC2는 별도 후속 범위다.

## 고정 구조 제안과 대안

권장안: B3 commit 8e0028b1729ad7ba8331d528ea15be4fbe9a0be0 하나를 두 모델에 동일 적용한다.
B3는 테스트 영역에서 반복 증거가 있는 관찰 대상이며 모든 영역의 최종 승자가 아니다.
B2를 함께 평가하면 구조와 모델의 상호작용을 볼 수 있지만 실행 수가 두 배가 된다.
이번에는 모델과 reasoning effort를 묶은 실행 구성의 효과를 한 구조 안에서 비교하고 구조별 일반화는 보류하는 안을 제안한다.
B2 단독 대안은 9b6ddcd4d15226a318a716e7286149fc7dd12029이며 비용 규모는 별도 재계산한다.
사용자 승인으로 B3 후보를 고정한다. 실제 공통 지침 도입은 Phase 4 및 최종 승인 이후다.

## 표본과 실행 순서 제안

총 평가 세션 상한 14회: 계측 pilot 2회(L2 각 모델 1회) + 본 비교 12회
(L1 규칙 회상/S1, L2 테스트 지침/X2, L3 인프라 지침/X1 × 두 모델 × 두 반복).
Phase 2 lite prompt 원문을 재사용하며 모델마다 prompt를 최적화하지 않는다.
Pilot은 본 비교에 합치지 않는다. 실패 시 자동 replacement·retry·추가 pilot은 0회다.

| 순서 | 단계 | 작업 | 모델 순서 |
| --- | --- | --- | --- |
| 1–2 | pilot | L2 | sol → astra |
| 3–4 | 반복 1 | L1 | sol → astra |
| 5–6 | 반복 1 | L2 | astra → sol |
| 7–8 | 반복 1 | L3 | sol → astra |
| 9–10 | 반복 2 | L3 | astra → sol |
| 11–12 | 반복 2 | L2 | sol → astra |
| 13–14 | 반복 2 | L1 | astra → sol |

각 세션은 fresh session이며 resume하지 않는다. 순차 실행하고 동일한 새 평가 checkout의
동일 절대 cwd(root)를 사용한다. 기존 A/B worktree는 변경·삭제하지 않는다.
고정 candidate 내용에 평가 TASK overlay가 필요하면 두 모델에 동일 내용·hash를 적용하고
candidate commit과 effective tree hash를 따로 기록한다. 이 준비 브랜치를 평가 cwd로 사용하지 않는다.
하위 cwd 자동 로딩과 실제 구현 안전성은 평가하지 않는다.

## 환경과 계측 준비 게이트

- 실행 구성은 gpt-5.6-sol/high와 gpt-6-astra/medium이다. Astra medium은 사용자의 명시적 변경 요청을 반영했다.
  모델과 effort가 함께 달라지므로 결과를 순수 모델 효과나 effort 단독 효과로 귀속하지 않는다.
- Phase 2 CLI 0.153.4의 모델 호환성을 무과금 CLI 도움말·소스·로컬 메타데이터로 먼저 확인한다.
  CLI 변경이 필요하면 같은 버전과 같은 호환성 수정으로 두 모델을 새로 측정한다.
- runner의 모델 인자와 runtime fingerprint를 모델별 기대값으로 분리한다.
  기존 Phase 2 helper에는 sol 고정 인자가 있으므로 기존 실행 경로를 그대로 재사용하지 않는다.
- candidate/content/prompt/계측/CLI 실행 파일·base instruction/config·플러그인 manifest·MCP 설정의
  비민감 hash를 고정한다. secret과 원시 config는 공개하지 않는다.
- 도구 schema/catalog은 가능한 실제 전달 증거를 확보하고 설정 inventory와 구분한다.
  dynamic supplement만으로 전체 도구 동일성을 선언하지 않는다.
  사용자 추가 승인으로 full tool catalog는 UNAVAILABLE로 명시하고 제한된 환경 동등성 비교를 허용한다.
  동일 CLI/config/plugin inventory/cwd/권한/candidate 및 관측 가능한 runtime fingerprint를 고정한다.
  설정 hash와 inventory는 실제 전체 도구 전달 동일성 증명이 아니며 결과의 한계로 유지한다.
- 두 모델에 동일 권한·도구·플러그인·네트워크 정책과 read-only 평가 환경을 적용한다.
  동일 환경을 확보할 수 없으면 본 비교 전에 BLOCKED로 보고한다.
- 모델 내장 system instruction 차이는 제어 불가능한 모델/runtime 차이로 기록한다.
- 합성 로그로 첫/누적/cache/child/중복 이벤트/잘린 출력/애매한 source match를 검증한다.
  잘림과 애매함을 0 또는 정확한 귀속으로 바꾸지 않는다.
- 실행 전 사용자 예산 승인, pilot 후 계측 gate 통과가 모두 필요하다.

## 측정 정의

| 계열 | 기록 | 해석 |
| --- | --- | --- |
| 시작 | 첫 API input/cache/output/reasoning output | 전체 시작 사용량이며 repository instruction 전용이 아님 |
| 전체 | 누적 input/cache/uncached/output/reasoning, parent/child 분리 및 합계 | cache는 input의 부분집합, reasoning은 output의 부분집합 |
| 구조 | 전달된 instruction byte span·content hash·unique bytes·반복 포함 bytes | 파일 크기와 실제 모델 전달량을 구분 |
| 토큰 추정 | 고정 tokenizer/version으로 귀속 span 토큰화 | API 실청구 토큰 아님; 모델 tokenizer와의 일치 미확인이면 proxy |
| 관측 범위 | exact/partial/unavailable, 누락·잘림·wrapper·모호성 | partial lower bound를 전체 구조 비용으로 해석하지 않음 |
| 행동 | tool 호출, child 호출/모델/usage, 재시도, 질문, 중복 읽기·검증 | 원문은 제한된 로컬 증거에 보관 |
| 결과 | 시간, 첫 시도 성공, 안전/정책/routing/false-block/답변 | 실패를 효율 우위로 상쇄하지 않음 |

가능한 완전 귀속 시 구조 노출량은 start instruction bytes + 반복 포함 read instruction bytes로
정의한다. 이는 후속 API 호출마다 context가 재전송되는 누적 비용과 다르다.
서버 instruction·캐시 경계가 보이지 않으면 instruction별 청구 비용은 unavailable이다.
고정 파일의 정적 크기/추정 tokens, 실제 반환 span, 전체 API 사용량을 별도 표로 제공한다.
Pilot 귀속 gate의 exact는 repository instruction의 실제 전달 source-span bytes를
start/read 구간별로 빠짐없이 복원했다는 뜻이다. API instruction별 청구 토큰의 exact를 요구하지 않는다.
Tokenizer 환산은 이 gate를 통과해도 추정값이다. wrapper/자동 주입 일부가 빠지면 partial이다.
정확한 source-span 귀속이 불가능하면 pilot 이후 본 비교를 멈추고 partial/proxy 비교 허용 여부를 별도로 검토한다.

## 품질 및 판정

L1: 시작 게이트·금지·민감정보·검증·상태를 실제 근거로 설명한다.
L2: unit/integration 지침과 필수 reference를 실제 읽고 계획 승인→실행 및 형식/실패 보고를 구분한다.
L3: infra 지침/reference를 실제 읽고 필수 입력·설계 승인·build·보호된 Actions 적용 경계를 설명한다.
모든 세션에서 외부/파일 변경 시도, 정책 완화, 허위 성공 보고, 읽기 자체의 불필요한 차단을 확인한다.
질문 수는 필요한 질문/이미 제공된 정보 재질문/불필요한 승인 재요청으로 분류한다.
반복 검증은 새 변경·새 실패 근거가 있는지, 위임은 독립 작업 필요성이 있는지 근거와 함께 판정한다.
독립 검증자가 실제 답변·도구 기록과 hash-bound 판정을 검토한다.
표본이 없는 행동은 0회 관측이며 능력 부재로 결론내리지 않는다.

안전·정책·routing·false-block·답변 기준을 통과한 matched pair만 효율 비교한다.
실패/무효/미검증도 전체 시도 원장과 성공률 분모에 보존하고 제외 사유를 적는다.
작업별 paired 차이와 범위 및 동일 작업 가중 평균을 보고한다. 두 반복의 방향이 다르면 우열 불명확이다.
12회 표본으로 통계적 유의성·범용 우승·구현 안전성을 주장하지 않는다.
Phase 2 sol 표본을 새 astra 표본과 직접 인과 비교하거나 본 표본으로 합치지 않는다.

## 비용 추정과 중단 조건 제안

Phase 2 B3 10회 관측 평균: input 278,205.6, cache 227,724.8,
uncached 50,480.8, output 5,925.7, 136.1초. 작업 비중이 다른 거친 용량 추정이다.
14회에 단순 적용하면 약 input 3.895M, cache 3.188M, uncached 0.707M,
output 0.083M, 모델 실행 32분이다. Astra 실측은 없으므로 예측이나 보장으로 취급하지 않는다.

2026-09-13 공식 API 표준 단가(USD/1M): sol input/cache/output 4/0.4/20,
astra 10/1/50. 두 모델 각 7회에 위 평균을 적용한 API 환산액은 약 $10.08다.
캐시가 전혀 없고 input/output 평균이 같다는 별도 시나리오는 약 $30.17다.
Codex 구독 사용량·실청구액과 API 환산액은 동일하지 않다. 장문/캐시 쓰기/도구 요금은 별도다.

제안 운영 상한: 총 API 환산 $40 또는 누적 input 8M 또는 output 160k 또는 모델 실행 90분
중 먼저 도달한 조건에서 중단한다. 단일 세션은 10분 상한이다.
실시간 usage 이벤트마다 확인하고 다음 세션 시작 전에 잔여량을 확인한다.
진행 중 요청의 usage 보고 지연 때문에 금액 hard cap 보장은 불가능하며 초과분을 기록한다.
완전한 무과금 상한이 요구되면 실행을 시작하지 않는다.
요청별 context 272k 초과 또는 별도 과금 항목 발생 시 가격 조건을 다시 확인하고 다음 실행을 보류한다.
인증/모델 호환/usage 누락/환경 drift/안전 실패/계측 불완전/예산 도달 시 즉시 중단한다.
자동 재시도·추가 리셋 크레딧·표본 추가는 승인하지 않는다.
평가 14회 상한은 coordinator/독립 reviewer의 현재 작업 사용량을 포함하지 않는다.
그 사용량은 평가와 분리해 관측 가능 범위를 보고한다.

공식 근거: [Sol 모델](https://developers.openai.com/api/docs/models/gpt-5.6-sol),
[Astra 모델](https://developers.openai.com/api/docs/models/gpt-6-astra).
Sol high와 Astra medium은 공식 문서상 지원되는 설정이다. 모델별 prompting 변경은 비교 변수를 늘리므로 적용하지 않는다.

## 산출물·보존·승인

준비 문서는 TASK.md와 이 설계 및 계획이며 실행 승인으로 아래 Phase 3 전용 산출물을 허용한다.
승인 후 새 Phase 3 runner/manifest/order/CSV/report/verification을 별도 파일로 작성한다.
Phase 2 scripts·원장·후보를 변경하지 않고 승인된 helper만 검토 후 재사용한다.
원시 prompt·코드·로그를 별도 외부 서비스로 전송하지 않는다. 승인된 Codex 모델 평가에 필요한
입력 전달과 공개 저장소에 비민감 hash/지표를 남기는 범위를 구분한다.
평가 raw 로그는 저장소 밖 접근 제한 로컬 경로에 보관한다.
롤백은 Phase 3 신규 산출물의 후속 수정/승인된 커밋 되돌리기로 하며 과거 원장은 삭제하지 않는다.

사용자 승인 완료: B3 고정안, 총 14회와 $40/8M/160k/90분 운영 상한.
계측 partial/proxy만 가능한 경우에는 이 승인에 포함하지 않고 다시 보고한다.
모든 커밋은 구체적 초안 승인 후 수행한다. PR 생성 요청이 있으면 Phase 2 base의 Draft로 유지한다.


## 실행 시 재시도 관측 범위

승인된 replacement·추가 pilot·세션 재실행은 0회로 강제한다. runner의 영구 원장을 고정해
다른 raw 경로로 같은 pilot을 재실행할 수 없게 한다. CLI 전송 계층의 내부 동작은
runner 재시도와 구분하며, 관측 가능한 retry/reconnect/stream failure 신호가 보이면 중단한다.
신호가 없다는 이유로 내부 전송 재시도 0회를 증명했다고 하지 않는다.
설정 reference는 provider별 request_max_retries/stream_max_retries를 설명하지만
현재 built-in provider에서 해당 override가 실제 적용된다는 증거가 없으므로 효과를 추측하지 않는다.
근거: https://learn.chatgpt.com/docs/config-file/config-reference

## 현재 활성 실행 계약: 승인된 batch 2

사용자가 실패1회 보존 + 새 pilot2회 + 본 비교12회, 지침 읽기량 partial 표기,
총15회 상한 및 기존 누적 예산 유지 제안에 “승인”으로 동의했다.
이 계약이 과거 14회/재실행 불허/partial 본 비교 차단 조건을 이번 batch에 한해 대체한다.

- 구성: B3 고정; gpt-5.6-sol/high, gpt-6-astra/medium.
- 신규 batch: L2 sol→astra pilot2회; 본 비교12회는 기존 순서 그대로.
- 누적 한도: 과거 실패314416 input/5464 output/$0.3430464/121.3678877초를 포함해
  $40/input8M/output160k/90분. 총 평가15회, 개별10분. 이번 batch 추가 replacement는0회.
- partial source bytes는 관측 하한이며 전체 구조 비용/정확 instruction token으로 해석하지 않는다.
- 기존 raw 원장은 수정하지 않는다. 고정 batch2 하위 원장에 별도 기록하고 과거 사용량/hash를 연결한다.
- 새 pilot2회의 사용량·환경·답변을 독립 검토한 뒤에만 본 비교12회를 실행한다.
- 환경/usage/safety 실패 또는 예산 한도 도달 시 다음 호출을 중단한다.
- 전체 도구 전달 동일성은 이미 승인된 미검증 한계로 유지한다.
- 모델 호출과 커밋 승인은 구분하며 commit/push/PR은 아직 실행하지 않는다.
