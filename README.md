# DOSim

> Una recreación conceptual de sistemas operativos históricos para Android, empezando por **MS-DOS 1.25**.

![Estado](https://img.shields.io/badge/estado-alpha%20interna-yellow)
![Plataforma](https://img.shields.io/badge/plataforma-Android-3DDC84)
![Lenguaje](https://img.shields.io/badge/lenguaje-Kotlin-7F52FF)
 
---

## Qué es esto

**DOSim** recrea sistemas operativos históricos dentro de una sola app de Android, sin emular hardware real. La primera etapa es **ReSim DOS**, una recreación funcional de MS-DOS 1.25: una consola, un sistema de archivos virtual respaldado por archivos reales del dispositivo, y un intérprete de comandos que se comporta como el COMMAND.COM de la época.

El objetivo no es levantar un emulador de PC (no hay CPU 8086, ni BIOS, ni FAT12 real) sino **recrear el comportamiento y la sensación** de usar esos sistemas, con una arquitectura pensada para poder sumar después otras recreaciones (Windows 1.0 es la siguiente en el roadmap) sobre el mismo motor.

## Qué NO es

Para que quede claro desde el README: esto **no ejecuta binarios x86 reales**. No hay decodificador de instrucciones, ni registros de CPU, ni `INT 21h` real. Un archivo `.COM` existe como archivo real en el disco virtual y puede "ejecutarse", pero el programa de verdad corre como código Kotlin (o, si el archivo trae la línea `FILETYPE=BAT` al principio, como un script interpretado línea por línea). Esta es una decisión de alcance consciente, no una limitación temporal.

## Estado actual

**MS-DOS 1.25 — Alpha interna.** Arranca, monta el disco, corre AUTOEXEC.BAT si existe, y soporta los comandos internos reales de esa versión de DOS. Los comandos externos (`FORMAT`, `CHKDSK`, etc.) existen como archivos reales, pero todavía son *stubs* sin implementación real.



### Módulos

| Módulo | Responsabilidad |
|---|---|
| `resimcore` | Monta y administra las unidades virtuales |
| `resimdos-fs` | Sistema de archivos virtual: rutas, atributos DOS, persistencia real |
| `resimdos-runtime` | Intérprete de comandos, Program Loader, procesos, Environment Manager |
| `resimdos-boot` | Secuencia de arranque: asegura COMMAND.COM/comandos externos, corre AUTOEXEC.BAT |
| `resimsdk` | Vacío por ahora — reservado para el futuro SDK de Windows 1.0 (`win1sdk`) |
| `launcher` | Las dos pantallas: selector de sistemas y la consola de DOS |

## Comandos soportados (DOS 1.25)

**Internos** (en `COMMAND.COM`): `DIR`, `COPY`, `DEL`/`ERASE`, `REN`/`RENAME`, `TYPE`, `DATE`, `TIME`, `REM`, `PAUSE`

**Externos** (archivos reales en el disco, todavía sin implementación real): `FORMAT`, `DISKCOPY`, `DISKCOMP`, `CHKDSK`, `MODE`, `SYS`, `DEBUG`, `LINK`, `EXE2BIN`

Deliberadamente, **no** están `CD`, `MD`, `RD`, `PATH`, `VER`, `VOL`, `SET`, `ECHO`, `CLS`, `MOVE` — son de versiones de DOS posteriores a 1.25 (DOS 1.x no tenía subdirectorios).

## Fidelidad histórica

- Unidad `A:` (disquete, sin disco duro — DOS 1.x es de esa época)
- Sin subdirectorios: el sistema de archivos virtual se fuerza a una raíz plana
- Nombres de archivo 8.3 reales (máximo 8 + 3 caracteres)
- Resolución de archivos sin distinguir mayúsculas/minúsculas, como DOS reales
- Arranque interactivo pidiendo fecha y hora (como las PC de esa época, sin reloj con batería)
- `AUTOEXEC.BAT` se ejecuta al arrancar si existe (confirmado contra el código fuente histórico de MS-DOS 1.25); `CONFIG.SYS` no existe todavía en esa versión
## Compilar

El proyecto se desarrolla en **Android Studio** y muy pocas veces en **CodeAssist**, directamente en un dispositivo **Android(con CodeAssist)** o en **PC(con Android Studio)**., usando su formato de proyecto propio (`.caproj`, módulos definidos por `module.toml`). También se exporta como proyecto **Gradle** estándar (AGP 8.13.0, Kotlin 2.4.0) para compatibilidad con otras herramientas, aunque esa vía de compilación todavía no se ha validado de punta a punta.

## Roadmap

- [ ] Terminar las utilidades externas (`FORMAT`, `CHKDSK`, etc.) con comportamiento real
- [ ] `win1sdk`: SDK para crear aplicaciones, empezando por DOS y pensado para escalar a interfaces gráficas
- [ ] Windows 1.0 sobre el mismo `ReSimCore`, arrancado desde `WIN.COM`
- [ ] Versiones posteriores (95, XP, 7) una vez que el núcleo esté estable

## Licencia

- Licencia de código fuente: GPL-3.0

## Autor

Keyler — proyecto personal, desarrollado en solitario.
 +