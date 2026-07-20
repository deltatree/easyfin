package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TanMethodSelectionTest {

	private static final String OFFERED = "910:chipTAN manuell|911:chipTAN optisch|920:pushTAN";

	private MyHBCICallback callbackWithSelector(Function<Map<String, String>, String> selector) {
		return new MyHBCICallback(new MyHBCICallbackAnswers() {
			public BankData getBankData() {
				return null;
			}

			public String getUserId() {
				return null;
			}

			public String getCustomerId() {
				return null;
			}

			public String getPin() {
				return null;
			}

			public Function<Map<String, String>, String> getTanCallback() {
				return null;
			}

			@Override
			public Function<Map<String, String>, String> getTanMethodSelector() {
				return selector;
			}
		});
	}

	@Test
	void parsesOfferedMethodsPreservingOrder() {
		Map<String, String> parsed = MyHBCICallback.parseSecMechs(OFFERED);
		assertIterableEquals(List.of("910", "911", "920"), new ArrayList<>(parsed.keySet()));
		assertEquals("pushTAN", parsed.get("920"));
	}

	@Test
	void selectorReceivesOrderedMapAndPicksMethod() {
		List<String> keysSeen = new ArrayList<>();
		MyHBCICallback cb = callbackWithSelector(offered -> {
			keysSeen.addAll(offered.keySet());
			return "920";
		});
		assertEquals("920", cb.selectSecMech(OFFERED));
		assertIterableEquals(List.of("910", "911", "920"), keysSeen);
	}

	@Test
	void invalidSelectorReturnFailsFast() {
		MyHBCICallback cb = callbackWithSelector(offered -> "999");
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> cb.selectSecMech(OFFERED));
		assertTrue(ex.getMessage().contains("999"));
	}

	@Test
	void nullSelectorReturnFailsFast() {
		MyHBCICallback cb = callbackWithSelector(offered -> null);
		assertThrows(IllegalStateException.class, () -> cb.selectSecMech(OFFERED));
	}

	@Test
	void withoutSelectorPicksFirstNumeric() {
		MyHBCICallback cb = callbackWithSelector(null);
		assertEquals("910", cb.selectSecMech(OFFERED));
	}
}
