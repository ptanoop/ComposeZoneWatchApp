package com.zonewatch.service;

import com.zonewatch.api.ApiException;
import com.zonewatch.domain.Models.BatchIngestResponse;
import com.zonewatch.domain.Models.ParsedReport;
import com.zonewatch.domain.Models.RejectedLine;
import com.zonewatch.entity.AlertAuditEntity;
import com.zonewatch.entity.RejectedReportEntity;
import com.zonewatch.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class IngestService {

    private static final int MAX_BATCH_LINES = 1000;
    private final ReportLineValidator validator;
    private final RejectedReportRepository rejectedReportRepository;
    private final AssetReportRepository assetReportRepository;
    private final AssetRepository assetRepository;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final AlertAuditRepository alertAuditRepository;
    private final IngestCounterRepository ingestCounterRepository;

    public IngestService(
            ReportLineValidator validator,
            RejectedReportRepository rejectedReportRepository,
            AssetReportRepository assetReportRepository,
            AssetRepository assetRepository,
            ZoneRepository zoneRepository,
            AlertRepository alertRepository,
            AlertAuditRepository alertAuditRepository,
            IngestCounterRepository ingestCounterRepository) {
        this.validator = validator;
        this.rejectedReportRepository = rejectedReportRepository;
        this.assetReportRepository = assetReportRepository;
        this.assetRepository = assetRepository;
        this.zoneRepository = zoneRepository;
        this.alertRepository = alertRepository;
        this.alertAuditRepository = alertAuditRepository;
        this.ingestCounterRepository = ingestCounterRepository;
    }

    @Transactional
    public BatchIngestResponse ingestNdjson(String payload) {
        if (payload == null || payload.isBlank()) {
            return new BatchIngestResponse(0, 0, 0, 0, List.of());
        }

        String[] rawLines = payload.split("\r?\n");
        List<String> nonEmptyLines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            if (!line.isBlank()) {
                nonEmptyLines.add(line);
            }
        }

        if (nonEmptyLines.size() > MAX_BATCH_LINES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE",
                    "Maximum 1,000 reports allowed per batch");
        }

        List<ParsedReport> validReports = new ArrayList<>(nonEmptyLines.size());
        List<RejectedLine> rejectedLines = new ArrayList<>();
        List<RejectedReportEntity> rejectedEntities = new ArrayList<>();

        for (int i = 0; i < nonEmptyLines.size(); i++) {
            int lineNum = i + 1;
            String raw = nonEmptyLines.get(i);
            var outcome = validator.validate(lineNum, raw);
            if (outcome.isValid()) {
                validReports.add(outcome.report());
            } else {
                RejectedLine rej = outcome.rejection();
                rejectedLines.add(rej);
                rejectedEntities.add(new RejectedReportEntity(rej.line(), raw, rej.code(), rej.reason()));
            }
        }

        if (!rejectedEntities.isEmpty()) {
            rejectedReportRepository.saveAll(rejectedEntities);
        }

        // Sort deterministically by (assetId, observedAt) to prevent cross-transaction deadlocks
        validReports.sort(Comparator.comparing(ParsedReport::assetId)
                .thenComparing(ParsedReport::observedAt));

        int accepted = 0;
        int duplicates = 0;

        for (ParsedReport r : validReports) {
            // Rule R1: Exact-once idempotency via report_id PK
            int inserted = assetReportRepository.insertIfAbsent(
                    r.reportId(), r.assetId(), r.observedAt(),
                    r.lat(), r.lon(), r.speedKn(), r.headingDeg(), r.source(), r.sourceType().name());

            if (inserted == 0) {
                duplicates++;
                continue;
            }
            accepted++;

            // Capture previous RESTRICTED zones before updating current position
            List<String> previousRestrictedZones = zoneRepository.findPreviousRestrictedZones(
                    r.assetId(), r.observedAt());

            // Rule R2: Conditional upsert updates current position ONLY if observed_at is strictly newer
            int moved = assetRepository.upsertLatestPosition(
                    r.assetId(), r.observedAt(),
                    r.lat(), r.lon(), r.speedKn(), r.headingDeg(), r.source());

            // Late report (moved == 0): stored in track only, never alters current position or alerts
            if (moved > 0) {
                evaluateZoneTransitions(r, new HashSet<>(previousRestrictedZones));
            }
        }

        int received = nonEmptyLines.size();
        ingestCounterRepository.incrementCounters(received, accepted, duplicates, rejectedLines.size());

        return new BatchIngestResponse(received, accepted, duplicates, rejectedLines.size(), rejectedLines);
    }

    private void evaluateZoneTransitions(ParsedReport r, Set<String> previousRestrictedZones) {
        List<String> currentRestrictedZones = zoneRepository.findRestrictedZonesCoveringPoint(r.lon(), r.lat());

        for (String zoneId : currentRestrictedZones) {
            // Trigger alert only on transition: outside/unknown -> inside
            if (!previousRestrictedZones.contains(zoneId)) {
                UUID alertId = UUID.randomUUID();
                // Rule R3: Partial unique index uq_active_alert_per_asset_zone blocks duplicate OPEN/CLAIMED alerts
                int created = alertRepository.insertOpenAlertIfAbsent(
                        alertId, r.assetId(), zoneId, r.observedAt());

                if (created > 0) {
                    // Rule R5: Audit trail written in same transaction
                    alertAuditRepository.save(new AlertAuditEntity(
                            alertId, "OPENED", "SYSTEM", "Asset entered restricted zone", Instant.now()));
                }
            }
        }
    }
}
