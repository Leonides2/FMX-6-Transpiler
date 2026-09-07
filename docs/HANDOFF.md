# Handoff — estado del proyecto para continuar en otra sesión

Este documento es el punto de entrada para retomar el trabajo. Los detalles técnicos profundos ya están
en los otros docs de esta carpeta — léelos en este orden si necesitas el detalle:

1. `fmx-bytecode-notes.md` — por qué el `.fmx` NO sirve para lógica (bytecode Diana sin documentar).
2. `fmb-format-notes.md` — cómo se extrae la lógica y el árbol de nombres directo del `.fmb` binario.
3. `fmt-format-notes.md` — el hallazgo más fuerte: el `.fmt` (export a texto de Forms Builder) es un
   modelo de objetos estructurado, mucho más confiable que reversear el `.fmb`.
4. `credits-and-references.md` — qué se tomó del repo externo `LUMC/fmb2txt` y por qué.

## Objetivo del usuario

Extraer la lógica de negocio (PL/SQL de triggers/program units) y la estructura (bloques/ítems/columnas)
de una aplicación completa Oracle Forms 6 (muchos formularios, tiene `.fmb`+`.fmx` de casi todo el
aplicativo), para poder migrarla a otro lenguaje/plataforma. Primero como XML/texto intermedio.

## Qué existe hoy en el repo (todo compila con `javac`, JDK 23 disponible)

- `src/info/phosco/forms/translate/**` — parser del **`.fmx`** heredado del proyecto original, recortado
  (se eliminó todo lo puramente visual: canvas, gráficos, ventanas, fuentes, atributos visuales, editor,
  visor JavaFX). Extrae bien la estructura de **data blocks** (nombre, WHERE/ORDER clause, etc.) — validado
  contra archivos reales. **No sirve para lógica** (ver punto 1 arriba) y **no tiene Items** implementados
  (`element/datablock/item/` solo tiene el enum `ItemType`, vacío).
- `src/info/phosco/forms/fmb/**` — extractor de lógica desde el **`.fmb` binario crudo**, sin necesitar
  exportar a texto:
  - `FmbBuffer` — acceso a bytes crudos.
  - `FmbLogicScanner` — escanea records `[4 bytes BE longitud][texto]` para reconstruir triggers
    (con scope Form/Block/Item resuelto de su etiqueta) y program units completos. **Validado 100%**
    contra transcripción manual del usuario (`cr_cierr_datos.txt`) y en un segundo archivo independiente.
  - `NameAnchorScanner` — heurístico "todo nombre aparece duplicado pegado" para recuperar el árbol de
    nombres (bloques→ítems→valores de radio) del `.fmb` crudo. Da una **lista plana**, sin nesting real
    (se infiere por cercanía/orden). Validado en los mismos dos archivos.
  - `TriggerGroup`/`ProgramUnitGroup` — agrupan/dedupen los matches crudos (a veces el mismo trigger
    aparece 2+ veces en el archivo; si el contenido difiere entre copias, se reporta el conflicto en vez
    de ocultarlo).
  - `FmbXmlExporter`/`FmbHacker` (CLI) — vuelca todo esto a XML. Uso:
    `java -cp build info.phosco.forms.fmb.FmbHacker archivo.fmb [salida.xml]`
- `src/info/phosco/forms/fmt/**` — parser del **`.fmt`** (export a texto de Forms Builder, formato "ROS
  Script"). Esto es lo más sólido del proyecto:
  - `FmtParser` — parser genérico de la gramática `DEFINE <tabla> BEGIN campo=valor END` (soporta enteros,
    `NULLP`, strings `<<"...">>`, blobs `(BLONG)` multi-línea).
  - `FmtObjectGraph`/`FmtObject`/`FmtProperty` — interpreta el directorio de objetos (`FRM50_IDFO`) y las
    tablas de propiedades genéricas (`F50T/F50N/F50B/F50P/F50S/F50O`) en un **árbol real** con
    parent-child correcto (no heurístico). Validado: 4748 registros → 566 objetos, 0 propiedades huérfanas
    en `CR_CIERR.fmt` completo.
  - `FmtHacker` (CLI de prueba) — imprime el árbol. Uso:
    `java -cp build info.phosco.forms.fmt.FmtHacker archivo.fmt [profundidad_maxima]`
  - **Confirmado**: se puede reusar `FmbBuffer`+`FmbLogicScanner` (del paquete `fmb`) directamente sobre
    el `byte[]` de una propiedad `BLONG` de un objeto `.fmt` para decodificar su trigger — sin cambiar una
    línea de ese código. Ver el ejemplo en `fmt-format-notes.md`.
  - **No implementado todavía**: un `FmtXmlExporter` (existe `FmbXmlExporter` para el paquete `fmb`, pero
    no su equivalente para `fmt` — sería el mismo patrón, usando `info.phosco.forms.xml.XmlWriter`).
- `src/info/phosco/forms/xml/XmlWriter.java` — escritor XML genérico, sin dependencias externas.
  Sanitiza caracteres inválidos de XML 1.0 automáticamente (encontramos un NUL embebido real en datos
  extraídos — ver el código, tiene esto ya resuelto). Ojo: al escribir a archivo, usar
  `OutputStreamWriter(FileOutputStream, StandardCharsets.UTF_8)` explícito — `FileWriter` a secas usa el
  charset por defecto de Windows y produce XML inválido si el texto tiene acentos reales (ya nos pasó,
  ya está arreglado en `FmbHacker`, pero cualquier exportador nuevo debe recordar esto).

## Archivos de prueba reales (no están en el repo, viven en la máquina del usuario)

`C:\Users\leoni\OneDrive\Documentos\Trabajo - Coopemapro\FMX\`:
`CR_CIERR.FMB`, `cr_cierr.fmx`, `CR_CIERR.fmt`, `gl_cierres.fmb`, `gl_cierres.fmx`, `gl_cierres.fmt`,
`cr_cierr_datos.txt` (ground truth manual del usuario — triggers y textos exactos de `BLK_DATOS`).

## Pendiente / próximos pasos (en orden de valor)

1. **Mapear códigos de propiedad del `.fmt`** — sobre todo cuál da el "Item Type" real (hoy todos los
   ítems comparten `IDFOS_TYP=30`, el tipo específico vive en alguna propiedad sin identificar). Usar como
   vocabulario objetivo la lista de `credits-and-references.md` (`ItemType`, `Required`, `XPosition`, etc.
   — nombres reales de la API oficial de Oracle). Metodología: correlacionar contra Property Palette de
   Forms Builder para una muestra de ítems de tipos conocidos (radio group, botón, texto, display, imagen).
2. **Escribir `FmtXmlExporter`** — mismo patrón que `FmbXmlExporter`, pero recorriendo el árbol real de
   `FmtObjectGraph` (con nesting correcto) en vez de la lista plana de `NameAnchorScanner`.
3. **Investigar si el usuario tiene acceso a `frmxmltools.jar`** (utilidad oficial `Forms2XML` de Oracle,
   JDAPI) desde cualquier instalación de Forms/AS más nueva a la mano — si existe, probablemente reemplaza
   gran parte de este trabajo de ingeniería inversa con la API oficial. Ver `credits-and-references.md`.
4. **Exportación masiva de `.fmt`** — el usuario pidió un script de PowerShell para convertir en lote
   una carpeta (y subcarpetas) de `.fmb`/`.mmb`/`.pll` a `.fmt` usando `ifcmp60 module=... script=YES
   userid=...`. Confirmar con el usuario si ya lo corrió y qué tan bien generalizó a más formularios.
5. **Decodificar el bytecode Diana del `.fmx`** (proyecto grande, ver `fmx-bytecode-notes.md`) — el
   usuario tiene "casi todo el código" del aplicativo en pares `.fmb`/`.fmx`, corpus ideal para esto, pero
   es trabajo de varias semanas. Baja prioridad mientras el camino `.fmt` siga rindiendo mejor por menos
   esfuerzo.
6. **Estructura del `.fmx`** (bloques ya funcionan; items/columns/record groups siguen sin implementar en
   `info.phosco.forms.translate`) — probablemente ya no vale la pena invertir aquí dado que `.fmt` da todo
   esto mejor y más barato. Confirmar con el usuario antes de retomarlo.

## Estado de git al momento de este handoff

Sin commitear (working tree, rama `master`): `docs/` completo, `src/info/phosco/forms/xml/`,
`src/info/phosco/forms/fmt/`, y varios archivos nuevos/modificados en `src/info/phosco/forms/fmb/`
(`FmbXmlExporter.java`, `NameAnchor.java`, `NameAnchorScanner.java`, `ProgramUnitGroup.java`,
`TriggerGroup.java`, más cambios en `FmbBuffer.java`/`FmbHacker.java`). Hay un commit previo del usuario
("minimal core", `d94ef9d`) que sí capturó el recorte inicial del `.fmx` (Fase 0). Nadie ha pedido commit
de lo nuevo — confirmar con el usuario antes de commitear.

## Cómo verificar que todo sigue compilando

```
cd "c:\Users\leoni\source\Web\FMX-6-Transpiler"
rm -rf build && mkdir -p build
javac -d build -cp src $(find src -name "*.java")
```

Sin salida = compiló bien. Luego probar contra los archivos reales de arriba con `FmbHacker`/`FmtHacker`.
