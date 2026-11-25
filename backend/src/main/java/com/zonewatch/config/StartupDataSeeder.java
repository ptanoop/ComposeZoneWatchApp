package com.zonewatch.config;


import com.zonewatch.repository.UserRepository;
import com.zonewatch.repository.ZoneRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class StartupDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDataSeeder.class);

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final ObjectMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final String usersFilePath;
    private final String zonesFilePath;

    public StartupDataSeeder(
            UserRepository userRepository,
            ZoneRepository zoneRepository,
            ObjectMapper mapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.users-file:/app/config/seed-users.sample.json}") String usersFilePath,
            @Value("${app.zones-file:/app/config/zones.sample.geojson}") String zonesFilePath) {
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.usersFilePath = usersFilePath;
        this.zonesFilePath = zonesFilePath;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        seedUsers();
        seedZones();
    }

    private InputStream openStream(String path) throws Exception {
        if (path != null && !path.isBlank()) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f.toURI().toURL().openStream();
            }
            if (path.startsWith("classpath:")) {
                ClassPathResource cpr = new ClassPathResource(path.substring("classpath:".length()));
                if (cpr.exists()) return cpr.getInputStream();
            }
        }
        String fallbackName = (path != null && path.contains("zones"))
                ? "seed/zones.sample.geojson"
                : "seed/seed-users.sample.json";
        ClassPathResource fallback = new ClassPathResource(fallbackName);
        return fallback.exists() ? fallback.getInputStream() : null;
    }

    private void seedUsers() throws Exception {
        try (InputStream is = openStream(usersFilePath)) {
            if (is == null) {
                log.warn("Users seed file not found at {}", usersFilePath);
                return;
            }
            List<Map<String, String>> users = mapper.readValue(is, new TypeReference<>() {});
            for (Map<String, String> u : users) {
                String username = u.get("username");
                String rawPassword = u.get("password");
                String role = u.get("role");
                if (username == null || rawPassword == null || role == null) continue;
                String hash = passwordEncoder.encode(rawPassword);
                userRepository.upsertUser(username, hash, role);
            }
            log.info("Seeded {} user accounts from {}", users.size(), usersFilePath);
        }
    }

    private void seedZones() throws Exception {
        try (InputStream is = openStream(zonesFilePath)) {
            if (is == null) {
                log.warn("Zones GeoJSON file not found at {}", zonesFilePath);
                return;
            }
            JsonNode root = mapper.readTree(is);
            JsonNode features = root.path("features");
            int count = 0;
            for (JsonNode feat : features) {
                JsonNode props = feat.path("properties");
                String zoneId = props.path("zoneId").asText(props.path("id").asText("ZONE-" + count));
                String name = props.path("name").asText(zoneId);
                String zoneClass = props.path("zoneClass").asText(props.path("class").asText("ADVISORY")).toUpperCase();
                if (!zoneClass.equals("RESTRICTED") && !zoneClass.equals("ADVISORY")) {
                    zoneClass = "ADVISORY";
                }
                String geomJson = mapper.writeValueAsString(feat.path("geometry"));
                String fullFeatureJson = mapper.writeValueAsString(feat);

                zoneRepository.upsertZoneWithGeom(zoneId, name, zoneClass, fullFeatureJson, geomJson);
                count++;
            }
            log.info("Loaded {} spatial zones into PostGIS from {}", count, zonesFilePath);
        }
    }
}
