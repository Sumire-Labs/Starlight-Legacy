package jp.s12kuma01.starlightlegacy.common.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class CoordinateUtils {

    public static int getNeighbourMappedIndex(final int dx, final int dz, final int radius) {
        return (dx + radius) + (2 * radius + 1) * (dz + radius);
    }

    public static long getChunkKey(final BlockPos pos) {
        return ((long)(pos.getZ() >> 4) << 32) | ((pos.getX() >> 4) & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final ChunkPos pos) {
        return ((long)pos.z << 32) | (pos.x & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final int x, final int z) {
        return ((long)z << 32) | (x & 0xFFFFFFFFL);
    }

    public static int getChunkX(final long chunkKey) {
        return (int)chunkKey;
    }

    public static int getChunkZ(final long chunkKey) {
        return (int)(chunkKey >>> 32);
    }

    public static long getBlockKey(final int x, final int y, final int z) {
        return ((long)x & 0x7FFFFFF) | (((long)z & 0x7FFFFFF) << 27) | ((long)y << 54);
    }

    public static long getBlockKey(final BlockPos pos) {
        return ((long)pos.getX() & 0x7FFFFFF) | (((long)pos.getZ() & 0x7FFFFFF) << 27) | ((long)pos.getY() << 54);
    }

    private CoordinateUtils() {
        throw new RuntimeException();
    }
}
