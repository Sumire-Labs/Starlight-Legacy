package ca.spottedleaf.starlight.common.world;

import net.minecraft.world.chunk.Chunk;

public interface ExtendedWorld {

    Chunk getChunkAtImmediately(final int chunkX, final int chunkZ);

    Chunk getAnyChunkImmediately(final int chunkX, final int chunkZ);
}
