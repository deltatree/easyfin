package de.deltatree.pub.apis.easyfin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.callback.HBCICallbackUnsupported;
import org.kapott.hbci.concurrent.DefaultHBCIPassportFactory;
import org.kapott.hbci.concurrent.HBCIPassportFactory;
import org.kapott.hbci.concurrent.HBCIThreadFactory;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import com.google.common.collect.Lists;

public class DefaultEasyFin implements EasyFin {

	static {
		HBCIUtils.init(new Properties(), new HBCICallbackUnsupported());
	}

	private final static ExecutorService EXECUTOR = Executors.newCachedThreadPool(new HBCIThreadFactory());

	private final static HBCIPassportFactory PASSPORT_FACTORY = new DefaultHBCIPassportFactory((Object) "Passports");

	private final Properties props;

	private final HBCICallback callback;

	public DefaultEasyFin(String loginName, String password, BankData bankData,
			Map<String, String> additionalHBCIConfiguration) {
		this.props = initProperties(bankData, additionalHBCIConfiguration);

		this.callback = new MyHBCICallback(new MyHBCICallbackAnswers() {

			@Override
			public String getVRNetKeyPin() {
				return password;
			}

			@Override
			public String getVRNetKey() {
				return loginName;
			}

			@Override
			public BankData getBankData() {
				return bankData;
			}
		});
	}

	@Override
	public Stream<UmsLine> getTurnoversAsStream(Konto account) {
		Callable<TurnoversResult> callable = new HBCICallable<TurnoversResult>(this.props, this.callback,
				PASSPORT_FACTORY) {

			@Override
			protected TurnoversResult execute(HBCIPassport passport, HBCIHandler handler) throws Exception {
				HBCIJob job = handler.newJob("KUmsAll");
				job.setParam("my", account);
				job.setParam("startdate", "1970-01-01");
				job.addToQueue();

				HBCIExecStatus ret = handler.execute();
				checkForFailure(ret);

				GVRKUms result = (GVRKUms) job.getJobResult();
				checkForFailure(result);

				List<UmsLine> lines = result.getFlatData();

				return new TurnoversResult(lines);
			}

			private void checkForFailure(GVRKUms result) {
				if (!result.isOK()) {
					throw new IllegalStateException("Fetching turnovers failed: "
							+ result.getJobStatus().getErrorString() + " / " + result.getGlobStatus().getErrorString());
				}
			}

			private void checkForFailure(HBCIExecStatus result) {
				if (!result.isOK()) {
					throw new IllegalStateException(
							"Fetching turnovers failed: " + result.getErrorString() + " / " + result.getErrorString());
				}
			}
		};

		Future<TurnoversResult> submit = EXECUTOR.submit(callable);
		try {
			List<UmsLine> result = submit.get().getTurnovers();
			return result.stream();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public List<Konto> getAccounts() {
		Callable<AccountsResult> callable = new HBCICallable<AccountsResult>(this.props, this.callback,
				PASSPORT_FACTORY) {

			@Override
			protected AccountsResult execute(HBCIPassport passport, HBCIHandler handler) throws Exception {
				Konto[] accounts = passport.getAccounts();
				List<Konto> result = Lists.newArrayList();
				for (Konto account : accounts) {
					result.add(account);
				}
				return new AccountsResult(result);
			}
		};

		Future<AccountsResult> submit = EXECUTOR.submit(callable);
		try {
			return submit.get().getAccounts();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	public static void cleanShutdown() {
		EXECUTOR.shutdown();
		while (!EXECUTOR.isTerminated()) {
			try {
				EXECUTOR.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
		HBCIUtils.done();
	}

	private static Properties initProperties(BankData bankData, Map<String, String> additionalHBCIConfiguration) {
		Properties p = new Properties();

		// Set basic parameters
		p.setProperty("default.hbciversion", bankData.getPinTanVersion());
		p.setProperty("log.loglevel.default", "3");

		p.setProperty("client.passport.default", "PinTan");

		// Configure for PinTan
		p.setProperty("client.passport.hbciversion.default", bankData.getPinTanVersion());
		p.setProperty("client.passport.PinTan.checkcert", "1");
		p.setProperty("client.passport.PinTan.init", "1");
		File passportFile = new File("hbci---" + UUID.randomUUID().toString() + ".passport");
		passportFile.deleteOnExit();
		p.setProperty("client.passport.PinTan.filename", passportFile.getName());

		for (String key : additionalHBCIConfiguration.keySet()) {
			p.setProperty(key, additionalHBCIConfiguration.get(key));
		}

		return p;
	}
}
