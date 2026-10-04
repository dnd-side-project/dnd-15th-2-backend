# GitHub Issue #308 Task Contract

> Generated at: `2026-10-05T02:43:56+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `배포 job의 이미지 주소 출력 누락 수정`
- GitHub Issue: `#308`
- Branch: `fix/gh-308-deploy-sha-output`
- Base branch: `main`

## Objective

- TASK-ID: GH-308-DEPLOY-SHA-OUTPUT
- 실제 실패 로그: 실행 #37217587602의 build 종료에서 image_ref output을 secret으로 간주해 생략했다.
- 사용자 요청(2026-10-05): 실제 테스트 서버 배포와 헬스체크 성공까지 진행한다.

## Scope

- `.github/workflows/deploy-test-server.yml`: image_ref output 제거, image_sha만 전달,
  deploy에서 SHA 검증 후 ECR 주소와 조합. 계정 마스킹과 기존 배포 안전장치를 유지한다.
- `TASK.md`: 계약과 검증 증거.
- 기존 테스트 서버 배포 workflow dispatch 및 실행·SSM·헬스체크 결과 확인.

## Explicit exclusions

- 앱 코드, Java 테스트, Terraform, IAM, DB, 운영 환경 변경과 승인 우회.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| workflow 수정 | executor | 독립 verifier의 job 출력·mock 검증 |
| 작업 계약·원격 배포 확인 | root | 현재 commit의 승인·CI 확인 |

## Existing user-owned changes

- 작업 시작 시 변경 없음. origin/main의 be85a3e에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
actionlint .github/workflows/deploy-test-server.yml
git diff --check
```

## Completion criteria

- [x] 전체 이미지 주소를 job output으로 전달하지 않는다.
- [x] job 간 SHA 전달·재구성과 잘못된 SHA 거부를 독립 mock으로 검증한다.
- [ ] 필수 전체 검사와 원격 CI를 확인한다.
- [ ] 현재 head의 사람 승인 후 PR을 병합한다.
- [ ] 최신 main workflow를 실행하고 build 및 deploy·SSM·헬스체크 성공을 확인한다.

## 로컬 검증 (2026-10-05)

- status: PASS (로컬 구현·검증); 실제 배포는 아래 완료 조건으로 별도 확인한다.
- issue_number: 308
- task_id: GH-308-DEPLOY-SHA-OUTPUT
- design_id: N/A
- changed_files: `.github/workflows/deploy-test-server.yml`, `TASK.md`
- executed_checks / passed_checks: `./harness check`, `./harness pr-ready --project-tests`,
  `npm run hooks:validate`, actionlint, workflow run 블록 shellcheck, `git diff --check`.
  Gradle check는 exit 0이며 단위·통합 테스트는 기존 성공 결과를 UP-TO-DATE로 재사용했다.
- 독립 mock: `/private/tmp/gh308_mock_verify.py` PASS.
  실제 build output을 deploy에 전달하고, 계정 마스킹에 의해 주소 출력이 제거되는 조건을
  모의했다. SHA만 전달되어 정확한 SSM APP_IMAGE로 재구성되며 빈·잘못된 SHA는 SSM 전 거부한다.
  기존 이미지 재사용·빌드·롤백·오류 전파·환경값 및 파일 권한 보존도 확인했다.
- failed_checks: 없음.
- blocked_checks: 로컬 검사에는 없음. 원격 CI·사람 승인·실제 배포는 진행 중이다.
- assumptions: 기존 승인된 ECR·SSM 역할과 서버 compose의 APP_IMAGE 계약을 사용한다.
- risks: 로컬 검증은 실제 서버 헬스체크 성공을 대신하지 않는다.
- required_human_decisions: PR의 현재 head 승인. 승인·CI 게이트를 우회하지 않는다.
