# GitHub Issue #306 Task Contract

> Generated at: `2026-10-04T22:46:26+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `테스트 서버 배포의 ECR immutable 태그 충돌 수정`
- GitHub Issue: `#306`
- Branch: `fix/gh-306-ecr-immutable-sha-deploy`
- Base branch: `main`

## Objective

- TASK-ID: GH-306-ECR-IMMUTABLE-SHA-DEPLOY
- SHA push 후 immutable latest 덮어쓰기에서 실패한 배포를 SHA 직접 배포로 수정한다.
- 사용자 요청(2026-10-04): AWS CI 오류 원인 확인 및 수정. 실제 배포는 제외한다.
- 후속 사용자 요청: 수정 내용을 반영한다. 커밋·push·PR 생성으로 연결하며,
  push 훅에서 필수 전체 검사를 다시 실행한다. 테스트 실패 우회와 자동 병합은 포함하지 않는다.

## Scope

- `.github/workflows/deploy-test-server.yml`: SHA 직접 배포, BatchGetImage 기반 기존 이미지 재사용,
  수동 image_sha 입력(기존 이미지만), ECR 인증 갱신, 배포 직렬화, APP_IMAGE 안전 갱신.
- `README.md`: SHA 배포 및 롤백 안내.
- `TASK.md`: 계약과 로컬 검증 증거.

## Explicit exclusions

- 앱 코드, Java 테스트, Terraform, IAM, AWS 리소스 및 DB 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| workflow와 README | executor | 독립 verifier의 실제 diff 및 모의 실행 검증 |
| TASK 계약과 통합 검증 | root orchestrator | 독립 검증 증거 확인 |

## Existing user-owned changes

- 작업 시작 시 변경 없음. 기존 #284 브랜치를 보존하고 최신 origin/main에서 별도 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
python3 scripts/validate-workflows.py
git diff --check
```

## Completion criteria

- [x] latest push 제거 및 선택한 SHA를 서버 APP_IMAGE에 반영하는 명령을 모의 검증했다.
- [x] 동일 SHA 재실행은 기존 이미지를 재사용한다.
- [x] ImageNotFound 외 오류는 실패하고, 수동 SHA의 없는 이미지는 빌드하지 않는다.
- [x] 같은 서버 배포를 직렬화하고 ECR 인증을 갱신한다.
- [x] APP_IMAGE 외 환경값을 보존하고 비밀값을 출력하지 않는다(모의 파일 검증).
- [x] 로컬 모의 AWS/Docker/SSM 시나리오와 필수 검사 결과를 기록한다.
- 실제 AWS end-to-end 배포는 실행하지 않으며 로컬 검증과 구분한다.

## Verification report (2026-10-04)

- status: FAIL — workflow 수정의 독립 모의 검증은 PASS. 필수 전체 검사는
  PostGIS 컨테이너 시작 시간 초과 2건으로 실패해 전체 게이트는 통과하지 못했다.
- issue_number: 306
- task_id: GH-306-ECR-IMMUTABLE-SHA-DEPLOY
- design_id: N/A (Terraform 및 AWS 리소스 변경 없음)
- changed_files: `.github/workflows/deploy-test-server.yml`, `README.md`, `TASK.md`
- executed_checks / passed_checks:
  - `./harness check`: PASS (모든 정책 검사).
  - `npm run hooks:validate`: PASS.
  - `python3 scripts/validate-workflows.py .github/workflows/deploy-test-server.yml`: PASS.
  - `actionlint .github/workflows/deploy-test-server.yml`: PASS.
  - workflow의 두 run 블록 `shellcheck -s bash -`: PASS.
  - `python3 /private/tmp/gh306_mock_verify.py`: 독립 verifier PASS.
    자동 SHA 존재/부재, 수동 SHA 존재/부재/잘못된 입력, AWS 조회·로그인 오류,
    malformed/혼합/불일치 응답, APP_IMAGE만 변경·기존 파일 권한 보존,
    고정 concurrency, health/SSM/compose 실패 전파를 확인했다.
    scratch 파일은 커밋 대상이 아니며 fixture와 stub만 사용했다.
  - `git diff --check`: PASS.
  - 전체 검사의 단위 테스트: 174개 클래스, 1,098개 테스트 PASS.
- failed_checks:
  - `./harness pr-ready --project-tests`: exit 1, `integrationTest` 실패.
    98개 클래스의 750개 테스트 중 2개 initializationError.
    `AnswerModerationRetryIntegrationTest`, `MediaAssetStorageIntegrationTest` 모두
    `postgis/postgis:16-3.5-alpine` 컨테이너의 database ready 로그 대기 시간 초과.
    테스트 메서드 실행 전 ContainerLaunchException이며 Java 소스·테스트 변경은 없다.
    재실행 조건: Docker/PostGIS 시작 상태를 확인한 뒤 동일 전체 검증을 실행한다.
- blocked_checks: 없음. 실제 AWS 배포 검증은 명시적 제외 범위이며 미실행이다.
- assumptions: ECR/SSM 기존 IAM 선언과 서버 compose의 APP_IMAGE 계약을 사용한다.
  실제 적용된 IAM과 EC2 상태는 이 작업에서 확인하지 않았다.
- risks: AWS end-to-end 배포 미검증; 전체 테스트 게이트 실패;
  immutable SHA 재사용은 최초 등록 이미지를 유지한다. 헬스체크 실패 시 자동 롤백하지 않는다.
- required_human_decisions: 추가 로컬 수정 승인 불필요. 병합 전 전체 검증 실패를 해소해야 한다.
  실제 서버 배포와 병합은 이번 작업에서 실행하지 않았다.

## 반영 및 재검증 (2026-10-05)

- status: PASS (로컬 필수 검사). 위 최초 실패 기록은 이력으로 보존한다.
- 커밋: `f16833a` — SHA 이미지 배포 수정. 최신 `origin/main` 동기화 후 작업 브랜치를 push했다.
- `git push -u origin fix/gh-306-ecr-immutable-sha-deploy`의 pre-push 훅에서
  `./harness pr-ready --project-tests`를 재실행했고 exit 0으로 통과했다.
  Gradle `check`는 `BUILD SUCCESSFUL`이며 실행 시간은 21분 56초다.
- 단위 1,098개(기존 성공 결과를 Gradle이 UP-TO-DATE로 재사용), 통합 759개(이번 실행)를
  확인했다. 실패·오류·skip은 모두 0이다.
  최초 실행의 컨테이너 시작 오류로 실행되지 못한 테스트도 이번 실행에서 수행됐다.
- `./harness check`, `npm run hooks:validate`, actionlint, `git diff --check`도 통과했다.
- failed_checks: 없음 (최신 검사 기준).
- blocked_checks: 없음 (로컬 필수 검사 기준).
- 실제 AWS 배포와 병합은 미실행이며, 원격 CI 결과와 로컬 통과를 구분한다.
- required_human_decisions: PR 검토·병합. 승인 절차를 우회하지 않는다.
