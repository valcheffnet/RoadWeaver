# RoadWeaver for Minecraft 26.2, full version

This is our fork of RoadWeaver 2.3.2 for Fabric 26.2, branch `26.2-full`, version `2.3.2-full.1+26.2`. It has the features the original has on 1.21.1: roads, bridges, tunnels, signs, lamps and roadside structures on the server, plus the road map, the settings screens and the overlays on the client. The client part is optional. A player with an unmodded 26.2 client can join a server that runs it and sees all the roads; only the map and the screens are missing for that player.

Upstream is [shiroha-233/RoadWeaver](https://github.com/shiroha-233/RoadWeaver) (MIT). The fork started as a server-side port to 26.3 (branch `26.3-server`), was backported to 26.2 (branch `26.2-server`) and then got the client side back on this branch. Bugs we found in the original 1.21.x code are written up separately in `upstream-report.md`.

## Install

| Where | What |
|---|---|
| Server | `roadweaver-2.3.2-full.1+26.2.jar` and Fabric API for 26.2 in `mods/`. |
| Client, optional | The same jar and Fabric API. Gives the road map, the settings screens and the overlays. ModMenu is optional and adds a config button. |
| Single player | The same jar on the client. |

Cloth Config (26.2.155) is bundled in the jar, so it does not need to be installed separately.

On a new world the first road network is planned before the server opens, which takes a minute or two. Chunks that already exist are left alone, so on an old world roads only appear in new areas.

## Client keys and commands

| Key or command | What it does |
|---|---|
| H | Road map with structures, search, teleport and notes. |
| F9 | Debug overlay with the unprocessed path nodes. Needs `planning.debugRawPaths` and operator rights. |
| `/roadweaver mapaccess info`, `mode all`, `mode ops_only`, `allow`, `deny` | Who may open the map on a server. |
| `/roadweaver reload` | Re-reads the config and presets. Applies to roads planned after the reload. |

## Removed compared with upstream 2.3.2

| Removed | Why |
|---|---|
| NeoForge build and Architectury | Fabric only. The Architectury `@ExpectPlatform` calls were replaced with direct calls. |
| OpenCL terrain sampler and the bundled LWJGL OpenCL | The CPU sampler was already the fallback. The OpenCL options are gone from the config screen; the keys in `roadweaver.json` are ignored. |
| Compat mixins for Oh The Trees You'll Grow, ReTerraForged and Dynamic Trees | Not ported. |
| `BiomeGrassColorFailSafeMixin` | Its target changed in 26.2 and the client works without it. |

## Fixed

These are bugs of the original that are fixed here. The ones marked "upstream" are in the 1.21.x code too and are described in detail, with what we changed, in `upstream-report.md`.

| Bug | Upstream |
|---|---|
| Grave templates were never found (old `structures/` folder) | yes |
| Two bridges used biome tags that do not exist | yes |
| Item tags and advancements in the pre-1.21 folders | yes |
| Roadside structures took the road's height instead of the ground's | yes |
| Size hints of seven roadside structures smaller than their templates | yes |
| Roadside village houses sunk or raised by a block | yes |
| Campsites one or two blocks above the ground | yes |
| No road and no spawn cabin at world spawn | yes |
| Structures hanging over slopes | yes |
| Mixin log error from an empty refmap name | yes |
| About one road sign in four had no text | yes |
| Side roads fell several blocks per segment where they merged into a main road | yes |
| Roads over a deep dip or along a cliff edge sat on a thin sheet of dirt in the air | yes |
| Sign posts and lamps stood on grass and flowers, a block above the ground | yes |
| Roadside structures four to eight blocks below the ground, in a dug pit, because their height came from the coarse terrain sampler | yes |
| The spawn cabin set into a slope with a pit around it | yes |
| Signs showed translation keys to players without the mod | no, upstream needs the mod on the client |
| Every bridge block turned into air (template palette format) | 26.x only |
| Map textures were released on the network thread when leaving a server | 1.21.6 and later |

## New in this fork

### Roads reach the village streets

Upstream stops every road about 60 blocks before a village and leaves the last segments unpaved, so roads end in a field. Once a village exists, we now pave a short path from the end of the road to the nearest street of that village. The path is found with A* on block columns and never enters a house or any other non-street piece. It is stored as a road with a `village_connector` flag, paved to both ends, without lamps or signs.

Config: `structurePrediction.connectVillageStreets`, default `true`.

### Stairs on steep slopes

Where the ground climbs faster than the road's slope limit, upstream cuts deep into the hill or raises a bank of earth. We build stairs there instead: flights of up to 8 steps with 2-segment landings, a block of fill under every step, a low wall along the edges where the ground drops away, and a lantern on each landing. Paved roads get stairs and walls in their own material (stone brick, mud brick and so on). Dirt and gravel paths get cobblestone.

A few details that took several rounds of testing:

- The stair starts and ends at the level of the road around it. The steps first followed the ground, and in 42 of 67 stairs the top step stood a block above the road beyond it.
- Every column of a stair is paved once and in one material, so landings and the gaps between steps are not left as dirt, gravel or slabs. Other roads that cross or share the stretch leave those columns alone.
- Steps are placed after the whole chunk is paved, from the blocks that are actually there. Any column that is exactly one block below its neighbour gets a step, so nowhere on the stairs needs a jump.
- Stairs keep at least 4 segments away from bridges.

Config: `roadAppearance.roadStairsEnabled`, default `true`.

### Flatter roads

A stretch of up to 4 segments that lies one or two blocks above or below the road on both sides is levelled to the nearer side, and the terrain is shaped to the road. Before, roads went up and down a block every few metres on rough ground. Longer hills are still followed.

### Decks over deep dips

This only applies where the gap opens to the side, over a dip or past a cliff edge. A road over a cave keeps its ground. Gaps of one or two blocks under the road are filled like an embankment. Over a deeper gap the road becomes a deck of planks in the biome's wood, or stone bricks on a stone road, with posts down to the ground every 3 blocks: fences where the deck is up to 4 blocks high, stone brick pillars where it is higher.

### Slabs that match the road

Natural roads choose their half-step slabs from their surface: mud brick slabs on dirt and mud paths, cobblestone or stone brick slabs on stone, sandstone on sand. The natural presets no longer use wooden slabs. Preset files written by an older version keep their slabs until the `config/roadweaver/presets/` folder is deleted and regenerated.

### Works with and without the client mod

Map packets are only sent to players whose client registered the channel, and nothing in the login phase needs the mod. The map access policy is stored per world.

## Porting the client code to 26.2

| Problem | Fix |
|---|---|
| Text drawn with a colour without alpha (`0xFFFFFF`) is invisible since 1.21.6. The structure list and the preset list looked empty. | 29 colours given full alpha (`0xFFFFFFFF`). |
| `blit(texture, x, y, u, v, w, h, texW, texH)` is gone, and the overload that still compiles with the same arguments draws corner to corner. The mod icon in the structure list was stretched into a grey box. | Uses `blit(RenderPipelines.GUI_TEXTURED, ...)`. |
| Screens that drew their own background blurred twice and the game stopped with "Can only blur once per frame". | The screens no longer draw the background themselves. |
| The world creation screen has no `LEVEL_STEM` registry, so the structure list crashed. | The overworld biomes come from `selectedDimensions().overworld().getBiomeSource()`. |
| The loading screen mixin targeted `render`, which is now `extractRenderState`. | Retargeted. |

## Known issues

- Where a road makes a hairpin turn on a steep slope, the two legs can run side by side several blocks apart in height, with no wall between them (seen at -790 99 -370 on our test seed).
- A stair on a stretch that two roads share takes the style of the road that owns the stair, so a cobblestone stair can appear in the middle of a stone brick road.
- Where two stair spans of a road meet, the last step of one and the first of the next can be three blocks apart.
- A distance sign placed on a column that a stair in a neighbouring chunk later paves loses its post and hangs in the air without text.
- The first road generation on a new world holds up server start, as it does upstream.
- `PathPostProcessorBoundsTest.staircaseSearchNodesProduceWidelySpacedCurveControls` fails on unchanged upstream code.
