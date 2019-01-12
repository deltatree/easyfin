package de.deltatree.pub.apis.easyfin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultEasyFinBuilder implements EasyFinBuilder {

	private String pin;
	private String customerId;
	private String userId;
	private BankData bankData;
	private Map<String, String> additionalHBCIConfiguration = new HashMap<String, String>();

	@Override
	public EasyFinBuilder pin(String pin) {
		this.pin = pin;
		return this;
	}

	@Override
	public EasyFinBuilder bankData(BankData bankData) {
		this.bankData = bankData;
		return this;
	}

	@Override
	public EasyFin build() {
		DefaultEasyFin ef = new DefaultEasyFin(this.bankData,this.additionalHBCIConfiguration);
		ef.setPin(this.pin);
		ef.setUserId(this.userId);
		ef.setCustomerId(this.customerId);
		return ef;
	}

	@Override
	public EasyFinBuilder bankData(String bankDataSearchString) {
		Stream<BankData> bankDataLookup = EasyFinHelper.bankDataLookup(bankDataSearchString);
		List<BankData> collect = bankDataLookup.collect(Collectors.toList());
		if (collect.size() == 1) {
			this.bankData = collect.get(0);
			return this;
		}
		throw new IllegalStateException("lookup result is odd: [" + collect + "]");
	}

	@Override
	public EasyFinBuilder proxy(String proxyString) {
		this.additionalHBCIConfiguration.put("client.passport.PinTan.proxy", proxyString);
		return this;
	}

	@Override
	public EasyFinBuilder additionalHBCIConfiguration(String key, String value) {
		this.additionalHBCIConfiguration.put(key, value);
		return this;
	}

	@Override
	public EasyFinBuilder customerId(String customerId) {
		this.customerId = customerId;
		return this;
	}

	@Override
	public EasyFinBuilder userId(String userId) {
		this.userId = userId;
		return this;
	}

}
