package info.phosco.forms.fmt;

import info.phosco.forms.fmb.FmbBuffer;

/**
 * Recovers the PL/SQL source text out of the {@code P464} blob that a trigger
 * ({@code IDFOS_TYP=68}) or program unit ({@code IDFOS_TYP=58}) carries in a
 * .fmt file.
 * <p>
 * That blob is the same "compiled unit + attached source" container the .fmb
 * stores, so its inner layout is the familiar {@code [4-byte big-endian
 * length][payload]} record chain that {@link FmbBuffer} already knows how to
 * read. The difference - and the reason this class exists instead of reusing
 * {@link info.phosco.forms.fmb.FmbLogicScanner} - is that here the blob is
 * already known to belong to <em>one specific object</em> whose name and owner
 * come from the object tree. So there is no need to scan a whole file for
 * candidate records, infer scope from a trailing {@code "NAME (BLOCK.ITEM)"}
 * label, or deduplicate repeated copies: we just have to find this unit's
 * source inside its own blob. That makes the extraction both simpler and much
 * harder to get wrong.
 * <p>
 * Two layouts were confirmed empirically over the whole corpus:
 * <ul>
 * <li><b>Trigger</b>: a record opening the anonymous block Forms wraps every
 * trigger in ({@code "BEGIN\n"}, or {@code "BEGIN\n:BLOCK.ITEM:=("} for a
 * FORMULA-CALCULATION), immediately followed by the record holding the
 * trigger's real text (which may itself start with {@code DECLARE}), then the
 * block's closing record and the {@code "NAME (OWNER)"} label. The useful
 * source is the middle record.</li>
 * <li><b>Program unit</b>: the first text record that opens with
 * {@code PROCEDURE}/{@code FUNCTION}/{@code PACKAGE} - ignoring any leading
 * comment banner, which real sources very often have - is the whole unit.</li>
 * </ul>
 * When neither shape matches, {@link #decode} falls back to the longest
 * text-shaped record in the blob, which is in practice the source; callers
 * that need to know whether the clean shape matched can use
 * {@link #decodeExact}.
 */
public class FmtSourceDecoder {

	private static final int MAX_RECORD_LEN = 4_000_000;
	private static final int MAX_MARKER_LEN = 256; // a FORMULA-CALCULATION opener carries a qualified item name

	/** The PL/SQL text of a trigger object, or null if this blob holds no recoverable text. */
	public static String decodeTrigger(byte[] blob) {
		String exact = triggerBody(blob);
		return exact != null ? exact : longestTextRecord(blob);
	}

	/** The PL/SQL text of a program unit object, or null if this blob holds no recoverable text. */
	public static String decodeProgramUnit(byte[] blob) {
		String exact = programUnitBody(blob);
		return exact != null ? exact : longestTextRecord(blob);
	}

	/**
	 * Same as {@link #decodeTrigger}/{@link #decodeProgramUnit} for the given
	 * object type, but returns null rather than falling back to a heuristic -
	 * useful to measure how often the confirmed layout actually holds.
	 */
	public static String decodeExact(long objectType, byte[] blob) {
		if (objectType == FmtSchema.TYPE_TRIGGER) {
			return triggerBody(blob);
		}
		if (objectType == FmtSchema.TYPE_PROGRAM_UNIT) {
			return programUnitBody(blob);
		}
		return null;
	}

	/** Convenience: decode whichever shape matches this object's type, or null if it carries no source blob. */
	public static String decode(FmtObject obj) {
		Object blob = obj.property(FmtSchema.PROP_SOURCE_BLOB);
		if (!(blob instanceof byte[])) {
			return null;
		}
		byte[] bytes = (byte[]) blob;
		if (obj.typ == FmtSchema.TYPE_TRIGGER) {
			return decodeTrigger(bytes);
		}
		if (obj.typ == FmtSchema.TYPE_PROGRAM_UNIT) {
			return decodeProgramUnit(bytes);
		}
		return longestTextRecord(bytes);
	}

	/**
	 * The record right after the one opening the anonymous block Forms wraps
	 * every trigger in. That opener is {@code "BEGIN\n"} on its own for an
	 * ordinary trigger, and {@code "BEGIN\n:BLOCK.ITEM:=("} for a
	 * FORMULA-CALCULATION trigger, whose "source" is a bare expression rather
	 * than a statement - hence matching on the prefix rather than the whole
	 * record. Scanning by ascending offset always reaches the wrapper before a
	 * body that happens to start with BEGIN itself.
	 */
	private static String triggerBody(byte[] blob) {
		FmbBuffer buf = new FmbBuffer(blob);
		for (int i = 0; i < blob.length - 8; i++) {
			byte[] marker = buf.tryReadRecord(i, MAX_MARKER_LEN);
			if (marker == null || !FmbBuffer.asText(marker).startsWith("BEGIN\n")) {
				continue;
			}
			byte[] body = buf.tryReadRecord(FmbBuffer.recordEnd(i, marker), MAX_RECORD_LEN);
			if (body != null && FmbBuffer.looksLikeText(body)) {
				return FmbBuffer.asText(body);
			}
		}
		return null;
	}

	/** The first text record that opens with a PL/SQL unit keyword. */
	private static String programUnitBody(byte[] blob) {
		FmbBuffer buf = new FmbBuffer(blob);
		for (int i = 0; i < blob.length - 8; i++) {
			byte[] rec = buf.tryReadRecord(i, MAX_RECORD_LEN);
			if (rec == null || !FmbBuffer.looksLikeText(rec)) {
				continue;
			}
			String text = FmbBuffer.asText(rec);
			if (startsWithUnitKeyword(text)) {
				return text;
			}
		}
		return null;
	}

	/**
	 * Does this text open a PL/SQL program unit? Real sources very often lead
	 * with comments - a banner of {@code --} lines, or a
	 * {@code /* Formatted on ... *}{@code /} header left by a formatting tool -
	 * so leading whitespace and comments are skipped before looking for the
	 * keyword.
	 */
	private static boolean startsWithUnitKeyword(String text) {
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (text.startsWith("--", i)) {
				int nl = text.indexOf('\n', i);
				if (nl < 0) {
					return false;
				}
				i = nl + 1;
			} else if (text.startsWith("/*", i)) {
				int close = text.indexOf("*/", i + 2);
				if (close < 0) {
					return false;
				}
				i = close + 2;
			} else {
				break;
			}
		}
		String head = text.substring(i, Math.min(text.length(), i + 10)).toUpperCase();
		return head.startsWith("PROCEDURE") || head.startsWith("FUNCTION") || head.startsWith("PACKAGE");
	}

	/** Fallback for blobs whose confirmed shape doesn't match: the biggest text-shaped record present. */
	private static String longestTextRecord(byte[] blob) {
		FmbBuffer buf = new FmbBuffer(blob);
		String best = null;
		for (int i = 0; i < blob.length - 8; i++) {
			byte[] rec = buf.tryReadRecord(i, MAX_RECORD_LEN);
			if (rec == null || rec.length < 8 || !FmbBuffer.looksLikeText(rec)) {
				continue;
			}
			if (best == null || rec.length > best.length()) {
				best = FmbBuffer.asText(rec);
			}
		}
		return best;
	}

	private FmtSourceDecoder() {
	}
}
