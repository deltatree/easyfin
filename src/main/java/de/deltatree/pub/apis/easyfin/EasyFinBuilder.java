package de.deltatree.pub.apis.easyfin;

public interface EasyFinBuilder {

	EasyFinBuilder pin(String loginPassword);

	EasyFinBuilder bankData(BankData bankData);

	EasyFinBuilder bankData(String bankDataSearch);

	EasyFin build();

	EasyFinBuilder proxy(String proxy);

	EasyFinBuilder additionalHBCIConfiguration(String key, String value);

	EasyFinBuilder customerId(String customerId);

	EasyFinBuilder userId(String userId);

}
