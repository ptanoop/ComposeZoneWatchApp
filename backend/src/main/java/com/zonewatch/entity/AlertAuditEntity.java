package com.zonewatch.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_audit")
public class AlertAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "action", length = 16, nullable = false)
    private String action;

    @Column(name = "actor_username", length = 64)
    private String actorUsername;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AlertAuditEntity() {}

    public AlertAuditEntity(UUID alertId, String action, String actorUsername, String note, Instant occurredAt) {
        this.alertId = alertId;
        this.action = action;
        this.actorUsername = actorUsername;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public Long getAuditId() { return auditId; }
    public UUID getAlertId() { return alertId; }
    public String getAction() { return action; }
    public String getActorUsername() { return actorUsername; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
