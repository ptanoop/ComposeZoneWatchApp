package com.zonewatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "zones")
public class ZoneEntity {

    @Id
    @Column(name = "zone_id", length = 64, nullable = false)
    private String zoneId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "zone_class", length = 32, nullable = false)
    private String zoneClass;

    @Column(name = "geojson", columnDefinition = "TEXT", nullable = false)
    private String geojson;

    public ZoneEntity() {}

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getZoneClass() { return zoneClass; }
    public void setZoneClass(String zoneClass) { this.zoneClass = zoneClass; }
    public String getGeojson() { return geojson; }
    public void setGeojson(String geojson) { this.geojson = geojson; }
}
