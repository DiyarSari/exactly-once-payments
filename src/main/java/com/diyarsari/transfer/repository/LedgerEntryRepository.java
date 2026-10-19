package com.diyarsari.transfer.repository;

import com.diyarsari.transfer.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Newest first; the id is monotonic so it gives a stable order even for equal timestamps. */
    Page<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId, Pageable pageable);
}
