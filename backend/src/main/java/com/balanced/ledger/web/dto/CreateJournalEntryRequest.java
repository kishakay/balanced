package com.balanced.ledger.web.dto;

import com.balanced.ledger.domain.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateJournalEntryRequest(
        @NotBlank @Size(max = 512)
        String description,

        @NotEmpty @Valid
        List<Line> lines
) {
    public record Line(
            @NotNull
            UUID accountId,

            @NotNull
            Direction direction,

            @Min(value = 1, message = "amountCents must be at least 1")
            long amountCents,

            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
            String currency
    ) {
    }
}
