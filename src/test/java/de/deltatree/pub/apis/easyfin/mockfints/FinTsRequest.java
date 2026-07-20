package de.deltatree.pub.apis.easyfin.mockfints;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A decoded FinTS request, split into segments.
 *
 * <p>
 * The tokenizer honours the two FinTS escaping rules that matter here: a
 * {@code ?} escapes the following delimiter, and {@code @len@} introduces a
 * binary block whose bytes are copied verbatim (the PIN/TAN payload container
 * {@code HNVSD} uses one).
 * </p>
 */
public final class FinTsRequest {

	private final List<String> segments;

	private FinTsRequest(List<String> segments) {
		this.segments = segments;
	}

	public static FinTsRequest parse(String raw) {
		List<String> segments = splitSegments(raw);
		// The PIN/TAN payload container carries the real segments as a binary block.
		List<String> flattened = new ArrayList<>();
		for (String segment : segments) {
			if (segment.startsWith("HNVSD")) {
				int at = segment.indexOf('@');
				int at2 = segment.indexOf('@', at + 1);
				if (at != -1 && at2 != -1) {
					flattened.addAll(splitSegments(segment.substring(at2 + 1)));
					continue;
				}
			}
			flattened.add(segment);
		}
		return new FinTsRequest(flattened);
	}

	private static List<String> splitSegments(String raw) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '?' && i + 1 < raw.length()) {
				current.append(c).append(raw.charAt(++i));
			} else if (c == '@') {
				int close = raw.indexOf('@', i + 1);
				if (close != -1) {
					String lengthText = raw.substring(i + 1, close);
					try {
						int length = Integer.parseInt(lengthText);
						current.append(raw, i, close + 1).append(raw, close + 1,
								Math.min(close + 1 + length, raw.length()));
						i = close + length;
						continue;
					} catch (NumberFormatException ignored) {
						// not a binary block, fall through
					}
				}
				current.append(c);
			} else if (c == '\'') {
				if (current.length() > 0) {
					result.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			result.add(current.toString());
		}
		return result;
	}

	public List<String> segments() {
		return this.segments;
	}

	/** True when the request contains a segment with the given FinTS code. */
	public boolean has(String code) {
		return segment(code).isPresent();
	}

	/** The first segment with the given code, if present. */
	public Optional<String> segment(String code) {
		return this.segments.stream().filter(s -> s.startsWith(code + ":")).findFirst();
	}

	/** The sequence number of the first segment with the given code. */
	public int segmentNumber(String code) {
		return segment(code).map(s -> {
			String[] head = s.split("\\+", 2)[0].split(":");
			return head.length > 1 ? Integer.parseInt(head[1]) : 0;
		}).orElse(0);
	}

	/** The dialog id from the message head, or {@code "0"} for a new dialog. */
	public String dialogId() {
		return segment("HNHBK").map(s -> field(s, 3)).orElse("0");
	}

	/** The message number from the message head. */
	public int msgNum() {
		return segment("HNHBK").map(s -> {
			try {
				return Integer.parseInt(field(s, 4));
			} catch (NumberFormatException e) {
				return 1;
			}
		}).orElse(1);
	}

	/** Returns the n-th {@code +}-separated field of a segment (0 = segment head). */
	static String field(String segment, int index) {
		String[] parts = segment.split("(?<!\\?)\\+");
		return index < parts.length ? parts[index] : "";
	}
}
