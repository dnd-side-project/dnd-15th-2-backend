# 푸시 발송 운영 세팅 절차

GitHub Issue `#179`가 만든 FCM 발송 경계를 실제 운영에서 켜기 위해 사람이
수행해야 하는 준비 절차다. 저장소 코드는 "이미 존재하는 Firebase 프로젝트"를
전제로 HTTP v1을 호출할 뿐이며, 프로젝트 생성·자격증명 발급·secret 등록은
코드가 대신할 수 없다.

secret 취급은 `docs/harness/SECRET_HANDLING.md`와 `AGENTS.md` 4.10을 따른다.
이 문서에는 실제 key, credential, 계정 식별자, 프로젝트 ID를 기록하지 않는다.

## 1. 현재 상태

| 구성 요소 | 상태 |
| --- | --- |
| Device 등록·해지 API | 구현됨 (`#179`) |
| token 암호화·fingerprint | 구현됨 (`#179`) |
| FCM HTTP v1 adapter | 구현됨 (`#179`) |
| dispatch worker 로직 | 구현됨 (`#179`) |
| dispatch worker·retry policy bean 등록 | 구현됨, 조건부 (`#182`) |
| scheduler/poller 배선 | 구현됨, **기본 비활성** (`#182`) |
| 운영 주기·batch·lease·retry 수치 | **미결정. 사람 작업** |
| Firebase 프로젝트·credential | **미생성. 사람 작업** |
| SSM SecureString·ECS 주입 | **미구현. 별도 설계 승인 필요** |

`#182`이 주기 실행 기반을 붙였지만 `qello.worker.scheduling.enabled`의 기본값이
`false`이므로 아무것도 자동 실행되지 않는다. 발송을 실제로 켜는 것은 8단계다.

`local`, `test`, `integration` profile에서는 `PushConfiguration`의
`NoOpPushProvider`가 등록되어 아래 설정이 하나도 없어도 애플리케이션이 기동한다.
그 외 profile에서는 `PushProperties`와 `PushTokenProperties`가 기동 시점에
값을 검증하고, 비어 있거나 형식이 틀리면 **기동에 실패한다.** 경고 로그만 남기고
뜨지 않는다. 설정이 빠진 채 뜨면 사용자 기기 token이 보호되지 않은 상태로
저장될 수 있기 때문이다.

## 2. 절차 개요

각 단계는 앞 단계의 산출물을 입력으로 받는다. 순서를 건너뛰지 않는다.

| 단계 | 내용 | 담당 | 선행 조건 |
| --- | --- | --- | --- |
| 1 | Firebase 프로젝트와 서비스 계정 | secret owner | 없음 |
| 2 | Android 앱 등록 | 모바일 | 1 |
| 3 | Apple Developer 가입과 APNs 연결 | 모바일 + secret owner | 1 |
| 4 | 서버 token 보호 key 생성 | secret owner | 없음 |
| 5 | 관리형 secret 저장소 등록 | secret owner | 1, 4 |
| 6 | runtime 주입 인프라 | 승인된 인프라 workflow | 5 |
| 7 | 기동 확인과 등록 경로 검증 | 백엔드 | 6 |
| 8 | worker 수치 결정과 활성화 | 백엔드 + secret owner | 7 |
| 9 | 실기기 수신 검증 | 모바일 | 2, 3, 8 |

## 3. 단계 1 — Firebase 프로젝트와 서비스 계정

1. Google 계정으로 Firebase 프로젝트를 생성한다. FCM 발송 자체에는 요금이
   부과되지 않으므로 무료 플랜으로 시작할 수 있다.
2. 해당 프로젝트에서 Cloud Messaging API (V1)를 활성화한다. 레거시 서버 key
   방식은 사용하지 않는다. adapter는 HTTP v1 endpoint만 호출한다.
3. 서비스 계정 key(JSON)를 발급한다. 서버는
   `https://www.googleapis.com/auth/firebase.messaging` scope만 요청하므로
   메시지 전송에 필요한 최소 권한만 부여한다.
4. 산출물 두 가지를 5단계로 전달한다.
   - 프로젝트 ID
   - 서비스 계정 key JSON 원문

JSON key 파일은 저장소, Issue, PR, 대화, 로그 어디에도 붙여넣지 않는다.
발급 직후 관리형 secret 저장소로 옮기고 로컬 사본은 폐기한다.

## 4. 단계 2 — Android 앱 등록

1. Firebase 프로젝트에 Android 앱을 package name으로 등록한다.
2. `google-services.json`을 앱에 배치한다.
3. 앱은 FCM registration token을 획득·갱신할 때마다
   `POST /api/v1/notifications/devices`를 `platform=ANDROID`로 호출하고,
   로그아웃 시 `POST /api/v1/notifications/devices/revoke`를 호출한다.

서버 쪽 추가 작업은 없다.

## 5. 단계 3 — Apple Developer 가입과 APNs 연결

iOS 푸시는 Apple Developer Program **유료 멤버십이 있어야 한다.** APNs key를
발급할 수 없으면 iOS 발송은 어떤 우회로도 불가능하다. 일정 계획 시 이 단계의
리드타임을 가장 먼저 확보한다.

1. Apple Developer Program에 가입한다.
2. APNs Auth Key(`.p8`)를 발급하고 Key ID와 Team ID를 확인한다.
3. Firebase 프로젝트의 Cloud Messaging 설정에 위 세 값을 등록한다.
   서버는 APNs를 직접 호출하지 않는다(`TASK.md` 명시적 제외). iOS 발송은
   Firebase를 경유하므로, APNs 연결이 없는 Firebase 프로젝트에서는 iOS만
   조용히 실패한다.
4. Firebase 프로젝트에 iOS 앱을 bundle ID로 등록하고
   `GoogleService-Info.plist`를 배치한다.
5. 앱에 Push Notifications capability를 추가하고 배포 채널별 APNs 환경
   (개발/운영)이 일치하는지 확인한다. 환경 불일치는 등록은 성공하고 발송만
   실패하는 형태로 나타난다.
6. token 전달 계약은 Android와 동일하며 `platform=IOS`를 사용한다.

`.p8` key 파일도 서비스 계정 key와 같은 취급을 받는다.

## 6. 단계 4 — 서버 token 보호 key 생성

Firebase와 무관하게 서버가 자체적으로 생성하는 key다. 기기 token 원문을
암호화하고 fingerprint를 만드는 데 쓴다.

필요한 값:

| 값 | 제약 |
| --- | --- |
| current key ID | 우리가 정하는 논리 식별자. previous key ID와 달라야 한다 |
| current encryption key | standard Base64로 인코딩한 정확히 32 bytes |
| fingerprint key | standard Base64로 인코딩한 정확히 32 bytes |
| previous key ID | 회전 기간에만 사용 |
| previous encryption key | 회전 기간에만 사용 |

생성 예:

```bash
openssl rand -base64 32
```

세 key는 서로 다른 값이어야 한다. 암호화 key와 fingerprint key를 공유하면
`DEC-179-004`의 key 분리 요구를 위반한다.

previous key ID와 previous encryption key는 **둘 다 있거나 둘 다 없어야 한다.**
한쪽만 주입하면 기동에 실패한다. 암호화 write는 항상 current key만 사용하고,
previous key는 복호화 read에만 참여한다.

## 7. 단계 5 — 관리형 secret 저장소 등록

`TASK.md`의 D-2 secret ownership에 따라 secret owner(`@Byuntil`, `@tkv00`)가
SSM SecureString에 사전 등록한다. 논리 prefix는
`qello.notification.push.token-protection`과 `qello.notification.push.fcm`이며,
애플리케이션은 아래 환경변수로 값을 읽는다.

| 환경변수 | 출처 | 필수 |
| --- | --- | --- |
| `QELLO_NOTIFICATION_PUSH_FCM_PROJECT_ID` | 단계 1 | 예 |
| `QELLO_NOTIFICATION_PUSH_FCM_CREDENTIAL_JSON` | 단계 1 | 예 |
| `QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_KEY_ID` | 단계 4 | 예 |
| `QELLO_NOTIFICATION_PUSH_TOKEN_CURRENT_ENCRYPTION_KEY_BASE64` | 단계 4 | 예 |
| `QELLO_NOTIFICATION_PUSH_TOKEN_FINGERPRINT_KEY_BASE64` | 단계 4 | 예 |
| `QELLO_NOTIFICATION_PUSH_TOKEN_PREVIOUS_KEY_ID` | 단계 4 | 회전 중에만 |
| `QELLO_NOTIFICATION_PUSH_TOKEN_PREVIOUS_ENCRYPTION_KEY_BASE64` | 단계 4 | 회전 중에만 |

FCM credential, AES key ring, HMAC key는 서로 다른 파라미터로 분리한다.
timeout(`connect-timeout`, `read-timeout`)은 secret이 아니며 `application.properties`에
이미 고정돼 있다.

## 8. 단계 6 — runtime 주입 인프라

현재 `infra/`에는 bootstrap과 S3 모듈만 있고, ECS 실행 환경과 SSM 파라미터
리소스가 없다. 이 단계는 `#179` 범위 밖이며 다음 절차를 따른다.

```text
GitHub Issue
→ TASK.md 초기화
→ /harness-infra-design
→ Infrastructure Design Report
→ 사람의 설계 승인
→ /harness-infra-build
→ Terraform plan
→ Pull Request
→ 보호된 GitHub Actions에서 apply
```

설계에 포함해야 하는 것:

- 위 파라미터 경로와 SecureString 지정
- ECS runtime identity에 해당 경로 **read-only**만 부여하는 최소 권한 정책
- key 회전 시 current/previous를 동시에 유지할 수 있는 파라미터 구조
- rollback 경로(이전 key version과 credential로 복귀)

AI 에이전트는 apply를 실행하지 않는다.

## 9. 단계 7 — 기동 확인과 등록 경로 검증

1. 배포 후 애플리케이션이 정상 기동하는지 확인한다. 기동 실패 시 예외 메시지가
   비어 있거나 형식이 틀린 항목을 가리킨다. 이때 값 원문은 로그에 남지 않는다
   (`PushProperties`, `PushTokenProperties` 모두 `toString`을 redaction한다).
2. 실기기에서 `POST /api/v1/notifications/devices`를 호출해 등록이 성공하는지
   확인한다. token은 4096 bytes를 넘으면 거절된다. 서버는 truncate하지 않는다.
3. 재등록, 다른 사용자의 동일 token 등록, `POST .../devices/revoke` 멱등성을
   확인한다.
4. DB, 응답, 로그, 예외 어디에도 token 원문이 없는지 확인한다.

이 시점에는 `notification_delivery`가 쌓이기만 하고 발송은 일어나지 않는다.

## 10. 단계 8 — worker 수치 결정과 활성화

`#182`이 `PushDeliveryDispatchWorker`, `PushDeliveryRetryPolicy`와 주기 실행
adapter를 배선했다. 세 bean은 `qello.worker.scheduling.enabled`와
`qello.worker.scheduling.push-delivery-dispatch.enabled`가 **모두 `true`일 때만**
등록된다. 둘 중 하나라도 꺼져 있으면 bean 자체가 만들어지지 않으므로 push
dispatch 경로가 존재하지 않는다.

`#182`은 코드 경로만 만들었고 운영 수치는 결정하지 않았다. 이 단계에서 값을
정하고 주입한다.

### 10.1 활성화 gate

| 키 | 기본값 | 의미 |
| --- | --- | --- |
| `qello.worker.scheduling.enabled` | `false` (`QELLO_WORKER_SCHEDULING_ENABLED`) | 전체 worker scheduling |
| `qello.worker.scheduling.push-delivery-dispatch.enabled` | 없음 | push dispatch worker |

global gate가 꺼져 있으면 scheduler thread pool, lease owner, metrics bean까지
전부 등록되지 않는다. 롤백은 이 값을 `false`로 되돌리고 재배포하는 것이다.

### 10.2 결정해야 하는 값

enabled 상태에서 아래 값이 비어 있거나 0 이하이면 **기동에 실패한다.** 운영
fallback 기본값은 두지 않는다. 어떤 블록의 어떤 값이 왜 거절됐는지는 예외
메시지가 `qello.worker.scheduling.<블록>.<필드>` 형태로 가리킨다.

| 키 | 제약 | 결정 기준 |
| --- | --- | --- |
| `qello.worker.scheduling.pool-size` | 양수 | 활성화한 worker 수와 동시 실행 허용치 |
| `...push-delivery-dispatch.fixed-delay` | 양수 기간 | 발송 지연 허용치 대 DB polling 비용 |
| `...push-delivery-dispatch.batch-size` | 양수 | 한 주기에 claim할 group 수 |
| `...push-delivery-dispatch.lease-duration` | 양수 기간 | 한 batch의 최악 처리 시간보다 길어야 한다 |
| `...push-delivery-dispatch.retry.max-attempts` | 양수 | 초과 시 `DEAD` |
| `...push-delivery-dispatch.retry.base-backoff` | 양수 기간 | 지수 backoff 시작값 |
| `...push-delivery-dispatch.retry.backoff-cap` | `base-backoff` 이상 | 지수 backoff 상한 |

provider가 `Retry-After`를 주면 그 값을 backoff보다 우선한다.

`lease-duration`이 한 batch의 실제 처리 시간보다 짧으면 lease가 만료된 뒤
다른 instance가 같은 group을 다시 claim한다. 이 경우에도 `STALE_CLAIM`
fencing이 중복 발송을 막지만, 처리량이 낭비되므로 `batch-size`와 함께 정한다.

### 10.3 활성화 후 확인

1. 기동 로그에 설정 검증 예외가 없는지 확인한다.
2. `qello-worker-` prefix thread에서 dispatch가 도는지 확인한다.
3. `worker=push_delivery_dispatch` tag로 아래 metric이 등록되는지 확인한다.
   tag는 `worker`와 `outcome`뿐이며 owner, 행 ID, 사용자 정보는 포함하지 않는다.
   (`qello.worker.scanned.total`은 sweep worker만 기록한다.)
   - `qello.worker.claimed.total`
   - `qello.worker.outcome.total`
4. `outcome=SENT`와 `outcome=DEAD` 추이를 확인한다. `BATCH_FAILED`가 반복되면
   해당 주기가 통째로 실패한 것이므로 즉시 gate를 되돌린다.
5. 다중 instance로 띄운 경우 같은 group이 두 번 발송되지 않는지 확인한다.
   lease owner는 application context마다 생성되는 UUID이며, 서로 다른
   instance의 owner가 실제로 겹치지 않는지는 `#182`에서 검증하지 않았다.

## 11. 단계 9 — 실기기 수신 검증

모바일 팀이 아래 상태 조합에서 수신을 확인하고 증거를 남긴다. 이 검증은 서버
테스트로 대체할 수 없다.

- foreground / background / 앱 종료 / 화면 잠금
- Android / iOS 각각
- 알림 권한 거부 상태에서의 동작
- 알림 tap 시 알림함 홈 진입

payload는 `type`, `count`, `hasRemainingTime` 세 필드만 포함한다. 문구는
client가 조립한다. server는 `title`/`body`와 `notificationId`를 보내지 않는다
(`DEC-179-008`).

## 12. key 회전 절차

1. 새 key ID와 새 32-byte key를 생성한다.
2. 기존 current 값을 previous 자리로 옮기고, 새 값을 current로 등록한다.
3. 배포한다. 이 시점부터 write는 새 key로, read는 두 key 모두로 동작한다.
4. 기존 key로 암호화된 행이 모두 갱신되거나 만료된 뒤 previous 항목을 제거한다.
5. 각 단계는 보호된 인프라 workflow에서 수행한다.

FCM 서비스 계정 key도 같은 방식으로 새 key 등록 후 구 key 폐기 순서를 지킨다.
fingerprint key를 교체하면 기존 fingerprint가 전부 무효가 되어 소유권 이전
판정이 깨진다. 교체가 필요하면 별도 Issue로 영향 범위를 먼저 설계한다.

## 13. 이 절차가 보장하지 않는 것

- Firebase 프로젝트의 조직 소유권과 결제 계정 관리
- Apple Developer 멤버십 갱신
- 알림 발송량 상한, 방해 금지 시간, 묶음 처리 (`#180`)
- 발송 실패율 경보. `#182`이 counter를 등록하지만 metric exporter와 alert rule이
  없어 지표가 프로세스 밖으로 나가지 않는다
- 운영 주기·batch·lease·retry 수치의 적정성. `#182`은 값을 검증만 하고 결정하지
  않았다
- 다중 instance 동시 실행 시 lease owner 유일성. 단일 context 안의 안정성만
  검증했다
- push 성공 여부와 무관하게 보존되는 `notification` 원장과 콘텐츠 열람 자격은
  이 절차의 영향을 받지 않는다
