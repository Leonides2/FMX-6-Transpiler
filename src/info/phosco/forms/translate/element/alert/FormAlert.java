package info.phosco.forms.translate.element.alert;

import info.phosco.forms.translate.element.ElementType;
import info.phosco.forms.translate.element.FormElement;

import java.util.Properties;

public class FormAlert implements FormElement<AlertAttributes> {

	private final Properties props;

	FormAlert(int offset) {
		this.props = new Properties();
		setProperty(AlertAttributes.OFFSET, offset);
	}

	@Override
	public void setProperty(AlertAttributes key, Object value) {
		this.props.put(key.toString(), value);

	}

	@Override
	public Object getProperty(AlertAttributes key) {
		return this.props.get(key.toString());
	}

	@Override
	public String getName() {
		return (String) getProperty(AlertAttributes.NAME);
	}

	@Override
	public ElementType getType() {
		return ElementType.ALERT;
	}

	@Override
	public int getOffset() {
		return (Integer) getProperty(AlertAttributes.OFFSET);
	}

	@Override
	public String toString() {
		String out = "";
		out += "\nType                                       : " + getType();
		out += "\nOffset                                     : " + Integer.toHexString(getOffset());
		out += "\nName                                       : " + getName();
		out += "\nTitel                                      : " + getProperty(AlertAttributes.TITLE);
		out += "\nMeldung                                    : " + getProperty(AlertAttributes.MESSAGE);
		out += "\nWarnstil                                   : " + getProperty(AlertAttributes.STYLE);

		out += "\nBeschriftung Schaltfläche 1                : " + getProperty(AlertAttributes.BUTTON_1);
		out += "\nBeschriftung Schaltfläche 2                : " + getProperty(AlertAttributes.BUTTON_2);
		out += "\nBeschriftung Schaltfläche 3                : " + getProperty(AlertAttributes.BUTTON_3);
		out += "\nStandard-Warnschaltfläche                  : " + getProperty(AlertAttributes.DEFAULT_BUTTON);

		out += "\nRichtung                                   : " + getProperty(AlertAttributes.DIRECTION);
		return out;
	}

}
