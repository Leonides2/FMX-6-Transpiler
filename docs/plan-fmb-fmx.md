# Plan: qué tanta información se puede sacar del `.fmx`, del `.fmb` y del `.fmt`

Documento de planificación, **no implementado todavía**. Propone los pasos, el método de validación y el
costo/beneficio de cada uno, para revisarlos antes de escribir código.

## 0. El objetivo y por qué esto no es opcional

**Meta**: un aplicativo open source que extraiga la mayor cantidad posible de información de un `.fmx`,
un `.fmb` y un `.fmt`, para migrar pantallas y lógica a tecnologías modernas con asistencia de IA.

**Restricción dura, y es la que manda el plan**: hay módulos **en producción cuyo `.fmb` se perdió**.
De esos solo queda el `.fmx` compilado. No es un ejercicio de curiosidad: si no se recupera lo que hay
dentro de esos `.fmx`, esa lógica no se puede migrar y punto.

Eso invierte el orden que tenía la versión anterior de este plan. La ruta `.fmx` deja de ser "el último
recurso que probablemente no valga la pena" y pasa a ser **el camino crítico**. La ruta `.fmb` baja a
segundo lugar: para un módulo cuyo `.fmb` se perdió, un parser de `.fmb` no sirve de nada.

Con eso, los módulos se parten en tres grupos y cada uno tiene su ruta:

| grupo | qué hay | ruta | estado |
|---|---|---|---|
| A | `.fmb` + `.fmt` generado | `.fmt` | **resuelto hoy**, 176 módulos |
| B | `.fmb` pero el `.fmt` no se pudo generar | parser `.fmb` (Fase A) | pendiente |
| C | **solo `.fmx`** (fuente perdido) | recuperación desde `.fmx` (Fase B) | **pendiente y crítico** |

---

## 1. La tesis: esto ya no es exploración, es decodificación supervisada

Es el cambio más importante y de él depende todo el plan.

**Antes** del trabajo con `.fmt`, atacar el binario era exploración a ciegas: mirar bytes, inventar una
heurística y esperar que generalizara. La única forma de saber si funcionaba era que alguien transcribiera
un formulario a mano.

**Ahora** hay ground truth completo y automático de 176 módulos: 187.169 objetos con su id, tipo, dueño y
todas sus propiedades, más 11.969 triggers y 5.612 program units con su PL/SQL exacto. Eso convierte el
problema en uno supervisado, y en dos sentidos distintos:

- **Los 176 módulos del grupo A son el conjunto de calibración.** Para casi todos ellos existe también su
  `.fmx`. Entonces se puede construir y afinar un decodificador de `.fmx` contra módulos donde ya sabemos
  la respuesta, y recién después aplicarlo a los módulos huérfanos del grupo C. Nunca hay que adivinar si
  funcionó.
- **El criterio de aceptación deja de ser subjetivo.** La salida de cualquier ruta nueva debe reproducir
  el XML que ya produce la ruta `.fmt` para el mismo módulo. Lo decide un `diff`, no una opinión.

**Dependencia crítica que hay que confirmar ya**: hacen falta los `.fmx` **de los 176 módulos que sí
tienen `.fmt`**, no solo los de los huérfanos. Sin ese conjunto de calibración, la Fase B vuelve a ser
exploración a ciegas y se encarece un orden de magnitud.

## 2. El hallazgo que cambia la estrategia: el corpus Rosetta

Medido sobre los 176 XML ya extraídos:

| | |
|---|---|
| triggers con fuente | 11.969 |
| **fuentes distintas** | **3.275** (ratio de duplicación 3,7×) |
| fuentes que aparecen en 2 o más módulos | 1.190 → **cubren 9.665 triggers, el 81%** |

Los más repetidos son de barra de herramientas y navegación estándar: `do_key('delete_record');` aparece
176 veces en **171 módulos distintos**; `null;` 231 veces en 56 módulos. Es la consecuencia natural de que
todo el aplicativo se construyó heredando de la misma librería de objetos (`UP_BASE`/`OBJ_GENERAL`).

**Implicación**: para un módulo huérfano del grupo C, cerca del 80% de sus triggers son casi con certeza
idénticos a triggers cuyo fuente **ya tenemos**. Entonces la pregunta deja de ser "¿cómo decodifico el
bytecode Diana?" y pasa a ser **"¿cómo emparejo el bytecode de este trigger con el de un trigger cuyo
fuente ya conozco?"**. Emparejar es enormemente más barato que decodificar: no hace falta entender un
solo opcode, basta con que el mismo fuente compile a los mismos bytes.

Eso no resuelve el 100%: el ~19% restante es justamente la lógica específica de cada módulo, que es la más
valiosa para migrar. Pero cambia el problema de "decodificar un bytecode propietario entero" a
"decodificar el resto, con miles de pares (fuente, bytecode) ya verificados como punto de partida".

## 3. Qué sobrevive realmente en cada formato

Verificado directamente sobre `cr_cierr.fmx` contra su XML de referencia:

| | `.fmt` | `.fmb` | `.fmx` |
|---|---|---|---|
| árbol real de objetos | sí | por decodificar | parcial (hay parser de bloques) |
| **nombres de bloque, ítem, canvas, ventana** | sí | sí | **sí** — confirmado |
| propiedades y geometría | sí | por decodificar | por decodificar |
| etiquetas y textos visibles | sí | sí | **sí** (`Realizar Cierre` está en el `.fmx`) |
| **nombres de trigger** | sí | sí | **no** — 0 ocurrencias de `WHEN-BUTTON-PRESSED`, `KEY-EXIT`, etc. |
| texto del PL/SQL | sí | sí | **no** (bytecode) |
| literales de texto del PL/SQL | sí | sí | sí, formato `[4 bytes LE][texto]` confirmado |

La fila que más importa para el objetivo del usuario: **las pantallas sí están en el `.fmx`.** Los nombres
de bloques, ítems, canvas y ventanas sobreviven a la compilación porque son datos, no código. Lo que se
pierde es el *texto* del código.

La otra fila clave: los nombres de trigger no aparecen como texto. Casi seguro están como **códigos
numéricos** (el `.fmx` tiene que saber qué trigger disparar, pero no necesita su nombre legible). Eso es
una hipótesis concreta y comprobable, ver B3.

## 4. Restricciones de licencia y método (open source)

El objetivo es distribuir esto como open source sin código propietario. Tres consecuencias prácticas:

1. **La base actual está limpia**: el fork heredado es **MIT** (© 2019 André Rothe). Compatible con
   cualquier licencia permisiva; solo hay que conservar el aviso de copyright. Todo lo escrito en este
   proyecto (`fmt/`, `fmb/`, `xml/`) es propio.
2. **No vendorizar nada de Oracle.** `frmxmltools.jar`/`frmjdapi.jar` (la utilidad oficial `Forms2XML`)
   **no se pueden redistribuir**. Se pueden usar en local como verificación cruzada independiente si el
   usuario tiene una instalación con licencia, pero no pueden ser dependencia del aplicativo ni entrar al
   repo. Conviene bajarlo de prioridad justamente por esto.
3. **Mantener el método del lado seguro y documentarlo.** Todo lo hecho hasta ahora analiza **archivos de
   datos propios del usuario** (`.fmt`, `.fmb`, `.fmx` de su propia aplicación), comparándolos contra
   fuentes que el propio usuario posee. Eso es análisis de datos propios para interoperabilidad, muy
   distinto de desensamblar los ejecutables de Oracle (`ifrun60.exe` y compañía) — cosa que este plan
   **no propone y conviene no hacer**. Vale la pena dejarlo escrito en el repo: la metodología es
   diferencial sobre datos propios, nunca ingeniería inversa del runtime. Si en algún momento se
   distribuye comercialmente, ahí sí conviene una revisión legal real; esto es una postura de ingeniería,
   no asesoría jurídica.

---

## 5. Fase 0 — Inventario (bloqueante, primero)

**Qué**: una herramienta que cruce por nombre de módulo los conjuntos disponibles: `.fmb`, `.fmx`, `.pll`,
`.mmb`, y los `.fmt`/`.pld`/`.mmt` ya generados. Salida: los tres grupos A/B/C de la sección 0, nombrados.

**Qué decide**:

- **La lista concreta del grupo C** (huérfanos con solo `.fmx`). Es el alcance real del trabajo crítico, y
  su tamaño decide cuánto esfuerzo justifica. No es lo mismo 3 módulos que 40.
- **Si existe el conjunto de calibración**: cuántos de los 176 módulos con `.fmt` tienen también su `.fmx`.
- **Si el grupo B existe** (Fase A justificada o no).
- **Si hay `.pll`/`.mmb` sin exportar** → ganancia barata inmediata, ver abajo.

**Costo**: horas. **Dependencia**: acceso al corpus completo, que hoy no está en esta máquina.

### Ganancia barata, antes que cualquier ingeniería inversa

Las **librerías `.pll` y los menús `.mmb`**. Las librerías concentran la lógica compartida del aplicativo
(se ven adjuntas `MENSAJE`, `IN_LIB`, `D2KWUTIL`, `CR_LIB`, `HTML_HELP`) y hoy no hay ni un `.pld`
exportado. El `.pld` y el `.mmt` son el mismo formato ROS Script del `.fmt`, así que la hipótesis es que
**`FmtParser` los parsea sin cambios** y solo faltaría mapear los tipos de objeto propios de librería y
menú. Horas de trabajo por, posiblemente, una porción grande de la lógica de negocio que hoy no está
extraída — y además **amplía el corpus Rosetta**, que es lo que hace funcionar la Fase B.

---

## 6. Fase B — el `.fmx` (camino crítico)

Objetivo: sacar lo máximo posible de un módulo del que **solo** existe el `.fmx`. Está ordenada de mayor a
menor relación valor/esfuerzo, y **cada escalón entrega valor por sí solo** — no hay que llegar al final
para que sirva.

### B1 — Estructura de pantalla

**Qué**: recuperar bloques, ítems, canvas, ventanas, etiquetas y geometría. Ya está confirmado que los
nombres sobreviven, y el parser heredado en `translate` ya extrae data blocks.

**Método supervisado**: tomar módulos del grupo A (con `.fmx` y `.fmt`), medir qué recupera hoy
`translate` contra el XML de referencia, y extender el parser hasta que el árbol coincida. La misma
técnica de "dato conocido → buscar su huella" que funcionó con el `.fmt`, pero ahora con 187k datos
conocidos en vez de un puñado.

**Por qué primero**: es exactamente el objetivo declarado del usuario — **migrar pantallas**. Y es la
parte del `.fmx` que *no* se perdió en la compilación. Empezar por aquí entrega la pantalla completa de
los módulos huérfanos aunque nunca se decodifique un solo opcode.

### B2 — Literales de texto

**Qué**: implementar el escaneo `[4 bytes LE = longitud][texto]`, ya confirmado en dos archivos, y emitir
la secuencia ordenada de literales por módulo.

**Dos usos, ninguno requiere entender el bytecode**:

1. Recuperar textos de negocio (mensajes, títulos, etiquetas) de los módulos huérfanos.
2. **Huella digital para emparejar un `.fmx` con su fuente.** Comparar la secuencia de literales de un
   `.fmx` contra la del PL/SQL ya extraído de los 176 módulos responde una pregunta operativa de riesgo:
   *¿el `.fmx` que corre en producción corresponde al `.fmb` que tenemos?* Y de paso **verifica la lista
   de huérfanos** de la Fase 0: puede que algún módulo que parece huérfano en realidad corresponda a un
   `.fmb` guardado con otro nombre.

**Costo**: bajo, el formato ya está confirmado.

### B3 — Inventario de triggers (hipótesis a comprobar)

**La hipótesis**: los nombres de trigger no están como texto porque están como **códigos numéricos**. El
runtime necesita saber qué disparar y cuándo, así que en algún lado tiene que haber una tabla que asocie
`(objeto, código de trigger) → offset de bytecode`.

**Cómo comprobarla, supervisado**: en un módulo del grupo A sabemos exactamente qué triggers tiene cada
objeto (el `.fmt` lo dice: p. ej. `BLK_DATOS` tiene 6, y `BUT_ACEPTAR` tiene `WHEN-BUTTON-PRESSED`). Se
busca en el `.fmx` una tabla cuya cardinalidad y agrupación reproduzcan ese inventario conocido. Como en
todo el corpus solo hay **83 nombres de trigger distintos**, el código debería ser un entero pequeño, y
una vez emparejado un módulo, el mapa código→nombre vale para todos.

**Valor si se confirma**: para un módulo huérfano se recupera **qué triggers existen y sobre qué objeto**,
aunque no se sepa aún qué hacen. Para migrar asistido con IA eso ya es mucho: se sabe que ese botón tiene
un `WHEN-BUTTON-PRESSED` y que ese bloque tiene un `WHEN-VALIDATE-RECORD`, es decir el esqueleto de
comportamiento de la pantalla. Además es el prerrequisito de B4: sin saber dónde empieza y termina el
bytecode de cada trigger, no hay nada que emparejar.

### B4 — Recuperar el fuente por emparejamiento (el paso de mayor retorno)

**Qué**: construir, desde los módulos del grupo A, una tabla `bytecode → fuente PL/SQL`. Para un módulo
huérfano, se toma el bytecode de cada trigger (delimitado gracias a B3), se busca en la tabla, y si hay
coincidencia **se recupera el fuente exacto, verbatim, sin decodificar nada**.

**Por qué puede cubrir tanto**: por la sección 2 — el 81% de los triggers del aplicativo tiene un fuente
que se repite entre módulos. Si el mismo fuente compila a los mismos bytes, ese 81% se recupera de una.

**El riesgo técnico a resolver, y cómo**: es posible que el bytecode no sea idéntico entre módulos porque
embeba cosas locales (ids de ítem, offsets, índices al pool de literales). Se mide primero: tomar dos
módulos del grupo A que compartan un trigger de fuente idéntico y comparar sus bytecodes. Tres
escenarios, todos con salida útil:

- **Idénticos** → emparejamiento exacto por hash. Ideal.
- **Idénticos salvo campos concretos** → se normalizan esos campos (se enmascaran) y se empareja igual.
  Además, saber *qué* campos varían ya enseña dónde viven las referencias a objetos en el bytecode, que es
  información directamente aprovechable en B5.
- **Muy distintos** → se cae a emparejamiento aproximado (similitud + secuencia de literales de B2), que
  da candidatos con nivel de confianza en vez de certeza. Sigue siendo útil para una migración asistida
  por IA, siempre que la salida diga que es un candidato y no un hecho.

**Costo**: medio. **Retorno**: potencialmente ~80% de la lógica de los módulos huérfanos sin tocar un
opcode. Es, con diferencia, el mejor negocio de todo el plan.

### B5 — Decodificar el bytecode Diana (lo que quede)

Ahora sí, pero con un alcance mucho menor y un arranque en caliente: solo el ~19% sin coincidencia, y con
miles de pares `(fuente, bytecode)` ya alineados y **verificados** por B4.

**Técnicas, en orden**:

1. **Línea base por triggers triviales.** Hay 231 triggers cuyo fuente es exactamente `null;`. Su bytecode
   es prácticamente solo el envoltorio. Diferenciar dos de ellos aísla el envoltorio antes de intentar
   nada complejo.
2. **Pares diferenciales minados automáticamente.** Con 3.275 fuentes distintos se pueden **buscar por
   programa** pares que difieran en un solo token (mismo built-in con distinto argumento, mismo `IF` con
   distinta condición). Cada par es un experimento controlado que aísla qué bytes codifican esa pieza.
   Antes había que construir esos casos a mano; ahora se extraen del corpus real por miles.
3. **Tabla de opcodes por frecuencia**, empezando por los built-ins más repetidos (`do_key`,
   `set_item_property`, `go_item`, `exit_form`), que son los que más ejemplos dan y más cobertura suman.

**Criterio de parada, definido de antemano**: si tras un esfuerzo acotado la tabla no cubre los built-ins
más frecuentes, se detiene, se documenta lo aprendido y se entrega lo que B1–B4 ya dieron. Un decoder
aproximado de los patrones comunes es meta realista; el 100% no lo es. Para los casos sin resolver, la
salida debe decirlo explícitamente en vez de inventar.

---

## 7. Fase A — el `.fmb` (segundo lugar, no primero)

Sigue valiendo la pena, pero **solo para el grupo B** (módulos con `.fmb` cuyo `.fmt` no se pudo generar,
p. ej. porque `ifcmp60` compila contra la base y falla por tablas o permisos faltantes). Para un módulo
cuyo `.fmb` se perdió no aporta nada, y ese es hoy el problema urgente.

**Techo teórico**: el `.fmb` es el archivo de diseño y contiene **todo** — es de donde sale el `.fmt`. Un
parser completo daría exactamente lo mismo que la ruta `.fmt`, sin depender de Forms Builder ni de una
conexión a la base. Ese es el premio, y es la razón por la que a largo plazo conviene tenerlo.

- **A1 — Medir la brecha.** Un reporte que compare lo que la ruta `.fmb` recupera hoy contra el XML de
  referencia, por tipo de objeto, y el porcentaje de coincidencia exacta de texto en triggers y program
  units. Hoy el "validado 100%" descansa en una transcripción manual de un bloque de un formulario; ahora
  se puede medir de verdad. Barato, y no se debe planificar el resto sin este número.
- **A2 — Hipótesis del nombre duplicado.** Que "todo nombre aparece dos veces pegado" no sea una
  curiosidad sino que **cada objeto guarde su nombre dos veces por razones estructurales**, igual que en
  el `.fmt`: una en la entrada del directorio (`IDFOS_NAM`) y otra como la propiedad de texto 211
  (`Name`). Si se confirma, la heurística se vuelve regla estructural y —más valioso— los ~40 bytes "de
  ruido" entre las dos copias dejan de ser ruido: son candidatos concretos al id, el tipo y el dueño.
  Máximo apalancamiento por costo de toda la Fase A.
- **A3 — Localizar el directorio de objetos.** Los ids son enteros pequeños y consecutivos (1..566 en
  `cr_cierr`); una secuencia larga de enteros crecientes es una firma muy distintiva en un binario. Se
  valida exigiendo que tipo y dueño de cada id coincidan con los ya conocidos para los 566 objetos:
  acertar por azar en 566 tripletas es imposible.
- **A4 — Decodificar las tablas de propiedades.** Se sabe que el objeto #45 tiene exactamente los códigos
  `{211, 500, 242, 42, 321, 314}`; se buscan como enteros de 2 y 4 bytes en la región que A2/A3 acotaron.
  Como los códigos ya están mapeados en `FmtSchema`, **no hay una segunda fase de "y ahora qué significa
  cada uno"**.
- **Plan B si A3/A4 no ceden**: no insistir. Con A1, A2 y los `owner` de los triggers
  (`"NOMBRE (BLOQUE.ITEM)"`, que ya se extraen) se puede armar un árbol parcial pero real, mucho mejor que
  la lista plana de hoy.

**Criterio de aceptación**: para los módulos con `.fmb` y `.fmt`, el XML generado desde el `.fmb` es
idéntico al generado desde el `.fmt`, salvo diferencias documentadas y explicadas.

---

## 8. Arquitectura del aplicativo

Para que las tres rutas converjan en algo consumible por una migración asistida con IA:

**Tres lectores, un modelo, un exportador.**

```
.fmt ──> FmtReader ─┐
.fmb ──> FmbReader ─┼──> modelo ROS común ──> exportador ──> XML/JSON por pantalla
.fmx ──> FmxReader ─┘    (objetos, propiedades,
                          triggers, program units)
```

**Decisión de diseño a tomar ya, antes de escribir el segundo lector**: hoy el modelo (`FmtObject`,
`FmtProperty`, `FmtObjectGraph`) vive en el paquete `fmt` y lleva ese prefijo. Si el `.fmb` y el `.fmx` lo
van a producir también, el nombre miente. Propongo moverlo a un paquete neutro —los tres formatos son el
mismo repositorio ROS— tipo `info.phosco.forms.ros` con `RosObject`/`RosGraph`/`RosSchema`, dejando en
`fmt`, `fmb` y `fmx` solo los lectores. Es un refactor mecánico y barato ahora, caro después.

**Cada dato debe cargar su procedencia y su confianza.** Es la decisión de diseño más importante de un
extractor multi-fuente, y hace falta justo por el escenario del usuario: la salida va a mezclar hechos
exactos con candidatos probables, y tanto la IA como la persona necesitan saber cuál es cuál. Por ejemplo:

```xml
<Trigger name="WHEN-BUTTON-PRESSED" source="fmt" confidence="exact">
<Trigger name="WHEN-BUTTON-PRESSED" source="fmx-bytecode-match" confidence="exact"
         matchedFrom="cm_facturas.fmt#BLK_BOTONES.SALIR"/>
<Trigger name="WHEN-VALIDATE-ITEM"  source="fmx-literals" confidence="partial"
         note="solo se recuperaron los literales; la lógica no pudo decodificarse"/>
```

Sin esto, una migración asistida por IA tratará una conjetura como un hecho, que es exactamente el error
más caro y más difícil de detectar después.

**Formato de salida orientado a la IA.** El XML actual es completo pero pesado (92,9 MB en 176 archivos,
con muchas propiedades sin identificar). Para alimentar un LLM conviene además un **paquete por pantalla,
autocontenido**: estructura + su lógica en línea + herencia ya resuelta + procedencia. Es decir, algo
entre el `--brief` actual y el volcado completo. Vale la pena definirlo cuando se sepa a qué tecnología se
migra, porque eso condiciona qué hace falta.

---

## 9. Orden recomendado

1. **Fase 0** — inventario. Bloqueante. En particular: la lista del grupo C y si existen los `.fmx` de los
   176 módulos de calibración.
2. **`.pll` y `.mmb` por la ruta `.fmt` existente.** Máxima ganancia por hora, sin ingeniería inversa
   nueva, y amplía el corpus Rosetta del que depende la Fase B.
3. **B2 — literales del `.fmx`.** Barato, y verifica/corrige la lista de huérfanos antes de invertir en
   ella.
4. **B1 — estructura de pantalla del `.fmx`.** Es el objetivo declarado (migrar pantallas) y la parte que
   no se perdió al compilar.
5. **B3 — inventario de triggers.** Hipótesis barata, y prerrequisito de B4.
6. **B4 — recuperación por emparejamiento.** El mejor retorno del plan: potencialmente ~80% de la lógica
   huérfana sin decodificar opcodes.
7. **A1/A2** — medir la ruta `.fmb` y comprobar la hipótesis del nombre duplicado. Baratas; se pueden
   intercalar cuando la Fase B esté bloqueada esperando algo.
8. **B5 — bytecode Diana** para el resto, con criterio de parada.
9. **A3/A4/A5** — parser completo del `.fmb`, si el grupo B lo justifica.

**Lo que recomiendo no hacer**: depender de `frmxmltools.jar` de Oracle (impide distribuir open source,
ver sección 4), y desensamblar los ejecutables del runtime de Forms.

---

## 10. Preguntas abiertas

1. **¿Cuántos módulos hay en el grupo C** (solo `.fmx`, fuente perdido)? Define el alcance del trabajo
   crítico. Lo responde la Fase 0.
2. **¿Están disponibles los `.fmx` de los 176 módulos que sí tienen `.fmt`?** Es el conjunto de
   calibración; sin él la Fase B se encarece muchísimo. **Es la pregunta más urgente del documento.**
3. **¿Existen los `.pll` y `.mmb`?** Se ven librerías adjuntas en los formularios, así que deberían
   existir.
4. **¿A qué tecnología se migra?** No cambia la extracción, pero sí el formato de salida orientado a IA
   (sección 8) y qué información hace falta priorizar.
5. **¿Hay acceso a la librería de objetos `UP_BASE`?** De ahí heredan sus propiedades el 37% de los ítems,
   y hoy esas propiedades no están en ningún archivo extraído.
