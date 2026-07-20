package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kapott.hbci.structures.Konto;

/**
 * The full happy-path user journey against a real bank. This is the only place a
 * complete FinTS dialog (BPD/UPD negotiation, TAN challenge, MT940 delivery) can
 * genuinely be exercised, so it is opt-in and never runs in CI.
 *
 * <p>
 * Run it with:
 * </p>
 *
 * <pre>
 * EASYFIN_REALBANK_SEARCH="GENODEF1S02" \
 * EASYFIN_REALBANK_USERID="myVRNetKey" \
 * EASYFIN_REALBANK_CUSTOMERID="myVRNetKey" \
 * EASYFIN_REALBANK_PIN="1234" \
 *   ./gradlew realBankTest
 * </pre>
 *
 * <p>
 * The TAN is read interactively from stdin, exactly as an end user would enter
 * it.
 * </p>
 */
@Tag("real-bank")
@EnabledIfEnvironmentVariable(named = "EASYFIN_REALBANK_USERID", matches = ".+")
class RealBankSmokeTest {

	@Test
	@DisplayName("real bank: list accounts and read the last 10 days of turnovers")
	void listAccountsAndReadTurnovers() {
		EasyFin ef = EasyFinFactory.builder() //
				.bankData(env("EASYFIN_REALBANK_SEARCH")) //
				.userId(env("EASYFIN_REALBANK_USERID")) //
				.customerId(env("EASYFIN_REALBANK_CUSTOMERID")) //
				.pin(env("EASYFIN_REALBANK_PIN")) //
				.tanCallback(RealBankSmokeTest::askForTan) //
				.build();
		try {
			List<Konto> accounts = ef.getAccounts();
			assertNotNull(accounts);
			assertTrue(!accounts.isEmpty(), "the credentials should expose at least one account");

			for (Konto account : accounts) {
				System.out.println("account: " + account);
				ef.getTurnoversAsStream(account, daysAgo(10))
						.forEach(t -> System.out.println("  " + account.number + " " + t.value + " " + t.usage));
			}
		} finally {
			ef.clean();
			EasyFinFactory.clean();
		}
	}

	private static String env(String name) {
		String value = System.getenv(name);
		assertNotNull(value, "missing environment variable " + name);
		return value;
	}

	private static Date daysAgo(int days) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -days);
		return cal.getTime();
	}

	private static String askForTan(Map<String, String> challenge) {
		String prompt = challenge.get(MyHBCICallback.CHALLENGE_KEY);
		if (prompt != null) {
			System.out.println("TAN challenge: " + prompt);
		}
		System.out.print("Please enter TAN: ");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		try {
			return reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException("could not read TAN from stdin", e);
		}
	}
}
