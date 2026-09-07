package info.phosco.forms.xml;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small, dependency-free XML writer. Not tied to any object model - it just
 * knows how to emit well-formed, indented XML, so it can be reused by any
 * exporter in this project (the .fmb logic dump today, a .fmx structure dump
 * later, etc.).
 * <p>
 * Typical usage:
 * <pre>
 * try (XmlWriter xml = new XmlWriter(new FileWriter("out.xml"))) {
 *     xml.startElement("form").attribute("name", name);
 *     xml.startElement("trigger").attribute("name", t.name);
 *     xml.elementWithCdata("source", t.source);
 *     xml.endElement(); // trigger
 *     xml.endElement(); // form
 * }
 * </pre>
 * Elements must be closed in the order they were opened; {@link #attribute}
 * may only be called right after {@link #startElement}, before any child
 * element or text is written (this mirrors how real XML attributes work).
 */
public class XmlWriter implements AutoCloseable {

	private final Writer out;
	private final Deque<String> openTags = new ArrayDeque<>();
	private boolean startTagOpen = false;
	private boolean hasChildOrText = false;
	/**
	 * Whether the last thing written into the current element was text/CDATA
	 * rather than a child element. Indentation must not be inserted before the
	 * closing tag in that case: it would land *inside* the value and silently
	 * append whitespace to extracted PL/SQL source.
	 */
	private boolean lastWasText = false;

	public XmlWriter(Writer out) throws IOException {
		this.out = out;
		out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	}

	public XmlWriter startElement(String name) throws IOException {
		closeStartTagIfNeeded();
		newlineAndIndent();
		out.write('<');
		out.write(name);
		openTags.push(name);
		startTagOpen = true;
		hasChildOrText = false;
		lastWasText = false;
		return this;
	}

	public XmlWriter attribute(String name, Object value) throws IOException {
		if (!startTagOpen) {
			throw new IllegalStateException("attribute(" + name + ") must come right after startElement, before any child/text");
		}
		if (value == null) {
			return this;
		}
		out.write(' ');
		out.write(name);
		out.write("=\"");
		out.write(escapeAttribute(String.valueOf(value)));
		out.write('"');
		return this;
	}

	/** Writes escaped text content (use {@link #cdata} instead for PL/SQL source or other free-form text). */
	public XmlWriter text(String value) throws IOException {
		closeStartTagIfNeeded();
		out.write(escapeText(stripInvalidXmlChars(value)));
		hasChildOrText = true;
		lastWasText = true;
		return this;
	}

	/** Writes a CDATA section, splitting on any embedded "]]&gt;" so the section stays well-formed. */
	public XmlWriter cdata(String value) throws IOException {
		closeStartTagIfNeeded();
		out.write("<![CDATA[");
		out.write(stripInvalidXmlChars(value).replace("]]>", "]]]]><![CDATA[>"));
		out.write("]]>");
		hasChildOrText = true;
		lastWasText = true;
		return this;
	}

	/**
	 * XML 1.0 forbids most control characters (NUL included) even inside
	 * CDATA. Source recovered from a heuristic byte scan can occasionally
	 * carry a stray one (padding/garbage caught alongside real text) - strip
	 * those rather than emitting XML that no parser can read back.
	 */
	private static String stripInvalidXmlChars(String s) {
		StringBuilder sb = null; // only allocate if we actually need to change something
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean valid = c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD);
			if (!valid && sb == null) {
				sb = new StringBuilder(s.substring(0, i));
			}
			if (sb != null && valid) {
				sb.append(c);
			}
		}
		return sb == null ? s : sb.toString();
	}

	/** Convenience: `<name attr="...">` + CDATA body + `</name>`, all in one call. */
	public XmlWriter elementWithCdata(String name, String value) throws IOException {
		startElement(name);
		cdata(value);
		endElement();
		return this;
	}

	public XmlWriter endElement() throws IOException {
		String name = openTags.pop();
		if (startTagOpen) {
			// no children/text were ever written - self-close
			out.write("/>");
			startTagOpen = false;
		} else {
			if (hasChildOrText && !lastWasText) {
				newlineAndIndent();
			}
			out.write("</");
			out.write(name);
			out.write('>');
		}
		hasChildOrText = true; // this element is itself a "child" from the parent's point of view
		lastWasText = false;
		return this;
	}

	private void closeStartTagIfNeeded() throws IOException {
		if (startTagOpen) {
			out.write('>');
			startTagOpen = false;
		}
	}

	private void newlineAndIndent() throws IOException {
		out.write('\n');
		for (int i = 0; i < openTags.size(); i++) {
			out.write("  ");
		}
	}

	private static String escapeAttribute(String s) {
		return escapeText(s).replace("\"", "&quot;").replace("\n", "&#10;").replace("\t", "&#9;");
	}

	private static String escapeText(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	@Override
	public void close() throws IOException {
		while (!openTags.isEmpty()) {
			endElement();
		}
		out.write('\n');
		out.close();
	}
}
