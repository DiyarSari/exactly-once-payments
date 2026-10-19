package com.diyarsari.transfer.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void validTransferRequestPasses() {
        assertThat(violatedFields(new TransferRequest(1L, 2L, new BigDecimal("10.50"), "rent"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void amountMustBePositive(String amount) {
        assertThat(violatedFields(new TransferRequest(1L, 2L, new BigDecimal(amount), null))).containsExactly("amount");
    }

    @Test
    void amountMustHaveAtMostTwoDecimals() {
        assertThat(violatedFields(new TransferRequest(1L, 2L, new BigDecimal("1.001"), null))).containsExactly("amount");
    }

    @Test
    void allTransferFieldsAreRequired() {
        assertThat(violatedFields(new TransferRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("fromAccountId", "toAccountId", "amount");
    }

    @Test
    void sourceAndTargetMustDiffer() {
        assertThat(violatedFields(new TransferRequest(1L, 1L, BigDecimal.TEN, null))).containsExactly("distinctAccounts");
    }

    @Test
    void descriptionIsLimited() {
        assertThat(violatedFields(new TransferRequest(1L, 2L, BigDecimal.TEN, "x".repeat(141))))
                .containsExactly("description");
    }

    @Test
    void validAccountRequestPasses() {
        assertThat(violatedFields(new CreateAccountRequest("Ali Veli", "TRY", new BigDecimal("0.00")))).isEmpty();
        assertThat(violatedFields(new CreateAccountRequest("Ali Veli", null, BigDecimal.ONE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"try", "XYZ", "TL", "TRYY"})
    void currencyMustBeIso4217(String currency) {
        assertThat(violatedFields(new CreateAccountRequest("Ali Veli", currency, BigDecimal.ONE)))
                .containsExactly("currency");
    }

    @Test
    void accountRequestRejectsBlankOwnerAndNegativeBalance() {
        assertThat(violatedFields(new CreateAccountRequest(" ", "TRY", new BigDecimal("-1"))))
                .containsExactlyInAnyOrder("ownerName", "initialBalance");
    }

    @Test
    void defaultsCurrencyToTry() {
        assertThat(new CreateAccountRequest("Ali Veli", null, BigDecimal.ONE).currencyOrDefault()).isEqualTo("TRY");
    }
}
