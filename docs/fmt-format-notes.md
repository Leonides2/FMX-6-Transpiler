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
