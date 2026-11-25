package com.zonewatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "assets")
public class AssetEntity {

    @Id
    @Column(name = "asset_id", length = 32, nullable = false)
    private String assetId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "speed_kn")
    private Double speedKn;

    @Column(name = "heading_deg")
    private Double headingDeg;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AssetEntity() {}

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public Double getSpeedKn() { return speedKn; }
    public void setSpeedKn(Double speedKn) { this.speedKn = speedKn; }
    public Double getHeadingDeg() { return headingDeg; }
    public void setHeadingDeg(Double headingDeg) { this.headingDeg = headingDeg; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
