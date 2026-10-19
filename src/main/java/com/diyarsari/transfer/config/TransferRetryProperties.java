package com.diyarsari.transfer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Bounded retry for transfers that lose an optimistic-lock race. */
@Validated
@ConfigurationProperties(prefix = "transfer.retry")
public record TransferRetryProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff
) {
}
