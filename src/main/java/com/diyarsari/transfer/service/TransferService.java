package com.diyarsari.transfer.service;

import com.diyarsari.transfer.config.TransferRetryProperties;
import com.diyarsari.transfer.dto.TransferRequest;
import com.diyarsari.transfer.dto.TransferResponse;
import com.diyarsari.transfer.exception.TransferConflictException;
import com.diyarsari.transfer.exception.TransferNotFoundException;
import com.diyarsari.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entry point for transfers. Deliberately not transactional: every attempt runs in its own
 * transaction inside {@link TransferProcessor}, so a retry re-reads fresh balances and versions.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferProcessor processor;
    private final TransferRepository transfers;
    private final RequestHasher hasher;
    private final TransferRetryProperties retry;

    public TransferService(TransferProcessor processor, TransferRepository transfers, RequestHasher hasher,
                           TransferRetryProperties retry) {
        this.processor = processor;
        this.transfers = transfers;
        this.hasher = hasher;
        this.retry = retry;
    }

    public TransferResult transfer(String idempotencyKey, TransferRequest request) {
        String requestHash = hasher.hash(request);
        for (int attempt = 1; ; attempt++) {
            try {
                return processor.execute(idempotencyKey, requestHash, request);
            } catch (ConcurrencyFailureException e) {
                // Another transaction changed one of the accounts first (optimistic lock).
                if (attempt >= retry.maxAttempts()) {
                    throw new TransferConflictException(attempt, e);
                }
                log.debug("Optimistic lock conflict for key {} (attempt {}), retrying", idempotencyKey, attempt);
                backOff(attempt);
            } catch (DataIntegrityViolationException e) {
                // Two requests with the same key raced past the lookup; the unique constraint let only one
                // insert win. Answer this one with the winner's stored result.
                return processor.findPrevious(idempotencyKey, requestHash).orElseThrow(() -> e);
            }
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id) {
        return transfers.findById(id).map(TransferResponse::from).orElseThrow(() -> new TransferNotFoundException(id));
    }

    /** Exponential backoff with full jitter, so competing requests do not retry in lockstep. */
    private void backOff(int attempt) {
        long cap = Math.min(retry.maxBackoff().toMillis(), retry.initialBackoff().toMillis() << (attempt - 1));
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(cap + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransferConflictException(attempt, e);
        }
    }
}
