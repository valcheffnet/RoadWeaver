# RoadWeaver

> **This is an unofficial fork of [shiroha-233/RoadWeaver](https://github.com/shiroha-233/RoadWeaver).** It runs RoadWeaver 2.3.2 on Minecraft 26.2, for Fabric and NeoForge. The original mod and its own downloads are linked below; they do not have a 26.2 version.
>
> **Download for 26.2:** [latest release](https://github.com/valcheffnet/RoadWeaver/releases/latest)
>
> | Loader | File | Also needs |
> |---|---|---|
> | Fabric | `roadweaver-fabric-…+26.2.jar` | Fabric API |
> | NeoForge | `roadweaver-neoforge-…+26.2.jar` | NeoForge 26.2.0.93 or later |
>
> Put the jar on the server and players can join with an unmodded client; add it to the client as well for the road map and the settings screens. In single player, install it on the client. What this fork changes is in [docs/fork-26.2.md](docs/fork-26.2.md). Please report problems with these builds in this repository's [issues](https://github.com/valcheffnet/RoadWeaver/issues), not upstream.
>
> The rest of this page is the original README.

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/6jk8Pote?style=flat-square&logo=Modrinth&label=Modrinth)](https://modrinth.com/mod/roadweaver)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1358489?style=flat-square&logo=CurseForge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/roadweaver)
[![Discord Invite](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=flat-square)](https://discord.gg/tUJMJkbbr2)
[![Github Stars](https://img.shields.io/github/stars/shiroha-233/RoadWeaver?logo=github&style=flat-square)](https://github.com/shiroha-233/RoadWeaver)

English | [简体中文](README_CN.md)

A Minecraft mod that automatically generates beautiful roads between villages or custom structures.

## Key Features

### 1. Smart Road Generation

- **Intelligent Pathfinding**: Multiple pathfinding algorithms that avoid steep and dangerous areas; adjusts routes based on terrain height, biomes, and ground stability
- **Bezier Curves**: Applies Bezier curve smoothing to polyline paths, creating natural smooth curves and avoiding sharp turns
- **Multiple Road Types**:
  - Artificial roads: (stone bricks, slabs), (dirt, mud bricks), etc., or customize your own in the preset editor
  - Natural roads: Biome-adaptive road materials
- **Obstacle Avoidance**: Cuts, fills terrain and removes trees to ensure road passability
- **Tunnel & Bridge System**: Tunnels through mountains, bridges over water
- **Slab System**: Fills slabs at elevation changes to improve passability
- **Road Foundation**: Smoothly interpolates surrounding height field based on road elevation, fills foundation only when road is above original terrain, creating natural convex slopes that blend seamlessly with the landscape

### 2. Decoration System

- **Lamp System**: Redstone lamps with automatic day/night control
- **Signpost System**: Distance markers and directional signs
- **Roadside Structure Decorations**: Randomly generates benches, campfires and other decorative structures along roads

### 3. Configuration Options

- **Performance Optimization**: Multi-threaded async generation with concurrency control; height and terrain caching to reduce redundant calculations
- **Multiple Network Planning Algorithms**: KNN (sparsest) / Delaunay (densest) / RNG (balanced)
- **Multiple Pathfinding Algorithms**: A* / Bidirectional A* / Fluid Simulation
- **Road Block Customization**: Mix and match in the preset editor

### 4. Visualization Tools

- **Visual Debugging**: Road network map; status colors (planned/generating/completed/failed); interactions (drag, zoom, right-click teleport); statistics for road count, length and status
- **Manual Link Mode**: Plan road networks according to your preferences

## Compatibility

- New versions (2.0.0+) completely abandon the old `/locate` command search mechanism, no longer blocking the game main thread
- Structure prediction, road network planning and pathfinding all run in dedicated thread pools; main thread only handles driving and result application

### Known Compatibility & Performance Issues

- This mod (2.0.6+) is compatible with Tectonic-V2 / Epic Terrain / Terralith, but **incompatible with Tectonic-V3**
- This mod relies on vanilla mechanics, so it's incompatible with mods that overhaul vanilla mechanics like TerraFirmaCraft: The Next Generation

**Incompatibility symptoms:**
- Extremely slow road generation
- Very slow or blank map data loading
- Map shows roads as generated but nothing appears when approaching

**Performance factors:**
- World terrain complexity
- Pathfinding step size and weight configuration
- Concurrent road generation count and thread pool size

## Usage

- **Auto Generation**: Roads automatically generate between structures after entering the world
- **Road Network Map**: Press **H** to open the debug map and view the road network
- **Configuration**: Cloth Config API settings screen (built-in since 2.0.2), accessible from the world creation screen or by pressing **H** in-game and clicking the top-right corner

## Inspiration

Based on [Countered's Settlement Roads](https://modrinth.com/mod/countereds-settlement-roads), map inspired by [RoadArchitect](https://github.com/FranckRJ/RoadArchitect).

## Future Plans

- [ ] More roadside decorations?
- [x] Link multiple structure types
- [ ] Link biomes?
- [ ] More beautiful buildings?
- [ ] Road events?
- [x] Custom linking
- [ ] Main road system?
- [x] Slab transitions
- [x] Bezier curve smoothing

## Notes

- The more structures configured to locate when loading a world, the longer world creation takes, but the more complete the road network
- Roads cannot generate on already-loaded chunks, so don't approach road segments before they finish generating
