package de.deltatree.pub.apis.easyfin;

import org.kapott.hbci.manager.HBCIUtils;

public class EasyFinFactory {

	public static EasyFinBuilder builder() {
		return new DefaultEasyFinBuilder();
	}

	public static void clean() {
		HBCIUtils.done();
	}

}
