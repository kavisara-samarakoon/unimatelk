package com.unimatelk.service;

import com.unimatelk.config.AppProps;
import com.unimatelk.domain.*;
import com.unimatelk.repo.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SafetyService {

    private final AppUserRepository userRepo;
    private final BlockRepository blockRepo;
    private final ReportRepository reportRepo;
    private final ModerationCaseRepository caseRepo;
    private final AppProps props;

    public SafetyService(AppUserRepository userRepo, BlockRepository blockRepo, ReportRepository reportRepo, ModerationCaseRepository caseRepo, AppProps props) {
        this.userRepo = userRepo;
        this.blockRepo = blockRepo;
        this.reportRepo = reportRepo;
        this.caseRepo = caseRepo;
        this.props = props;
    }

    public Block block(AppUser blocker, Long blockedUserId) {
        if (blockedUserId == null) throw new IllegalArgumentException("User id is required");
        if (blocker.getId().equals(blockedUserId)) throw new IllegalArgumentException("You cannot block yourself");

        AppUser blocked = userRepo.findById(blockedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (blockRepo.existsByBlockerAndBlocked(blocker, blocked)) {
            throw new IllegalArgumentException("Already blocked");
        }

        Block b = new Block();
        b.setBlocker(blocker);
        b.setBlocked(blocked);
        b.setCreatedAt(Instant.now());
        return blockRepo.save(b);
    }

    public void unblock(AppUser blocker, Long blockedUserId) {
        AppUser blocked = userRepo.findById(blockedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        blockRepo.deleteByBlockerAndBlocked(blocker, blocked);
    }

    public List<Block> myBlocks(AppUser me) {
        return blockRepo.findAll().stream()
                .filter(b -> b.getBlocker().getId().equals(me.getId()))
                .toList();
    }

    public Report report(AppUser reporter, Long reportedUserId, String reason) {
        if (reportedUserId == null) throw new IllegalArgumentException("User id is required");
        if (reporter.getId().equals(reportedUserId)) throw new IllegalArgumentException("You cannot report yourself");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason is required");

        AppUser reported = userRepo.findById(reportedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (reportRepo.findByReporterAndReported(reporter, reported).isPresent()) {
            throw new IllegalArgumentException("You already reported this user");
        }

        Report r = new Report();
        r.setReporter(reporter);
        r.setReported(reported);
        r.setReason(reason.trim());
        r.setCreatedAt(Instant.now());
        Report saved = reportRepo.save(r);

        // Auto TEMP_BLOCK if threshold reached within rolling window
        int threshold = props.getReportThreshold();
        int days = props.getReportWindowDays();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long count = reportRepo.countReportsSince(reported, since);

        if (count >= threshold && "ACTIVE".equalsIgnoreCase(reported.getStatus())) {
            reported.setStatus("TEMP_BLOCKED");
            userRepo.save(reported);

            boolean hasOpen = caseRepo.findFirstByReportedUserAndStatusOrderByCreatedAtDesc(reported, "OPEN").isPresent();
            if (!hasOpen) {
                ModerationCase c = new ModerationCase();
                c.setReportedUser(reported);
                c.setStatus("OPEN");
                c.setAction("TEMP_BLOCK");
                c.setCreatedAt(Instant.now());
                caseRepo.save(c);
            }
        }

        return saved;
    }
}
