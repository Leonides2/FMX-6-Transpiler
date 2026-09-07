package info.phosco.forms.fmb;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Raw byte access to an Oracle Forms 6 .fmb (design/source) file.
 * <p>
 * Unlike the .fmx runtime format (see {@code info.phosco.forms.translate.bytes}),
 * the .fmb stores most of its content as a flat, linear sequence of
 * length-prefixed byte blobs:
 * <pre>
 *   [4-byte big-endian length][that many bytes...]
 * </pre>
 * repeated back-to-back throughout the file. There is no separate TEXT/ATTRIBUTES
 * segment split and no tagged pointer scheme like in .fmx - the data is simply
 * inline, in file order. This class only knows how to read one such record at a
 * given position; see {@link FmbLogicScanner} for how records are chained
 * together into triggers and program units.
 */
public class FmbBuffer {

	private final byte[] data;

	public FmbBuffer(byte[] data) {
		this.data = data;
	}

	public static FmbBuffer load(String fileName) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(fileName, "r")) {
			byte[] data = new byte[(int) raf.length()];
			raf.readFully(data);
			return new FmbBuffer(data);
		}
	}

	public int length() {
		return data.length;
	}

	/**
	 * Reads a [4-byte length][bytes] record starting exactly at pos.
	 *
	 * @param pos    absolute position of the length field
	 * @param maxLen refuse to read a record whose declared length exceeds this
	 *               (guards against false positives / garbage length fields)
	 * @return the record's payload bytes, or null if there is no plausible
	 *         record at pos (out of bounds, zero/negative length, or too long)
	 */
	public byte[] tryReadRecord(int pos, int maxLen) {
		if (pos < 0 || pos + 4 > data.length) {
			return null;
		}
		long len = ((long) (data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
				| ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
		if (len <= 0 || len > maxLen) {
			return null;
		}
		if (pos + 4 + len > data.length) {
			return null;
		}
		byte[] res = new byte[(int) len];
		System.arraycopy(data, pos + 4, res, 0, (int) len);
		return res;
	}

	/** Position right after the record (length field + payload) starting at pos, given its payload. */
	public static int recordEnd(int pos, byte[] payload) {
		return pos + 4 + payload.length;
	}

	public static String asText(byte[] payload) {
		return new String(payload, StandardCharsets.ISO_8859_1);
	}

	/**
	 * Heuristic: is this payload "text-shaped" (mostly printable / ISO-8859-1
	 * accented / whitespace)? Used to reject false-positive length matches on
	 * binary data.
	 */
	public static boolean looksLikeText(byte[] b) {
		if (b.length == 0) {
			return false;
		}
		int printable = 0;
		for (byte x : b) {
			int v = x & 0xFF;
			if (v == 0x09 || v == 0x0A || v == 0x0D || (v >= 0x20 && v <= 0x7E) || (v >= 0xA0)) {
				printable++;
			}
		}
		return printable >= b.length * 0.85;
	}
}
