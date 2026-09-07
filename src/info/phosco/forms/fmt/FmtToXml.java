package info.phosco.forms.fmt;

import info.phosco.forms.xml.XmlWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command-line entry point: converts a .fmt (Forms Builder text export) to
 * XML, one file or a whole folder tree at a time.
 *
 * <pre>
 * java -cp build info.phosco.forms.fmt.FmtToXml archivo.fmt [salida.xml] [--brief]
 * java -cp build info.phosco.forms.fmt.FmtToXml carpetaFmt carpetaXml [--brief]
 * </pre>
 *
 * In folder mode every {@code .fmt} found underneath the input folder is
 * converted, the source subfolder layout is mirrored in the output folder, and
 * a per-file summary line plus totals are printed - including how many
 * triggers and program units decoded through the confirmed blob layout versus
 * the fallback, so a bad batch is visible rather than silent.
 * <p>
 * {@code --brief} leaves out the properties whose meaning hasn't been
 * identified yet ({@code <Property code="N372" .../>}), which makes the output
 * far smaller and readable at the cost of no longer being a complete view of
 * the file.
 */
public class FmtToXml {

	public static void main(String[] args) throws Exception {
		List<String> positional = new ArrayList<>();
		boolean brief = false;
		for (String arg : args) {
			if (arg.equals("--brief")) {
				brief = true;
			} else {
				positional.add(arg);
			}
		}
		if (positional.isEmpty()) {
			System.out.println("Usage:");
			System.out.println("  java -cp build info.phosco.forms.fmt.FmtToXml archivo.fmt [salida.xml] [--brief]");
			System.out.println("  java -cp build info.phosco.forms.fmt.FmtToXml carpetaFmt carpetaXml [--brief]");
			System.exit(1);
		}

		Path in = Paths.get(positional.get(0));
		if (Files.isDirectory(in)) {
			Path out = Paths.get(positional.size() > 1 ? positional.get(1) : "fmt-xml");
			convertFolder(in, out, brief);
		} else {
			Path out = Paths.get(positional.size() > 1 ? positional.get(1) : replaceExtension(in.toString()));
			Stats stats = convertFile(in, out, brief);
			System.out.println(stats.describe(in.getFileName().toString()));
			System.out.println("-> " + out);
		}
	}

	private static void convertFolder(Path inRoot, Path outRoot, boolean brief) throws IOException {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(inRoot)) {
			files = walk.filter(p -> p.toString().toLowerCase().endsWith(".fmt")).sorted(Comparator.naturalOrder())
					.collect(java.util.stream.Collectors.toList());
		}
		System.out.println("Encontrados " + files.size() + " archivo(s) .fmt en " + inRoot);

		Stats totals = new Stats();
		int failed = 0;
		for (Path file : files) {
			Path relative = inRoot.relativize(file);
			Path out = outRoot.resolve(replaceExtension(relative.toString()));
			Files.createDirectories(out.getParent() == null ? outRoot : out.getParent());
			try {
				Stats stats = convertFile(file, out, brief);
				totals.add(stats);
				System.out.println("  " + stats.describe(relative.toString()));
			} catch (Exception e) {
				failed++;
				System.out.println("  FALLO " + relative + " : " + e);
			}
		}

		System.out.println();
		System.out.println("Listo. Convertidos: " + (files.size() - failed) + " | Fallidos: " + failed);
		System.out.println("Totales -> " + totals.describe("(todos)"));
		if (totals.orphanProperties > 0 || totals.unrecognizedRecords > 0 || totals.fallbackDecodes > 0) {
			System.out.println("Revisa: propiedades huerfanas=" + totals.orphanProperties
					+ ", registros de tablas no reconocidas=" + totals.unrecognizedRecords
					+ ", fuentes decodificadas por respaldo=" + totals.fallbackDecodes);
		}
	}

	private static Stats convertFile(Path in, Path out, boolean brief) throws IOException {
		List<FmtRecord> records = FmtParser.parse(in.toString());
		FmtObjectGraph graph = FmtObjectGraph.build(records);

		// FileWriter would use the platform charset here and produce invalid
		// UTF-8 XML for the accented text these forms are full of.
		try (Writer w = new OutputStreamWriter(new FileOutputStream(out.toFile()), StandardCharsets.UTF_8);
				XmlWriter xml = new XmlWriter(w)) {
			new FmtXmlExporter(graph, !brief).export(in.getFileName().toString(), xml);
		}

		Stats stats = new Stats();
		stats.files = 1;
		stats.records = records.size();
		stats.objects = graph.objectCount();
		stats.orphanProperties = graph.orphanPropertyCount();
		stats.unrecognizedRecords = graph.unknownTableRecordCount();
		countSources(graph, stats);
		return stats;
	}

	private static void countSources(FmtObjectGraph graph, Stats stats) {
		for (FmtObject o : graph.allObjects()) {
			if (o.typ != FmtSchema.TYPE_TRIGGER && o.typ != FmtSchema.TYPE_PROGRAM_UNIT) {
				continue;
			}
			Object blob = o.property(FmtSchema.PROP_SOURCE_BLOB);
			if (!(blob instanceof byte[])) {
				continue;
			}
			if (o.typ == FmtSchema.TYPE_TRIGGER) {
				stats.triggers++;
			} else {
				stats.programUnits++;
			}
			if (FmtSourceDecoder.decodeExact(o.typ, (byte[]) blob) == null) {
				stats.fallbackDecodes++;
			}
		}
	}

	private static String replaceExtension(String path) {
		int dot = path.lastIndexOf('.');
		return (dot < 0 ? path : path.substring(0, dot)) + ".xml";
	}

	/** Per-file (or accumulated) conversion counters, reported so a partial extraction is visible. */
	private static class Stats {
		int files;
		long records;
		long objects;
		long triggers;
		long programUnits;
		long orphanProperties;
		long unrecognizedRecords;
		long fallbackDecodes;

		void add(Stats s) {
			files += s.files;
			records += s.records;
			objects += s.objects;
			triggers += s.triggers;
			programUnits += s.programUnits;
			orphanProperties += s.orphanProperties;
			unrecognizedRecords += s.unrecognizedRecords;
			fallbackDecodes += s.fallbackDecodes;
		}

		String describe(String label) {
			return String.format("%-46s registros=%-8d objetos=%-7d triggers=%-6d programUnits=%-5d huerfanas=%-4d respaldo=%d",
					label, records, objects, triggers, programUnits, orphanProperties, fallbackDecodes);
		}
	}

	private FmtToXml() {
	}
}
