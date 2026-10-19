package com.diyarsari.transfer.service;

import com.diyarsari.transfer.dto.TransferResponse;

/**
 * @param replayed true when the response comes from an earlier request with the same Idempotency-Key
 *                 and no money was moved by this call
 */
public record TransferResult(TransferResponse transfer, boolean replayed) {
}
