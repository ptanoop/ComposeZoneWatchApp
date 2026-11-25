package com.zonewatch;

import com.zonewatch.domain.Models.BatchIngestResponse;
import com.zonewatch.service.AlertService;
import com.zonewatch.service.IngestService;
import com.zonewatch.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RulesVerificationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired IngestService ingestService;
    @Autowired QueryService queryService;
    @Autowired AlertService alertService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setupData() {
        jdbc.execute("TRUNCATE alert_audit, alerts, asset_reports, assets, rejected_reports, zones, users CASCADE");
        jdbc.update("UPDATE ingest_counters SET received_count=0, accepted_count=0, duplicate_count=0, rejected_count=0");
        jdbc.update("INSERT INTO users (username, password_hash, role) VALUES ('op1', 'x', 'OPERATOR'), ('op2', 'x', 'OPERATOR')");
        jdbc.update("""
                INSERT INTO zones (zone_id, name, zone_class, geojson, geom)
                VALUES ('Z-TEST', 'Restricted Box', 'RESTRICTED', '{}',
                        ST_Multi(ST_GeomFromText('POLYGON((55.1 25.1, 55.2 25.1, 55.2 25.2, 55.1 25.2, 55.1 25.1))', 4326)))
                """);
    }

    @Test
    void rulesR1ToR6HoldUnderParallelAndOutOfOrderDelivery() throws Exception {
        // R6: Bad input is isolated from good lines in same batch
        String batch1 = """
                {"reportId":"R-100","assetId":"ASSET-01","observedAt":"2026-11-02T06:00:10.000Z","lat":25.05,"lon":55.15,"source":"SAT-1"}
                {not-valid-json
                {"reportId":"R-101","assetId":"ASSET-01","observedAt":"2026-11-02T06:00:20.000Z","lat":25.15,"lon":55.15,"source":"GND-2"}
                """;

        BatchIngestResponse res1 = ingestService.ingestNdjson(batch1);
        assertThat(res1.accepted()).isEqualTo(2);
        assertThat(res1.rejected()).isEqualTo(1);
        assertThat(res1.rejectedLines().getFirst().code()).isEqualTo("MALFORMED_JSON");

        // R1: Duplicate delivery changes nothing
        BatchIngestResponse dupRes = ingestService.ingestNdjson(batch1);
        assertThat(dupRes.accepted()).isEqualTo(0);
        assertThat(dupRes.duplicates()).isEqualTo(2);

        // R2: Late report (06:00:05) is appended to track but does NOT regress current position (still at 25.15)
        String lateBatch = """
                {"reportId":"R-099","assetId":"ASSET-01","observedAt":"2026-11-02T06:00:05.000Z","lat":25.01,"lon":55.01,"source":"MOB-9"}
                """;
        ingestService.ingestNdjson(lateBatch);
        assertThat(queryService.getAsset("ASSET-01").lat()).isEqualTo(25.15);
        assertThat(queryService.getAssetTrack("ASSET-01", null, 100)).hasSize(3);

        // CR-1 sourceType filtering check
        assertThat(queryService.getAssetTrack("ASSET-01", "SATELLITE", 100)).hasSize(1);

        // R3: Only 1 OPEN alert exists for ASSET-01 in Z-TEST
        var alerts = alertService.listAlerts("OPEN", "Z-TEST", "ASSET-01", null, 10);
        assertThat(alerts).hasSize(1);
        UUID alertId = alerts.getFirst().alertId();

        // R4 & R5: Concurrent claims allow exactly 1 owner and write 1 CLAIMED audit entry
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> claimOp1 = () -> { try { alertService.claim(alertId, "op1"); return true; } catch (Exception e) { return false; } };
        Callable<Boolean> claimOp2 = () -> { try { alertService.claim(alertId, "op2"); return true; } catch (Exception e) { return false; } };
        List<Future<Boolean>> results = pool.invokeAll(List.of(claimOp1, claimOp2));
        pool.shutdown();

        long winners = results.stream().filter(f -> {
            try { return f.get(); } catch (Exception e) { return false; }
        }).count();
        assertThat(winners).isEqualTo(1);
        assertThat(alertService.getAuditTrail(alertId)).hasSize(2);
    }
}
