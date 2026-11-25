package com.zonewatch.repository;

import com.zonewatch.entity.ZoneEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<ZoneEntity, String> {

    List<ZoneEntity> findAllByOrderByZoneIdAsc(Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO zones (zone_id, name, zone_class, geojson, geom)
            VALUES (:zoneId, :name, :zoneClass, :geojson, ST_Multi(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(:geomJson), 4326))))
            ON CONFLICT (zone_id) DO UPDATE SET
                name = EXCLUDED.name,
                zone_class = EXCLUDED.zone_class,
                geojson = EXCLUDED.geojson,
                geom = EXCLUDED.geom
            """, nativeQuery = true)
    void upsertZoneWithGeom(@Param("zoneId") String zoneId,
                            @Param("name") String name,
                            @Param("zoneClass") String zoneClass,
                            @Param("geojson") String geojson,
                            @Param("geomJson") String geomJson);

    @Query(value = """
            SELECT z.zone_id
            FROM assets a
            JOIN zones z ON z.zone_class = 'RESTRICTED' AND ST_Covers(z.geom, a.geom)
            WHERE a.asset_id = :assetId AND a.observed_at < :observedAt
            """, nativeQuery = true)
    List<String> findPreviousRestrictedZones(@Param("assetId") String assetId,
                                             @Param("observedAt") Instant observedAt);

    @Query(value = """
            SELECT z.zone_id
            FROM zones z
            WHERE z.zone_class = 'RESTRICTED'
              AND ST_Covers(z.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
            """, nativeQuery = true)
    List<String> findRestrictedZonesCoveringPoint(@Param("lon") double lon,
                                                  @Param("lat") double lat);
}
