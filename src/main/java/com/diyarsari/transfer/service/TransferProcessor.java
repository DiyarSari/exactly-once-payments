package com.diyarsari.transfer.service;

import com.diyarsari.transfer.domain.Account;
import com.diyarsari.transfer.domain.LedgerEntry;
import com.diyarsari.transfer.domain.RejectionReason;
import com.diyarsari.transfer.domain.Transfer;
import com.diyarsari.transfer.domain.TransferRules;
import com.diyarsari.transfer.dto.TransferRequest;
import com.diyarsari.transfer.dto.TransferResponse;
import com.diyarsari.transfer.exception.AccountNotFoundException;
import com.diyarsari.transfer.exception.IdempotencyKeyReusedException;
import com.diyarsari.transfer.repository.AccountRepository;
import com.diyarsari.transfer.repository.LedgerEntryRepository;
import com.diyarsari.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Executes one attempt of a transfer in a single database transaction: idempotency check, balance
 * updates, ledger entries and the transfer record either all commit or none do.
 * <p>
 * A lost optimistic-lock race surfaces as a {@code ConcurrencyFailureException} on commit; retrying
 * is the caller's job ({@link TransferService}), because a retry needs a brand-new transaction.
 */
@Service
public class TransferProcessor {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;

    public TransferProcessor(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
    }

    @Transactional
    public TransferResult execute(String idempotencyKey, String requestHash, TransferRequest request) {
        Optional<TransferResult> previous = findPrevious(idempotencyKey, requestHash);
        if (previous.isPresent()) {
            return previous.get();
        }

        // Always load (and therefore later update) accounts in ascending id order.
        List<Account> locked = accounts.findAllByIdInOrderByIdAsc(List.of(request.fromAccountId(), request.toAccountId()));
        Account from = pick(locked, request.fromAccountId());
        Account to = pick(locked, request.toAccountId());

        Optional<RejectionReason> rejection = TransferRules.check(from, to, request.amount());
        if (rejection.isPresent()) {
            Transfer rejected = transfers.save(Transfer.rejected(idempotencyKey, requestHash, from, to,
                    request.amount(), request.description(), rejection.get()));
            return new TransferResult(TransferResponse.from(rejected), false);
        }

        from.debit(request.amount());
        to.credit(request.amount());
        Transfer completed = transfers.save(Transfer.completed(idempotencyKey, requestHash, from, to,
                request.amount(), request.description()));
        ledger.saveAll(List.of(LedgerEntry.debit(completed), LedgerEntry.credit(completed)));
        return new TransferResult(TransferResponse.from(completed), false);
    }

    /**
     * Returns the stored outcome for this key if there is one. Throws if the key belongs to a different request.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResult> findPrevious(String idempotencyKey, String requestHash) {
        return transfers.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            return new TransferResult(TransferResponse.from(existing), true);
        });
    }

    private static Account pick(List<Account> loaded, Long id) {
        return loaded.stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
