package de.deltatree.pub.apis.easyfin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultEasyFinBuilder implements EasyFinBuilder {

	private String pin;
	private String customerId;
	private String userId;
	private BankData bankData;
	private Map<String, String> additionalHBCIConfiguration = new HashMap<String, String>();
	private Function<Map<String, String>, String> tanCallback;
	private Function<Map<String, String>, String> tanMethodSelector;
	private Path passportDirectory;

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
	public EasyFinBuilder tanCallback(Function<Map<String, String>, String> tanCallback) {
		this.tanCallback = tanCallback;
		return this;
	}

	@Override
	public EasyFinBuilder tanMethodSelector(Function<Map<String, String>, String> tanMethodSelector) {
		this.tanMethodSelector = tanMethodSelector;
		return this;
	}

	@Override
	public EasyFinBuilder passportDirectory(Path passportDirectory) {
		this.passportDirectory = passportDirectory;
		return this;
	}

	@Override
	public EasyFin build() {
		if (this.pin == null || this.pin.isEmpty()) {
			throw new IllegalStateException("pin is required: call pin(...) before build()");
		}
		if (this.bankData == null) {
			throw new IllegalStateException("bankData is required: call bankData(...) before build()");
		}
		DefaultEasyFin ef = new DefaultEasyFin(this.bankData, this.additionalHBCIConfiguration, this.passportDirectory);
		ef.setPin(this.pin);
		ef.setUserId(this.userId);
		ef.setCustomerId(this.customerId);
		ef.setTanCallback(this.tanCallback);
		ef.setTanMethodSelector(this.tanMethodSelector);
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
		if (collect.isEmpty()) {
			throw new IllegalStateException("bank lookup for [" + bankDataSearchString + "] found no matching bank");
		}
		throw new IllegalStateException("bank lookup for [" + bankDataSearchString + "] is ambiguous (" + collect.size()
				+ " matches); refine the search (e.g. by BLZ or BIC)");
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
