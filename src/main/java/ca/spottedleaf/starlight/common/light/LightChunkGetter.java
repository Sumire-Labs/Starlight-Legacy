package ca.spottedleaf.starlight.common.light;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Interface for getting chunks for the lighting engine.
 * In 1.12.2 this is simplified since there's only one chunk type.
 */
public interface LightChunkGetter {

    Chunk getChunkForLighting(final int chunkX, final int chunkZ);

    World getWorld();
}
