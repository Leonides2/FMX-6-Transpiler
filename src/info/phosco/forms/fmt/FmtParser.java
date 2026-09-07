package info.phosco.forms.fmt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the Oracle Forms "ROS Script" text export (`.fmt`/`.mmt`/`.plt`,
 * produced by Forms Builder's text-export feature). It's a plain, regular,
 * line-oriented grammar - a generic property-graph dump, not the compiled
 * binary format:
 * <pre>
 * DESCRIBE  &lt;table&gt;
 * BEGIN
 *   &lt;type&gt;  &lt;field&gt;
 *   ...
 * END
 *
 * DEFINE  &lt;table&gt;
 * BEGIN
 *   &lt;field&gt; = &lt;value&gt;
 *   ...
 * END
 * </pre>
 * where {@code <value>} is one of: a bare (possibly negative) integer,
 * {@code NULLP}, a single-line quoted string {@code <<"...">>}, or a
 * multi-line hex blob:
 * <pre>
 * &lt;field&gt; = (BLONG)
 * &lt;&lt;"
 * 0a0b0c0d ...
 * ...
 * "&gt;&gt;
 * </pre>
 * This class only cares about {@code DEFINE} blocks (the actual data) -
 * {@code DESCRIBE} blocks just declare field names/types redundantly with
 * what the DEFINE blocks already show, so they're skipped entirely. See
 * {@code docs/fmb-format-notes.md} for what's been learned about the tables
 * this format actually contains (object directory + generic per-type-tagged
 * property tables).
 */
public class FmtParser {

	public static List<FmtRecord> parse(String path) throws IOException {
		List<FmtRecord> records = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new FileReader(path, java.nio.charset.StandardCharsets.ISO_8859_1))) {
			String line;
			while ((line = in.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.startsWith("DEFINE")) {
					records.add(parseDefine(trimmed, in));
				} else if (trimmed.startsWith("DESCRIBE")) {
					skipBlock(in);
				}
				// everything else (comments, blank lines, stray BEGIN/END) is noise between blocks
			}
		}
		return records;
	}

	private static void skipBlock(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.trim().equals("END")) {
				return;
			}
		}
	}

	private static FmtRecord parseDefine(String defineLine, BufferedReader in) throws IOException {
		String table = defineLine.substring("DEFINE".length()).trim();
		FmtRecord rec = new FmtRecord(table);

		String line = in.readLine(); // expect BEGIN
		if (line == null || !line.trim().equals("BEGIN")) {
			return rec; // malformed - return what we have (empty)
		}

		while ((line = in.readLine()) != null) {
			String trimmed = line.trim();
			if (trimmed.equals("END")) {
				break;
			}
			if (trimmed.isEmpty()) {
				continue;
			}
			int eq = trimmed.indexOf('=');
			if (eq < 0) {
				continue; // unexpected - be lenient
			}
			String field = trimmed.substring(0, eq).trim();
			String valuePart = trimmed.substring(eq + 1).trim();
			rec.fields.put(field, parseValue(valuePart, in));
		}
		return rec;
	}

	/** value is the trimmed text right after "FIELD = " on its own line; may need to read more lines (BLONG). */
	private static Object parseValue(String value, BufferedReader in) throws IOException {
		if (value.equals("NULLP")) {
			return null;
		}
		if (value.equals("(BLONG)")) {
			return readBlong(in);
		}
		if (value.startsWith("<<\"") && value.endsWith("\">>") && value.length() >= 6) {
			return value.substring(3, value.length() - 3);
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return value; // unknown shape - keep raw text rather than throw the whole file away
		}
	}

	private static byte[] readBlong(BufferedReader in) throws IOException {
		String opener = in.readLine();
		if (opener == null || !opener.trim().equals("<<\"")) {
			return new byte[0]; // unexpected shape
		}
		StringBuilder hex = new StringBuilder();
		String line;
		while ((line = in.readLine()) != null) {
			String trimmed = line.trim();
			if (trimmed.equals("\">>")) {
				break;
			}
			for (int i = 0; i < trimmed.length(); i++) {
				char c = trimmed.charAt(i);
				if (isHex(c)) {
					hex.append(c);
				}
			}
		}
		int n = hex.length() / 2;
		byte[] res = new byte[n];
		for (int i = 0; i < n; i++) {
			res[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
		}
		return res;
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private FmtParser() {
	}
}
