package info.phosco.forms.fmb;

import info.phosco.forms.xml.XmlWriter;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point: extracts every trigger and program unit (full
 * PL/SQL source) from a .fmb file and either prints a human-readable report
 * or writes it out as XML (see {@link FmbXmlExporter}).
 * <p>
 * This is deliberately independent from {@code info.phosco.forms.translate}
 * (the .fmx parser) - the .fmb uses a different, much simpler storage scheme,
 * see {@link FmbLogicScanner}.
 */
public class FmbHacker {

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			System.out.println("FMB Logic Extractor v0.1");
			System.out.println("Usage: java -cp build info.phosco.forms.fmb.FmbHacker archivo.fmb [output.xml]");
			System.out.println("  Sin segundo argumento: imprime un reporte de texto en pantalla.");
			System.out.println("  Con segundo argumento: escribe XML en esa ruta (y solo un resumen en pantalla).");
			System.exit(1);
		}

		FmbBuffer buf = FmbBuffer.load(args[0]);
		FmbLogicScanner scanner = new FmbLogicScanner(buf);

		List<ExtractedTrigger> triggers = scanner.scanTriggers();
		List<ExtractedProgramUnit> units = scanner.scanProgramUnits();
		List<NameAnchor> nameAnchors = new NameAnchorScanner(buf).scan();

		if (args.length >= 2) {
			try (XmlWriter xml = new XmlWriter(new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8))) {
				FmbXmlExporter.export(args[0], triggers, units, nameAnchors, xml);
			}
			System.out.println("Escrito " + args[1] + " (" + TriggerGroup.groupAll(triggers).size() + " triggers, "
					+ ProgramUnitGroup.groupAll(units).size() + " program units, " + nameAnchors.size()
					+ " nombres detectados; de " + triggers.size() + "/" + units.size() + " coincidencias crudas).");
			return;
		}

		printTextReport(triggers, units, nameAnchors);
	}

	private static void printTextReport(List<ExtractedTrigger> rawTriggers, List<ExtractedProgramUnit> rawUnits, List<NameAnchor> nameAnchors) {

		System.out.println("=== NOMBRES DETECTADOS (" + nameAnchors.size() + ", orden de aparición en el archivo) ===");
		System.out.println("(bloques, items, valores de radio, alertas... sin clasificar por rol - ver docs/fmx-bytecode-notes.md)");
		for (NameAnchor n : nameAnchors) {
			System.out.println("  " + n);
		}
		System.out.println();

		Map<String, List<TriggerGroup>> byOwner = new LinkedHashMap<>();
		for (TriggerGroup g : TriggerGroup.groupAll(rawTriggers)) {
			byOwner.computeIfAbsent(g.ownerKey(), k -> new java.util.ArrayList<>()).add(g);
		}

		System.out.println("=== TRIGGERS (" + rawTriggers.size() + " raw matches) ===");
		for (Map.Entry<String, List<TriggerGroup>> ownerEntry : byOwner.entrySet()) {
			System.out.println("\n[" + ownerEntry.getKey() + "]");
			for (TriggerGroup g : ownerEntry.getValue()) {
				if (g.hasConflict()) {
					System.out.println("  " + g.name + "  (!) " + g.distinctSources.size() + " DIFFERENT copies found, showing all:");
					int i = 1;
					for (String s : g.distinctSources) {
						System.out.println("    --- copy " + (i++) + " ---\n" + indent(s));
					}
				} else {
					System.out.println("  " + g.name + ":");
					System.out.println(indent(g.distinctSources.get(0)));
				}
			}
		}

		List<ProgramUnitGroup> units = ProgramUnitGroup.groupAll(rawUnits);
		System.out.println("\n=== PROGRAM UNITS (" + rawUnits.size() + " raw matches) ===");
		for (ProgramUnitGroup g : units) {
			System.out.println("\n" + g.name + (g.hasConflict() ? "  (!) " + g.distinctSources.size() + " DIFFERENT copies found:" : ":"));
			for (String s : g.distinctSources) {
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
