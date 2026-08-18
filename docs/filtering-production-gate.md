# 필터링 production 활성화 게이트

GitHub Issue `#113`(F11)이 만든 게이트의 운영 문서다. 필터링 시스템을
production에서 켜기 전에 사람이 확인해야 하는 항목과, 그 확인을 코드가 어떻게
강제하는지를 정의한다.

## 1. 게이트가 하는 일

`qello.filtering.production.enabled=true`로 두면
`FilteringProductionGate`가 기동 시점에 아래 확인 항목을 검사한다. 하나라도
비어 있으면 **애플리케이션이 기동하지 않는다.**

경고 로그만 남기고 뜨는 방식을 쓰지 않는다. 아무도 로그를 보지 않는 사이
사용자 콘텐츠가 외부 moderation 공급자로 나가기 때문이다. fail-closed다.

비활성 상태(`enabled=false`, 기본값)에서는 확인 항목을 검사하지 않는다. 게이트는
"켜려는 시도"에만 개입하며, 개발·테스트 환경이 빈 값 때문에 못 뜨는 일을 만들지
않는다.

## 2. 확인 항목

각 항목은 boolean이 아니라 **확인한 근거를 가리키는 문자열**을 값으로 받는다.
`true` 한 글자는 누가 언제 무엇을 근거로 확인했는지를 남기지 않아, 나중에
책임자를 특정할 수 없다.

값에는 승인 문서의 식별자나 결재 번호처럼 추적 가능한 참조를 넣는다. 실제
계약서 내용, 계정 식별자, 토큰, 서버 주소는 넣지 않는다.

| 프로퍼티 | 확인해야 하는 것 |
| --- | --- |
| `qello.filtering.production.data-processing-agreement` | moderation 공급자와의 DPA와 Services Agreement가 체결됐고, 사용자 콘텐츠를 처리 위탁하는 범위가 그 계약 안에 있는가 |
| `qello.filtering.production.data-residency` | 콘텐츠가 실제로 어느 국가에서 처리·저장되는지 확인했고, 국외 이전이 필요한 경우 그 근거와 고지가 갖춰졌는가 |
| `qello.filtering.production.retention-policy` | 원문·판정·case·appeal·감사 로그의 보관 기간, 삭제·익명화 절차, legal hold 처리가 정해졌는가 |
| `qello.filtering.production.content-safety-policy` | 대상 국가의 UGC·신고·통지·이의제기 요구사항을 만족하는가. 특히 이의제기 접수 기간(6개월)이 각 관할의 하한을 밑돌지 않는가 |
| `qello.filtering.production.secret-handling` | 공급자 API key와 Slack webhook의 저장·rotation 방식, 그리고 그 값에 접근할 수 있는 관리자 권한 분리가 정해졌는가 |

## 3. 이 게이트가 보장하지 않는 것

게이트는 **확인이 있었다는 사실**만 강제한다. 확인의 내용이 옳은지, 법률 검토가
충분했는지는 판단하지 않는다. 그것은 사람의 몫이다(`INV-CMP-005`,
`INV-CMP-006`).

또한 게이트가 열려도 다음은 자동으로 켜지지 않는다. 전부 `#113` 범위 밖이다.

- moderation pipeline 컴포넌트의 Spring bean 등록(`PolicyEngine` 구현체 미정)
- `AnswerModerationDeadlineWorker`, `AnswerModerationVerdictWorker`,
  `SnapshotHealthProbeRecorder`, `SlackManualReviewNotificationDispatchWorker`의
  스케줄러 배선
- `SlackNotifier` 실제 구현체
- metric exporter와 경보 규칙

## 4. 활성화 절차

1. 위 다섯 항목을 각 책임자가 확인하고 승인 근거를 남긴다.
2. 배포 환경의 설정에 각 항목의 참조 문자열을 주입한다. 값은 저장소에 커밋하지
   않고 환경별 설정으로 주입한다.
3. `qello.filtering.production.enabled=true`로 전환한다.
4. 기동에 실패하면 예외 메시지에 비어 있는 항목 이름이 나온다. 그 항목의 확인을
   끝내기 전에는 우회하지 않는다.

## 5. 관측

지표는 Micrometer의 `MeterRegistry`에만 기록되고 exporter가 없어 프로세스 밖으로
나가지 않는다. 관측·경보 도구를 고른 뒤 exporter를 붙이는 것이 다음 단계다.

모든 metric tag는 `FilteringMetricTags`의 허용목록을 통과해야 한다. 답변 원문,
사용자 식별자, 닉네임은 어떤 경로로도 tag가 될 수 없다(`INV-CMP-001`,
`INV-CMP-002`). 새 tag가 필요하면 허용목록에 추가하면서 그 값이 개인 식별자가
될 수 있는지 먼저 검토한다.
