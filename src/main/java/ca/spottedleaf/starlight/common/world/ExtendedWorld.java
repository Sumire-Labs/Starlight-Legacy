package ca.spottedleaf.starlight.common.world;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.VariableBlockLightHandler;
import net.minecraft.world.chunk.Chunk;

public interface ExtendedWorld {

    Chunk getChunkAtImmediately(final int chunkX, final int chunkZ);

    VariableBlockLightHandler getCustomLightHandler();

    void setCustomLightHandler(final VariableBlockLightHandler handler);

    StarLightInterface getLightEngine();
}
