# Oracle Forms 6 — extractor de lógica y estructura

Extrae la **lógica de negocio** (PL/SQL de triggers y program units) y la **estructura** (bloques, ítems,
LOVs, relaciones, record groups) de una aplicación Oracle Forms 6, para poder migrarla a otro lenguaje o
plataforma. La salida es XML intermedio, pensado para ser consumido por un transpilador, por una migración
asistida con IA, o leído a mano.

Objetivo del proyecto: sacar la mayor cantidad posible de información de los tres formatos —`.fmt`, `.fmb`
y `.fmx`— incluyendo el caso difícil de **módulos en producción cuyo fuente se perdió y solo queda el
`.fmx` compilado**. Todo open source y sin código propietario. El plan para llegar ahí está en
[`docs/plan-fmb-fmx.md`](docs/plan-fmb-fmx.md).

Nace como fork de un decompilador de investigación de `.fmx` que reconstruía la UI completa (canvas,
ventanas, fuentes, gráficos). Ese código de UI se eliminó a propósito: aquí el objetivo no es reconstruir
el formulario sino extraer lo que hace.

## Tres rutas, en orden de preferencia

| ruta | fuente | estado | qué da |
|---|---|---|---|
| **`.fmt`** | export a texto de Forms Builder | **completa y validada** | todo: árbol real de objetos, PL/SQL íntegro, propiedades |
| `.fmb` | binario de diseño | parcial, heurística | triggers y program units + lista plana de nombres |
| `.fmx` | binario compilado (runtime) | parcial | estructura de data blocks; **no** tiene el texto del PL/SQL |

Del `.fmx` se pierde el *texto* del código (se compila a bytecode propietario), pero **no** la estructura
de la pantalla: los nombres de bloques, ítems, canvas, ventanas y las etiquetas visibles sobreviven a la
compilación. Ver el plan para qué tanto se puede recuperar de ahí.

**Usa la ruta `.fmt` siempre que puedas.** Es determinística (no heurística), da el anidamiento real
padre-hijo y viene con contadores de integridad. Sobre el aplicativo completo del caso real —176
formularios, 1,7M de registros— parsea 176/176 archivos con **0 propiedades huérfanas** y recupera
**11.969 triggers y 5.612 program units**, con el 99,99% de los fuentes decodificados por el layout
confirmado. Las otras dos rutas son respaldo para cuando no se pueda generar el `.fmt`.

Detalle técnico de cada formato en [`docs/`](docs/); empieza por [`docs/HANDOFF.md`](docs/HANDOFF.md).

## Compilar

Necesitas un JDK 8 o superior (probado con 23). No hay dependencias externas ni build system:

```sh
cd FMX-6-Transpiler
rm -rf build && mkdir -p build
javac -d build -cp src $(find src -name "*.java")
```

Sin salida = compiló bien.

## Uso — ruta `.fmt` (la principal)

### Paso 1: generar los `.fmt` desde los `.fmb`

Forms Builder exporta un módulo a texto ("ROS Script") con `File > Administration > Convert`, o en lote
con el compilador de línea de comandos. El script incluido hace lo segundo sobre una carpeta y sus
subcarpetas, replicando la estructura en el destino:

```powershell
# Prueba primero en seco (no ejecuta nada, solo muestra qué haría)
.\scripts\Export-FmtBatch.ps1 -SourceFolder "C:\ruta\Formularios" -OutputFolder "C:\ruta\FMT" `
    -Credentials "usuario/clave@basedatos" -WhatIf

# Corrida real
.\scripts\Export-FmtBatch.ps1 -SourceFolder "C:\ruta\Formularios" -OutputFolder "C:\ruta\FMT" `
    -Credentials "usuario/clave@basedatos"
```

Convierte también menús (`.mmb`→`.mmt`) y librerías (`.pll`→`.pld`). Si `ifcmp60.exe` no está en el
`PATH`, pásalo con `-IfcmpPath "C:\orant\bin\ifcmp60.exe"`. Al terminar imprime cuántos convirtió y
cuántos fallaron — **revisa los fallidos**: son los módulos que necesitarían la ruta `.fmb`.

### Paso 2: convertir los `.fmt` a XML

```sh
# Un formulario
java -cp build info.phosco.forms.fmt.FmtToXml archivo.fmt salida.xml

# Una carpeta completa, en lote (respeta las subcarpetas)
java -Xmx6g -cp build info.phosco.forms.fmt.FmtToXml "C:\ruta\FMT" "C:\ruta\XML"

# Versión compacta: omite las propiedades cuyo significado todavía no está identificado
java -cp build info.phosco.forms.fmt.FmtToXml archivo.fmt salida.xml --brief
```

El `-Xmx6g` es por los `.fmt` grandes (los hay de 12 MB). Cada archivo reporta una línea:

```
cr_cierr.fmt     registros=4748   objetos=566   triggers=30   programUnits=42   huerfanas=0   respaldo=0
```

Las dos señales de alarma son **`huerfanas`** (propiedades que no encontraron su objeto — debe ser 0) y
**`respaldo`** (fuentes que no calzaron con el layout confirmado y se recuperaron por heurística; esos
quedan marcados con `decode="fallback"` en el XML, no se dan por buenos en silencio).

### El XML que sale

```xml
<FormsModuleExport file="cr_cierr.fmt">
  <FormModule id="1" name="CR_CIERR" Title="Cierre Diario" FirstWindowName="WND_INICIAL">
    <Block id="123" name="BLK_DATOS" QueryDataSourceName="CREDITO.CR_DIAGNOSTICO_CIERRE"
           WhereClause="cod_compania = :global.compania">
      <Trigger id="125" name="WHEN-NEW-BLOCK-INSTANCE" SourceLength="2788">
        <Source blobBytes="2788"><![CDATA[:system.message_level := 20;
default_value('','global.traslado');
...]]></Source>
      </Trigger>
      <Item id="130" name="BUT_ACEPTAR" ItemType="PushButton" Label="&amp;Realizar Cierre"
            XPosition="239" YPosition="207" Width="90" Height="17" CanvasName="CNV_CIERR">
        <SubclassedFrom module="UP_BASE" object="OBJ_GENERAL" objectType="ObjectGroup"/>
        <Trigger name="WHEN-BUTTON-PRESSED">...</Trigger>
        <Property code="N175" value="128"/>   <!-- propiedad aún sin identificar -->
      </Item>
    </Block>
  </FormModule>
  <Integrity objects="566" orphanProperties="0" unrecognizedTableRecords="229" objectsOutsideTree="115"/>
</FormsModuleExport>
```

Tres cosas a tener en cuenta al consumirlo:

- Las propiedades **sin identificar se emiten igual**, como `<Property code="N175" value="128"/>`. Nada se
  descarta por no saber qué significa; nada se renombra por conjetura. Los tipos de objeto no
  identificados salen como `<Type19 rawType="19">`.
- Un valor **`(unset)`** es el centinela `-2147483647` del formato: "propiedad no fijada / heredada".
- **`<SubclassedFrom>`** dice de dónde hereda un objeto. Si un ítem casi no trae propiedades es porque las
  hereda de una librería de objetos externa, y **esas propiedades no están en este archivo** — hay que
  exportar también el módulo origen. Cuando el `.fmt` ni siquiera registra el origen, sale
  `sourceNotRecorded="true"`.

### Explorar un `.fmt` sin generar XML

```sh
java -cp build info.phosco.forms.fmt.FmtHacker archivo.fmt [profundidad_maxima]
```

Imprime el árbol de objetos con sus contadores de integridad. Útil para inspeccionar rápido.

## Uso — ruta `.fmb` (respaldo)

Cuando solo tienes el binario de diseño y no puedes exportarlo a texto. Extrae triggers y program units
con su fuente completo, más una **lista plana** de nombres (sin anidamiento real: se infiere por orden y
cercanía).

```sh
# Reporte de texto en pantalla
java -cp build info.phosco.forms.fmb.FmbHacker archivo.fmb

# XML
java -cp build info.phosco.forms.fmb.FmbHacker archivo.fmb salida.xml
```

Si un mismo trigger aparece duplicado en el archivo con contenido distinto, se reporta el conflicto en
vez de ocultarlo.

## Uso — ruta `.fmx` (parcial)

El `.fmx` es el compilado de runtime: **no contiene el texto PL/SQL**, solo bytecode propietario sin
documentar. Sirve para la estructura de data blocks. Hoy imprime a log, no exporta XML.

```sh
java -cp build info.phosco.forms.translate.OracleHacker archivo.fmx <debug|info|warn|off>
```

Ver [`docs/fmx-bytecode-notes.md`](docs/fmx-bytecode-notes.md) para qué se puede y qué no se puede sacar
de este formato.

## Estructura del repo

```
src/info/phosco/forms/
  fmt/          ruta .fmt — parser, esquema de códigos, decodificador de PL/SQL, exportador XML, CLI
  fmb/          ruta .fmb — escáner de lógica y de nombres, exportador XML, CLI
  translate/    ruta .fmx — parser heredado y recortado
  xml/          escritor XML genérico, sin dependencias
scripts/        Export-FmtBatch.ps1 (conversión en lote .fmb/.mmb/.pll a texto)
docs/           notas de investigación de cada formato + HANDOFF (estado del proyecto)
```

## Límites conocidos

- La ruta `.fmt` depende de poder correr Forms Builder / `ifcmp60`, que compila contra la base de datos:
  un módulo que no compile puede no exportarse.
- Las propiedades heredadas de librerías de objetos externas no viven en el `.fmt` del formulario (ver
  `<SubclassedFrom>` arriba).
- Quedan códigos de propiedad sin identificar, sobre todo flags booleanos (Required, Enabled, Visible,
  Insert/Update/Query Allowed). Se exportan crudos. La metodología para seguir mapeándolos está en
  [`docs/fmt-format-notes.md`](docs/fmt-format-notes.md).
- No se decodifican imágenes: quedan como referencia/placeholder.
- Del `.fmx` no se recupera hoy el PL/SQL. Los nombres de trigger tampoco sobreviven a la compilación
  (están como códigos numéricos, no como texto), así que del `.fmx` solo no se sabe todavía ni cuántos
  triggers tenía un formulario.

## Licencia y créditos

Ver [`LICENSE`](LICENSE) y [`docs/credits-and-references.md`](docs/credits-and-references.md).
