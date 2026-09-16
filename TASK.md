# GitHub Issue #225 Task Contract

## Work gate

- Title: `Phase 3 모델별 지침 구조 시작·추가 읽기 비용 비교`
- GitHub Issue: `#225`
- Branch: `chore/gh-225-instruction-model-phase3`
- Base branch: `main`
- Task ID: `GH-225-INSTRUCTION-MODEL-PHASE3`
- Design ID: `HARNESS-DESIGN-GH-225-001`
- Status: `EVALUATION_VERIFIED_COMMIT_APPROVED`
- Intake approval: 사용자가 새 작업에서 Issue 생성과 Phase 3 시작을 명시적으로 요청했다.
- Project: P2 / In Progress / Chore / Sprint 미지정(활성 iteration 없음).
- Base commit: `4c63ee6348b9610470e3fe6a5b7429549008be77`

## Objective

동일 지침 구조에서 gpt-5.6-sol/high와 gpt-6-astra/medium의 시작 및 추가 읽기 비용을 비교한다.
구조에 귀속 가능한 지표와 전체 API 사용량을 분리한다.
사용자 요청으로 Astra effort를 medium으로 변경했다. 결과는 모델+effort 실행 구성 비교이며 순수 모델 효과로 해석하지 않는다.

## Scope

- 최신 Phase 2 승인 범위·공식 문서·계측 한계 감사.
- [설계](docs/superpowers/specs/2026-09-13-instruction-model-phase3-design.md)와
  [준비 계획](docs/superpowers/plans/2026-09-13-instruction-model-phase3.md) 작성.
- 설계/예산 승인 후에만 계측 구현 및 제한된 모델 평가와 독립 검증.
- 승인된 산출물: TASK.md, 설계/계획, Phase 3 전용 runner·environment·order·runs·report·verification. 기존 Phase 2 파일 변경은 제외한다.
- 실행 승인: 사용자가 Astra medium 변경 후 “계획대로 진행해줘”로 B3 고정, pilot 2회+본 비교 12회 및 $40/8M/160k/90분 상한을 승인했다. 사전 계측 gate와 별도 커밋 승인은 유지한다.

## Explicit exclusions

- 기존 204회 평가와 actual-change smoke 재활성화.
- 실제 지침 도입, 앱·Terraform·인프라·DB·배포·운영 변경.
- 공통 측정 도구·기간 Before/After·OTel·Grafana·EC2.
- 기존 checkout 및 A0~A3/B0~B3 worktree 변경·삭제.
- 승인 없는 평가 모델 호출·reset credit·commit·push·PR·merge.
- 원시 로그와 민감정보 커밋/별도 외부 전송.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 계약·실험 설계 | 오케스트레이터 | 사람의 후보·실행 예산 승인 |
| 승인 후 계측·실행 | 실행 에이전트 | 독립 검증 에이전트 |
| 실제 산출물·증거 판정 | 독립 검증 에이전트 | 사람 최종 판단 |

## Existing user-owned changes

시작 시 git status --short는 빈 출력이었다. 현재 격리 worktree에서만 문서를 작성한다.
Harness가 사용 중인 부모 로컬 branch 갱신을 건너뛰었고 원격 부모 commit에서 새 branch를 만들었다.

## Validation

준비 단계: Issue/Project/branch/base 일치, 문서 링크와 비용 산술, git diff --check, harness check,
 hooks:validate. 완료/PR 단계: ./harness pr-ready --project-tests와 필수 검증을 실행한다.
평가 전 합성 계측 검증과 승인된 pilot gate가 필요하다. 기존 실험 통과는 Phase 3 통과가 아니다.

## Completion criteria

- [x] 후보·횟수·예산 승인과 환경 고정 증거.
- [x] instruction 귀속 가능 여부 및 전체 사용량 분리.
- [x] 승인된 반복 및 안전·정책·routing·false-block 판정.
- [x] 독립 검증·한계·실패·미검증 보고.
- [x] 구체적 커밋 초안 검토 및 승인.

## 사전 점검 결과

[GH-225 보고서](docs/experiments/codex-agents/gh-225-report.md): 실행 승인은 완료됐으나 full delivered catalog 동일성 증거를 확보하지 못해 사전 gate에서 차단됐다. 평가 호출 0회. CLI 변경과 attribution 미검증 범위도 기록했다.

## 제한된 환경 동등성 승인

사용자는 전체 도구 전달 동일성을 미검증으로 명시하고 같은 CLI·설정·플러그인·권한을 고정하는 제한된 비교 제안에 “좋아”로 승인했다. full catalog gate만 변경하며 instruction source-span partial/proxy의 본 비교 허용은 아직 포함하지 않는다.

## Pilot 1 실행 결과

Sol/high 1회 실행 완료, 프로세스 exit 0. POST_ENVIRONMENT_DRIFT로 측정 표본은 채택하지 않는다. 입력314416/캐시284416/출력5464, 환산 $0.3430464. 실제 tool10회와 원시 사용량은 독립 검증했다. custom 출력 parser 누락을 별도 수정하며 원본 ERROR와 원시 증거를 보존한다. Astra 및 본 비교는 실행하지 않았다.

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

## Batch2 pilot gate

새 Sol/high와 Astra/medium pilot 모두 독립 검증 PASS. 환경/usage/품질/안전 확인 및 partial 승인 조건을 충족했다. 비공개 pilot-review.json은 manifest와 두 결과 hash에 연결됐다. 본 비교12회를 시작한다.

## 평가 완료

총15회(과거 무효1+새 pilot2+본 비교12) 완료. 누적 입력3,864,044/출력59,037/1,795.1초/API 환산 $6.8557556. 본 비교 품질10 PASS/2 FAIL(7·10), 안전12 PASS. L3 두 쌍은 효율 비교 제외. 원본 실패를 보존하며 추가 호출은 없다. 최종 결과는 GH-225 보고서를 따른다.

최종 저장소 검증 PASS: collector17, 단위1,064, 통합735 모두 통과. Docker 미기동 첫 실패를 보존하고 복구 후 pr-ready 재검증 통과. harness check·hooks:validate·diff --check 통과. 구체적 3개 커밋 초안은 GH-225 보고서에 있으며 승인 대기다.

사용자가 결과 요약 확인 후 “승인할게”로 보고서의 3개 커밋 초안을 승인했다. 승인된 순서와 파일 범위로 로컬 커밋하며 push·PR은 포함하지 않는다.
