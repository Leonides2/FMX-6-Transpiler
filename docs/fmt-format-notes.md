# Notas de investigación: el `.fmt` (export a texto de Forms Builder)

Forms Builder puede exportar un módulo a un archivo de texto (`.fmt` para forms, presumiblemente
`.mmt`/`.plt` para menús/librerías) usando el formato interno "ROS Script" (mismo "ROS" que la firma
`ROS.60050` del `.fmb` binario — es el mismo repositorio de objetos, solo que serializado como texto en
vez de binario empaquetado). El usuario ya tenía esta opción disponible en su Forms Builder y no la había
usado porque a simple vista "no es muy legible" — pero **es una fuente muchísimo más confiable que seguir
reverseando el `.fmb` binario a puro heurístico**, porque en vez de bytes hay una gramática de texto
regular, con IDs de tipo/propiedad como números planos.

## Gramática (confirmada, parser implementado en `info.phosco.forms.fmt`)

```
DESCRIBE  <tabla>          <- declara nombres de campo; no aporta datos, se ignora
BEGIN
  <tipo>  <campo>
  ...
END

DEFINE  <tabla>             <- esto sí es un registro de datos real
BEGIN
  <campo> = <valor>
  ...
END
```

`<valor>` es uno de:
- entero (posiblemente negativo): `NV = 8`
- `NULLP`
- string en una línea: `TV = <<"texto">>`
- blob binario multi-línea:
  ```
  PV = (BLONG)
  <<"
  <hex, 8 grupos de 8 dígitos por línea>
  ...
  ">>
  ```

## El modelo de objetos

- **`FRM50_IDFO`** = directorio de objetos. Cada entrada: `IDFOS_OID` (id del objeto), `IDFOS_NAM`
  (nombre, si tiene), `IDFOS_TYP` (código numérico de qué CLASE de objeto es), `IDFOS_OOI` (id del objeto
  **padre** - así se arma el árbol real, con anidamiento correcto, no adivinado), `IDFOS_OPN`/`IDFOS_CNT`
  (referencia + conteo de sub-objetos).
- **`F50T` / `F50N` / `F50B` / `F50P` / `F50S` / `F50O`** = tablas de propiedades genéricas (Texto /
  Número / Booleano / Blob-largo / String-extendido / Referencia-a-objeto). Cada registro tiene forma
  `(dueño, índice, código-de-propiedad, valor)` — p. ej. `F50T{TP=123, TI=1, TN=211, TV="BLK_DATOS"}`
  significa "el objeto 123 tiene la propiedad de texto #211 = 'BLK_DATOS'".

Los **códigos de propiedad son globales** (no por tipo de objeto) — el código `211` significa "Name" para
un bloque, un ítem, un trigger, lo que sea. Eso hace que aprender un código sirva para *todo* el archivo,
muy distinto a la arqueología de offsets fijos del `.fmx`.

**Tipos de objeto (`IDFOS_TYP`) identificados hasta ahora** (por correlación con nombres conocidos):
`22` = módulo de formulario, `7` = data block, `30` = ítem (todos los tipos de ítem comparten este código
- el tipo específico, radio/botón/texto/etc., debe vivir en alguna propiedad interna, no identificada
todavía), `68` = trigger, `3` = alerta (probable), `5` = librería adjunta/editor (probable). Sin mapear:
`69/70/71/105/107/109/125/126` (sub-objetos internos del módulo, de administración/compilación).

**Tablas sin reconocer** (recolectadas pero ignoradas por ahora, volumen bajo - 229 registros de 4748 en
`CR_CIERR.fmt`): `TOOL_MODULE`, `VG_COLOR`.

## Validación cruzada con lo que ya existía

El mismo blob `BLONG` (propiedad `464` de un objeto `typ=68`) que aquí aparece como texto hexadecimal
es *byte por byte* el mismo dato que `FmbLogicScanner` ya decodificaba escaneando el `.fmb` crudo. Prueba
directa: se tomó el `byte[]` decodificado de esa propiedad y se le pasó **sin ningún cambio** al
`FmbLogicScanner` existente (`new FmbLogicScanner(new FmbBuffer(blob)).scanTriggers()`) — decodificó
correctamente `WHEN-NEW-BLOCK-INSTANCE (BLK_DATOS)` con el texto exacto conocido. Es decir: el trabajo ya
hecho en `info.phosco.forms.fmb` es reutilizable tal cual sobre los blobs que entrega el parser de `.fmt`.

`FmtObjectGraph` sobre `CR_CIERR.fmt` completo: 4748 registros → 566 objetos, **0 propiedades huérfanas**
(ninguna propiedad quedó sin encontrar a su dueño). El árbol resultante coincide exacto con lo ya conocido:
`BLK_FORMATO` con sus 22 hijos reales (ítems), cada botón con su propio trigger anidado *debajo del ítem*
(no del bloque, como se había asumido con el heurístico de nombres duplicados del `.fmb`) - información
de anidamiento que el heurístico plano no podía dar.

## Recomendación

Para cualquier formulario/menú/librería del que se pueda generar `.fmt` desde Forms Builder, este parser
estructurado debería ser la fuente **primaria** de verdad para el árbol (bloques/ítems/triggers/relaciones
reales) - es determinístico, no heurístico, y ya viene con conteo de integridad (huérfanos/tablas
desconocidas) para detectar si algo no calzó. El escáner heurístico de `.fmb`
(`NameAnchorScanner`/`FmbLogicScanner` corriendo sobre el binario crudo) queda como respaldo para cuando
solo se tenga el `.fmb`/`.fmx` sin exportar a texto.

Pendiente: mapear los códigos de propiedad más importantes (¿cuál da el "Item Type" real de cada ítem?,
posición/tamaño, flags de required/enabled/visible) correlacionando contra el Property Palette de Forms
Builder para una muestra de ítems de tipos conocidos - mismo método de siempre (dato conocido → buscar su
huella), pero ahora sobre propiedades ya tipadas en vez de bytes crudos.

---

# Segunda iteración: validación a escala y mapeo de códigos

Todo lo de abajo se derivó del corpus completo del aplicativo — **176 `.fmt`** exportados con
`scripts/Export-FmtBatch.ps1` (`FMX/FMT/`, 342 MB), es decir **1.723.047 registros y 187.169 objetos**.
Ya no es "validado en dos archivos": es el aplicativo entero.

## Integridad del parser sobre el corpus completo

| métrica | resultado |
|---|---|
| archivos parseados | 176 / 176 (0 fallos) |
| registros | 1.723.047 |
| objetos | 187.169 |
| **propiedades huérfanas** (sin dueño) | **0** |
| registros de tablas no reconocidas | 40.304 (solo `TOOL_MODULE` y `VG_COLOR`, ver abajo) |

Cero huérfanas en 1,7M de registros es la señal más fuerte de que el modelo objeto/propiedad está bien
entendido: cada propiedad encontró su objeto. Las tablas no reconocidas son las mismas dos de siempre y
son ruido conocido: `TOOL_MODULE` (1 registro por archivo, cabecera de la herramienta) y `VG_COLOR`
(228 por archivo, la paleta de colores fija — 40.128 registros que son la *misma* paleta repetida).

## Cómo se identificaron los códigos (metodología reutilizable)

No se adivinó por nombre: cada código se fijó correlacionándolo contra evidencia independiente del
propio corpus. Las tres técnicas que funcionaron, en orden de contundencia:

1. **Clasificación cruzada contra evidencia estructural.** Para el "Item Type" se etiquetaron los ~18.000
   ítems sin mirar la propiedad buscada: un ítem con un trigger hijo `WHEN-BUTTON-PRESSED` *es* un botón;
   uno con hijos `RadioButton` *es* un radio group; uno con hijos `ListElement` *es* una lista. Luego se
   tabuló cada código numérico contra esas clases. El código **139** separó las clases limpiamente
   (botones → 7 en 1461 de 1462 casos; radio groups → 8; listas → 4) mientras el resto de códigos se
   repartía indistinto entre clases. Eso es Item Type, sin ambigüedad.
2. **Verificación aritmética.** La geometría se confirmó en bloques de tipo grilla: en
   `CR_DIAGNOSTICO_CIERRE` todas las columnas comparten `N373=32` (misma fila) y sus `N372` encadenan
   exacto con los anchos — 23+71=94, 94+28≈123, 123+83=206, 206+83≈290, 290+83=373. Cuatro sumas
   consecutivas cuadrando no es casualidad: `372=X`, `373=Y`, `361=Width`, `121=Height`. Refuerzo: el
   único ítem multilínea del bloque (`OBSERVACIONES`) tiene `121=42` ≈ 3 × 14.
3. **Pareo de conteos.** Códigos que aparecen exactamente el mismo número de veces son propiedades
   compañeras de un mismo rasgo (p. ej. `N721` aparece 8028 veces, igual que `T394`/Prompt; `N722` 3994
   veces, igual que `T383`/Tooltip). Sirvió sobre todo como **antídoto**: hizo descartar a `N721`/`N722`
   como candidatos a ancho/alto, que era la conjetura obvia por su cantidad de valores distintos.

## Tipos de objeto (`IDFOS_TYP`)

Confirmados por nombres reales y por estructura de anidamiento (ver `FmtSchema`):

| typ | qué es | typ | qué es |
|---|---|---|---|
| 3 | Alert | 60 | Property Class (`CLS_*`) |
| 5 | Attached Library | 62 | Radio Button (valor de un radio group) |
| 7 | Data Block | 64 | Record Group |
| 9 | Graphic (frames, textos) | 66 | Relation (maestro-detalle) |
| 11 | Canvas | 68 | Trigger |
| 13 | LOV Column Mapping | 69 | Child List (contenedor, `O312` → primer hijo) |
| 15 | Record Group Column | 74 | Visual Attribute |
| 20 | Editor | 76 | Canvas View (geometría del canvas) |
| 22 | Form Module | 78 | Window |
| 24 | Parameter | 82 | Point |
| 26 | Query Data Source Column | 84 / 86 | Compound Text / Text String |
| 30 | Item (todos los tipos) | 88 | Tab Page |
| 34 | List Element | 90 | **Subclass Reference** |
| 36 | LOV | 94 | **Reference Path Segment** |
| 40 / 42 | Popup Menu / Menu Item | 100 | Block Column (`BLOQUE.ITEM`) |
| 52 | Object Group | 123 | Static Record Group Value |
| 54 | Object Group Child | | |

Siguen sin identificar (se exportan como `Type<n>` con sus propiedades crudas, nunca inventadas):
`19, 70, 95, 105, 107, 108, 109, 125, 126`. Los seis últimos aparecen exactamente **176 veces** cada uno
— uno por módulo — así que son objetos administrativos del módulo, no contenido del formulario.

## Item Type (propiedad `139`)

| valor | tipo de ítem | evidencia |
|---|---|---|
| 0 | Bean Area | único ítem llamado `BEAN` |
| 1 | Check Box | 329 ítems, nombres `CHECK_*`/`IND_*`, triggers `WHEN-CHECKBOX-CHANGED` |
| 2 | Display Item | 1804 ítems, `DES_*`/`MENSAJE`, sin navegación |
| 3 | Image | 36 ítems: `LOGO_*`, `FOTO`, `IMAGEN` |
| 4 | List Item | 629 ítems, **todos** con hijos `ListElement`, `WHEN-LIST-CHANGED` |
| 7 | Push Button | 1484 ítems, 1461 con `WHEN-BUTTON-PRESSED`, casi todos con Label |
| 8 | Radio Group | 48 ítems, todos con hijos `RadioButton` |
| 10 | Text Item | 6990 ítems (el más común, como se espera) |

El valor `9` (4 casos, todos en formas demo de Oracle) quedó **sin identificar** a propósito.

**Ojo — el 37% de los ítems no trae esta propiedad.** 6737 de 18070 ítems no declaran tipo: lo heredan
por subclase (72% de ellos solo tienen nombre + referencia de subclase). No es un fallo de extracción,
es cómo está construido el aplicativo: los ítems estándar (barra de botones, etc.) se heredan de una
librería de objetos externa. Ver la sección siguiente.

## Herencia por subclase (resuelta)

Un objeto heredado lleva la propiedad `500` apuntando a un objeto `typ=90` (Subclass Reference), cuyos
hijos `typ=94` (Reference Path Segment) deletrean el camino calificado al origen, cada uno con un nombre
(`504`) y el tipo de objeto de ese segmento (`505`):

```
Item LIMPIAR --500--> #46 (typ=90)
                        +-- typ=94: nombre="UP_BASE",     tipo=22 (Form Module)
                        +-- typ=94: nombre="OBJ_GENERAL", tipo=52 (Object Group)
  => "LIMPIAR se hereda del object group OBJ_GENERAL del módulo UP_BASE"
```

Ese mismo mecanismo `typ=94` es genérico: también arma las referencias `BLOQUE.ITEM` de las columnas.
**Consecuencia práctica para la migración**: las propiedades reales de esos ítems heredados *no están*
en el `.fmt` del formulario — viven en el módulo/librería origen (`UP_BASE`, etc.). Si se necesitan,
hay que exportar también ese módulo. El exportador marca los casos donde el `.fmt` ni siquiera registra
el origen con `sourceNotRecorded="true"` en vez de emitir un elemento vacío que parezca un bug.

## Códigos de propiedad identificados

Ver `FmtSchema.java` para la lista viva. Los más útiles para migrar lógica y estructura:

- **Universal**: `211` Name · `57` Comment · `500` SubclassReference
- **Lógica**: `464` blob del fuente PL/SQL · `465` su longitud · `477` CalculationFormula
- **Geometría**: `372` X · `373` Y · `361` Width · `121` Height
- **Ítem**: `139` ItemType · `33` CanvasName · `140` ColumnName · `110` FormatMask · `151` Label ·
  `394` Prompt · `383` Tooltip · `124` Hint · `165` LovName · `498` TabPageName
- **Bloque**: `522` QueryDataSourceName (tabla o `SELECT` completo) · `521` DmlDataTargetName ·
  `359` WhereClause · `241` OrderByClause
- **Relación / LOV / Record Group**: `148` JoinCondition · `68` DetailBlockName · `273` RecordGroupName ·
  `275` RecordGroupQuery (el `SELECT`) · `284` ReturnItem · `327` ElementValue · `264` RadioButtonValue
- **Alert**: `10` AlertMessage · `6`/`7`/`8` etiquetas de botón · `307` Title
- **Módulo/ventana**: `59` FirstWindowName · `93` FirstBlockName · `97` MenuModule · `156` LibraryFileName
- **Visual**: `19` BackColor · `108` ForeColor · `103` FontName · `104` FontSize · `328` VisualAttributeName

Un valor de **-2147483647** es el centinela de "propiedad no fijada / heredada", no un número real; el
exportador lo escribe como `(unset)`.

## El blob de fuente (`464`) — decodificación

Adentro del blob está la misma cadena de registros `[4 bytes BE longitud][payload]` del `.fmb`, así que
`FmbBuffer` se reusa tal cual. Pero **no** se reusa `FmbLogicScanner`: aquí el blob ya pertenece a un
objeto concreto cuyo nombre y dueño vienen del árbol, así que no hay que barrer un archivo entero,
inferir el scope de una etiqueta `"NOMBRE (BLOQUE.ITEM)"`, ni deduplicar copias. `FmtSourceDecoder` hace
lo mínimo y por eso acierta más. Layouts confirmados:

- **Trigger**: registro que abre el bloque anónimo — `"BEGIN\n"` solo, o `"BEGIN\n:BLOQUE.ITEM:=("` en
  los `FORMULA-CALCULATION`, cuyo "fuente" es una expresión y no una sentencia — y **el registro
  siguiente es el texto real** del trigger.
- **Program unit**: el primer registro de texto que abre con `PROCEDURE`/`FUNCTION`/`PACKAGE`,
  **saltando el banner de comentarios** (`--` o `/* Formatted on ... */`) que traen muchos fuentes reales.

Resultado sobre el corpus: **17.579 de 17.581 fuentes (99,99%)** decodificados con el layout confirmado.
Los 2 restantes caen a un respaldo (el registro de texto más largo del blob) y quedan **marcados** con
`decode="fallback"` en el XML — se recupera el fuente legible pero con basura de bordes, así que están
señalados en vez de darse por buenos.

## Trampas encontradas al escribir el exportador

- **Propiedades multivaluadas.** Un mismo código puede repetirse en un objeto con distinto índice (el
  campo `*I` de las tablas `F50*`); p. ej. un `typ=19` lista con el código `211` todos los program units
  que referencia. Colapsarlas en un atributo XML produce atributos duplicados y XML inválido — van como
  elementos `<Property>` repetidos.
- **Indentación dentro del texto.** `XmlWriter` indentaba antes de la etiqueta de cierre aunque el
  contenido fuera texto/CDATA, lo que **añadía espacios en blanco al final de cada fuente PL/SQL
  extraído**. Ya está corregido (no indenta cuando lo último escrito fue texto), pero es el tipo de
  defecto que pasa desapercibido y contamina todo lo extraído.
- **Blobs que no son fuente.** La propiedad `464` también aparece en objetos que no son trigger ni
  program unit (payload binario de un menú). El exportador ya no intenta decodificarlos como PL/SQL.
