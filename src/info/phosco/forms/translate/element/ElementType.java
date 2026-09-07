package info.phosco.forms.translate.element;

public enum ElementType {

	ALERT(0xA), PARAMETER(0xD),

	TRIGGER(0xF7),
	LIBRARY(0xF8),
	DATA_BLOCK(0xFB),
	RECORD_GROUP_COLUMN(0xFC),
	RECORD_GROUP(0xFD), // could be 0x3!
	MODULE(0xFF),
	PROGRAM_UNIT(0x9);

	private final int hex;

	private ElementType(int hex) {
		this.hex = hex;
	}

	public int hex() {
		return this.hex;
	}

	public static ElementType lookup(int hex) {
		for (ElementType t : ElementType.values()) {
			if (t.hex == hex) {
				return t;
			}
		}
		throw new IllegalArgumentException("Unknown FormElement Type 0x" + Integer.toHexString(hex));
	}
}
