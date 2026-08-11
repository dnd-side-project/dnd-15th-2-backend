# Direction Communication Schema Manifest

> GitHub Issue: #53
>
> Snapshot date: 2026-08-11
>
> Status: V1~V8 적용 이력과 V1~V9 baseline inventory를 보존한다. 현재 작업 브랜치에는
> `V10`, filtering release registry `V11`, Issue #115의 `V12` migration이 존재하며,
> 아래에 `V12` 계약을 추가로 기록했다. V12의 실제 PostgreSQL catalog 검증은 구현·통합 테스트 executor의 책임이다. `V7`(Issue #73, PR #81)이 `device_credential`을
> 먼저 `main`에 merge해 번호를 점유했고, 2026-08-07 스키마 개정(답변 격리 폐기,
> ADR-0002)은 `V8`로 반영했다(Issue #78, 원래 `V7`로 작성했으나 재번호). `V8` 적용
> 검증은 Issue #78에서 한다.
>
> **§5~§12 인벤토리는 2026-08-08(V1~V8) 기준으로 갱신했다.** 2026-08-05(V1+V2 상태)
> 이후 `V3`~`V7`(user_account 비밀번호·낙관적 잠금, operator_credential, Spring
> Session, device_credential)에서 소급 반영하지 않고 남아 있던 공백을 이번에 함께
> 메웠다. 각 항목의 유래(어느 migration이 추가했는지)는 표·목록에 `(V5)`, `(V7)`,
> `(V8)` 식으로 표시했다.
> 근거는 `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java`의
> `EXPECTED_TABLES`/`EXPECTED_INDEXES`/`EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`와
> `catalogMatchesApprovedManifest()`의 제약 개수 assertion이다 — 이 테스트가 매
> 마이그레이션마다 실제 DB catalog와 대조하므로, 이후에도 이 절들이 뒤처지면 그
> 테스트를 1차 대조군으로 다시 맞춘다.
>
> **2026-08-08(Issue #79) 정정**: 이 표의 DBML 행 SHA-256이 저장소에 실제로 커밋된
> 파일과 불일치했다(`3b443c4b…` 기록 vs 실제 파일 `fb39599f…`) — `#78`이 §5~§12
> 인벤토리는 갱신했지만 이 SHA-256 값을 재계산하지 않고 남긴 결함이다. 같은 시점에
> vault 원본도 `post_reaction` 테이블 Note가 08-07 개정(공감 개수 노출 범위 확대)을
> 놓친 것을 뒤늦게 발견해 정정했다. 이번에 저장소 DBML·ERD를 vault의 2026-08-08
> 정정본으로 다시 동기화하고 SHA-256을 재계산했다 — 스키마·제약 변경은 없고 Note
> 문구만 바뀌었으므로 §5~§12 인벤토리는 그대로 유효하다.
>
> **2026-08-08b 재동기화**: 위 `#79` 판 이후 vault DBML이 인증 부록(§6 참고)을 더
> 정합화했다 — `uq_user_account_id_role`/`uq_operator_credential_login_id`/
> `uq_active_device_installation`은 전부 `V5`·`V7`이 이미 만든 실제 제약인데 vault
> DBML의 `indexes{}` 선언이 빠져 있거나 `[unique]` 태그가 누락돼 있던 것을 vault
> 쪽에서 바로잡았다. 이 저장소 DBML·ERD를 그 판으로 다시 byte-for-byte 재동기화하고
> SHA-256을 갱신했다. 스키마·제약 개수는 바뀌지 않는다 — §5~§12는 그대로 유효하다.
> 같은 작업 중 `ck_answer_edit_count_matches_edited_at`(vault) vs
> `ck_answer_edit_count_edited_at`(`V8` 실제)이라는 두 번째 이름 불일치를 발견해
> §3에 기록했다.

## 1. 목적

이 문서는 방향 소통 DBML, 설명 ERD, 독립 실행형 기준 DDL의 출처와
동기화 지점을 고정한다. Issue #36부터는 이 manifest와 ADR을 검토 기준으로
사용한다.

## 2. 권위와 변경 순서

1. 제품·논리 스키마 변경은 `direction_communication.dbml`에 먼저 반영한다.
2. Issue #36에서 생성할 Flyway migration만 실행 DB schema를 변경한다.
3. `DIRECTION_COMMUNICATION_ERD.md`는 DBML과 migration의 설명 문서다.
4. 이미 적용된 Flyway migration은 수정하지 않고 새 versioned migration을 추가한다.
5. DBML, migration, ERD가 충돌하면 임의로 DDL만 고치지 않는다. DBML 변경과
   정책 근거를 먼저 리뷰한 뒤 새 migration으로 반영한다.

## 3. Source snapshot

| Artifact | Repository path or source | SHA-256 | Role |
| --- | --- | --- | --- |
| DBML (2026-08-08b, baseline) | `docs/product/data-model/direction_communication.dbml` | `ef5e9885f9308d1b86946094dfa49f084e2e3ace4094482d8b3e4b3c3dccd13c` | Issue #115 이전 logical schema baseline |
| ERD (2026-08-08b, baseline) | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` | `6d99297f5bf771a48db98f616c3bbeb7282e44311aadf47080214f276dd8c4ac` | Issue #115 이전 explanatory contract baseline |
| DBML (Issue #115, 2026-08-11) | `docs/product/data-model/direction_communication.dbml` | `3e5c9142eeb415ccfd503413ea73a3de74f3ba472ee4c3803e390af1f326213c` | `direction_post.request_fingerprint`, `outbox_event.match_round`·lease fencing, matching partial unique index와 claim index, exact-coordinate payload exclusion을 반영한 현재 working-tree 판 |
| ERD (Issue #115, 2026-08-11) | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` | `8ac5080dd0fd0fd8a5c9e3ac6fba75f58f3ab22d8103eb7d77f12c1543be9a77` | Issue #115의 fingerprint·outbox-as-matching-job·lease fencing·payload 보안 계약을 설명하는 현재 working-tree 판 |
| V12 migration (Issue #115, working branch) | `src/main/resources/db/migration/V12__add_direction_matching_outbox_contract.sql` | `f62f6ad5bfdc88601a3630b10bdc7a48b546d1913152e31ead2fd1bf5a1364be` | 이 문서 변경의 비교 기준. 실제 catalog 적용 검증은 통합 테스트가 소유한다 |
| target DDL (2026-08-08b) | source workspace `docs/sql/direction_communication_ddl.sql` | `98b611c9d5ca8a912a97fb0be77de4ae9c6d31b3982acac04cfb3a44ac47e5a9` | vault의 최신 상태 스크립트. `uq_operator_credential_login_id` UNIQUE INDEX 추가와 헤더 주석 갱신. 이 저장소는 이 판도 어떤 migration의 authoring reference로 쓰지 않았다 |
| DBML (2026-08-08a, 이력) | 위 파일의 2026-08-08a 판 | `2386e15ebcf6eb3f89b093fe3904b9402c41b50fcd4cfbc24ca83f09ff9ea4da` | `post_reaction` Note 정정만 반영하고 인증 부록 정합화(위 08-08b) 이전인 판. `#79`가 만든 판 |
| ERD (2026-08-08a, 이력) | 위 파일의 2026-08-08a 판 | `a8487e35d63d174eb73e3b18fcc26172881e29eefdd74a9dd36329f39070309c` | 위 DBML(08-08a)과 짝을 이루는 판 |
| target DDL (2026-08-08a, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-08a 판 | `ac57f3229a4bc439153c1c3e8b39d37877e064ce07adf01e54254bd7094892c7` | `post_reaction` Note 정정에 더해 `user_account.version`(V4)/`operator_credential`(V5)/`device_credential`(V7)을 처음 반영한 판 |
| DBML (2026-08-07, 이력) | 위 파일의 2026-08-07 판 | `3b443c4bea41a92d4f803e78d004fbe1ca6e1475f7ed6ae8f94fa8cc3121acc1` | `post_reaction` Note 정정 이전 판. 이 표는 이 값을 `현행`으로 잘못 기록한 채 `#78`에서 남아 있었다(§5~§12 갱신 시 SHA-256 재계산을 누락) — `#79`에서 바로잡았다 |
| target DDL (2026-08-07, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-07 판 | `d873908c802b4c3ff73637e3ef4c1ec84862146055f3ff7672e6908947fb2d31` | V8 authoring reference(원래 V7로 작성, `#81`과의 번호 충돌로 재번호). `V8`은 이 판을 기준으로 작성됐고 그 사실은 바뀌지 않는다 |
| DBML (2026-08-04, 이력) | 위 파일의 2026-08-04 판 | `4637f956f9703a8bdc38590957c2e48d60633e6d633beb3f193151b5c4c928f5` | 답변 격리 폐기(ADR-0002) 이전 판 |
| ERD (2026-08-04, 이력) | 위 파일의 2026-08-04 판 | `181604080ecffd58752e2b40bc3008fbdcdfb7736caff00820535ed6ba128886` | 답변 격리 폐기(ADR-0002) 이전 판 |
| target DDL (2026-08-04, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-04 판 | `be8aaee3b4671aa218c78c15bf33d6ade3ad1cfce902dc10d2cbd45b9fe5805f` | V2 authoring reference only |
| V1 원본 DDL (2026-08-03, 이력) | 위 파일의 2026-08-03 판 | `cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7` | `V1__…sql`이 파생된 원본 |

원본 DBML과 ERD는 byte-for-byte로 복사되어 위 checksum과 일치한다. target DDL은
전체 상태 스크립트이므로 migration 경로에 복사하지 않는다. `V8__…sql`은 V7과
target DDL의 차이만 담은 delta로 손으로 작성한다. 이력 행은 보존용이며,
`FlywayMigrationContractTest`가 `V1__…sql`의 sha256을 그 값으로 잠근다.

vault DBML과 target DDL 사이에는 알려진 불일치가 하나 있다. vault의 DBML은
`ck_direction_post_answers_read_at`을 선언하지 않지만, 같은 vault의 target DDL
(따라서 이를 손으로 옮긴 `V2__…sql`)은 이 제약을 선언한다. 이는 vault 원본 자체의
내부 일관성 결함이며 V2나 이 저장소가 보관한 DBML 사본의 오류가 아니다 — 이
저장소의 DBML은 불완전한 원본을 byte-for-byte로 정확히 복사한 것이다. 대조적으로
`ck_post_recipient_skip_pending`은 vault DBML에도 선언되어 있어 이런 불일치가 없다.
2026-08-07 판에서 이 불일치는 다시 확인하지 않았다 — `V8`이 이 제약을 건드리지
않으므로 범위 밖이다.

**2026-08-08b에 새로 발견한 두 번째 불일치**: vault DBML은 `answer` 테이블에
`ck_answer_edit_count_matches_edited_at`이라는 이름의 check를 선언하지만, `V8`이
실제로 만든 제약 이름은 `ck_answer_edit_count_edited_at`이다(§12 참고, 검증 근거는
`FlywayMigrationIntegrationTest`). 검사하는 조건(`(edit_count = 0) = (edited_at IS
NULL)`)은 동일하고 이름만 다르다. 앞의 `ck_direction_post_answers_read_at` 사례와
같은 종류의 결함 — vault DBML의 표현 오류이며 `V8`이나 이 저장소가 보관한 DBML
사본의 오류가 아니다. 임의로 고치지 않고 기록만 해둔다.

## 4. 폐기된 계보

다음 파일은 중간 설계 이력이며 새 Flyway migration의 입력이 아니다.

- `001_create_direction_communication_schema.sql`
- `002_add_topic_generation_schema.sql`
- `003_add_region_code_master.sql`
- `004_add_user_demographic.sql`

이 계보를 이어서 실행하거나 Flyway baseline으로 이름만 바꾸는 작업은 금지한다.

## 5. Baseline summary

V1~V9(2026-08-08) 전체를 반영한다. `V7`(#81, `device_credential`)과 `V8`(#78, 답변
열람 범위 확대)은 서로 다른 테이블을 다뤄 내용은 겹치지 않지만, `V8`이 원래 `V7`로
작성됐다가 `V7` 번호 충돌로 재번호된 이력이 있다 — Flyway 카탈로그 카운트에는 영향이
없다. 표는 `FlywayMigrationIntegrationTest`의
`EXPECTED_TABLES`/`EXPECTED_INDEXES`/`EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`와
`catalogMatchesApprovedManifest()`의 `countConstraints` assertion으로 검증된 값과
일치한다.

| Object | Count | Notes |
| --- | ---: | --- |
| DBML enums | 28 | SQL에서는 `VARCHAR + CHECK`로 표현. `V3`~`V7`(운영자 인증, Spring Session, 기기 자격증명)은 vault DBML이 다루는 범위 밖이라 이 수치에 영향이 없다 |
| Tables | 32 | 모든 테이블에 논리 PK 존재. `V5`가 `operator_credential`, `V6`이 `spring_session`/`spring_session_attributes`, `V7`이 `device_credential`을 추가 |
| Primary keys | 32 | 28개는 단일 컬럼 inline, 4개는 명시적으로 이름 붙인 복합 PK(`pk_user_block`, `pk_notification_preference`, `pk_post_reaction`, `pk_answer_reaction`) |
| Foreign keys | 52 | named `fk_*` constraints. `V5`가 `fk_operator_credential_user`, `V6`이 `spring_session_attributes_fk`, `V7`이 `fk_device_credential_user`, `V9`가 `fk_user_account_country`를 추가 |
| Unique constraints | 21 | named `uq_*` constraints. `V5`가 `uq_user_account_id_role`, `uq_operator_credential_login_id`, `V9`가 `uq_region_code_code_level`을 추가. `V7`은 named unique 제약이 아니라 `CREATE UNIQUE INDEX` 2개를 추가해 이 수치에 영향이 없다 |
| Unique indexes | 12 | `CREATE UNIQUE INDEX`로 만든 것만 센다(named unique 테이블 제약이 만드는 인덱스는 위 "Unique constraints"에서 센다). `V6`이 `spring_session_ix1`, `V7`이 `uq_device_credential_secret`/`uq_active_device_installation`, `V12`가 `uq_outbox_event_direction_matching_round`를 추가 |
| Check constraints | 113 | named `ck_*` constraints. `V3`이 추가한 `ck_user_account_password_hash`는 `V5`가 제거해 순증감 없음. `V5`가 operator_credential 관련 4개, `V7`이 device_credential 관련 4개, `V8`이 6개(방향·거리·수정 이력 컬럼), `V9`가 USER 국가 필수·국가 코드 형식 2개를 추가 |
| Non-unique indexes | 47 | GiST, partial, sort-order index 포함. `V6`이 `spring_session_ix2`, `spring_session_ix3`, `V7`이 `device_credential_user_idx`, `V9`가 `user_account_country_idx`, `V12`가 `outbox_event_claim_idx`를 추가 |
| Functions | 11 | trigger support functions. `V8`이 `enforce_answer_reaction_reactor_is_sender`를 `enforce_answer_reaction_reactor_can_view`로 교체(개수 불변) |
| Triggers | 10 | 2 regular + 8 constraint triggers. `V8`이 `ct_answer_reaction_reactor_is_sender`를 `ct_answer_reaction_reactor_can_view`로 교체(개수 불변) |
| Extensions | 1 | `postgis` |

"Unique indexes"(12) + "Non-unique indexes"(47) = 59이며, `uq_user_account_id_role`/
`uq_operator_credential_login_id`가 만드는 인덱스 2개는 "Unique constraints"에서만
센다. `FlywayMigrationIntegrationTest`의 `EXPECTED_INDEXES.hasSize(62)`는 이 3개를
포함해 세므로(pg_indexes catalog는 제약이 만든 인덱스와 `CREATE INDEX`로 만든 인덱스를
구분하지 않는다) 62 = 59 + 3다. 두 표가 다른 숫자를 보여주는 것은 오류가 아니라
분류 기준의 차이다.

## 5.1 Issue #115 비동기 매칭 delta (V12)

아래 항목은 V1~V9 baseline 이후 Issue #115에서 추가된 논리·물리 계약이다. 이
문서는 현재 작업 브랜치의 `V12__add_direction_matching_outbox_contract.sql`과
DBML/ERD를 대조해 기록하며, 실제 PostgreSQL catalog 적용 여부는 통합 테스트에서
확정한다. lease duration과 retry backoff의 숫자값은 application configuration의
책임이므로 manifest에 기록하지 않는다.

| 대상 | 계약 |
| --- | --- |
| `direction_post.request_fingerprint` | `VARCHAR(80)`, legacy 행의 `NULL`을 허용한다. 새 제출은 `v1:SHA-256` fingerprint를 저장하고, 기존 행은 첫 idempotency 재시도에서 저장된 의도를 복원할 수 있을 때 lazy backfill한다. 복원할 수 없는 legacy 행은 기존 결과를 반환하고 reconciliation 대상으로 남긴다. |
| `outbox_event.match_round` | `aggregate_type = 'DIRECTION_POST'`이고 `event_type = 'RECIPIENT_MATCH_REQUESTED'`인 행에만 필수다. 초기 매칭은 `1`이며 retry/reclaim은 round를 증가시키지 않는다. 별도 `matching_job` 테이블 없이 이 Outbox row 자체를 matching job으로 취급한다. |
| `uq_outbox_event_direction_matching_round` | `(aggregate_id, match_round, event_type)` partial unique index. 방향글의 같은 matching round/event 작업을 한 번만 생성한다. |
| `outbox_event.lease_owner` / `lease_expires_at` / `lease_generation` | `PROCESSING`일 때 owner와 expiry가 함께 존재하고, 그 외 상태에서는 둘 다 `NULL`이다. generation은 claim/reclaim마다 증가하는 monotonic fencing token이다. stale worker 갱신은 id·PROCESSING·owner·generation·유효 lease 조건이 모두 맞을 때만 허용한다. |
| `outbox_event_claim_idx` | `(status, next_attempt_at, lease_expires_at, id)` claim index. due PENDING/FAILED와 만료된 PROCESSING을 찾는 경로이며, due 판정과 row 점유는 한 transaction에서 처리한다. |
| matching payload | `postId`, `matchRound`, `eventType`, `requestFingerprint`와 coarse 식별자만 저장한다. 정확 좌표, `PostGIS point`, WKB/GeoJSON 등 좌표를 복원할 수 있는 값은 저장하지 않는다. |

### V12 constraint/index inventory

- Check: `ck_outbox_event_match_round`, `ck_outbox_event_lease_generation`, `ck_outbox_event_lease_state`
- Partial unique index: `uq_outbox_event_direction_matching_round`
- Claim index: `outbox_event_claim_idx`
- 기존 `outbox_event_dispatch_idx`와 `uq_outbox_event_dedup`는 유지한다.

## 6. Table inventory

- `region_code`
- `user_account`
- `user_private_attribute`
- `active_user_presence`
- `recipient_receive_state`
- `question_proposal`
- `question_proposal_review`
- `approved_question`
- `question_assignment_cycle`
- `question_assignment`
- `direction_scheme`
- `direction_segment`
- `media_asset`
- `direction_post`
- `post_audience`
- `post_recipient`
- `answer`
- `post_reaction`
- `answer_reaction`
- `media_attachment`
- `user_block`
- `report`
- `moderation_review`
- `push_device`
- `notification_preference`
- `outbox_event`
- `notification`
- `notification_delivery`
- `operator_credential` (`V5`)
- `spring_session` (`V6`)
- `spring_session_attributes` (`V6`)
- `device_credential` (`V7`, `#81`)

## 7. Function inventory

- `enforce_question_text_immutability`
- `enforce_direction_post_question_active`
- `enforce_post_recipient_not_sender`
- `enforce_post_recipient_capacity_release`
- `assert_post_has_content`
- `assert_answer_has_content`
- `enforce_post_has_content`
- `enforce_answer_has_content`
- `enforce_media_attachment_preserves_content`
- `enforce_media_status_preserves_content`
- `enforce_answer_reaction_reactor_can_view` (`V8`, `enforce_answer_reaction_reactor_is_sender`에서 교체)

## 8. Trigger inventory

- `tr_approved_question_text_immutable`
- `tr_question_proposal_text_immutable_after_submit`
- `ct_direction_post_question_active`
- `ct_post_recipient_not_sender`
- `ct_post_recipient_capacity_release`
- `ct_direction_post_has_content`
- `ct_answer_has_content`
- `ct_media_attachment_preserves_content`
- `ct_media_status_preserves_content`
- `ct_answer_reaction_reactor_can_view` (`V8`, `ct_answer_reaction_reactor_is_sender`에서 교체)

## 9. Index inventory

- `uq_media_attachment_post_order`
- `uq_media_attachment_answer_order`
- `region_code_parent_idx`
- `active_user_presence_position_gix`
- `active_user_presence_expiry_idx`
- `approved_question_active_idx`
- `uq_direction_scheme_active`
- `user_account_region_idx`
- `user_account_country_idx` (`V9`)
- `active_user_presence_region_idx`
- `question_proposal_proposer_idx`
- `question_proposal_review_reviewer_idx`
- `approved_question_approver_idx`
- `media_asset_owner_idx`
- `direction_post_question_idx`
- `direction_post_region_idx`
- `post_audience_segment_idx`
- `post_recipient_region_idx`
- `answer_region_idx`
- `report_target_user_idx`
- `report_direction_post_idx`
- `report_answer_idx`
- `report_queue_idx`
- `moderation_review_report_idx`
- `moderation_review_reviewer_idx`
- `push_device_user_idx`
- `notification_preference_user_idx`
- `notification_outbox_idx`
- `notification_post_idx`
- `notification_answer_idx`
- `notification_delivery_device_idx`
- `question_proposal_review_queue_idx`
- `question_proposal_review_history_idx`
- `question_assignment_history_idx`
- `direction_post_sender_idx`
- `direction_post_expiry_idx`
- `post_recipient_inbox_idx`
- `post_recipient_capacity_idx`
- `recipient_receive_selection_idx`
- `answer_recipient_idx`
- `user_block_reverse_idx`
- `uq_open_report_user`
- `uq_open_report_post`
- `uq_open_report_answer`
- `uq_active_push_token`
- `outbox_event_dispatch_idx`
- `uq_outbox_event_direction_matching_round` (V12, partial unique)
- `outbox_event_claim_idx` (V12)
- `notification_inbox_idx`
- `notification_delivery_dispatch_idx`
- `uq_answer_one_per_recipient` (`V8`에서 조건 축소, `status <> 'REJECTED'`)
- `post_reaction_reactor_idx`
- `answer_reaction_reactor_idx`
- `spring_session_ix1` (`V6`)
- `spring_session_ix2` (`V6`)
- `spring_session_ix3` (`V6`)
- `uq_device_credential_secret` (`V7`, `#81`)
- `uq_active_device_installation` (`V7`, `#81`, partial unique — `credential_status = 'ACTIVE'`)
- `device_credential_user_idx` (`V7`, `#81`, partial index 동일 조건)

## 10. Foreign-key constraint inventory

- `fk_region_code_parent`
- `fk_user_account_region`
- `fk_user_private_attribute_user`
- `fk_active_user_presence_user`
- `fk_active_user_presence_region`
- `fk_recipient_receive_state_user`
- `fk_question_proposal_proposer`
- `fk_question_proposal_review_proposal`
- `fk_question_proposal_review_reviewer`
- `fk_approved_question_source_proposal`
- `fk_approved_question_approver`
- `fk_question_assignment_cycle_user`
- `fk_question_assignment_cycle`
- `fk_question_assignment_question`
- `fk_direction_segment_scheme`
- `fk_media_asset_owner`
- `fk_direction_post_sender`
- `fk_direction_post_question`
- `fk_direction_post_region`
- `fk_post_audience_post`
- `fk_post_audience_segment`
- `fk_post_recipient_post`
- `fk_post_recipient_user`
- `fk_post_recipient_region`
- `fk_answer_recipient_author`
- `fk_answer_region`
- `fk_media_attachment_asset_owner`
- `fk_media_attachment_post_owner`
- `fk_media_attachment_answer_owner`
- `fk_user_block_blocker`
- `fk_user_block_blocked`
- `fk_report_reporter`
- `fk_report_target_user`
- `fk_report_direction_post`
- `fk_report_answer`
- `fk_moderation_review_report`
- `fk_moderation_review_reviewer`
- `fk_push_device_user`
- `fk_notification_preference_user`
- `fk_notification_recipient`
- `fk_notification_outbox`
- `fk_notification_post`
- `fk_notification_answer`
- `fk_notification_delivery_notification`
- `fk_notification_delivery_device`
- `fk_post_reaction_recipient`
- `fk_answer_reaction_answer`
- `fk_answer_reaction_user`
- `fk_operator_credential_user` (`V5`)
- `spring_session_attributes_fk` (`V6`)
- `fk_device_credential_user` (`V7`, `#81`, `ON DELETE CASCADE`)
- `fk_user_account_country` (`V9`, `country_code`와 생성 `country_level`의 복합 FK)

## 11. Unique-constraint inventory

- `uq_approved_question_source_proposal`
- `uq_question_assignment_cycle_user_key`
- `uq_question_assignment_cycle_question`
- `uq_question_assignment_cycle_order`
- `uq_direction_scheme_code_version`
- `uq_direction_segment_key`
- `uq_direction_segment_order`
- `uq_media_asset_id_owner`
- `uq_media_asset_storage_key`
- `uq_direction_post_idempotency`
- `uq_direction_post_id_sender`
- `uq_post_recipient_post_user`
- `uq_post_recipient_id_user`
- `uq_answer_idempotency`
- `uq_answer_id_author`
- `uq_outbox_event_dedup`
- `uq_notification_recipient_dedup`
- `uq_notification_delivery_device`
- `uq_user_account_id_role` (`V5`)
- `uq_operator_credential_login_id` (`V5`)
- `uq_region_code_code_level` (`V9`)

partial unique object는 위 Index inventory에 포함된다. 반대로 이 절의 named unique
테이블 제약이 만드는 인덱스는 Index inventory에 다시 넣지 않는다 — §5 표 아래 설명
참고.

## 12. Check-constraint inventory

- `ck_region_code_not_self_parent`
- `ck_region_code_display_name`
- `ck_region_code_level`
- `ck_region_code_root`
- `ck_user_account_role`
- `ck_user_account_status`
- `ck_user_account_nickname`
- `ck_user_account_deleted_at`
- `ck_user_account_country_code` (`V9`)
- `ck_user_account_user_country` (`V9`)
- `ck_user_private_attribute_gender`
- `ck_user_private_attribute_age_band`
- `ck_active_user_presence_location`
- `ck_active_user_presence_accuracy`
- `ck_active_user_presence_expiry`
- `ck_recipient_receive_state_active_count`
- `ck_recipient_receive_state_recent_count`
- `ck_recipient_receive_state_last_received`
- `ck_question_proposal_status`
- `ck_question_proposal_text`
- `ck_question_proposal_submission`
- `ck_question_proposal_review_decision`
- `ck_question_proposal_review_reason`
- `ck_approved_question_source_type`
- `ck_approved_question_source`
- `ck_approved_question_status`
- `ck_approved_question_text`
- `ck_approved_question_answer_format`
- `ck_approved_question_active_range`
- `ck_approved_question_approval`
- `ck_question_assignment_cycle_status`
- `ck_question_assignment_cycle_range`
- `ck_question_assignment_display_order`
- `ck_question_assignment_viewed_at`
- `ck_question_assignment_used_at`
- `ck_direction_scheme_version`
- `ck_direction_scheme_type`
- `ck_direction_scheme_segment_count`
- `ck_direction_scheme_start_offset`
- `ck_direction_scheme_status`
- `ck_direction_segment_center`
- `ck_direction_segment_width`
- `ck_direction_segment_order`
- `ck_media_asset_status`
- `ck_media_asset_moderation`
- `ck_media_asset_size`
- `ck_media_asset_deleted_at`
- `ck_direction_post_status`
- `ck_direction_post_moderation`
- `ck_direction_post_body`
- `ck_direction_post_expiry`
- `ck_direction_post_published_at`
- `ck_direction_post_deleted_at`
- `ck_direction_post_answers_read_at`
- `ck_post_audience_center`
- `ck_post_audience_width`
- `ck_post_audience_distance`
- `ck_post_audience_origin`
- `ck_post_recipient_status`
- `ck_post_recipient_bearing`
- `ck_post_recipient_distance_band`
- `ck_post_recipient_timestamps`
- `ck_post_recipient_status_timestamps`
- `ck_post_recipient_skip_pending`
- `ck_answer_status`
- `ck_answer_moderation`
- `ck_answer_body`
- `ck_answer_bearing`
- `ck_answer_distance_band`
- `ck_answer_published_at`
- `ck_answer_deleted_at`
- `ck_media_attachment_exactly_one_target`
- `ck_media_attachment_order`
- `ck_user_block_not_self`
- `ck_user_block_release`
- `ck_report_exactly_one_target`
- `ck_report_reason`
- `ck_report_status`
- `ck_report_resolution`
- `ck_moderation_review_decision`
- `ck_moderation_review_action_type`
- `ck_push_device_platform`
- `ck_push_device_status`
- `ck_push_device_revoked_at`
- `ck_notification_preference_type`
- `ck_notification_preference_quiet_hours`
- `ck_outbox_event_aggregate_type`
- `ck_outbox_event_event_type`
- `ck_outbox_event_payload`
- `ck_outbox_event_status`
- `ck_outbox_event_attempt_count`
- `ck_outbox_event_processed_at`
- `ck_outbox_event_match_round` (V12)
- `ck_outbox_event_lease_generation` (V12)
- `ck_outbox_event_lease_state` (V12)
- `ck_notification_type`
- `ck_notification_target`
- `ck_notification_status`
- `ck_notification_read_at`
- `ck_notification_delivery_status`
- `ck_notification_delivery_attempt_count`
- `ck_notification_delivery_sent_at`
- `ck_operator_credential_role` (`V5`)
- `ck_operator_credential_login_id` (`V5`)
- `ck_operator_credential_failed_attempt` (`V5`)
- `ck_operator_credential_locked_until` (`V5`)
- `ck_device_credential_platform` (`V7`, `#81`)
- `ck_device_credential_status` (`V7`, `#81`)
- `ck_device_credential_revoked_at` (`V7`, `#81`)
- `ck_device_credential_installation_id` (`V7`, `#81`)
- `ck_post_recipient_inbound_bearing` (`V8`)
- `ck_post_recipient_distance_m` (`V8`)
- `ck_post_recipient_answers_read_at` (`V8`)
- `ck_answer_distance_m` (`V8`)
- `ck_answer_edit_count` (`V8`)
- `ck_answer_edit_count_edited_at` (`V8`)

`V3`이 추가한 `ck_user_account_password_hash`는 `V5`가 운영자 자격증명을
`operator_credential`로 분리하며 제거했다 — 이 목록에는 없다.

## 13. 구현 전 결정과 명시적 제외

| Topic | Issue #35 contract | Enforcement timing |
| --- | --- | --- |
| 인증·신원 매핑 | 현재 baseline은 내부 `user_account.id`만 가진다. 외부 IdP subject 매핑은 인증 Issue의 새 migration으로 추가하며 이메일을 식별자로 추정하지 않는다. | 인증 구현 전 별도 승인 |
| P04 만료 | `direction_post.expires_at`은 필수지만 기간 기본값은 두지 않는다. 서버가 승인된 제품 정책으로 계산한 절대 시각을 저장한다. | 제품 기간 확정 후 application policy |
| P07 보관·삭제 | 보관 기간, 익명화, 물리 삭제 job을 baseline에 넣지 않는다. 상태/soft-delete 필드와 FK로 참조만 보존한다. | Privacy/Security 승인 후 별도 Issue |
| `updated_at` | JPA 쓰기는 auditing, JDBC 쓰기는 SQL에서 명시적으로 갱신한다. DB default는 insert fallback이며 범용 update trigger를 추가하지 않는다. | Issues #36-#40 |
| FK 삭제 | 기준 DDL의 명시적 CASCADE/RESTRICT/SET NULL을 유지한다. 제품 경로에서 hard delete API를 만들지 않고 상태 전이를 우선한다. | P07 확정 시 재검토 |
| `region_code` seed | table과 FK만 baseline에 포함한다. 출처·버전이 승인되기 전 seed data를 넣지 않는다. | 지역 데이터 출처 승인 후 migration |
| 방향 coverage | 8개 45° half-open sector `[start, end)`와 0°/360° 정규화를 기준으로 한다. Issue #36은 고정 scheme seed를, Issue #39는 모든 경계값 테스트를 소유한다. | Issues #36, #39 |
| PostGIS extension | local/test migration은 `CREATE EXTENSION IF NOT EXISTS postgis`를 검증한다. production은 플랫폼이 extension과 migration-role 권한을 사전 준비해야 한다. | Issue #36 local/test only |

## 14. Issue handoff

- Issue #36: Flyway baseline과 빈 PostgreSQL/PostGIS 재현
- Issue #37: JPA 공통 규칙과 Account 첫 수직 슬라이스
- Issue #38: Question persistence
- Issue #39: Direction/PostGIS persistence
- Issue #40: Answer/Safety/Notification persistence
- Issue #73 (PR #81): `V7` — `device_credential` 테이블, 앱 사용자 기기 자격증명과
  액세스 토큰 발급. `main`에 먼저 merge되어 `V7` 번호를 점유했다
- Issue #78: `V8` — 2026-08-07 스키마 개정(답변 격리 폐기, ADR-0002) 반영. `answer_reaction`
  복합 PK 전환과 `ct_answer_reaction_reactor_can_view` 자격 트리거, `post_recipient`/
  `answer`의 방향·거리·수정 이력 컬럼과 백필, `uq_answer_one_per_recipient` 조건 축소.
  원래 `V7`로 작성했으나 #81과의 번호 충돌로 `V8`로 재번호했다
