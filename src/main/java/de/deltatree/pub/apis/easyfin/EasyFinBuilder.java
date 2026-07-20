package de.deltatree.pub.apis.easyfin;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * Fluent builder for an {@link EasyFin} client. Obtain one via
 * {@link EasyFinFactory#builder()}.
 *
 * <p>
 * Required before {@link #build()}: {@link #pin(String)} and bank data (either
 * {@link #bankData(BankData)} or {@link #bankData(String)}). Missing required
 * values fail fast with an {@link IllegalStateException}.
 * </p>
 */
public interface EasyFinBuilder {

	EasyFinBuilder pin(String loginPassword);

	EasyFinBuilder bankData(BankData bankData);

	EasyFinBuilder bankData(String bankDataSearch);

	EasyFin build();

	EasyFinBuilder proxy(String proxy);

	EasyFinBuilder additionalHBCIConfiguration(String key, String value);

	EasyFinBuilder customerId(String customerId);

	EasyFinBuilder userId(String userId);

	EasyFinBuilder tanCallback(Function<Map<String, String>, String> tanCallback);

	/**
	 * Directory into which the transient HBCI passport file is written. When not
	 * set, the JVM temp directory ({@code java.io.tmpdir}) is used. The file is
	 * removed on {@link EasyFin#clean()} and on JVM exit.
	 *
	 * @param passportDirectory target directory (must exist / be writable)
	 * @return this builder
	 * @since 1.1.0
	 */
	default EasyFinBuilder passportDirectory(Path passportDirectory) {
		throw new UnsupportedOperationException("passportDirectory(Path) is not implemented by this builder");
	}

	/**
	 * Optional hook to choose the PIN/TAN security mechanism (TAN method) when the
	 * bank offers several. The selector receives an insertion-ordered map in the
	 * bank's announced order, mapping the TAN method code to its human-readable
	 * label, and must return one of the offered codes. Returning {@code null} or a
	 * code that was not offered fails fast with an {@link IllegalStateException}.
	 *
	 * <p>
	 * When not set, easyfin keeps its historical behavior of selecting the first
	 * offered method automatically.
	 * </p>
	 *
	 * @param tanMethodSelector maps offered {@code code -> label} to the chosen code
	 * @return this builder
	 * @since 1.1.0
	 */
	default EasyFinBuilder tanMethodSelector(Function<Map<String, String>, String> tanMethodSelector) {
		throw new UnsupportedOperationException("tanMethodSelector(Function) is not implemented by this builder");
	}

}
