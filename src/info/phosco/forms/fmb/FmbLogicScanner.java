package info.phosco.forms.fmb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers triggers and program units (full PL/SQL source, verbatim) from a
 * .fmb by scanning its length-prefixed record stream, rather than decoding
 * the full binary schema.
 * <p>
 * Two record shapes were identified empirically (see the project plan notes)
 * and validated byte-for-byte against a real form's known trigger text:
 * <ul>
 * <li><b>Trigger</b>: {@code [len]"BEGIN\n" [len]<body> [len]"END;"/"\nEND;" [len]"NAME (OWNER)"}</li>
 * <li><b>Program unit</b>: {@code [len]NAME [padding] [len]"PROCEDURE ...END;"/"FUNCTION ...END;"}</li>
 * </ul>
 * Both shapes can appear more than once for the same object (the .fmb seems
 * to keep more than one internal copy); callers that want a deduplicated view
 * should group by name/owner themselves - see {@code toString} usage in
 * {@link info.phosco.forms.fmb.FmbHacker} for a simple example.
 */
public class FmbLogicScanner {

	private static final int MAX_BODY_LEN = 500_000;
	private static final int MAX_LABEL_LEN = 512;
	private static final int MAX_NAME_GAP = 40;

	private static final Pattern LABEL = Pattern.compile("^(.+?) (\\(.+\\))$");

	private final FmbBuffer buf;

	public FmbLogicScanner(FmbBuffer buf) {
		this.buf = buf;
	}

	public List<ExtractedTrigger> scanTriggers() {
		List<ExtractedTrigger> res = new ArrayList<>();
		int len = buf.length();
		int i = 0;
		while (i < len - 8) {
			byte[] beginRec = buf.tryReadRecord(i, 32);
			if (beginRec != null && FmbBuffer.asText(beginRec).equals("BEGIN\n")) {
				int p = FmbBuffer.recordEnd(i, beginRec);
				byte[] bodyRec = buf.tryReadRecord(p, MAX_BODY_LEN);
				if (bodyRec != null && FmbBuffer.looksLikeText(bodyRec)) {
					int p2 = FmbBuffer.recordEnd(p, bodyRec);
					byte[] endRec = buf.tryReadRecord(p2, 32);
					if (endRec != null) {
						String endStr = FmbBuffer.asText(endRec);
						if (endStr.equals("\nEND;") || endStr.equals("END;")) {
							int p3 = FmbBuffer.recordEnd(p2, endRec);
							byte[] labelRec = buf.tryReadRecord(p3, MAX_LABEL_LEN);
							if (labelRec != null && FmbBuffer.looksLikeText(labelRec)) {
								String label = FmbBuffer.asText(labelRec);
								Matcher m = LABEL.matcher(label);
								if (m.matches()) {
									String source = FmbBuffer.asText(bodyRec);
									ExtractedTrigger t = ExtractedTrigger.parse(m.group(1), m.group(2), source, p);
									if (t != null) {
										res.add(t);
										i = FmbBuffer.recordEnd(p3, labelRec);
										continue;
									}
								}
							}
						}
					}
				}
			}
			i++;
		}
		return res;
	}

	public List<ExtractedProgramUnit> scanProgramUnits() {
		List<ExtractedProgramUnit> res = new ArrayList<>();
		int len = buf.length();
		int i = 0;
		while (i < len - 8) {
			byte[] bodyRec = buf.tryReadRecord(i, MAX_BODY_LEN);
			if (bodyRec != null && FmbBuffer.looksLikeText(bodyRec)) {
				String body = FmbBuffer.asText(bodyRec).stripLeading();
				String upper = body.length() > 10 ? body.substring(0, 10).toUpperCase() : body.toUpperCase();
				if (upper.startsWith("PROCEDURE") || upper.startsWith("FUNCTION")) {
					String name = findPrecedingName(i);
					res.add(new ExtractedProgramUnit(name, FmbBuffer.asText(bodyRec), i));
					i = FmbBuffer.recordEnd(i, bodyRec);
					continue;
				}
			}
			i++;
		}
		return res;
	}

	/** Looks a few bytes back from a program-unit body for its preceding [len]NAME record. */
	private String findPrecedingName(int bodyStart) {
		String best = null;
		for (int j = Math.max(0, bodyStart - (MAX_NAME_GAP + 132)); j < bodyStart - 4; j++) {
			byte[] rec = buf.tryReadRecord(j, 128);
			if (rec == null || !FmbBuffer.looksLikeText(rec)) {
				continue;
			}
			int end = FmbBuffer.recordEnd(j, rec);
			if (end <= bodyStart && end >= bodyStart - MAX_NAME_GAP) {
				String candidate = FmbBuffer.asText(rec);
				if (candidate.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#')) {
					best = candidate; // keep the closest match to bodyStart
				}
			}
		}
		return best == null ? "???" : best;
	}
}
