package de.deltatree.pub.apis.easyfin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Maps;

public class DefaultEasyFinBuilder implements EasyFinBuilder {

	private String loginName;
	private String password;
	private BankData bankData;
	private Map<String, String> additionalHBCIConfiguration = Maps.newHashMap();

	@Override
	public EasyFinBuilder loginName(String loginName) {
		this.loginName = loginName;
		return this;
	}

	@Override
	public EasyFinBuilder loginPassword(String password) {
		this.password = password;
		return this;
	}

	@Override
	public EasyFinBuilder bankData(BankData bankData) {
		this.bankData = bankData;
		return this;
	}

	@Override
	public EasyFin build() {
		return new DefaultEasyFin(this.loginName, this.password, this.bankData, additionalHBCIConfiguration);
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

}
