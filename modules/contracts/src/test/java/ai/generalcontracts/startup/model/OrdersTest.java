package ai.generalcontracts.startup.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrdersTest {

    @Test
    void exposesOrderPropertiesThroughGeneratedAccessors() {
        Orders order = new Orders();
        order.setId("order-123");
        order.setCustomer("Example Customer");
        order.setAmount(125.50);
        order.setStatus("PENDING");

        assertAll(
            () -> assertEquals("order-123", order.getId()),
            () -> assertEquals("Example Customer", order.getCustomer()),
            () -> assertEquals(125.50, order.getAmount()),
            () -> assertEquals("PENDING", order.getStatus())
        );
    }
}
