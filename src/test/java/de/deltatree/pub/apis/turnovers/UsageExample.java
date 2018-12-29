package de.deltatree.pub.apis.turnovers;

import java.util.concurrent.atomic.AtomicInteger;

import org.kapott.hbci.structures.Konto;

import de.deltatree.pub.apis.easyfin.EasyFin;
import de.deltatree.pub.apis.easyfin.EasyFinFactory;

public class UsageExample {

	public static void main(String[] args) {
		EasyFin to = EasyFinFactory.builder() //
				.loginName("VRNetKey Alias/ID") //
				.loginPassword("VRNetKey Password") //
				.bankData("Name / BIC / BLZ der Zielbank") //
				.proxy("proxy.intern.domain.com:3128") // optional
				.additionalHBCIConfiguration("key1", "value1") // optional
				.additionalHBCIConfiguration("keyN", "valueN") // optional
				.build();

		try {
			final AtomicInteger i = new AtomicInteger(0);

			for (Konto k : to.getAccounts()) {
				to.getTurnoversAsStream(k).forEach(t -> System.out.println(i.incrementAndGet() + " " + t.bdate));
			}
		} finally {
			EasyFinFactory.destroyAll();
		}
	}

}
