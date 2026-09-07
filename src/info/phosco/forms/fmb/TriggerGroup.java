package info.phosco.forms.fmb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All raw {@link ExtractedTrigger} matches for one (scope, block, item, name)
 * combination, collapsed to their distinct source texts. The .fmb seems to
 * keep more than one internal copy of some triggers; when those copies
 * disagree we keep every distinct version rather than silently picking one.
 */
public class TriggerGroup {

	public final TriggerScope scope;
	public final String blockName;
	public final String itemName;
	public final String name;
	public final List<String> distinctSources;

	private TriggerGroup(TriggerScope scope, String blockName, String itemName, String name, List<String> distinctSources) {
		this.scope = scope;
		this.blockName = blockName;
		this.itemName = itemName;
		this.name = name;
		this.distinctSources = distinctSources;
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

	public boolean hasConflict() {
		return distinctSources.size() > 1;
	}

	/** Groups raw scanner matches by owner then trigger name, preserving first-seen order. */
	public static List<TriggerGroup> groupAll(List<ExtractedTrigger> raw) {
		Map<String, ExtractedTrigger> first = new LinkedHashMap<>(); // owner|name -> one sample (for scope/block/item)
		Map<String, List<String>> sources = new LinkedHashMap<>();
		for (ExtractedTrigger t : raw) {
			String key = t.ownerKey() + "|" + t.name;
			first.putIfAbsent(key, t);
			sources.computeIfAbsent(key, k -> new ArrayList<>()).add(t.source);
		}
		List<TriggerGroup> res = new ArrayList<>();
		for (Map.Entry<String, ExtractedTrigger> e : first.entrySet()) {
			ExtractedTrigger t = e.getValue();
			List<String> distinct = sources.get(e.getKey()).stream().distinct().toList();
			res.add(new TriggerGroup(t.scope, t.blockName, t.itemName, t.name, distinct));
		}
		return res;
	}
}
