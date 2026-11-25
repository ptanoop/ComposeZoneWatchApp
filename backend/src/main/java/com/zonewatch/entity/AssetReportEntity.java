package com.zonewatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "asset_reports")
public class AssetReportEntity {

    @Column(name = "seq_id", insertable = false, updatable = false)
    private Long seqId;

    @Id
    @Column(name = "report_id", length = 64, nullable = false)
    private String reportId;

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

    @Column(name = "source_type", length = 16)
    private String sourceType;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    public AssetReportEntity() {}

    public Long getSeqId() { return seqId; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
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
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Instant getReceivedAt() { return receivedAt; }
}
