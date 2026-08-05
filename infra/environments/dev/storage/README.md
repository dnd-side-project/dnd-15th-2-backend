# Dev Image Storage

게시물 이미지 업로드 테스트용 S3 버킷과 그 부속 리소스를 만든다.
Infrastructure Design Report `docs/reports/infrastructure/gh-63-D-1.md`
(DESIGN-ID `D-1`, GitHub Issue #63)를 따른다.

## 리소스

| 리소스 | 설명 |
| --- | --- |
| 이미지 버킷 | private, versioning, SSE-KMS(전용 CMK), 180일 만료, 접근 로그 |
| 접근 로그 버킷 | 위 버킷의 S3 서버 접근 로그 대상. 90일 만료 |
| KMS Key | 이미지 버킷 전용 Customer-managed Key, 자동 연간 회전 |
| `${project_prefix}-s3-tester` Role | 팀원이 assume해서 버킷을 테스트하는 역할 |
| `${project_prefix}-s3-tester` User | 팀원 콘솔 로그인용 신원. Access Key 없음 |
| `${project_prefix}-s3-testers` Group | 위 User에게 MFA 자기관리와 Role assume 권한을 주는 그룹 |

## 팀원 온보딩 절차

Terraform은 신원만 만들고 비밀번호는 만들지 않는다. 비밀번호를 Terraform으로
만들면 State에 남기 때문이다(AGENTS.md 4.6).

계정 소유자가 할 일:

1. 콘솔에서 `${project_prefix}-s3-tester` User에 **비밀번호를 설정**하고
   "다음 로그인 시 비밀번호 변경"을 켠다.
2. 초기 비밀번호를 안전한 경로로 팀원에게 전달한다. 이 저장소, Issue, PR,
   로그에는 기록하지 않는다.

팀원이 할 일:

1. 콘솔에 로그인하고 비밀번호를 변경한다.
2. **MFA 디바이스를 등록한다.** 등록 전에는 Role을 assume할 수 없으므로
   사실상 아무 권한이 없다.
3. 콘솔 우측 상단에서 `${project_prefix}-s3-tester` Role로 switch role
   하거나, CLI에서 assume한다.

```bash
aws sts assume-role \
  --role-arn "<dev_s3_tester_role_arn output 값>" \
  --role-session-name s3-test \
  --serial-number "<본인 MFA 디바이스 ARN>" \
  --token-code "<MFA 코드>"
```

## 권한 경계

Role이 할 수 있는 일은 이미지 버킷의 객체 읽기/쓰기/삭제와 그 버킷의 설정
**조회**까지다. 암호화, Public Access Block, 수명주기 같은 보안 설정은
콘솔에서 바꿀 수 없다. 이 설정들을 사람이 바꾸면 Terraform 상태와 어긋나기
때문이며, 변경이 필요하면 이 디렉터리의 코드를 고쳐 PR로 처리한다.

다른 버킷과 다른 KMS Key에는 접근할 수 없다. 계정의 버킷 **목록** 조회는
콘솔 화면이 요구해 허용되어 있으나, 목록 외의 내용은 보이지 않는다.

## 변수

실제 버킷 이름은 전역 고유값이라 기본값이 없다. `terraform.tfvars.example`을
복사해 사용하고 실제 값은 커밋하지 않는다.
