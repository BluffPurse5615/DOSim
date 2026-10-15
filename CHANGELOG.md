# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Todavía no hay versiones publicadas formalmente (el proyecto está en Alpha interna), así que por ahora todo vive bajo **[Sin publicar]**.

## [0.1.0-alpha] — MS-DOS 1.25, Alpha interna

### Added

**Arquitectura base**
- Motor `ReSimCore`: monta y administra unidades virtuales
- `ReSim-FS`: sistema de archivos virtual respaldado en almacenamiento real del dispositivo (`Android/data/<paquete>/files/ReSim-FS/A`), con atributos DOS, fechas de creación/modificación y persistencia real
- `DOS Runtime`: Command Interpreter, Program Loader, Process Manager, Environment Manager
- `resimdos-boot`: secuencia de arranque (COMMAND.COM, comandos externos, AUTOEXEC.BAT)
- Launcher con dos pantallas: selector de sistemas y consola de DOS

**Comandos de COMMAND.COM (DOS 1.25)**
- Internos: `DIR`, `COPY`, `DEL`/`ERASE`, `REN`/`RENAME`, `TYPE`, `DATE`, `TIME`, `REM`, `PAUSE`
- Externos (como archivos reales en el disco; implementación real pendiente): `FORMAT`, `DISKCOPY`, `DISKCOMP`, `CHKDSK`, `MODE`, `SYS`, `DEBUG`, `LINK`, `EXE2BIN`
- Redirección de salida a archivo con `>` y `>>`
- `BatchProgram`: ejecución de `.BAT` línea por línea, con `ECHO ON`/`ECHO OFF`, soporte de `@` para silenciar una línea, sustitución de `%0`–`%9`, y capacidad de lanzar programas externos cuando una línea no es un comando interno
- Mecanismo genérico `FILETYPE=BAT`: cualquier archivo (`.COM`, `.EXE`, lo que sea) puede declararse ejecutable línea por línea con esa línea al principio, sin depender de su extensión ni de reconocer el nombre del archivo
- `AUTOEXEC.BAT` se ejecuta al arrancar si existe, antes de mostrar el prompt
- Arranque interactivo: fecha y hora reales del dispositivo, escritas con animación letra por letra (como una PC real de la época, sin reloj con batería)

**Interfaz**
- Consola de una sola caja de texto continua (no un campo de entrada separado): se escribe pegado directamente después del prompt, como una terminal real
- Protección real del historial: si algo borra o modifica el texto ya impreso, se restaura al instante
- Fuente `dos-vga.ttf` de época, con cálculo automático de tamaño (`computeConsoleTextSizePx`) para que el texto siempre ocupe el número de columnas objetivo dentro del lienzo virtual, sin depender de un valor fijo adivinado
- "Monitor" de resolución fija (CGA 320×200): la consola se mide y dibuja en esa resolución exacta y luego se escala para llenar la pantalla real, como un framebuffer de emulador retro — con franjas negras donde no encaje
- Pantalla completa inmersiva (oculta barra de estado y de navegación)
- Modo "anti-DPI": la interfaz usa siempre la misma densidad de referencia, así que no cambia de tamaño entre dispositivos de distinto DPI
- Manejador de crashes propio (`CrashHandler`), que guarda el último error para poder revisarlo sin depender de herramientas externas

### Changed

- Rebase completo a **MS-DOS 1.25 real** (ver sección dedicada más abajo) — cambió la unidad (`A:` en vez de `C:`), el prompt (`A:`), la ausencia de subdirectorios, y la lista completa de comandos
- `resimdos-shell` y `resimdos-api` se fusionaron dentro de `resimdos-runtime`
- El módulo `resimsdk` original (con su lenguaje `ReSimScript`/`.SIM`) se retiró; en su lugar se dejó preparado `resimsdk/win1sdk`, vacío, para el futuro SDK de Windows 1.0
- El módulo `app` (plantilla sin usar) se eliminó del proyecto
- La app arranca directo en la consola de DOS, sin pasar por la pantalla de selección de sistemas
- `COMMAND.COM` es texto plano con comandos reales de DOS 1.25 (se evaluó usar el binario real de MS-DOS, pero se descartó a favor de que el motor lo ejecute de verdad)

### Fixed

- Crash al usar `DATE`/`TIME` una segunda vez (la respuesta a "Enter new time:" se interpretaba como un comando nuevo en vez de como respuesta)
- `VirtualDrive` no encontraba archivos reales si su mayúscula/minúscula no coincidía exactamente con la ruta DOS
- `DIR` contaba los directorios como si fueran archivos en el conteo final
- `COPY` permitía copiar un archivo sobre sí mismo
- `DATE`/`TIME` aceptaban cualquier texto sin validar el formato
- Varias regresiones de formato de prompt (`A:\>` reapareciendo en lugar de `A:`) al sincronizar código entre sesiones
- `NullPointerException` al activar pantalla completa antes de que la vista de la actividad existiera

### Scope / decisiones de alcance

- **No hay CPU 8086 real.** Se evaluó construir uno (registros, decodificador de instrucciones x86, memoria segmentada, `INT 21h`) para ejecutar binarios `.COM` auténticos, y se descartó: es un proyecto del tamaño de un emulador completo (DOSBox), no algo incremental. El proyecto se mantiene como recreación conceptual en Kotlin.
- Los comandos externos (`FORMAT`, `CHKDSK`, etc.) existen como archivos reales pero siguen siendo *stubs* — confirman que se "ejecutaron" sin hacer su función real todavía.
- La capacidad del disquete virtual usa el espacio libre real del dispositivo en vez de simular 160/320 KB auténticos de un disquete de la época (pendiente, no bloqueante).
