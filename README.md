# Discy

A Minecraft 1.20.1 mod for custom music discs. Upload songs at the DJ deck, burn them onto blank discs, and play them in jukeboxes or compatible mods.

**Repository:** [github.com/AlfieeLawrence/Discy-A-custom-Disc-mod-for-Minecraft-](https://github.com/AlfieeLawrence/Discy-A-custom-Disc-mod-for-Minecraft-)

> **Not affiliated with Mojang or Microsoft.** Minecraft is a trademark of Mojang Synergies AB.

## Features

- **DJ Deck** — browse, search, and burn custom audio (OGG/MP3)
- **Custom discs** — per-disc artwork and streaming playback via OpenAL
- **Disc Design Studio** — in-game pixel editor for disc textures
- **Permanent discs** — add baked-in discs via `data/discy/permanent_discs.json`
- **Optional compatibility** — Sophisticated Core jukebox upgrades and Let's Do Furniture gramophones (install those mods separately)

## Installation

### Fabric
1. Install [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Install [Architectury API](https://modrinth.com/mod/architectury-api)
3. Place `discy-fabric-*.jar` in your `mods` folder

### Forge
1. Install [Forge](https://files.minecraftforge.net/) (tested on 47.4.20)
2. Install [Architectury API](https://modrinth.com/mod/architectury-api)
3. Place `discy-forge-*.jar` in your `mods` folder

### Optional compatibility mods

Discy does **not** bundle these mods. Install **official releases** from Modrinth or CurseForge under each mod's own license:

| Mod | Purpose |
|-----|---------|
| [Sophisticated Core](https://modrinth.com/mod/sophisticated-core) + [Storage](https://modrinth.com/mod/sophisticated-storage) / [Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | Jukebox upgrade on backpacks and storage |
| [Let's Do Furniture](https://modrinth.com/mod/lets-do-furniture) | Gramophone playback |

## Building from Source

1. Clone this repository.
2. Create a `libs/` folder and add **compile-only** mod JARs (see [`libs/README.md`](libs/README.md)). Do not commit these files.
3. Run:

```bash
./gradlew build
```

Built JARs are in `fabric/build/libs/` and `forge/build/libs/`.

## Adding a permanent disc

Edit `common/src/main/resources/data/discy/permanent_discs.json` and add assets (`.ogg`, texture, `sounds.json` entry, lang strings). See `permanent_discs.example.json`.

Only include audio and art you have the right to redistribute.

## User-uploaded content

Discy lets players upload their own songs and disc textures at the DJ deck or save designs from the Disc Design Studio. **That content is created by players, not shipped with the mod.**

- You are responsible for having the rights to any audio or images you upload.
- Do not upload copyrighted music or artwork without permission from the rights holder.
- Server owners may wish to set rules for what players may upload.

Discy stores uploads in the world's `discy/` folder (songs, textures, sidecars). Removing content in-game deletes those files on the server.

## Credits & assets

Bundled art in this repository is included with the creators' permission:

| Assets | Credit |
|--------|--------|
| Mod code | [Alfie Lawrence](https://github.com/AlfieeLawrence) |
| Mod icon, DJ Deck block textures, most item textures | Random Nautilus |
| Sap textures, UI textures, Disc Design Studio block textures | Mrdragonclaw |

Runtime-only references to vanilla Minecraft assets (for example the dispenser container GUI used by the DJ Deck screen) are not redistributed in this repository.

## License

Discy mod code is licensed under the **MIT License** — see [LICENSE](LICENSE).

Summary of other terms:

| Component | License | Where |
|-----------|---------|--------|
| Discy source & mod code | MIT | [LICENSE](LICENSE) |
| JLayer (bundled in release JARs) | LGPL 2.1 | [COPYING.LGPL](COPYING.LGPL) |
| All third-party notices | — | [NOTICE](NOTICE) |

`LICENSE`, `NOTICE`, and `COPYING.LGPL` are also packaged inside release JARs.

### Third-party libraries (shipped in Discy JARs)

Discy **bundles** [JLayer](https://github.com/javazoom/jlayer) (`javazoom:jlayer:1.0.1`) for MP3 decoding.

- JLayer is copyright JavaZoom and licensed under **LGPL 2.1**
- Source: [github.com/javazoom/jlayer](https://github.com/javazoom/jlayer) or Maven `javazoom:jlayer:1.0.1`
- Full license text: [COPYING.LGPL](COPYING.LGPL) ([GNU LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html))

Discy itself (excluding bundled third-party libraries) is MIT-licensed.

### Optional mod compatibility (not included)

Discy provides **optional** integration with other mods via mixins and public APIs. Discy does **not**:

- Redistribute, patch, or repackage those mods
- Ship their source code, assets, or JARs
- Include modified versions of their files

Users must install compatible mods from their official authors. Discy only adds small optional mixins and API hooks when those mods are present at runtime.

- **Sophisticated Core / Storage / Backpacks** — copyright P3pp3rF1y, [All Rights Reserved](https://github.com/P3pp3rf1y/SophisticatedCore). Install from official releases only. Discy registers against public APIs (`IDiscHandler`, `SoundHandler`) and does not redistribute or modify Sophisticated files.
- **Let's Do Furniture** — copyright the Furniture mod authors. Install from [official releases](https://modrinth.com/mod/lets-do-furniture) only, under that mod's own license. Discy provides optional gramophone integration via mixin when Furniture is installed and does not redistribute or modify Furniture files.

If you publish forks or derivatives of Discy, keep these mods as separate optional runtime dependencies and retain [NOTICE](NOTICE) and [COPYING.LGPL](COPYING.LGPL) for JLayer.
