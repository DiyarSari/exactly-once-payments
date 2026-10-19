package com.diyarsari.transfer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    @AfterEach
    void ledgerInvariantsHold() {
        LedgerInvariants.assertHold(jdbc);
    }

    protected long createAccount(String balance) {
        return createAccount(balance, "TRY");
    }

    protected long createAccount(String balance, String currency) {
        ResponseEntity<JsonNode> response = http.postForEntity("/api/accounts",
                Map.of("ownerName", "Test Owner", "currency", currency, "initialBalance", new BigDecimal(balance)),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    protected BigDecimal balanceOf(long accountId) {
        return new BigDecimal(http.getForObject("/api/accounts/" + accountId, JsonNode.class).get("balance").asText());
    }

    protected ResponseEntity<JsonNode> transfer(String idempotencyKey, long from, long to, String amount) {
        return transfer(idempotencyKey, Map.of("fromAccountId", from, "toAccountId", to, "amount", new BigDecimal(amount)));
    }

    protected ResponseEntity<JsonNode> transfer(String idempotencyKey, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return http.exchange("/api/transfers", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected int ledgerEntryCount(long accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?", Integer.class, accountId);
    }
}
