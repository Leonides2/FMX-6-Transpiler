package info.phosco.forms.translate.element.application;

import info.phosco.forms.translate.element.ElementType;
import info.phosco.forms.translate.element.FormElement;

import java.util.Properties;

public class FormModule implements FormElement<ModuleAttributes> {

	private final Properties props;

	FormModule(int offset) {
		this.props = new Properties();
		setProperty(ModuleAttributes.OFFSET, offset);
	}

	@Override
	public void setProperty(ModuleAttributes key, Object value) {
		this.props.put(key.toString(), value);

	}

	@Override
	public Object getProperty(ModuleAttributes key) {
		return this.props.get(key.toString());
	}

	@Override
	public String getName() {
		return (String) getProperty(ModuleAttributes.NAME);
	}

	@Override
	public ElementType getType() {
		return ElementType.MODULE;
	}

	@Override
	public int getOffset() {
		return (Integer) getProperty(ModuleAttributes.OFFSET);
	}

	public String getCharacterSet() {
		return (String) getProperty(ModuleAttributes.CHARACTERSET);
	}

	@Override
	public String toString() {
		String out = "";
		out += "\nType                                       : " + getType();
		out += "\nOffset                                     : " + Integer.toHexString(getOffset());
		out += "\nCharacterset                               : " + getCharacterSet();
		out += "\nName                                       : " + getName();
		out += "\nTitel                                      : " + getProperty(ModuleAttributes.TITLE);
		out += "\nMenüquelle                                 : " + getProperty(ModuleAttributes.MENU_SOURCE);
		out += "\nMenümodule                                 : " + getProperty(ModuleAttributes.MENU_MODULE);

		out += "\nDefer Required-Durchsetzung                : " + getProperty(ModuleAttributes.DEFER_REQUIRED);

		out += "\nMausnavigation                             : " + getProperty(ModuleAttributes.MOUSE_NAVIGATION_LIMIT);

		out += "\nVisuelle Attributgruppe Aktueller Datensatz: " + Integer.toHexString((Integer) getProperty(ModuleAttributes.VISUAL_GROUP_RECORD));

		out += "\nValidierungseinheit                        : " + getProperty(ModuleAttributes.VALIDATION_UNIT);
		out += "\nInteraktionsmodus                          : " + getProperty(ModuleAttributes.INTERCATION_MODE);
		out += "\nMaximale Abfragezeit                       : " + getProperty(ModuleAttributes.MAX_QUERY_TIME);
		out += "\nMaximal abgerufene Datensätze              : " + getProperty(ModuleAttributes.MAX_RECORDS_FETCHED);
		out += "\nIsolationsmodus                            : " + getProperty(ModuleAttributes.ISOLATION_MODE);

		out += "\nKoordinatensystem                          : " + getProperty(ModuleAttributes.COORD_SYSTEM);
		out += "\nKoordinatensystem-Einheit                  : " + getProperty(ModuleAttributes.COORD_SYSTEM_UNIT);
		out += "\n3D-Steuerelemente verwenden                : " + getProperty(ModuleAttributes.USE_3D_CONTROLS);
		out += "\nZeicheneinheit Breite                      : " + getProperty(ModuleAttributes.UNIT_WIDTH);
		out += "\nZeicheneinheit Höhe                        : " + getProperty(ModuleAttributes.UNIT_HEIGHT);

		out += "\nRichtung                                   : " + getProperty(ModuleAttributes.DIRECTION);
		out += "\nLaufzeit-Kompatibilitätsmodus              : " + getProperty(ModuleAttributes.RUNTIME_COMPATIBILITY);
		return out;

	}

}
