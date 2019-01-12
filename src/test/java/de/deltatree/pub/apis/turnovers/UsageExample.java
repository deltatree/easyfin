package de.deltatree.pub.apis.turnovers;

import java.util.concurrent.atomic.AtomicInteger;

import org.kapott.hbci.structures.Konto;

import de.deltatree.pub.apis.easyfin.EasyFin;
import de.deltatree.pub.apis.easyfin.EasyFinFactory;

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
				.userId("VRNetKey Alias/ID") // haben CustomerId und UserId gleicher Wert
				.pin("VRNetKey Pin") //
				.bankData("Name / BIC / BLZ der Zielbank") //
				.proxy("proxy.intern.domain.com:3128") // optional
				.additionalHBCIConfiguration("key1", "value1") // optional
				.additionalHBCIConfiguration("keyN", "valueN") // optional
				.build();
	}

}
