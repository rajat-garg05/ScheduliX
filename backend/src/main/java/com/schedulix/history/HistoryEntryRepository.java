package com.schedulix.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface HistoryEntryRepository
        extends JpaRepository<HistoryEntry, UUID>, JpaSpecificationExecutor<HistoryEntry> {
}