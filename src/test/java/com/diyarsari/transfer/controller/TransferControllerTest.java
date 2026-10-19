package com.diyarsari.transfer.controller;

import com.diyarsari.transfer.domain.TransferStatus;
import com.diyarsari.transfer.dto.TransferRequest;
import com.diyarsari.transfer.dto.TransferResponse;
import com.diyarsari.transfer.exception.IdempotencyKeyReusedException;
import com.diyarsari.transfer.exception.TransferConflictException;
import com.diyarsari.transfer.service.TransferResult;
import com.diyarsari.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String BODY = """
            {"fromAccountId": 1, "toAccountId": 2, "amount": 10.00}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Idempotency-Key")))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        verifyNoInteractions(transferService);
    }

    @Test
    void blankIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/transfers").header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        verifyNoInteractions(transferService);
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": 1, "toAccountId": 1, "amount": -5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.distinctAccounts").exists());
        verifyNoInteractions(transferService);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void reusedKeyIs422() throws Exception {
        when(transferService.transfer(eq("k1"), any(TransferRequest.class)))
                .thenThrow(new IdempotencyKeyReusedException("k1"));

        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key reused"))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void exhaustedRetriesIs409() throws Exception {
        when(transferService.transfer(eq("k1"), any(TransferRequest.class)))
                .thenThrow(new TransferConflictException(5, null));

        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void replayIsFlaggedInHeader() throws Exception {
        UUID id = UUID.randomUUID();
        TransferResponse response = new TransferResponse(id, 1L, 2L, new BigDecimal("10.00"), "TRY", null,
                TransferStatus.COMPLETED, null, Instant.now());
        when(transferService.transfer(eq("k1"), any(TransferRequest.class))).thenReturn(new TransferResult(response, true));

        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/transfers/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
