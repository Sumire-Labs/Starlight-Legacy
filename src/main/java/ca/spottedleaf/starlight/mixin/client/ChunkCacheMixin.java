package ca.spottedleaf.starlight.mixin.client;

import ca.spottedleaf.starlight.common.blockstate.ILightInfoProvider;
import ca.spottedleaf.starlight.common.world.ILightLevelProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Hooks into the client-side rendering pipeline to enable directional neighbor brightness.
 * ChunkCache is the IBlockAccess used by the chunk rendering system.
 * getLightForExt is called by getCombinedLight during chunk mesh building.
 */
@Mixin(ChunkCache.class)
public abstract class ChunkCacheMixin implements ILightLevelProvider {

    @SideOnly(Side.CLIENT)
    @Shadow
    public abstract int getLightFor(final EnumSkyBlock lightType, final BlockPos blockPos);

    @Shadow
    public abstract IBlockState getBlockState(final BlockPos pos);

    /**
     * @author Starlight (ported from Alfheim)
     * @reason Route light queries through the directional neighbor brightness system.
     *         Instead of vanilla's simple lookup, this delegates to the block state's
     *         ILightInfoProvider which applies per-block directional neighbor brightness.
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    private int getLightForExt(final EnumSkyBlock lightType, final BlockPos blockPos) {
        return ((ILightInfoProvider) getBlockState(blockPos)).starlight$getLightFor(
                (ChunkCache) (Object) this, lightType, blockPos);
    }

    @Override
    public int starlight$getLight(final EnumSkyBlock lightType, final BlockPos blockPos) {
        return getLightFor(lightType, blockPos);
    }
}
