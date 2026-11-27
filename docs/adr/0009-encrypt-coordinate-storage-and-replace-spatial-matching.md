---
id: ADR-0009
title: 정확 좌표를 앱에서 암호화하고 매칭 계산 경계를 분리한다
status: accepted
category: SECURITY
date: 2026-11-24
tags: [location, encryption, postgis, matching, key-rotation]
related: ["#388", "#364", "#365", "ADR-0001", "ADR-0002", "ADR-0008"]
---

# ADR-0009. 정확 좌표를 앱에서 암호화하고 매칭 계산 경계를 분리한다

> **요약**
> - **무엇을**: `active_user_presence.position`과 `post_audience.origin_position`의 정확 좌표를 암호화하고, DB에서 수행하던 거리·방위 매칭 계산의 경계를 바꾼다.
> - **왜**: 위치기반서비스 사업 신고 점검표(첨부 2, 2항) 대응으로 컬럼 단위 좌표 암호화가 필요하지만, 암호화하면 DB가 좌표를 읽지 못해 기존 PostGIS 공간 쿼리가 동작하지 않기 때문이다.
> - **채택안**: **대안 A(앱 AES-256-GCM 암호화 + 앱 회전타원체 계산)**를 조건부 채택한다.
> - **승인과 남은 검증**: @Byuntil이 2026-11-25 D1~D6·D8~D10을 추천안대로, D7을 기존 평문 폐기·단일 migration으로 승인했다. 설계는 `accepted`이며 수치·성능 검증과 테스트 계획 승인은 별도다.

## 배경

### 1. 왜 지금 하는가

위치기반서비스 사업 신고를 위해 첨부 2 점검표를 제출해야 한다.
점검표 2항 "좌표 저장 암호화"(작업 ID C8)가 현재 미충족 상태다.

점검표 PDF 예시가 암호화된 DB 테이블 캡처여서 RDS 디스크 암호화만으로는 부족하다고 보았다.
Issue #388과 `TASK.md`에 기록된 Q1 답변(2026-11-23)은 컬럼 단위 저장 암호화가 필요하다는 사람의 판단이다(`CONFIRMED`).

점검표 원문과 심사기관의 수용 여부는 이번 조사에서 확인하지 않았다(`UNKNOWN`).
이 ADR은 법적 적합성 판정이나 운영 배포 승인이 아니다.

### 2. 무엇이 평문인가

현재 DB에서 정확 좌표를 평문 `geography(Point, 4326)`로 저장하는 컬럼은 두 곳이다(`src/main/resources/db/migration/V1__create_direction_communication_schema.sql`).

- `active_user_presence.position`: 수신 후보를 찾는 TTL(유효 기간) 기반 읽기 모델이다. 여기서 presence는 사용자의 최근 활성 위치 상태를 뜻한다.
- `post_audience.origin_position`: 질문글 발송 시점의 원점 좌표를 고정해 둔 snapshot(당시 상태 사본)이다.

V1의 P06 규칙은 정확 좌표를 API 응답·로그·분석 이벤트·Outbox payload에 넣지 않도록 정한다.
같은 파일의 presence 테이블 주석에는 정확 좌표의 유일한 저장소라고 쓰여 있지만, 실제 audience 정의와 코드에도 원점이 있다.
보호 범위는 주석 한 줄이 아니라 두 실제 컬럼으로 정한다.

이미 적용된 V1과 기존 원장은 수정하지 않는다.
TTL 만료는 조회 제외 조건일 뿐 물리 삭제가 아니며, 보존과 파기는 C6 범위다.

### 3. 왜 단순히 암호화만 할 수 없는가

현재 매칭과 미리보기는 두 평문 컬럼 위에서 PostGIS로 거리·방위·역방위를 계산한다.
여기서 역방위는 수신자 위치에서 발신자 원점을 바라본 방위각을 뜻한다.
후보 탐색에는 `active_user_presence_position_gix`(`receive_allowed = TRUE` 부분 GiST 인덱스, 공간 탐색용 트리 인덱스)를 사용한다.

컬럼을 암호화하면 DB가 좌표를 읽을 수 없으므로 기존 공간 쿼리와 GiST 인덱스가 동작하지 않는다.
따라서 암호화만 적용할 수 없고, 공간 계산을 다른 곳으로 옮기거나 복호화 경계를 새로 정해야 한다.

현재 구현에서 확인한 코드 경계는 다음과 같다. Java 경로의 공통 접두사는 `src/main/java/com/dnd/qello/`다.

| 근거 | 현재 저장·계산 |
| --- | --- |
| `V1__create_direction_communication_schema.sql` | `active_user_presence.position`, `post_audience.origin_position`이 평문 `geography(Point, 4326)`다. `active_user_presence_position_gix`는 `receive_allowed = TRUE` 부분 GiST 인덱스다. |
| `direction/repository/jdbc/sql/ActiveUserPresenceSql.java` | `UPSERT`와 `UPSERT_IF_NEWER`가 평문 좌표를 저장한다. `FIND_CANDIDATES_SQL`과 `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`은 `ST_DWithin`, `ST_Distance`, `ST_Azimuth`를 사용한다. |
| `direction/repository/jdbc/sql/PostAudienceSql.java` | `UPSERT`가 발송 원점의 평문 snapshot을 저장한다. |
| `direction/repository/jdbc/JdbcActiveUserPresenceRepository.java`, `JdbcPostAudienceRepository.java` | 단건 조회에서 `ST_Y`와 `ST_X`로 위도·경도를 복원한다. |
| `direction/matching/DirectionMatchingWorker.java` | 발송 원점으로 후보 전체를 조회·정렬한 뒤 최대 수신자 수의 3배를 검사한다. 수신 상태 잠금·용량 예약·lease fencing(만료된 작업자의 뒤늦은 갱신을 막는 잠금 검증)을 유지해야 한다. |
| `direction/service/DirectionPostService.java` | `preview`는 후보 조회, `previewAll`은 방향별 집계를 사용한다. `send`는 presence를 audience 원점으로 복사한다. |
| `direction/config/DirectionPostProperties.java`, `src/main/resources/application.yml` | 실제 설정 prefix는 `qello.direction.post`다. 최대 거리 기본값·상한은 `20,100,000m`, 범위는 `GLOBAL`, presence TTL은 `PT24H`, 질문글 TTL은 `PT12H`다. |

### 4. 설계를 제약하는 사실

- **공간 인덱스의 축소 효과가 작다**: 최대 거리 기본값이 `20,100,000m`(지구 반대편까지, 범위 `GLOBAL`)이므로 넓은 대역에서는 활성 후보 대부분을 읽는다.
- **최대 수신자 수 설정이 계산 대상을 줄이지 않는다**: worker는 현재 10명 설정의 3배인 30명을 검사하지만, 정렬 전 후보 전체를 공간 계산하므로 계산 대상이 30명으로 제한되지 않는다.
- **PostGIS 계산은 회전타원체 기준이다**: `geography`의 거리와 방위는 단순 구면이 아니라 회전타원체 공식으로 계산한다[S1, S2].
- **기존 AES-GCM 구현 자산이 있다**: push 토큰용 `AesGcmPushTokenProtector`와 `PushTokenKeyRing`이 이미 저장소에 있다.

## 고려한 선택지

아래 비교에서 `N`은 계정 상태·수신 허용·관측 시각·만료·차단 같은 비공간 조건을 통과한 후보 수다.
`M`은 공간 필터를 통과한 수, `K`는 B의 보수적 셀 필터 결과다.
비용은 실측 시간이 아닌 구조적 추정이며, 정렬 `O(M log M)`과 worker의 최종 용량 예약 비용은 별도다.

| 선택지 | 키 노출 경로 | 인덱스 | 매칭 1회 비용 | 코드 변경 범위 | PostGIS 결과 일치 방법 |
| --- | --- | --- | --- | --- | --- |
| A. 앱 AES-GCM 암호화 + 앱 계산 | 키와 복호화 좌표는 앱 메모리에만 둔다. DB에는 암호문만 전달한다. 앱 로그·heap dump·secret 주입 경로를 보호해야 한다. | 정확 좌표 GiST는 쓸 수 없다. 기존 만료·계정·차단 인덱스는 유지한다. | 암호문 N행 전송, N회 복호화와 회전타원체 계산, M행 정렬. | 두 JDBC 저장·복원 경계, 후보 조회·집계 구현, 계산 컴포넌트가 바뀐다. worker·service의 port 계약은 유지한다. | GeographicLib Java 추가 후 기존 PostGIS SQL과 차등 검증한다. |
| B. A + 평문 저정밀 셀 | A와 같지만 셀 ID로 대략 위치가 드러난다. 기존 nullable `coarse_cell_id`, `origin_cell_id`가 있다고 임의 정밀도를 승인한 것은 아니다. | 승인된 셀 ID 인덱스로 K행을 읽는다. GLOBAL 기본 범위에서는 K가 N에 가까워진다. | K행 전송, K회 복호화·계산. 짧은 대역에서만 K가 N보다 작을 수 있다. | A에 셀 생성·해상도·covering·인덱스가 추가된다. | 날짜변경선·극지·셀 경계에서 false negative 없는 후보 상위집합을 만든 뒤 A와 같이 정확 계산한다. |
| C. DB 내부 pgcrypto 복호화 + PostGIS | 대칭 키를 SQL bind로 DB에 넘긴다. 앱·DB 관리자, DB 메모리, statement·error·audit 로그가 신뢰 경계에 들어간다. TLS가 DB 내부 노출을 없애지는 않는다. | 암호문에 기존 공간 GiST를 적용할 수 없다. | 비공간 필터 뒤 DB에서 N회 복호화·계산. 결과 M행만 전송하나 DB CPU에 부하가 모인다. | 저장 포맷·key ID 선택·SQL·extension 운영이 바뀐다. pgcrypto PGP 포맷과 앱 AES-GCM envelope는 호환되지 않는다. | 복호화된 `geography`에 기존 PostGIS 함수를 그대로 적용한다. |
| D. 앱 복호화 + 평문 bind만 PostGIS에 전달 | 키는 DB에 전달하지 않지만 정확 좌표 N개와 원점이 DB 메모리에 도달한다. bind·error·audit 로그와 임시 spill을 확인해야 한다. | 정확 좌표 GiST는 쓸 수 없다. | 암호문 N행 조회, 복호화, 평문 N행 재전송, DB 계산. 묶음 전달을 써도 추가 전송·왕복 비용이 든다. | 두 저장 경계와 2단계 후보 쿼리가 바뀐다. 단일 snapshot과 전역 정렬·집계를 보존해야 한다. | 동일 좌표와 기존 PostGIS 함수·비교식을 유지해 A보다 수치 이식 위험이 작다. |

C에서 복호화 결과를 영속 generated column이나 함수 인덱스로 저장하면 다시 평문 위치를 보관하게 되므로 해결책으로 삼지 않는다.
D도 임시 테이블이나 materialized view에 평문 좌표를 넣지 않는다.
bind 사용만으로 로그·디스크 비기록을 보장할 수 없으므로 D의 운영 수용 가능성은 `UNKNOWN`이다.

### 좌표 데이터 흐름 비교

```text
[현재]
앱(평문 위도·경도) ──▶ DB 저장(geography 평문 + GiST 인덱스)
매칭·미리보기 ──▶ DB(PostGIS 거리·방위·역방위 계산·정렬) ──▶ 앱(결과 M행 수신)

[채택안 A]
앱(평문 위도·경도 → AES-256-GCM 암호화) ──▶ DB 저장(암호문 컬럼, GiST 제거)
매칭·미리보기 ──▶ DB(비공간 필터로 암호문 N행 조회) ──▶ 앱(복호화 → 회전타원체 계산·정렬)
```

## 결정

@Byuntil의 2026-11-25 승인에 따라 대안 A를 조건부 채택한다.
D1~D6·D8~D10은 추천안대로 확정했고, D7은 재암호화 대신 기존 평문 폐기와 단일 Flyway migration으로 확정했다.

앱 계산의 기존 후보 집합·순서·집계 일치 여부와 성능은 아직 `UNKNOWN`이다.
테스트 계획을 별도로 승인받고 구현·측정한다.
불일치가 나오면 허용 오차를 넓혀 통과시키거나 D로 자동 전환하지 않고 결정을 재검토한다.
API 요청·응답과 발송 정책은 변경하지 않는다.

### 정확도와 매칭 계약

Issue의 A 설명에 있는 "구면"을 구현 기준으로 사용하지 않는다.
PostGIS `geography`의 `ST_Distance` 기본값은 SRID의 회전타원체 거리이고, `ST_Azimuth`도 회전타원체 inverse geodesic 계산이다[S1, S2].
Haversine, 평면 근사, 단순 방위 `+180`은 대체안이 아니다.

A와 B는 WGS84 회전타원체의 GeographicLib Java `Geodesic.WGS84.Inverse`를 후보로 삼는다[S4].
현재 `build.gradle`에는 이 의존성이 없다. D2에서 추가를 승인했으며 버전·라이선스·배포물은 구현 착수 시 확인하고 버전을 고정한다.

거리 `s12`와 출발점 방위 `azi1`을 사용하고 방위는 `[0, 360)`으로 정규화한다.
역방위는 수신자와 발신자를 뒤집어 별도로 계산하며, 도착점의 전방 접선인 `azi2`를 역방위로 그대로 쓰지 않는다.
암호화 직렬화는 기존 `geography`에 전달하던 좌표의 수치 정밀도를 보존하고 임의로 반올림하지 않는다.

일반적인 서로 다른 두 점의 승인된 허용 오차는 거리 절댓값 `0.001m` 이하, 방위·역방위 순환 각 차이 `0.0000001도` 이하다(D3, 실측 전).
이는 수치 결과 비교 기준이지 후보 포함 조건의 epsilon이 아니다.
다음 동등성 조건을 별도로 요구한다.

- 동일 입력·동일 시각의 후보 ID 집합, 순서, 방향별 개수는 정확히 일치해야 한다. 거리 경계·섹터 경계 직전·일치·직후와 거리 동률에 오차를 이유로 예외를 두지 않는다.
- SQL의 거리 하한 포함, 섹터 시작 포함·끝 제외, 0도 경계 wrap을 유지한다. 매칭의 `ST_DWithin` 상한 판정과 집계의 추가 `distance_m <= max`를 각각 기존 SQL oracle로 비교하며, 둘이 단순 거리 비교와 항상 같다고 가정하지 않는다.
- 공정성 순서(`recent_received_count`, `last_received_at NULLS FIRST`, 거리, `user_id`)를 유지한다. 정렬 전 거리 반올림이나 후보 임의 LIMIT를 금지하며, worker의 저장 거리는 기존 `HALF_UP` 정수 반올림을 유지한다.
- 동일점은 PostGIS 방위가 NULL이므로 기존 SQL에서 섹터 후보가 되지 않는다[S2]. Java가 임의 방위를 반환해도 후보로 넣지 않는다. 극지·날짜변경선·대척점과 근대척점은 별도 비교하며, 다해성 방위의 PostGIS 선택 규칙 일치는 `UNKNOWN`이다.
- 미리보기는 수신 이력 정렬 없이 모든 segment를 포함하고 0명 방향도 보존한다. 다단계 조회가 필요하면 한 후보 snapshot에서 조건·정렬 자료를 읽으며, D의 두 SQL이나 chunk 사이에서 변동 행을 다시 읽어 혼합하지 않는다.
- worker의 수신 상태 잠금, 용량 예약, 발신 상태 검사, 이벤트 원자성·fencing을 유지한다. 계산이 오래 걸릴 때의 transaction 시간과 lease 여유도 측정한다.

같은 알고리즘 계열을 쓴다는 사실만으로 PostGIS와 Java의 bit 단위 일치를 보장하지 않는다.
경계 불일치가 남으면 A의 완료 조건을 충족하지 못한다.

### 암호문과 키 수명주기

기존 `AesGcmPushTokenProtector`의 AES-256-GCM(12-byte 무작위 nonce, 128-bit tag, version·key ID를 담은 envelope 형식)과 `PushTokenKeyRing`의 current-write·previous-read 패턴을 참고한다.
여기서 envelope는 버전·키 ID·암호문을 함께 묶은 저장 형식을 뜻한다.

push token 타입이나 fingerprint 기능은 좌표에 끌어오지 않는다.
좌표 전용 키와 설정을 두고 push AES/HMAC 키와 분리하며, 좌표 동일성 검색용 hash·fingerprint는 만들지 않는다.

승인된 D4에 따라 위도·경도를 한 payload로 묶고, version·key ID와 용도(table·column)·행 식별자를 AAD(암호화하지 않지만 위변조 검증에 함께 묶는 추가 인증 데이터)에 결합한다.
기존 push 구현의 AAD는 header뿐이므로 행 결합은 그대로 재사용되는 기능이 아니라 새 결정이다.
변조·모르는 key ID·복호화 실패는 좌표와 키를 출력하지 않는 오류로 처리하고, 평문 fallback이나 조용한 후보 누락으로 숨기지 않는다.

| 환경 | 승인된 주입 방침·확인 상태 |
| --- | --- |
| local / test / integration | 합성 데이터 전용 고정 fixture 키를 명시적으로 구성한다. 실제 키 값은 이 문서에 기록하지 않는다. 비테스트 프로필에는 fallback을 두지 않으며, 기존 push가 이 프로필에서 자동 protector를 제공한다고 가정하지 않는다(#365). |
| dev test-server | #364 D-5의 SSM SecureString → EC2 runtime → 보호된 환경 설정 → Compose·app 패턴을 참고하되 좌표 전용 current/previous 키를 별도 승인한다. 기존 push용 파라미터에 좌표 키를 덮어쓰지 않는다. 좌표용 SSM·IAM·배포 연결은 아직 없다(`UNKNOWN`). |
| 운영 | I2와 맞춰 비밀 주입·권한·키 보관·복구 책임을 확정한다. RDS 디스크 암호화 키와 앱 컬럼 키는 별개다. 운영이 dev와 같은 EC2·Compose라고 가정하지 않으며, 리소스 생성·Terraform 변경은 #388 밖이다. |

키 회전은 새 키를 읽을 수 있는 reader를 먼저 배포한 뒤 current-write를 전환한다.
기존 행을 재암호화하거나 승인된 보존 기간이 끝날 때까지 previous-read를 유지한다.
기존 push 설정은 current/previous 두 슬롯이고 D-5 주입에는 previous 파라미터가 포함되지 않았으므로, 무제한 key ring이나 주입 완료로 해석하지 않는다.

두 번째 회전 전에 이전 키 사용 행과 복구 대상 백업이 남았는지 확인한다.
TTL만 지났다는 이유로 키를 폐기하지 않는다.
키 유실은 복호화 불능이며 DB 백업만으로 복구되지 않고, 롤백 버전도 새 envelope와 읽기 키를 알아야 한다.

### 기존 평문 행 이행과 복구

D7의 승인된 수정안에 따라 기존 평문 좌표를 폐기하고 새 Flyway migration 한 번으로 암호문 컬럼으로 전환한다.
운영 DB가 없고 dev는 점검표 대응 대상이 아니라는 사람 확인이 근거다.
B1 위치 수집 게이트(#379)는 기본적으로 수집을 막고 운영 활성화에 신고 수리 참조를 요구한다.
단, `LocationCollectionGate`는 dev·local의 참조 검사를 생략하므로 dev 데이터가 존재하지 않는다는 뜻은 아니다.
dev의 실제 행 수는 `UNKNOWN`이며 아래 손실 범위를 명시한다.

단일 migration의 처리 계약은 다음과 같다.

1. `active_user_presence`에서 `position IS NOT NULL`인 기존 행을 삭제한다. 셀만 있는 행은 보존하되 정확 좌표 매칭 후보에는 포함하지 않는다. 평문 컬럼과 GiST를 제거하고 암호문 컬럼을 추가하며 `ck_active_user_presence_location`은 암호문 또는 기존 셀 중 하나가 있도록 교체한다. 사용자는 게이트가 열린 환경에서 다음 유효한 위치 갱신으로 presence를 다시 채운다.
2. `post_audience`의 정확 원점은 모든 기존 행에서 폐기한다. audience 행과 방향·거리 범위·snapshot 시각·기존 셀은 보존해 발송 멱등 재생에 필요한 메타데이터를 잃지 않게 한다. 새 원점 암호문은 NULL로 시작하며 셀이나 현재 presence에서 원점을 합성하지 않는다.
3. 원점이 폐기되는 audience에 연결된 미완료 글(`SUBMITTED`, `SAFETY_CHECKING`, `MATCHING`, `REVIEW_HELD`)은 같은 migration에서 `MATCH_FAILED`로 종결한다. `ACTIVE`와 기존 종결 상태, 수신자·슬롯·본문·moderation 상태·만료 시각은 변경하지 않는다. 기존 매칭 Outbox는 삭제하거나 이력을 재작성하지 않는다. 이후 claim되면 worker가 비매칭 상태를 확인해 수신자 없이 정상 완료한다.
4. `ck_post_audience_origin`의 좌표·셀 최소 하나 필수 조건은 제거한다. 원점 폐기 후 암호문과 셀이 모두 NULL인 기존 snapshot을 허용하며 가짜 셀을 채우지 않는다. 새 원점 컬럼은 nullable `bytea`로 두고 비NULL 값의 envelope 형식·인증은 앱 경계에서 검증한다. `PostAudience` 복원도 이 NULL 상태를 허용하도록 구현해야 한다. 신규 발송의 정확 원점 필수 검증과 worker의 정확 원점 없는 매칭 거부는 유지한다.

이 처리는 기존 실패 경로를 자동 전이로 오인하지 않는다.
`DirectionMatchingWorker.loadPreciseAudience`의 원점 누락은 영구 실패로 Outbox를 DEAD로 만들지만 글을 `MATCH_FAILED`로 바꾸지는 않는다.
`DirectionPost.expire`도 deadline 이후에만 허용되고 DEAD 이벤트가 글 만료를 다시 실행한다는 보장은 없다.
따라서 migration에서 이미 enum과 DB CHECK에 있는 `MATCH_FAILED`를 사용해 미완료 글을 즉시 닫는다.
`DirectionMatchingWorker.isEligibleForMatching`은 이 상태를 건너뛰고 `DirectionPost.moderate`도 MATCHING이 아니면 뒤늦은 판정을 적용하지 않는다.
이후 같은 멱등키 재요청은 보존된 audience 메타데이터로 기존 실패 글을 재생하며 새 매칭 이벤트를 만들지 않는다.

스키마 변경과 위 데이터 처리를 하나의 transaction에서 수행하며 재암호화 도구·키 주입·평문 fallback을 migration에 넣지 않는다.
실제 migration 번호는 착수 시 최신 기준과 #381의 V35 사용 여부를 확인한다.
완료된 migration 이후 이전 스키마·폐기 좌표로 돌아가는 롤백 경로는 migration 전 백업 복원뿐이다.
구버전 앱만 되돌리거나 암호문 컬럼을 평문 컬럼으로 바꿔도 폐기 좌표는 돌아오지 않는다.
dev의 손실 범위는 정확 좌표가 있던 presence 행, 모든 기존 audience의 정확 원점과 그 원점을 잃은 미완료 글의 매칭 기회다.
백업이 없으면 이 데이터는 복구할 수 없으며 백업 복원은 평문을 다시 가져오므로 별도 사람 승인과 재이행이 필요하다.

옛 WAL·백업·스냅샷과 삭제 페이지까지 즉시 평문이 사라진다고 주장하지 않는다.
그 보존·파기와 매체 보호는 C6·I2와 함께 완료해야 한다.

### post_recipient 파생 값

거리·방위 파생 값도 별도 보호가 필요하다고 판단한다.
worker는 정수 거리와 발신 기준 방위·수신 기준 역방위를 snapshot으로 전달한다.
원점과 결합하면 반올림 정밀도 범위 안에서 수신 위치를 재구성할 수 있으므로, 좌표 컬럼이 아니라는 이유만으로 안전하다고 보지 않는다.

원점 암호화는 키 없는 DB 유출에서 절대 위치 복원을 어렵게 하지만, 알려진 원점·허용된 복호화 결과·반복 관측과 결합하는 위험은 남는다.
정밀 파생 값의 암호화 또는 표시 목적에 맞는 비가역적 저정밀화를 별도 Issue로 분리할 것을 추천한다.

현재 API와 정렬 의존성은 그 Issue에서 확인한다.
이번 #388에서는 구현하지 않으며, 위치 데이터 전체 보호가 끝났다고 선언하지 않는다.
V1의 `distance_band`와 `matched_bearing_deg` 정의만으로 현재 전체 컬럼 이력을 단정하지 않는다.

### 성능 측정 기준

다음 항목은 D8에서 승인한 측정 기준이다. 후보 규모는 설계 가정이며 실제 운영 규모·SLO나 측정 결과를 의미하지 않는다.

| 항목 | 제안 |
| --- | --- |
| 후보 규모 | 비공간 필터 후 `N = 1,000 / 10,000 / 100,000명`. 기준 판단은 `10,000명`, `100,000명`은 한계 측정이다. |
| 분포·거리 | 고정 seed 합성 전세계 분포와 밀집 분포, 짧은 `10km·100km`와 기본 `20,100km`를 비교한다. 동일점·극지·경계 자료도 별도로 둔다. |
| 동시성 | 매칭 1개·4개와 preview 동시 요청을 나눠 측정한다. 키 상태는 current-only와 current/previous 혼합이다. |
| 반복 | 동일 JDK·PostgreSQL/PostGIS·CPU·메모리·네트워크 조건에서 warm-up 20회 뒤 200회 측정하고 cold 구간은 따로 기록한다. 고정 sleep으로 성공을 판정하지 않는다. |
| 범위 | 단일 글의 원점 복호화, 후보 읽기·복호화·계산·정렬·예약·commit까지의 wall time을 기록한다. 후보 repository 시간과 `previewAll` 시간은 별도로 분리한다. |
| 지표 | p50/p95/p99, 처리량, 앱·DB CPU, heap/GC, 읽은 행·전송 bytes, transaction 시간, lease 여유, lock 대기, DB buffer·temp I/O를 기록한다. SQL 계획은 합성 값만으로 `EXPLAIN (ANALYZE, BUFFERS)`를 수집한다. |
| 합격 제안 | `N=10,000`, 동시성 1에서 매칭 p95 `≤ 1초`, `previewAll` p95 `≤ 500ms`, 각 p95가 같은 환경의 기존 SQL 대비 2배 이내. 후보 집합·순서·집계 불일치 0건과 lease 만료로 인한 중복 확정 0건을 별도로 요구한다. |

위 규모와 합격 기준을 제안한 근거는 다음과 같다.

- **10배수 후보 규모(`1,000 / 10,000 / 100,000명`)**: `N`은 최근 24시간(`PT24H`) 활성·수신 허용 후보 수다. `1,000명`은 고정 비용 확인, `10,000명`은 초기~중규모 DAU 기준 판정, `100,000명`은 전송·GC·CPU·lease 중 먼저 한계에 닿는 지점을 찾는 구간이다.
- **`previewAll p95 ≤ 500ms`와 매칭 `p95 ≤ 1초`**: `previewAll`은 사용자가 화면에서 기다리는 동기 요청이므로 더 짧은 상한을 둔다. 매칭은 비동기 worker지만 처리가 길어지면 transaction 점유·잠금 경합·lease 만료 위험이 커지므로 1초 이내로 묶는다.
- **기존 SQL 대비 `2배 이내`**: 절대 시간만 두면 측정 머신 사양에 따라 결과가 흔들린다. 암호문 전송과 복호화 비용을 감안하되, 같은 환경에서 2배를 넘는 퇴행은 구현 병목 신호로 본다.

지연 기준은 D8에서 위 수치로 확정했다.
기준을 못 맞추면 먼저 병목을 분리하고 설계를 재검토하며, B를 광역 성능 개선으로 간주하거나 후보 수를 잘라 통과시키지 않는다.
실제 운영 동시성, 사용 가능한 CPU, DB 배치, 허용 transaction 시간과 비용은 `UNKNOWN`이며 실측은 승인된 테스트 계획 이후에 수행한다.

## 선택 이유

- A는 컬럼 암호화 키와 복호화 좌표를 DB 계산 경계로 재전달하지 않는다.
- GLOBAL 기본 범위에서는 B의 셀 노출·covering 복잡도에 비해 후보 축소 이득이 작다.
- C는 DB 내부까지 키 신뢰 경계를 넓힌다[S3]. D는 수치 호환성에는 유리하지만 좌표 재전송과 DB 로그·spill 통제를 추가로 요구한다.
- 기존 암호화 패턴은 참고하되 목적별 키와 타입을 분리해 push 변경 범위를 늘리지 않는다.

## 결과

### 장점

- 두 정확 좌표 컬럼을 키 없는 일반 DB 조회·덤프에서 보호하는 경계를 명시한다.
- 미리보기와 worker가 공유해야 할 계산·정렬 계약과 사람 결정 사항을 함께 남긴다.

### 단점

- A는 공간 인덱스를 잃고 앱 CPU·전송·메모리 비용과 계산 의존성을 추가한다.
- PostGIS와 앱의 경계 수치 일치는 아직 검증되지 않았다.
- 앱 런타임 침해, 과거 평문 백업, 파생 위치 값은 컬럼 암호화만으로 해결되지 않는다.
- 키 회전·이행·복구와 dev·운영 주입에 별도 승인과 운영 책임이 필요하다.

## 관련 자료

- GitHub Issue: #388; 기존 키 보호·주입: #364, #365
- 저장소: `TASK.md`, ADR-0001, ADR-0002, ADR-0008, `docs/reports/infrastructure/gh-364-D-5.md`, `docs/reports/infrastructure/gh-364-D-5-build.md`
- [S1] PostGIS 공식 `ST_Distance` 문서: `https://postgis.net/docs/ST_Distance.html`
- [S2] PostGIS 공식 `ST_Azimuth`, `ST_DWithin` 문서: `https://postgis.net/docs/ST_Azimuth.html`, `https://postgis.net/docs/ST_DWithin.html`
- [S3] PostgreSQL 공식 pgcrypto Security Limitations·Raw Encryption Functions: `https://www.postgresql.org/docs/current/pgcrypto.html`
- [S4] GeographicLib Java 공식 Geodesic source/Javadoc: `https://github.com/geographiclib/geographiclib-java/blob/main/src/main/java/net/sf/geographiclib/Geodesic.java`

문서 확인일은 2026-11-24다.
공식 문서의 일반 동작과 실제 배포된 PostGIS/PROJ 버전의 동작 일치는 구분하며, 실제 배포 버전은 `UNKNOWN`이다.

## 사람 결정 항목

승인자 @Byuntil, 승인일 2026-11-25. D1~D6·D8~D10은 아래 추천안대로 승인했고 D7은 폐기·단일 migration 수정안을 승인했다.
승인 원문: `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/388#issuecomment-7138508452`
댓글 생성 시각은 2026-11-24T16:59:06Z이며 한국 시각으로 2026-11-25다. 문서 최초 작성일은 유지한다.
승인은 설계에 대한 것이며 수치·성능 검증 통과 또는 테스트 계획 승인을 의미하지 않는다.

| ID | 항목 | 선택지 | 추천 | 이유 |
| --- | --- | --- | --- | --- |
| D1 | 계산·신뢰 경계 | A / B / C / D | A 조건부 채택 | 키와 복호화 좌표를 DB로 전달하지 않되 수치·성능 검증을 채택 조건으로 둔다. |
| D2 | 앱 측 계산 의존성 | GeographicLib Java / 자체 구현 / D로 전환 | GeographicLib Java 버전 고정 후 추가 | 회전타원체 계산을 자체 재구현하지 않고 공식 알고리즘을 사용한다. 버전은 UNKNOWN이다. |
| D3 | 동등성 기준 | 제안 오차와 정확 집합·순서 / 정책 변경 / D 재검토 | 거리 0.001m·각도 0.0000001도 및 집합·순서·개수 정확 일치 | 수치 오차를 후보 정책 변경으로 숨기지 않는다. |
| D4 | 키·envelope | push 키 공유 / 좌표 전용 키; header-only / 행 결합 AAD | 좌표 전용 AES 키와 행 결합 AAD | 키 유출 범위와 행 사이 암호문 교체 위험을 분리한다. |
| D5 | 환경 주입 | fixture / SSM / 운영 I2 연계 | local·test·integration fixture, dev 별도 SSM, 운영 I2 합의 | 기존 push 주입 경로를 참고하되 새 secret 준비를 완료로 오인하지 않는다. |
| D6 | 회전·복구 | current/previous / 다중 역사 키 | 두 슬롯과 이전 키 잔존 확인, 추가 회전 전 재암호화 | 기존 패턴을 유지하되 남은 행·백업을 읽지 못하는 회전을 막는다. |
| D7 | 평문 이행 | 재암호화 / 폐기·재수집 | 승인 수정안: 기존 평문 폐기와 단일 Flyway migration | 운영 DB가 없으며 dev 좌표를 보존할 필요가 없다. presence는 재수집하고 audience 메타데이터는 보존하며 원점을 잃은 미완료 글은 MATCH_FAILED로 종결한다. |
| D8 | 성능 예산 | 제안 규모·지연 / 운영 수치 재지정 | 10,000명 기준 제안 예산부터 측정 | 실측 없이 성능을 보장하지 않으며 100,000명 한계도 드러낸다. |
| D9 | 파생 위치 보호 | 현상 유지 / 암호화 / 저정밀화 | 별도 Issue에서 암호화 또는 저정밀화 결정 | 원점 결합 복원 위험이 있어 #388 완료와 전체 위치 보호를 구분한다. |
| D10 | 저정밀 셀 도입 | 새 셀 인덱스 도입 / 도입 보류 | B 도입 보류 | 광역 기본 범위에서는 이득이 작고 셀 해상도·노출 승인이 없다. |
