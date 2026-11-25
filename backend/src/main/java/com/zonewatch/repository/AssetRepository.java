package com.zonewatch.repository;

import com.zonewatch.entity.AssetEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, String> {

    List<AssetEntity> findAllByOrderByAssetIdAsc(Pageable pageable);

    List<AssetEntity> findByLonBetweenAndLatBetweenOrderByAssetIdAsc(
            double minLon, double maxLon, double minLat, double maxLat, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO assets (asset_id, observed_at, lat, lon, speed_kn, heading_deg, source, geom, updated_at)
        VALUES (
            :assetId,
            :observedAt,
            :lat,
            :lon,
            CAST(:speedKn AS DOUBLE PRECISION),
            CAST(:headingDeg AS DOUBLE PRECISION),
            CAST(:source AS VARCHAR),
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
            NOW()
        )
        ON CONFLICT (asset_id) DO UPDATE SET
            observed_at = EXCLUDED.observed_at,
            lat = EXCLUDED.lat,
            lon = EXCLUDED.lon,
            speed_kn = EXCLUDED.speed_kn,
            heading_deg = EXCLUDED.heading_deg,
            source = EXCLUDED.source,
            geom = EXCLUDED.geom,
            updated_at = NOW()
        WHERE EXCLUDED.observed_at > assets.observed_at
            """, nativeQuery = true)
    int upsertLatestPosition(@Param("assetId") String assetId,
                           @Param("observedAt") Instant observedAt,
                           @Param("lat") double lat,
                           @Param("lon") double lon,
                           @Param("speedKn") Double speedKn,
                           @Param("headingDeg") Double headingDeg,
                           @Param("source") String source);
}
