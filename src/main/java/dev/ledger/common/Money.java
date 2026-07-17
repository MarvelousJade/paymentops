package dev.ledger.common;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/** Money is BIGINT minor units (cents) internally, decimal strings ("12.34") at the API edge. */
public final class Money {

    private Money() {
    }

    public static String toDecimalString(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2).toPlainString();
    }

    public static long toCents(String decimal) {
        try {
            BigDecimal value = new BigDecimal(decimal);
            if (value.scale() > 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Amounts have at most 2 decimal places");
            }
            long cents = value.movePointRight(2).longValueExact();
            if (cents <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be positive");
            }
            return cents;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid amount");
        }
    }
}
