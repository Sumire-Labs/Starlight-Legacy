package ca.spottedleaf.starlight.common.light;

import net.minecraft.util.math.BlockPos;
import java.util.Collection;

public interface VariableBlockLightHandler {

    int getLightLevel(final int x, final int y, final int z);

    Collection<BlockPos> getCustomLightPositions(final int chunkX, final int chunkZ);
}
