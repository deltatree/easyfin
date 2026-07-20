package de.deltatree.pub.apis.easyfin.mockfints;

import de.deltatree.pub.apis.easyfin.BankData;
import de.deltatree.pub.apis.easyfin.DefaultBankData;
import de.deltatree.pub.apis.easyfin.EasyFin;
import de.deltatree.pub.apis.easyfin.EasyFinBuilder;
import de.deltatree.pub.apis.easyfin.EasyFinFactory;
import java.nio.file.Path;

/**
 * Test fixtures for wiring easyfin against a {@link MockFinTsServer} exactly the
 * way a consumer wires it against a real bank.
 */
public final class MockBank {

	public static final String BLZ = "12345678";
	public static final String USER_ID = "testuser";
	public static final String CUSTOMER_ID = "testcustomer";
	public static final String PIN = "543210";
	public static final String TAN = "112233";

	private MockBank() {
	}

	/** Bank data pointing at the given mock endpoint. */
	public static BankData bankDataFor(String pinTanAddress) {
		DefaultBankData bank = new DefaultBankData();
		bank.setBlz(BLZ);
		bank.setName("Mock Testbank");
		bank.setLocation("Teststadt");
		bank.setBic("MOCKDEFFXXX");
		bank.setPinTanVersion("300");
		bank.setPinTanAddress(pinTanAddress);
		return bank;
	}

	/** A builder configured the way the usage example configures a real bank. */
	public static EasyFinBuilder builderFor(String pinTanAddress, Path passportDirectory) {
		return EasyFinFactory.builder() //
				.bankData(bankDataFor(pinTanAddress)) //
				.userId(USER_ID) //
				.customerId(CUSTOMER_ID) //
				.pin(PIN) //
				.tanCallback(ctx -> TAN) //
				.passportDirectory(passportDirectory) //
				// The mock uses a self-signed certificate.
				.additionalHBCIConfiguration("client.passport.PinTan.checkcert", "0");
	}

	public static EasyFin clientFor(MockFinTsServer server, Path passportDirectory) {
		return builderFor(server.getPinTanAddress(), passportDirectory).build();
	}

	/**
	 * A responder that answers every request with a payload the FinTS parser
	 * rejects — used to drive the error journeys deterministically.
	 */
	public static String rejectingResponse(String request) {
		return "INVALID";
	}
}
