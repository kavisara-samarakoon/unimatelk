package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.ModerationCase;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.ModerationCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdminService {

    private final ModerationCaseRepository caseRepo;
    private final AppUserRepository userRepo;

    public AdminService(ModerationCaseRepository caseRepo, AppUserRepository userRepo) {
        this.caseRepo = caseRepo;
        this.userRepo = userRepo;
    }

    public Page<ModerationCase> listCases(String status, Pageable pageable) {
        String s = (status == null || status.isBlank()) ? "OPEN" : status.trim().toUpperCase();
        return caseRepo.findByStatusOrderByCreatedAtDesc(s, pageable);
    }

    public ModerationCase resolve(AppUser admin, Long caseId, String action, String note) {
        ModerationCase c = caseRepo.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        if (!"OPEN".equalsIgnoreCase(c.getStatus())) {
            throw new IllegalArgumentException("Case already resolved");
        }

        String act = action == null ? "" : action.trim().toUpperCase();
        if (!(act.equals("UNBLOCK") || act.equals("BAN"))) {
            throw new IllegalArgumentException("Action must be UNBLOCK or BAN");
        }

        AppUser reported = c.getReportedUser();
        if (act.equals("UNBLOCK")) {
            reported.setStatus("ACTIVE");
        } else {
            reported.setStatus("BANNED");
        }
        userRepo.save(reported);

        c.setAction(act);
        c.setResolutionNote(note);
        c.setStatus("RESOLVED");
        c.setResolvedAt(Instant.now());
        c.setResolvedBy(admin);
        return caseRepo.save(c);
    }
}
