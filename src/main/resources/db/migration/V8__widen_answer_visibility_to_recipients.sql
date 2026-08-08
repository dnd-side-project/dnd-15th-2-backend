-- =============================================================================
-- Qello 방향 소통 schema V8 — 2026-08-07 기능 명세서 개정 반영 (답변 격리 폐기)
--
-- 기준 문서
--   docs/product/data-model/direction_communication.dbml (2026-08-07 개정)
--   docs/adr/0002-답변은-질문글을-받은-사람-모두에게-공개된다.md (vault, superseded 0001 대체)
--
-- 원래 V7로 작성했으나, #81(Issue #73)의 device_credential 마이그레이션이 먼저
-- main에 merge되어 V7 번호를 점유했다. 이 파일은 V8로 재번호했다. 내용은 원래
-- V7 delta와 동일하다 — device_credential은 이 파일이 다루는 answer_reaction·
-- post_recipient·answer 테이블과 겹치지 않는다.
--
-- 답변이 질문자 1명 전용 사적 회신에서 그 질문글의 수신 자격자 전원 공개로
-- 바뀐다. V7(#81의 device_credential 포함)까지는 수정하지 않는다. 이 파일은
-- 그 상태와 목표 상태의 차이만 담은 delta다. extension을 만들지 않으므로
-- Flyway 기본 트랜잭션 안에서 실행한다.
--
-- 이 파일이 구현하지 않는 것
--   "지금 이 답변을 볼 수 있는가"는 넘김·만료로 자격을 잃는 시간 의존 판정이라
--   DB가 아니라 조회 계층이 강제한다(#79). 이 마이그레이션이 강제하는 것은
--   "이 사용자가 그 질문글의 수신자 집합에 속하는가"라는 정적 사실뿐이다.
--
-- 백필 원칙 — 되돌릴 수 없는 값은 되돌리지 않는다
--   inbound_bearing_deg/distance_m은 매칭 시점 좌표에서 파생되는데, 그 좌표
--   (active_user_presence)는 TTL로 이미 사라졌다. 기존 행에는 "올바른" 값이
--   존재하지 않는다. distance_m은 0으로 백필한다 — 근거리 하한(10km) 미만이면
--   조회 계층이 정확 거리 대신 distance_band를 노출하기로 돼 있으므로(§F06
--   거리 표시), 0은 그 하한 규칙을 항상 타서 조회 계층이 조작된 정확 거리를
--   보여줄 길을 원천적으로 막는 유일하게 안전한 상수다. inbound_bearing_deg는
--   대응하는 값이 없어 matched_bearing_deg(발송자 기준)를 단순 180도 반전한
--   근사값으로 채운다 — 구면 역방위 공식과 다르다는 것을 알고 쓰는 최선의
--   근사이며, 이 저장소는 아직 프로덕션 사용자 데이터가 없다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. answer_reaction — 단일 PK를 복합 PK로, 자격 트리거를 "질문자 또는 수신자
--    집합 소속"으로 확장
--
-- answer_id 단독 PK는 "공감할 수 있는 사람이 질문자 한 명"이라는 폐기된 전제로
-- 걸린 것이라, 그대로 두면 두 번째 사람의 공감이 PK 충돌로 실패한다.
-- (answer_id, reactor_id) 복합 PK로 바꾸면 "한 사람은 한 답변에 한 번만"이
-- 여전히 키로 성립하면서 서로 다른 사람의 공감은 별도 행이 된다.
-- -----------------------------------------------------------------------------

ALTER TABLE answer_reaction
    DROP CONSTRAINT answer_reaction_pkey;

ALTER TABLE answer_reaction
    ADD CONSTRAINT pk_answer_reaction
    PRIMARY KEY (answer_id, reactor_id);

DROP TRIGGER ct_answer_reaction_reactor_is_sender ON answer_reaction;
DROP FUNCTION enforce_answer_reaction_reactor_is_sender();

-- answer는 post_id를 직접 갖지 않고 post_recipient를 거쳐 도달하므로 "질문자
-- 또는 수신자 집합 소속"을 복합 FK로 표현할 수 없다. 이 constraint trigger가
-- 강제한다. 자기 답변 금지는 이번에 새로 생긴 규칙이다 — 열람 자격이 전원으로
-- 넓어지며 작성자 본인도 열람 자격자가 되므로, 그 넓어진 자격에서 자기 답변만
-- 따로 빼는 예외가 필요해졌다.
CREATE FUNCTION enforce_answer_reaction_reactor_can_view()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_post_id BIGINT;
    post_sender_id BIGINT;
    answer_author_id BIGINT;
    reactor_can_view BOOLEAN;
BEGIN
    SELECT p.id, p.sender_id, a.author_id
    INTO target_post_id, post_sender_id, answer_author_id
    FROM answer a
    JOIN post_recipient pr ON pr.id = a.post_recipient_id
    JOIN direction_post p ON p.id = pr.post_id
    WHERE a.id = NEW.answer_id;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF NEW.reactor_id = answer_author_id THEN
        RAISE EXCEPTION
            'answer_reaction on answer % must not be created by the answer author %',
            NEW.answer_id, NEW.reactor_id
            USING ERRCODE = '23514';
    END IF;

    reactor_can_view := NEW.reactor_id = post_sender_id
        OR EXISTS (
            SELECT 1 FROM post_recipient
            WHERE post_id = target_post_id AND recipient_id = NEW.reactor_id
        );

    IF NOT reactor_can_view THEN
        RAISE EXCEPTION
            'answer_reaction on answer % must be created by the question author or a recipient of post %, not %',
            NEW.answer_id, target_post_id, NEW.reactor_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_answer_reaction_reactor_can_view
AFTER INSERT OR UPDATE OF answer_id, reactor_id ON answer_reaction
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_answer_reaction_reactor_can_view();

COMMENT ON TABLE answer_reaction IS
    '답변에 남긴 공감. 볼 수 있는 사람(질문자 + 그 질문글의 수신 자격자) 전원이 남길 수 있고 자기 답변에는 남길 수 없다. 답변자가 받는 유일한 반응 신호다. 만료된 질문글의 답변에도 공감할 수 있다 — 만료는 새 답변만 차단한다.';

-- -----------------------------------------------------------------------------
-- 2. post_recipient — 수신자 기준 방향, 정확 거리, 답변 읽음 기준선
--
-- inbound_bearing_deg: 목록·카드에 표시하는 방향은 보는 사람 기준이다.
-- matched_bearing_deg(발송자가 보낸 방향)를 그대로 쓰면 방향이 뒤집혀 보인다.
-- 구간 키는 저장하지 않는다 — 라벨은 조회 시점에 ACTIVE direction_scheme으로
-- 파생시켜야 8방향에서 16방향으로 바뀔 때 데이터 마이그레이션 없이 재분류된다.
--
-- distance_m: 카드와 답변에 정확 거리를 표시하기로 확정됐다. distance_band는
-- 폐기하지 않고 근거리 하한(10km) 미만 표시용으로 남는다.
--
-- answers_read_at: direction_post.answers_read_at은 질문자 전용이다. 답변이
-- 전원에게 공개되며 사람마다 마지막으로 본 시점이 달라져, 수신자별 기준선이
-- 따로 필요하다. NULL 허용이며 신규 컬럼이라 백필 없이 NULL로 시작한다.
-- -----------------------------------------------------------------------------

ALTER TABLE post_recipient
    ADD COLUMN inbound_bearing_deg NUMERIC(7, 3),
    ADD COLUMN distance_m BIGINT,
    ADD COLUMN answers_read_at TIMESTAMPTZ;

-- 백필 근거는 파일 상단 주석 참고. 신규 행은 애플리케이션이 매칭 시점에 실제
-- 값을 채우므로 이 UPDATE는 V8 적용 시점에 이미 있던 행에만 적용된다.
UPDATE post_recipient
SET inbound_bearing_deg = MOD(matched_bearing_deg + 180, 360),
    distance_m = 0
WHERE inbound_bearing_deg IS NULL;

ALTER TABLE post_recipient
    ALTER COLUMN inbound_bearing_deg SET NOT NULL,
    ALTER COLUMN distance_m SET NOT NULL;

ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_inbound_bearing
    CHECK (inbound_bearing_deg >= 0 AND inbound_bearing_deg < 360);

ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_distance_m
    CHECK (distance_m >= 0);

ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_answers_read_at
    CHECK (answers_read_at IS NULL OR answers_read_at >= matched_at);

COMMENT ON COLUMN post_recipient.inbound_bearing_deg IS
    '수신자 위치를 원점으로 계산한 역방위 매칭 시점 스냅샷. 목록·카드가 표시하는 방향은 언제나 이 값에서 파생한다. 구간 라벨은 저장하지 않고 조회 시점의 ACTIVE direction_scheme으로 파생시킨다.';

COMMENT ON COLUMN post_recipient.distance_m IS
    '발송 원점에서 수신자까지의 정확한 거리(미터) 매칭 시점 스냅샷. 근거리 하한(10km) 미만이면 이 값 대신 distance_band를 노출한다.';

COMMENT ON COLUMN post_recipient.answers_read_at IS
    '이 수신자가 해당 질문글의 답변 목록을 마지막으로 확인한 시각. 새 답변 n개 배지의 수신자별 기준선이다.';

-- -----------------------------------------------------------------------------
-- 3. answer — 정확 거리와 수정 이력
--
-- distance_m: post_recipient.distance_m과 같은 값이어야 하며 답변 옆에 정확
-- 거리를 표시하는 근거다. 백필 근거는 파일 상단 주석과 동일하다.
--
-- edited_at/edit_count: 답변 수정이 허용됐다. 공감은 수정에도 승계되므로
-- 무제한이면 "공감을 모은 뒤 반복 교체"가 사고가 아니라 기능이 된다. 실효
-- 상한(초기값 3)은 운영 설정값으로 애플리케이션이 강제하고, DB에는 어떤
-- 설정에서도 넘을 수 없는 안전 상한 10만 둔다 — recipient_receive_state의
-- 수신 상한과 같은 방식이다. 두 컬럼의 동치(수정 이력이 있으면 반드시 시각이
-- 있다)는 CHECK로 즉시 강제한다.
-- -----------------------------------------------------------------------------

ALTER TABLE answer
    ADD COLUMN distance_m BIGINT,
    ADD COLUMN edited_at TIMESTAMPTZ,
    ADD COLUMN edit_count INTEGER;

-- 백필 근거는 파일 상단 주석 참고.
UPDATE answer
SET distance_m = 0,
    edit_count = 0
WHERE distance_m IS NULL;

-- ct_answer_has_content는 post_recipient의 두 constraint trigger와 달리 컬럼 제한 없이
-- AFTER INSERT OR UPDATE 전체에 걸린다. 바로 위 백필 UPDATE가 큐에 넣은 미확정(pending)
-- 이벤트를 이 transaction 안에서 즉시 처리해 비우지 않으면, 뒤따르는 ALTER TABLE answer가
-- "cannot ALTER TABLE answer because it has pending trigger events"로 거부된다. 이
-- 백필은 status·body_text를 건드리지 않으므로 즉시 평가해도 결과는 원래 커밋 시점
-- 평가와 같다 — 더 이르게, 같은 결론을 낼 뿐이다.
SET CONSTRAINTS ct_answer_has_content IMMEDIATE;

ALTER TABLE answer
    ALTER COLUMN distance_m SET NOT NULL,
    ALTER COLUMN edit_count SET NOT NULL,
    ALTER COLUMN edit_count SET DEFAULT 0;

ALTER TABLE answer
    ADD CONSTRAINT ck_answer_distance_m
    CHECK (distance_m >= 0);

ALTER TABLE answer
    ADD CONSTRAINT ck_answer_edit_count
    CHECK (edit_count BETWEEN 0 AND 10);

ALTER TABLE answer
    ADD CONSTRAINT ck_answer_edit_count_edited_at
    CHECK ((edit_count = 0) = (edited_at IS NULL));

COMMENT ON COLUMN answer.distance_m IS
    '질문자에서 답변자까지의 정확한 거리(미터) 매칭 시점 스냅샷. post_recipient.distance_m과 같은 값이어야 한다. 근거리 하한(10km) 미만이면 이 값 대신 distance_band를 노출한다.';

COMMENT ON COLUMN answer.edited_at IS
    '수정된 내용이 검토를 통과해 실제로 반영된 시각. 값이 있으면 화면에 수정됨을 표시한다.';

COMMENT ON COLUMN answer.edit_count IS
    '검토를 통과해 실제로 반영된 수정 횟수. 거절된 수정은 세지 않는다. 실효 상한은 운영 설정값(초기 3)이며 이 CHECK는 그보다 넉넉한 안전 상한 10만 강제한다.';

-- -----------------------------------------------------------------------------
-- 4. uq_answer_one_per_recipient — DELETED를 제외 목록에서 뺀다
--
-- 원래는 REJECTED·DELETED 둘 다 제외해 자리를 비켜줬다. "삭제 후 재작성
-- 불가"가 확정되며 DELETED를 열어두면 삭제→재작성이 사실상 무제한 수정이 되어
-- 수정 정책(검토를 통과해야 반영)이 무의미해진다. REJECTED만 자리를 비켜주는
-- 이유는 운영이 거절한 것은 사용자 잘못이 아니기 때문이다.
--
-- 실패 시 대응: 이 인덱스 재생성이 실패하면 같은 post_recipient_id에 DELETED가
-- 아니면서 status <> 'REJECTED'인 답변이 이미 둘 이상 있다는 뜻이다.
-- 마이그레이션이 데이터를 임의로 지우지 않는다. 아래 쿼리로 중복을 찾아 제품
-- 판단으로 정리한 뒤 다시 실행한다.
--   SELECT post_recipient_id, count(*)
--   FROM answer WHERE status <> 'REJECTED'
--   GROUP BY post_recipient_id HAVING count(*) > 1;
-- -----------------------------------------------------------------------------

DROP INDEX uq_answer_one_per_recipient;

CREATE UNIQUE INDEX uq_answer_one_per_recipient
    ON answer (post_recipient_id) WHERE status <> 'REJECTED';
