package info.phosco.forms.fmb;

/**
 * A trigger recovered from a .fmb, with its full PL/SQL body as literal text.
 * <p>
 * The .fmb stores every trigger followed by a label of the form
 * {@code "TRIGGER-NAME (Form)"}, {@code "TRIGGER-NAME (BLOCK)"} or
 * {@code "TRIGGER-NAME (BLOCK.ITEM)"} - that label alone is enough to tell
 * apart form/block/item scope without needing the .fmx offset-based item
 * structure at all.
 */
public class ExtractedTrigger {

	public final String name;
	public final TriggerScope scope;
	public final String blockName; // null for FORM scope
	public final String itemName; // null unless ITEM scope
	public final String source;
	public final int offset;

	public ExtractedTrigger(String name, TriggerScope scope, String blockName, String itemName, String source, int offset) {
		this.name = name;
		this.scope = scope;
		this.blockName = blockName;
		this.itemName = itemName;
		this.source = source;
		this.offset = offset;
	}

	/** Parses a "(Form)" / "(BLOCK)" / "(BLOCK.ITEM)" owner label into a trigger, or null if the label doesn't match. */
	static ExtractedTrigger parse(String name, String label, String source, int offset) {
		if (!label.startsWith("(") || !label.endsWith(")")) {
			return null;
		}
		String owner = label.substring(1, label.length() - 1);
		if (owner.equals("Form")) {
			return new ExtractedTrigger(name, TriggerScope.FORM, null, null, source, offset);
		}
		int dot = owner.indexOf('.');
		if (dot < 0) {
			return new ExtractedTrigger(name, TriggerScope.BLOCK, owner, null, source, offset);
		}
		return new ExtractedTrigger(name, TriggerScope.ITEM, owner.substring(0, dot), owner.substring(dot + 1), source, offset);
	}

	public String ownerKey() {
		switch (scope) {
		case FORM:
			return "Form";
		case BLOCK:
			return blockName;
		default:
			return blockName + "." + itemName;
		}
	}

	@Override
	public String toString() {
		return "TRIGGER " + name + " (" + ownerKey() + ") @0x" + Integer.toHexString(offset) + "\n" + source;
	}
}
