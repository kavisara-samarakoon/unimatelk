package com.unimatelk.repo;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Report;
import com.unimatelk.domain.ReportStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // Used by SafetyService
    Optional<Report> findByReporterAndReported(AppUser reporter, AppUser reported);

    @Query("""
        select count(r) from Report r
        where r.reported = :reported
          and r.createdAt >= :since
        """)
    long countReportsSince(@Param("reported") AppUser reported, @Param("since") Instant since);

    // ✅ Admin list with JOIN FETCH to avoid LazyInitializationException
    @Query(
            value = """
            select r from Report r
            join fetch r.reporter
            join fetch r.reported
            where r.status = :status
            order by r.createdAt desc
        """,
            countQuery = """
            select count(r) from Report r
            where r.status = :status
        """
    )
    Page<Report> findByStatusFetched(@Param("status") ReportStatus status, Pageable pageable);

    // ✅ Safe search (reason/details) + JOIN FETCH + pagination countQuery
    @Query(
            value = """
            select r from Report r
            join fetch r.reporter
            join fetch r.reported
            where r.status = :status
              and (
                   lower(r.reason) like :q
                or lower(coalesce(r.details, '')) like :q
              )
            order by r.createdAt desc
        """,
            countQuery = """
            select count(r) from Report r
            where r.status = :status
              and (
                   lower(r.reason) like :q
                or lower(coalesce(r.details, '')) like :q
              )
        """
    )
    Page<Report> searchByStatusFetched(@Param("status") ReportStatus status,
                                       @Param("q") String q,
                                       Pageable pageable);
}