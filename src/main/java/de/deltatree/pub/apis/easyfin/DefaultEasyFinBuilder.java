package de.deltatree.pub.apis.easyfin;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultEasyFinBuilder implements EasyFinBuilder {

	private String loginName;
	private String password;
	private BankData bankData;

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
		return new DefaultEasyFin(this.loginName, this.password, this.bankData);
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

}
