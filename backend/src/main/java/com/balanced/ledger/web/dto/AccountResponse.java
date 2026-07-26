package com.balanced.ledger.web.dto;

import com.balanced.ledger.domain.Account;
import com.balanced.ledger.domain.AccountStatus;
import com.balanced.ledger.domain.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        AccountType accountType,
        String currency,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
