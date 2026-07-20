package de.deltatree.pub.apis.easyfin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomStringTest {

	private final RandomString randomString = new RandomString();

	@Test
	void producesRequestedLength() {
		assertEquals(34, randomString.generateRandomString(34).length());
		assertEquals(1, randomString.generateRandomString(1).length());
	}

	@Test
	void producesOnlyAlphanumericCharacters() {
		String value = randomString.generateRandomString(500);
		assertTrue(value.matches("[A-Za-z0-9]+"), "expected only alphanumerics but was: " + value);
	}

	@Test
	void producesDifferentValuesAcrossCalls() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			seen.add(randomString.generateRandomString(34));
		}
		// With a 34-char alphanumeric space, 50 draws must not collide.
		assertEquals(50, seen.size());
		assertNotEquals(randomString.generateRandomString(34), randomString.generateRandomString(34));
	}
}
