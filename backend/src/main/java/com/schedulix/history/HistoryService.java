package com.schedulix.history;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HistoryService {

    private final HistoryEntryRepository historyEntryRepository;
    private final CurrentUser currentUser;
    private final ZoneId departmentZone;

    public HistoryService(
            HistoryEntryRepository historyEntryRepository,
            CurrentUser currentUser,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        this.historyEntryRepository = historyEntryRepository;
        this.currentUser = currentUser;
        this.departmentZone = ZoneId.of(timezone);
    }

    @Transactional
    public void record(String kind, String sentence) {
        historyEntryRepository.save(new HistoryEntry(
                currentUser.departmentId(),
                currentUser.userId(),
                currentUser.displayName(),
                kind,
                sentence
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryEntryResponse> list(
            String search,
            String kind,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        UUID deptId = currentUser.departmentId();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Specification<HistoryEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("departmentId"), deptId));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("text")), pattern),
                        cb.like(cb.lower(root.get("userName")), pattern)
                ));
            }
            if (kind != null && !kind.isBlank()) {
                predicates.add(cb.equal(root.get("kind"), kind.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                Instant fromStart = from.atStartOfDay(departmentZone).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromStart));
            }
            if (to != null) {
                Instant toExclusive = to.plusDays(1).atStartOfDay(departmentZone).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HistoryEntryResponse> result = historyEntryRepository
                .findAll(spec, pageable)
                .map(HistoryEntryResponse::from);
        return PageResponse.from(result);
    }
}