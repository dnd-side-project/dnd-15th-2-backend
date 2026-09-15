# GH-227 여섯 과제 실행 계약

- 상위 계약: GH-227-EVAL-V2-001 (`gh-227-v2-execution-contract.md`)
- 상태: OFFLINE_PREPARED — 격리 fixture·oracle 검증 완료; 실제 평가용 입력 분리와 모델 실행은 미완료
- 이 문서는 준비자/검증자용이다. 실행자에게는 과제별 요청·요구·허용 파일·검사만 추출해 제공하고 정답 출처/채점표/숨긴 응답표는 제외한다.
- 모든 경로는 평가 사본의 저장소 상대 경로다. 실제 저장소 구현 지시가 아니다.

## F. 프로필 이미지 조회 기능

**수정 전 제품:** b81ff9e9451c7d2d6db5af249ab7a60bf1b7e1fb / 출처 #166.

**실행 요청:**

> 계정의 프로필 이미지 참조를 조회 가능한 이미지 정보로 바꾸는 기능을 추가하세요. 본인 소유 READY 자산을 조회하고, 유효한 참조가 없으면 설정된 기본 이미지로 처리하세요. 조회 과정에서 계정이나 자산을 수정하지 마세요. 승인된 범위의 단위 검증과 보고를 완료하세요.

**제공 계약:** 기존 Account, MediaAssetRepository, MediaStorageProperties, ObjectStoragePort, PresignedView를 사용한다. 신규 `ProfileImageResolver.resolve(Account)`는 URL·만료 시각·기본 이미지 여부를 담은 `ResolvedProfileImage`를 반환한다. 저장소 키·버킷 정보는 반환값에 넣지 않는다. 이 항목은 최종 평가에서 독립 소스 검토로 확인한다. 메서드/record 이름은 양쪽 동일한 호출 계약이며 구현 방법은 지정하지 않는다. 외부 객체 저장소를 호출하지 않고 port를 mock한다.

**수정 허용:**

- `src/main/java/com/dnd/qello/account/service/ProfileImageResolver.java` (신규)
- `src/test/java/com/dnd/qello/account/service/ProfileImageResolverTest.java` (신규 제안; 역사적 테스트가 아님)
- `evaluation-output/F/report.md`

ProfileService, HTTP endpoint, 이미지 변경/제거 기능, 설정·의존성 변경은 제외한다. 필요한 port/property/domain API가 수정 전 commit에 있음을 읽기 검증했다.

**TEST-PLAN-GH-227-V2-F:**

| 시나리오 | 인수 조건 |
| --- | --- |
| F-001 | 참조가 없으면 기본 키와 viewUrlTtl로 URL을 발급한다 |
| F-002 | 참조를 찾지 못하거나 타인 소유 또는 READY 이외이면 기본 이미지로 처리한다 |
| F-003 | 본인 READY 자산이면 해당 키와 동일 TTL을 사용한다 |
| F-004 | 반환 URL·만료 값은 port 결과와 일치하고 기본값 여부가 정확하다 |
| F-005 | 모든 조회에서 저장/삭제·참조 제거·자산 상태 변경이 없다 |

검사: `./gradlew test --tests 'com.dnd.qello.account.service.ProfileImageResolverTest'` 및 상위 계약의 최종 검사. 준비자 oracle는 위 다섯 관측을 독립적으로 검사한다. ProfileServiceTest의 후속 예외 번역 동작을 가져오지 않는다.

## B. 목록과 칩 집계의 읽기 불일치 버그

**수정 전 제품:** 433b62e54d87ce4dc2b8657824c48ed73c33a21d / 출처 #212.

**실행 요청:**

> 받은 글 목록을 읽는 동안 다른 트랜잭션에서 글이 추가되면 목록과 방향별 칩 수가 서로 다른 상태를 보여줄 수 있습니다. 실제 애플리케이션 진입점에서 원인을 확인하고 같은 요청의 목록과 집계가 일관되도록 수정하세요. 기존 상세 조회·넘김·계정 자격 동작과 실패 시 롤백을 유지하세요.

**수정 허용:**

- `src/main/java/com/dnd/qello/feed/service/InboxApplicationService.java`
- `src/test/java/com/dnd/qello/feed/service/InboxApplicationServiceTest.java`
- `src/integrationTest/java/com/dnd/qello/InboxListIsolationIntegrationTest.java`
- `evaluation-output/B/report.md`

**TEST-PLAN-GH-227-V2-B:**

| 시나리오 | 인수 조건 |
| --- | --- |
| B-001 | 첫 목록 읽기 후 별도 transaction commit이 있어도 같은 요청의 칩 집계에 새 행이 섞이지 않는다 |
| B-002 | 방향 필터 요청에서도 다른 방향의 후속 commit이 칩 집계에 섞이지 않는다 |
| B-003 | 실제 Spring bean을 통해 실행하며 단순 annotation 문자열 검사만으로 통과하지 않는다 |
| B-004 | 상세 projection 실패 시 OPENED 변경이 rollback된다 |
| B-005 | 계정 자격·상세·넘김의 기존 반환/실패 계약을 유지한다 |

검사:

```bash
./gradlew test --tests 'com.dnd.qello.feed.service.InboxApplicationServiceTest'
./gradlew integrationTest --tests 'com.dnd.qello.InboxListIsolationIntegrationTest'
```

PostgisContainerIntegrationTestSupport, Inbox124IntegrationFixtures, Inbox124TestClockConfiguration은 읽기 전용 지원 파일이다. 다른 transaction의 commit 시점을 latch/barrier로 제어하고 시간 sleep의 우연한 순서로 판정하지 않는다. 지원 코드 변경이 필요하면 실행 전에 fixture를 수정한다. DB 컨테이너가 없으면 환경 실패이며 버그 수정 성공으로 판정하지 않는다.

## R. 재시도 정책 생성 중복 제거

**수정 전 제품:** 0f89f6b801fe9f1cda91d0183783e4ff6a34f28e / 출처 #182.
기존 카탈로그의 오류 문구 개선 commit 대신 그 다음 호출부 리팩터링을 대상으로 바꿨다. 변경 전에는 필요한 factory가 이미 있다.

**실행 요청:**

> scheduled worker와 push 설정에 반복된 재시도 정책 생성 코드를 기존 설정 객체의 팩토리로 통일하세요. 설정값·오류 메시지·공개 API·스케줄링·실패 복구 동작은 유지하고 새 추상화나 설정을 추가하지 마세요.

**수정 허용:**

- `src/main/java/com/dnd/qello/notification/config/PushConfiguration.java`
- `src/main/java/com/dnd/qello/scheduling/adapter/DirectionMatchingScheduledAdapter.java`
- `src/main/java/com/dnd/qello/scheduling/adapter/NotificationFanOutScheduledAdapter.java`
- `src/main/java/com/dnd/qello/scheduling/adapter/RecipientNotificationFanOutScheduledAdapter.java`
- `src/main/java/com/dnd/qello/scheduling/adapter/ReportResolutionFanOutScheduledAdapter.java`
- `evaluation-output/R/report.md`

**검증 시나리오:**

| 시나리오 | 인수 조건 |
| --- | --- |
| R-001 | 네 outbox adapter와 push 설정이 이미 존재하는 해당 retry factory를 사용한다 |
| R-002 | 각 worker의 maxAttempts·delay/cap·정책 종류를 유지한다 |
| R-003 | scheduling/활성화/batch/lease/identity/clock/metrics를 유지한다 |
| R-004 | Push bean 개수·주입 및 local/test NoOp provider 동작을 유지한다 |
| R-005 | 설정 유효성·오류 메시지·공개 API·factory 구현이 바뀌지 않는다 |
| R-006 | 실패 이후 다음 trigger가 실행되는 기존 동작을 유지한다 |

검사:

```bash
./gradlew test --tests 'com.dnd.qello.scheduling.adapter.CoreWorkerScheduledAdapterTest' --tests 'com.dnd.qello.notification.config.PushConfigurationTest' --tests 'com.dnd.qello.scheduling.config.WorkerSchedulingPropertiesTest'
```

기존 테스트는 수정하지 않는다. 준비자 oracle도 변경 전후 같은 값 전달·조건·실패 복구를 확인하며 jitter의 정확한 난수값 일치를 요구하지 않는다. 역사적 참고0bc7a7f는5파일 추가5/삭제19줄이지만 줄 수/정답 patch 동일성을 채점하지 않는다.

## A. 데이터 접근 아키텍처 설계

**제품/문서 snapshot:** 38895830c508a9efd246fe4f6f17e2db55c44d81 / 출처 #35.
ADR-0002 최초 추가 직전이며 accepted로만 바뀌기 직전 commit을 쓰지 않는다. 실행자에게 결정이 이미 적힌 ADR을 주는 오류를 방지한다.

**실행 요청:**

> Qello의 데이터 접근 방식을 설계하세요. 일반 계정·질문·답변의 생성/조회/변경과 함께 방향·거리 검색, 수신 용량의 조건부 갱신, 행 잠금, 수신자 일괄 저장, Outbox claim을 지원해야 합니다. 작은 팀이 유지보수할 수 있도록 대안을 비교하고 책임·트랜잭션 경계와 검증 계획을 제안하세요. 구현이나 DB 변경은 하지 마세요.

**동일 제공 사실:** PostgreSQL 기반이며 방향/거리 계산이 필요하다. aggregate 간 거대한 ORM 관계를 만들지 않아야 하고 동시성 무결성이 필요하다. 트래픽 수치·월 예산·가용성 수치는 미확정이다. 도입할 persistence 의존성은 설계 제안으로만 적고 실제 build를 바꾸지 않는다. 제품 범위는 snapshot의 BACKEND_ROADMAP을 제공한다. 미래 ADR/스키마·정답 코드·과거결과는 제공하지 않는다.

**수정 허용:** `evaluation-output/A/design.md`, `evaluation-output/A/report.md`.

**필수 rubric:**

1. 일반 CRUD·공간 조회·조건부 갱신/잠금·bulk/claim의 요구를 각각 다룬다.
2. 하나의 접근법에 고정하지 않고 적합한 대안과 선택 근거·탈락 이유를 설명한다. JPA/JDBC 혼합만 유일한 정답으로 강제하지 않는다.
3. domain/application의 의존 경계, cross-aggregate 참조 및 transaction/flush 일관성을 명시한다.
4. 경쟁 요청·원자성·중복 claim의 실패 모드와 실제 DB 검증 방법을 제안한다.
5. 운영 부담·미래 전환 비용은 정성적으로 설명하고 미확정 가격/성능 수치를 꾸며내지 않는다.
6. 미결정 사항과 사람 검토 항목을 기록하며 자기 승인·구현·배포를 하지 않는다.

검사: `git diff --check`; 독립 평가자가 허용 경로와1~6항목의 구체적 근거를 확인한다. 필수 항목 누락 또는 요구와 모순되는 선택은 FAIL. 깊이 차이는 별도 평점/서술로 보존하며 필수 항목 통과를 완전히 동일한 답변 품질로 표현하지 않는다.

## Q. 국가 선택 요구사항 구체화

**제품/문서 snapshot:** 5d276e08bb5f9f08b1c1698d311db813a073913f / 출처 #88.
국가 필수값 설계 최초 추가 직전이다. 당시 인증 구현·기존 API는 제공하되 ONBOARDING_COUNTRY_DESIGN/ADR-0007/후속 구현을 제공하지 않는다.

**첫 요청:**

> 앱 가입 시 국가를 선택하도록 바꾸고 싶습니다. 구현에 필요한 중요한 질문을 정리하고, 답변을 바탕으로 범위·요구사항·완료 조건을 작성하세요. 구현과 마이그레이션은 하지 마세요.

**수정 허용:** `evaluation-output/Q/requirements.md`, `evaluation-output/Q/report.md`.

**준비자 전용 고정 응답표:**

| 질문 의미 | 같은 의미에 제공할 답변 |
| --- | --- |
| 대상·필수 시점 | 일반 앱 USER는 계정/자격증명 발급 전 국가 선택이 필수이고 OPERATOR는 예외다 |
| 입력·지원 국가 | alpha-2 코드를 공백 제거/대문자 정규화하고 COUNTRY 마스터에 있는 값만 허용한다. locale/GeoIP 추정은 하지 않는다 |
| 기존 지역과 관계 | coarseRegionCode의 최상위 COUNTRY가 선택 국가와 같아야 한다 |
| API·응답 | 기존 기기 등록 API에 필드를 추가한다. 성공 응답·token claim 확장은 하지 않는다 |
| 실패/중간 상태 | 검증 실패 시 계정·기기·토큰 생성0. 임시계정/온보딩 세션 없이 저장 실패도 원자적으로 rollback한다 |
| 기존 사용자 | 기존 coarse region의 국가가 하나로 확정될 때만 이관한다. 불명확하면 추정하지 않고 배포를 보류한다. 기존 migration은 수정하지 않는다 |
| 구버전 앱·배포 | 모바일의 필드 지원이 선행돼야 한다. 구버전 정책·실제 운영 이관 가능성은 출시 전 팀 확인이 필요하다 |
| 개인정보 | 제출 국가/지역값을 오류 설명·로그에 노출하지 않는다 |
| 그 외 예산·일정·새 기능 | 현재 미확정이다. 가정/후속 결정으로 기록하고 범위를 추가하지 않는다 |

두 군 모두 최초 질문 응답 후 최대2회 같은 세션에서 답변을 제공한다. 준비자가 질문 의미를 위 key에 대응시키고 사용한 key·질문·정확한 답변·turn을 기록한다. 이미 답한 질문은 같은 답을 반복한다. 표 밖 질문에는 마지막 행의 답변을 제공한다. 추가 해설/정답 유도는 하지 않는다. 질문하지 않은 항목의 답을 먼저 주지 않는다. 자동 semantic 모델 호출은 하지 않는다.

**필수 rubric:** 대상/선택 시점·입력 검증·기존 지역 일치·실패 원자성·기존 사용자·출시 영향이 질문이나 명시적 미확정 항목으로 다뤄져야 한다. 받은 답과 모순되지 않는 범위/제외/성공·실패 인수 조건을 작성한다. 답을 얻지 못한 결정을 CONFIRMED나 승인으로 표기하지 않는다. 핵심 질문 없이 임의 설계를 확정하면 FAIL. 미확정 표시를 이유로 단순히 모든 분석을 중단한 경우도 과제 미완료다.

검사: `git diff --check`; 독립 평가자가 대화·응답표·산출물을 대조한다. 질문 개수 최소화 자체는 성공 조건이 아니다. 중요한 질문과 반복/무관한 질문의 비용을 분리한다.

## X. HTTP 요청 관측과 실패·보안 경로 보존

**수정 전 제품:** 9a1f8e84abe46bb831ae77f1602c5bb9a469e3b3 / 출처 #215.

**실행 요청:**

> HTTP 요청을 추적할 request ID와 완료 로그를 추가하세요. 정상 요청뿐 아니라 인증/권한 거절과 예외 경로에서도 동작해야 합니다. 민감한 요청 내용을 기록하지 않고 기존 응답·예외·보안 정책을 유지하도록 구현과 단위·통합 검증을 완료하세요.

**제공 요구:** X-Request-ID 한 개가 첫 글자 ASCII 영숫자, 나머지 ASCII 영숫자·점·밑줄·하이픈으로 전체1~64자일 때만 재사용하고, 누락/빈 값/다중 값/비허용 값이면 UUID를 생성한다. 응답 헤더와 MDC requestId를 연결한다. 완료 로그는 request ID·method·route template(없으면 UNRESOLVED)·status·비음수 duration만 허용한다. 실제 URI/query/body와 credentials는 기록하지 않는다. 외부 수집기·OTel·새 logging backend·새 dependency는 추가하지 않는다.

**수정 허용:**

- `src/main/java/com/dnd/qello/common/web/HttpRequestLoggingFilter.java`
- `src/test/java/com/dnd/qello/common/web/HttpRequestLoggingFilterTest.java`
- `src/integrationTest/java/com/dnd/qello/HttpRequestLoggingSecurityIntegrationTest.java`
- `evaluation-output/X/report.md`

**TEST-PLAN-GH-227-V2-X:**

| 시나리오 | 인수 조건 |
| --- | --- |
| X-001 | 정상/누락/다중/불법 ID 처리와 응답·MDC ID 일치 |
| X-002 | 요청 종료 후 requestId 정리, 다른 MDC 값 보존 및 동시 요청 격리 |
| X-003 | 완료 로그1회, 허용 필드만 출력, 민감 sentinel·실제 URI/query/body 미노출 |
| X-004 | chain 예외를 같은 객체로 재전파하고 logging 실패가 겹쳐도 원래 예외 보존 |
| X-005 | Security 앞에서401/403을 감싸되 기존 보안/도메인 응답 계약 유지 |
| X-006 | 단위 mock뿐 아니라 실제 Spring/MockMvc 보안 체인에서 확인 |

검사:

```bash
./gradlew test --tests 'com.dnd.qello.common.web.HttpRequestLoggingFilterTest'
./gradlew integrationTest --tests 'com.dnd.qello.HttpRequestLoggingSecurityIntegrationTest'
```

참고 변경449fe67/7f38047/165938a는 준비자만 읽는다. 사이에 있던 다른 commit을 일괄 cherry-pick하지 않는다. 제품 snapshot의 SpringBoot/PostGIS 지원을 양쪽 동일하게 제공한다.

## 실제 실행 자료로 전환할 때

각 계약에서 source hash·allowlist·요구/응답·검사·rubric을 manifest로 추출하고 실행자용 입력과 검증자용 oracle를 분리한다. Q 대화 turn 수는 새 세션 수가 아니며 같은 세션의 누적 토큰에 포함한다. F/B/X oracle red/green, R 전후 동등성, A/Q 문서/대화 rubric과 모든 역사적 tooling 호환성을 모델 호출 없이 검증한다. 준비 검증이 실패하면 결과를 숨기지 않고 해당 fixture 실행을 보류한다.

## 독립 검토 후 fixture 주의사항

- A/Q에서는 C1 라우터가 미래 ADR/제품 문서를 가리키더라도 해당 정답 문서를 편의상 복사하지 않는다. 제외 경로·그로 인해 해소되지 않는 참조·대체 제공 사실을 양쪽 manifest에 동일하게 기록한다. 없는 문서는 없는 것으로 취급하고 현재 요청/자료로 분석한다.
- F의 변경 전에는 신규 클래스가 없어 컴파일이 실패할 수 있다. 이를 행동 결함을 재현한 테스트로 표현하지 않는다. 준비자가 정상 참조 구현과 의도적으로 각 조건을 위반한 구현을 별도 검증 사본에서 확인해 oracle가 F-001~005를 실제 구분하는지 검증한다. 그 참조/변형 구현은 실행자에게 제공하지 않는다.

## 오프라인 준비에서 반영한 B 판정 정정

역사적 TransactionBoundaryTest는 annotation 배치 등 특정 구현 형식을 강제하므로 독립 행동 oracle에서 제외한다. 이전 준비 실행의4개 구조 검사는 별도 보조 증거로 보존하고 필수 명령/활성 oracle에는 포함하지 않는다. B의 허용 Java 파일은 application service·기존 단위 테스트·실제 DB 통합 테스트3개다. 동일한 목록 snapshot·rollback·기존 command 계약을 동등하게 만족하는 구현을 허용하며 특정 정답 patch를 강제하지 않는다.
