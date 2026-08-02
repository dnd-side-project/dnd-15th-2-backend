# Agent Roles

Qello 하네스는 설계와 실행을 분리한다.

| 역할 | 책임 | 기본 권한 |
| --- | --- | --- |
| Test Orchestrator | 위험 기반 테스트 계획과 시나리오 설계 | 읽기, 계획 문서 작성 |
| Test Executor | 승인된 JUnit 5 테스트 구현·실행·보고 | 테스트 파일과 보고서 |
| Infrastructure Orchestrator | AWS 설계, 대안, 비용, 위험 분석 | 설계 문서와 계획 |
| Infrastructure Executor | 승인된 IaC 구현과 plan | IaC 파일, apply 금지 |
| PM/Reviewer | GitHub Issue 범위, 증거, 승인, 완료 조건 검토 | 읽기와 리뷰 |

실제 모델 식별자는 저장소에 고정하지 않는다. 팀은
`model-profiles.example.yml`을 복사해 `model-profiles.local.yml`을 만들고
도구별 프로필을 연결한다. 로컬 파일은 Git에서 제외된다.

```bash
cp agents/model-profiles.example.yml agents/model-profiles.local.yml
```

오케스트레이터에는 높은 추론 능력과 넉넉한 예산을, 실행 역할에는 비용 효율적인
Claude Sonnet 계열 또는 GPT-5.6 계열 프로필과 중간 예산을 연결하는 것을
권장한다. 제공자가 지원하는 정확한 모델 이름은 팀 계정과 도구 설정에서
확인한다.
