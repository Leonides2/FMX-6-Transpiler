package info.phosco.forms.fmb;

/** One declared identifier (block, item, radio button value, alert, trigger owner, ...) found in a .fmb. */
public class NameAnchor {

	public final int offset;
	public final String name;

	public NameAnchor(int offset, String name) {
		this.offset = offset;
		this.name = name;
	}

	@Override
	public String toString() {
		return "0x" + Integer.toHexString(offset) + ": " + name;
	}
}
