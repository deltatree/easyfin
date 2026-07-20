package de.deltatree.pub.apis.easyfin.mockfints;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a syntactically valid FinTS 3.0 message.
 *
 * <p>
 * Responses are sent unencrypted and unsigned: hbci4java first tries to parse a
 * reply as {@code CryptedRes} and falls back to the plain {@code <Msg>Res}
 * form, and the signature segments are declared {@code minnum="0"} in the
 * hbci-300 syntax. That keeps the simulator free of crypto while still
 * producing messages the real client accepts.
 * </p>
 *
 * <p>
 * The builder owns the two things that are easy to get wrong by hand: the
 * running segment numbers and the 12-digit message length in {@code HNHBK}.
 * </p>
 */
public final class FinTsMessage {

	private static final String LENGTH_PLACEHOLDER = "000000000000";

	private final String dialogId;
	private final int msgNum;
	private final String refDialogId;
	private final int refMsgNum;
	private final List<String> segments = new ArrayList<>();
	private int nextSegNum = 2; // 1 is the message head

	private final String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
	private final String time = DateTimeFormatter.ofPattern("HHmmss").format(LocalDateTime.now());
	private String blz = MockBank.BLZ;
	private String userId = MockBank.USER_ID;

	private FinTsMessage(String dialogId, int msgNum, String refDialogId, int refMsgNum) {
		this.dialogId = dialogId;
		this.msgNum = msgNum;
		this.refDialogId = refDialogId;
		this.refMsgNum = refMsgNum;
	}

	/** Identifies the key owner in the encryption head. */
	public FinTsMessage keyOwner(String blz, String userId) {
		this.blz = blz;
		this.userId = userId;
		return this;
	}

	/**
	 * @param dialogId    the dialog this message belongs to
	 * @param msgNum      the number of this message within the dialog
	 * @param refDialogId dialog id of the message being answered
	 * @param refMsgNum   message number of the message being answered
	 */
	public static FinTsMessage response(String dialogId, int msgNum, String refDialogId, int refMsgNum) {
		return new FinTsMessage(dialogId, msgNum, refDialogId, refMsgNum);
	}

	/**
	 * Appends a segment. Use {@code {n}} as a placeholder for this segment's
	 * number, which the builder assigns.
	 *
	 * @param template segment text without the trailing apostrophe
	 * @return this builder
	 */
	public FinTsMessage segment(String template) {
		int seq = this.nextSegNum++;
		this.segments.add(template.replace("{n}", Integer.toString(seq)));
		return this;
	}

	/** A global result segment (HIRMG). */
	public FinTsMessage retGlob(String code, String text) {
		return segment("HIRMG:{n}:2+" + code + "::" + escape(text));
	}

	/** A segment-related result segment (HIRMS) referring to a request segment. */
	public FinTsMessage retSeg(int refSegment, String code, String text) {
		return segment("HIRMS:{n}:2:" + refSegment + "+" + code + "::" + escape(text));
	}

	/** A segment-related result carrying additional parameters. */
	public FinTsMessage retSeg(int refSegment, String code, String text, String... params) {
		StringBuilder sb = new StringBuilder("HIRMS:{n}:2:" + refSegment + "+" + code + "::" + escape(text));
		for (String p : params) {
			sb.append(':').append(escape(p));
		}
		return segment(sb.toString());
	}

	/**
	 * Renders the message inside the PIN/TAN encryption envelope
	 * ({@code HNVSK}/{@code HNVSD}). hbci4java rejects an unencrypted reply
	 * ("Nachricht ist nicht verschlüsselt"), and for PIN/TAN the envelope is
	 * plaintext, so wrapping is a framing concern rather than real cryptography.
	 */
	public String render() {
		StringBuilder payload = new StringBuilder();
		for (String segment : this.segments) {
			payload.append(segment).append('\'');
		}

		String timestamp = "1:" + this.date + ":" + this.time;
		String cryptHead = "HNVSK:998:3+PIN:1+998+1+1::0+" + timestamp + "+2:2:13:@8@00000000:5:1+280:" + this.blz + ":"
				+ this.userId + ":V:0:0+0'";
		String cryptData = "HNVSD:999:1+@" + payload.length() + "@" + payload + "'";
		String tail = "HNHBS:" + this.nextSegNum + ":1+" + this.msgNum + "'";

		String head = "HNHBK:1:3+" + LENGTH_PLACEHOLDER + "+300+" + this.dialogId + "+" + this.msgNum + "+"
				+ this.refDialogId + ":" + this.refMsgNum + "'";

		String full = head + cryptHead + cryptData + tail;
		String length = String.format("%012d", full.length());
		return full.replaceFirst(LENGTH_PLACEHOLDER, length);
	}

	/**
	 * Escapes the FinTS delimiters so free text cannot break the framing.
	 */
	static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : value.toCharArray()) {
			if (c == '+' || c == ':' || c == '\'' || c == '?' || c == '@') {
				sb.append('?');
			}
			sb.append(c);
		}
		return sb.toString();
	}

	/** Wraps a payload as a FinTS binary block ({@code @len@data}). */
	static String binary(String payload) {
		return "@" + payload.length() + "@" + payload;
	}
}
