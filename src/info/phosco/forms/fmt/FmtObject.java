package info.phosco.forms.fmt;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of the `FRM50_IDFO` object directory: an object of the form's
 * repository (the form module itself, a block, an item, a trigger, a canvas,
 * ...), identified by {@code oid}, owned by the object with id {@code ooi}
 * (0/absent for the root), of kind {@code typ} (a numeric code - see
 * docs/fmb-format-notes.md for the ones identified so far, e.g. 7 = data
 * block), together with whatever properties were attached to it.
 */
public class FmtObject {

	public final long oid;
	public final long poi;
	public final String name;
	public final long typ;
	public final long ooi;
	public final long opn;
	public final long cnt;
	public final List<FmtProperty> properties = new ArrayList<>();
	public final List<FmtObject> children = new ArrayList<>();

	public FmtObject(long oid, long poi, String name, long typ, long ooi, long opn, long cnt) {
		this.oid = oid;
		this.poi = poi;
		this.name = name;
		this.typ = typ;
		this.ooi = ooi;
		this.opn = opn;
		this.cnt = cnt;
	}

	public Object property(long code) {
		for (FmtProperty p : properties) {
			if (p.code == code) {
				return p.value;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return "#" + oid + " \"" + name + "\" typ=" + typ + " ooi=" + ooi + " (" + properties.size() + " props, " + children.size() + " children)";
	}
}
