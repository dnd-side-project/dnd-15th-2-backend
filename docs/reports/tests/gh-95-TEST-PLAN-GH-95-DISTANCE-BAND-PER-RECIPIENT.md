# Test Report: TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT

> Created at: 2026-08-10T20:08:00+09:00
> Updated at: 2026-08-10T21:10:00+09:00
> GitHub Issue: #95
> Branch: fix/gh-95-distance-band-per-recipient
> Commit: uncommitted

## 1. Executive summary

- Result: PASS
- Implemented: 수신자별 거리 band 파생, 10km 표시 하한, 조회자별 답변 거리 projection
- Test result: unit 178건, integration 187건, skipped/failures/errors 0건
- Release recommendation: 로컬 검증 기준 PASS. 커밋·push·PR 생성은 별도 승인 단계

## 2. Implementation summary

- `DirectionPostService.SendCommand`에서 호출자 `distanceBand`를 제거했다.
- `DirectionCandidate.distanceMeters`에서 수신자별 band를 서버가 파생한다.
- 10,000m 미만 저장값은 `10km 이내`, 10,000m 이상 저장값은 외부에 노출하지 않는
  내부 표식 `EXACT_DISTANCE`다.
- 수신함 카드는 `distance_m`으로 `10km 이내`/정확 거리를 projection한다.
- 답변 목록은 답변 작성자의 `answer.distance_m`·`distance_band`를 사용하지 않고,
  현재 조회자의 `post_recipient.distance_m`을 사용한다.
- 하한 설정은 양수만 허용하며, 저장 band와 수신함·답변 조회의 표시 문구가 같은
  `FeedDistanceProperties` 정책을 사용한다.
- 거리 정책 내부 오류는 `FeedException`으로 전환해 `GlobalExceptionHandler`의
  공통 도메인 오류 응답 경로를 사용한다.

## 3. Execution results

| Command / suite | Result | Evidence |
| --- | --- | --- |
| `./gradlew test` | PASS — 178 tests, 0 skipped/failures/errors | `build/test-results/test/` |
| `./gradlew integrationTest` | PASS — 187 tests, 0 skipped/failures/errors | `build/test-results/integrationTest/` |
| `./harness test-run --id TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT` | Tests PASS; report overwrite guard로 명령 종료 코드는 2 | Gradle BUILD SUCCESSFUL, 기존 보고서 자동 덮어쓰기 거부 |
| `./harness check` | PASS | secret/JUnit/convention/workflow/label/Husky checks |
| `./harness pr-ready --project-tests` | PASS | local PR readiness checks passed |
| `npm run hooks:validate` | PASS | Husky validation passed |
| `git diff --check` | PASS | whitespace 검사 오류 없음 |

추가 검증으로 `DistanceBandPolicyTest`, `GlobalExceptionHandlerTest`,
`InboxQueryIntegrationTest`, `PostAnswerQueryIntegrationTest`,
`DirectionPostDistanceBandIntegrationTest`를 실행했고 모두 통과했습니다.

최초 Gradle 실행은 저장소 밖 Gradle wrapper cache 잠금 파일 권한으로 실패했으나,
권한 승인 후 동일 명령을 재실행해 컴파일과 테스트를 완료했다. 이는 코드 실패가
아닌 실행 환경 문제다.

## 4. Scenario results

| Scenario ID | Result | Evidence |
| --- | --- | --- |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-001 | PASS | `DistanceBandPolicyTest.storesNearDistanceLabelBelowFloor`, `storesExactDistanceMarkerAtAndAboveFloor` |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-002 | PASS | `DistanceBandPolicyTest.rejectsNegativeDistance` 및 반복 호출의 순수 정책 구현 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-003 | PASS | `SendCommand`에서 band 입력 필드 제거 |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-001 | PASS | `DirectionPostDistanceBandIntegrationTest.derivesDistanceBandPerCandidateDistance` |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-003 | PASS | `InboxQueryIntegrationTest.exposesExactDistanceAtAndAboveFloorOnly` |
| TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-INT-005 | PASS | `PostAnswerQueryIntegrationTest.usesViewerDistanceToQuestionOriginForAnswerDisplay` |
| 기존 방향·수신 슬롯·답변 회귀 | PASS | 전체 integrationTest 187건 |

## 5. Potential issues and residual risks

### Application and privacy

- 답변 표시 거리는 현재 조회자의 질문 원점 거리다. 동일 답변도 조회자별로 다른 값이
  반환될 수 있으며, 10km 미만에서는 `10km 이내`만 반환한다.
- `EXACT_DISTANCE`는 NOT NULL 저장 제약을 위한 내부 표식이다. 조회 projection은
  실제 `distance_m`과 하한으로 결정하므로 이 표식이 응답으로 나가지 않는다.
- 기존 운영 행의 자유 문자열 `distance_band`는 백필하지 않았다. 수신함 조회는
  기존 문자열 대신 거리와 하한으로 `10km 이내`를 재산출한다.

### Database, transaction, concurrency

- 스키마와 Flyway migration은 변경하지 않았다.
- 이번 변경은 기존 send transaction 안에서 band를 파생하므로 recipient 저장과
  receive-state 예약의 원자성 경계는 유지된다.
- 동일 idempotency 재시도 전용 assertion은 추가하지 않았지만, 기존 전체 통합 회귀가
  통과했다. 후속으로 후보 상태 변경 후 snapshot 불변성을 별도 강화할 수 있다.
- band 파생 예외의 partial write rollback 전용 실패 변형은 실행하지 않았다.

### External APIs and recovery

- 외부 API 연동은 없다.
- 기존 운영 데이터의 재분류·백필과 배포 후 데이터 검증은 별도 운영 작업이다.

## 6. Artifacts

- Test plan: `docs/test-plans/gh-95-TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT.md`
- Test source: `src/integrationTest/java/com/dnd/qello/DirectionPostDistanceBandIntegrationTest.java`
- Unit source: `src/test/java/com/dnd/qello/feed/config/DistanceBandPolicyTest.java`
- Gradle XML: `build/test-results/test/`, `build/test-results/integrationTest/`
- CI run: 미실행
- PR: 미생성

## 7. Reviewer checklist

- [x] 보고서에 .env 값이나 비밀정보가 없음
- [x] 테스트 클래스에 `@DisplayName`과 ISO 8601 생성 시각/source scenario가 있음
- [x] 단위·통합 테스트 결과와 미실행 범위를 구분함
- [x] 조회자 기준 답변 거리와 10km 경계를 검증함
- [x] 하한 설정 변경과 전역 feed 오류 경로를 검증함
- [ ] 커밋·push·PR 및 사람 리뷰
