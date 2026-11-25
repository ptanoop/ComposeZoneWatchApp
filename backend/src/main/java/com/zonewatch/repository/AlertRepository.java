package com.zonewatch.repository;

import com.zonewatch.entity.AlertEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    @Query("""
            SELECT a FROM AlertEntity a JOIN FETCH a.zone
            WHERE a.alertId = :alertId
            """)
    Optional<AlertEntity> findByIdWithZone(@Param("alertId") UUID alertId);

    @Query("""
                SELECT a FROM AlertEntity a
                JOIN FETCH a.zone
                WHERE (:status IS NULL OR a.status = CAST(:status AS String))
                AND (:zoneId IS NULL OR a.zoneId = CAST(:zoneId AS String))
                AND (:assetId IS NULL OR a.assetId = CAST(:assetId AS String))
                ORDER BY a.triggeredAt DESC, a.alertId DESC
            """)
    List<AlertEntity> searchAlerts(@Param("status") String status,
                                   @Param("zoneId") String zoneId,
                                   @Param("assetId") String assetId,
                                   @Param("cursor") Instant cursor,
                                   Pageable pageable);

    long countByStatus(String status);

    @Modifying
    @Query(value = """
            INSERT INTO alerts (alert_id, asset_id, zone_id, status, triggered_at, updated_at)
            VALUES (:alertId, :assetId, :zoneId, 'OPEN', :triggeredAt, NOW())
            ON CONFLICT (asset_id, zone_id) WHERE status IN ('OPEN', 'CLAIMED') DO NOTHING
            """, nativeQuery = true)
    int insertOpenAlertIfAbsent(@Param("alertId") UUID alertId,
                                @Param("assetId") String assetId,
                                @Param("zoneId") String zoneId,
                                @Param("triggeredAt") Instant triggeredAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AlertEntity a
            SET a.status = 'CLAIMED', a.ownerUsername = :actor, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.alertId = :alertId AND a.status = 'OPEN'
            """)
    int claimIfOpen(@Param("alertId") UUID alertId, @Param("actor") String actor);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AlertEntity a
            SET a.status = 'OPEN', a.ownerUsername = NULL, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.alertId = :alertId AND a.status = 'CLAIMED'
            """)
    int releaseIfClaimed(@Param("alertId") UUID alertId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AlertEntity a
            SET a.status = 'RESOLVED', a.ownerUsername = :actor, a.resolutionNote = :note, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.alertId = :alertId AND a.status = 'CLAIMED'
            """)
    int resolveIfClaimed(@Param("alertId") UUID alertId,
                         @Param("actor") String actor,
                         @Param("note") String note);
}
