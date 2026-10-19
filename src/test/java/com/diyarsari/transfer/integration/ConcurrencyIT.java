package com.diyarsari.transfer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIT extends AbstractIntegrationTest {

    private static final int CLIENT_RETRIES = 20;

    private final AtomicInteger conflicts = new AtomicInteger();

    /**
     * Acts like a well-behaved client: on 409 (server-side retries exhausted) it simply retries with the
     * SAME Idempotency-Key, which is always safe.
     */
    private ResponseEntity<JsonNode> transferWithClientRetry(String key, long from, long to, String amount) {
        ResponseEntity<JsonNode> response = transfer(key, from, to, amount);
        for (int i = 0; i < CLIENT_RETRIES && response.getStatusCode() == HttpStatus.CONFLICT; i++) {
            conflicts.incrementAndGet();
            response = transfer(key, from, to, amount);
        }
        return response;
    }

    private BigDecimal totalMoney() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class);
    }

    @Test
    void fiftyParallelTransfersNeverOverdrawTheAccount() throws Exception {
        long source = createAccount("1000.00");
        long targetA = createAccount("0.00");
        long targetB = createAccount("0.00");
        BigDecimal moneyBefore = totalMoney();

        int requests = 50; // 50 x 100.00 requested, only 1000.00 available
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                long target = i % 2 == 0 ? targetA : targetB;
                futures.add(pool.submit(() -> {
                    start.await();
                    return transferWithClientRetry(UUID.randomUUID().toString(), source, target, "100.00");
                }));
            }
            start.countDown();
        }

        int completed = 0;
        int rejected = 0;
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            ResponseEntity<JsonNode> response = f.get();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            switch (response.getBody().get("status").asText()) {
                case "COMPLETED" -> completed++;
                case "REJECTED" -> {
                    rejected++;
                    assertThat(response.getBody().get("failureReason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
                }
                default -> throw new AssertionError("Unexpected status " + response.getBody());
            }
        }

        // Exactly as many transfers as the balance allows went through - no more, no fewer.
        assertThat(completed).isEqualTo(10);
        assertThat(rejected).isEqualTo(40);
        assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(targetA).add(balanceOf(targetB))).isEqualByComparingTo("1000.00");
        // Money was moved, never created or destroyed.
        assertThat(totalMoney()).isEqualByComparingTo(moneyBefore);
        System.out.printf("50 parallel transfers: %d completed, %d rejected, %d client retries after 409%n",
                completed, rejected, conflicts.get());
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        long a = createAccount("1000.00");
        long b = createAccount("1000.00");
        BigDecimal moneyBefore = totalMoney();

        int requests = 40; // 20 x A->B and 20 x B->A, all at once
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                boolean forward = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    return transferWithClientRetry(UUID.randomUUID().toString(),
                            forward ? a : b, forward ? b : a, "10.00");
                }));
            }
            start.countDown();
        }

        for (Future<ResponseEntity<JsonNode>> f : futures) {
            ResponseEntity<JsonNode> response = f.get();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().get("status").asText()).isEqualTo("COMPLETED");
        }
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.00");
        assertThat(ledgerEntryCount(a)).isEqualTo(requests);
        assertThat(totalMoney()).isEqualByComparingTo(moneyBefore);
        System.out.printf("40 opposing transfers: all completed, %d client retries after 409%n", conflicts.get());
    }
}
