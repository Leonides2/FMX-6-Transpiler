package info.phosco.forms.fmb;

/** A program unit (stored procedure/function attached to the form) recovered from a .fmb. */
public class ExtractedProgramUnit {

	public final String name;
	public final String source;
	public final int offset;

	public ExtractedProgramUnit(String name, String source, int offset) {
		this.name = name;
		this.source = source;
		this.offset = offset;
	}

	@Override
	public String toString() {
		return "PROGRAM UNIT " + name + " @0x" + Integer.toHexString(offset) + "\n" + source;
	}
}
