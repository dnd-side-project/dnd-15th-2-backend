---
id: ADR-0004
title: AWS 인프라 관리 도구로 Terraform을 단독 채택한다
status: proposed
category: INFRASTRUCTURE
date: 2026-08-05
tags: [terraform, aws-cdk, iac, infrastructure]
related: ["#58", "#9"]
---

# ADR-0004. AWS 인프라 관리 도구로 Terraform을 단독 채택한다

## 배경

`agents/infrastructure-orchestrator.md`, `agents/infrastructure-executor.md`와
`harness-infra-design`/`harness-infra-build` 스킬 문서는 지금까지 "Terraform
또는 AWS CDK"를 병기해 왔다. 실제 IaC 구현을 시작하기 전에 도구를 하나로
확정해야 다음이 명확해진다.

- Terraform 실행 에이전트(`terraform-implementer`)와 검증
  에이전트(`terraform-plan-verifier`)가 다룰 언어와 명령
- Terraform State backend 요건(`AGENTS.md` 4.6)의 적용 대상
- `terraform fmt`/`validate`/`plan`, `tflint`, `Checkov`, `Infracost` 등 정적
  검사·비용·정책 도구 파이프라인의 적용 범위

관련 선행 이슈 #9(AWS 최소비용 기준 아키텍처와 IaC 선택)는 산출물 없이
종료되어 참고할 수 있는 이전 결정이 없다. Qello 백엔드는 Java/Spring/Gradle
기반이며 별도 인프라 전담 인력 없이 소수 인원이 설계·구현·검증 역할을
병행한다.

## 고려한 선택지

1. Terraform
2. AWS CDK

## 결정

Qello의 AWS 인프라는 Terraform으로만 관리한다.

AWS CDK, CloudFormation, Pulumi, AWS SDK/CLI를 이용한 선언적 인프라 대체는
사용하지 않는다. 이 결정은 `agents/infrastructure-orchestrator.md`,
`agents/infrastructure-executor.md`, `.claude/skills/harness-infra-design/
SKILL.md`, `.claude/skills/harness-infra-build/SKILL.md`에 이미 반영되어
있다.

## 선택 이유

- **언어 종속성**: CDK는 TypeScript/Python/Java 등 특정 언어 런타임으로
  코드를 합성한 뒤 CloudFormation을 생성한다. 리뷰어는 소스 코드와 합성된
  템플릿을 모두 확인해야 한다. Terraform은 HCL 전용 선언적 문법을 사용해
  사람과 AI 에이전트가 동일한 표현으로 plan을 읽고 검토할 수 있다.
- **상태 관리**: Terraform은 S3 backend와 명시적 locking으로 상태를 관리할
  수 있고, `AGENTS.md` 4.6에 원격 backend 요건(Versioning, 암호화, Public
  Access 차단, State Locking, 최소 권한 IAM)이 이미 정의되어 있다. CDK도
  CloudFormation stack 상태를 가지지만 Terraform plan만큼 세밀한 사전
  diff를 제공하지 않는다.
- **팀 숙련도**: 팀은 Java/Spring 백엔드에 집중되어 있고 인프라 전담
  인력이 없다. CDK는 팀에 익숙한 언어로 작성할 수 있다는 장점이 있지만
  construct·합성 모델을 별도로 학습해야 한다. Terraform의 HCL은 백엔드
  언어와 무관하게 학습 부담이 낮고 팀 전체가 동일한 문법을 공유한다.
- **커뮤니티 모듈 생태계**: Terraform Registry의 공식/커뮤니티 module과
  tflint, Checkov, Conftest, Infracost 같은 정적 분석·정책·비용 도구
  생태계가 넓어 `AGENTS.md` 10절, 11절이 요구하는 fmt·validate·plan·정책
  검사 파이프라인을 그대로 구성할 수 있다.
- **에이전트 검증 가능성**: Terraform plan은 사람이 읽을 수 있는 diff와
  JSON을 생성해 독립 검증 에이전트(`terraform-plan-verifier`)가 apply
  전에 생성·수정·삭제·교체 리소스를 기계적으로 분류할 수 있다. CDK diff도
  가능하지만 언어 런타임 실행(합성) 단계가 추가로 필요해 plan 재현성과
  감사 가능성이 상대적으로 낮다.

## 결과

### 장점

- 사람과 에이전트가 동일한 HCL·plan 표현으로 리뷰한다.
- Terraform Registry 생태계와 기존 정적 분석·비용·정책 도구를 그대로
  활용한다.
- 특정 언어 런타임 없이 Terraform CLI만 설치하면 실행할 수 있다.
- `AGENTS.md`에 이미 정의된 State·Backend·plan 증거 규칙과 정합적이다.

### 단점

- CDK를 선택했을 때 얻을 수 있는 "백엔드와 동일한 언어(Java)로 인프라
  작성" 이점을 포기한다.
- 반복·조건 분기가 많은 복잡한 로직은 범용 프로그래밍 언어인 CDK보다
  HCL의 표현력이 떨어질 수 있다.
- 팀에 Terraform 실무 경험이 없는 경우 초기 학습 비용이 발생한다.(@Byuntil님이 이전에 Terraform 기반으로 인프라 구축 경험이 있어 러닝 커브 비용은 낮출 수 있다고 판단)

## 관련 자료

- GitHub Issue: #58, #9
- 문서: `agents/infrastructure-orchestrator.md`,
  `agents/infrastructure-executor.md`,
  `.claude/skills/harness-infra-design/SKILL.md`,
  `.claude/skills/harness-infra-build/SKILL.md`
