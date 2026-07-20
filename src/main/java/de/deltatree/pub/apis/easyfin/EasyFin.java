package de.deltatree.pub.apis.easyfin;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;

/**
 * Threadsafe entry point for accessing a single bank connection over FinTS/HBCI
 * PIN/TAN. Obtain an instance via {@link EasyFinFactory#builder()}.
 *
 * <p>
 * Every method performs a full HBCI dialog on a dedicated single worker thread,
 * so an instance may be shared safely, but each call is executed sequentially.
 * The returned {@link Stream}s are backed by fully materialized result lists
 * (the whole dialog completes before the stream is returned); they are not lazy
 * and require no closing. Failures surface as {@link IllegalStateException} with
 * a {@code "<operation> failed: <detail>"} message.
 * </p>
 *
 * <p>
 * Always call {@link #clean()} when done (ideally in a {@code finally} block) to
 * release the worker thread and delete the transient passport file.
 * </p>
 */
public interface EasyFin {

	/**
	 * Fetches all available turnovers for the account (mode {@code KUmsAll}, from
	 * the earliest available date).
	 *
	 * @param account the account to query
	 * @return a stream of turnover lines (never {@code null})
	 */
	Stream<UmsLine> getTurnoversAsStream(Konto account);

	/**
	 * Fetches all available turnovers for the account using the given retrieval
	 * mode.
	 *
	 * @param account the account to query
	 * @param mode    the HBCI retrieval mode (MT940 vs. camt)
	 * @return a stream of turnover lines (never {@code null})
	 */
	Stream<UmsLine> getTurnoversAsStream(Konto account, GetTurnoversModeEnum mode);

	/**
	 * Fetches turnovers for the account from the given date onward (mode
	 * {@code KUmsAll}).
	 *
	 * @param account the account to query
	 * @param from    the earliest booking date to include
	 * @return a stream of turnover lines (never {@code null})
	 */
	Stream<UmsLine> getTurnoversAsStream(Konto account, Date from);

	/**
	 * Fetches turnovers for the account from the given date onward using the given
	 * retrieval mode.
	 *
	 * @param account the account to query
	 * @param from    the earliest booking date to include
	 * @param mode    the HBCI retrieval mode (MT940 vs. camt)
	 * @return a stream of turnover lines (never {@code null})
	 */
	Stream<UmsLine> getTurnoversAsStream(Konto account, Date from, GetTurnoversModeEnum mode);

	/**
	 * Lists all accounts reachable with the configured credentials.
	 *
	 * @return the accounts (never {@code null})
	 */
	List<Konto> getAccounts();

	/**
	 * Finds exactly one account whose identifying fields (account number, sub
	 * number, IBAN, holder name, BIC, BLZ, account type, currency, country or
	 * customer id) contain the given search string.
	 *
	 * @param search substring to look for
	 * @return the single matching account
	 * @throws IllegalStateException if zero or more than one account matches
	 */
	Konto getAccount(String search);

	/**
	 * Releases all resources held by this instance (worker thread, passport file).
	 * Idempotent; after calling it the instance must not be used again.
	 */
	void clean();
}
