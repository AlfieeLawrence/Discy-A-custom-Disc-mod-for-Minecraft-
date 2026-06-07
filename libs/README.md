# libs/

Place compiled mod JARs here for **compile-time compatibility** when building Discy from source.

**Important:** Only place whole `.jar` files here. Never extract, decompile, or commit class files from other mods. The `libs/` folder is gitignored except this README.

Gradle reads from this folder via `fabric/build.gradle` and `forge/build.gradle`.

## Required for building

| File | Used for |
|------|----------|
| `sophisticatedcore.jar` | Sophisticated Core API (compile-only for `:forge` and `:fabric`) |
| `furniture-fabric-1.0.4.jar` | Let's Do Furniture / gramophone (compile-only) |

Obtain these from official Modrinth/CurseForge releases. Discy does not redistribute them.

## Optional (local compat testing)

Keep these locally if you test cross-mod behaviour — not required to compile:

| File | Loader |
|------|--------|
| `sophisticatedcore-fabric.jar` | Fabric (alternate SC build) |
| `amendments-1.20-2.2.5-fabric.jar` | Fabric |
| `amendments-1.20-2.2.5-forge.jar` | Forge |

## Sophisticated Storage in Motion

Use the **official** SSIM release only. Discy does not patch, repackage, or redistribute any Sophisticated mod files.
