package de.deltatree.pub.apis.easyfin;

import java.util.Map;
import java.util.function.Function;

public interface EasyFinBuilder {

	EasyFinBuilder pin(String loginPassword);

	EasyFinBuilder bankData(BankData bankData);

	EasyFinBuilder bankData(String bankDataSearch);

	EasyFin build();

	EasyFinBuilder proxy(String proxy);

	EasyFinBuilder additionalHBCIConfiguration(String key, String value);

	EasyFinBuilder customerId(String customerId);

	EasyFinBuilder userId(String userId);

	EasyFinBuilder tanCallback(Function<Map<String, String>, String> tanCallback);

}
