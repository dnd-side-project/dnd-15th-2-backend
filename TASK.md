# GitHub Issue #113 Task Contract

> Generated at: `2026-08-18T20:03:43+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `필터링 관측·감사와 production gate`
- GitHub Issue: `#113`
- Branch: `feat/gh-113-filtering-observability-gate`
- Base branch: `main`
- 선행 이슈 `#103`~`#112` 전부 CLOSED 확인(`#112`는 PR `#159`가
  2026-08-18에 병합됐다).

## Objective

- 필터링 시스템은 `#103`~`#112`로 판정·재시도·수동 검토·이의제기까지 갖췄지만,
  **운영 중에 무슨 일이 일어나는지 볼 방법이 없다.** 저장소 전체에
  `MeterRegistry` 사용처가 하나도 없고 actuator·micrometer 의존성도 없다.
- 운영자 행위는 "누가·언제"만 남고 **왜·어떤 정책 버전으로**가 빠져 있다
  (`ReleasePromotionHistoryEntry`, `ManualReviewCase.resolve`,
  `AppealCase.decide` 모두 `reason`과 `policyVersion`이 없다). `INV-APL-012`가
  요구하는 감사 4요소 중 둘이 없는 상태다.
- 법률·계약·보안 확인 항목이 미결인 채로 필터링이 production에서 켜지면
  안 되는데, 그것을 막는 장치가 없다.
- 이 세 구멍을 메운다.

## Scope

1. **운영자 감사 이력 통합(`INV-APL-012`)**
   - `operator_action_audit` 신규 append-only 테이블(V20): `operator_user_id`,
     `action_type`, `target_type`, `target_key`, `reason_code`, `reason_text`,
     `policy_version`, `occurred_at`. 대상 식별자를 문자열 한 컬럼으로 통일한
     이유는 snapshot health가 `modelSnapshot` 문자열을, 나머지가 숫자 id를
     키로 쓰기 때문이다.
   - 도메인 `OperatorActionAudit`, `OperatorActionType`, repository와
     기록 지점 하나(`OperatorActionAuditRecorder`).
   - 필터링의 운영자 권한 변경 경로 전부에 같은 트랜잭션으로 배선한다:
     release `offline-evaluation`/`shadow`/`canary`/`promote`/`rollback`,
     manual review `decide`, appeal `decide`/`extend`,
     snapshot health `confirm-permanent`, emergency migration.
   - 해당 endpoint 요청 본문에 `reason`을 필수로 추가한다(운영자 API 계약 변경).
2. **관측 지표(`INV-CMP-001`, `INV-CMP-002`)**
   - Micrometer 도입과 계측: 경로별 latency·timeout·오류, release/actual model별
     판정 분포, 언어별 queue 체류와 deadline 경과, logical attempt 대비 실제
     공급자 호출 수, manual overturn·appeal·aging·priority fallback,
     Slack delivery 결과.
   - metric tag 허용목록과, 원문·직접 식별정보가 tag나 metric 이름에 실리지
     못하게 막는 가드와 회귀 테스트.
3. **Production 활성화 게이트(`INV-CMP-005`, `INV-CMP-006`)**
   - 법률·보안 확인 항목이 명시적으로 승인되지 않으면 필터링 파이프라인을
     production에서 켤 수 없는 fail-closed 게이트.
   - 승인 대상 항목을 열거한 체크리스트 문서. 승인 행위 자체는 사람의 몫이다.
4. 단위 테스트, PostgreSQL 통합 테스트와 테스트 계획·보고서.

## Design decisions (구현 전 확정, 리뷰 필요)

1. **Micrometer는 도입하되 exporter는 고르지 않는다.** 이슈가 "외부 연동:
   관측·경보 도구 (미정)"이라고 못박았다. Micrometer는 vendor-neutral
   추상이라 지금 계측해 두고 backend는 나중에 붙일 수 있다. actuator를
   추가하되 web endpoint 노출은 전부 끈다 — springdoc을 운영 기본
   `api-docs.enabled=false`로 둔 것과 같은 처리이며, 미매칭 경로는
   `SecurityConfiguration`의 fallback `denyAll` 체인이 이미 막는다.
2. **감사 이력은 기존 이력 테이블을 대체하지 않고 추가한다.**
   `release_promotion_history`, `filter_job_status_history`,
   `manual_review_priority_evaluation`은 각자 도메인 이벤트 원장이고,
   `operator_action_audit`은 "사람이 무엇을 왜 바꿨는가"의 단일 원장이다.
   기존 원장을 옮기면 이미 배포된 계약을 깨고 마이그레이션 위험만 커진다.
3. **`reason`은 필수 입력으로 받는다.** 서버가 기본값을 채우면 감사 이력에
   "왜"가 사실상 없는 것과 같다. 운영자 API 계약이 바뀌지만, `INV-APL-012`를
   만족하려면 다른 방법이 없다.
4. **`policy_version`은 행위 시점의 정책 식별자를 그대로 적재한다.**
   행위마다 관련 정책이 다르므로(우선순위 정책, 접수 기간 정책, release
   정책) 단일 전역 버전을 만들지 않고 호출 지점이 자신의 정책 버전을 넘긴다.
   해당 정책이 없는 행위는 고정 상수를 쓰고 그 사실을 주석에 남긴다.
5. **metric tag는 허용목록 방식이다.** 금지목록은 새 필드가 생길 때마다
   구멍이 난다. 허용된 tag 키만 통과시키고 나머지는 거부한다
   (`INV-CMP-001`, `INV-CMP-002`).
6. **production 게이트는 fail-closed다.** 확인 항목이 하나라도 비어 있으면
   필터링 활성화 시도가 기동 실패로 끝난다. "확인하지 못했다"를 "확인됐다"로
   해석하지 않는다.
7. **감사 배선 범위는 필터링 도메인으로 한정한다.** 질문 제안 검토
   (`/admin/questions/proposals/**`)와 신고 처리도 운영자 행위지만 `#113`의
   부모 이슈 범위 밖이다. 같은 테이블을 쓰도록 설계하되 배선은 하지 않는다.

## Explicit exclusions

- `SlackNotifier` 실제 구현체와 worker scheduler 배선(`#111`이 이연). 이슈가
  "secret 저장·rotation"을 제외했는데 webhook 구현은 secret 취급이 전제라
  이 이슈에서 완결할 수 없다. `SlackNotifier`,
  `SlackManualReviewNotificationDispatchWorker`,
  `SnapshotHealthProbeRecorder`의 "#113으로 이연" 주석은 후속 이슈를 가리키도록
  정정한다.
- `AnswerModerationDeadlineWorker`, `AnswerModerationVerdictWorker`,
  `RecipientExpirationSweepWorker`의 `@Scheduled` 배선.
- metric exporter 선택과 경보 규칙 자체(도구 미정).
- OpenAI DPA·Services Agreement, data residency·processing location,
  subprocessor, quota/SLA.
- 원문·case·appeal·로그의 접근 권한, 보관·삭제·익명화, legal hold.
- 대상 국가별 UGC·신고·통지·이의제기와 국외 이전 적용 범위.
- local dictionary·dataset의 상업 이용 조건.
- secret 저장·rotation과 관리자 권한 분리.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `operator_action_audit` 스키마·배선, metric 계측과 tag 가드, production 게이트, 테스트 | Feature executor | 감사 누락 경로가 없는지, metric에 원문·식별정보가 실릴 수 있는 경로가 없는지, 게이트가 확인 누락 시 열리지 않는지 |

## Existing user-owned changes

- `origin/main`(4b8bc4e)에서 새로 분기했다. 분기 시점 `git status --short`는
  비어 있었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 운영 지표에 원문이나 직접 식별정보를 불필요하게 복제하지 않는다.
      (`INV-CMP-001`, `INV-CMP-002`) `FilteringMetricTags`가 허용목록 방식으로
      tag 키를 제한하고, 값 길이 상한으로 자유 텍스트 유입을 막는다. 허용목록에
      사용자 식별자·원문 계열 키가 없음을 UNIT-002가, 기록된 모든 meter의 tag
      키가 허용목록 안임을 UNIT-013이 전수 확인한다.
- [x] 모든 수동·운영자 권한 변경에 actor, reason, policy version과 시간이
      남는다. (`INV-APL-012`, 필터링 도메인 범위) `operator_action_audit`
      원장과 `OperatorActionAuditRecorder`를 만들고 release 전이 5종, 수동 검토
      결정, 이의제기 결정·연장, snapshot health 승인, 긴급 이관에 배선했다.
      네 요소를 도메인·DB CHECK가 각각 강제하고(UNIT-004~006, INT-006), 감사가
      결정과 같은 트랜잭션임을 `Propagation.MANDATORY`와 INT-005가 보장한다.
      `reason`은 운영자 API 요청 본문의 필수 항목이 됐다.
- [x] 법률·계약상 `확인 필요` 항목은 production 활성화 전에 책임자 승인을
      받는다. (`INV-CMP-005`, `INV-CMP-006`) `FilteringProductionGate`가
      fail-closed로 동작해, 활성화를 요청했는데 확인 항목이 하나라도 비면 기동을
      실패시킨다(UNIT-007~009). 확인 항목과 절차는
      `docs/filtering-production-gate.md`에 있다. 승인 행위 자체는 사람의 몫이다.
- [x] 승인된 테스트 계획과 실행 보고서가 존재한다.
      계획 `docs/test-plans/gh-113-TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE.md`
      (Status: Approved), 보고서
      `docs/reports/tests/gh-113-TEST-PLAN-GH-113-FILTERING-OBSERVABILITY-AND-GATE.md`.
      단위 636건·통합 482건 전체 통과했다(신규 단위 14, 통합 5).

## Delivered vs deferred

계획했으나 이번에 넣지 않은 것과 그 이유를 명시한다.

- **판정 경로 계측 미배선.** `FilteringMetrics`의 계측 메서드는 만들었지만
  `ModerationPipelineService`에 연결하지 않았다. 그 클래스는 `PolicyEngine`
  구현체가 미정이라 의도적으로 Spring bean이 아니고, 호출자가 직접 생성한다.
  지금 배선하면 손으로 만드는 생성 지점 10곳을 고쳐야 하는데 정작 그것을
  구동하는 프로덕션 경로가 없어 관측할 대상이 없다. pipeline이 bean으로
  배선되는 후속 이슈에서 함께 넣는 것이 맞다. 그 결과 계획의 INT-003,
  INT-007~INT-011은 실행하지 않았다.
- **exporter·경보 규칙 없음.** 이슈가 관측·경보 도구를 미결정으로 뒀다.
  Micrometer는 vendor-neutral 추상이라 지금 계측해 두고 backend는 나중에 고른다.
