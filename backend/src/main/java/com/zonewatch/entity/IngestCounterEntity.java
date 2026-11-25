package com.zonewatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingest_counters")
public class IngestCounterEntity {

    @Id
    @Column(name = "singleton_id")
    private Integer singletonId;

    @Column(name = "received_count", nullable = false)
    private long receivedCount;

    @Column(name = "accepted_count", nullable = false)
    private long acceptedCount;

    @Column(name = "duplicate_count", nullable = false)
    private long duplicateCount;

    @Column(name = "rejected_count", nullable = false)
    private long rejectedCount;

    public IngestCounterEntity() {}

    public Integer getSingletonId() { return singletonId; }
    public long getReceivedCount() { return receivedCount; }
    public long getAcceptedCount() { return acceptedCount; }
    public long getDuplicateCount() { return duplicateCount; }
    public long getRejectedCount() { return rejectedCount; }
}
