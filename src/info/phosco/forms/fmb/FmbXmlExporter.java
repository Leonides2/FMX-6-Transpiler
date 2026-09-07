package info.phosco.forms.fmb;

import info.phosco.forms.xml.XmlWriter;

import java.io.IOException;
import java.util.List;

/**
 * Dumps whatever {@link FmbLogicScanner} can currently recover from a .fmb -
 * triggers (grouped by Form/Block/Item scope) and program units - to XML,
 * using the generic {@link XmlWriter}.
 * <p>
 * This only reflects what has actually been decoded so far; it is not a full
 * form export (no items-without-triggers, no data block/column structure -
 * that still comes from the .fmx side, see {@code info.phosco.forms.translate}).
 */
public class FmbXmlExporter {

	public static void export(String fileLabel, List<ExtractedTrigger> rawTriggers, List<ExtractedProgramUnit> rawUnits,
			List<NameAnchor> nameAnchors, XmlWriter xml) throws IOException {

		List<TriggerGroup> triggers = TriggerGroup.groupAll(rawTriggers);
		List<ProgramUnitGroup> units = ProgramUnitGroup.groupAll(rawUnits);

		xml.startElement("fmb").attribute("file", fileLabel);

		// Approximate module -> block -> item (-> radio value) naming tree, in
		// file order (see NameAnchorScanner). Not yet classified by role - a
		// block's items are simply the anchors that follow it, before the next
		// one; cross-reference against .fmx block names or trigger owners above
		// to tell them apart.
		xml.startElement("name_anchors").attribute("count", nameAnchors.size());
		for (NameAnchor n : nameAnchors) {
			xml.startElement("name").attribute("offset", "0x" + Integer.toHexString(n.offset)).attribute("value", n.name).endElement();
		}
		xml.endElement(); // name_anchors

		xml.startElement("triggers").attribute("count", triggers.size());
		for (TriggerGroup t : triggers) {
			xml.startElement("trigger");
			xml.attribute("name", t.name);
			xml.attribute("scope", t.scope.name());
			xml.attribute("block", t.blockName);
			xml.attribute("item", t.itemName);
			if (t.hasConflict()) {
				xml.attribute("conflict", "true");
				int i = 1;
				for (String source : t.distinctSources) {
					xml.startElement("source").attribute("variant", i++);
					xml.cdata(source);
					xml.endElement();
				}
			} else {
				xml.elementWithCdata("source", t.distinctSources.get(0));
			}
			xml.endElement(); // trigger
		}
		xml.endElement(); // triggers

		xml.startElement("program_units").attribute("count", units.size());
		for (ProgramUnitGroup u : units) {
			xml.startElement("program_unit");
			xml.attribute("name", u.name);
			if (u.hasConflict()) {
				xml.attribute("conflict", "true");
				int i = 1;
				for (String source : u.distinctSources) {
					xml.startElement("source").attribute("variant", i++);
					xml.cdata(source);
					xml.endElement();
				}
			} else {
				xml.elementWithCdata("source", u.distinctSources.get(0));
			}
			xml.endElement(); // program_unit
		}
		xml.endElement(); // program_units

		xml.endElement(); // fmb
	}
}
