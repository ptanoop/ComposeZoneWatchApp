package com.zonewatch.repository;

import com.zonewatch.entity.IngestCounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestCounterRepository extends JpaRepository<IngestCounterEntity, Integer> {

    @Modifying
    @Query(value = """
            UPDATE ingest_counters
            SET received_count = received_count + :received,
                accepted_count = accepted_count + :accepted,
                duplicate_count = duplicate_count + :duplicates,
                rejected_count = rejected_count + :rejected
            WHERE singleton_id = 1
            """, nativeQuery = true)
    void incrementCounters(@Param("received") long received,
                           @Param("accepted") long accepted,
                           @Param("duplicates") long duplicates,
                           @Param("rejected") long rejected);

    @Query(value = "SELECT 1", nativeQuery = true)
    Integer ping();
}
