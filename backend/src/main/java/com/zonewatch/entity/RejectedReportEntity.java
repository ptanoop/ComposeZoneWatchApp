package com.zonewatch.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rejected_reports")
public class RejectedReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "raw_payload", columnDefinition = "TEXT", nullable = false)
    private String rawPayload;

    @Column(name = "rejection_code", length = 64, nullable = false)
    private String rejectionCode;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "rejected_at", nullable = false, insertable = false, updatable = false)
    private Instant rejectedAt;

    public RejectedReportEntity() {}

    public RejectedReportEntity(int lineNumber, String rawPayload, String rejectionCode, String reason) {
        this.lineNumber = lineNumber;
        this.rawPayload = rawPayload;
        this.rejectionCode = rejectionCode;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public int getLineNumber() { return lineNumber; }
    public String getRawPayload() { return rawPayload; }
    public String getRejectionCode() { return rejectionCode; }
    public String getReason() { return reason; }
    public Instant getRejectedAt() { return rejectedAt; }
}
