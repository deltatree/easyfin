package de.deltatree.pub.apis.easyfin;

import java.util.Map;
import java.util.function.Function;

public interface MyHBCICallbackAnswers {

	BankData getBankData();

	String getUserId();

	String getCustomerId();

	String getPin();

	Function<Map<String, String>, String> getTanCallback();

	/**
	 * Optional selector for the PIN/TAN security mechanism. May be {@code null},
	 * in which case the first offered method is chosen automatically.
	 *
	 * @return the configured TAN-method selector, or {@code null}
	 */
	default Function<Map<String, String>, String> getTanMethodSelector() {
		return null;
	}
}
