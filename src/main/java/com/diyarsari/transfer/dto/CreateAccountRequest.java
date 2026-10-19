package com.diyarsari.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @Schema(example = "Ali Veli")
        @NotBlank @Size(max = 100)
        String ownerName,

        @Schema(example = "TRY", description = "ISO 4217 code, defaults to TRY")
        @IsoCurrency
        String currency,

        @Schema(example = "1000.00")
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
        BigDecimal initialBalance
) {

    public static final String DEFAULT_CURRENCY = "TRY";

    public String currencyOrDefault() {
        return currency == null ? DEFAULT_CURRENCY : currency;
    }
}
