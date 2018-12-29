package de.deltatree.pub.apis.easyfin;


import java.util.Random;

public class RandomString {
	private final Random random = new Random();

	public String generateRandomString(int length) {
		return this.random.ints(48, 123).filter(i -> (i < 58) || (i > 64 && i < 91) || (i > 96)).limit(length)
				.collect(StringBuilder::new, (sb, i) -> sb.append((char) i), StringBuilder::append).toString();
	}
}