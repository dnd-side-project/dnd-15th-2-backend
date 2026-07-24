# Secret Handling

## 절대 기록하지 않는 값

- `.env` 실제 값
- API token, session, password, private key
- AWS access key
- AWS account ID와 IAM ID
- 서버 주소, 내부 endpoint, 데이터베이스 연결 주소
- GitHub/Jira/Slack 비밀정보

대상:

- 코드와 테스트
- Issue와 PR
- 커밋 메시지
- 테스트/인프라 보고서
- CI 로그와 artifact
- AI 대화와 프롬프트 예시

## 허용되는 것

- 변수 이름
- `<configure-in-github>` 같은 명백한 placeholder
- 공개된 공식 문서 URL
- 자원 유형과 일반 아키텍처 설명

## 저장 위치

| 값 | 위치 |
| --- | --- |
| 로컬 개발 비밀 | Git에서 제외된 로컬 환경 또는 OS keychain |
| GitHub 자동화 | Repository/Environment Secret |
| AWS 배포 인증 | GitHub OIDC 단기 토큰 |
| 모델 선택 | `agents/model-profiles.local.yml` |

## 검사

```bash
python3 scripts/preflight.py
```

검사는 값 자체를 출력하지 않고 파일과 줄, 규칙 이름만 표시한다. 자동 검사는
보조 수단이므로 PR 리뷰에서 새 자격 증명 형식과 민감한 endpoint도 확인한다.

## 노출이 의심될 때

1. 값을 메시지에 다시 복사하지 않는다.
2. 해당 자격 증명을 즉시 폐기/회전한다.
3. GitHub와 공급자 감사 로그를 확인한다.
4. 저장소 이력 정리가 필요하면 별도 보안 절차와 사람 승인을 거친다.
5. 원인과 영향은 실제 값을 제외해 보안 이슈로 기록한다.
