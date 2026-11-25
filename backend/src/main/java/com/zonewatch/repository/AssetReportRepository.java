package com.zonewatch.repository;

import com.zonewatch.entity.AssetReportEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AssetReportRepository extends JpaRepository<AssetReportEntity, String> {

    List<AssetReportEntity> findByAssetIdOrderByObservedAtAscSeqIdAsc(String assetId, Pageable pageable);

    List<AssetReportEntity> findByAssetIdAndSourceTypeOrderByObservedAtAscSeqIdAsc(
            String assetId, String sourceType, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO asset_reports (report_id, asset_id, observed_at, lat, lon, speed_kn, heading_deg, source, source_type)
            VALUES (:reportId, :assetId, :observedAt, :lat, :lon, :speedKn, :headingDeg, :source, :sourceType)
            ON CONFLICT (report_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("reportId") String reportId,
                       @Param("assetId") String assetId,
                       @Param("observedAt") Instant observedAt,
                       @Param("lat") double lat,
                       @Param("lon") double lon,
                       @Param("speedKn") Double speedKn,
                       @Param("headingDeg") Double headingDeg,
                       @Param("source") String source,
                       @Param("sourceType") String sourceType);
}
