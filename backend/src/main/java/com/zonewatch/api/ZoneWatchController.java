package com.zonewatch.api;

import com.zonewatch.domain.Models.*;
import com.zonewatch.entity.UserEntity;
import com.zonewatch.repository.IngestCounterRepository;
import com.zonewatch.repository.UserRepository;
import com.zonewatch.security.JwtService;
import com.zonewatch.service.AlertService;
import com.zonewatch.service.IngestService;
import com.zonewatch.service.QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ZoneWatchController {

    private final UserRepository userRepository;
    private final IngestCounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final IngestService ingestService;
    private final QueryService queryService;
    private final AlertService alertService;

    public ZoneWatchController(
            UserRepository userRepository,
            IngestCounterRepository counterRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            IngestService ingestService,
            QueryService queryService,
            AlertService alertService) {
        this.userRepository = userRepository;
        this.counterRepository = counterRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.ingestService = ingestService;
        this.queryService = queryService;
        this.alertService = alertService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        counterRepository.ping();
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, String role) {}

    @PostMapping("/api/v1/auth/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        if (req == null || req.username() == null || req.password() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }
        UserEntity user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }
        String token = jwtService.issueToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @PostMapping(value = "/api/v1/reports/batch", consumes = "application/x-ndjson")
    public BatchIngestResponse ingestBatch(@RequestBody(required = false) String ndjsonBody) {
        return ingestService.ingestNdjson(ndjsonBody);
    }

    @GetMapping("/api/v1/stats")
    public StatsDto stats() {
        return queryService.getStats();
    }

    @GetMapping("/api/v1/zones")
    public List<ZoneDto> zones() {
        return queryService.listZones();
    }

    @GetMapping("/api/v1/assets")
    public List<AssetPosition> assets(
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "3500") int limit) {
        return queryService.findAssetsInBbox(bbox, limit);
    }

    @GetMapping("/api/v1/assets/{assetId}")
    public AssetPosition asset(@PathVariable String assetId) {
        return queryService.getAsset(assetId);
    }

    @GetMapping("/api/v1/assets/{assetId}/track")
    public List<TrackPoint> assetTrack(
            @PathVariable String assetId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1000") int limit) {
        return queryService.getAssetTrack(assetId, sourceType, limit);
    }

    @GetMapping("/api/v1/alerts")
    public List<AlertDto> alerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return alertService.listAlerts(status, zoneId, assetId, cursor, limit);
    }

    @GetMapping("/api/v1/alerts/{alertId}")
    public AlertDto alert(@PathVariable UUID alertId) {
        return alertService.getAlert(alertId);
    }

    @GetMapping("/api/v1/alerts/{alertId}/audit")
    public List<AlertAuditDto> alertAudit(@PathVariable UUID alertId) {
        return alertService.getAuditTrail(alertId);
    }

    @PostMapping("/api/v1/alerts/{alertId}/claim")
    public AlertDto claim(@PathVariable UUID alertId, Authentication auth) {
        return alertService.claim(alertId, auth.getName());
    }

    @PostMapping("/api/v1/alerts/{alertId}/release")
    public AlertDto release(@PathVariable UUID alertId, Authentication auth) {
        boolean isSupervisor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        return alertService.release(alertId, auth.getName(), isSupervisor);
    }

    public record ResolveRequest(String note) {}

    @PostMapping("/api/v1/alerts/{alertId}/resolve")
    public AlertDto resolve(@PathVariable UUID alertId, @RequestBody ResolveRequest req, Authentication auth) {
        boolean isSupervisor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        return alertService.resolve(alertId, auth.getName(), isSupervisor, req != null ? req.note() : null);
    }
}
