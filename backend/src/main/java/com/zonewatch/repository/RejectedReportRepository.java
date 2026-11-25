package com.zonewatch.repository;

import com.zonewatch.entity.RejectedReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RejectedReportRepository extends JpaRepository<RejectedReportEntity, Long> {
}
