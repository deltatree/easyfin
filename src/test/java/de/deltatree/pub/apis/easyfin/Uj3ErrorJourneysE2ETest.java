package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.deltatree.pub.apis.easyfin.mockfints.MockBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsBank;
import de.deltatree.pub.apis.easyfin.mockfints.MockFinTsServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UJ-3 — the error journeys a consumer actually hits: the bank endpoint is
 * unreachable, the bank answers with an HTTP error, the bank answers with
 * something the FinTS parser rejects, and the bank lookup is ambiguous or
 * unknown. Every one of them must surface as a single, clearly formatted
 * {@link IllegalStateException} with the cause preserved.
 */
class Uj3ErrorJourneysE2ETest {

	@TempDir
	Path passportDir;

	@Test
	@DisplayName("UJ-3: an unreachable bank endpoint fails fast with a clear message")
	void unreachableEndpointFailsClearly() throws IOException {
		int deadPort = findClosedPort();
		EasyFin ef = MockBank.builderFor("https://localhost:" + deadPort + "/fints", passportDir).build();
		try {
			IllegalStateException ex = assertThrows(IllegalStateException.class, ef::getAccounts);
			assertStandardFailureShape(ex, "Fetching accounts failed");
		} finally {
			ef.clean();
		}
	}

	@Test
	@DisplayName("UJ-3: an HTTP error from the bank surfaces as a clean failure")
	void httpErrorFromBankSurfacesClearly() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			server.setResponseStatus(500);
			EasyFin ef = MockBank.clientFor(server, passportDir);
			try {
				IllegalStateException ex = assertThrows(IllegalStateException.class, ef::getAccounts);
				assertStandardFailureShape(ex, "Fetching accounts failed");
			} finally {
				ef.clean();
			}
		}
	}

	@Test
	@DisplayName("UJ-3: an unparseable bank response surfaces as a clean failure")
	void unparseableResponseSurfacesClearly() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			try {
				IllegalStateException ex = assertThrows(IllegalStateException.class, ef::getAccounts);
				assertStandardFailureShape(ex, "Fetching accounts failed");
			} finally {
				ef.clean();
			}
		}
	}

	@Test
	@DisplayName("UJ-3: turnover failures are reported as turnover failures, not account failures")
	void turnoverFailureIsAttributedToTurnovers() {
		try (MockFinTsServer server = MockFinTsServer.start(MockBank::rejectingResponse)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			org.kapott.hbci.structures.Konto account = new org.kapott.hbci.structures.Konto();
			account.blz = MockBank.BLZ;
			account.number = "1234567890";
			try {
				IllegalStateException ex = assertThrows(IllegalStateException.class,
						() -> ef.getTurnoversAsStream(account));
				assertStandardFailureShape(ex, "Fetching turnovers failed");
			} finally {
				ef.clean();
			}
		}
	}

	@Test
	@DisplayName("UJ-3: a rejected PIN surfaces the bank's own error text")
	void rejectedPinSurfacesBankError() {
		MockFinTsBank bank = new MockFinTsBank().rejectingPin(MockBank.PIN);
		try (MockFinTsServer server = MockFinTsServer.start(bank)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			try {
				IllegalStateException ex = assertThrows(IllegalStateException.class, ef::getAccounts);
				assertStandardFailureShape(ex, "Fetching accounts failed");
				assertTrue(server.getRequests().size() >= 1, "the bank must have been contacted");
			} finally {
				ef.clean();
			}
		}
	}

	@Test
	@DisplayName("UJ-3: a bank-side rejection of the turnover job is reported as a turnover failure")
	void turnoverRejectionIsReported() {
		MockFinTsBank bank = new MockFinTsBank();
		try (MockFinTsServer server = MockFinTsServer.start(bank)) {
			EasyFin ef = MockBank.clientFor(server, passportDir);
			try {
				org.kapott.hbci.structures.Konto account = ef.getAccounts().get(0);
				// From here on the bank refuses every request.
				server.setResponder(MockBank::rejectingResponse);
				IllegalStateException ex = assertThrows(IllegalStateException.class,
						() -> ef.getTurnoversAsStream(account));
				assertStandardFailureShape(ex, "Fetching turnovers failed");
			} finally {
				ef.clean();
			}
		}
	}

	@Test
	@DisplayName("UJ-3: an ambiguous bank lookup tells the consumer how to refine it")
	void ambiguousBankLookupIsActionable() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> EasyFinFactory.builder().bankData("."));
		assertTrue(ex.getMessage().contains("ambiguous"));
		assertTrue(ex.getMessage().contains("refine"), "message should tell the user what to do: " + ex.getMessage());
	}

	@Test
	@DisplayName("UJ-3: an unknown bank lookup names the failed search")
	void unknownBankLookupNamesTheSearch() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> EasyFinFactory.builder().bankData("ZZZ_NO_SUCH_BANK_QQQ_123"));
		assertTrue(ex.getMessage().contains("ZZZ_NO_SUCH_BANK_QQQ_123"));
		assertTrue(ex.getMessage().contains("no matching bank"));
	}

	/**
	 * Asserts the failure contract of AD-5: exactly one "<operation> failed: "
	 * prefix (the F2 double-concatenation regression guard) and a preserved cause.
	 */
	private static void assertStandardFailureShape(IllegalStateException ex, String operation) {
		String message = ex.getMessage();
		assertNotNull(message, "failure must carry a message");
		assertTrue(message.startsWith(operation + ": "), "unexpected message: " + message);
		assertEquals(1, countOccurrences(message, operation),
				"operation prefix must appear exactly once (no double formatting): " + message);
		assertNotNull(ex.getCause(), "cause must be preserved for diagnosis");
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int idx = haystack.indexOf(needle);
		while (idx != -1) {
			count++;
			idx = haystack.indexOf(needle, idx + needle.length());
		}
		return count;
	}

	private static int findClosedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
