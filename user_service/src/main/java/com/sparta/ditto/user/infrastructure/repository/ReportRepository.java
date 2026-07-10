package com.sparta.ditto.user.infrastructure.repository;

import com.sparta.ditto.user.domain.report.Report;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndReportedId(UUID reporterId, UUID reportedId);
}
