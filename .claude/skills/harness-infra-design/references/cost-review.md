---
name: infra-cost-review
description: AWS 설계의 월 비용 가정, 비용 증가 요인과 대안별 비용 차이를 독립적으로 검토한다.
user-invocable: false
---

# Infrastructure Cost Review

비용 숫자를 추측하지 않는다.

## 필수 가정

- Region
- 한 달 사용 시간
- Compute 사양
- 최소 및 평균 실행 개수
- 저장 용량
- 데이터 증가율
- IOPS 또는 요청 수
- 외부 데이터 전송량
- NAT 처리량
- 로그 수집량
- 백업 보관량
- DNS와 Load Balancer 사용량

## 비용 분류

- 고정 비용
- 사용량 기반 비용
- 트래픽 기반 비용
- 저장량 기반 비용
- 관측성 비용
- 백업 비용
- 예상되지 않은 증가 요인

## 필수 비교

- 선택안의 월 추정
- 가장 저렴한 대안
- 운영 부담이 가장 낮은 대안
- 트래픽 2배 시 추정
- 저장량 3배 시 추정
- 로그량 3배 시 추정

## 출력

```text
status:
currency:
region:
pricing_date:
assumptions:
fixed_monthly_cost:
usage_based_cost:
estimated_total:
alternative_costs:
cost_growth_risks:
missing_pricing_inputs:
```
공식 가격을 확인할 수 없으면 숫자를 생성하지 않고 BLOCKED로 반환한다.

계산 결과가 실제 청구액이 아닌 추정치임을 명시한다.