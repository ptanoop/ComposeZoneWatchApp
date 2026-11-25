package com.zonewatch.repository;

import com.zonewatch.entity.AlertAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertAuditRepository extends JpaRepository<AlertAuditEntity, Long> {

    List<AlertAuditEntity> findByAlertIdOrderByAuditIdAsc(UUID alertId);
}
