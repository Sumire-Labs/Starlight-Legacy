package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VariableBlockLightHandlerImpl implements VariableBlockLightHandler {

    protected final Long2ObjectOpenHashMap<Set<BlockPos>> positionsByChunk = new Long2ObjectOpenHashMap<>();
    protected final Long2IntOpenHashMap lightValuesByPosition = new Long2IntOpenHashMap();
    {
        this.lightValuesByPosition.defaultReturnValue(-1);
        this.positionsByChunk.defaultReturnValue(Collections.emptySet());
    }

    @Override
    public int getLightLevel(final int x, final int y, final int z) {
        return this.lightValuesByPosition.get(CoordinateUtils.getBlockKey(x, y, z));
    }

    @Override
    public Collection<BlockPos> getCustomLightPositions(final int chunkX, final int chunkZ) {
        return new HashSet<>(this.positionsByChunk.get(CoordinateUtils.getChunkKey(chunkX, chunkZ)));
    }

    public void setSource(final int x, final int y, final int z, final int to) {
        if (to < 0 || to > 15) {
            throw new IllegalArgumentException();
        }
        if (this.lightValuesByPosition.put(CoordinateUtils.getBlockKey(x, y, z), to) == -1) {
            this.positionsByChunk.computeIfAbsent(CoordinateUtils.getChunkKey(x >> 4, z >> 4), (final long keyInMap) -> {
                return new HashSet<>();
            }).add(new BlockPos(x, y, z));
        }
    }

    public int removeSource(final int x, final int y, final int z) {
        final int ret = this.lightValuesByPosition.remove(CoordinateUtils.getBlockKey(x, y, z));
        if (ret != -1) {
            final long chunkKey = CoordinateUtils.getChunkKey(x >> 4, z >> 4);
            final Set<BlockPos> positions = this.positionsByChunk.get(chunkKey);
            positions.remove(new BlockPos(x, y, z));
            if (positions.isEmpty()) {
                this.positionsByChunk.remove(chunkKey);
            }
        }
        return ret;
    }
}
