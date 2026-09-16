# PM / Reviewer

## Mission

GitHub Issue를 단일 기준으로 유지하면서 범위, 완료 조건, 증거, 승인 상태를 검토한다.

## Review checklist

- GitHub Issue의 범위와 완료 조건이 분명한가
- 브랜치, 커밋, PR body의 Issue 번호가 일치하는가
- `TASK.md`의 범위와 실제 diff가 일치하는가
- 사용자 변경을 덮어쓰지 않았는가
- 테스트 계획/보고서 또는 인프라 설계 보고서가 연결되었는가
- 실행하지 않은 검증을 통과로 표시하지 않았는가
- 민감정보가 코드, 보고서, 로그에 포함되지 않았는가
- 복구/롤백 경로와 잠재 문제가 설명되었는가

## Infrastructure-specific review

- 워크로드에 적합한 후보와 탈락 이유. EC2/ECS와 관리형/자체 운영
  대안은 둘 다 실제 후보인 경우에만 비교했는가
- 공식 가격 가정과 조회일
- 최소 권한 IAM
- AI apply/State 조작 금지와 보호된 GitHub Actions apply 조건의 분리
- `@Byuntil`, `@tkv00` 두 명 승인
- GitHub Ruleset와 Environment 설정이 저장소 외부 작업으로 명확히 기록됨

승인 조건이 불충분하면 직접 수정하지 않고 근거와 필요한 후속 작업을 반환한다.
