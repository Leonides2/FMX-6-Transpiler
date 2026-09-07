# Notas de investigación: cómo el `.fmx` guarda el PL/SQL compilado

Investigación empírica hecha comparando `cr_cierr.fmx`/`cr_cierr.fmb` y `gl_cierres.fmx`/`gl_cierres.fmb`
(pares reales de la aplicación, no archivos de prueba sintéticos). Nada de esto está implementado
todavía como parser — son hallazgos para retomar si algún día se decide invertir en decodificar el
bytecode "Diana" de Oracle Forms 6.

## Contexto

El `.fmx` (runtime compilado) **no** guarda el texto PL/SQL de triggers/program units. Se compila a un
bytecode propietario y sin documentar ("Diana" p-code). El `.fmb` (diseño/fuente) sí guarda el texto
completo, verbatim — ver `info.phosco.forms.fmb` en este mismo repo, que ya lo extrae con éxito.

## Lo confirmado sobre el `.fmx`

Cada **literal de texto** (string) que el PL/SQL compilado usa como argumento de un built-in (o en una
comparación) sí sobrevive en el `.fmx`, con este formato:

```
[4 bytes, entero LITTLE-ENDIAN = longitud del texto][el texto, tal cual, sin terminador]
```

Validado byte-exacto, de forma independiente, en dos archivos distintos:

- `cr_cierr.fmx`, trigger `WHEN-NEW-BLOCK-INSTANCE (BLK_DATOS)`: se encontraron, en el mismo orden en
  que aparecen en el código fuente real (`.fmb`), los literales `global.traslado` (15),
  `Operación a Realizar` (20), `cierre diario` (13), `SI` (2), `blk_datos.fec_cierre`,
  `Traslado Contable` (17), `traslado`, `blk_datos.operacion_cierre`, `Fecha traslado` (14),
  `blk_datos.but_aceptar`, `Realizar traslado`, `WND_INICIAL`, `Sistema de Crédito...` — cada uno con
  su longitud de 4 bytes LE exacta inmediatamente antes.
- `gl_cierres.fmx` (archivo no usado para calibrar nada de esto): el literal
  `'Error al crear la lista de parámetros'` de un `MENSAJE(...)` aparece con longitud 37 codificada en
  los 4 bytes anteriores — exactamente su longitud real.

Entre un literal y el siguiente hay una cantidad **variable** de bytes (se observó 1, 3 y 4 en la misma
secuencia) — no es padding de alineación fijo. Eso es señal de que ahí vive bytecode real de longitud
variable: el opcode del built-in llamado, operadores de comparación, referencias a `:bloque.item` cuando
se usan como variable (no como argumento string), literales numéricos, control de flujo (`if/then/end if`).

**Built-ins, palabras clave y referencias de variable NO dejan rastro como texto.** Confirmado con cero
coincidencias en ambos `.fmx` para: `message_level`, `default_value`, `go_item`, `set_item_property`,
`no_validate`. Un trigger sin ningún literal de texto (p. ej. `KEY-EXIT: exit_form(no_validate);`) es
**completamente invisible** en el `.fmx` — ni su nombre ni su existencia se pueden inferir sin el `.fmb`.

## Qué se puede sacar del `.fmx` hoy, sin decodificar el bytecode

Por cada trigger que sí usa literales de texto: la **secuencia ordenada** de esos literales. Sirve para:
- Verificar que un `.fmb` dado corresponde de verdad al `.fmx` desplegado (fingerprint por orden de literales).
- Recuperar textos de negocio (mensajes, labels, títulos) cuando *solo* se tiene el `.fmx`.

No sirve para reconstruir la lógica (qué built-in, en qué orden, con qué control de flujo).

## Camino a futuro: ¿decodificar el bytecode Diana?

Es técnicamente viable con suficientes ejemplos reales — y la aplicación completa (todos los `.fmb`/`.fmx`
del proyecto real, no solo estos dos) es justo el tipo de corpus que lo hace tratable:

1. **Alineación conocida**: para cada trigger ya se tiene el PL/SQL exacto (`.fmb`) y la posición de su
   bytecode en el `.fmx` (entre el registro del trigger y el siguiente literal/registro) → miles de pares
   `(fuente real, bytes compilados)`.
2. **Diferencial dirigido**: comparar triggers que difieran en una sola cosa (mismo built-in con distinto
   argumento; mismo `IF` con distinta condición) para aislar qué byte(s) codifican esa pieza.
3. **Tabla de opcodes incremental**: igual que se reversearon offset por offset las estructuras del `.fmx`
   en este proyecto, aquí sería opcode por opcode — empezando por los built-ins más frecuentes
   (`go_item`, `set_item_property`, `exit_form`, etc.) por ser los que más ejemplos repetidos dan.

Es un proyecto de varias semanas, no de horas, y probablemente nunca llegue al 100% (built-ins raros,
casos borde) — pero un decoder *aproximado* para los patrones más comunes es una meta realista dado el
volumen de ejemplos disponible.
