package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class BuilderValidationTest {

	private DefaultBankData bank() {
		DefaultBankData bd = new DefaultBankData();
		bd.setBlz("10000000");
		bd.setName("Testbank");
		bd.setPinTanVersion("300");
		return bd;
	}

	@Test
	void buildWithoutPinFails() {
		EasyFinBuilder builder = EasyFinFactory.builder().bankData(bank());
		IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);
		assertTrue(ex.getMessage().contains("pin"));
	}

	@Test
	void buildWithoutBankDataFails() {
		EasyFinBuilder builder = EasyFinFactory.builder().pin("1234");
		IllegalStateException ex = assertThrows(IllegalStateException.class, builder::build);
		assertTrue(ex.getMessage().contains("bankData"));
	}

	@Test
	void buildWithRequiredFieldsSucceeds() {
		EasyFin ef = EasyFinFactory.builder().pin("1234").bankData(bank())
				.passportDirectory(Paths.get(System.getProperty("java.io.tmpdir"))).build();
		try {
			assertNotNull(ef);
		} finally {
			ef.clean();
		}
	}

	@Test
	void ambiguousBankLookupFails() {
		// "." matches every bank name in the bundled directory.
		EasyFinBuilder builder = EasyFinFactory.builder();
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> builder.bankData("."));
		assertTrue(ex.getMessage().contains("ambiguous"));
	}

	@Test
	void unknownBankLookupFails() {
		EasyFinBuilder builder = EasyFinFactory.builder();
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> builder.bankData("ZZZ_NO_SUCH_BANK_QQQ_123"));
		assertTrue(ex.getMessage().contains("no matching bank"));
	}

	@Test
	void cleanIsIdempotentAndBlocksReuse() {
		EasyFin ef = EasyFinFactory.builder().pin("1234").bankData(bank()).build();
		ef.clean();
		ef.clean(); // must not throw
		assertThrows(IllegalStateException.class, ef::getAccounts);
	}

	@Test
	void reflowToStringExercisesEquals() {
		DefaultBankData a = bank();
		DefaultBankData b = bank();
		assertEquals(a, b);
	}
}
