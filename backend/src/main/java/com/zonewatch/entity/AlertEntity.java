package com.zonewatch.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "asset_id", length = 32, nullable = false)
    private String assetId;

    @Column(name = "zone_id", length = 64, nullable = false)
    private String zoneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", insertable = false, updatable = false)
    private ZoneEntity zone;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "owner_username", length = 64)
    private String ownerUsername;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AlertEntity() {}

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public ZoneEntity getZone() { return zone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
