# Unit Test Path Instructions

[root `AGENTS.md`](../../AGENTS.md#qello-repository-agent-contract)의 안전·승인 조건을 유지한다.
이 경로는 `src/test`의 단위 테스트를 소유한다. 기본 검증은 저장소 root에서 `./gradlew test`로
실행하며, 명령 실행 위치를 세션 시작 cwd나 instruction chain 변경으로 간주하지 않는다.

테스트 작업 전에 [`harness-test-plan`](../../.agents/skills/harness-test-plan/SKILL.md#test-plan-orchestration)과
[테스트 정책](../../.agents/skills/harness-test-plan/references/test-policy.md#테스트-정책)을 읽는다.
읽기·분석과 계획 작성은 승인 없이 허용된 범위에서 수행할 수 있다. 테스트 작성·실행 전에는
계획의 사람 승인을 확인하고 [`harness-test-run`](../../.agents/skills/harness-test-run/SKILL.md#test-execution)과
[보고 계약](../../.agents/skills/harness-test-run/references/reporting.md#테스트-보고-계약)을 읽어 실행한다.
통합 경계가 필요한 시나리오는 `src/integrationTest`로 분리한다. 검증 결과와 사람의 승인을 구분한다.
