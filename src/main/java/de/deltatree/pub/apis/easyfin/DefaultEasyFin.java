package de.deltatree.pub.apis.easyfin;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.concurrent.DefaultHBCIPassportFactory;
import org.kapott.hbci.concurrent.HBCIPassportFactory;
import org.kapott.hbci.concurrent.HBCIThreadFactory;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class DefaultEasyFin implements EasyFin {

	private static final String YYYY_MM_DD = "yyyy-MM-dd";

	static {
		HBCIUtils.init(new Properties(), new HBCICallback() {
			@Override
			public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
				// Do nothing - classify all calls as successful
			}

			@Override
			public void status(HBCIPassport passport, int statusTag, Object[] o) {
				// Do nothing - classify all calls as successful
			}

			@Override
			public void log(String msg, int level, Date date, StackTraceElement trace) {
				// Do nothing - classify all calls as successful
			}

			@Override
			public void status(HBCIPassport passport, int statusTag, Object o) {
				// Do nothing - classify all calls as successful
			}

			@Override
			public boolean useThreadedCallback(HBCIPassport passport, int reason, String msg, int datatype,
					StringBuffer retData) {
				return false; // Do not use threaded callback
			}
		});
	}

	private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new HBCIThreadFactory());

	private final static HBCIPassportFactory PASSPORT_FACTORY = new DefaultHBCIPassportFactory((Object) "Passports");

	private Properties props;

	private HBCICallback callback;

	private String pin;

	private String customerId;

	private String userId;

	private File passportFile;

	private Function<Map<String, String>, String> tanCallback;

	public DefaultEasyFin(BankData bankData, Map<String, String> additionalHBCIConfiguration) {
		this.passportFile = new File("hbci---" + UUID.randomUUID().toString() + ".passport");
		this.passportFile.deleteOnExit();

		this.props = initProperties(bankData, additionalHBCIConfiguration, this.passportFile);

		this.callback = new MyHBCICallback(new MyHBCICallbackAnswers() {

			@Override
			public String getPin() {
				return DefaultEasyFin.this.pin;
			}

			@Override
			public String getUserId() {
				return DefaultEasyFin.this.userId;
			}

			@Override
			public BankData getBankData() {
				return bankData;
			}

			@Override
			public String getCustomerId() {
				return DefaultEasyFin.this.customerId;
			}

			@Override
			public Function<Map<String, String>, String> getTanCallback() {
				return DefaultEasyFin.this.tanCallback;
			}
		});
	}

	@Override
	public Stream<UmsLine> getTurnoversAsStream(Konto account) {
		return getTurnoversAsStream(account, GetTurnoversModeEnum.KUmsAll);
	}

	@Override
	public Stream<UmsLine> getTurnoversAsStream(Konto account, GetTurnoversModeEnum mode) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
			return getTurnoversAsStream(account, sdf.parse("1970-01-01"), mode);
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Stream<UmsLine> getTurnoversAsStream(Konto account, Date from) {
		return getTurnoversAsStream(account, from, GetTurnoversModeEnum.KUmsAll);
	}

	@Override
	public Stream<UmsLine> getTurnoversAsStream(Konto account, Date from, GetTurnoversModeEnum mode) {
		Callable<TurnoversResult> callable = new HBCICallable<TurnoversResult>(this.props, this.callback,
				PASSPORT_FACTORY) {

			@Override
			protected TurnoversResult execute(HBCIPassport passport, HBCIHandler handler) throws Exception {
				HBCIJob job = handler.newJob(mode.name());
				job.setParam("my", account);
				job.setParam("startdate", new SimpleDateFormat(YYYY_MM_DD).format(from));
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
				List<Konto> result = new ArrayList<Konto>();
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

	public void clean() {
		EXECUTOR.shutdown();
		while (!EXECUTOR.isTerminated()) {
			try {
				EXECUTOR.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}
		this.passportFile.delete();
	}

	private static Properties initProperties(BankData bankData, Map<String, String> additionalHBCIConfiguration,
			File passportFile) {
		Properties p = new Properties();

		// Set basic parameters
		p.setProperty("default.hbciversion", bankData.getPinTanVersion());
		p.setProperty("log.loglevel.default", "1");

		p.setProperty("client.passport.default", "PinTan");

		// Configure for PinTan
		p.setProperty("client.passport.hbciversion.default", bankData.getPinTanVersion());
		p.setProperty("client.passport.PinTan.checkcert", "1");
		p.setProperty("client.passport.PinTan.init", "1");
		p.setProperty("client.passport.PinTan.filename", passportFile.getName());

		for (String key : additionalHBCIConfiguration.keySet()) {
			p.setProperty(key, additionalHBCIConfiguration.get(key));
		}

		return p;
	}

	public void setTanCallback(Function<Map<String, String>, String> tanCallback) {
		this.tanCallback = tanCallback;
	}

	@Override
	public Konto getAccount(String search) {
		List<Konto> list = getAccounts().stream().filter(k -> k.toString().contains(search))
				.collect(Collectors.toList());
		if (list.size() == 1) {
			return list.get(0);
		} else if (list.size() < 1) {
			throw new IllegalStateException("search with " + search + " gives no results");
		} else {
			throw new IllegalStateException(
					"Multiple results: " + list.stream().map(k -> k.toString()).collect(Collectors.joining(",")));
		}
	}
}
