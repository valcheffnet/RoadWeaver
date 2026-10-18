# RoadWeaver 2.3.2: bugs found while porting to Minecraft 26.3

We ported RoadWeaver 2.3.2 (branch `1.21.1-Architectury`, commit `9b76a41`) to Fabric 26.3 as a server-side only build, so players can join with an unmodded client. That is close to what #53 asks for, and it also touches #81 and #85. While testing it on a dedicated server we ran into a number of bugs. Most of them are in the 1.21.x code and data too, so we are listing them here together with what we changed.

Every item in the first section was checked against both `1.21.1-Architectury` (`9b76a41`) and `1.21.11-Architectury` (`593bbed`). We searched the issue tracker on 2026-10-15 and found none of them reported. #95 (floating arches) shows a road climbing into the sky, which looks like a different problem.

## Bugs in the 1.21.x branches

### 1. The grave templates cannot be found on 1.21+

The five grave templates (`easter_egg`, `fogg05`, `magic_muggle`, `notch`, `technoblade`) only exist under `data/roadweaver/structures/roadside/small/graves/`. Since 1.21 the game reads templates from `structure/` (singular), and the other templates were copied there, but the graves were not. The `roadside_grave_*` structures point at `roadweaver:roadside/small/graves/...`, so on 1.21.x they have no template to place.

We moved the five files into `structure/roadside/small/graves/` and deleted the old `structures/` and `loot_tables/` folders, which were byte-for-byte copies of the singular ones.

### 2. Two bridges use biome tags that do not exist

`wood_bridge_huamu.json` uses `#minecraft:is_birch_forest` and `wood_bridge_hongshu.json` uses `#minecraft:is_mangrove`. Vanilla has neither tag. On 26.3 the server refuses to start with "Unbound tags in registry worldgen/biome". On 1.21.x we believe the tag just resolves to nothing, so these two bridges never match a biome.

We replaced them with explicit lists: `["minecraft:birch_forest", "minecraft:old_growth_birch_forest"]` and `["minecraft:mangrove_swamp"]`.

### 3. The item tags are in the old folder

The five item tags (`dyes`, `glazed_terracotta`, `stained_glass`, `basic_redstone_component`, `advanced_redstone_component`) are in `tags/items/`. Since 1.21 the folder is `tags/item/`, so the tags are not loaded and the grave loot table entries that use `#roadweaver:stained_glass` and the other four tags have nothing to resolve to. We renamed the folder.

### 4. The two advancements cannot load and cannot be earned

`road_first_step_1/2.json` are in `advancements/`, which 1.21 renamed to `advancement/`. Even in the right folder they would not do much: the trigger is `minecraft:impossible`, nothing in the code grants them, and the lang files have no `adv.roadweaver.*` keys, so the title would show as a raw key. We moved them, changed the icon to the 1.21 format (`"id"` instead of `"item"`), added English fallback text and set them to hidden. They should probably either be wired up or removed.

### 5. Roadside structures take the road's height, not the ground's

In `RoadsideStructurePrecomputer`, the height of a roadside structure is the road's target height for that segment:

```java
int baseHeight = (targetY != null && i < targetY.size()) ?
    targetY.get(i) :
    getHeight(placeX, placeZ, level, cache, terrain, heightmap, useBaseHeight);
```

The structure is placed beside the road (offset plus half its size), and on a hillside the ground there can be several blocks above or below the road. We found a grave whose base was at y 96 over ground at y 90 to 91. We now always use `getHeight(placeX, placeZ, ...)`, the predicted ground under the structure itself.

### 6. Several size hints are smaller than their templates

`size_hint` is used for the slope check and for the distance from the road. In six roadside structures it is smaller than the template, so the slope check misses part of the footprint and the structure can end up on the road or hanging over a slope.

| Structure | size_hint | Template size |
|---|---|---|
| roadside_bench | 3, 2, 2 | 5, 4, 2 |
| roadside_campfire | 3, 3, 3 | 6, 2, 6 |
| roadside_campsite_1 | 12, 8, 12 | 18, 8, 18 |
| roadside_campsite_2 | 12, 8, 12 | 18, 7, 17 |
| roadside_campsite_3 | 10, 6, 10 | 8, 7, 14 |
| roadside_maid_house | 10, 10, 12 | 12, 10, 11 |
| roadside_sakura_coffee_house | 12, 10, 12 | 19, 13, 17 |

The sakura coffee house was the worst case: with a 12x12 hint and `beard_thin`, we saw it hang up to 9 blocks above a slope. We set every hint to the real template size.

### 7. Roadside village houses end up sunk or raised

Two things combine here, both in how `RoadsideVillageJigsawPlanner` and `RoadsideVillagePrecomputer` place a house next to the road.

The ground level delta. The beard shapes the terrain around a rigid piece towards `minY + groundLevelDelta`. The planner always passes the template default, `element.getGroundLevelDelta()`, which is 1. Vanilla `JigsawPlacement` instead derives the delta of a rigid child from where its entrance jigsaw sits (`parentDelta - (parentJigsawY - childJigsawY)`). In the vanilla plains houses the entrance jigsaw is at y 0 when the house has a front step (the stairs), at y 1 for most others and at y 2 in a few. With a fixed delta of 1, houses with a step got the ground raised one block around them and looked sunk, and houses with the entrance at y 2 got it lowered and looked raised. We pass the entrance jigsaw's height as the delta instead (the anchor height minus the piece position in the fallback path).

The anchor height. The precomputer uses the mean height of the whole footprint as the anchor, while vanilla uses `getFirstFreeHeight()` at the connection point. On a slope the two differ. We take the surface height at the anchor itself and keep the mean for the slope and step checks. The fallback (no jigsaw faces the road) also put the template's bottom layer at the anchor; it now uses the height of the horizontal entrance jigsaw, like the main path.

With both changes, 10 of 12 measurable house entrances near spawn had the ground right outside at exactly one block below the entrance, for entrances at y 0, 1 and 2. The other two were one block off on uneven ground.

### 8. The campsites have ground layers but are not sunk

The campsite templates carry their own ground. `roadside_campsite_2` and `_3` have a grass and dirt layer at y 0, and `roadside_campsite_1` has dirt at y 0 and grass at y 1. The graves already handle this with `"start_height": { "absolute": -1 }`, but the campsites have no `start_height` and sit one or two blocks above the terrain. We added -1 to campsites 2 and 3, and -2 to campsite 1.

### 9. No road and no spawn cabin at world spawn

On a new world, `MinecraftServer.createLevels()` calls `setInitialSpawn()`, which looks for a spawn point with `PlayerSpawnFinder.getSpawnPosInChunk` and `ServerLevel.getHeight`. That fully generates the chunks around spawn. RoadWeaver plans its first roads in a `prepareLevels` HEAD mixin, which runs after this, so the spawn chunks already exist without roads. The spawn cabin is queued as a pending structure, and it can only be injected into chunks that are still being generated, so it never appears.

We confirmed this on 26.3: the four chunks around spawn had no road blocks and no cabin. The 1.21.1 code calls `setInitialSpawn` before `prepareLevels` in the same way, so we expect the same result there, but we did not run 1.21.1 to check.

We moved the initial planning to the start of `setInitialSpawn` and centred it on the point where vanilla begins its search: the middle of `ChunkGenerator.getOrigin(randomState)`, with the height from `getBaseHeight`, so no chunk is generated yet. The cabin is built directly with the existing (unused) `SpawnCabinStructure.placeAt` once `setInitialSpawn` returns, one block into the ground. Before that it checks its footprint against structure pieces and the road and moves up to 32 blocks away if the spot is taken. Without that check we once had the cabin built inside a roadside village house. It also prefers a spot where the ground under the whole footprint is level within one block, and sets the floor at the median height of the footprint: placed by the ground at its centre on a slope, the template cleared a pit two to three blocks deep around the cabin. On 1.21.1 vanilla starts the spawn search at `randomState.sampler().findSpawnPosition()` rather than the generator origin, so the predicted spawn is the middle of that chunk.

### 10. Structures with thin or no terrain adaptation can hang over slopes

Even with correct heights, a template on a slope keeps some columns in the air: graves use `terrain_adaptation: none`, two structures use `beard_thin`, and the directly built cabin gets no adaptation. We added a step to `SimpleTemplatePiece.postProcess` that fills air under the bottom layer of the template down to the ground, at most 12 blocks deep, inside the processing box. It uses dirt, or the stone or sandstone underneath. On a fresh world every roadside structure near spawn then had no hanging columns.

### 11. Empty refmap name

`MixinConfigPlugin.getRefMapperConfig()` returns `""`. Mixin then tries to read a refmap with an empty name and logs `Invalid REFMAP JSON in : ... Expected BEGIN_OBJECT but was STRING`. Returning `null` keeps the default.

### 12. One unit test fails on unchanged code

`PathPostProcessorBoundsTest.staircaseSearchNodesProduceWidelySpacedCurveControls` fails with "grid staircases should not become Bezier control points at each step". Neither the test nor `PathPostProcessor` was changed in our port. The control point logic changed in `7cadbb7`, so the test may simply be out of date.

## Things that break when moving to 26.x

These only matter for a 26.x port, but they cost us time, so they may save you some.

- Templates now store their palette with `id` and `properties` instead of `Name` and `Properties`. `BridgeTemplateStructureRegistry` parses `template.save()` by hand with the old keys, so every bridge block silently turns into air. `NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag)` reads it correctly.
- `Beardifier.forStructuresInChunk` returns the shared `Beardifier.EMPTY` for chunks without structures. The road plan must not be attached to that instance.
- `ConfiguredFeature` is gone. Features are records with a `MapCodec`, registered in `BuiltInRegistries.FEATURE_TYPE`, and the data folder is `worldgen/feature`.
- Density functions are sampled with `sampleVolume` / `sampleValue` on a `DensitySampler`, and `compute(FunctionContext)` no longer exists. The old `initialDensityWithoutJaggedness` scan can be replaced by the router's `chunkSurfaceLevel`. For exact heights, vanilla `NoiseBasedChunkGenerator.iterateNoiseColumn` is a good model: a `NoiseChunk`, `cachingSamplers().get(finalDensity).sampleVolume(volume)` and then `aquifer().computeSubstance`.
- Tag entries in loot tables use `"items": "#tag"` instead of `"name"`.
- An unbound biome tag now stops the server instead of being ignored.
- `RandomState.sampler()` is gone. `BiomeSource.createUncachedResolver(randomState)` covers the biome lookups.
- The bundled templates were on DataVersion 3465 and 3955 and were upgraded on every load. We upgraded them once with the vanilla path (`DataFixTypes.STRUCTURE`, then `StructureTemplate.load` and `save`). The only block changes were the vanilla renames `grass` to `short_grass` and `chain` to `iron_chain`.
- Inside `PlannedPathCache.PointBudgetLru`, which extends `LinkedHashMap`, the simple name `Entry` now resolves to `Map.Entry`, so the class needs `PlannedPathCache.Entry`.

## Smaller notes

- `roadside_maid_house` spawns `touhou_little_maid:maid`. Without that mod the server logs "Missing entity type touhou_little_maid:maid, skipping spawn rule" and builds the house without the mob. A mod-loaded check could skip the structure instead.
- `PresetService.reload()` deletes any preset file whose `dimensions` is not exactly `["minecraft:overworld"]`. That is easy to lose work to; logging and skipping such a file would be safer.
- `tags/worldgen/biome/has_biome_anchors.json` is still unused (#36).

## Found later, while building the full 26.2 version

We later backported the port to 26.2 and put the client side back, so it can be installed on both sides like the original. Testing that build turned up four more bugs that are in the 1.21.x code as well, because the code involved is unchanged apart from API names.

### 13. Some road signs stay empty

Sign text is not written while a chunk generates. `DistanceSignDecoration` and `SeaQuestionSignDecoration` queue it in `SignTextShardStorage`, and `SignTextService.processChunk` writes it once the chunk is marked dirty and is a full chunk. Two things lose queued text.

- `ServerPlanningHooks` calls `SignTextService.clearPending()` on `SERVER_STARTED`. The chunks around spawn are generated before that event, so the text of every sign near spawn is deleted. On a fresh world we logged 552 dirty chunks and one pending sign being cleared at that moment.
- A chunk is only marked when its structure starts become available or when a player stands in it. If it is not a full chunk yet when the mark is processed, `processChunk` returns and the mark is gone. Signs at the edge of a player's view distance can stay empty this way.

In our test worlds about one sign in four had no text on either side (18 with text and 7 empty in one 26.3 world). We removed the `clearPending()` call on start (stopping still clears) and mark every chunk again from `ServerChunkEvents.CHUNK_LOAD`, which fires when a chunk becomes a full chunk and also covers queue entries that were flushed to disk before a restart. A fresh world then had text on every sign, including the one at spawn.

### 14. A side road can drop several blocks per segment where it merges

`RoadSnapService.trySnap` replaces the side road's segments near the main road with the main road's segments and heights, and blends over `ROAD_SNAP_TRANSITION_SEGMENTS` (3). Nothing applies the slope limit again afterwards. Where the main road runs higher than the side road's own path, the side road falls the whole difference within those few segments. In our world a side road went from 108 to 94 in four segments (108, 105, 101, 98, 94), which builds a staircase straight down a hillside, while the limit is one block per two segments. We counted steps of 27, 9 and 5 blocks in another world.

The deeper cause is that `markSnapTargets` only compares horizontal distance (12 blocks to start, 24 to continue). A road on a ridge and a road in the valley below it are close on the map, so they get merged even when they are 13 blocks apart in height. Holding the side road to the slope limit alone then left it on a narrow raised wall of earth for some 25 segments, which looked no better.

We changed two things. `markSnapTargets` only starts or continues a merge where the two roads are within 3 blocks of each other in height, so the side road keeps its own path along the terrain until the roads are level. And `JunctionSlopeLimiter` handles the short transitions that remain: after snapping, segments that sit on the main road or on bridges keep their heights, and every other segment is held to the same rule as a road's own heights (`maxSlopeStepPerTwoSegments`, at most half of it per segment, and at most `step * ceil(d / 2)` away from a fixed segment `d` segments away). On a fresh world no road had two neighbouring segments more than one block apart in height afterwards, where there were eight such steps before.

### 15. Roads over a deep dip hang on a sheet of dirt

`RoadChunkPlanCompiler` raises the terrain under a road with a density stamp that fades out over `FILL_DEPTH` (6) blocks below the road. Where the ground lies deeper than that, at a dip, a cliff edge or a cave mouth, the stamp produces a thin sheet of dirt at road level with air under it, and the road is paved on top. From the side it looks like a platform floating next to the hill. In one test world 64 road segments had two or more blocks of air under their surface, up to nine.

We check each road column after paving (`RoadSupports`). Only a gap that opens to the side counts: some column within three blocks beside the road must be open from the road surface down to the gap. A road over a cave keeps its ground, which our first version of this check got wrong and turned into planks. A gap of one or two blocks is filled with dirt like an embankment. Over a deeper gap the hanging dirt is removed and the road becomes a deck of planks in the biome's wood, or stone bricks on a stone road, with posts down to the ground every three blocks: fences up to four blocks high, stone brick pillars above that.

### 16. Sign posts and lamps can stand a block above the ground

`Decoration.computeSurfacePos` takes the height from `Heightmap.Types.WORLD_SURFACE_WG`, which counts any non-air block, including grass, flowers and pink petals. In a cherry grove a distance sign stood on top of a flower, one block above the ground, and the extra plank `ensureFoundation` puts under it hung in the air. We now step down from that height through replaceable blocks that are not fluids, at most four blocks.

### 17. Roadside structures take their height from the coarse sampler

Item 5 moved the height of a roadside structure from the road to the ground under the structure, but that ground still comes from `getHeight`, which uses the terrain field or `TerrainSamplingCache.height` unless the structure sets `use_accurate_height`. None of the bundled structures does. The fast sampler reads the preliminary surface level, which can be four to eight blocks off on slopes and in cherry groves. In one world three of twelve roadside structures sat four to eight blocks below the ground around them, and `beard_box` dug a terraced pit around a bench to reach it.

The height is now the median of the ground at the centre, the edge middles and the corners of the footprint, always from `ChunkGenerator.getBaseHeight`, and the terrain check rejects a footprint whose highest and lowest point differ by more than `MAX_STRUCTURE_SLOPE`. The old check compared the centre with the four edge middles only, so a diagonal slope passed. In a new world none of the roadside structures sat more than two blocks below the ground around it.

## Checked on 1.21.1

We applied the code fixes to `1.21.1-Architectury` (with the merged data fixes) and generated the same 256 chunks around spawn on a Fabric dedicated server, with the same seed, before and after.

| Item | Unchanged 1.21.1 | With the fixes |
|---|---|---|
| 9. Road segments near spawn that are paved | 9 of 32 | 32 of 32, cabin built |
| 11. Refmap error in the log | yes | no |
| 13. Empty road signs | 2 of 4 | 0 of 6 |
| 14. Neighbouring segments 3 or more blocks apart | 5 | 0 |
| 15. Road segments on a sheet of dirt over air | 3 | 0 |

Item 16 did not show up in that area in either run.

The data fixes (items 1, 2, 3, 6 and 8) are merged. Items 4, 5, 7 and 9 to 11 were fixed and tested on our 26.3 port, on the `26.3-server` branch. Items 13 to 17 were tested on 26.2 and on 1.21.1 as above.
