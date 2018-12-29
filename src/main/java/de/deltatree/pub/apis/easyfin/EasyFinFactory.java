package de.deltatree.pub.apis.easyfin;


public class EasyFinFactory {

	public static EasyFinBuilder builder() {
		return new DefaultEasyFinBuilder();
	}

	public static void destroyAll() {
		DefaultEasyFin.cleanShutdown();
	}

}
