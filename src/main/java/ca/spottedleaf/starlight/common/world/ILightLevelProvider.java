package ca.spottedleaf.starlight.common.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

/**
 * Interface injected onto {@link net.minecraft.world.ChunkCache} via Mixin.
 * Provides raw light level queries for the directional neighbor brightness system.
 */
public interface ILightLevelProvider {

    int starlight$getLight(final EnumSkyBlock lightType, final BlockPos blockPos);
}
