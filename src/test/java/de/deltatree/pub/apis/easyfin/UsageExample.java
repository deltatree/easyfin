package de.deltatree.pub.apis.easyfin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.kapott.hbci.structures.Konto;

public class UsageExample {

	public static void main(String[] args) {
		final AtomicInteger a = new AtomicInteger(0);
		try {
			for (int i = 1; i <= 1; i++) {
				EasyFin ef = initEasyfin();
				try {
					for (Konto k : ef.getAccounts()) {
						ef.getTurnoversAsStream(k)
								.forEach(t -> System.out.println(a.incrementAndGet() + " " + t.value));
					}
				} finally {
					ef.clean();
				}
			}
		} finally {
			EasyFinFactory.clean();
		}
	}

	private static EasyFin initEasyfin() {
		return EasyFinFactory.builder() //
				.customerId("VRNetKey Alias/ID") // Bei Volksbanken (agree21) und Sparkassen
				.userId("VRNetKey Alias/ID") // haben CustomerId und UserId den gleichen Wert
				.pin("Pin") //
				.tanCallback((i) -> exampleTanCallback(i)) // Callback bei TAN-Abfrage
				.bankData("Name / BIC / BLZ der Zielbank") //
				.proxy("proxy.intern.domain.com:3128") // optional
				.additionalHBCIConfiguration("key1", "value1") // optional
				.additionalHBCIConfiguration("keyN", "valueN") // optional
				.build();
	}

	private static String exampleTanCallback(Map<String, String> in) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		try {
			return reader.readLine();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

//	private static EasyFin initEasyfin() {
//		return EasyFinFactory.builder() //
//				.customerId("VRNetKey Alias/ID") // Bei Volksbanken (agree21) und Sparkassen
//				.userId("VRNetKey Alias/ID") // haben CustomerId und UserId den gleichen Wert
//				.pin("Pin") //
//				.bankData("Name / BIC / BLZ der Zielbank") //
//				.proxy("proxy.intern.domain.com:3128") // optional
//				.additionalHBCIConfiguration("key1", "value1") // optional
//				.additionalHBCIConfiguration("keyN", "valueN") // optional
//				.build();
//	}

}
