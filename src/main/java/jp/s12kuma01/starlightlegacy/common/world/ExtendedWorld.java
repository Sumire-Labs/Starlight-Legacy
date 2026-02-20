package jp.s12kuma01.starlightlegacy.common.world;

import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.light.VariableBlockLightHandler;
import net.minecraft.world.chunk.Chunk;

public interface ExtendedWorld {

    Chunk getChunkAtImmediately(final int chunkX, final int chunkZ);

    VariableBlockLightHandler getCustomLightHandler();

    void setCustomLightHandler(final VariableBlockLightHandler handler);

    StarLightInterface getLightEngine();
}
