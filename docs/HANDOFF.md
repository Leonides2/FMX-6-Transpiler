# Handoff — estado del proyecto para continuar en otra sesión

Este documento es el punto de entrada para retomar el trabajo. Los detalles técnicos profundos ya están
en los otros docs de esta carpeta — léelos en este orden si necesitas el detalle:

1. `fmx-bytecode-notes.md` — por qué el `.fmx` NO sirve para lógica (bytecode Diana sin documentar).
2. `fmb-format-notes.md` — cómo se extrae la lógica y el árbol de nombres directo del `.fmb` binario.
3. `fmt-format-notes.md` — **el camino principal**: el `.fmt` (export a texto de Forms Builder) es un
   modelo de objetos estructurado. Su segunda mitad tiene el mapeo de códigos derivado del corpus completo.
4. `credits-and-references.md` — qué se tomó del repo externo `LUMC/fmb2txt` y por qué.
5. `plan-fmb-fmx.md` — **plan propuesto (sin implementar)** para atacar el `.fmb` y el `.fmx` con el
   ground truth que ahora da el `.fmt`. Pendiente de revisión del usuario.

## Objetivo del usuario

Extraer la lógica de negocio (PL/SQL de triggers/program units) y la estructura (bloques/ítems/columnas)
de una aplicación completa Oracle Forms 6, para poder migrarla a otro lenguaje/plataforma. Primero como
XML/texto intermedio.

## Dónde está el trabajo hoy

**El camino `.fmt` → XML está terminado y corrido sobre el aplicativo entero.** Lo que queda es
aprovechar ese XML (o afinar detalles), no seguir haciendo ingeniería inversa básica.

Números del último corrido completo (reproducible, ver "Cómo verificar" abajo):

| | |
|---|---|
| formularios convertidos | **176 / 176** (0 fallos) |
| registros parseados | 1.723.047 |
| objetos | 187.169 |
| **propiedades huérfanas** | **0** |
| triggers extraídos | **11.969** |
| program units extraídos | **5.612** |
| fuentes con layout confirmado | 17.579 / 17.581 (**99,99%**) |
| XML generado | 92,9 MB, los 176 bien formados |

Validado además contra el ground truth manual del usuario (`cr_cierr_datos.txt`): bloques, nombres de
triggers, texto exacto del PL/SQL, y lista+orden de ítems coinciden exacto. Los tipos de ítem salen bien
clasificados (`BUT_ACEPTAR`=PushButton, `OPERACION_CIERRE`=RadioGroup, `ESTATUS`=DisplayItem).

## Qué existe hoy en el repo (todo compila con `javac`, JDK 23 disponible)

- `src/info/phosco/forms/fmt/**` — **la parte sólida del proyecto**:
  - `FmtParser` — parser de la gramática `DEFINE <tabla> BEGIN campo=valor END`.
  - `FmtObjectGraph`/`FmtObject`/`FmtProperty` — árbol real de objetos con parent-child correcto
    (no heurístico), con contadores de integridad.
  - `FmtSchema` — **nuevo**: diccionario de tipos de objeto y códigos de propiedad, todo derivado del
    corpus de 176 formularios. Solo nombra lo que tiene evidencia; el resto queda sin nombre a propósito.
  - `FmtSourceDecoder` — **nuevo**: saca el PL/SQL del blob `464` de un trigger o program unit. No usa
    `FmbLogicScanner` (barrido heurístico de archivo entero) porque aquí el blob ya pertenece a un objeto
    conocido; reusa solo `FmbBuffer` para leer los registros `[len][payload]`.
  - `FmtXmlExporter` — **nuevo**: vuelca el árbol real a XML. Nombra lo conocido como atributos, resuelve
    la herencia por subclase, mete el PL/SQL en CDATA, y **emite igual las propiedades sin identificar**
    como `<Property code="N372" value="22"/>` para no perder nada. Cierra con un `<Integrity>` que reporta
    huérfanas/tablas no reconocidas/objetos fuera del árbol.
  - `FmtToXml` — **nuevo**: CLI, un archivo o una carpeta completa en lote.
  - `FmtHacker` — CLI viejo de prueba, imprime el árbol.
- `src/info/phosco/forms/fmb/**` — extractor desde el `.fmb` binario crudo (`FmbLogicScanner`,
  `NameAnchorScanner`, `FmbXmlExporter`, CLI `FmbHacker`). **Sigue siendo útil solo como respaldo** para
  cuando no se pueda exportar el `.fmt`; da una lista plana de nombres sin anidamiento real.
- `src/info/phosco/forms/translate/**` — parser del `.fmx` heredado y recortado. Extrae data blocks pero
  no lógica ni items. Ya superado por el camino `.fmt`.
- `src/info/phosco/forms/xml/XmlWriter.java` — escritor XML sin dependencias. Ojo con dos cosas que ya
  están resueltas pero que cualquier código nuevo debe respetar: escribir siempre con
  `OutputStreamWriter(..., StandardCharsets.UTF_8)` explícito (`FileWriter` a secas rompe los acentos), y
  no indentar antes de cerrar un elemento cuyo contenido es texto (añadía espacios al PL/SQL extraído).
- `scripts/Export-FmtBatch.ps1` — conversión en lote `.fmb`→`.fmt` con `ifcmp60`. **Ya se corrió**: es lo
  que produjo los 176 `.fmt`.

## Archivos reales (en la máquina del usuario, no en el repo)

`C:\Users\leoni\OneDrive\Documentos\Trabajo - Coopemapro\FMX\`:
- `FMT\` — **176 `.fmt`** del aplicativo completo (342 MB), salida de `Export-FmtBatch.ps1`.
- `XML\` — **176 `.xml`** (92,9 MB), salida de `FmtToXml`, misma estructura de subcarpetas.
- `CR_CIERR.FMB`, `cr_cierr.fmx`, `gl_cierres.*` — muestras sueltas.
- `cr_cierr_datos.txt` — ground truth manual del usuario.

## Restricción que manda las prioridades

**Hay módulos en producción cuyo `.fmb` se perdió**: de ellos solo queda el `.fmx` compilado. Recuperar
lo que se pueda de esos `.fmx` no es opcional — sin eso, esa lógica no se puede migrar. El objetivo del
proyecto es un aplicativo open source, sin código propietario, que saque el máximo de los tres formatos
para migrar pantallas y lógica asistido con IA.

Eso reordena todo: la ruta `.fmx` pasa a ser el camino crítico y la `.fmb` baja a segundo lugar (para un
módulo cuyo `.fmb` se perdió, un parser de `.fmb` no sirve de nada). El plan completo, con el método de
validación y el costo/beneficio de cada paso, está en **`plan-fmb-fmx.md`** — pendiente de aprobación,
nada de eso está implementado.

Dos hallazgos medidos en esta sesión que condicionan ese plan:

- **La estructura de pantalla sobrevive en el `.fmx`.** Los nombres de bloques, ítems, canvas, ventanas y
  las etiquetas visibles están ahí (verificado en `cr_cierr.fmx`). Los nombres de trigger **no** — están
  como códigos numéricos. Se pierde el texto del código, no la pantalla.
- **El 81% de los triggers tiene un fuente que se repite entre módulos** (11.969 triggers, solo 3.275
  fuentes distintos; `do_key('delete_record');` aparece en 171 módulos). Para un módulo huérfano, eso
  abre recuperar el fuente **emparejando bytecode** contra los módulos que sí tienen fuente, sin
  decodificar un solo opcode. Es el paso de mayor retorno del plan.

## Pendiente / próximos pasos (en orden de valor)

1. **Decidir qué se hace con el XML** — es el paso que le toca al usuario. Ya está toda la lógica de
   negocio del aplicativo en 176 XML navegables. La pregunta ya no es "cómo extraerlo" sino "a qué se
   migra". Vale la pena preguntarle antes de seguir puliendo el extractor.
2. **Exportar los módulos origen de la herencia** (`UP_BASE` y compañía). El 37% de los ítems hereda sus
   propiedades de una librería de objetos externa, y esas propiedades **no están** en el `.fmt` del
   formulario. Si la migración necesita geometría/propiedades de la barra de botones estándar, hay que
   exportar también esos módulos y cruzarlos. El XML ya dice de dónde hereda cada objeto
   (`<SubclassedFrom module="UP_BASE" object="OBJ_GENERAL"/>`).
3. **Exportar menús y librerías** (`.mmb`→`.mmt`, `.pll`→`.pld`). El script ya los contempla pero en la
   carpeta actual solo hay `.fmt` — o no se corrió sobre ellos, o el aplicativo no los tiene aparte.
   Confirmar con el usuario: las librerías `.pll` suelen tener mucha lógica compartida.
4. **Seguir mapeando códigos de propiedad**, si la migración los necesita. Quedan sin nombrar los
   códigos de flags booleanos (Required/Enabled/Visible/Insert-Update-Query Allowed), que son muchos
   `B<n>` con valores 0/1. La metodología que funcionó está documentada en `fmt-format-notes.md`
   ("Cómo se identificaron los códigos"): correlacionar contra evidencia independiente, nunca adivinar.
5. **Investigar `frmxmltools.jar`** (utilidad oficial `Forms2XML` de Oracle) — ver
   `credits-and-references.md`. Con el camino `.fmt` ya rindiendo al 99,99% esto bajó bastante de
   prioridad; serviría sobre todo como verificación cruzada independiente.
6. **Recuperar lógica de los `.fmx` huérfanos** — **ahora sí hace falta**: existen esos módulos. Ver
   `plan-fmb-fmx.md`, Fase B. El bytecode Diana (`fmx-bytecode-notes.md`) queda como último escalón, y con
   un alcance mucho menor del que se pensaba gracias al emparejamiento por corpus.
7. **Estructura del `.fmx`** en `info.phosco.forms.translate` — probablemente ya no vale la pena; el
   `.fmt` da todo esto mejor. Confirmar con el usuario antes de retomarlo.

## Estado de git

El trabajo de la iteración anterior sí está commiteado (último commit: "added scripts to automated export
of fmt and context for claude code in the next iteration"). **Lo de esta iteración está sin commitear**:
`FmtSchema.java`, `FmtSourceDecoder.java`, `FmtXmlExporter.java`, `FmtToXml.java`, más cambios en
`FmtObjectGraph.java` (método `allObjects()`), `XmlWriter.java` (arreglo de indentación en texto) y estos
docs. Nadie ha pedido commit — confirmar con el usuario.

## Cómo verificar que todo sigue en pie

```
cd "c:\Users\leoni\source\Web\FMX-6-Transpiler"
rm -rf build && mkdir -p build
javac -d build -cp src $(find src -name "*.java")

# un formulario
java -cp build info.phosco.forms.fmt.FmtToXml "...\FMX\FMT\cr_cierr\cr_cierr.fmt" salida.xml

# el aplicativo completo (~3 min, necesita -Xmx6g por los .fmt de 12 MB)
java -Xmx6g -cp build info.phosco.forms.fmt.FmtToXml "...\FMX\FMT" "...\FMX\XML"
```

La última línea del lote debe decir `Convertidos: 176 | Fallidos: 0` y `huerfanas=0`. Si `huerfanas`
sube o `respaldo` crece mucho, algo se rompió: son las dos señales de alarma del extractor.
Con `--brief` se omiten las propiedades sin identificar (XML mucho más chico y legible, pero ya no es
una vista completa del archivo).
