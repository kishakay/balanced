package com.balanced.ledger.web.dto;

import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String accountCode,
        String currency,
        long balanceCents,
        String balance,
        long debitTotalCents,
        long creditTotalCents
) {
}
