package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The PIN/TAN address a caller configures decides which endpoint is actually
 * contacted, so its parsing is covered explicitly — including the shapes the
 * usage guide invites (custom hosts, ports and paths).
 */
class EndpointParsingTest {

	@Test
	void parsesFullHttpsUrl() {
		assertEquals("hbci.example.de/bank/hbci",
				MyHBCICallback.hostAndPathOf("https://hbci.example.de/bank/hbci"));
		assertNull(MyHBCICallback.portOf("https://hbci.example.de/bank/hbci"));
	}

	@Test
	void parsesExplicitPort() {
		assertEquals("localhost/fints", MyHBCICallback.hostAndPathOf("https://localhost:8443/fints"));
		assertEquals(Integer.valueOf(8443), MyHBCICallback.portOf("https://localhost:8443/fints"));
	}

	@Test
	void parsesAddressWithoutScheme() {
		assertEquals("hbci.example.de/bank", MyHBCICallback.hostAndPathOf("hbci.example.de/bank"));
		assertEquals(Integer.valueOf(9000), MyHBCICallback.portOf("hbci.example.de:9000/bank"));
	}

	@Test
	void parsesHostWithoutPath() {
		assertEquals("hbci.example.de", MyHBCICallback.hostAndPathOf("https://hbci.example.de"));
	}

	@Test
	void keepsTrailingSlash() {
		assertEquals("hbci.example.de/", MyHBCICallback.hostAndPathOf("https://hbci.example.de/"));
	}

	@Test
	void handlesIpv6Literals() {
		// The host must not be truncated at the first colon.
		assertEquals("[::1]/fints", MyHBCICallback.hostAndPathOf("https://[::1]:8443/fints"));
		assertEquals(Integer.valueOf(8443), MyHBCICallback.portOf("https://[::1]:8443/fints"));
	}

	@Test
	void handlesUserinfo() {
		assertEquals("hbci.example.de/bank", MyHBCICallback.hostAndPathOf("https://user:secret@hbci.example.de/bank"));
	}

	@Test
	void stripsWhitespace() {
		assertEquals("hbci.example.de/bank", MyHBCICallback.hostAndPathOf("  https://hbci.example.de/bank  "));
	}

	@Test
	void blankOrUnparseableAddressKeepsTheDefault() {
		assertNull(MyHBCICallback.hostAndPathOf(null));
		assertNull(MyHBCICallback.hostAndPathOf(""));
		assertNull(MyHBCICallback.hostAndPathOf("   "));
		assertNull(MyHBCICallback.hostAndPathOf("https://"));
		assertNull(MyHBCICallback.portOf(null));
		assertNull(MyHBCICallback.portOf(""));
	}

	@Test
	void portIsOnlyReportedWhenExplicit() {
		assertNull(MyHBCICallback.portOf("https://hbci.example.de/bank"));
		assertNull(MyHBCICallback.portOf("hbci.example.de"));
	}
}
