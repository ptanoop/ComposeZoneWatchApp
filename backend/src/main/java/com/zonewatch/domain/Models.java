package com.zonewatch.domain;

import java.time.Instant;
import java.util.UUID;

public final class Models {
    private Models() {}

    public enum SourceType {
        SATELLITE, GROUND, MOBILE, UNKNOWN;

        public static SourceType fromSource(String source) {
            if (source == null || source.isBlank()) return UNKNOWN;
            if (source.startsWith("SAT")) return SATELLITE;
            if (source.startsWith("GND")) return GROUND;
            if (source.startsWith("MOB")) return MOBILE;
            return UNKNOWN;
        }
    }

    public record ParsedReport(
            int lineNumber,
            String reportId,
            String assetId,
            Instant observedAt,
            double lat,
            double lon,
            String kind,
            Double speedKn,
            Double headingDeg,
            String source,
            SourceType sourceType
    ) {}

    public record RejectedLine(
            int line,
            String code,
            String reason
    ) {}

    public record BatchIngestResponse(
            int received,
            int accepted,
            int duplicates,
            int rejected,
            java.util.List<RejectedLine> rejectedLines
    ) {}

    public record AssetPosition(
            String assetId,
            Instant observedAt,
            double lat,
            double lon,
            Double speedKn,
            Double headingDeg,
            String source
    ) {}

    public record TrackPoint(
            String reportId,
            String assetId,
            Instant observedAt,
            double lat,
            double lon,
            Double speedKn,
            Double headingDeg,
            String source,
            String sourceType
    ) {}

    public record ZoneDto(
            String zoneId,
            String name,
            String zoneClass,
            String geojson
    ) {}

    public record AlertDto(
            UUID alertId,
            String assetId,
            String zoneId,
            String zoneName,
            String status,
            String owner,
            String resolutionNote,
            Instant triggeredAt,
            Instant updatedAt
    ) {}

    public record AlertAuditDto(
            long auditId,
            UUID alertId,
            String action,
            String actor,
            String note,
            Instant occurredAt
    ) {}

    public record StatsDto(
            long received,
            long accepted,
            long duplicates,
            long rejected,
            long assets,
            long openAlerts,
            long claimedAlerts,
            long resolvedAlerts
    ) {}
}
