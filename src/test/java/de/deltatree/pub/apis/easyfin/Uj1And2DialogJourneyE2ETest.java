package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.deltatree.pub.apis.easyfin.mockfints.MockBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsServer;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kapott.hbci.structures.Konto;

/**
 * UJ-1 / UJ-2 — end-to-end journeys driving easyfin's public API through the
 * real hbci4java client, real TLS transport and real FinTS PIN/TAN framing
 * against an embedded mock bank.
 *
 * <p>
 * These tests assert what the consumer's configuration actually produces on the
 * wire: that the configured endpoint is contacted, and that the credentials,
 * bank identification and protocol version the consumer supplied end up in a
 * well-formed FinTS dialog. Completing a full bank dialog (BPD/UPD negotiation
 * and MT940 delivery) is covered by the opt-in real-bank runner, see
 * {@link RealBankSmokeTest}.
 * </p>
 */
class Uj1And2DialogJourneyE2ETest {

	@TempDir
	Path passportDir;

	@Test
	@DisplayName("UJ-1: listing accounts opens a real FinTS dialog against the configured bank")
	void listingAccountsPerformsRealFinTsDialog() {
		AtomicReference<String> seen = new AtomicReference<>();
		try (MockFinTsServer server = MockFinTsServer.start(request -> {
			seen.compareAndSet(null, request);
			return MockBank.rejectingResponse(request);
		})) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			try {
				ef.getAccounts();
			} catch (IllegalStateException expected) {
				// The mock deliberately rejects; the dialog itself is what we assert on.
			} finally {
				ef.clean();
			}

			assertEquals(1, server.getRequests().size(), "client should contact the configured endpoint exactly once");
			String request = seen.get();
			assertNotNull(request);

			// A well-formed FinTS 3.0 PIN/TAN message envelope.
			assertTrue(request.startsWith("HNHBK:"), "message must start with the FinTS header: " + request);
			assertTrue(request.contains("HNVSK:"), "missing PIN/TAN encryption head");
			assertTrue(request.contains("HNVSD:"), "missing PIN/TAN payload container");
			assertTrue(request.endsWith("'"), "message must be segment-terminated");

			// The consumer's configuration must be what travels on the wire.
			assertTrue(request.contains("+300+"), "configured HBCI version 300 must be negotiated");
			assertTrue(request.contains("HKIDN:"), "missing identification segment");
			assertTrue(request.contains("280:" + MockBank.BLZ), "configured BLZ must be sent: " + request);
			assertTrue(request.contains(MockBank.USER_ID), "configured user id must be sent");
			assertTrue(request.contains("HKVVB:"), "missing processing-preparation segment");
			assertTrue(request.contains("HKSYN:"), "missing synchronisation segment");
			assertTrue(request.contains(MockBank.PIN), "configured PIN must be delivered via the callback chain");
		}
	}

	@Test
	@DisplayName("UJ-2: fetching turnovers for a date range opens a real FinTS dialog")
	void fetchingTurnoversPerformsRealFinTsDialog() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);

			Konto account = new Konto();
			account.blz = MockBank.BLZ;
			account.number = "1234567890";
			account.iban = "DE02120300000000202051";

			try {
				ef.getTurnoversAsStream(account, daysAgo(10), GetTurnoversModeEnum.KUmsAll);
			} catch (IllegalStateException expected) {
				// see above
			} finally {
				ef.clean();
			}

			assertFalse(server.getRequests().isEmpty(), "turnover retrieval must contact the bank");
			String request = server.getRequests().get(0);
			assertTrue(request.startsWith("HNHBK:"));
			assertTrue(request.contains("280:" + MockBank.BLZ));
			assertTrue(request.contains(MockBank.USER_ID));
		}
	}

	@Test
	@DisplayName("UJ-2: the camt retrieval mode uses the same dialog machinery")
	void camtModeAlsoOpensDialog() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);

			Konto account = new Konto();
			account.blz = MockBank.BLZ;
			account.number = "1234567890";

			try {
				ef.getTurnoversAsStream(account, daysAgo(3), GetTurnoversModeEnum.KUmsAllCamt);
			} catch (IllegalStateException expected) {
				// see above
			} finally {
				ef.clean();
			}

			assertFalse(server.getRequests().isEmpty());
		}
	}

	private static Date daysAgo(int days) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -days);
		return cal.getTime();
	}
}
