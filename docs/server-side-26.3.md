# RoadWeaver for Minecraft 26.3, server-side only

This fork runs RoadWeaver 2.3.2 on a Fabric 26.3 dedicated server. Players join with an unmodded 26.3 client. Roads, bridges, tunnels, lamps, signs and roadside structures are all built by the server during world generation, so nothing has to be installed on the client.

Upstream is [shiroha-233/RoadWeaver](https://github.com/shiroha-233/RoadWeaver) (MIT). The port lives on the `26.3-server` branch.

## Install

1. Put `roadweaver-server-<version>.jar` in the server's `mods/` folder next to Fabric API for 26.3.
2. Start the server once. It writes the default config to `config/roadweaver/`.
3. Edit the config if needed, then restart or run `/roadweaver reload`.

Roads are planned for terrain that has not been generated yet. Chunks that already exist are left alone, so on an old world the roads only appear in new areas.

## What is different from upstream

Everything that needs the mod on the client is gone. In detail:

| Removed | What it did upstream | Replacement here |
|---|---|---|
| Road network map on H | Map of roads and structures with teleport, search, notes and manual road linking | None. Roads are planned automatically. |
| `/roadweaver mapaccess` and map sync | Who may open the map, plus the packets that fed the map | None. |
| Structure picker screen | Choosing which structures get connected | `structurePrediction.structureWhitelist` and `structureBlacklist` in `roadweaver.json` |
| Preset editor screen | Editing road surface presets | The JSON files in `config/roadweaver/presets/` |
| Cloth Config screen, ModMenu entry, settings on the world creation screen | Editing the config in game | `roadweaver.json` and `/roadweaver reload` |
| Loading screen overlay and tips | Progress of the first road generation | Progress lines in the server log |
| F9 raw path debug overlay | Showed unprocessed path nodes | None. |
| OpenCL (GPU) terrain sampler and the bundled LWJGL | Faster terrain sampling on a GPU | The CPU sampler, which was already the fallback |
| NeoForge build, Architectury, Cloth Config dependency | Second loader and the shared platform layer | Fabric only |
| Compat mixins for Oh The Trees You'll Grow, ReTerraForged, Dynamic Trees | Kept roads clear of those mods' trees | None. Those mods have no 26.3 builds. |

The map's tile data (`data/roadweaver/map/` in the world) is still written, because road planning reads it. Nothing displays it.

The first road generation on a new world blocks server startup until it finishes, as it does upstream. On a test world with the default config this took 91 seconds for 16 roads. Progress is printed to the server log every 10 seconds.

Signs carry English fallback text, so a vanilla client shows "Next Location 847 m" instead of a translation key.

On a new world the network is planned at the very start of vanilla's spawn search. Vanilla generates the chunks around spawn while it searches, so planning any later would leave them without roads. The plan is centred on the point where that search starts, and the spawn cabin is built straight into the world once the spawn chunks exist, on level ground within 32 blocks of the spawn that is clear of structures and roads. On an existing world no cabin is built, since the area around its spawn may already be built on.

## Added in this fork

These are new behaviour rather than ports; they work with vanilla clients like everything else.

- Roads continue to the nearest street of a village instead of stopping in front of it. The connecting path goes around the houses.
- Where the ground climbs faster than the slope limit, the road gets stone stairs with landings, a low wall where the ground drops away and a lantern on each landing, instead of a deep cut.
- Short humps and dips are levelled out of a road.
- Over a deep dip or along a cliff edge the road becomes a plank or stone brick deck on posts instead of a sheet of dirt in the air. A road over a cave keeps its ground.
- Half slabs match the road: mud brick on dirt paths, cobblestone or stone brick on stone, sandstone on sand. The natural presets no longer use wooden slabs.
- Side roads merge into a main road only where the two are close in height, and stay within the slope limit where they do.
- Roadside structures take the real ground height of their footprint and skip slopes steeper than 3 blocks; lamps and sign posts stand on the ground and keep off road columns.

Known issues: where a road makes a hairpin on a steep slope the two legs can run side by side a few blocks apart with no wall between them, and a few distance signs can stay without text.

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
| `roadAppearance.roadStairsEnabled` | `true` | Stairs on slopes too steep for the road. |
| `structurePrediction.connectVillageStreets` | `true` | Continue roads to the nearest village street. |
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
