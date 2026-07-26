package com.balanced.ledger.web.dto;

import com.balanced.ledger.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,63}$",
                message = "code must be uppercase alphanumeric with optional _ or -")
        String code,

        @NotBlank @Size(max = 255)
        String name,

        @NotNull
        AccountType accountType,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency
) {
}
