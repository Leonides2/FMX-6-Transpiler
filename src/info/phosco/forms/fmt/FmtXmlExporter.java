package info.phosco.forms.fmt;

import info.phosco.forms.xml.XmlWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dumps a parsed .fmt to XML, walking the <em>real</em> object tree built by
 * {@link FmtObjectGraph} (parent-child taken from {@code IDFOS_OOI}, not
 * guessed from name proximity like the .fmb-side
 * {@link info.phosco.forms.fmb.NameAnchorScanner} has to do).
 * <p>
 * What each object becomes:
 * <ul>
 * <li>the element name is its object kind ({@code Block}, {@code Item},
 * {@code Trigger}, ...) per {@link FmtSchema}, or {@code Type<n>} for kinds
 * still unidentified - never a made-up name;</li>
 * <li>properties whose code has a confirmed meaning become attributes
 * ({@code ItemType="PushButton"}, {@code WhereClause="..."});</li>
 * <li>the PL/SQL blob of a trigger or program unit becomes a
 * {@code <Source>} CDATA child, decoded by {@link FmtSourceDecoder};</li>
 * <li>an inheritance reference becomes {@code <SubclassedFrom module=".."
 * object=".."/>}, resolved through the {@code SubclassReference} ->
 * {@code ReferencePathSegment} chain;</li>
 * <li>every remaining property is still emitted, as a raw
 * {@code <Property code="N372" value="22"/>}, so nothing in the file is
 * silently dropped just because its meaning isn't known yet. Pass
 * {@code includeUnknownProperties=false} for a compact, readable dump.</li>
 * </ul>
 * The export closes with an {@code <Integrity>} element reporting orphan
 * properties, unrecognized tables and objects not reachable from the module
 * root - so a consumer can tell a complete extraction from a partial one
 * instead of trusting it blindly.
 */
public class FmtXmlExporter {

	/** The "property not set / inherited" sentinel the format uses in numeric and boolean columns. */
	public static final long UNSET_SENTINEL = -2147483647L;

	private final FmtObjectGraph graph;
	private final boolean includeUnknownProperties;
	private final Set<Long> emitted = new HashSet<>();

	public FmtXmlExporter(FmtObjectGraph graph, boolean includeUnknownProperties) {
		this.graph = graph;
		this.includeUnknownProperties = includeUnknownProperties;
	}

	public void export(String fileLabel, XmlWriter xml) throws IOException {
		xml.startElement("FormsModuleExport").attribute("file", fileLabel);

		if (graph.root() != null) {
			writeObject(graph.root(), xml);
		}

		List<FmtObject> unreachable = unreachableObjects();
		if (!unreachable.isEmpty()) {
			// Objects referenced only through O-properties (subclass links,
			// child-list holders) live outside the ownership tree; keep them so
			// the export stays a faithful, complete view of the file.
			xml.startElement("ReferencedObjects").attribute("count", unreachable.size());
			for (FmtObject o : unreachable) {
				if (!emitted.contains(o.oid)) { // a previous one may have pulled it in as a reference
					writeObject(o, xml);
				}
			}
			xml.endElement();
		}

		xml.startElement("Integrity");
		xml.attribute("objects", graph.objectCount());
		xml.attribute("orphanProperties", graph.orphanPropertyCount());
		xml.attribute("unrecognizedTableRecords", graph.unknownTableRecordCount());
		xml.attribute("objectsOutsideTree", unreachable.size());
		xml.endElement();

		xml.endElement(); // FormsModuleExport
	}

	private void writeObject(FmtObject obj, XmlWriter xml) throws IOException {
		emitted.add(obj.oid);

		xml.startElement(FmtSchema.typeName(obj.typ));
		xml.attribute("id", obj.oid);
		if (obj.name != null && !obj.name.isEmpty()) {
			xml.attribute("name", obj.name);
		}
		if (!FmtSchema.isTypeKnown(obj.typ)) {
			xml.attribute("rawType", obj.typ);
		}

		// Named scalar properties become attributes. Attributes must all be
		// written before any child element, so this pass comes first.
		String itemType = FmtSchema.itemTypeName(asLong(obj.property(FmtSchema.PROP_ITEM_TYPE)));
		if (itemType != null) {
			xml.attribute("ItemType", itemType);
		}
		for (FmtProperty p : obj.properties) {
			if (isHandledSeparately(obj, p) || isRepeated(obj, p)) {
				continue;
			}
			String name = FmtSchema.propertyName(p.code);
			if (name != null && !(p.value instanceof byte[])) {
				xml.attribute(name, formatValue(p.value));
			}
		}

		writeSource(obj, xml);
		writeSubclassOrigin(obj, xml);

		for (FmtProperty p : obj.properties) {
			if (isHandledSeparately(obj, p)) {
				continue;
			}
			String name = FmtSchema.propertyName(p.code);
			boolean alreadyAnAttribute = name != null && !(p.value instanceof byte[]) && !isRepeated(obj, p);
			if (alreadyAnAttribute || (name == null && !includeUnknownProperties)) {
				continue;
			}
			xml.startElement("Property").attribute("code", p.family + String.valueOf(p.code));
			xml.attribute("name", name);
			if (p.index >= 0) {
				xml.attribute("index", p.index);
			}
			xml.attribute("value", formatValue(p.value)).endElement();
		}

		for (FmtObject child : obj.children) {
			writeObject(child, xml);
		}
		xml.endElement();
	}

	/**
	 * True when this object carries the same property code more than once - a
	 * multi-valued property (the {@code F50*} tables index repeats with their
	 * {@code *I} field, e.g. the list of program units a unit references). Those
	 * can't collapse into a single XML attribute, so they're emitted as repeated
	 * {@code <Property>} children instead.
	 */
	private static boolean isRepeated(FmtObject obj, FmtProperty p) {
		int seen = 0;
		for (FmtProperty other : obj.properties) {
			if (other.code == p.code && ++seen > 1) {
				return true;
			}
		}
		return false;
	}

	/** Properties rendered by a dedicated element rather than as a plain attribute. */
	private boolean isHandledSeparately(FmtObject obj, FmtProperty p) {
		if (p.code == FmtSchema.PROP_SOURCE_BLOB
				|| p.code == FmtSchema.PROP_ITEM_TYPE
				|| p.code == FmtSchema.PROP_SUBCLASS_REF) {
			return true;
		}
		// The name is already the object directory's own IDFOS_NAM, emitted as
		// the "name" attribute; property 211 just repeats it.
		return p.code == FmtSchema.PROP_NAME && String.valueOf(p.value).equals(obj.name);
	}

	private void writeSource(FmtObject obj, XmlWriter xml) throws IOException {
		Object blob = obj.property(FmtSchema.PROP_SOURCE_BLOB);
		if (!(blob instanceof byte[])) {
			return;
		}
		byte[] bytes = (byte[]) blob;
		if (obj.typ != FmtSchema.TYPE_TRIGGER && obj.typ != FmtSchema.TYPE_PROGRAM_UNIT) {
			// Only those two kinds are known to carry PL/SQL here. Other kinds
			// (a menu item's binary payload, say) keep the blob recorded but must
			// not have its bytes passed off as source text.
			xml.startElement("Blob").attribute("code", "P" + FmtSchema.PROP_SOURCE_BLOB)
					.attribute("bytes", bytes.length).endElement();
			return;
		}
		String source = FmtSourceDecoder.decode(obj);
		xml.startElement("Source").attribute("blobBytes", bytes.length);
		if (FmtSourceDecoder.decodeExact(obj.typ, bytes) == null) {
			// The confirmed layout didn't match, so this text came from the
			// fallback. Flag it rather than passing it off as a clean decode.
			xml.attribute("decode", "fallback");
		}
		if (source != null) {
			xml.cdata(source);
		}
		xml.endElement();
	}

	/**
	 * Resolves {@code SubclassReference -> ReferencePathSegment*} into a
	 * readable origin. The segments spell out a qualified path, e.g.
	 * ("UP_BASE", FormModule) then ("OBJ_GENERAL", ObjectGroup) = "subclassed
	 * from object group OBJ_GENERAL of module UP_BASE".
	 */
	private void writeSubclassOrigin(FmtObject obj, XmlWriter xml) throws IOException {
		Long refId = asLong(obj.property(FmtSchema.PROP_SUBCLASS_REF));
		if (refId == null) {
			return;
		}
		FmtObject ref = graph.byOid(refId);
		if (ref == null) {
			return;
		}
		emitted.add(ref.oid);

		String module = null;
		String source = null;
		String sourceType = null;
		for (FmtObject seg : ref.children) {
			emitted.add(seg.oid);
			if (seg.typ != FmtSchema.TYPE_REF_PATH_SEGMENT) {
				continue;
			}
			Object segName = seg.property(FmtSchema.PROP_REF_SEGMENT_NAME);
			Long segType = asLong(seg.property(FmtSchema.PROP_REF_SEGMENT_TYPE));
			if (segType != null && segType == FmtSchema.TYPE_FORM_MODULE) {
				module = String.valueOf(segName);
			} else {
				source = segName == null ? null : String.valueOf(segName);
				sourceType = segType == null ? null : FmtSchema.typeName(segType);
			}
		}
		xml.startElement("SubclassedFrom");
		xml.attribute("module", module);
		xml.attribute("object", source);
		xml.attribute("objectType", sourceType);
		if (module == null && source == null) {
			// The object is flagged as inherited but this file doesn't record
			// where from (typically an object library the module only links to).
			// Say so, rather than emitting an empty element that reads like a bug.
			xml.attribute("sourceNotRecorded", "true");
		}
		xml.endElement();
	}

	/** Objects the module root never reaches - referenced only through O-properties. */
	private List<FmtObject> unreachableObjects() {
		List<FmtObject> res = new ArrayList<>();
		for (FmtObject o : graph.allObjects()) {
			if (!emitted.contains(o.oid)) {
				res.add(o);
			}
		}
		return res;
	}

	private static Long asLong(Object v) {
		return v instanceof Long ? (Long) v : null;
	}

	private static String formatValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof byte[]) {
			return "(blob " + ((byte[]) value).length + " bytes)";
		}
		if (value instanceof Long && (Long) value == UNSET_SENTINEL) {
			return "(unset)";
		}
		return String.valueOf(value);
	}
}
