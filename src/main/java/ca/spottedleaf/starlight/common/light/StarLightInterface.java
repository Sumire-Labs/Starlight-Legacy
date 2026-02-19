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
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

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
        // Only queue edge checks for sections near non-empty content.
        // Checking all 18 sections is wasteful when most chunks have only a few
        // non-empty sections (surface-level terrain).
        final Chunk chunk = this.getAnyChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }

        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        final ShortOpenHashSet sectionsToCheck = new ShortOpenHashSet();
        for (int s = this.minSection; s <= this.maxSection; s++) {
            final int idx = s - this.minSection;
            if (idx >= 0 && idx < sections.length && sections[idx] != null && !sections[idx].isEmpty()) {
                // Include this section and its vertical neighbors for edge checking
                for (int dy = -1; dy <= 1; dy++) {
                    final int sy = s + dy;
                    if (sy >= this.minLightSection && sy <= this.maxLightSection) {
                        sectionsToCheck.add((short) sy);
                    }
                }
            }
        }

        if (sectionsToCheck.isEmpty()) {
            return;
        }

        if (this.hasSkyLight) {
            this.lightQueue.queueChunkSkylightEdgeCheck(chunkX, chunkZ, sectionsToCheck);
        }
        if (this.hasBlockLight) {
            this.lightQueue.queueChunkBlocklightEdgeCheck(chunkX, chunkZ, sectionsToCheck);
        }
    }

    public void removeChunkTasks(final int chunkX, final int chunkZ) {
        this.lightQueue.removeChunk(chunkX, chunkZ);
    }

    /**
     * Maximum time (nanoseconds) to spend on light propagation per frame.
     * Only used on the client to prevent lag spikes during batch chunk loading.
     * Server has no budget — all tasks are drained immediately. In 1.12.2 there
     * is no threaded light engine, so deferring work with a budget only delays it
     * until ensureChunkLit() forces synchronous processing. Draining everything
     * at once amortizes engine setup/teardown across all queued chunks.
     */
    private static final long MAX_PROPAGATION_TIME_NS_CLIENT = 8_000_000L;  // 8ms

    public void propagateChanges() {
        if (this.lightQueue.isEmpty()) {
            return;
        }

        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            LightQueue.ChunkTasks task;
            if (this.isClientSide) {
                final long startTime = System.nanoTime();
                while ((task = this.lightQueue.removeFirstTask()) != null) {
                    this.processTask(task, skyEngine, blockEngine);
                    if ((System.nanoTime() - startTime) > MAX_PROPAGATION_TIME_NS_CLIENT) {
                        break;
                    }
                }
            } else {
                // Server: drain all pending tasks immediately
                while ((task = this.lightQueue.removeFirstTask()) != null) {
                    this.processTask(task, skyEngine, blockEngine);
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
    }

    /**
     * Ensure pending light tasks are processed before chunk serialization.
     * Drains ALL pending tasks (not just the target chunk) so that subsequent
     * calls in the same tick (batch chunk sends) find an empty queue and return
     * immediately, avoiding repeated engine setup/teardown overhead.
     */
    public void ensureChunkLit(final int chunkX, final int chunkZ) {
        this.propagateChanges();
    }

    /**
     * Queue a newly generated chunk for deferred lighting.
     * The lightChunk task is added to the LightQueue and processed
     * during propagateChanges() or ensureChunkLit().
     */
    public void queueLightChunk(final Chunk chunk) {
        final int chunkX = chunk.x;
        final int chunkZ = chunk.z;

        // Add lightChunk as a deferred task.
        // emptySections is computed at execution time (not capture time) so it sees
        // the final terrain after populate() has placed trees, structures, etc.
        // Edge checks are NOT queued here: light() already handles neighbor
        // initialization via handleEmptySectionChanges(), and neighbor chunks will
        // queue their own edge checks when they load. This cuts per-chunk work
        // from 4 setupCaches cycles to 2 (~50% less overhead).
        this.lightQueue.queueChunkLighting(chunkX, chunkZ, () -> {
            final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
            this.lightChunk(chunk, emptySections);
            ((ExtendedChunk)chunk).setStarlightLightInitialized(true);
        });
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
            synchronized (this) {
                this.chunkTasks.remove(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            }
        }

        public synchronized ChunkTasks removeFirstTask() {
            if (this.chunkTasks.isEmpty()) {
                return null;
            }
            return this.chunkTasks.removeFirst();
        }

        public static final class ChunkTasks {

            public final Set<BlockPos> changedPositions = new ObjectOpenHashSet<>();
            public Boolean[] changedSectionSet;
            public ShortOpenHashSet queuedEdgeChecksSky;
            public ShortOpenHashSet queuedEdgeChecksBlock;
            public List<Runnable> lightTasks;

            public final long chunkCoordinate;

            public ChunkTasks(final long chunkCoordinate) {
                this.chunkCoordinate = chunkCoordinate;
            }
        }
    }
}
