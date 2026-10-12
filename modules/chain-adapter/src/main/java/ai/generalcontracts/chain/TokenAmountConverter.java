package ai.generalcontracts.chain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class TokenAmountConverter {
    private TokenAmountConverter() {
    }

    public static BigInteger toBaseUnits(String decimalAmount, int tokenDecimals) {
        if (tokenDecimals < 0 || tokenDecimals > 255) {
            throw new IllegalArgumentException("Token decimals are outside the supported range");
        }
        try {
            BigDecimal amount = new BigDecimal(decimalAmount);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Transfer amount must be positive");
            }
            return amount
                .movePointRight(tokenDecimals)
                .setScale(0, RoundingMode.UNNECESSARY)
                .toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Amount cannot be represented in configured token base units", exception);
        }
    }
}
