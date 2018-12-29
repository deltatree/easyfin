package de.deltatree.pub.apis.easyfin;


import lombok.Data;

@Data
public class DefaultBankData implements BankData {
	String blz;
	String name;
	String location;
	String bic;
	String checksumMethod;
	String rdhAddress;
	String pinTanAddress;
	String rdhVersion;
	String pinTanVersion;
}
