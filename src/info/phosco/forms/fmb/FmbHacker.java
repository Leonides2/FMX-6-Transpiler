package info.phosco.forms.fmb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point: extracts every trigger and program unit (full
 * PL/SQL source) from a .fmb file and prints them, grouped by owner.
 * <p>
 * This is deliberately independent from {@code info.phosco.forms.translate}
 * (the .fmx parser) - the .fmb uses a different, much simpler storage scheme,
 * see {@link FmbLogicScanner}.
 */
public class FmbHacker {

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			System.out.println("FMB Logic Extractor v0.1");
			System.out.println("Usage: java -cp build info.phosco.forms.fmb.FmbHacker archivo.fmb");
			System.exit(1);
		}

		FmbBuffer buf = FmbBuffer.load(args[0]);
		FmbLogicScanner scanner = new FmbLogicScanner(buf);

		List<ExtractedTrigger> triggers = scanner.scanTriggers();
		List<ExtractedProgramUnit> units = scanner.scanProgramUnits();

		// group triggers by owner, then by name - warn instead of silently
		// picking one when the .fmb's duplicate copies disagree.
		Map<String, Map<String, List<String>>> byOwner = new LinkedHashMap<>();
		for (ExtractedTrigger t : triggers) {
			byOwner.computeIfAbsent(t.ownerKey(), k -> new LinkedHashMap<>())
					.computeIfAbsent(t.name, k -> new java.util.ArrayList<>())
					.add(t.source);
		}

		System.out.println("=== TRIGGERS (" + triggers.size() + " raw matches) ===");
		for (Map.Entry<String, Map<String, List<String>>> ownerEntry : byOwner.entrySet()) {
			System.out.println("\n[" + ownerEntry.getKey() + "]");
			for (Map.Entry<String, List<String>> nameEntry : ownerEntry.getValue().entrySet()) {
				List<String> sources = nameEntry.getValue();
				long distinct = sources.stream().distinct().count();
				if (distinct > 1) {
					System.out.println("  " + nameEntry.getKey() + "  (!) " + distinct + " DIFFERENT copies found, showing all:");
					int i = 1;
					for (String s : sources.stream().distinct().toArray(String[]::new)) {
						System.out.println("    --- copy " + (i++) + " ---\n" + indent(s));
					}
				} else {
					System.out.println("  " + nameEntry.getKey() + ":");
					System.out.println(indent(sources.get(0)));
				}
			}
		}

		System.out.println("\n=== PROGRAM UNITS (" + units.size() + " raw matches) ===");
		Map<String, List<String>> puByName = new LinkedHashMap<>();
		for (ExtractedProgramUnit u : units) {
			puByName.computeIfAbsent(u.name, k -> new java.util.ArrayList<>()).add(u.source);
		}
		for (Map.Entry<String, List<String>> e : puByName.entrySet()) {
			List<String> sources = e.getValue().stream().distinct().toList();
			System.out.println("\n" + e.getKey() + (sources.size() > 1 ? "  (!) " + sources.size() + " DIFFERENT copies found:" : ":"));
			for (String s : sources) {
				System.out.println(indent(s));
			}
		}
	}

	private static String indent(String s) {
		StringBuilder sb = new StringBuilder();
		for (String line : s.split("\n", -1)) {
			sb.append("    ").append(line).append('\n');
		}
		return sb.toString();
	}
}
