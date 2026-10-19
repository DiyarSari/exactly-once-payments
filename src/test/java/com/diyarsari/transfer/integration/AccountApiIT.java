package com.diyarsari.transfer.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountApiIT extends AbstractIntegrationTest {

    @Test
    void demoAccountsAreSeeded() {
        JsonNode first = http.getForObject("/api/accounts/1", JsonNode.class);
        assertThat(first.get("accountNumber").asText()).isEqualTo("TR809999900000000000000001");
        assertThat(first.get("ownerName").asText()).isEqualTo("Ayşe Yılmaz");
        assertThat(first.get("currency").asText()).isEqualTo("TRY");
    }

    @Test
    void createsAndFetchesAnAccount() {
        ResponseEntity<JsonNode> created = http.postForEntity("/api/accounts",
                Map.of("ownerName", "Ali Veli", "initialBalance", new BigDecimal("250.5")), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        JsonNode body = created.getBody();
        assertThat(body.get("accountNumber").asText()).matches("TR\\d{24}");
        assertThat(body.get("currency").asText()).isEqualTo("TRY");
        assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("250.50");

        JsonNode fetched = http.getForObject(created.getHeaders().getLocation(), JsonNode.class);
        assertThat(fetched).isEqualTo(body);
    }

    @Test
    void invalidAccountRequestIs400WithFieldErrors() {
        ResponseEntity<JsonNode> response = http.postForEntity("/api/accounts",
                Map.of("ownerName", "", "currency", "ABC", "initialBalance", new BigDecimal("-1")), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").fieldNames())
                .toIterable().containsExactlyInAnyOrder("ownerName", "currency", "initialBalance");
    }

    @Test
    void unknownAccountIs404Problem() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/accounts/999999", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("status").asInt()).isEqualTo(404);
        assertThat(response.getBody().get("detail").asText()).contains("999999");
    }

    @Test
    void ledgerIsPagedNewestFirst() {
        long from = createAccount("100.00");
        long to = createAccount("0.00");
        for (int i = 1; i <= 5; i++) {
            transfer(UUID.randomUUID().toString(), from, to, i + ".00");
        }

        JsonNode page0 = http.getForObject("/api/accounts/" + from + "/ledger?page=0&size=2", JsonNode.class);
        assertThat(page0.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("content")).hasSize(2);
        JsonNode newest = page0.get("content").get(0);
        assertThat(newest.get("entryType").asText()).isEqualTo("DEBIT");
        assertThat(newest.get("amount").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(newest.get("balanceAfter").decimalValue()).isEqualByComparingTo("85.00");

        JsonNode credits = http.getForObject("/api/accounts/" + to + "/ledger", JsonNode.class);
        assertThat(credits.get("content")).allSatisfy(e -> assertThat(e.get("entryType").asText()).isEqualTo("CREDIT"));

        assertThat(http.getForEntity("/api/accounts/" + from + "/ledger?size=500", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/accounts/999999/ledger", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
