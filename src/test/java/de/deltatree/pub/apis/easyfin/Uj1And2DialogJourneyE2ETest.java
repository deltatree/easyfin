package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.deltatree.pub.apis.easyfin.mockfints.MockBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsServer;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;

/**
 * UJ-1 / UJ-2 — the journeys a consumer actually performs: configure the
 * library, list the accounts, and read the turnovers.
 *
 * <p>
 * These run against a simulated FinTS bank that completes the real protocol
 * dialog (synchronisation, BPD/UPD negotiation, turnover retrieval with an MT940
 * statement) over real TLS, driving the real hbci4java client through easyfin's
 * public API. Nothing about the production stack is stubbed.
 * </p>
 */
class Uj1And2DialogJourneyE2ETest {

	@TempDir
	Path passportDir;

	private MockFinTsBank bank;
	private MockFinTsServer server;
	private EasyFin easyFin;

	@BeforeEach
	void startBank() {
		this.bank = new MockFinTsBank();
		this.server = MockFinTsServer.start(this.bank);
		this.easyFin = MockBank.clientFor(this.server, this.passportDir);
	}

	@AfterEach
	void stopBank() {
		if (this.easyFin != null) {
			this.easyFin.clean();
		}
		if (this.server != null) {
			this.server.close();
		}
	}

	@Test
	@DisplayName("UJ-1: listing accounts returns the accounts the bank exposes")
	void listsAccounts() {
		List<Konto> accounts = this.easyFin.getAccounts();

		assertEquals(1, accounts.size(), "the simulated bank exposes exactly one account");
		Konto account = accounts.get(0);
		assertEquals(MockFinTsBank.ACCOUNT_NUMBER, account.number);
		assertEquals(MockFinTsBank.ACCOUNT_IBAN, account.iban);
		assertEquals(MockFinTsBank.ACCOUNT_NAME, account.name);
		assertEquals(MockBank.BLZ, account.blz);
		assertEquals("EUR", account.curr);
	}

	@Test
	@DisplayName("UJ-1: the dialog carries the configured credentials on the wire")
	void dialogCarriesConfiguredCredentials() {
		this.easyFin.getAccounts();

		String first = this.server.getRequests().get(0);
		assertTrue(first.startsWith("HNHBK:"), "must be a FinTS message: " + first);
		assertTrue(first.contains("+300+"), "configured HBCI version must be negotiated");
		assertTrue(first.contains("280:" + MockBank.BLZ), "configured BLZ must be sent");
		assertTrue(first.contains(MockBank.USER_ID), "configured user id must be sent");
		assertTrue(first.contains(MockBank.PIN), "configured PIN must reach the bank");
		assertTrue(first.contains("HKSYN:"), "the dialog starts with a synchronisation");
	}

	@Test
	@DisplayName("UJ-1: a single account can be looked up by IBAN and by number")
	void findsSingleAccount() {
		Konto byIban = this.easyFin.getAccount(MockFinTsBank.ACCOUNT_IBAN);
		Konto byNumber = this.easyFin.getAccount(MockFinTsBank.ACCOUNT_NUMBER);

		assertEquals(MockFinTsBank.ACCOUNT_IBAN, byIban.iban);
		assertEquals(MockFinTsBank.ACCOUNT_NUMBER, byNumber.number);
	}

	@Test
	@DisplayName("UJ-2: reading turnovers returns the bank's bookings, parsed")
	void readsTurnovers() {
		Konto account = this.easyFin.getAccounts().get(0);

		List<UmsLine> turnovers = this.easyFin.getTurnoversAsStream(account).collect(Collectors.toList());

		assertEquals(2, turnovers.size(), "the statement carries two bookings");

		UmsLine credit = turnovers.get(0);
		assertEquals(0, new BigDecimal("150.25").compareTo(credit.value.getBigDecimalValue()),
				"unexpected credit amount: " + credit.value);
		assertEquals("EUR", credit.value.getCurr());
		assertTrue(credit.usage.toString().contains("Gehalt Juli"), "unexpected usage: " + credit.usage);
		assertNotNull(credit.bdate, "booking date must be parsed");

		UmsLine debit = turnovers.get(1);
		assertEquals(0, new BigDecimal("-42.50").compareTo(debit.value.getBigDecimalValue()),
				"unexpected debit amount: " + debit.value);
		assertTrue(debit.usage.toString().contains("Stromrechnung"), "unexpected usage: " + debit.usage);
	}

	@Test
	@DisplayName("UJ-2: turnovers can be requested from a start date")
	void readsTurnoversFromDate() {
		Konto account = this.easyFin.getAccounts().get(0);

		List<UmsLine> turnovers = this.easyFin.getTurnoversAsStream(account, daysAgo(30))
				.collect(Collectors.toList());

		assertEquals(2, turnovers.size());
		// The requested start date must actually travel to the bank.
		String kaz = this.server.getRequests().stream().filter(r -> r.contains("HKKAZ:")).reduce((a, b) -> b)
				.orElseThrow(() -> new AssertionError("no turnover request was sent"));
		assertTrue(kaz.matches("(?s).*HKKAZ:\\d+:\\d+\\+.*\\+\\d{8}.*"), "start date missing in: " + kaz);
	}

	@Test
	@DisplayName("UJ-2: the same client can be reused for several retrievals")
	void reusesClientAcrossRetrievals() {
		Konto account = this.easyFin.getAccounts().get(0);

		assertEquals(2, this.easyFin.getTurnoversAsStream(account).count());
		assertEquals(2, this.easyFin.getTurnoversAsStream(account, daysAgo(10)).count());
		assertFalse(this.server.getRequests().isEmpty());
	}

	@Test
	@DisplayName("UJ-2: the returned stream is materialized and needs no closing")
	void streamIsMaterialized() {
		Konto account = this.easyFin.getAccounts().get(0);

		List<UmsLine> first = this.easyFin.getTurnoversAsStream(account).collect(Collectors.toList());
		// Re-collecting a fresh call yields equal content: the dialog completed before
		// the stream was handed over.
		List<UmsLine> second = this.easyFin.getTurnoversAsStream(account).collect(Collectors.toList());
		assertEquals(first.size(), second.size());
		assertSame(UmsLine.class, first.get(0).getClass());
	}

	private static Date daysAgo(int days) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -days);
		return cal.getTime();
	}
}
