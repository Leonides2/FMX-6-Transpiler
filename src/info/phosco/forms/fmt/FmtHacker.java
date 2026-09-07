package info.phosco.forms.fmt;

import java.util.List;

/** Command-line entry point: parses a .fmt (ROS Script text export) and prints its real object tree. */
public class FmtHacker {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Usage: java -cp build info.phosco.forms.fmt.FmtHacker archivo.fmt [maxDepth]");
			System.exit(1);
		}

		int maxDepth = args.length >= 2 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

		long t0 = System.currentTimeMillis();
		List<FmtRecord> records = FmtParser.parse(args[0]);
		long t1 = System.currentTimeMillis();
		FmtObjectGraph graph = FmtObjectGraph.build(records);
		long t2 = System.currentTimeMillis();

		System.out.println("Parsed " + records.size() + " records in " + (t1 - t0) + "ms; built " + graph.objectCount()
				+ " objects in " + (t2 - t1) + "ms.");
		System.out.println("Orphan properties (owner id not found): " + graph.orphanPropertyCount());
		System.out.println("Records from unrecognized tables: " + graph.unknownTableRecordCount());
		System.out.println();

		printTree(graph.root(), 0, maxDepth);
	}

	private static void printTree(FmtObject obj, int depth, int maxDepth) {
		if (obj == null || depth > maxDepth) {
			return;
		}
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			indent.append("  ");
		}
		System.out.println(indent + obj.toString());
		for (FmtObject child : obj.children) {
			printTree(child, depth + 1, maxDepth);
		}
	}
}
