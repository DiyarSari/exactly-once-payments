package com.diyarsari.transfer.service;

import com.diyarsari.transfer.dto.TransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    private final RequestHasher hasher = new RequestHasher();

    private String hash(long from, long to, String amount, String description) {
        return hasher.hash(new TransferRequest(from, to, new BigDecimal(amount), description));
    }

    @Test
    void producesSha256Hex() {
        assertThat(hash(1, 2, "10.00", null)).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void equivalentAmountsHashTheSame() {
        assertThat(hash(1, 2, "100", null)).isEqualTo(hash(1, 2, "100.00", null)).isEqualTo(hash(1, 2, "100.0", null));
    }

    @Test
    void anyChangeInTheRequestChangesTheHash() {
        String original = hash(1, 2, "100.00", "rent");
        assertThat(hash(1, 3, "100.00", "rent")).isNotEqualTo(original);
        assertThat(hash(2, 1, "100.00", "rent")).isNotEqualTo(original);
        assertThat(hash(1, 2, "100.01", "rent")).isNotEqualTo(original);
        assertThat(hash(1, 2, "100.00", "Rent")).isNotEqualTo(original);
        assertThat(hash(1, 2, "100.00", null)).isNotEqualTo(original);
    }
}
