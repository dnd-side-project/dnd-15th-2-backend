# Failure Recovery

## 설치 점검 실패

`hd`에서 누락된 도구만 확인한다. 설치 스크립트를 반복 실행하기 전에 Homebrew,
PATH, Java 버전을 점검한다. 인증 오류는 `gh auth status`, `claude doctor`,
Codex 로그인 화면에서 해결하며 인증 출력을 공유하지 않는다.

## 브랜치 생성 실패

- 작업 트리가 dirty인지 확인한다.
- 사용자 변경을 stash/reset하지 않는다.
- GitHub Issue가 열려 있고 현재 저장소에 존재하는지 확인한다.
- 기존 브랜치가 있으면 새 브랜치를 덮어쓰지 않고 상태를 검토한다.

## 하네스 검사 실패

```bash
hc
```

첫 번째 실패부터 수정한다. preflight는 매치된 값을 숨기므로 해당 파일을
직접 안전하게 검토한다. 테스트 메타데이터 실패는 헤더와 `@DisplayName`을
보완한다.

## Husky Hook이 실행되지 않음

```bash
npm ci
npm run hooks:validate
git config --get core.hooksPath
```

Mac GUI에서만 실패한다면 `~/.config/husky/init.sh`에 Node와 Python을 제공하는
버전 관리자의 최소 초기화만 넣는다. 무거운 `.zshrc` 전체 source는 Hook 속도를
늦출 수 있다.

`--no-verify`로 문제를 숨기지 않는다. 긴급 우회가 이미 발생했다면 `hpr`을 직접
실행하고 PR에 우회 이유와 결과를 기록한다.

## 테스트 실패

1. 실패한 시나리오 ID와 재현 명령을 기록한다.
2. 원인이 테스트 데이터/환경인지 제품 코드인지 분리한다.
3. 테스트를 삭제하거나 skip해 통과시키지 않는다.
4. 수정 범위가 계획을 넘으면 오케스트레이터에게 되돌린다.
5. 미해결이면 보고서를 `FAIL` 또는 `PARTIAL`로 남긴다.

## 보고서 파일이 이미 존재

하네스는 덮어쓰지 않는다. 기존 문서가 같은 실행인지 확인하고 새로운 안정적
식별자를 사용한다. 기존 증거를 삭제하지 않는다.

## Terraform plan 실패

- apply하지 않는다.
- backend/provider/권한/변수 누락을 실제 값 없이 분류한다.
- 설계 또는 IaC PR을 수정하고 새 head 승인을 받는다.
- 실패한 plan을 성공 증거로 사용하지 않는다.

## Apply 실패

자동 재시도하지 않는다.

1. workflow와 AWS 감사 로그를 권한 있는 사람이 확인한다.
2. Terraform state와 실제 자원을 대사한다.
3. 추가 변경 전에 운영 이슈와 복구 계획을 만든다.
4. 부분 생성 자원을 수동 삭제하지 않는다.
5. 수정 PR은 두 명의 새 exact-head 승인을 다시 받는다.

workflow는 plan/apply 원문을 로그에 출력하지 않는다. 상세 진단은 제한된 AWS
도구와 감사 기록에서 수행한다.

## 에이전트가 범위를 넘었을 때

에이전트를 중지하고 `git status --short`, `git diff --name-only`로 영향 범위를
확인한다. 사용자 소유 변경은 되돌리지 않는다. 승인되지 않은 새 파일은 별도
목록으로 검토한 뒤 사람에게 처리 방향을 묻는다.

## Rebase 충돌 (`./harness sync`)

`./harness sync`가 충돌로 멈추면 출력된 파일 목록을 그대로 확인한다.

1. 충돌 파일을 열어 `<<<<<<<`/`=======`/`>>>>>>>` 마커를 직접 해결한다.
2. `git add <file>`로 해결한 파일만 스테이징한다.
3. 전부 해결했으면 `git rebase --continue`.
4. 판단이 서지 않으면 `git rebase --abort`로 되돌리고 사람에게 묻는다.

Claude는 사용자 승인 없이 충돌을 자동으로 해결하거나 `--continue`를 대신
실행하지 않는다. `./harness pr-ready`가 "behind origin/\<base\>" 오류를 내면
먼저 `./harness sync`부터 실행한다. base가 무엇인지 헷갈리면 `./harness base`로
확인한다.

## Git 작업 중단

커밋이나 push가 실패해도 파일 구현 결과와 Git 이력을 혼동하지 않는다. 현재
브랜치, staged 파일, 원격 상태를 읽기 전용으로 확인한다. 강제 push, hard reset,
광범위 checkout은 사용하지 않는다.
