package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.structures.Konto;

class GetAccountMatchingTest {

	private EasyFin ef;
	private Konto giro;
	private Konto savings;

	@BeforeEach
	void setUp() {
		DefaultBankData bd = new DefaultBankData();
		bd.setPinTanVersion("300");

		giro = new Konto();
		giro.blz = "10000000";
		giro.number = "1234567890";
		giro.iban = "DE02100000000000012345";
		giro.name = "Max Mustermann";
		giro.bic = "MARKDEF1100";

		savings = new Konto();
		savings.blz = "10000000";
		savings.number = "9998887776";
		savings.iban = "DE02100000009998887776";
		savings.name = "Max Mustermann Sparen";
		savings.bic = "MARKDEF1100";

		final List<Konto> accounts = List.of(giro, savings);
		ef = new DefaultEasyFin(bd, new HashMap<>()) {
			@Override
			public List<Konto> getAccounts() {
				return accounts;
			}
		};
	}

	@AfterEach
	void tearDown() {
		ef.clean();
	}

	@Test
	void findsByUniqueAccountNumber() {
		assertSame(giro, ef.getAccount("1234567890"));
	}

	@Test
	void findsByUniqueIban() {
		assertSame(savings, ef.getAccount("9998887776"));
	}

	@Test
	void ambiguousMatchThrows() {
		// Both accounts share the holder name prefix.
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ef.getAccount("Max Mustermann"));
		assertEquals(true, ex.getMessage().contains("Multiple results"));
	}

	@Test
	void noMatchThrows() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ef.getAccount("NOPE"));
		assertEquals(true, ex.getMessage().contains("no results"));
	}
}
