package jp.s12kuma01.starlightlegacy.common.light;

import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunkSection;
import jp.s12kuma01.starlightlegacy.common.util.CoordinateUtils;
import jp.s12kuma01.starlightlegacy.common.util.WorldUtil;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StarLightInterface {

    protected final World world;
    protected final LightChunkGetter lightAccess;

    protected final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    protected final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    protected final LightQueue lightQueue = new LightQueue(this);

    protected final boolean isClientSide;

    protected final int minSection;
    protected final int maxSection;
    protected final int minLightSection;
    protected final int maxLightSection;

    public StarLightInterface(final LightChunkGetter lightAccess, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.lightAccess = lightAccess;
        this.world = lightAccess == null ? null : lightAccess.getWorld();
        this.cachedSkyPropagators = hasSkyLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.isClientSide = !(this.world instanceof WorldServer);
        if (this.world == null) {
            this.minSection = 0;
            this.maxSection = 15;
            this.minLightSection = -1;
            this.maxLightSection = 16;
        } else {
            this.minSection = WorldUtil.getMinSection(this.world);
            this.maxSection = WorldUtil.getMaxSection(this.world);
            this.minLightSection = WorldUtil.getMinLightSection(this.world);
            this.maxLightSection = WorldUtil.getMaxLightSection(this.world);
        }
    }

    /**
     * Ensures a chunk has been lit by Starlight. Initializes section transparency data,
     * runs the light engine, and syncs to vanilla nibbles.
     */
    public static void ensureChunkLit(final World world, final Chunk chunk) {
        if (chunk == null) {
            return;
        }
        final ExtendedChunk exChunk = (ExtendedChunk) chunk;
        if (exChunk.isStarlightLit()) {
            return;
        }
        // Initialize section transparency data
        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (final ExtendedBlockStorage section : sections) {
            if (section != null && section != Chunk.NULL_BLOCK_STORAGE) {
                ((ExtendedChunkSection) section).starlight$initKnownTransparenciesData();
            }
        }

        // Light the chunk using Starlight engine
        final StarLightInterface lightEngine = ((ExtendedWorld) world).getLightEngine();
        if (lightEngine != null) {
            // lightChunk() computes correct lighting from the final block state via full BFS.
            // Any blockChange() calls queued during world generation are redundant — discard them.
            lightEngine.lightChunk(chunk, StarLightEngine.getEmptySectionsForChunk(chunk));
            lightEngine.removeChunkTasks(new ChunkPos(chunk.x, chunk.z));
            syncNibbleToVanilla(chunk);
        }
        exChunk.setStarlightLit(true);
    }

    /**
     * Syncs SWMR nibble data to vanilla NibbleArrays in ExtendedBlockStorage.
     * Must be called after propagateChanges() and before rendering/packet sending.
     */
    public static void syncNibbleToVanilla(final Chunk chunk) {
        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        final SWMRNibbleArray[] blockNibbles = ((ExtendedChunk) chunk).getBlockNibbles();
        final SWMRNibbleArray[] skyNibbles = ((ExtendedChunk) chunk).getSkyNibbles();

        if (blockNibbles == null || skyNibbles == null) {
            return;
        }

        for (int i = 0; i < sections.length; ++i) {
            final ExtendedBlockStorage section = sections[i];
            if (section == null || section == Chunk.NULL_BLOCK_STORAGE) {
                continue;
            }

            // nibble index offset: minLightSection = -1, so section index 0 maps to nibble index 1
            final int nibbleIndex = i + 1; // +1 because minLightSection = -1

            if (nibbleIndex >= 0 && nibbleIndex < blockNibbles.length) {
                final SWMRNibbleArray blockNibble = blockNibbles[nibbleIndex];
                if (blockNibble != null) {
                    blockNibble.syncToVanillaNibble(section.getBlockLight());
                }
            }

            if (nibbleIndex >= 0 && nibbleIndex < skyNibbles.length) {
                final SWMRNibbleArray skyNibble = skyNibbles[nibbleIndex];
                if (skyNibble != null && section.getSkyLight() != null) {
                    skyNibble.syncToVanillaNibble(section.getSkyLight());
                }
            }
        }
    }

    public boolean isClientSide() {
        return this.isClientSide;
    }

    public Chunk getAnyChunkNow(final int chunkX, final int chunkZ) {
        if (this.world == null) {
            return null;
        }
        return ((ExtendedWorld) this.world).getChunkAtImmediately(chunkX, chunkZ);
    }

    public boolean hasUpdates() {
        return !this.lightQueue.isEmpty();
    }

    public World getWorld() {
        return this.world;
    }

    public LightChunkGetter getLightAccess() {
        return this.lightAccess;
    }

    protected final SkyStarLightEngine getSkyLightEngine() {
        if (this.cachedSkyPropagators == null) {
            return null;
        }
        final SkyStarLightEngine ret = this.cachedSkyPropagators.pollFirst();
        if (ret == null) {
            return new SkyStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseSkyLightEngine(final SkyStarLightEngine engine) {
        if (this.cachedSkyPropagators == null || engine == null) {
            return;
        }
        this.cachedSkyPropagators.addFirst(engine);
    }

    protected final BlockStarLightEngine getBlockLightEngine() {
        if (this.cachedBlockPropagators == null) {
            return null;
        }
        final BlockStarLightEngine ret = this.cachedBlockPropagators.pollFirst();
        if (ret == null) {
            return new BlockStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseBlockLightEngine(final BlockStarLightEngine engine) {
        if (this.cachedBlockPropagators == null || engine == null) {
            return;
        }
        this.cachedBlockPropagators.addFirst(engine);
    }

    public void blockChange(final BlockPos pos) {
        if (this.world == null || pos.getY() < WorldUtil.getMinBlockY(this.world) || pos.getY() > WorldUtil.getMaxBlockY(this.world)) {
            return;
        }
        this.lightQueue.queueBlockChange(pos);
    }

    public void sectionChange(final int sectionX, final int sectionY, final int sectionZ, final boolean newEmptyValue) {
        if (this.world == null) {
            return;
        }
        this.lightQueue.queueSectionChange(sectionX, sectionY, sectionZ, newEmptyValue);
    }

    public void forceLoadInChunk(final Chunk chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void loadInChunk(final int chunkX, final int chunkZ, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void lightChunk(final Chunk chunk, Boolean[] emptySections) {
        if (emptySections == null) {
            emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.light(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkChunkEdges(final int chunkX, final int chunkZ) {
        this.checkSkyEdges(chunkX, chunkZ);
        this.checkBlockEdges(chunkX, chunkZ);
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void removeChunkTasks(final ChunkPos pos) {
        this.lightQueue.removeChunk(pos);
    }

    /**
     * Drains all queued light tasks. No time budget - processes everything.
     * After processing, syncs vanilla NibbleArrays for all affected chunks
     * so that ChunkCache/rendering reads updated light data.
     */
    public void propagateChanges() {
        if (this.lightQueue.isEmpty()) {
            return;
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        // Track affected chunks for vanilla NibbleArray sync
        final LongOpenHashSet affectedChunks = new LongOpenHashSet();

        try {
            LightQueue.ChunkTasks task;
            while ((task = this.lightQueue.removeFirstTask()) != null) {
                if (task.lightTasks != null) {
                    for (final Runnable run : task.lightTasks) {
                        run.run();
                    }
                }

                final long coordinate = task.chunkCoordinate;
                final int chunkX = CoordinateUtils.getChunkX(coordinate);
                final int chunkZ = CoordinateUtils.getChunkZ(coordinate);

                final Set<BlockPos> positions = task.changedPositions;
                final Boolean[] sectionChanges = task.changedSectionSet;

                if (skyEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                    skyEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }
                if (blockEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                    blockEngine.blocksChangedInChunk(this.lightAccess, chunkX, chunkZ, positions, sectionChanges);
                }

                if (skyEngine != null && task.queuedEdgeChecksSky != null) {
                    skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, task.queuedEdgeChecksSky);
                }
                if (blockEngine != null && task.queuedEdgeChecksBlock != null) {
                    blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ, task.queuedEdgeChecksBlock);
                }

                task.completed = true;

                // Light can propagate into adjacent chunks (up to 15 blocks),
                // so sync the center chunk and its immediate neighbors
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        affectedChunks.add(CoordinateUtils.getChunkKey(chunkX + dx, chunkZ + dz));
                    }
                }
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }

        // Sync vanilla NibbleArrays so ChunkCache/rendering sees updated light
        for (final long chunkKey : affectedChunks) {
            final Chunk chunk = this.getAnyChunkNow(
                    CoordinateUtils.getChunkX(chunkKey),
                    CoordinateUtils.getChunkZ(chunkKey)
            );
            if (chunk != null) {
                syncNibbleToVanilla(chunk);
            }
        }
    }

    /**
     * Gets the sky light level from SWMR data for a given position.
     */
    public int getSkyLightLevel(final BlockPos pos) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        final int cy = y >> 4;

        if (cy < this.minLightSection || cy > this.maxLightSection) {
            return cy > this.maxLightSection ? 15 : 0;
        }

        final Chunk chunk = this.getAnyChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return 15;
        }

        final SWMRNibbleArray[] nibbles = ((ExtendedChunk) chunk).getSkyNibbles();
        if (nibbles == null) {
            return 15;
        }

        final SWMRNibbleArray nibble = nibbles[cy - this.minLightSection];
        if (nibble == null || nibble.isNullNibbleVisible()) {
            // check emptiness map for above
            final boolean[] emptinessMap = ((ExtendedChunk) chunk).getSkyEmptinessMap();
            if (emptinessMap == null) {
                return 15;
            }
            // find the lowest non-empty section
            int lowestY = this.minLightSection - 1;
            for (int currY = this.maxSection; currY >= this.minSection; --currY) {
                if (!emptinessMap[currY - this.minSection]) {
                    lowestY = currY;
                    break;
                }
            }
            if (cy > lowestY) {
                return 15;
            }
            // find first non-null nibble above
            for (int currY = cy + 1; currY <= this.maxLightSection; ++currY) {
                final SWMRNibbleArray above = nibbles[currY - this.minLightSection];
                if (above != null && !above.isNullNibbleVisible()) {
                    return above.getVisible(x, 0, z);
                }
            }
            return 15;
        }

        if (this.isClientSide) {
            return nibble.getUpdating(x, y, z);
        } else {
            return nibble.getVisible(x, y, z);
        }
    }

    /**
     * Gets the block light level from SWMR data for a given position.
     */
    public int getBlockLightLevel(final BlockPos pos) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        final int cy = y >> 4;

        if (cy < this.minLightSection || cy > this.maxLightSection) {
            return 0;
        }

        final Chunk chunk = this.getAnyChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return 0;
        }

        final SWMRNibbleArray[] nibbles = ((ExtendedChunk) chunk).getBlockNibbles();
        if (nibbles == null) {
            return 0;
        }

        final SWMRNibbleArray nibble = nibbles[cy - this.minLightSection];
        if (nibble == null) {
            return 0;
        }

        if (this.isClientSide) {
            return nibble.getUpdating(x, y, z);
        } else {
            return nibble.getVisible(x, y, z);
        }
    }

    protected static final class LightQueue {

        protected final Long2ObjectLinkedOpenHashMap<ChunkTasks> chunkTasks = new Long2ObjectLinkedOpenHashMap<>();
        protected final StarLightInterface manager;

        public LightQueue(final StarLightInterface manager) {
            this.manager = manager;
        }

        public boolean isEmpty() {
            return this.chunkTasks.isEmpty();
        }

        public void queueBlockChange(final BlockPos pos) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);
            tasks.changedPositions.add(pos.toImmutable());
        }

        public void queueSectionChange(final int sectionX, final int sectionY, final int sectionZ, final boolean newEmptyValue) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(sectionX, sectionZ), ChunkTasks::new);
            if (tasks.changedSectionSet == null) {
                tasks.changedSectionSet = new Boolean[this.manager.maxSection - this.manager.minSection + 1];
            }
            tasks.changedSectionSet[sectionY - this.manager.minSection] = Boolean.valueOf(newEmptyValue);
        }

        public void queueChunkLighting(final ChunkPos pos, final Runnable lightTask) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos.x, pos.z), ChunkTasks::new);
            if (tasks.lightTasks == null) {
                tasks.lightTasks = new ArrayList<>();
            }
            tasks.lightTasks.add(lightTask);
        }

        public void removeChunk(final ChunkPos pos) {
            final ChunkTasks tasks = this.chunkTasks.remove(CoordinateUtils.getChunkKey(pos.x, pos.z));
            if (tasks != null) {
                tasks.completed = true;
            }
        }

        public ChunkTasks removeFirstTask() {
            if (this.chunkTasks.isEmpty()) {
                return null;
            }
            return this.chunkTasks.removeFirst();
        }

        protected static final class ChunkTasks {

            public final Set<BlockPos> changedPositions = new HashSet<>();
            public final long chunkCoordinate;
            public Boolean[] changedSectionSet;
            public ShortOpenHashSet queuedEdgeChecksSky;
            public ShortOpenHashSet queuedEdgeChecksBlock;
            public List<Runnable> lightTasks;
            public volatile boolean completed;

            public ChunkTasks(final long chunkCoordinate) {
                this.chunkCoordinate = chunkCoordinate;
            }
        }
    }
}
