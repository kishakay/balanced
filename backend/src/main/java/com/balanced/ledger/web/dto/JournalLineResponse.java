package com.balanced.ledger.web.dto;

import com.balanced.ledger.domain.Direction;
import com.balanced.ledger.domain.LedgerLine;
import java.util.UUID;

public record JournalLineResponse(
        UUID id,
        UUID accountId,
        String accountCode,
        String accountName,
        Direction direction,
        long amountCents,
        String amount,
        String currency
) {
    public static JournalLineResponse from(LedgerLine line) {
        return new JournalLineResponse(
                line.getId(),
                line.getAccount().getId(),
                line.getAccount().getCode(),
                line.getAccount().getName(),
                line.getDirection(),
                line.getAmountCents(),
                MoneyFormat.format(line.getAmountCents()),
                line.getCurrency()
        );
    }
}
