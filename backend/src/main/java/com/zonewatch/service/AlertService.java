package com.zonewatch.service;

import com.zonewatch.api.ApiException;
import com.zonewatch.domain.Models.AlertAuditDto;
import com.zonewatch.domain.Models.AlertDto;
import com.zonewatch.entity.AlertAuditEntity;
import com.zonewatch.entity.AlertEntity;
import com.zonewatch.repository.AlertAuditRepository;
import com.zonewatch.repository.AlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertAuditRepository auditRepository;

    public AlertService(AlertRepository alertRepository, AlertAuditRepository auditRepository) {
        this.alertRepository = alertRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> listAlerts(String status, String zoneId, String assetId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanZoneId = (zoneId != null && !zoneId.isBlank()) ? zoneId.trim() : null;
        String cleanAssetId = (assetId != null && !assetId.isBlank()) ? assetId.trim() : null;

        Instant cursorInstant = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorInstant = Instant.parse(cursor.trim());
            } catch (DateTimeParseException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR",
                        "cursor must be a valid ISO-8601 timestamp");
            }
        }

        return alertRepository.searchAlerts(
                        cleanStatus, cleanZoneId, cleanAssetId, cursorInstant, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toAlertDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertDto getAlert(UUID alertId) {
        AlertEntity entity = alertRepository.findByIdWithZone(alertId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert not found"));
        return toAlertDto(entity);
    }

    @Transactional(readOnly = true)
    public List<AlertAuditDto> getAuditTrail(UUID alertId) {
        getAlert(alertId);
        return auditRepository.findByAlertIdOrderByAuditIdAsc(alertId)
                .stream()
                .map(a -> new AlertAuditDto(
                        a.getAuditId(),
                        a.getAlertId(),
                        a.getAction(),
                        a.getActorUsername(),
                        a.getNote(),
                        a.getOccurredAt()
                ))
                .toList();
    }

    @Transactional
    public AlertDto claim(UUID alertId, String actor) {
        // Rule R4: Atomic conditional update guarantees single owner under concurrent claims
        int updated = alertRepository.claimIfOpen(alertId, actor);

        if (updated == 1) {
            auditRepository.save(new AlertAuditEntity(alertId, "CLAIMED", actor, null, Instant.now()));
            return getAlert(alertId);
        }

        AlertDto current = getAlert(alertId);
        // Idempotent retry by same owner -> 200 OK, no new audit entry
        if ("CLAIMED".equals(current.status()) && actor.equals(current.owner())) {
            return current;
        }
        if ("CLAIMED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALERT_ALREADY_CLAIMED",
                    "Alert is already claimed by " + current.owner());
        }
        throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                "Cannot claim alert in state " + current.status());
    }

    @Transactional
    public AlertDto release(UUID alertId, String actor, boolean isSupervisor) {
        AlertDto current = getAlert(alertId);
        if (!"CLAIMED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Alert must be CLAIMED to release");
        }
        if (!isSupervisor && !actor.equals(current.owner())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ALERT_OWNER",
                    "Only the alert owner or a supervisor may release this alert");
        }

        int updated = alertRepository.releaseIfClaimed(alertId);

        if (updated == 1) {
            auditRepository.save(new AlertAuditEntity(alertId, "RELEASED", actor, null, Instant.now()));
        }
        return getAlert(alertId);
    }

    @Transactional
    public AlertDto resolve(UUID alertId, String actor, boolean isSupervisor, String note) {
        if (note == null || note.trim().isEmpty() || note.trim().length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Resolution note must be between 1 and 500 characters");
        }
        String cleanNote = note.trim();
        AlertDto current = getAlert(alertId);

        // Idempotent repeat by same resolver -> 200 OK, no new audit entry
        if ("RESOLVED".equals(current.status()) && actor.equals(current.owner())) {
            return current;
        }
        if (!"CLAIMED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Alert must be CLAIMED before resolving");
        }
        if (!isSupervisor && !actor.equals(current.owner())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ALERT_OWNER",
                    "Only the alert owner or a supervisor may resolve this alert");
        }

        int updated = alertRepository.resolveIfClaimed(alertId, actor, cleanNote);

        if (updated == 1) {
            auditRepository.save(new AlertAuditEntity(alertId, "RESOLVED", actor, cleanNote, Instant.now()));
        }
        return getAlert(alertId);
    }

    private AlertDto toAlertDto(AlertEntity a) {
        return new AlertDto(
                a.getAlertId(),
                a.getAssetId(),
                a.getZoneId(),
                a.getZone() != null ? a.getZone().getName() : a.getZoneId(),
                a.getStatus(),
                a.getOwnerUsername(),
                a.getResolutionNote(),
                a.getTriggeredAt(),
                a.getUpdatedAt()
        );
    }
}
