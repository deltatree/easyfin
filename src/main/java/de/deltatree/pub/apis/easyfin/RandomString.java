package de.deltatree.pub.apis.easyfin;


import java.security.SecureRandom;

/**
 * Generates random alphanumeric strings using a cryptographically strong
 * {@link SecureRandom}. Used to protect the transient HBCI passport file with a
 * per-instance passphrase that never leaves the process.
 */
public class RandomString {
	private final SecureRandom random = new SecureRandom();

	public String generateRandomString(int length) {
		return this.random.ints(48, 123).filter(i -> (i < 58) || (i > 64 && i < 91) || (i > 96)).limit(length)
				.collect(StringBuilder::new, (sb, i) -> sb.append((char) i), StringBuilder::append).toString();
	}
}
