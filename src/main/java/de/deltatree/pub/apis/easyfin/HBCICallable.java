package de.deltatree.pub.apis.easyfin;

import java.util.Properties;
import java.util.concurrent.Callable;

import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.concurrent.HBCIPassportFactory;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;

public abstract class HBCICallable<A extends HBCICommandResult> implements Callable<A> {

	private final Properties properties;
	private final HBCICallback callback;
	private HBCIPassportFactory passportFactory;

	protected HBCIPassport passport = null;
	protected HBCIHandler handler = null;

	public HBCICallable(Properties properties, HBCICallback callback, HBCIPassportFactory passportFactory) {
		this.properties = properties;
		this.callback = callback;
		this.passportFactory = passportFactory;
	}

	@Override
	public A call() throws Exception {
		init();
		try {
			prepare();
			return execute(this.passport, this.handler);
		} catch (Exception e) {
			throw e;
		} finally {
			done();
		}
	}

	private void init() {
		HBCIUtils.initThread(properties, callback);
	}

	private void prepare() throws Exception {
		passport = passportFactory.createPassport();
		if (passport != null) {
			handler = new HBCIHandler(this.properties.getProperty("default.hbciversion"), passport);
		}
	}

	protected abstract A execute(HBCIPassport passport, HBCIHandler handler) throws Exception;

	private void done() {
		if (handler != null) {
			handler.close();
		}
		if (passport != null) {
			passport.close();
		}
		HBCIUtils.doneThread();
	}

}
