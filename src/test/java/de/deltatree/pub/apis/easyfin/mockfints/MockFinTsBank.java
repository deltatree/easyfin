package de.deltatree.pub.apis.easyfin.mockfints;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A simulated FinTS bank: a stateful dialog machine that answers the messages
 * hbci4java actually sends, so end-to-end tests can complete real user journeys
 * (list accounts, read turnovers, answer a TAN challenge) without a real bank.
 *
 * <p>
 * Dialogs are keyed by dialog id, so several clients can talk to one instance
 * concurrently. Responses are unencrypted and unsigned — see {@link FinTsMessage}.
 * </p>
 */
public final class MockFinTsBank implements Function<String, String> {

	/** The account the simulated bank exposes. */
	public static final String ACCOUNT_NUMBER = "1234567890";
	public static final String ACCOUNT_IBAN = "DE02120300000000202051";
	public static final String ACCOUNT_NAME = "Erika Mustermann";
	public static final String CUSTOMER_SYSTEM_ID = "SYSID4711";
	public static final String TAN_CHALLENGE = "Bitte TAN eingeben (Testbank)";

	private final AtomicInteger dialogCounter = new AtomicInteger();
	private final List<String> tansSeen = new ArrayList<>();

	private volatile boolean requireTanForTurnovers = false;
	private volatile String rejectPin;
	private volatile String expectedTan;

	public MockFinTsBank() {
		this.expectedTan = null;
	}

	/** Makes the bank reject dialogs whose PIN equals the given value. */
	public MockFinTsBank rejectingPin(String pin) {
		this.rejectPin = pin;
		return this;
	}

	/** Requires the given TAN; any other value is rejected with a 9xxx code. */
	public MockFinTsBank expectingTan(String tan) {
		this.expectedTan = tan;
		return this;
	}

	/** Turns the TAN challenge for turnover retrieval off. */
	public MockFinTsBank withoutTan() {
		this.requireTanForTurnovers = false;
		return this;
	}

	/** Every TAN the client submitted, in order. */
	public List<String> getTansSeen() {
		synchronized (this.tansSeen) {
			return new ArrayList<>(this.tansSeen);
		}
	}

	@Override
	public String apply(String rawRequest) {
		FinTsRequest request = FinTsRequest.parse(rawRequest);
		String dialogId = request.dialogId();
		if ("0".equals(dialogId)) {
			dialogId = "MOCKDIALOG" + this.dialogCounter.incrementAndGet();
		}
		int msgNum = request.msgNum();

		if (this.rejectPin != null && rawRequest.contains(this.rejectPin)) {
			return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
					.retGlob("9050", "Teilweise fehlerhaft")
					.retSeg(request.segmentNumber("HKIDN"), "9340", "PIN falsch. Zugang gesperrt nach 3 Versuchen.")
					.render();
		}

		if (request.has("HKSYN")) {
			return synchronisation(request, dialogId, msgNum);
		}
		if (request.has("HKKAZ")) {
			return turnovers(request, dialogId, msgNum);
		}
		if (request.has("HKTAN")) {
			return tanAnswer(request, dialogId, msgNum);
		}
		if (request.has("HKEND")) {
			return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
					.retGlob("0100", "Dialog beendet").render();
		}
		if (request.has("HKIDN") || request.has("HKVVB")) {
			return dialogInit(request, dialogId, msgNum);
		}
		return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
				.retGlob("0010", "Nachricht entgegengenommen").render();
	}

	/**
	 * Synchronisation dialog: hands out the customer system id and, like a real
	 * bank, already delivers BPD and UPD so the client learns the accounts and the
	 * supported TAN methods.
	 */
	private String synchronisation(FinTsRequest request, String dialogId, int msgNum) {
		FinTsMessage msg = FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
				.retGlob("0010", "Nachricht entgegengenommen")
				.retSeg(request.segmentNumber("HKIDN"), "0020", "Auftrag ausgefuehrt");
		appendTanMethods(msg, request);
		appendBpd(msg);
		appendUpd(msg);
		msg.segment("HISYN:{n}:4+" + CUSTOMER_SYSTEM_ID);
		return msg.render();
	}

	/** Dialog initialisation: BPD, UPD and the supported TAN methods. */
	private String dialogInit(FinTsRequest request, String dialogId, int msgNum) {
		FinTsMessage msg = FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
				.retGlob("0010", "Nachricht entgegengenommen")
				.retSeg(request.segmentNumber("HKIDN"), "0020", "Auftrag ausgefuehrt");
		appendTanMethods(msg, request);
		appendBpd(msg);
		appendUpd(msg);
		return msg.render();
	}

	/**
	 * Announces the usable TAN methods via return code 3920, which is how a bank
	 * tells the client which security mechanisms it may pick from.
	 */
	private void appendTanMethods(FinTsMessage msg, FinTsRequest request) {
		msg.retSeg(request.segmentNumber("HKVVB"), "3920", "Zugelassene Zwei-Schritt-Verfahren", "910", "920");
	}

	/**
	 * Bank parameter data: institute data, the two-step TAN parameters and the
	 * parameters for the turnover job.
	 */
	private void appendBpd(FinTsMessage msg) {
		msg.segment("HIBPA:{n}:3+1+280:12345678+Mock Testbank+3+1+300+0");
		msg.segment("HITANS:{n}:6:4+1+1+1+1:N:N:0:910:2:chipTAN manuell:6:1:TAN:4:3:2:1:N:0:2:N:N:N:N:1:N"
				+ ":N:1:1:N:0:920:2:pushTAN:6:1:TAN:4:3:2:1:N:0:2:N:N:N:N:1:N:N:1:1:N:0");
		// HIKAZS v6 = GVP2 (maxnum, minsigs, secclass) + ParKUmsZeit2
		// (timerange, canmaxentries, canallaccounts). Without it hbci4java reports
		// "Geschaeftsvorfall KUmsZeit wird nicht unterstuetzt".
		msg.segment("HIKAZS:{n}:6+1+1+0+90:J:N");
	}

	/** User parameter data: the accounts the credentials may access. */
	private void appendUpd(FinTsMessage msg) {
		msg.segment("HIUPA:{n}:4+" + MockBank.USER_ID + "+1+0");
		msg.segment("HIUPD:{n}:6+" + ACCOUNT_NUMBER + "::280:12345678+" + ACCOUNT_IBAN + "+" + MockBank.CUSTOMER_ID
				+ "+10+EUR+" + FinTsMessage.escape(ACCOUNT_NAME) + "++Girokonto+HKKAZ:1");
	}

	/**
	 * Turnover retrieval. Unless the TAN requirement is switched off, the first
	 * request is answered with a TAN challenge (HITAN) that the client surfaces
	 * through easyfin's TAN callback.
	 */
	private String turnovers(FinTsRequest request, String dialogId, int msgNum) {
		boolean tanSubmitted = request.has("HKTAN") && hasSubmittedTan(request);
		if (this.requireTanForTurnovers && !tanSubmitted) {
			return challenge(request, dialogId, msgNum);
		}
		if (this.expectedTan != null && !this.expectedTan.equals(submittedTan(request))) {
			return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum).retGlob("9050", "Teilweise fehlerhaft")
					.retSeg(request.segmentNumber("HKKAZ"), "9942", "TAN falsch").render();
		}
		return statementResponse(request, dialogId, msgNum);
	}

	private String tanAnswer(FinTsRequest request, String dialogId, int msgNum) {
		recordTan(request);
		if (this.expectedTan != null && !this.expectedTan.equals(submittedTan(request))) {
			return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum).retGlob("9050", "Teilweise fehlerhaft")
					.retSeg(request.segmentNumber("HKTAN"), "9942", "TAN falsch").render();
		}
		return statementResponse(request, dialogId, msgNum);
	}

	private String challenge(FinTsRequest request, String dialogId, int msgNum) {
		return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
				.retGlob("0010", "Nachricht entgegengenommen")
				.retSeg(request.segmentNumber("HKKAZ"), "0030", "Auftrag empfangen - Sicherheitsfreigabe erforderlich")
				.segment("HITAN:{n}:6:" + request.segmentNumber("HKTAN") + "+4++MOCKREF01+"
						+ FinTsMessage.escape(TAN_CHALLENGE))
				.render();
	}

	private String statementResponse(FinTsRequest request, String dialogId, int msgNum) {
		return FinTsMessage.response(dialogId, msgNum, dialogId, msgNum)
				.retGlob("0010", "Nachricht entgegengenommen")
				.retSeg(request.segmentNumber("HKKAZ"), "0020", "Auftrag ausgefuehrt")
				.segment("HIKAZ:{n}:7:" + request.segmentNumber("HKKAZ") + "+" + FinTsMessage.binary(mt940()))
				.render();
	}

	private boolean hasSubmittedTan(FinTsRequest request) {
		return submittedTan(request) != null;
	}

	private String submittedTan(FinTsRequest request) {
		// The TAN travels in the signature tail (HNSHA) as the user's authorisation.
		return request.segment("HNSHA").map(s -> {
			String value = FinTsRequest.field(s, 3);
			return value.isEmpty() ? null : value;
		}).orElse(null);
	}

	private void recordTan(FinTsRequest request) {
		String tan = submittedTan(request);
		if (tan != null) {
			synchronized (this.tansSeen) {
				this.tansSeen.add(tan);
			}
		}
	}

	/**
	 * A minimal but well-formed MT940 statement with two bookings: one credit and
	 * one debit.
	 */
	public static String mt940() {
		return ":20:MOCKSTMT\r\n" //
				+ ":25:12345678/" + ACCOUNT_NUMBER + "\r\n" //
				+ ":28C:1/1\r\n" //
				+ ":60F:C260701EUR1000,00\r\n" //
				+ ":61:2607020702CR150,25NTRFNONREF//MOCK1\r\n" //
				+ ":86:166?00GUTSCHRIFT?20Gehalt Juli?30MOCKDEFFXXX?31DE02120300000000202051?32Arbeitgeber GmbH\r\n" //
				+ ":61:2607030703DR42,50NTRFNONREF//MOCK2\r\n" //
				+ ":86:005?00LASTSCHRIFT?20Stromrechnung?30MOCKDEFFXXX?31DE02120300000000202051?32Stadtwerke AG\r\n" //
				+ ":62F:C260703EUR1107,75\r\n" //
				+ "-";
	}
}
