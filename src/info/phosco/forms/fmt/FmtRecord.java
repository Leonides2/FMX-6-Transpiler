package info.phosco.forms.fmt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One {@code DEFINE <table> BEGIN ... END} block from a .fmt file, as a
 * generic bag of fields. Field values are one of: {@link Long} (a plain
 * integer), {@link String} (a quoted `&lt;&lt;"..."&gt;&gt;` value), {@code byte[]}
 * (a `(BLONG)` hex blob), or {@code null} (the file's `NULLP` sentinel).
 */
public class FmtRecord {

	public final String table;
	public final Map<String, Object> fields = new LinkedHashMap<>();

	public FmtRecord(String table) {
		this.table = table;
	}

	public Long getLong(String field) {
		Object v = fields.get(field);
		return v instanceof Long ? (Long) v : null;
	}

	public String getString(String field) {
		Object v = fields.get(field);
		return v instanceof String ? (String) v : null;
	}

	public byte[] getBytes(String field) {
		Object v = fields.get(field);
		return v instanceof byte[] ? (byte[]) v : null;
	}

	@Override
	public String toString() {
		return "DEFINE " + table + " " + fields;
	}
}
