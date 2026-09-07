package info.phosco.forms.fmt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The vocabulary of the .fmt object model: what each {@code IDFOS_TYP} object
 * kind is, and what the numeric property codes of the generic {@code F50*}
 * tables mean.
 * <p>
 * Everything in here was derived empirically from a corpus of 176 real forms
 * (~1.7M records, ~187k objects) - see {@code docs/fmt-format-notes.md} for
 * the method and the evidence behind each mapping. Only codes with solid
 * evidence are named; the rest deliberately stay unnamed so the exporter emits
 * them as raw {@code <Property code="N372">} entries instead of inventing a
 * meaning. Property codes are <em>global</em>: code 211 is "Name" on a block,
 * an item, a trigger, anything - so one confirmed code pays off everywhere.
 */
public class FmtSchema {

	// ---- object kinds (IDFOS_TYP) -------------------------------------------------

	public static final long TYPE_ALERT = 3;
	public static final long TYPE_ATTACHED_LIBRARY = 5;
	public static final long TYPE_DATA_BLOCK = 7;
	public static final long TYPE_GRAPHIC = 9;
	public static final long TYPE_CANVAS = 11;
	public static final long TYPE_LOV_COLUMN = 13;
	public static final long TYPE_RECORD_GROUP_COLUMN = 15;
	public static final long TYPE_EDITOR = 20;
	public static final long TYPE_FORM_MODULE = 22;
	public static final long TYPE_PARAMETER = 24;
	public static final long TYPE_QUERY_COLUMN = 26;
	public static final long TYPE_ITEM = 30;
	public static final long TYPE_LIST_ELEMENT = 34;
	public static final long TYPE_LOV = 36;
	public static final long TYPE_POPUP_MENU = 40;
	public static final long TYPE_MENU_ITEM = 42;
	public static final long TYPE_OBJECT_GROUP = 52;
	public static final long TYPE_OBJECT_GROUP_CHILD = 54;
	public static final long TYPE_PROGRAM_UNIT = 58;
	public static final long TYPE_PROPERTY_CLASS = 60;
	public static final long TYPE_RADIO_BUTTON = 62;
	public static final long TYPE_RECORD_GROUP = 64;
	public static final long TYPE_RELATION = 66;
	public static final long TYPE_TRIGGER = 68;
	public static final long TYPE_CHILD_LIST = 69;
	public static final long TYPE_VISUAL_ATTRIBUTE = 74;
	public static final long TYPE_CANVAS_VIEW = 76;
	public static final long TYPE_WINDOW = 78;
	public static final long TYPE_POINT = 82;
	public static final long TYPE_COMPOUND_TEXT = 84;
	public static final long TYPE_TEXT_STRING = 86;
	public static final long TYPE_TAB_PAGE = 88;
	public static final long TYPE_SUBCLASS_REF = 90;
	public static final long TYPE_REF_PATH_SEGMENT = 94;
	public static final long TYPE_BLOCK_COLUMN = 100;
	public static final long TYPE_STATIC_RG_VALUE = 123;

	private static final Map<Long, String> TYPE_NAMES = new LinkedHashMap<>();
	static {
		TYPE_NAMES.put(TYPE_ALERT, "Alert");
		TYPE_NAMES.put(TYPE_ATTACHED_LIBRARY, "AttachedLibrary");
		TYPE_NAMES.put(TYPE_DATA_BLOCK, "Block");
		TYPE_NAMES.put(TYPE_GRAPHIC, "Graphic");
		TYPE_NAMES.put(TYPE_CANVAS, "Canvas");
		TYPE_NAMES.put(TYPE_LOV_COLUMN, "LovColumnMapping");
		TYPE_NAMES.put(TYPE_RECORD_GROUP_COLUMN, "RecordGroupColumn");
		TYPE_NAMES.put(TYPE_EDITOR, "Editor");
		TYPE_NAMES.put(TYPE_FORM_MODULE, "FormModule");
		TYPE_NAMES.put(TYPE_PARAMETER, "Parameter");
		TYPE_NAMES.put(TYPE_QUERY_COLUMN, "QueryDataSourceColumn");
		TYPE_NAMES.put(TYPE_ITEM, "Item");
		TYPE_NAMES.put(TYPE_LIST_ELEMENT, "ListElement");
		TYPE_NAMES.put(TYPE_LOV, "LOV");
		TYPE_NAMES.put(TYPE_POPUP_MENU, "PopupMenu");
		TYPE_NAMES.put(TYPE_MENU_ITEM, "MenuItem");
		TYPE_NAMES.put(TYPE_OBJECT_GROUP, "ObjectGroup");
		TYPE_NAMES.put(TYPE_OBJECT_GROUP_CHILD, "ObjectGroupChild");
		TYPE_NAMES.put(TYPE_PROGRAM_UNIT, "ProgramUnit");
		TYPE_NAMES.put(TYPE_PROPERTY_CLASS, "PropertyClass");
		TYPE_NAMES.put(TYPE_RADIO_BUTTON, "RadioButton");
		TYPE_NAMES.put(TYPE_RECORD_GROUP, "RecordGroup");
		TYPE_NAMES.put(TYPE_RELATION, "Relation");
		TYPE_NAMES.put(TYPE_TRIGGER, "Trigger");
		TYPE_NAMES.put(TYPE_CHILD_LIST, "ChildList");
		TYPE_NAMES.put(TYPE_VISUAL_ATTRIBUTE, "VisualAttribute");
		TYPE_NAMES.put(TYPE_CANVAS_VIEW, "CanvasView");
		TYPE_NAMES.put(TYPE_WINDOW, "Window");
		TYPE_NAMES.put(TYPE_POINT, "Point");
		TYPE_NAMES.put(TYPE_COMPOUND_TEXT, "CompoundText");
		TYPE_NAMES.put(TYPE_TEXT_STRING, "TextString");
		TYPE_NAMES.put(TYPE_TAB_PAGE, "TabPage");
		TYPE_NAMES.put(TYPE_SUBCLASS_REF, "SubclassReference");
		TYPE_NAMES.put(TYPE_REF_PATH_SEGMENT, "ReferencePathSegment");
		TYPE_NAMES.put(TYPE_BLOCK_COLUMN, "BlockColumn");
		TYPE_NAMES.put(TYPE_STATIC_RG_VALUE, "StaticRecordGroupValue");
	}

	/** "Block", "Item", ... or "Type<n>" for the kinds still unidentified. */
	public static String typeName(long typ) {
		String name = TYPE_NAMES.get(typ);
		return name != null ? name : "Type" + typ;
	}

	public static boolean isTypeKnown(long typ) {
		return TYPE_NAMES.containsKey(typ);
	}

	// ---- property codes -----------------------------------------------------------

	/** Name - on every named object kind. */
	public static final long PROP_NAME = 211;
	/** Item type; see {@link #itemTypeName}. */
	public static final long PROP_ITEM_TYPE = 139;
	/** PL/SQL source blob of a trigger or program unit; decode with {@link FmtSourceDecoder}. */
	public static final long PROP_SOURCE_BLOB = 464;
	/** Byte length of the {@link #PROP_SOURCE_BLOB} payload. */
	public static final long PROP_SOURCE_LENGTH = 465;
	/** Subclass/inheritance reference; points at a {@link #TYPE_SUBCLASS_REF} object. */
	public static final long PROP_SUBCLASS_REF = 500;
	/** Name carried by a {@link #TYPE_REF_PATH_SEGMENT}, together with {@link #PROP_REF_SEGMENT_TYPE}. */
	public static final long PROP_REF_SEGMENT_NAME = 504;
	/** Object kind (an {@code IDFOS_TYP} value) of a {@link #TYPE_REF_PATH_SEGMENT}. */
	public static final long PROP_REF_SEGMENT_TYPE = 505;

	private static final Map<Long, String> PROPERTY_NAMES = new LinkedHashMap<>();
	static {
		PROPERTY_NAMES.put(PROP_NAME, "Name");

		// geometry - confirmed arithmetically on grid blocks, where consecutive
		// columns share a Y and each X equals the previous X plus its width.
		PROPERTY_NAMES.put(121L, "Height");
		PROPERTY_NAMES.put(361L, "Width");
		PROPERTY_NAMES.put(372L, "XPosition");
		PROPERTY_NAMES.put(373L, "YPosition");

		// item
		PROPERTY_NAMES.put(PROP_ITEM_TYPE, "ItemType");
		PROPERTY_NAMES.put(33L, "CanvasName");
		PROPERTY_NAMES.put(110L, "FormatMask");
		PROPERTY_NAMES.put(124L, "Hint");
		PROPERTY_NAMES.put(140L, "ColumnName");
		PROPERTY_NAMES.put(151L, "Label");
		PROPERTY_NAMES.put(165L, "LovName");
		PROPERTY_NAMES.put(383L, "Tooltip");
		PROPERTY_NAMES.put(394L, "Prompt");
		PROPERTY_NAMES.put(477L, "CalculationFormula");
		PROPERTY_NAMES.put(498L, "TabPageName");

		// block
		PROPERTY_NAMES.put(241L, "OrderByClause");
		PROPERTY_NAMES.put(359L, "WhereClause");
		PROPERTY_NAMES.put(521L, "DmlDataTargetName");
		PROPERTY_NAMES.put(522L, "QueryDataSourceName");

		// trigger / program unit
		PROPERTY_NAMES.put(PROP_SOURCE_BLOB, "SourceBlob");
		PROPERTY_NAMES.put(PROP_SOURCE_LENGTH, "SourceLength");

		// relation, LOV, record group
		PROPERTY_NAMES.put(68L, "DetailBlockName");
		PROPERTY_NAMES.put(148L, "JoinCondition");
		PROPERTY_NAMES.put(273L, "RecordGroupName");
		PROPERTY_NAMES.put(275L, "RecordGroupQuery");
		PROPERTY_NAMES.put(284L, "ReturnItem");
		PROPERTY_NAMES.put(307L, "Title");
		PROPERTY_NAMES.put(327L, "ElementValue");
		PROPERTY_NAMES.put(264L, "RadioButtonValue");

		// alert
		PROPERTY_NAMES.put(6L, "Button1Label");
		PROPERTY_NAMES.put(7L, "Button2Label");
		PROPERTY_NAMES.put(8L, "Button3Label");
		PROPERTY_NAMES.put(10L, "AlertMessage");

		// module, window, canvas, library, parameter
		PROPERTY_NAMES.put(59L, "FirstWindowName");
		PROPERTY_NAMES.put(93L, "FirstBlockName");
		PROPERTY_NAMES.put(97L, "MenuModule");
		PROPERTY_NAMES.put(116L, "ParameterDefaultValue");
		PROPERTY_NAMES.put(156L, "LibraryFileName");
		PROPERTY_NAMES.put(367L, "WindowName");

		// visual / font, shared by every visible object kind
		PROPERTY_NAMES.put(19L, "BackColor");
		PROPERTY_NAMES.put(91L, "FillPattern");
		PROPERTY_NAMES.put(103L, "FontName");
		PROPERTY_NAMES.put(104L, "FontSize");
		PROPERTY_NAMES.put(108L, "ForeColor");
		PROPERTY_NAMES.put(328L, "VisualAttributeName");

		// comment, and the reference machinery
		PROPERTY_NAMES.put(57L, "Comment");
		PROPERTY_NAMES.put(PROP_SUBCLASS_REF, "SubclassReference");
		PROPERTY_NAMES.put(PROP_REF_SEGMENT_NAME, "ReferencedName");
		PROPERTY_NAMES.put(PROP_REF_SEGMENT_TYPE, "ReferencedType");
		PROPERTY_NAMES.put(523L, "QueryColumnName");
	}

	/** The human name of a property code, or null if this code hasn't been identified yet. */
	public static String propertyName(long code) {
		return PROPERTY_NAMES.get(code);
	}

	// ---- item types (property 139) ------------------------------------------------

	private static final Map<Long, String> ITEM_TYPES = new LinkedHashMap<>();
	static {
		ITEM_TYPES.put(0L, "BeanArea");
		ITEM_TYPES.put(1L, "CheckBox");
		ITEM_TYPES.put(2L, "DisplayItem");
		ITEM_TYPES.put(3L, "Image");
		ITEM_TYPES.put(4L, "ListItem");
		ITEM_TYPES.put(7L, "PushButton");
		ITEM_TYPES.put(8L, "RadioGroup");
		ITEM_TYPES.put(10L, "TextItem");
	}

	/**
	 * "PushButton", "TextItem", ... for the values confirmed by cross-tabbing
	 * ~18k items against independent evidence (a WHEN-BUTTON-PRESSED child
	 * trigger means push button, {@code RadioButton} children mean radio group,
	 * {@code ListElement} children mean list item, and so on). Returns null for
	 * values still unidentified, and for items that carry no explicit type at
	 * all - those inherit it through {@link #PROP_SUBCLASS_REF}.
	 */
	public static String itemTypeName(Long code) {
		return code == null ? null : ITEM_TYPES.get(code);
	}

	private FmtSchema() {
	}
}
