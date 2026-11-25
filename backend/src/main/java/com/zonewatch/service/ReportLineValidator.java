package com.zonewatch.service;


import com.zonewatch.domain.Models.ParsedReport;
import com.zonewatch.domain.Models.RejectedLine;
import com.zonewatch.domain.Models.SourceType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class ReportLineValidator {

    private static final Pattern ASSET_ID_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,31}$");
    private final ObjectMapper mapper;

    public ReportLineValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ValidationOutcome validate(int lineNumber, String rawLine) {
        JsonNode node;
        try {
            node = mapper.readTree(rawLine);
            if (node == null || !node.isObject()) {
                return ValidationOutcome.invalid(new RejectedLine(lineNumber, "MALFORMED_JSON", "Line is not a JSON object"));
            }
        } catch (Exception e) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "MALFORMED_JSON", "Invalid JSON syntax"));
        }

        String reportId = getNonBlankText(node, "reportId");
        String assetId = getNonBlankText(node, "assetId");
        String observedAtStr = getNonBlankText(node, "observedAt");
        JsonNode latNode = node.get("lat");
        JsonNode lonNode = node.get("lon");

        if (reportId == null || assetId == null || observedAtStr == null
                || latNode == null || latNode.isNull() || lonNode == null || lonNode.isNull()) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "MISSING_FIELD",
                    "Required field missing or empty (reportId, assetId, observedAt, lat, lon)"));
        }

        if (reportId.length() > 64 || !ASSET_ID_PATTERN.matcher(assetId).matches()) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "INVALID_FIELD",
                    "reportId max 64 chars or assetId fails pattern ^[A-Z0-9][A-Z0-9-]{2,31}$"));
        }

        if (!latNode.isNumber() || !lonNode.isNumber()) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "INVALID_COORDINATE",
                    "lat and lon must be numeric"));
        }
        double lat = latNode.asDouble();
        double lon = lonNode.asDouble();
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0
                || Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180.0 || lon > 180.0) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "INVALID_COORDINATE",
                    "lat must be in [-90, 90] and lon in [-180, 180]"));
        }

        Instant observedAt;
        try {
            observedAt = Instant.parse(observedAtStr);
        } catch (DateTimeParseException e) {
            return ValidationOutcome.invalid(new RejectedLine(lineNumber, "INVALID_TIMESTAMP",
                    "observedAt must be a valid ISO-8601 instant"));
        }

        String kind = getOptionalText(node, "kind");
        Double speedKn = getOptionalDouble(node, "speedKn");
        Double headingDeg = getOptionalDouble(node, "headingDeg");
        String source = getOptionalText(node, "source");

        ParsedReport report = new ParsedReport(
                lineNumber, reportId, assetId, observedAt, lat, lon,
                kind, speedKn, headingDeg, source, SourceType.fromSource(source)
        );
        return ValidationOutcome.valid(report);
    }

    private String getNonBlankText(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || !val.isTextual()) return null;
        String text = val.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String getOptionalText(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    private Double getOptionalDouble(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || !val.isNumber()) return null;
        return val.asDouble();
    }

    public record ValidationOutcome(ParsedReport report, RejectedLine rejection) {
        public static ValidationOutcome valid(ParsedReport r) { return new ValidationOutcome(r, null); }
        public static ValidationOutcome invalid(RejectedLine rej) { return new ValidationOutcome(null, rej); }
        public boolean isValid() { return report != null; }
    }
}
