package de.deltatree.pub.apis.easyfin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EasyFinHelper {

	public static Stream<BankData> bankDataLookup(String searchString) {
		return StaticBankDataHelper.lookup(searchString);
	}

}

class StaticBankDataHelper {
	private final static List<BankData> bankInfos = initBankInfos();

	public static Stream<BankData> lookup(String bankname) {
		Pattern pattern = Pattern.compile(bankname, Pattern.CASE_INSENSITIVE);
		return getBankInfos(pattern);
	}

	private static Stream<BankData> getBankInfos(Pattern... pattern) {
		return bankInfos.stream().filter(new Predicate<BankData>() {
			@Override
			public boolean test(BankData info) {
				if (pattern.length <= 0) {
					return true;
				} else {
					for (Pattern p : pattern) {
						if (p.matcher(info.getName()).find()
								|| (info.getBic() != null && p.matcher(info.getBic()).find())
								|| (info.getBlz() != null && p.matcher(info.getBlz()).find())) {
							return true;
						}
					}
				}
				return false;
			}
		});
	}

	private static List<BankData> initBankInfos() {
		URL url = EasyFinHelper.class.getResource("/blz.properties");
		try {
			return getResourceFileAsStringStream(url).map(line -> parse(line)).collect(Collectors.toList());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static BankData parse(String line) {
		String[] payload = line.split("=", 2);
		if (payload.length == 2) {
			String blz = payload[0];
			String data = payload[1];

			DefaultBankData bankData = new DefaultBankData();
			bankData.setBlz(blz);

			// XXX - copied from private innerclass of hbci4java ...
			// Keep trailing empty fields (limit -1): entries such as
			// "Name|Ort|BIC|09|||||" would otherwise collapse below the 8-column check
			// and silently lose the columns that *are* populated.
			String[] cols = data.split("\\|", -1);
			bankData.setName(cols[0]);
			if (cols.length >= 8) {
				bankData.setLocation(emptyToNull(cols[1]));
				bankData.setBic(emptyToNull(cols[2]));
				bankData.setChecksumMethod(emptyToNull(cols[3]));
				bankData.setRdhAddress(emptyToNull(cols[4]));
				bankData.setPinTanAddress(emptyToNull(cols[5]));
				bankData.setRdhVersion(emptyToNull(cols[6]));
				bankData.setPinTanVersion(emptyToNull(cols[7]));
			}
			return bankData;
		}

		throw new IllegalStateException("line from blz.properties not readable: " + line);
	}

	/** Absent columns stay {@code null} rather than becoming empty strings. */
	private static String emptyToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	public static Stream<String> getResourceFileAsStringStream(URL url) throws IOException {
		// blz.properties ships in ISO-8859-1 (German bank names carry umlauts); read it explicitly
		// so the lookup is deterministic regardless of the platform default charset.
		BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.ISO_8859_1));
		return reader.lines();
	}
}
