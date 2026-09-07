# fmx.6.decompiler

Extractor de lógica de negocio (triggers PL/SQL, program units, definición de bloques/queries, parámetros, record groups, librerías) desde archivos Oracle Forms 6 `.fmx`, pensado para migrar esa lógica a otro lenguaje/plataforma.

Nace como fork de un decompilador de investigación para Forms 6 que reconstruía la UI completa (canvas, ventanas, fuentes, gráficos). Ese código de UI se eliminó a propósito: aquí el objetivo no es reconstruir el formulario, sino extraer su lógica. No se decodifican imágenes: quedan como referencia/placeholder.

No funciona con todas las funcionalidades de los archivos FMX.6 — varios offsets siguen sin identificar (ver `TODO`s en el código) y es trabajo en curso.

Compílalo con JDK 1.8+. Punto de entrada: `info/phosco/forms/translate/OracleHacker.java` (línea de comandos):

```
java -jar decompiler.jar archivo.fmx <debug|info|warn|off>
```
