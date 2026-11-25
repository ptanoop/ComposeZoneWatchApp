package com.zonewatch.service;

import com.zonewatch.api.ApiException;
import com.zonewatch.domain.Models.*;
import com.zonewatch.entity.AssetEntity;
import com.zonewatch.entity.AssetReportEntity;
import com.zonewatch.entity.IngestCounterEntity;
import com.zonewatch.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryService {

    private final AssetRepository assetRepository;
    private final AssetReportRepository assetReportRepository;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final IngestCounterRepository ingestCounterRepository;

    public QueryService(
            AssetRepository assetRepository,
            AssetReportRepository assetReportRepository,
            ZoneRepository zoneRepository,
            AlertRepository alertRepository,
            IngestCounterRepository ingestCounterRepository) {
        this.assetRepository = assetRepository;
        this.assetReportRepository = assetReportRepository;
        this.zoneRepository = zoneRepository;
        this.alertRepository = alertRepository;
        this.ingestCounterRepository = ingestCounterRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetPosition> findAssetsInBbox(String bboxParam, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 5000);
        PageRequest page = PageRequest.of(0, safeLimit);

        if (bboxParam == null || bboxParam.isBlank()) {
            return assetRepository.findAllByOrderByAssetIdAsc(page)
                    .stream()
                    .map(this::mapAsset)
                    .toList();
        }

        String[] parts = bboxParam.split(",");
        if (parts.length != 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BBOX",
                    "bbox must be minLon,minLat,maxLon,maxLat");
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());

            return assetRepository.findByLonBetweenAndLatBetweenOrderByAssetIdAsc(
                            minLon, maxLon, minLat, maxLat, page)
                    .stream()
                    .map(this::mapAsset)
                    .toList();
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BBOX", "bbox coordinates must be numeric");
        }
    }

    @Transactional(readOnly = true)
    public AssetPosition getAsset(String assetId) {
        AssetEntity entity = assetRepository.findById(assetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found"));
        return mapAsset(entity);
    }

    @Transactional(readOnly = true)
    public List<TrackPoint> getAssetTrack(String assetId, String sourceType, int limit) {
        getAsset(assetId);
        int safeLimit = Math.min(Math.max(limit, 1), 5000);
        PageRequest page = PageRequest.of(0, safeLimit);

        List<AssetReportEntity> reports = (sourceType != null && !sourceType.isBlank())
                ? assetReportRepository.findByAssetIdAndSourceTypeOrderByObservedAtAscSeqIdAsc(
                        assetId, sourceType.trim().toUpperCase(), page)
                : assetReportRepository.findByAssetIdOrderByObservedAtAscSeqIdAsc(assetId, page);

        return reports.stream()
                .map(r -> new TrackPoint(
                        r.getReportId(),
                        r.getAssetId(),
                        r.getObservedAt(),
                        r.getLat(),
                        r.getLon(),
                        r.getSpeedKn(),
                        r.getHeadingDeg(),
                        r.getSource(),
                        r.getSourceType() != null ? r.getSourceType() : "UNKNOWN"
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneDto> listZones() {
        return zoneRepository.findAllByOrderByZoneIdAsc(PageRequest.of(0, 500))
                .stream()
                .map(z -> new ZoneDto(
                        z.getZoneId(),
                        z.getName(),
                        z.getZoneClass(),
                        z.getGeojson()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsDto getStats() {
        IngestCounterEntity c = ingestCounterRepository.findById(1)
                .orElse(new IngestCounterEntity());

        long assetCount = assetRepository.count();
        long openAlerts = alertRepository.countByStatus("OPEN");
        long claimedAlerts = alertRepository.countByStatus("CLAIMED");
        long resolvedAlerts = alertRepository.countByStatus("RESOLVED");

        return new StatsDto(
                c.getReceivedCount(),
                c.getAcceptedCount(),
                c.getDuplicateCount(),
                c.getRejectedCount(),
                assetCount,
                openAlerts,
                claimedAlerts,
                resolvedAlerts
        );
    }

    private AssetPosition mapAsset(AssetEntity a) {
        return new AssetPosition(
                a.getAssetId(),
                a.getObservedAt(),
                a.getLat(),
                a.getLon(),
                a.getSpeedKn(),
                a.getHeadingDeg(),
                a.getSource()
        );
    }
}
