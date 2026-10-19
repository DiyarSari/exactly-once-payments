package com.diyarsari.transfer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Objects;

public record TransferRequest(
        @Schema(example = "1")
        @NotNull
        Long fromAccountId,

        @Schema(example = "2")
        @NotNull
        Long toAccountId,

        @Schema(example = "150.00")
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @Schema(example = "Rent - October")
        @Size(max = 140)
        String description
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "fromAccountId and toAccountId must be different")
    public boolean isDistinctAccounts() {
        return fromAccountId == null || !Objects.equals(fromAccountId, toAccountId);
    }
}
