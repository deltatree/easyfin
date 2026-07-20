package de.deltatree.pub.apis.easyfin;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;
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

@NoArgsConstructor
@Data
public class DefaultEasyFin implements EasyFin {

	private static final String YYYY_MM_DD = "yyyy-MM-dd";

	private static final String CLEANED_MESSAGE = "This EasyFin instance has been cleaned and can no longer be used";

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

	// One passport factory per instance, keyed by the instance UUID, so concurrent
	// EasyFin instances never share hbci4java passport state.
	private HBCIPassportFactory passportFactory;

	private Properties props;

	private HBCICallback callback;

	private String pin;

	private String customerId;

	private String userId;

	private File passportFile;

	private Function<Map<String, String>, String> tanCallback;

	private Function<Map<String, String>, String> tanMethodSelector;

	private volatile boolean cleaned = false;

	public DefaultEasyFin(BankData bankData, Map<String, String> additionalHBCIConfiguration) {
		this(bankData, additionalHBCIConfiguration, null);
	}

	public DefaultEasyFin(BankData bankData, Map<String, String> additionalHBCIConfiguration, Path passportDirectory) {
		String uuid = UUID.randomUUID().toString();
		Path dir = passportDirectory != null ? passportDirectory : Paths.get(System.getProperty("java.io.tmpdir"));
		this.passportFile = dir.resolve("hbci---" + uuid + ".passport").toFile();
		this.passportFile.deleteOnExit();

		this.passportFactory = new DefaultHBCIPassportFactory((Object) uuid);

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

			@Override
			public Function<Map<String, String>, String> getTanMethodSelector() {
				return DefaultEasyFin.this.tanMethodSelector;
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
		ensureActive();
		Callable<TurnoversResult> callable = new HBCICallable<TurnoversResult>(this.props, this.callback,
				this.passportFactory) {

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
					throw new IllegalStateException(
							result.getJobStatus().getErrorString() + " / " + result.getGlobStatus().getErrorString());
				}
			}

			private void checkForFailure(HBCIExecStatus result) {
				if (!result.isOK()) {
					throw new IllegalStateException(result.getErrorString());
				}
			}
		};

		Future<TurnoversResult> submit = submitOrFail(callable);
		try {
			List<UmsLine> result = submit.get().getTurnovers();
			return result.stream();
		} catch (ExecutionException e) {
			throw failed("Fetching turnovers failed", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Fetching turnovers interrupted", e);
		}
	}

	@Override
	public List<Konto> getAccounts() {
		ensureActive();
		Callable<AccountsResult> callable = new HBCICallable<AccountsResult>(this.props, this.callback,
				this.passportFactory) {

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

		Future<AccountsResult> submit = submitOrFail(callable);
		try {
			return submit.get().getAccounts();
		} catch (ExecutionException e) {
			throw failed("Fetching accounts failed", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Fetching accounts interrupted", e);
		}
	}

	/**
	 * Releases all per-instance resources: shuts down the executor and deletes the
	 * transient passport file. Idempotent — calling it more than once is safe.
	 * After {@code clean()} the instance must not be reused; further API calls
	 * throw {@link IllegalStateException}.
	 */
	public synchronized void clean() {
		if (this.cleaned) {
			return;
		}
		this.cleaned = true;
		EXECUTOR.shutdown();
		while (!EXECUTOR.isTerminated()) {
			try {
				EXECUTOR.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while shutting down EasyFin", e);
			}
		}
		if (this.passportFile != null) {
			this.passportFile.delete();
		}
	}

	private void ensureActive() {
		if (this.cleaned) {
			throw new IllegalStateException(CLEANED_MESSAGE);
		}
	}

	/**
	 * Submits work to the instance executor. {@link #ensureActive()} rejects the
	 * common case early, but a concurrent {@link #clean()} can still shut the
	 * executor down between the guard and the submit; that race must surface as the
	 * documented {@link IllegalStateException}, not as a raw
	 * {@link RejectedExecutionException}.
	 */
	private <A extends HBCICommandResult> Future<A> submitOrFail(Callable<A> callable) {
		try {
			return EXECUTOR.submit(callable);
		} catch (RejectedExecutionException e) {
			throw new IllegalStateException(CLEANED_MESSAGE, e);
		}
	}

	// Single, boundary-level failure formatting: "<operation> failed: <detail>" with
	// the underlying error text appearing exactly once, preserving the cause chain.
	private static IllegalStateException failed(String operation, Throwable cause) {
		String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : "unknown error";
		return new IllegalStateException(operation + ": " + detail, cause);
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
		p.setProperty("client.passport.PinTan.filename", passportFile.getAbsolutePath());

		// User-provided configuration is applied last and therefore wins (documented precedence).
		for (String key : additionalHBCIConfiguration.keySet()) {
			p.setProperty(key, additionalHBCIConfiguration.get(key));
		}

		return p;
	}

	public void setTanCallback(Function<Map<String, String>, String> tanCallback) {
		this.tanCallback = tanCallback;
	}

	/**
	 * Finds exactly one account whose identifying fields (account number, sub
	 * number, IBAN, holder name, BIC, BLZ, account type, currency, country or
	 * customer id) contain the given search string.
	 *
	 * @param search substring to look for
	 * @return the single matching account
	 * @throws IllegalStateException if zero or more than one account matches
	 */
	@Override
	public Konto getAccount(String search) {
		List<Konto> list = getAccounts().stream().filter(k -> matchesSearch(k, search)).collect(Collectors.toList());
		if (list.size() == 1) {
			return list.get(0);
		} else if (list.isEmpty()) {
			throw new IllegalStateException("search with " + search + " gives no results");
		} else {
			throw new IllegalStateException(
					"Multiple results: " + list.stream().map(Konto::toString).collect(Collectors.joining(",")));
		}
	}

	private static boolean matchesSearch(Konto k, String search) {
		if (search == null) {
			return false;
		}
		return contains(k.number, search) || contains(k.subnumber, search) || contains(k.iban, search)
				|| contains(k.name, search) || contains(k.name2, search) || contains(k.bic, search)
				|| contains(k.blz, search) || contains(k.type, search) || contains(k.curr, search)
				|| contains(k.country, search) || contains(k.customerid, search) || contains(k.acctype, search);
	}

	private static boolean contains(String field, String search) {
		return field != null && field.contains(search);
	}
}
