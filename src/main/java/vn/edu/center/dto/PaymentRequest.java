package vn.edu.center.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record PaymentRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER") String method,
        @NotBlank @Size(max = 100) String referenceCode) {}