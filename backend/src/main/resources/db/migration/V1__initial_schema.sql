CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('INGEST', 'OPERATOR', 'SUPERVISOR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE zones (
    zone_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    zone_class VARCHAR(32) NOT NULL CHECK (zone_class IN ('RESTRICTED', 'ADVISORY')),
    geojson TEXT NOT NULL,
    geom GEOMETRY(MultiPolygon, 4326) NOT NULL
);
CREATE INDEX idx_zones_geom ON zones USING GIST (geom);

CREATE TABLE assets (
    asset_id VARCHAR(32) PRIMARY KEY,
    observed_at TIMESTAMPTZ NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    speed_kn DOUBLE PRECISION,
    heading_deg DOUBLE PRECISION,
    source VARCHAR(128),
    geom GEOMETRY(Point, 4326) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assets_lat_lon ON assets (lat, lon);
CREATE INDEX idx_assets_geom ON assets USING GIST (geom);

CREATE TABLE asset_reports (
    seq_id BIGSERIAL,
    report_id VARCHAR(64) PRIMARY KEY,
    asset_id VARCHAR(32) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    speed_kn DOUBLE PRECISION,
    heading_deg DOUBLE PRECISION,
    source VARCHAR(128),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_asset_reports_asset_time ON asset_reports (asset_id, observed_at ASC);

CREATE TABLE rejected_reports (
    id BIGSERIAL PRIMARY KEY,
    line_number INT NOT NULL,
    raw_payload TEXT NOT NULL,
    rejection_code VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL,
    rejected_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rejected_reports_time ON rejected_reports (rejected_at DESC);

CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY,
    asset_id VARCHAR(32) NOT NULL REFERENCES assets(asset_id),
    zone_id VARCHAR(64) NOT NULL REFERENCES zones(zone_id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'CLAIMED', 'RESOLVED')),
    owner_username VARCHAR(64) REFERENCES users(username),
    resolution_note VARCHAR(500),
    triggered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Rule R3: At most one OPEN or CLAIMED alert per (asset_id, zone_id)
CREATE UNIQUE INDEX uq_active_alert_per_asset_zone
    ON alerts (asset_id, zone_id)
    WHERE status IN ('OPEN', 'CLAIMED');

CREATE INDEX idx_alerts_status_triggered ON alerts (status, triggered_at DESC);

-- Rule R5: Append-only audit trail
CREATE TABLE alert_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    alert_id UUID NOT NULL REFERENCES alerts(alert_id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('OPENED', 'CLAIMED', 'RELEASED', 'RESOLVED')),
    actor_username VARCHAR(64),
    note VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alert_audit_alert_id ON alert_audit (alert_id, audit_id ASC);

CREATE TABLE ingest_counters (
    singleton_id INT PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
    received_count BIGINT NOT NULL DEFAULT 0,
    accepted_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    rejected_count BIGINT NOT NULL DEFAULT 0
);
INSERT INTO ingest_counters (singleton_id) VALUES (1);
