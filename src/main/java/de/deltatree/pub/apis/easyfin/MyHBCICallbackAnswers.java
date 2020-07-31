package de.deltatree.pub.apis.easyfin;

import java.util.Map;
import java.util.function.Function;

public interface MyHBCICallbackAnswers {

	BankData getBankData();

	String getUserId();

	String getCustomerId();

	String getPin();

	Function<Map<String, String>, String> getTanCallback();
}
