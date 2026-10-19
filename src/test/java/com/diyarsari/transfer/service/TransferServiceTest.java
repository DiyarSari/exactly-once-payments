package com.diyarsari.transfer.service;

import com.diyarsari.transfer.config.TransferRetryProperties;
import com.diyarsari.transfer.domain.TransferStatus;
import com.diyarsari.transfer.dto.TransferRequest;
import com.diyarsari.transfer.dto.TransferResponse;
import com.diyarsari.transfer.exception.IdempotencyKeyReusedException;
import com.diyarsari.transfer.exception.TransferConflictException;
import com.diyarsari.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private static final String KEY = "key-1";
    private static final TransferRequest REQUEST = new TransferRequest(1L, 2L, new BigDecimal("10.00"), null);
    private static final TransferResult RESULT = new TransferResult(new TransferResponse(UUID.randomUUID(), 1L, 2L,
            new BigDecimal("10.00"), "TRY", null, TransferStatus.COMPLETED, null, Instant.now()), false);

    private TransferProcessor processor;
    private TransferService service;

    @BeforeEach
    void setUp() {
        processor = mock(TransferProcessor.class);
        service = new TransferService(processor, mock(TransferRepository.class), new RequestHasher(),
                new TransferRetryProperties(3, Duration.ofMillis(1), Duration.ofMillis(2)));
    }

    private static ObjectOptimisticLockingFailureException lockConflict() {
        return new ObjectOptimisticLockingFailureException("Account", 1L);
    }

    @Test
    void retriesAfterOptimisticLockConflict() {
        when(processor.execute(eq(KEY), anyString(), eq(REQUEST)))
                .thenThrow(lockConflict())
                .thenThrow(lockConflict())
                .thenReturn(RESULT);

        assertThat(service.transfer(KEY, REQUEST)).isEqualTo(RESULT);
        verify(processor, times(3)).execute(eq(KEY), anyString(), eq(REQUEST));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(processor.execute(eq(KEY), anyString(), eq(REQUEST))).thenThrow(lockConflict());

        assertThatThrownBy(() -> service.transfer(KEY, REQUEST)).isInstanceOf(TransferConflictException.class);
        verify(processor, times(3)).execute(eq(KEY), anyString(), eq(REQUEST));
    }

    @Test
    void duplicateKeyRaceReturnsTheWinnersResult() {
        TransferResult replay = new TransferResult(RESULT.transfer(), true);
        when(processor.execute(eq(KEY), anyString(), eq(REQUEST)))
                .thenThrow(new DataIntegrityViolationException("uk_transfers_idempotency_key"));
        when(processor.findPrevious(eq(KEY), anyString())).thenReturn(Optional.of(replay));

        assertThat(service.transfer(KEY, REQUEST)).isEqualTo(replay);
    }

    @Test
    void unrelatedIntegrityViolationIsRethrown() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("other");
        when(processor.execute(eq(KEY), anyString(), eq(REQUEST))).thenThrow(violation);
        when(processor.findPrevious(eq(KEY), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(KEY, REQUEST)).isSameAs(violation);
    }

    @Test
    void keyReuseIsNotRetried() {
        when(processor.execute(eq(KEY), anyString(), any())).thenThrow(new IdempotencyKeyReusedException(KEY));

        assertThatThrownBy(() -> service.transfer(KEY, REQUEST)).isInstanceOf(IdempotencyKeyReusedException.class);
        verify(processor, times(1)).execute(eq(KEY), anyString(), any());
    }
}
