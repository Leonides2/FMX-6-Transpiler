package info.phosco.forms.fmt;

/**
 * One property of an {@link FmtObject}: a (family, index, code, value) tuple
 * from one of the generic F50* tables. `family` is the table's value-kind
 * ('T'ext, 'N'umber, 'B'oolean, 'P'roperty/blob, 'S'tring+extra, 'O'bject-ref);
 * `code` is the numeric property id (e.g. 211 = "Name", empirically, across
 * every object kind seen so far) - its human meaning is not yet known for
 * most codes, see docs/fmb-format-notes.md.
 */
public class FmtProperty {

	public final char family;
	public final long index;
	public final long code;
	public final Object value; // Long, String, byte[], or null

	public FmtProperty(char family, long index, long code, Object value) {
		this.family = family;
		this.index = index;
		this.code = code;
		this.value = value;
	}

	@Override
	public String toString() {
		String v = value instanceof byte[] ? "(BLONG " + ((byte[]) value).length + " bytes)" : String.valueOf(value);
		return family + "[" + code + "] = " + v;
	}
}
