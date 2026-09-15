# 작업별 문서 선택

현재 요청·Issue·TASK의 작업 범위에 해당하는 행만 읽는다. 관련 문서가 없으면 없는 것으로 기록하고 제공된 요구에서 확인 가능한 범위와 미확정값을 구분한다. 문서의 proposed 상태를 승인으로 취급하지 않는다.

| 작업 근거/질문 | 읽을 원본 | 적용 주의 |
| --- | --- | --- |
| Java 구현/버그/리팩터링/코드 적합성 | docs/harness/JAVA_CONVENTIONS.md | formatter 실행은 허용 파일 범위 내; 전역 정리 지시 아님 |
| DB 소유·마이그레이션 | docs/adr/0001-database-schema-ownership.md | 운영 변경 승인 대체 불가 |
| persistence·transaction·repository 경계 | docs/adr/0002-jpa-jdbc-boundary.md | 코드/현재 Issue와 함께 판단 |
| 오류 응답 | docs/adr/0003-global-exception-handling.md | API 동작 변경은 승인 범위 확인 |
| 성공 응답 | docs/adr/0005-api-success-response-contract.md | 문서 작업과 동작 변경 구분 |
| 인증 요구/설계 | docs/adr/0006-split-operator-and-device-authentication.md, docs/product/AUTH_DESIGN.md | ADR 상태가 proposed이므로 최신 승인 근거 확인 |
| 온보딩 국가 | docs/product/ONBOARDING_COUNTRY_DESIGN.md | 현재 Issue의 범위 우선 확인 |
| 알림함·푸시 | docs/product/NOTIFICATION_INBOX_DESIGN.md, docs/adr/0008-adopt-fcm-push-delivery-pipeline.md | 관련 기능일 때만 선택 |
| 아직 요구가 모호함 | 현재 요청·Issue 또는 Project draft, 관련 제품 문서의 필요한 절 | 문서 전체 열람이나 Java 규칙 로딩을 일괄 요구하지 않음 |

## 요구사항 분석 시 확인

요구사항을 구체화하는 작업에만 적용한다. 중요한 항목은 `CONFIRMED`(사용자 답변·승인 문서의 근거), `ASSUMED`(제안과 가정), `UNKNOWN`(미확정)으로 구분한다. 기존 코드의 동작 관찰은 별도 근거이며 새 요구사항의 승인으로 취급하지 않는다.

실패 시 결과·부분 저장 또는 중간 상태·민감정보의 입력/출력/로그 처리가 범위에 영향을 주는지 확인한다. 필요한 결정은 묶어 질문하고, 답이 없으면 `UNKNOWN`과 후속 결정 사항으로 남긴다. 이미 확인된 답을 반복 질문하지 않는다. 완료 조건에 가정이나 미확정 결정을 확정 사실처럼 넣지 않는다.

## 탐색과 검증 진행

서로 독립적인 파일 위치 확인·검색·읽기는 한 번에 묶고, 다음 판단에 필요한 파일과 구간만 출력한다. 읽은 근거와 위치를 재사용하며 파일 변경·새 요구·근거 부족이 생기면 해당 범위를 다시 확인한다.

진행 중인 검사에는 도구의 완료 대기나 충분한 대기 간격을 사용하고, 새 결과 없이 같은 출력을 반복 조회하지 않는다. 사용자에게 필요한 진행 상황은 계속 알린다. 이 안내는 필수 검사나 승인 절차를 생략하거나 실패를 숨기는 근거가 아니다.
