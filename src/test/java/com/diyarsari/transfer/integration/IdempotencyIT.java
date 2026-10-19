package com.diyarsari.transfer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIT extends AbstractIntegrationTest {

    @Test
    void sameKeySentTwiceMovesMoneyOnce() {
        long from = createAccount("1000.00");
        long to = createAccount("0.00");
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = transfer(key, from, to, "100.00");
        ResponseEntity<JsonNode> second = transfer(key, from, to, "100.00");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(first.getBody().get("status").asText()).isEqualTo("COMPLETED");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getBody()).isEqualTo(first.getBody());

        assertThat(balanceOf(from)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
        assertThat(ledgerEntryCount(from)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIs422AndMovesNothing() {
        long from = createAccount("1000.00");
        long to = createAccount("0.00");
        String key = UUID.randomUUID().toString();
        transfer(key, from, to, "100.00");

        ResponseEntity<JsonNode> reused = transfer(key, from, to, "200.00");

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(reused.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(reused.getBody().get("errorCode").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(balanceOf(from)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
    }

    @Test
    void equivalentAmountFormattingIsTheSameRequest() {
        long from = createAccount("1000.00");
        long to = createAccount("0.00");
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = transfer(key, from, to, "100");
        ResponseEntity<JsonNode> second = transfer(key, from, to, "100.00");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(balanceOf(from)).isEqualByComparingTo("900.00");
    }

    @Test
    void missingKeyIs400() {
        long from = createAccount("1000.00");
        long to = createAccount("0.00");

        ResponseEntity<JsonNode> response = transfer(null, from, to, "100.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("Idempotency-Key");
        assertThat(balanceOf(from)).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectedTransferIsStoredAndReplayedAsRejected() {
        long from = createAccount("50.00");
        long to = createAccount("0.00");
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = transfer(key, from, to, "100.00");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("status").asText()).isEqualTo("REJECTED");
        assertThat(first.getBody().get("failureReason").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        // Funding the account later does not change the outcome of an already answered request.
        transfer(UUID.randomUUID().toString(), createAccount("500.00"), from, "100.00");
        ResponseEntity<JsonNode> replay = transfer(key, from, to, "100.00");

        assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(replay.getBody().get("status").asText()).isEqualTo("REJECTED");
        assertThat(balanceOf(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void currencyMismatchIsRejected() {
        long from = createAccount("100.00", "TRY");
        long to = createAccount("0.00", "EUR");

        ResponseEntity<JsonNode> response = transfer(UUID.randomUUID().toString(), from, to, "10.00");

        assertThat(response.getBody().get("status").asText()).isEqualTo("REJECTED");
        assertThat(response.getBody().get("failureReason").asText()).isEqualTo("CURRENCY_MISMATCH");
        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeySentConcurrentlyMovesMoneyOnce() throws Exception {
        long from = createAccount("1000.00");
        long to = createAccount("0.00");
        String key = UUID.randomUUID().toString();
        int clients = 10;
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            for (int i = 0; i < clients; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return transfer(key, from, to, "100.00");
                }));
            }
            start.countDown();
        }

        List<String> ids = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            ResponseEntity<JsonNode> response = f.get();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            ids.add(response.getBody().get("id").asText());
        }
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(balanceOf(from)).isEqualByComparingTo("900.00");
        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
    }

    @Test
    void unknownAccountIs404AndNothingIsStored() {
        long from = createAccount("100.00");
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> response = transfer(key, from, 999_999, "10.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?", Integer.class, key))
                .isZero();
    }

    @Test
    void transferCanBeFetchedById() {
        long from = createAccount("100.00");
        long to = createAccount("0.00");
        JsonNode created = transfer(UUID.randomUUID().toString(),
                Map.of("fromAccountId", from, "toAccountId", to, "amount", new BigDecimal("12.34"), "description", "Lunch"))
                .getBody();

        JsonNode fetched = http.getForObject("/api/transfers/" + created.get("id").asText(), JsonNode.class);

        assertThat(fetched).isEqualTo(created);
        assertThat(fetched.get("description").asText()).isEqualTo("Lunch");
        assertThat(http.getForEntity("/api/transfers/" + UUID.randomUUID(), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
