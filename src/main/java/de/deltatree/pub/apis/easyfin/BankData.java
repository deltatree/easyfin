package de.deltatree.pub.apis.easyfin;


public interface BankData {
	String getBlz();

	String getName();

	String getLocation();

	String getBic();

	String getChecksumMethod();

	String getRdhAddress();

	String getPinTanAddress();

	String getRdhVersion();

	String getPinTanVersion();
}
