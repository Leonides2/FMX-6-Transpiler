package info.phosco.forms.fmb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All raw {@link ExtractedProgramUnit} matches for one name, collapsed to their distinct source texts. */
public class ProgramUnitGroup {

	public final String name;
	public final List<String> distinctSources;

	private ProgramUnitGroup(String name, List<String> distinctSources) {
		this.name = name;
		this.distinctSources = distinctSources;
	}

	public boolean hasConflict() {
		return distinctSources.size() > 1;
	}

	public static List<ProgramUnitGroup> groupAll(List<ExtractedProgramUnit> raw) {
		Map<String, List<String>> sources = new LinkedHashMap<>();
		for (ExtractedProgramUnit u : raw) {
			sources.computeIfAbsent(u.name, k -> new ArrayList<>()).add(u.source);
		}
		List<ProgramUnitGroup> res = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : sources.entrySet()) {
			res.add(new ProgramUnitGroup(e.getKey(), e.getValue().stream().distinct().toList()));
		}
		return res;
	}
}
