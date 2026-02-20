# Starlight Legacy

This is a backport of some features from Starlight 1.16.5 and Moonrise/ScalableLux to 1.12.2. Since this is purely a
personal hobby project, please be aware that it may have some rough edges! 🫠
No benchmarking data has been collected.

Therefore, unless there's a specific reason not to, please use Alfheim when encountering this repository.

## Requirements

- [CleanroomLoader](https://github.com/CleanroomMC/Cleanroom) 0.3.31-alpha or later

## Incompatibilities

The following mods are not compatible:

- **CubicChunks**
- **Phosphor** / **Hesperus**
- **Alfheim**

## Architecture

### Core Algorithm

Starlight replaces vanilla Minecraft's iterative light propagation with a **BFS (Breadth-First Search) based algorithm
**. Instead of processing light updates one block at a time with potential cascading recalculations, Starlight processes
all increases and decreases in two efficient BFS passes:

1. **Decrease pass**: Propagates light removal outward from removed sources, collecting boundary values
2. **Increase pass**: Propagates light from all sources (new + boundary), writing final values in one sweep

This approach eliminates the redundant recalculations that make vanilla lighting slow, resulting in O(n) complexity
where n is the number of affected blocks.

### Dual Engine Design

- **BlockStarLightEngine** - Handles block light (torches, lava, glowstone, etc.)
- **SkyStarLightEngine** - Handles sky light with special column-based optimizations for vertical propagation
- **StarLightInterface** - Coordinates both engines, manages the light task queue, and handles vanilla synchronization

### SWMR (Single Writer Multi Reader) Data

Light data is stored in `SWMRNibbleArray` instead of vanilla's `NibbleArray`. This provides:

- **Dual-buffer design**: Separate `updating` and `visible` data paths
- Light engine writes to `updating` during computation
- Readers (rendering, mod compat) read from `visible` via synchronized access
- `updateVisible()` atomically publishes computed data to the visible buffer

### Light Read Redirect

All vanilla light read methods (`World.getLightFor()`, `World.getLight()`, `World.getLightFromNeighborsFor()`) are
redirected via Mixin to read directly from SWMR data through `StarLightInterface`. This ensures consistent, up-to-date
light values without waiting for vanilla NibbleArray sync.

### Vanilla Synchronization

For compatibility with mods that read light through `ChunkCache` or `ExtendedBlockStorage` (which bypass
`World.getLightFor()`), SWMR data is synced to vanilla NibbleArrays:

- After `propagateChanges()` completes (tracks affected chunks via `LongOpenHashSet`)
- Before chunk data packets are sent to clients
- After `ensureChunkLit()` during chunk generation

## Features Incorporated

### From Starlight 1.16.5

- Core BFS light propagation algorithm
- SkyStarLightEngine with column-based sky light optimization (heightmap tracking, `extrudeLower`)
- BlockStarLightEngine with emission-aware source detection
- SWMRNibbleArray dual-buffer data structure
- 5x5 chunk cache for neighbor access during propagation
- `KnownTransparenciesData` (bitset) for fast opacity lookups per chunk section
- Light task queue with per-chunk batching (`LightQueue.ChunkTasks`)
- `VariableBlockLightHandler` for mod-provided custom light sources
- Client-side vanilla NibbleArray to SWMR direct conversion (no BFS re-computation)

### From Moonrise & ScalableLux

- **`extrudeLower` fix**: Corrected byte-level copy range calculation for sky light extrusion below world minimum
- **Volatile `storageVisible`**: Ensures cross-thread visibility of SWMR visible data
- **`FlatBitsetUtil`**: Optimized bitset utility for tracking empty/non-empty sections and edge conditions

- **SWMR byte pool size limit**: `POOL_MAX_SIZE = 128` prevents unbounded memory growth from pooled byte arrays
- **Sky light early exit**: When `skyLight >= 15` in `World.getLight()`, returns immediately without checking block
  light

### Backport-Specific Adaptations

- **Single-threaded design**: 1.12.2 is single-threaded, so parallel processing from modern versions is not applicable.
  The light engine runs synchronously.
- **Server-side immediate processing**: `propagateChanges()` drains all queued light tasks without a time budget,
  ensuring lighting is always complete before chunk packets are sent.
- **Client-side budget processing**: Light updates on the client are processed with an 8ms per-tick budget to avoid
  frame drops.
- **Chunk generation integration**: `ensureChunkLit()` calls `lightChunk()` for full BFS computation, then removes any
  redundant queued tasks via `removeChunkTasks()`.
- **Renderer notification**: `updateVisible()` calls `markBlockRangeForRenderUpdate()` on the client to trigger
  re-renders for affected sections.

## License

This project is licensed under **LGPL-3.0-or-later** (GNU Lesser General Public License v3.0 or later).

- `COPYING` - GNU General Public License v3.0
- `COPYING.LESSER` - GNU Lesser General Public License v3.0 (additional permissions)

Portions derived from [Moonrise](https://github.com/Tuinity/Moonrise) (GPL-3.0) are used under GPL-3.0 compatibility
with LGPL-3.0.

## Credits

- **Spottedleaf** - Original Starlight author
- **Moonrise** contributors - `extrudeLower` fix, volatile visibility, `FlatBitsetUtil`
- **ScalableLux** contributors - Pool size limit, sky light early exit pattern
