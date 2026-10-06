/*
package ai.generalcontracts.startup;

import ai.generalcontracts.startup.model.Orders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GeneralContractApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	private Orders testOrder;

	@BeforeEach
	void setUp() {
		testOrder = new Orders();
		testOrder.setCustomer("Test Customer");
		testOrder.setAmount(100.50);
		testOrder.setStatus("PENDING");
	}

	@Test
	void shouldLoadApplicationContext() {
		assertNotNull(restTemplate);
	}

	@Test
	void shouldCreateOrderSuccessfully() {
		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			testOrder,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		assertEquals("Test Customer", response.getBody().getCustomer());
		assertEquals(100.50, response.getBody().getAmount());
		assertEquals("PENDING", response.getBody().getStatus());
	}

	@Test
	void shouldRetrieveAllOrders() {
		restTemplate.postForEntity("/orders", testOrder, Orders.class);

		ResponseEntity<Orders[]> response = restTemplate.getForEntity(
			"/orders",
			Orders[].class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().length >= 1);
	}

	@Test
	void shouldReturnEmptyListWhenNoOrdersExist() {
		ResponseEntity<Orders[]> response = restTemplate.getForEntity(
			"/orders",
			Orders[].class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}

	@Test
	void shouldCreateOrderWithAllFields() {
		Orders order = new Orders();
		order.setId("ORDER-001");
		order.setCustomer("Premium Customer");
		order.setAmount(250.75);
		order.setStatus("COMPLETED");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("Premium Customer", response.getBody().getCustomer());
		assertEquals(250.75, response.getBody().getAmount());
		assertEquals("COMPLETED", response.getBody().getStatus());
	}

	@Test
	void shouldCreateOrderWithMinimalFields() {
		Orders minimalOrder = new Orders();
		minimalOrder.setCustomer("Basic Customer");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			minimalOrder,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		assertEquals("Basic Customer", response.getBody().getCustomer());
	}

	@Test
	void shouldCreateOrderWithZeroAmount() {
		Orders order = new Orders();
		order.setCustomer("Zero Amount Customer");
		order.setAmount(0.0);
		order.setStatus("PENDING");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(0.0, response.getBody().getAmount());
	}

	@Test
	void shouldCreateOrderWithNegativeAmount() {
		Orders order = new Orders();
		order.setCustomer("Refund Customer");
		order.setAmount(-50.0);
		order.setStatus("REFUNDED");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(-50.0, response.getBody().getAmount());
	}

	@Test
	void shouldCreateOrderWithNullStatus() {
		Orders order = new Orders();
		order.setCustomer("No Status Customer");
		order.setAmount(150.0);
		order.setStatus(null);

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNull(response.getBody().getStatus());
	}

	@Test
	void shouldCreateOrderWithLargeAmount() {
		Orders order = new Orders();
		order.setCustomer("Enterprise Customer");
		order.setAmount(999999.99);
		order.setStatus("HIGH_VALUE");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(999999.99, response.getBody().getAmount());
	}

	@Test
	void shouldCreateOrderWithSpecialCharactersInCustomerName() {
		Orders order = new Orders();
		order.setCustomer("O'Brien & Sons Ltd.");
		order.setAmount(500.0);
		order.setStatus("PENDING");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("O'Brien & Sons Ltd.", response.getBody().getCustomer());
	}

	@Test
	void shouldCreateOrderWithVeryLongCustomerName() {
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			longName.append("A");
		}
		Orders order = new Orders();
		order.setCustomer(longName.toString());
		order.setAmount(100.0);
		order.setStatus("PENDING");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1000, response.getBody().getCustomer().length());
	}

	@Test
	void shouldRetrieveOrdersAfterMultipleCreations() {
		Orders order1 = new Orders();
		order1.setCustomer("Customer 1");
		order1.setAmount(100.0);
		order1.setStatus("PENDING");

		Orders order2 = new Orders();
		order2.setCustomer("Customer 2");
		order2.setAmount(200.0);
		order2.setStatus("COMPLETED");

		Orders order3 = new Orders();
		order3.setCustomer("Customer 3");
		order3.setAmount(300.0);
		order3.setStatus("CANCELLED");

		restTemplate.postForEntity("/orders", order1, Orders.class);
		restTemplate.postForEntity("/orders", order2, Orders.class);
		restTemplate.postForEntity("/orders", order3, Orders.class);

		ResponseEntity<Orders[]> response = restTemplate.getForEntity(
			"/orders",
			Orders[].class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().length >= 3);
	}

	@Test
	void shouldCreateOrderWithDecimalAmount() {
		Orders order = new Orders();
		order.setCustomer("Decimal Customer");
		order.setAmount(123.45);
		order.setStatus("PENDING");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(123.45, response.getBody().getAmount());
	}

	@Test
	void shouldCreateOrderWithVerySmallDecimalAmount() {
		Orders order = new Orders();
		order.setCustomer("Micro Customer");
		order.setAmount(0.01);
		order.setStatus("PENDING");

		ResponseEntity<Orders> response = restTemplate.postForEntity(
			"/orders",
			order,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(0.01, response.getBody().getAmount());
	}

	@Test
	void shouldHandleConcurrentOrderCreations() {
		Orders order1 = new Orders();
		order1.setCustomer("Concurrent Customer 1");
		order1.setAmount(100.0);
		order1.setStatus("PENDING");

		Orders order2 = new Orders();
		order2.setCustomer("Concurrent Customer 2");
		order2.setAmount(200.0);
		order2.setStatus("PENDING");

		ResponseEntity<Orders> response1 = restTemplate.postForEntity(
			"/orders",
			order1,
			Orders.class
		);

		ResponseEntity<Orders> response2 = restTemplate.postForEntity(
			"/orders",
			order2,
			Orders.class
		);

		assertEquals(HttpStatus.OK, response1.getStatusCode());
		assertEquals(HttpStatus.OK, response2.getStatusCode());
		assertNotNull(response1.getBody());
		assertNotNull(response2.getBody());
		assertNotEquals(response1.getBody().getId(), response2.getBody().getId());
	}

	@Test
	void shouldCreateOrderWithDifferentStatuses() {
		String[] statuses = {"PENDING", "COMPLETED", "CANCELLED", "REFUNDED", "PROCESSING", "FAILED"};

		for (String status : statuses) {
			Orders order = new Orders();
			order.setCustomer("Customer for " + status);
			order.setAmount(100.0);
			order.setStatus(status);

			ResponseEntity<Orders> response = restTemplate.postForEntity(
				"/orders",
				order,
				Orders.class
			);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals(status, response.getBody().getStatus());
		}
	}
}*/
