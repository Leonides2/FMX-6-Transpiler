package info.phosco.forms.fmb;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovers the approximate module -&gt; block -&gt; item (-&gt; radio value) naming
 * tree from a .fmb, using a much cheaper heuristic than fully decoding the
 * record schema: every declared object name in a .fmb appears to be written
 * <b>twice, back-to-back</b> (block names, item names, radio button values,
 * alert names, trigger-owner labels - all of them). Filtering out short/binary
 * noise and keeping only doubled, identifier-shaped tokens recovers the whole
 * naming hierarchy in file order, since a block's items are always declared
 * immediately after the block's own name and before the next block.
 * <p>
 * This does NOT tell you what role each name plays (block vs item vs radio
 * value) - only their names and relative order. Cross-reference against
 * {@code info.phosco.forms.translate} (.fmx block names) or against
 * {@link FmbLogicScanner} (trigger owners, which already carry scope) to
 * classify them further.
 */
public class NameAnchorScanner {

	private static final int MIN_NAME_LENGTH = 4;
	private static final int MAX_NAME_LENGTH = 40;
	private static final int MAX_GAP_BETWEEN_COPIES = 40;

	private final FmbBuffer buf;

	public NameAnchorScanner(FmbBuffer buf) {
		this.buf = buf;
	}

	public List<NameAnchor> scan() {
		List<int[]> tokenRanges = tokenizePrintableRuns(); // {start, endExclusive}
		List<NameAnchor> res = new ArrayList<>();

		for (int i = 0; i + 1 < tokenRanges.size(); i++) {
			int[] a = tokenRanges.get(i);
			int[] b = tokenRanges.get(i + 1);
			if (b[0] - a[1] > MAX_GAP_BETWEEN_COPIES) {
				continue;
			}
			String sa = textOf(a);
			String sb = textOf(b);
			if (sa.equals(sb) && looksLikeIdentifier(sa)) {
				res.add(new NameAnchor(a[0], sa));
			}
		}
		return res;
	}

	private String textOf(int[] range) {
		StringBuilder sb = new StringBuilder(range[1] - range[0]);
		for (int i = range[0]; i < range[1]; i++) {
			sb.append((char) buf.byteAt(i));
		}
		return sb.toString();
	}

	private List<int[]> tokenizePrintableRuns() {
		List<int[]> res = new ArrayList<>();
		int tokenStart = -1;
		int len = buf.length();
		for (int i = 0; i < len; i++) {
			int b = buf.byteAt(i);
			boolean printable = b >= 0x20 && b <= 0x7E;
			if (printable) {
				if (tokenStart < 0) {
					tokenStart = i;
				}
			} else if (tokenStart >= 0) {
				if (i - tokenStart >= 2) {
					res.add(new int[] { tokenStart, i });
				}
				tokenStart = -1;
			}
		}
		return res;
	}

	private static boolean looksLikeIdentifier(String s) {
		if (s.length() < MIN_NAME_LENGTH || s.length() > MAX_NAME_LENGTH) {
			return false;
		}
		char first = s.charAt(0);
		if (!(Character.isLetter(first) || first == '_')) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '.')) {
				return false;
			}
		}
		return true;
	}
}
