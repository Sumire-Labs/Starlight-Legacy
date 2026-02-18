package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class StarLightInterface {

    protected final World world;

    protected final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    protected final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    protected final LightQueue lightQueue = new LightQueue(this);

    protected final boolean isClientSide;

    protected final int minSection;
    protected final int maxSection;
    protected final int minLightSection;
    protected final int maxLightSection;

    private final boolean hasBlockLight;
    private final boolean hasSkyLight;

    public StarLightInterface(final World world, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.world = world;
        this.cachedSkyPropagators = hasSkyLight && world != null ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight && world != null ? new ArrayDeque<>() : null;
        this.isClientSide = world != null && world.isRemote;
        if (world == null) {
            this.minSection = 0;
            this.maxSection = 15;
            this.minLightSection = -1;
            this.maxLightSection = 16;
        } else {
            this.minSection = WorldUtil.getMinSection(world);
            this.maxSection = WorldUtil.getMaxSection(world);
            this.minLightSection = WorldUtil.getMinLightSection(world);
            this.maxLightSection = WorldUtil.getMaxLightSection(world);
        }
        this.hasBlockLight = hasBlockLight;
        this.hasSkyLight = hasSkyLight;
    }

    public boolean hasSkyLight() {
        return this.hasSkyLight;
    }

    public boolean hasBlockLight() {
        return this.hasBlockLight;
    }

    public int getSkyLightValue(final BlockPos blockPos, final Chunk chunk) {
        if (!this.hasSkyLight) {
            return 0;
        }
        final int x = blockPos.getX();
        int y = blockPos.getY();
        final int z = blockPos.getZ();

        if (chunk == null) {
            return 15;
        }

        int sectionY = y >> 4;

        if (sectionY > this.maxLightSection) {
            return 15;
        }

        if (sectionY < this.minLightSection) {
            sectionY = this.minLightSection;
            y = sectionY << 4;
        }

        final SWMRNibbleArray[] nibbles = ((ExtendedChunk)chunk).getSkyNibbles();
        final SWMRNibbleArray immediate = nibbles[sectionY - this.minLightSection];

        if (!immediate.isNullNibbleVisible()) {
            return immediate.getVisible(x, y, z);
        }

        final boolean[] emptinessMap = ((ExtendedChunk)chunk).getSkyEmptinessMap();

        if (emptinessMap == null) {
            return 15;
        }

        int lowestY = this.minLightSection - 1;
        for (int currY = this.maxSection; currY >= this.minSection; --currY) {
            if (emptinessMap[currY - this.minSection]) {
                continue;
            }
            lowestY = currY;
            break;
        }

        if (sectionY > lowestY) {
            return 15;
        }

        for (int currY = sectionY + 1; currY <= this.maxLightSection; ++currY) {
            final SWMRNibbleArray nibble = nibbles[currY - this.minLightSection];
            if (!nibble.isNullNibbleVisible()) {
                return nibble.getVisible(x, 0, z);
            }
        }

        return 15;
    }

    public int getBlockLightValue(final BlockPos blockPos, final Chunk chunk) {
        if (!this.hasBlockLight) {
            return 0;
        }
        final int y = blockPos.getY();
        final int cy = y >> 4;

        if (cy < this.minLightSection || cy > this.maxLightSection) {
            return 0;
        }

        if (chunk == null) {
            return 0;
        }

        final SWMRNibbleArray nibble = ((ExtendedChunk)chunk).getBlockNibbles()[cy - this.minLightSection];
        return nibble.getVisible(blockPos.getX(), y, blockPos.getZ());
    }

    public int getRawBrightness(final BlockPos pos, final int ambientDarkness) {
        final Chunk chunk = this.getAnyChunkNow(pos.getX() >> 4, pos.getZ() >> 4);

        final int sky = this.getSkyLightValue(pos, chunk) - ambientDarkness;
        if (sky == 15) {
            return 15;
        }
        final int block = this.getBlockLightValue(pos, chunk);
        return Math.max(sky, block);
    }

    public boolean isClientSide() {
        return this.isClientSide;
    }

    public Chunk getAnyChunkNow(final int chunkX, final int chunkZ) {
        if (this.world == null) {
            return null;
        }
        return ((ExtendedWorld)this.world).getAnyChunkImmediately(chunkX, chunkZ);
    }

    public boolean hasUpdates() {
        return !this.lightQueue.isEmpty();
    }

    public World getWorld() {
        return this.world;
    }

    protected final SkyStarLightEngine getSkyLightEngine() {
        if (this.cachedSkyPropagators == null) {
            return null;
        }
        final SkyStarLightEngine ret;
        synchronized (this.cachedSkyPropagators) {
            ret = this.cachedSkyPropagators.pollFirst();
        }

        if (ret == null) {
            return new SkyStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseSkyLightEngine(final SkyStarLightEngine engine) {
        if (this.cachedSkyPropagators == null || engine == null) {
            return;
        }
        synchronized (this.cachedSkyPropagators) {
            this.cachedSkyPropagators.addFirst(engine);
        }
    }

    protected final BlockStarLightEngine getBlockLightEngine() {
        if (this.cachedBlockPropagators == null) {
            return null;
        }
        final BlockStarLightEngine ret;
        synchronized (this.cachedBlockPropagators) {
            ret = this.cachedBlockPropagators.pollFirst();
        }

        if (ret == null) {
            return new BlockStarLightEngine(this.world);
        }
        return ret;
    }

    protected final void releaseBlockLightEngine(final BlockStarLightEngine engine) {
        if (this.cachedBlockPropagators == null || engine == null) {
            return;
        }
        synchronized (this.cachedBlockPropagators) {
            this.cachedBlockPropagators.addFirst(engine);
        }
    }

    public LightQueue.ChunkTasks blockChange(final BlockPos pos) {
        if (this.world == null || pos.getY() < WorldUtil.getMinBlockY(this.world) || pos.getY() > WorldUtil.getMaxBlockY(this.world)) {
            return null;
        }

        return this.lightQueue.queueBlockChange(pos);
    }

    public LightQueue.ChunkTasks sectionChange(final int sectionX, final int sectionY, final int sectionZ, final boolean newEmptyValue) {
        if (this.world == null) {
            return null;
        }

        return this.lightQueue.queueSectionChange(sectionX, sectionY, sectionZ, newEmptyValue);
    }

    public void forceLoadInChunk(final Chunk chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.forceHandleEmptySectionChanges(this.world, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.forceHandleEmptySectionChanges(this.world, chunk, emptySections);
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
                skyEngine.handleEmptySectionChanges(this.world, chunkX, chunkZ, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.handleEmptySectionChanges(this.world, chunkX, chunkZ, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void lightChunk(final Chunk chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.world, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.light(this.world, chunk, emptySections);
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
                skyEngine.checkChunkEdges(this.world, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.world, chunkX, chunkZ);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.world, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ, final ShortCollection sections) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.world, chunkX, chunkZ, sections);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void queueEdgeCheck(final int chunkX, final int chunkZ) {
        // Use section-specific edge check fields instead of a Runnable so that
        // propagateChanges() can combine edge checks with block changes in a single
        // cache setup/teardown cycle (avoiding redundant 5x5 chunk cache operations).
        final ShortOpenHashSet allSections = new ShortOpenHashSet(this.maxLightSection - this.minLightSection + 1);
        for (int s = this.minLightSection; s <= this.maxLightSection; s++) {
            allSections.add((short)s);
        }
        if (this.hasSkyLight) {
            this.lightQueue.queueChunkSkylightEdgeCheck(chunkX, chunkZ, allSections);
        }
        if (this.hasBlockLight) {
            this.lightQueue.queueChunkBlocklightEdgeCheck(chunkX, chunkZ, allSections);
        }
    }

    public void removeChunkTasks(final int chunkX, final int chunkZ) {
        this.lightQueue.removeChunk(chunkX, chunkZ);
    }

    /**
     * Maximum time (nanoseconds) to spend on light propagation per server tick.
     * Prevents the "stop-and-burst" pattern where all queued edge checks process
     * in one tick, stalling chunk generation. Remaining tasks carry over to next tick.
     * Client-side always processes all tasks (no budget) to keep rendering responsive.
     */
    private static final long MAX_PROPAGATION_TIME_NS = 5_000_000L; // 5ms

    public void propagateChanges() {
        if (this.lightQueue.isEmpty()) {
            return;
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        // Server-side: apply time budget to prevent stalling chunk generation.
        // Client-side: process all tasks to keep rendering responsive.
        final long startTime = !this.isClientSide ? System.nanoTime() : 0L;

        try {
            LightQueue.ChunkTasks task;
            while ((task = this.lightQueue.removeFirstTask()) != null) {
                this.processTask(task, skyEngine, blockEngine);

                // Server-side budget check: stop if we've exceeded our time budget.
                // Remaining tasks stay in the queue and will be processed next tick.
                if (startTime != 0L && (System.nanoTime() - startTime) > MAX_PROPAGATION_TIME_NS) {
                    break;
                }
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    private void processTask(final LightQueue.ChunkTasks task,
                             final SkyStarLightEngine skyEngine,
                             final BlockStarLightEngine blockEngine) {
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
        final boolean hasBlockChanges = !positions.isEmpty() || sectionChanges != null;
        final boolean hasSkyEdges = task.queuedEdgeChecksSky != null;
        final boolean hasBlockEdges = task.queuedEdgeChecksBlock != null;

        if (skyEngine != null && (hasBlockChanges || hasSkyEdges)) {
            skyEngine.blocksChangedInChunkAndCheckEdges(
                this.world, chunkX, chunkZ,
                hasBlockChanges ? positions : null,
                hasBlockChanges ? sectionChanges : null,
                hasSkyEdges ? task.queuedEdgeChecksSky : null
            );
        }
        if (blockEngine != null && (hasBlockChanges || hasBlockEdges)) {
            blockEngine.blocksChangedInChunkAndCheckEdges(
                this.world, chunkX, chunkZ,
                hasBlockChanges ? positions : null,
                hasBlockChanges ? sectionChanges : null,
                hasBlockEdges ? task.queuedEdgeChecksBlock : null
            );
        }

        task.onComplete.complete(null);
    }

    /**
     * Ensure a specific chunk's pending light tasks are processed immediately.
     * Used by SPacketChunkData to guarantee light data is correct before serialization.
     */
    public void ensureChunkLit(final int chunkX, final int chunkZ) {
        final LightQueue.ChunkTasks task = this.lightQueue.extractChunkTask(chunkX, chunkZ);
        if (task == null) {
            return;
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            this.processTask(task, skyEngine, blockEngine);
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    /**
     * Queue a newly generated chunk for deferred lighting.
     * lightChunk + edge checks are added to the LightQueue and processed
     * during propagateChanges() or ensureChunkLit().
     */
    public void queueLightChunk(final Chunk chunk) {
        final int chunkX = chunk.x;
        final int chunkZ = chunk.z;

        // Add lightChunk as a deferred task.
        // emptySections is computed at execution time (not capture time) so it sees
        // the final terrain after populate() has placed trees, structures, etc.
        this.lightQueue.queueChunkLighting(chunkX, chunkZ, () -> {
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
            this.lightChunk(chunk, emptySections);
            ((ExtendedChunk)chunk).setStarlightLightInitialized(true);
        });

        // Add edge checks to the same ChunkTasks entry
        final ShortOpenHashSet allSections = new ShortOpenHashSet(this.maxLightSection - this.minLightSection + 1);
        for (int s = this.minLightSection; s <= this.maxLightSection; s++) {
            allSections.add((short)s);
        }
        if (this.hasSkyLight) {
            this.lightQueue.queueChunkSkylightEdgeCheck(chunkX, chunkZ, allSections);
        }
        if (this.hasBlockLight) {
            this.lightQueue.queueChunkBlocklightEdgeCheck(chunkX, chunkZ, allSections);
        }
    }

    public static final class LightQueue {

        protected final Long2ObjectLinkedOpenHashMap<ChunkTasks> chunkTasks = new Long2ObjectLinkedOpenHashMap<>();
        protected final StarLightInterface manager;

        public LightQueue(final StarLightInterface manager) {
            this.manager = manager;
        }

        public synchronized boolean isEmpty() {
            return this.chunkTasks.isEmpty();
        }

        public synchronized ChunkTasks queueBlockChange(final BlockPos pos) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(pos), ChunkTasks::new);
            tasks.changedPositions.add(pos.toImmutable());
            return tasks;
        }

        public synchronized ChunkTasks queueSectionChange(final int sectionX, final int sectionY, final int sectionZ, final boolean newEmptyValue) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(sectionX, sectionZ), ChunkTasks::new);

            if (tasks.changedSectionSet == null) {
                tasks.changedSectionSet = new Boolean[this.manager.maxSection - this.manager.minSection + 1];
            }
            tasks.changedSectionSet[sectionY - this.manager.minSection] = Boolean.valueOf(newEmptyValue);

            return tasks;
        }

        public synchronized ChunkTasks queueChunkLighting(final int chunkX, final int chunkZ, final Runnable lightTask) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(chunkX, chunkZ), ChunkTasks::new);
            if (tasks.lightTasks == null) {
                tasks.lightTasks = new ArrayList<>();
            }
            tasks.lightTasks.add(lightTask);

            return tasks;
        }

        public synchronized ChunkTasks queueChunkSkylightEdgeCheck(final int chunkX, final int chunkZ, final ShortCollection sections) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(chunkX, chunkZ), ChunkTasks::new);

            ShortOpenHashSet queuedEdges = tasks.queuedEdgeChecksSky;
            if (queuedEdges == null) {
                queuedEdges = tasks.queuedEdgeChecksSky = new ShortOpenHashSet();
            }
            queuedEdges.addAll(sections);

            return tasks;
        }

        public synchronized ChunkTasks queueChunkBlocklightEdgeCheck(final int chunkX, final int chunkZ, final ShortCollection sections) {
            final ChunkTasks tasks = this.chunkTasks.computeIfAbsent(CoordinateUtils.getChunkKey(chunkX, chunkZ), ChunkTasks::new);

            ShortOpenHashSet queuedEdges = tasks.queuedEdgeChecksBlock;
            if (queuedEdges == null) {
                queuedEdges = tasks.queuedEdgeChecksBlock = new ShortOpenHashSet();
            }
            queuedEdges.addAll(sections);

            return tasks;
        }

        public void removeChunk(final int chunkX, final int chunkZ) {
            final ChunkTasks tasks;
            synchronized (this) {
                tasks = this.chunkTasks.remove(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            }
            if (tasks != null) {
                tasks.onComplete.complete(null);
            }
        }

        public synchronized ChunkTasks removeFirstTask() {
            if (this.chunkTasks.isEmpty()) {
                return null;
            }
            return this.chunkTasks.removeFirst();
        }

        public synchronized ChunkTasks extractChunkTask(final int chunkX, final int chunkZ) {
            return this.chunkTasks.remove(CoordinateUtils.getChunkKey(chunkX, chunkZ));
        }

        public static final class ChunkTasks {

            public final Set<BlockPos> changedPositions = new ObjectOpenHashSet<>();
            public Boolean[] changedSectionSet;
            public ShortOpenHashSet queuedEdgeChecksSky;
            public ShortOpenHashSet queuedEdgeChecksBlock;
            public List<Runnable> lightTasks;

            public final CompletableFuture<Void> onComplete = new CompletableFuture<>();

            public final long chunkCoordinate;

            public ChunkTasks(final long chunkCoordinate) {
                this.chunkCoordinate = chunkCoordinate;
            }
        }
    }
}
