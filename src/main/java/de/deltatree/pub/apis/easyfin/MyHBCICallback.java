package de.deltatree.pub.apis.easyfin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;

public class MyHBCICallback extends HBCICallbackConsole implements HBCICallback {
	/** Map key under which the bank's TAN challenge text is passed to the TAN callback. */
	public static final String CHALLENGE_KEY = "challenge";

	private final RandomString randomString = new RandomString();
	private final String password;
	private MyHBCICallbackAnswers answers;

	private Pattern P = Pattern.compile("(\\d{1,})");

	public MyHBCICallback(MyHBCICallbackAnswers answers) {
		this.answers = answers;
		this.password = this.randomString.generateRandomString(34);
	}

	@Override
	public synchronized void status(HBCIPassport passport, int statusTag, Object[] o) {
		// Intentionally empty
	}

	@Override
	public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
		// Never log PIN/TAN/passphrase content; only the reason/datatype are traced.
		HBCIUtils.log("[LOG] callback reason: " + reason + " / datatype: " + datatype, HBCIUtils.LOG_DEBUG);

		switch (reason) {
		case NEED_BLZ:
			retData.append(this.answers.getBankData().getBlz());
			break;

		case NEED_CUSTOMERID:
			retData.append(this.answers.getCustomerId());
			break;

		case NEED_USERID:
			retData.append(this.answers.getUserId());
			break;

		case NEED_PT_TAN:
			Map<String, String> tanContext = new LinkedHashMap<>();
			if (msg != null) {
				tanContext.put(CHALLENGE_KEY, msg);
			}
			retData.append(this.answers.getTanCallback().apply(tanContext));
			break;

		case NEED_PT_PIN:
			retData.append(this.answers.getPin());
			break;

		case NEED_PASSPHRASE_SAVE:
		case NEED_PASSPHRASE_LOAD:
			retData.append(this.password);
			break;

		case NEED_PT_SECMECH:
			String chosen = selectSecMech(retData.toString());
			retData.delete(0, retData.length());
			retData.append(chosen);
			break;

		case NEED_HOST:
			// Honor the endpoint of the BankData the caller configured. hbci4java pre-fills
			// this from its own bundled bank directory; only override when we actually have
			// an address, so lookups without a PIN/TAN URL keep the built-in default.
			String hostAndPath = hostAndPathOf(pinTanAddress());
			if (hostAndPath != null) {
				retData.delete(0, retData.length());
				retData.append(hostAndPath);
			}
			break;

		case NEED_PORT:
			Integer port = portOf(pinTanAddress());
			if (port != null) {
				retData.delete(0, retData.length());
				retData.append(port.intValue());
			}
			break;

		case NEED_COUNTRY:
		case NEED_CONNECTION:
		case CLOSE_CONNECTION:
		default:
			// Intentionally empty!
		}
	}

	private String pinTanAddress() {
		BankData bankData = this.answers.getBankData();
		return bankData != null ? bankData.getPinTanAddress() : null;
	}

	/**
	 * Extracts {@code host[/path]} from a configured PIN/TAN address, dropping the
	 * scheme and any explicit port. Returns {@code null} when no address is
	 * configured, so hbci4java's own default is kept.
	 */
	static String hostAndPathOf(String address) {
		URI uri = parse(address);
		if (uri == null) {
			return null;
		}
		String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			return null;
		}
		String path = uri.getRawPath() != null ? uri.getRawPath() : "";
		return host + path;
	}

	/**
	 * Extracts the explicit port from a configured PIN/TAN address, or {@code null}
	 * when the address carries none (hbci4java then keeps its default of 443).
	 */
	static Integer portOf(String address) {
		URI uri = parse(address);
		if (uri == null || uri.getHost() == null) {
			return null;
		}
		int port = uri.getPort();
		return port > 0 ? Integer.valueOf(port) : null;
	}

	/**
	 * Parses a configured address into a URI. A scheme is added when missing so
	 * bare {@code host[:port][/path]} values parse too. Returns {@code null} for
	 * blank or unparseable input, which makes the caller keep hbci4java's default.
	 */
	private static URI parse(String address) {
		if (address == null) {
			return null;
		}
		String trimmed = address.replaceAll("\\s", "");
		if (trimmed.isEmpty()) {
			return null;
		}
		if (!trimmed.contains("://")) {
			trimmed = "https://" + trimmed;
		}
		try {
			return new URI(trimmed);
		} catch (URISyntaxException e) {
			return null;
		}
	}

	/**
	 * Chooses the PIN/TAN security mechanism. When a selector is configured, it is
	 * offered an insertion-ordered {@code code -> label} map (bank announcement
	 * order) and must return one of the offered codes. Otherwise the first offered
	 * numeric code is chosen (historical behavior).
	 */
	String selectSecMech(String offeredRaw) {
		Function<Map<String, String>, String> selector = this.answers.getTanMethodSelector();
		if (selector != null) {
			Map<String, String> offered = parseSecMechs(offeredRaw);
			if (offered.isEmpty()) {
				throw new IllegalStateException("No TAN methods offered by the bank: [" + offeredRaw + "]");
			}
			String chosen = selector.apply(offered);
			if (chosen == null || !offered.containsKey(chosen)) {
				throw new IllegalStateException("TAN method selector returned [" + chosen
						+ "] which is not one of the offered methods " + offered.keySet());
			}
			return chosen;
		}
		// Historical fallback: first numeric code found.
		Matcher matcher = P.matcher(offeredRaw);
		if (matcher.find()) {
			return matcher.group(1);
		}
		throw new IllegalStateException("No TAN method could be determined from [" + offeredRaw + "]");
	}

	/**
	 * Parses the raw {@code NEED_PT_SECMECH} payload ({@code code:label|code:label|...})
	 * into an insertion-ordered map preserving the bank's announcement order.
	 */
	static Map<String, String> parseSecMechs(String offeredRaw) {
		Map<String, String> result = new LinkedHashMap<>();
		if (offeredRaw == null || offeredRaw.isEmpty()) {
			return result;
		}
		for (String entry : offeredRaw.split("\\|")) {
			String[] kv = entry.split(":", 2);
			if (kv.length == 2 && !kv[0].isEmpty()) {
				result.put(kv[0], kv[1]);
			}
		}
		return result;
	}
}
