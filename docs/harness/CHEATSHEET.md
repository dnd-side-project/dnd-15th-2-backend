# Miri Harness Cheat Sheet

## 처음 한 번

```bash
./scripts/setup-macos.sh --install --install-agents --install-hooks
./scripts/install-shortcuts.zsh --install
npm ci
exec zsh
gh auth login
claude doctor
codex --login
hd
```

## 작업 시작

```bash
git status --short
export JIRA_KEY=MIR-123
export GITHUB_ISSUE=42
h start --jira "$JIRA_KEY" --issue "$GITHUB_ISSUE" --type test \
  --slug order-reservation --confirm-jira-linked
h task-init --title "주문 예약 테스트" --replace
hs
```

브랜치:

```text
<type>/<JIRA>-gh-<issue>-<slug>
```

## 빠른 명령

```bash
hd                                  # 도구 점검
hs                                  # 현재 작업 상태
hc                                  # 하네스 정적 검사
hpr                                 # 정적 검사 + Gradle check
htp ORDER-RESERVATION               # 현재 Jira 키로 테스트 계획 생성
htr ORDER-RESERVATION               # 현재 Jira 키로 테스트 실행 + 보고서 생성
hid AWS-BASELINE                    # 현재 Jira 키로 인프라 설계 문서 생성
hcheat                              # 이 문서 출력
npm run hooks:validate              # Husky 구성 검사
```

## Claude Code

```text
/harness-test-plan
/harness-test-run
/harness-infra-design
/harness-infra-build
/harness-review
```

## Codex

```text
$harness-test-plan
$harness-test-run
$harness-infra-design
$harness-infra-build
$harness-review
```

## 테스트 클래스 필수 형식

```java
/**
 * Created at: 2026-07-24T13:52:05+09:00
 * Source scenario: TEST-PLAN-<JIRA-KEY>-ORDER-UNIT-001
 */
class OrderServiceTest {
    @Test
    @DisplayName("동시 주문에서도 같은 잔액을 중복 예약하지 않는다")
    void reservesBalanceOnce() {
    }
}
```

## 커밋과 PR

Husky 자동 검사:

```text
pre-commit → branch + staged 공백/secret + 관련 정책
prepare-commit-msg → branch에서 type/Jira/Issue 자동 추가
commit-msg → 커밋 메시지 형식
pre-push   → 전체 하네스 + Gradle check
```

```bash
git commit -m "Mac 설정 문서 추가"
# → 현재 branch가 docs라면:
# docs: [$JIRA_KEY] Mac 설정 문서 추가 (#$GITHUB_ISSUE)

git commit -m "test(order): 예약 테스트 추가"
# → test(order): [$JIRA_KEY] 예약 테스트 추가 (#$GITHUB_ISSUE)
```

```text
PR: [$JIRA_KEY] test: add order reservation scenarios
```

긴급 상황에서 `--no-verify`를 사용했다면 PR에 사유와 직접 실행한 검사 결과를
기록한다. CI 검사는 우회되지 않는다.

## 인프라 금지선

```text
설계 → IaC → fmt/validate/plan → PR
```

여기까지 자동/에이전트 작업 가능.

```text
@Byuntil + @tkv00 승인
→ protected Environment 승인
→ APPLY <JIRA> PR-<번호> 직접 입력
→ apply
```

두 승인 전에는 `terraform apply`, `cdk deploy`, 프로덕션 변경을 실행하지 않는다.

## 실패 시

```bash
hs
hc
git status --short
```

재시도 전에 원인을 확인한다. 민감한 로그는 채팅이나 PR에 붙이지 않는다.
인프라 apply 실패는 자동 재시도하지 않고 운영 이슈를 만든다.
