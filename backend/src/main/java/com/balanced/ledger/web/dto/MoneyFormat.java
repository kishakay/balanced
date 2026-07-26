package com.balanced.ledger.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String format(long amountCents) {
        return BigDecimal.valueOf(amountCents, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
