# GitHub Label Policy

참고 저장소의 실제 PR에서 반복적으로 쓰인 `feat`, `fix`, `test`, `docs`,
`infra`, 영역, 우선순위 패턴을 분석한 뒤 이 저장소에는 15개만 남겼다.

Jira가 일정과 우선순위의 기준이므로 `P0` 같은 우선순위 라벨은 GitHub에
복제하지 않는다. 팀명, 배포 대상, 릴리스 버전도 현재 두 명 규모에서는
상시 유지 비용이 더 크므로 제외한다.

## Canonical labels

### Type: 정확히 하나 필수

| Label | 사용 기준 |
| --- | --- |
| `type: feature` | 새 사용자 가치 또는 기능 |
| `type: bug` | 기대 동작과 다른 결함 |
| `type: refactor` | 외부 동작을 유지하는 구조 변경 |
| `type: test` | 테스트 계획, 구현, 품질 검증 |
| `type: docs` | 문서와 설계 산출물 |
| `type: infrastructure` | AWS, IaC, 배포 기반 |
| `type: performance` | 성능 또는 비용 효율 개선 |
| `type: chore` | CI, 도구, 의존성, 일반 유지보수 |

### Area: 필요한 경우에만

| Label | 사용 기준 |
| --- | --- |
| `area: api` | 백엔드 API와 애플리케이션 |
| `area: database` | 스키마, 마이그레이션, 쿼리 |
| `area: security` | 인증, 권한, 비밀정보 |
| `area: operations` | AWS, 배포, 관측, 운영 |

### Status: 자동화 또는 예외 표시

| Label | 사용 기준 |
| --- | --- |
| `status: blocked` | 외부 의존성이나 결정 대기 |
| `status: needs-review` | Draft가 아닌 PR의 검토 대기 |
| `status: needs-triage` | 유형이 없거나 잘못 분류됨 |

## Automatic behavior

Issue Form은 생성 시 기본 `type`을 지정한다. 범용 백엔드 작업 템플릿은
`작업 유형` 응답을 읽어 자동 분류한다. 유형이 없거나 여러 개면
`status: needs-triage`를 붙이고 Label Policy 검사를 실패시킨다.

PR의 `type`은 브랜치 접두사에서 자동 산출한다.

| Branch prefix | PR label |
| --- | --- |
| `feat`, `feature` | `type: feature` |
| `fix` | `type: bug` |
| `refactor` | `type: refactor` |
| `test` | `type: test` |
| `docs` | `type: docs` |
| `infra` | `type: infrastructure` |
| `perf` | `type: performance` |
| `chore`, `ci`, `build` | `type: chore` |

Draft PR에는 `status: needs-review`를 붙이지 않는다. Ready for review가 되면
자동으로 붙고, 다시 Draft가 되면 제거된다.

## Enforcement limits

- `.github/label-catalog.json` 변경을 push하면 라벨을 생성하거나 설명과 색을
  갱신한다. 기존 라벨은 자동 삭제하지 않는다.
- Issue Form과 `blank_issues_enabled: false`로 누락을 줄이지만 GitHub API는
  이슈 생성을 사전에 차단하지 않는다. 자동화가 분류 실패를 표시한다.
- PR 병합을 실제로 막으려면 GitHub Ruleset에서
  `Label Policy / classify-pull-request`를 required status check로 지정해야
  한다. 저장소 파일만으로 Ruleset이 활성화되지는 않는다.
- 외부 fork 코드를 실행하지 않도록 PR 라벨 자동화는 `pull_request_target`
  이벤트에서 checkout 없이 GitHub API만 호출한다.

## Maintenance

라벨 추가는 예외적으로만 수행한다. 변경 시 다음을 함께 수정하고 검증한다.

```bash
python3 scripts/validate-labels.py
./harness check
```

Jira 필드로 관리 가능한 일정, 스프린트, 우선순위는 GitHub 라벨로 다시
만들지 않는다.
