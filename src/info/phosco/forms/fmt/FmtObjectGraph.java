package info.phosco.forms.fmt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the flat {@link FmtRecord} list from {@link FmtParser} into a real
 * object tree: the `FRM50_IDFO` table is the object directory (id, name,
 * kind, owner id), and the generic `F50T`/`F50N`/`F50B`/`F50P`/`F50S`/`F50O`
 * tables attach typed properties to whichever object their "owner id" field
 * points at - see {@link FmtObject}/{@link FmtProperty}.
 */
public class FmtObjectGraph {

	private final Map<Long, FmtObject> byOid = new LinkedHashMap<>();
	private FmtObject root;
	private int orphanProperties = 0;
	private int unknownTables = 0;

	public static FmtObjectGraph build(List<FmtRecord> records) {
		FmtObjectGraph graph = new FmtObjectGraph();
		for (FmtRecord rec : records) {
			graph.apply(rec);
		}
		return graph;
	}

	private void apply(FmtRecord rec) {
		if (rec.table.equals("FRM50_IDFO")) {
			applyDirectoryEntry(rec);
			return;
		}
		Character family = propertyFamilyOf(rec.table);
		if (family == null) {
			unknownTables++;
			return;
		}
		applyProperty(family, rec);
	}

	private void applyDirectoryEntry(FmtRecord rec) {
		long oid = orZero(rec.getLong("IDFOS_OID"));
		long poi = orZero(rec.getLong("IDFOS_POI"));
		String name = rec.getString("IDFOS_NAM");
		long typ = orZero(rec.getLong("IDFOS_TYP"));
		long ooi = orZero(rec.getLong("IDFOS_OOI"));
		long opn = orZero(rec.getLong("IDFOS_OPN"));
		long cnt = orZero(rec.getLong("IDFOS_CNT"));

		FmtObject obj = new FmtObject(oid, poi, name, typ, ooi, opn, cnt);
		byOid.put(oid, obj);

		if (root == null) {
			root = obj; // the first directory entry is the form/menu/library module itself
		}
		FmtObject parent = byOid.get(ooi);
		if (parent != null && parent != obj) {
			parent.children.add(obj);
		}
	}

	/** F50T -> 'T', F50N -> 'N', ... null if this isn't one of the known property-table shapes. */
	private static Character propertyFamilyOf(String table) {
		if (table.length() == 4 && table.startsWith("F50")) {
			char c = table.charAt(3);
			if ("TNBPSO".indexOf(c) >= 0) {
				return c;
			}
		}
		return null;
	}

	private void applyProperty(char family, FmtRecord rec) {
		String f = String.valueOf(family);
		Long ownerId = rec.getLong(f + "P");
		Long index = rec.getLong(f + "I");
		Long code = rec.getLong(f + "N");
		String valueField = family == 'O' ? f + "O" : f + "V";
		Object value = rec.fields.get(valueField);

		if (ownerId == null || code == null) {
			unknownTables++;
			return;
		}
		FmtObject owner = byOid.get(ownerId);
		if (owner == null) {
			orphanProperties++;
			return;
		}
		owner.properties.add(new FmtProperty(family, index == null ? -1 : index, code, value));
	}

	private static long orZero(Long l) {
		return l == null ? 0 : l;
	}

	public FmtObject root() {
		return root;
	}

	public FmtObject byOid(long oid) {
		return byOid.get(oid);
	}

	public int objectCount() {
		return byOid.size();
	}

	public int orphanPropertyCount() {
		return orphanProperties;
	}

	public int unknownTableRecordCount() {
		return unknownTables;
	}
}
