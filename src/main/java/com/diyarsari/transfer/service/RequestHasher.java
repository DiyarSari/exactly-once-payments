package com.diyarsari.transfer.service;

import com.diyarsari.transfer.domain.Money;
import com.diyarsari.transfer.dto.TransferRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fingerprints a transfer request so a reused Idempotency-Key can be compared against the original.
 * <p>
 * The hash is built from a canonical form of the parsed request rather than the raw bytes, so
 * {@code 100}, {@code 100.0} and {@code 100.00}, whitespace or field order do not count as a different request.
 */
@Component
public class RequestHasher {

    public String hash(TransferRequest request) {
        String canonical = String.join("|",
                String.valueOf(request.fromAccountId()),
                String.valueOf(request.toAccountId()),
                Money.of(request.amount()).toPlainString(),
                request.description() == null ? "" : request.description());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
