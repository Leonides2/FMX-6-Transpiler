# Notas de investigación: formato interno del `.fmb`

Complementa `docs/fmx-bytecode-notes.md` (que es sobre el `.fmx` compilado). Esto es sobre el `.fmb`
(diseño/fuente), que es de donde `info.phosco.forms.fmb` extrae la información real hoy.

## Patrón 1 — records longitud-prefijada (triggers y program units)

Ver `FmbLogicScanner`. Un trigger es:

```
[4 bytes BE = len]"BEGIN\n"
[4 bytes BE = len]<cuerpo PL/SQL literal>
[4 bytes BE = len]"\nEND;" (o "END;")
[4 bytes BE = len]"NOMBRE-TRIGGER (OWNER)"   <- OWNER: "Form" | "BLOQUE" | "BLOQUE.ITEM"
```

Un program unit es distinto (nombre primero, sin envoltorio BEGIN/END separado):

```
[4 bytes BE = len]NOMBRE
[~6 bytes de padding]
[4 bytes BE = len]"PROCEDURE ...END;"  (o "FUNCTION ...END;")
```

Validado 100% contra `cr_cierr_datos.txt` (documento manual del usuario) y en un segundo archivo
(`gl_cierres.fmb`) nunca usado para calibrar nada de esto.

## Patrón 2 — nombres duplicados ("doubled name anchors")

Hallazgo más reciente, mucho más barato que decodificar un esquema de record fijo: **todo nombre de
objeto declarado en el `.fmb` aparece escrito dos veces, pegado** (con como mucho ~40 bytes binarios
entre las dos copias) — nombres de bloque, de ítem, de valores de radio button, de alertas, de labels de
trigger. Ejemplo real (`CR_CIERR.FMB`, hex):

```
...OPERACION_CIERRE\0\0\0\0\0\0\0\0OPERACION_CIERRE\0\0\0\0\0\0\0\0CNV_CIERR\0\0\0\0\0\0\0darkblue...
```

Filtrando el archivo completo por "runs de bytes imprimibles" y quedándose solo con los que (a) tienen
pinta de identificador (letras/dígitos/`_`/`.`, ≥4 caracteres) y (b) aparecen dos veces seguidas, se
recupera **el árbol módulo → bloque → ítem (→ valor de radio) casi completo, en el orden correcto**, sin
tener que entender el resto del record (colores, canvas, fuente, texto de ayuda, que quedan como ruido
alrededor pero no contaminan la detección porque no vienen duplicados).

Implementado en `NameAnchorScanner` / `NameAnchor`. Validado en los dos archivos reales:

- `cr_cierr.fmb`: recuperó `BLK_FORMATO` con sus 14 ítems reales (botones de toolbar, logos, ayuda) y
  `BLK_DATOS` con sus 8 ítems exactos del documento del usuario + las 3 opciones de radio
  (`OPCION_1/4/5`) dentro de `OPERACION_CIERRE`, y `CR_DIAGNOSTICO_CIERRE` con su ítem `NUM_OPERACION`.
- `gl_cierres.fmb` (no usado para calibrar): recuperó `BLK_FORMATO` y `BLK_BOTONES` con sus ítems reales,
  coincidiendo exactamente con lo que ya daban los `owner` de los triggers extraídos por
  `FmbLogicScanner` — confirmación cruzada independiente.

**Limitación**: el escáner da una lista *plana*, en orden de aparición, sin clasificar cada nombre por
rol (bloque vs. ítem vs. valor de radio vs. canvas vs. alerta). La agrupación real (qué ítems pertenecen
a qué bloque) hoy se infiere visualmente/manualmente por cercanía y orden — un bloque, seguido de sus
ítems, hasta el siguiente bloque. Formalizar esa agrupación automáticamente (por ejemplo cruzando contra
los nombres de bloque ya conocidos por `info.phosco.forms.translate`, o detectando algún límite estructural
entre bloques) es el siguiente paso pendiente si se quiere el árbol completo en XML con nesting real en
vez de una lista plana.

También aparece ruido esperado y descartable para el objetivo de "lógica de negocio": nombres de objetos
gráficos de canvas (`GRAPHIC214`, `TEXTSTR72`, `CMPTXT71`, `CNV_MENU`...) — visual puro, sin relación con
PL/SQL.
