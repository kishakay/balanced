package com.balanced.ledger.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferRequest(
        @NotNull
        UUID fromAccountId,

        @NotNull
        UUID toAccountId,

        @Min(value = 1, message = "amountCents must be at least 1")
        long amountCents,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank @Size(max = 512)
        String description
) {
}
