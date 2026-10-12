package ai.generalcontracts.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TokenAmountConverterTest {
    @Test
    void convertsExactDecimalsToIntegerBaseUnits() {
        assertEquals(new BigInteger("1234500"), TokenAmountConverter.toBaseUnits("1.2345", 6));
        assertEquals(new BigInteger("1"), TokenAmountConverter.toBaseUnits("0.000000000000000001", 18));
    }

    @Test
    void rejectsAmountsThatCannotBeRepresentedWithoutRounding() {
        assertThrows(IllegalArgumentException.class, () -> TokenAmountConverter.toBaseUnits("0.0000001", 6));
        assertThrows(IllegalArgumentException.class, () -> TokenAmountConverter.toBaseUnits("0", 18));
        assertThrows(IllegalArgumentException.class, () -> TokenAmountConverter.toBaseUnits("NaN", 18));
    }

    @Test
    void validatesTokenDecimals() {
        assertThrows(IllegalArgumentException.class, () -> TokenAmountConverter.toBaseUnits("1", -1));
        assertThrows(IllegalArgumentException.class, () -> TokenAmountConverter.toBaseUnits("1", 256));
    }
}
