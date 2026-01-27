package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReporterAndReported(AppUser reporter, AppUser reported);

    @Query("select count(r) from Report r where r.reported = :reported and r.createdAt >= :since")
    long countReportsSince(@Param("reported") AppUser reported, @Param("since") Instant since);
}
