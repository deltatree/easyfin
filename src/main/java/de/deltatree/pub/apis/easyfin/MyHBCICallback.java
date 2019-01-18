package de.deltatree.pub.apis.easyfin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.HBCIPassport;

public class MyHBCICallback extends HBCICallbackConsole implements HBCICallback {
	private final RandomString randomString = new RandomString();
	private final String password;
	private MyHBCICallbackAnswers answers;

	private Pattern P = Pattern.compile("(\\d{1,})");

	public MyHBCICallback(MyHBCICallbackAnswers answers) {
		this.answers = answers;
		this.password = this.randomString.generateRandomString(34);
	}

	@Override
	public synchronized void status(HBCIPassport passport, int statusTag, Object[] o) {
		// Intentionally empty
	}

	@Override
	public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
		HBCIUtils.log("[LOG] " + msg + " / Reason: " + reason + " / datatype: " + datatype, HBCIUtils.LOG_DEBUG);

		switch (reason) {
		case NEED_BLZ:
			retData.append(this.answers.getBankData().getBlz());
			break;

		case NEED_CUSTOMERID:
			retData.append(this.answers.getCustomerId());
			break;

		case NEED_USERID:
			retData.append(this.answers.getUserId());
			break;

		case NEED_PT_PIN:
			retData.append(this.answers.getPin());
			break;

		case NEED_PASSPHRASE_SAVE:
		case NEED_PASSPHRASE_LOAD:
			retData.append(this.password);
			break;

		case NEED_PT_SECMECH:
			Matcher matcher = P.matcher(retData.toString());
			matcher.find();
			retData.delete(0, retData.length());
			retData.append(matcher.group(1));
			break;

		case NEED_COUNTRY:
		case NEED_HOST:
		case NEED_CONNECTION:
		case CLOSE_CONNECTION:
		default:
			// Intentionally empty!
		}

		HBCIUtils.log("Returning " + retData.toString(), HBCIUtils.LOG_DEBUG);
	}
}
