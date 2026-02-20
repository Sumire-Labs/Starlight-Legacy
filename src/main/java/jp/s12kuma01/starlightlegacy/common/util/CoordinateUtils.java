package jp.s12kuma01.starlightlegacy.common.util;

import net.minecraft.util.math.BlockPos;

public final class CoordinateUtils {

    private CoordinateUtils() {
        throw new RuntimeException();
    }

    public static long getChunkKey(final BlockPos pos) {
        return ((long) (pos.getZ() >> 4) << 32) | ((pos.getX() >> 4) & 0xFFFFFFFFL);
    }

    public static long getChunkKey(final int x, final int z) {
        return ((long) z << 32) | (x & 0xFFFFFFFFL);
    }

    public static int getChunkX(final long chunkKey) {
        return (int) chunkKey;
    }

    public static int getChunkZ(final long chunkKey) {
        return (int) (chunkKey >>> 32);
    }
}
