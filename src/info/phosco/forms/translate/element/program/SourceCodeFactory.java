package info.phosco.forms.translate.element.program;

import info.phosco.forms.translate.bytes.AbstractFileStructure;
import info.phosco.forms.translate.bytes.Content;
import info.phosco.forms.translate.bytes.UnsignedByteBuffer;
import info.phosco.forms.translate.element.AbstractFactory;
import info.phosco.forms.translate.util.FileStructureTypeException;
import info.phosco.forms.translate.util.Log;

import java.util.logging.Logger;

public class SourceCodeFactory extends AbstractFactory {

	private final static Logger log = Log.getLogger(SourceCodeFactory.class);

	private static final int POS_SIZEOF = 0x14;

	private SourceCodeFactory() {
	}

	private static int getLength(Content content, int offset) throws FileStructureTypeException {

		int len = content.getByte(offset, POS_SIZEOF) << 24;
		len |= (content.getByte(offset, POS_SIZEOF + 1) << 16);
		len |= (content.getByte(offset, POS_SIZEOF + 2) << 8);
		len |= content.getByte(offset, POS_SIZEOF + 3);

		return len;
	}

	// Position (relative to offset) where the PL/SQL text is hypothesized to
	// start, right after the 4-byte length field at POS_SIZEOF. NOT yet
	// validated against a real .fmb (Forms Builder) - cross-check before
	// trusting the extracted text.
	private static final int POS_TEXT = POS_SIZEOF + 0x4;

	public static SourceCode instance(Content content, int offset) throws FileStructureTypeException {

		int length = getLength(content, offset);

		// TODO: for tests only
		UnsignedByteBuffer bf = UnsignedByteBuffer.wrap(content.getByteArray(offset, 0, length));
		AbstractFileStructure fs = new AbstractFileStructure(bf, 0, length) {
		};
		log.finest("\n" + fs.formatHex(true, true, false, 24));

		SourceCode res = new SourceCode(offset);
		res.setProperty(ProgramUnitAttributes.LENGTH, length);
		res.setProperty(ProgramUnitAttributes.TEXT, content.getString(offset, POS_TEXT, length));

		return res;
	}
}
