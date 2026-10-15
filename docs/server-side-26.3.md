# RoadWeaver for Minecraft 26.3, server-side only

This fork runs RoadWeaver 2.3.2 on a Fabric 26.3 dedicated server. Players join with an unmodded 26.3 client. Roads, bridges, tunnels, lamps, signs and roadside structures are all built by the server during world generation, so nothing has to be installed on the client.

Upstream is [shiroha-233/RoadWeaver](https://github.com/shiroha-233/RoadWeaver) (MIT). The port lives on the `26.3-server` branch.

## Install

1. Put `roadweaver-server-<version>.jar` in the server's `mods/` folder next to Fabric API for 26.3.
2. Start the server once. It writes the default config to `config/roadweaver/`.
3. Edit the config if needed, then restart or run `/roadweaver reload`.

Roads are planned for terrain that has not been generated yet. Chunks that already exist are left alone, so on an old world the roads only appear in new areas.

## What is different from upstream

The client parts are gone: the road map on H (with teleport, search, notes and manual road linking), the structure picker, the preset editor, the loading screen overlay and the F9 debug overlay. The NeoForge build and the OpenCL (GPU) terrain sampler were removed as well, so terrain is always sampled on the CPU.

The first road generation on a new world blocks server startup until it finishes, as it does upstream. On a test world with the default config this took 91 seconds for 16 roads. Progress is printed to the server log every 10 seconds.

Signs carry English fallback text, so a vanilla client shows "Next Location 847 m" instead of a translation key.

Known gap at world spawn: vanilla generates the chunks around spawn while it looks for a spawn point, and that happens before RoadWeaver plans the network. On a new world the road from spawn therefore starts one or two chunks away, and `roadAppearance.spawnCabinEnabled` has no effect because the cabin can only be added to chunks that are generated later.

## The command

`/roadweaver reload` needs permission level 2 (gamemaster). It re-reads `roadweaver.json` and the preset files. New settings apply to roads planned after the reload. Roads that are already planned or built stay as they are. The thread settings in the `performance` section only take effect after a restart.

## Config files

Everything lives in `config/roadweaver/`.

| File | Purpose |
|---|---|
| `roadweaver.json` | All settings. Missing keys fall back to the defaults. |
| `presets/*.json` | Road surface presets, one file per preset. |
| `structure_cache_overworld.json` | Cache of discovered structures. Safe to delete. |

### roadweaver.json, the settings worth knowing

| Key | Default | Meaning |
|---|---|---|
| `structurePrediction.structureWhitelist` | `["#minecraft:village"]` | Structures that get connected. Accepts structure ids (`minecraft:pillager_outpost`) and structure tags (`#minecraft:village`). |
| `structurePrediction.structureBlacklist` | `[]` | Structures to skip even if the whitelist matches them. |
| `structurePrediction.predictRadiusChunks` | `1024` | How far from spawn structures are predicted. |
| `planning.initialPlanRadiusChunks` | `128` | Radius of the network built before the server opens. A bigger value means a longer first start. |
| `planning.dynamicPlanEnabled` | `true` | Plan more roads while players explore. |
| `planning.planningAlgorithm` | `RNG` | Network shape: `KNN` (sparse), `RNG` (balanced), `DELAUNAY` (dense), `MST`, `SNAP_BRANCH`. |
| `planning.terrainSamplingMode` | `COARSE_CORRIDOR` | Leave it. `FULL_REGION` needed the removed GPU sampler and falls back automatically. |
| `roadAppearance.roadsEnabled` | `true` | Master switch for road placement. |
| `roadAppearance.allowArtificial` / `allowNatural` | `true` | Allow built roads (stone, mud brick) and biome matched natural paths. |
| `roadAppearance.roadWidth` | `3` | Road width in blocks. |
| `roadAppearance.lampInterval` | `32` | Blocks between lamps. The lamps switch on at night through a daylight detector. |
| `roadAppearance.roadSignsEnabled` | `true` | Distance signs at road ends. |
| `roadAppearance.tunnelEnabled` | `false` | Dig tunnels through hills instead of going over them. |
| `roadAppearance.spawnCabinEnabled` | `true` | Small cabin at world spawn. |
| `bridge.enabled` | `true` | Bridges over water. `maxLengthBlocks` (100) caps their length. |
| `roadsideStructure.enabled` | `true` | Benches, campfires, campsites and graves next to roads. |
| `roadsideVillage.enabled` | `true` | Extra small villages along long roads. `spawnChance` is 0.3. |
| `performance.sharedWorkerThreads` | CPU cores minus 1, at most 4 | Background planning threads. Restart needed. |
| `performance.initialGenerationThreads` | depends on CPU | Threads used during the first generation. Restart needed. |
| `performance.idleGenerationEnabled` | `false` | Build postponed roads in small background batches, with its own thread limit (`idleMaxConcurrentGenerations`). |

These keys do nothing in this fork and can stay in the file: the whole `client` section, every `opencl*` key in `performance`, and `planning.debugRawPaths`.

### Presets

A preset file looks like this:

```json
{
  "id": "aged_stone",
  "name": "Aged Stone",
  "type": "ARTIFICIAL",
  "materials": ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"],
  "slabMaterials": ["minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab"]
}
```

`type` is `ARTIFICIAL` or `NATURAL`. Unknown block ids are dropped when the file is read. If no valid material is left, the preset is ignored.

One upstream behaviour to watch: a preset with a `dimensions` list other than `["minecraft:overworld"]` is deleted from disk on reload. Leave the key out unless you need it.

## World data

Road data is stored in the world save under `data/roadweaver/`. The road network is in `persistence/roads`, and cached terrain samples are in `persistence/accurate_samples` and `map/`. The list of connected structures is kept separately, in the world's level data. Leave both alone on a live world: RoadWeaver reads them whenever a new chunk on a planned road is generated.

## Building

Java 25 and the Gradle wrapper:

```
./gradlew build
```

The jar ends up in `build/libs/`. One unit test, `PathPostProcessorBoundsTest.staircaseSearchNodesProduceWidelySpacedCurveControls`, fails. It fails the same way on upstream code, because neither the test nor the class changed in this port.

If a structure template (`.nbt`) is added or replaced, upgrade it to the current data version:

```
./gradlew test --tests '*StructureTemplateUpgradeTool' -Droadweaver.upgradeTemplates=true
```

`StructureTemplateDataVersionTest` fails until that has been done.
