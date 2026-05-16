package net.auctionapp.common.utils;

import java.math.BigDecimal;

public final class MoneyUtil {
    private static final int MAX_MONEY_SCALE = 2;

    private MoneyUtil() {
    }

    public static void requirePositiveMoney(BigDecimal amount, String label) {
        String amountLabel = (label == null || label.isBlank()) ? "Amount" : label;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(amountLabel + " must be greater than 0.");
        }
        if (amount.stripTrailingZeros().scale() > MAX_MONEY_SCALE) {
            throw new IllegalArgumentException(amountLabel + " cannot have more than 2 decimal places.");
        }
    }
}
