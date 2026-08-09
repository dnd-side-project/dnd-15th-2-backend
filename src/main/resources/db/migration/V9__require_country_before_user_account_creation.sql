-- 일반 사용자 계정 생성 전에 국가를 검증한다(#88, ADR-0007).
-- 국가 미입력 USER를 저장하지 않으며, OPERATOR만 country_code NULL을 허용한다.

ALTER TABLE region_code
    ADD CONSTRAINT uq_region_code_code_level UNIQUE (code, level);

-- 기존 데이터의 국가 코드는 backfill 전까지 길이 제약 없이 보관해
-- 잘못된 지역 계층을 조용히 잘라내지 않고 migration을 실패시킨다.
ALTER TABLE user_account
    ADD COLUMN country_code VARCHAR(100),
    ADD COLUMN country_level VARCHAR(20)
        GENERATED ALWAYS AS ('COUNTRY'::VARCHAR(20)) STORED;

WITH RECURSIVE region_hierarchy (user_id, code, parent_code, level, path) AS (
    SELECT account.id, region.code, region.parent_code, region.level,
           ARRAY[region.code]::VARCHAR(100)[]
    FROM user_account account
    JOIN region_code region ON region.code = account.coarse_region_code
    UNION ALL
    SELECT hierarchy.user_id, parent.code, parent.parent_code, parent.level,
           (hierarchy.path || parent.code::VARCHAR(100))::VARCHAR(100)[]
    FROM region_hierarchy hierarchy
    JOIN region_code parent ON parent.code = hierarchy.parent_code
    WHERE NOT parent.code = ANY(hierarchy.path)
), country_roots AS (
    SELECT user_id, min(code) AS country_code, count(*) AS root_count
    FROM region_hierarchy
    WHERE level = 'COUNTRY'
    GROUP BY user_id
)
UPDATE user_account account
SET country_code = roots.country_code
FROM country_roots roots
WHERE account.id = roots.user_id
  AND roots.root_count = 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_account
        WHERE role = 'USER'
          AND (country_code IS NULL OR country_code !~ '^[A-Z]{2}$')
    ) THEN
        RAISE EXCEPTION
            'V9 country backfill cannot resolve every USER to one ISO alpha-2 COUNTRY';
    END IF;
END
$$;

ALTER TABLE user_account
    ALTER COLUMN country_code TYPE VARCHAR(2),
    ADD CONSTRAINT fk_user_account_country
        FOREIGN KEY (country_code, country_level)
        REFERENCES region_code (code, level) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_user_account_country_code
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_user_account_user_country
        CHECK (role <> 'USER' OR country_code IS NOT NULL);

CREATE INDEX user_account_country_idx
    ON user_account (country_code);

COMMENT ON COLUMN user_account.country_code IS
    '일반 사용자 온보딩에서 검증한 ISO 3166-1 alpha-2 국가 코드. 운영자는 NULL을 허용한다.';

COMMENT ON COLUMN user_account.country_level IS
    'country_code가 region_code의 COUNTRY 행만 참조하도록 복합 FK에 사용하는 생성 컬럼.';
