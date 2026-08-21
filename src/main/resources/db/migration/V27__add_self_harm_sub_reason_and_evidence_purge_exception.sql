-- #157: 법률·안전 검토 결정 반영.
--
-- 1) 자해·자살 위험 하위 사유 추가. CSAM·NCII·CREDIBLE_THREAT와 동일하게
--    즉시 대응이 필요한 CRITICAL 심각도로 취급한다(ReportCaseSeverity.of).
ALTER TABLE report DROP CONSTRAINT ck_report_sub_reason;
ALTER TABLE report ADD CONSTRAINT ck_report_sub_reason
    CHECK (
        sub_reason_code IS NULL
        OR (reason_code = 'SEXUAL_CONTENT' AND sub_reason_code IN ('CSAM', 'NCII'))
        OR (reason_code = 'VIOLENCE_OR_THREAT' AND sub_reason_code = 'CREDIBLE_THREAT')
        OR (reason_code = 'ILLEGAL_OR_DANGEROUS' AND sub_reason_code = 'SELF_HARM_RISK')
    );

-- 2) 증거 스냅샷 append-only 트리거(V19)는 UPDATE·DELETE를 전부 거부해
--    purge_after 만료 스냅샷의 미디어조차 정리할 수 없었다. 본문·해시 등
--    증거 자체는 여전히 절대 불변으로 유지하되, media_object_keys만
--    비우는 좁은 예외 하나만 허용하도록 트리거 함수를 교체한다.
--    legal_hold 행은 이 예외에서도 제외된다. report_case_event 트리거는
--    이 변경과 무관하게 기존 enforce_report_evidence_immutability()를
--    그대로 쓴다(완전 append-only 유지).
-- 컬럼 목록을 명시적으로 나열해 "media_object_keys만" 허용을 강제한다. 이
-- 테이블에 컬럼을 추가하면 이 IF 조건에도 그 컬럼을 함께 추가해야 한다 —
-- 빠뜨리면 새 컬럼이 이 예외를 거치지 않고 조용히 변경 가능해진다.
CREATE FUNCTION enforce_report_snapshot_immutability_except_media_purge()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'report_content_snapshot is append-only and cannot be deleted'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.report_id IS DISTINCT FROM OLD.report_id
        OR NEW.captured_at IS DISTINCT FROM OLD.captured_at
        OR NEW.target_type IS DISTINCT FROM OLD.target_type
        OR NEW.target_id IS DISTINCT FROM OLD.target_id
        OR NEW.author_id IS DISTINCT FROM OLD.author_id
        OR NEW.body_text IS DISTINCT FROM OLD.body_text
        OR NEW.edit_count IS DISTINCT FROM OLD.edit_count
        OR NEW.content_published_at IS DISTINCT FROM OLD.content_published_at
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.legal_hold IS DISTINCT FROM OLD.legal_hold
        OR NEW.purge_after IS DISTINCT FROM OLD.purge_after THEN
        RAISE EXCEPTION
            'report_content_snapshot is append-only except a media purge that clears media_object_keys'
            USING ERRCODE = '23514';
    END IF;

    IF OLD.legal_hold THEN
        RAISE EXCEPTION 'report_content_snapshot under legal_hold cannot be purged'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.media_object_keys <> '{}' THEN
        RAISE EXCEPTION 'report_content_snapshot media purge must clear media_object_keys'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER tr_report_content_snapshot_immutable ON report_content_snapshot;

CREATE TRIGGER tr_report_content_snapshot_immutable
BEFORE UPDATE OR DELETE ON report_content_snapshot
FOR EACH ROW EXECUTE FUNCTION enforce_report_snapshot_immutability_except_media_purge();

COMMENT ON FUNCTION enforce_report_snapshot_immutability_except_media_purge() IS
    '증거 스냅샷은 media_object_keys를 비우는 purge 한 가지 UPDATE만 예외로 허용한다.
     legal_hold 행이거나 그 외 컬럼이 바뀌거나 DELETE면 전부 거부한다(#157).';
