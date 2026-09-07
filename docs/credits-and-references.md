# Créditos y referencias externas

## fmb2txt (LUMC)

https://github.com/LUMC/fmb2txt — MIT License, Copyright (c) 2025 Leiden University Medical Center.

Herramienta Java que convierte `.fmb` a XML invocando la utilidad oficial de Oracle
`oracle.forms.util.xmltools.Forms2XML` (clase Java que vive en `frmxmltools.jar`, parte de una
instalación de Oracle Forms/Application Server con el JDAPI — 10g/11g/12c; **no** confirmado que exista
en instalaciones puras de "Forms Developer" 6i como la del usuario). Comando que ejecuta internamente:

```
java -classpath <ORACLE_HOME>\jlib\frmxmltools.jar;<ORACLE_HOME>\jlib\frmjdapi.jar;<ORACLE_HOME>\lib\xmlparserv2.jar;<ORACLE_HOME>\lib\xschema.jar ^
     oracle.forms.util.xmltools.Forms2XML archivo.fmb
```

Si el usuario llega a tener acceso a un `frmxmltools.jar` (de cualquier instalación de Forms más nueva
que tenga a mano, incluso si no es la que usa a diario), **esto sería superior** a todo lo que este
proyecto reversea a mano — es la API oficial de Oracle leyendo el `.fmb` de verdad, no heurística.
Vale la pena revisarlo antes de seguir invirtiendo en el parser de `.fmt`/`.fmb` propio.

No se vendorizó el código (son ~2 clases Java que solo invocan el proceso externo y limpian el XML
resultante con `dom4j` — no hay parsing propio que nos sirva reusar). Lo que sí se tomó es su **lista de
nombres de propiedad reales** (el vocabulario objetivo para mapear los códigos numéricos del `.fmt` — ver
`docs/fmt-format-notes.md`, sección "pendiente"):

```
ItemType, Required, Enabled, Visible, XPosition, YPosition, Width, Height, DataType,
QueryAllowed, InsertAllowed, UpdateAllowed, DeleteAllowed, KeyMode, NavigationStyle,
RecordOrientation, LockMode, ProgramUnitType, FontName, FontSize, CanvasName, BackColor,
ForeColor, Prompt, Label, Hint, MultiLine, WrapStyle, Justification, TabPageName,
VisualAttributeName, SubclassObjectGroup, WindowStyle, MenuModule, CoordinateSystem,
RecordsFetchedCount, RecordsDisplayCount, RecordsBufferedCount, EnforcedPrimaryKey,
ValidateFromList, CheckBoxOtherValues, UncheckedValue, InitializeValue, ...
(lista completa en su FormsCleanerService.java, clase GRAPHICAL_ATTRIBUTES)
```

Y su modelo de salida XML confirma nombres de elemento reales: `<Trigger TriggerText="...">`,
`<ProgramUnit ProgramUnitText="...">`, `<Alert>`, `<AttachedLibrary>`, `<VisualAttribute>`,
`<ObjectGroup>` — útil como referencia de "cómo se llama esto realmente" al seguir mapeando
`FmtObject`/`FmtProperty` en `info.phosco.forms.fmt`.
