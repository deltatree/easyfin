package de.deltatree.pub.apis.easyfin;

public interface EasyFinBuilder {

	EasyFinBuilder loginName(String string);

	EasyFinBuilder loginPassword(String string);

	EasyFinBuilder bankData(BankData bankData);

	EasyFinBuilder bankData(String bankDataSearch);

	EasyFin build();

}
