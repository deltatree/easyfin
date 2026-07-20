package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BankDataHelperTest {

	@Test
	void parsesFullyPopulatedLine() {
		BankData bd = StaticBankDataHelper.parse(
				"72120207=UniCredit Bank Ndl 648|Aschheim|HYVEDEM1093|99|hbci.example.de|https://pintan.example.de/bank|300|300|");
		assertEquals("72120207", bd.getBlz());
		assertEquals("UniCredit Bank Ndl 648", bd.getName());
		assertEquals("Aschheim", bd.getLocation());
		assertEquals("HYVEDEM1093", bd.getBic());
		assertEquals("https://pintan.example.de/bank", bd.getPinTanAddress());
		assertEquals("300", bd.getPinTanVersion());
	}

	@Test
	void parsesShortLineWithNameOnly() {
		BankData bd = StaticBankDataHelper.parse("10000000=Bundesbank");
		assertEquals("10000000", bd.getBlz());
		assertEquals("Bundesbank", bd.getName());
		// Extended fields are not present on short lines.
		assertEquals(null, bd.getBic());
	}

	@Test
	void rejectsLineWithoutSeparator() {
		assertThrows(IllegalStateException.class, () -> StaticBankDataHelper.parse("not-a-valid-line"));
	}

	@Test
	void lookupByBlzFindsThatBank() {
		// Discover a real BLZ from the bundled directory, then confirm lookup finds it.
		List<BankData> all = EasyFinHelper.bankDataLookup(".").collect(Collectors.toList());
		assertFalse(all.isEmpty(), "bundled blz.properties should not be empty");
		String blz = all.get(0).getBlz();

		List<BankData> byBlz = EasyFinHelper.bankDataLookup(blz).collect(Collectors.toList());
		assertTrue(byBlz.stream().anyMatch(b -> blz.equals(b.getBlz())));
	}

	@Test
	void lookupForUnknownPatternIsEmpty() {
		List<BankData> result = EasyFinHelper.bankDataLookup("ZZZ_NO_SUCH_BANK_QQQ_123").collect(Collectors.toList());
		assertTrue(result.isEmpty());
	}
}
