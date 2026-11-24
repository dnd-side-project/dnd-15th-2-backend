# Test Report: TEST-PLAN-GH-379-LOCATION-GATE

> Created at: `2026-11-23T16:56:21+09:00`
> GitHub Issue: `#379`
> Branch: `feat/gh-379-location-collection-gate`
> Commit: `15bae1e2`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 UNIT-001~008, INT-001~005의 소스와 JUnit XML을 직접 대조했다. 단위 206개 XML / 1,553개 실행, 통합 114개 XML / 882개 실행 모두 failure/error/skip 0이다. 후속 fix1에서 헤더를 수정한 뒤 Spotless와 필수 네 검증을 모두 exit 0으로 확인했다.
- Unverified scope: 후속 `pr-ready`의 `./gradlew check`는 성공했지만 테스트 태스크는 up-to-date 결과를 재사용했다. 테스트 본문을 새로 실행한 결과로 취급하지 않는다. 성능 전용 테스트, 실제 운영 배포·복구, 모바일 503 처리는 미실행이다.
- Release recommendation: 로컬 필수 게이트 통과. 최초 헤더 정책 실패는 해결됐다. 아래 잔여 위험과 assertion 한계는 사람 리뷰 대상으로 남으며, 이 결과가 운영 배포나 PR 승인을 의미하지는 않는다.

| Contract field | Value |
| --- | --- |
| status | PASS |
| issue_number | 379 |
| task_id | TASK.md / TEST-PLAN-GH-379-LOCATION-GATE |
| design_id | 해당 없음; 테스트 계획 D1~D6 |
| changed_files | src/integrationTest/java/com/dnd/qello/QelloLocalProfileIntegrationTest.java 및 이 보고서 (2개) |
| executed_checks | XML 직접 집계·시나리오 대조, spotlessApply, harness check → pr-ready --project-tests → hooks:validate → diff --check, 파일 해시 비교 |
| passed_checks | 후속 Spotless와 필수 네 명령 모두 exit 0; 기존 테스트 XML failure/error/skip 0 |
| failed_checks | 현재 없음. 최초 두 하네스 실패는 §5에 이력으로 보존 |
| blocked_checks | 필수 검사 없음. jdtls 미설치로 LSP 진단은 미실행이며 Gradle 검증으로 구분 |
| assumptions | Gradle의 up-to-date 판정을 따랐으며 --rerun-tasks를 추가하지 않았다. 기존 XML을 새 테스트 실행으로 취급하지 않는다. |
| risks | 운영 dev/local 혼합 프로필, 저장 좌표 잔존, 모바일 503 재시도 미확인 |
| required_human_decisions | 이 후속 수정에 추가 결정 없음. 운영 활성 프로필·참조와 C6/C9 후속 작업은 별도 확인이 필요하다. |

현재 HEAD는 `15bae1e2f13672e77d1ed68b7b52a2042289f403`이며, 위 결과의 대상은 그 위의
미커밋 구현·테스트 변경이다. HEAD 자체의 검증 결과로 해석하지 않는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 프로젝트 toolchain 21. 후속 Spotless·검증은 JAVA_HOME을 Homebrew 21.0.13.0.3으로 지정. 기본 shell java는 Temurin 25.0.3; 기존 테스트 JVM의 정확한 patch 버전은 미확인 |
| Spring Boot | build.gradle 선언 3.5.16 |
| Database | PostgreSQL/PostGIS Testcontainers 기반 통합 테스트 |
| Test runner | JUnit 5 / Gradle |
| Node / npm | v26.13.0 / 11.20.0 |

로컬 환경값과 자격 증명을 보고서에 복사하지 않았다. 기존 JUnit XML의 system-out/error
원문은 첨부하지 않고 testsuite 집계 속성과 testcase 결과만 사용했다.

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 기존 ./gradlew test | XML PASS, 이번 세션 재실행 아님 | 1,553; 206 suites; failure/error/skip 0 | suite time 합계 33.864초 | build/test-results/test/TEST-*.xml |
| 기존 ./gradlew integrationTest | XML PASS, 이번 세션 재실행 아님 | 882; 114 suites; failure/error/skip 0 | suite time 합계 149.128초; 전달된 전체 명령 소요 약 4분 32초는 별도 직접 확인 불가 | build/test-results/integrationTest/TEST-*.xml |
| 기존 ./gradlew javaConventionCheck | 전달 기록 PASS; 하위 JUnit XML 확인 | source 3 + architecture 23, failure/error/skip 0 | source 0.758초 + architecture 3.502초 | build/test-results/javaConventionSourceTest/, javaConventionArchitectureTest/; 전체 task 종료 코드는 이번 세션 독립 재확인 안 함 |
| 기존 ./harness test-run --id TEST-PLAN-GH-379-LOCATION-GATE | 전달 기록 exit 0, 이번 세션 재실행 아님 | 위 unit/integration XML 확인 | 미확인 | 기존 보고서 scaffold와 XML; 명령 종료 로그는 직접 확인 안 함 |
| 최초 env -u SSL_CERT_FILE -u SSL_CERT_DIR GIT_MASTER=1 ./harness pr-ready --project-tests | FAIL, exit 1 (해결 전 이력) | JUnit 정책 단계 실패; 당시 Gradle check 미실행 | 미측정 | PR_READY_EXIT=1 |
| npm run hooks:validate | PASS, exit 0 | Husky validation passed | 미측정 | 이번 세션 HOOKS_EXIT=0 |
| GIT_MASTER=1 git diff --check | PASS, exit 0 | 공백 오류 없음 | 미측정 | 이번 세션 DIFF_CHECK_EXIT=0 |
| 최초 env -u SSL_CERT_FILE -u SSL_CERT_DIR GIT_MASTER=1 ./harness check | FAIL, exit 1 (해결 전 이력) | 같은 JUnit 정책 실패 | 미측정 | HARNESS_CHECK_EXIT=1 |
| fix1 ./gradlew spotlessApply | PASS, exit 0 | 헤더 유지; 허용 파일 외 Java 변경 없음 | 6초 | SPOTLESS_EXIT=0 |
| fix1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check | PASS, exit 0 | JUnit 정책 353파일, secret preflight·conventions·workflow·labels·hooks·baseline 검사 통과 | 미측정 | FIX1_HARNESS_EXIT=0 |
| fix1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests | PASS, exit 0 | 내부 ./gradlew check 성공; 14 actionable tasks: 1 executed, 13 up-to-date | Gradle 6초 | FIX1_PR_READY_EXIT=0; Local PR readiness checks passed |
| fix1 npm run hooks:validate | PASS, exit 0 | Husky validation passed | 미측정 | FIX1_HOOKS_EXIT=0 |
| fix1 git diff --check | PASS, exit 0 | 공백 오류 없음 | 미측정 | FIX1_DIFF_EXIT=0 |

fix1은 위 표의 순서대로 실행했다. 공통 환경은 Java 21과 `GIT_MASTER=1`, `GIT_PAGER=cat`이다.
`check` 안의 test/integrationTest 및 convention 테스트는 기존 결과를 재사용했고 XML 시작 시각도
변하지 않았다. 따라서 단위 1,553개·통합 882개는 재집계한 기존 통과 수치이며 신규 실행 수가 아니다.
검증 전후 저장소 파일 1,703개 해시는 모두 같았고 OpenAPI 등 다른 파일도 바뀌지 않았다.

보고서 작성 후 `git diff --check`도 exit 0이다. 미추적 보고서는 별도로 직접 읽어 trailing
whitespace 0개, 마지막 개행 존재, 미작성 템플릿 필드 0개를 확인했다.
`git diff --no-index --check /dev/null <이 보고서>`는 진단 출력 없이 exit 1이었다.
이는 빈 파일과 새 보고서 간 차이가 있는 no-index 결과이며 필수 하네스 실패와 구분한다.

기존 단위 XML의 suite 시작 시각 범위는 `2026-11-23T07:46:30.812Z`~
`2026-11-23T07:47:05.115Z`, 통합은 `2026-11-23T07:51:48.777Z`~
`2026-11-23T07:55:12.215Z`다. suite time 합계는 context 초기화 등을 포함하는 전체 명령
wall-clock과 다르다. 최초 정책 실패 당시 미실행이었던 Gradle check는 fix1에서 정상 완료됐다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS (기존 XML) | LocationCollectionGatePropertiesTest.rejectsUntraceableReferences / acceptsTraceableReferences | 거부 24회 + 허용 3회; null/공백/placeholder/7·8자 경계 |
| UNIT-002 | PASS (기존 XML) | LocationCollectionGateTest.startsWithoutCheckingReferenceWhenDisabled / defaultsToDisabled | 2회 |
| UNIT-003 | PASS (기존 XML) | LocationCollectionGateTest.failsToStartWithoutReferenceOutsideNonProductionProfiles / startsWithoutReferenceInNonProductionProfiles / startsWithTraceableReferenceOutsideNonProductionProfiles | 거부 5회, 예외 프로필 4회, 참조 있는 test 1회; dev+prod도 예외임 |
| UNIT-004 | PASS (기존 XML) | LocationCollectionGateTest.ensureEnabledRejectsWhenDisabled / ensureEnabledPassesWhenEnabled | 2회 |
| UNIT-005 | PASS (기존 XML), 검증 한계 있음 | DirectionPresenceServiceTest.rejectsBeforeAccountLookupWhenLocationCollectionGateIsDisabled | 잘못된 사용자·null command와 유효 command 모두 거부, fake repository.saved 불변. 저장소 호출 횟수를 직접 검증하는 mock 테스트는 아님 |
| UNIT-006 | PASS (기존 XML) | DirectionPostApplicationServiceTest.previewRejectsBeforeAnyLookupWhenLocationCollectionGateIsDisabled / submitRejectsBeforeReplayWhenLocationCollectionGateIsDisabled | 2회; 저장소·postService 무호출, replay stub 및 잘못된 command보다 게이트 선행 |
| UNIT-007 | PASS (기존 XML) | LocationCollectionGateConfigurationFilesTest.locationCollectionDisabledErrorCodeContract | 503 / DIR-APP-008 / APP / direction 코드 중복 없음 |
| UNIT-008 | PASS (기존 XML) | LocationCollectionGateConfigurationFilesTest.defaultConfigurationKeepsGateClosed / nonProductionProfilesOpenGateWithoutReference | 기본 false, dev/local true, 참조 미기재 |
| INT-001 | PASS (기존 XML), 검증 한계 있음 | LocationCollectionGateDisabledIntegrationTest.rejectsPresenceUpdateWithoutTouchingStoredLocation / rejectsPreview / rejectsSubmitAndReplay / authenticationPrecedesGate | 4회; 세 API 503, 같은 키 2회 거부, 무인증 401, 네 테이블 행 수 불변. 세부 한계는 §7 |
| INT-002 | PASS (기존 XML) | LocationCollectionGateBootIntegrationTest.failsToStartWithoutTraceableReference | 빈 값 / ok 2회; 실제 YAML, 명령행 설정 우선순위, root IllegalStateException |
| INT-003 | PASS (기존 XML; integrationTest 대상 한정) | 전체 integrationTest; OpenApiSpecificationIntegrationTest.writesSpecificationArtifact / generationIsDeterministic / documentsActiveUserPresenceContract / documentsDirectionPreviewContract / documentsDirectionPostSubmissionContract | 882개 통과. OpenAPI 클래스 13개 통과 및 실제 diff의 세 API 503 확인. performance 태그는 제외 |
| INT-004 | PASS (기존 XML) | DevProfileBootIntegrationTest.devProfileOpensLocationCollectionGate | dev 게이트 true, 인증된 presence PUT 200 및 저장 행 확인 |
| INT-005 | PASS (기존 XML + fix1 정책 통과) | QelloLocalProfileIntegrationTest.localProfileOpensLocationCollectionGate / contextLoadsWithLocalProfile | local 게이트 true·DB 연결 통과. 헤더를 package 앞으로 옮긴 뒤 JUnit 정책도 통과 |

축약 ID에는 `TEST-PLAN-GH-379-LOCATION-GATE-` 접두사를 붙인다. 단위 gate 클래스는
`src/test/java/com/dnd/qello/direction/gate/`, 서비스 테스트는 `direction/service/`,
통합 클래스는 `src/integrationTest/java/com/dnd/qello/` 아래에 있다.

## 5. Failures and diagnostics

### 해결된 최초 필수 게이트 실패: 테스트 메타데이터 형식

- 실패 명령: `./harness pr-ready --project-tests`, `./harness check` (위 표의 환경 접두사 적용).
- 두 명령 모두 secret preflight를 통과한 다음 JUnit policy validation에서 exit 1.
- 안전한 오류 요약: `src/integrationTest/java/com/dnd/qello/QelloLocalProfileIntegrationTest.java: missing Source scenario identifier`.
- 최초 재현 조건: 미커밋 파일에서 `Source scenario:`와 시나리오 ID가 서로 다른 줄에 있었다.
  `scripts/validate-java-tests.py`의 `SCENARIO_RE`는 같은 줄의 `Source scenario: <ID>`를 요구한다.
  실제 git diff에서 기존 한 줄 헤더가 여러 줄로 재포맷된 것을 확인했다.
- 분류: 애플리케이션 런타임 실패나 Docker/DB 장애가 아닌 테스트 소스 메타데이터 정책 위반.
  현재 변경에서 발생한 문제이며 pre-existing 환경 오류로 분류하지 않는다.
- 최초 미검증 범위: pr-ready 내부 Gradle check 및 정책 실패 뒤의 검사들. fix1에서 해소됐다.
- fix1 수정: class javadoc 위치의 헤더를 package 앞 독립 블록으로 옮기고 한 줄 한 항목으로 복원했다.
  생성·확장 시각과 기존 시나리오를 유지했고 #379 INT-005에는 지정된 `Source scenario:`를 썼다.
  테스트 로직·import·어노테이션은 수정하지 않았다.
- fix1 재현 방지 확인: `./gradlew spotlessApply` exit 0 이후에도 헤더가 다시 합쳐지지 않았다.
  수정 전후 1,138개 Java 파일 해시 비교에서 이 테스트 파일 하나만 달랐다.
- fix1 결과: `harness check`, `pr-ready --project-tests`, `hooks:validate`, `diff --check` 순서로
  모두 exit 0. 초기 실패를 숨기거나 검사기를 완화하지 않았다.
- 도구 한계: 편집 도구의 LSP 진단은 `jdtls` 미설치로 실행하지 못했다.
  허용 파일 범위 밖의 설치·설정 변경 없이 Gradle 및 저장소 필수 검사 결과를 확인했다.

### 계획 및 실행 과정과의 차이

1. 계획 §3·§9의 “서비스를 직접 생성하는 통합 테스트 6개”는 실제로 Bean 주입이다.
   `new ...SubmitCommand`를 서비스 생성으로 잘못 센 계획 오류이며 여섯 파일은 변경하지 않았다.
   일반 회귀 네 클래스의 XML은 통과했지만, 두 performance 클래스는 기본 integrationTest
   대상이 아니다(§7). 여섯 클래스 모두 실행됐다고 해석하지 않는다.
2. UNIT-007은 계획의 `direction/error/**` 대신
   `LocationCollectionGateConfigurationFilesTest`에서 오류 코드 계약을 검증한다.
3. 전달 지시에는 2026-11-23 @Byuntil의 범위 확장 승인이 기록돼 있다. 실제 diff에서도
   TX-001·CTOR-001 대응으로 두 서비스의 클래스 `@Transactional(readOnly = true)`,
   `DirectionPostApplicationService`의 final 제거와 `@RequiredArgsConstructor`,
   `submit`의 `NOT_SUPPORTED`, baseline `JAVA-CONV-0022` 삭제, 두 서비스 전체 탭 포맷을 확인했다.
   승인 대화 자체를 이 세션에서 별도 조회하지는 않았다. 이 실행자가 추가한 변경은 아니다.
4. INT-002는 초기 `SpringApplicationBuilder.properties()` 값이 YAML fixture보다 낮은
   우선순위여서 기대와 달리 기동했다는 전달 기록이 있다. 현재 소스는 명령행 인자를 쓰며
   해당 두 XML case는 통과했다. 초기 실패는 테스트 구성 문제로, 게이트 결함으로 분류하지 않는다.

## 6. Potential issues

### Application code

- D4는 활성 프로필 중 dev/local이 하나라도 있으면 참조 검사를 생략한다.
  `dev,prod` 허용은 UNIT-003이 확인한 승인 계약이며 구현 결함으로 단정하지 않는다.
  다만 운영 배포가 혼합 프로필을 사용하면 참조 검사가 우회되므로 운영 활성 프로필의 별도 검토가 필요하다.
  이 보고서는 운영 배포 설정을 검증하지 않았다. 추적: #379 및 점검표 C7(후속 Issue 번호 미제공).
- 게이트는 세 서비스 메서드 첫 줄에서 거부한다. 인증 401 및 web validation 400보다 먼저
  거부하는 전역 요청 필터가 아니며, 참조 문자열의 존재·형식만 검사하고 실제 신고 수리 사실을 확인하지 않는다.

### Infrastructure and resource limits

- 실행 중 토글을 지원하지 않아 차단 변경에는 재배포가 필요하다. 다중 인스턴스 롤링 배포 중에는
  이전 설정 인스턴스가 남는 시간을 별도로 통제해야 한다. 배포·부하·connection pool 검증은 미실행이다.
- preview는 이제 facade 진입부터 read-only 트랜잭션을 열므로 기존보다 DB 트랜잭션 보유 구간이
  길어진다. 게이트가 닫혀도 transaction interceptor는 메서드 본문보다 먼저 실행될 수 있다.
  DB 무호출 mock 검증과 DB 연결 획득·기동 여부는 다른 보장이다. 추적: #379.

### Database and migrations

- 이번 변경에는 DB migration이 없다. INT-001은 네 테이블 행 수 및 기존 presence의 위도·location_at
  불변을 확인하지만 저장 좌표를 삭제하는 기능은 아니다.
- 기존 dev 좌표와 post_audience 원점은 그대로 남는다. worker도 차단 대상이 아니므로
  이미 만들어진 작업의 처리를 멈추거나 기존 데이터 사용을 완전히 금지하는 장치로 해석하면 안 된다.
  파기·대사는 점검표 C6 범위이며 후속 Issue 번호는 전달되지 않았다.

### Concurrency and idempotency

- 설정은 기동 시 고정돼 같은 인스턴스 안에서 런타임 토글 경쟁은 없다.
  같은 멱등키 요청을 두 번 보내도 닫힌 게이트는 replay 이전에 거부한다.
- INT-001의 두 번 요청은 순차 요청이며 동시 요청 부하 증거가 아니다.
  기존 성공 요청의 저장 결과 반환 차단은 UNIT-006의 replay stub과 무호출 검증이 보완한다.
  다중 인스턴스 설정 혼재 중의 멱등 처리 검증은 미실행이다. 추적: #379.

### Transactions and event ordering

- `preview`는 클래스 read-only 트랜잭션에 들어가고 `postService.previewAll`도 그 경계에서 실행된다.
  조회 회귀 XML은 통과했지만 격리 수준이나 read-only 상태를 별도 직접 assertion한 신규 테스트는 없다.
- `submit`은 `NOT_SUPPORTED`로 호출자 트랜잭션을 중단하고,
  `replayIfExists`의 쓰기 트랜잭션과 `send` 내부 TransactionTemplate이 각자 경계를 유지한다.
  기존 facade에 바깥 트랜잭션이 없던 경로는 보존되지만, 외부 호출자가 트랜잭션을 갖고 있으면
  과거와 전파 의미가 다를 수 있다. 성공 발송 회귀는 XML로 확인했으며 해당 호출자 조합은 미검증이다.
- presence `update`는 method-level write 트랜잭션이다. INT-001의 거부 후 outbox 행 수 불변은
  신규 작업이 기록되지 않음을 검증하며, 기존 outbox의 worker 처리를 차단한다는 의미는 아니다.
  추적: #379, ADR-0002.

### External APIs

- 거부된 발송은 outbox를 추가하지 않으므로 그 요청에서 신규 비동기 작업을 만들지 않는다.
  실제 FCM·moderation에 대한 차단 네트워크 관측은 이번 게이트 검증 범위가 아니다.
- 모바일이 503 / DIR-APP-008을 무제한 재시도하는지, 사용자에게 기능 비활성 상태를
  어떻게 표시하는지는 미확인이다. 모바일 연동 검증이 필요하며 후속 Issue 번호는 미제공이다.
  추적 출발점: #379.

### Failure recovery and reconciliation

- 차단은 `enabled: false`, 재개는 신고 수리 참조와 `enabled: true`로 설정한 뒤 재배포하는
  문서 절차를 확인했다. 실제 롤백·재배포·장애 복구는 미실행이다.
- 참조 없이 켜면 기동 실패한다. 유효 설정으로 다시 배포하기 전까지 가용성 저하가 이어질 수 있으므로
  프로필·참조의 배포 전 검토가 필요하다.
- 차단 후에도 기존 좌표·기존 작업은 남는다. C6 데이터 파기와 C9 동의 이력은 별도이며,
  이 게이트의 테스트 통과로 둘의 완료를 주장할 수 없다. 추적: #379, 점검표 C6·C9.

## 7. Regression and residual risk

- 기능 XML은 모두 통과했고 fix1에서 필수 게이트 실패도 해결돼 최종 status는 PASS다.
  테스트 본문은 up-to-date 결과 재사용이며 새 실행을 강제하지 않았다.
- 계획에서 지목한 미수정 일반 회귀 클래스는 `QuestionRecommendationIntegrationTest`(2),
  `ActiveUserPresenceConfigurationIntegrationTest`(1), `DirectionMatchingVerticalFlowIntegrationTest`(5),
  `DirectionMatchingVerticalFlowRecoveryIntegrationTest`(3)이며 모두 XML failure/error/skip 0이다.
- `DirectionMatchingPerformanceIntegrationTest`, `DirectionMatchingE3PerformanceIntegrationTest`는
  `@Tag("performance")`여서 `integrationTest`가 제외한다. `./gradlew performanceTest`는
  이번 세션 미실행이며 기본 완료 게이트와 구분한다. 운영 규모 성능 통과를 주장하지 않는다.
- INT-001은 계획의 활성 질문·구획 fixture를 만들지 않고 유효 형식의 ID만 요청에 넣는다.
  게이트 선행 거부는 검증하지만 존재하는 질문·구획 기반의 성공 발송 준비 상태를 검증하지는 않는다.
- INT-001의 응답 비노출 assertion은 presence PUT의 합성 위도·경도에 한정된다.
  정확도와 preview/posts 응답 본문의 민감값 부재를 각각 직접 assertion하지 않는다.
  기존 presence의 경도·정확도·전체 행 내용을 비교하지도 않는다. UNIT-005 역시 계정 저장소
  무호출을 직접 계수하지 않는다. 승인 계획보다 좁은 assertion 범위이며 후속 검토 대상으로 남긴다.
- OpenAPI 세 API의 503은 실제 산출물 diff에서 확인했고 기존 생성·결정성 XML이 통과했다.
  이번 세션은 이를 다시 생성하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-379-TEST-PLAN-GH-379-LOCATION-GATE.md` (APPROVED; TASK에 승인 증거 기록).
- Test evidence: `build/test-results/test/`, `build/test-results/integrationTest/`,
  `build/test-results/javaConventionSourceTest/`, `build/test-results/javaConventionArchitectureTest/`.
- API artifact: `docs/api/openapi.json` (기존 생성 산출물).
- CI run: 미조회; 이 보고서는 로컬 증거만 다룬다.
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`.
- PR: 생성·조회·코멘트하지 않음.
- 작성 변경: `QelloLocalProfileIntegrationTest.java`의 헤더와 이 보고서만.
  기존 production·다른 test·TASK·계획·설정 변경은 보존했다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨: #379 및 C6/C7/C9 작업명은 기록했으나 별도 후속 Issue 번호는 미제공
- [ ] 실행 결과와 PR 설명이 일치함: PR 미생성, 사람 리뷰 미실행
