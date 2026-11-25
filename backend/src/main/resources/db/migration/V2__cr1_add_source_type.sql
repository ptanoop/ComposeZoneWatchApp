-- CR-1 (v1.1.0): Zero-downtime additive schema expansion + mixed-version compatibility trigger
ALTER TABLE asset_reports
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(16);

CREATE OR REPLACE FUNCTION derive_source_type(src TEXT) RETURNS VARCHAR(16) AS $$
BEGIN
    IF src IS NULL THEN
        RETURN 'UNKNOWN';
    ELSIF src LIKE 'SAT%' THEN
        RETURN 'SATELLITE';
    ELSIF src LIKE 'GND%' THEN
        RETURN 'GROUND';
    ELSIF src LIKE 'MOB%' THEN
        RETURN 'MOBILE';
    ELSE
        RETURN 'UNKNOWN';
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION trg_set_report_source_type() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.source_type IS NULL THEN
        NEW.source_type := derive_source_type(NEW.source);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_derive_source_type ON asset_reports;
CREATE TRIGGER trg_derive_source_type
    BEFORE INSERT ON asset_reports
    FOR EACH ROW EXECUTE FUNCTION trg_set_report_source_type();

CREATE INDEX IF NOT EXISTS idx_asset_reports_source_type_filter
    ON asset_reports (asset_id, source_type, observed_at ASC);

-- Throttled batch backfill procedure for 500M+ rows without long locks
CREATE OR REPLACE PROCEDURE backfill_source_type_batched(batch_size INT, sleep_ms INT)
LANGUAGE plpgsql AS $$
DECLARE
    rows_updated INT;
BEGIN
    LOOP
        WITH target AS (
            SELECT report_id
            FROM asset_reports
            WHERE source_type IS NULL
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE asset_reports r
        SET source_type = derive_source_type(r.source)
        FROM target
        WHERE r.report_id = target.report_id;

        GET DIAGNOSTICS rows_updated = ROW_COUNT;
        COMMIT;

        EXIT WHEN rows_updated = 0;
        PERFORM pg_sleep(sleep_ms / 1000.0);
    END LOOP;
END;
$$;
