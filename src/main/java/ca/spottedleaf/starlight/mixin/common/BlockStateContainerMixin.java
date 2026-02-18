package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.block.ILitBlock;
import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import ca.spottedleaf.starlight.common.blockstate.ILightInfoProvider;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class BlockStateContainerMixin implements ExtendedAbstractBlockState, ILightInfoProvider {

    @Shadow @Final
    private Block block;

    @Unique
    private int cachedOpacity = -2; // -2 = not computed yet

    // === ExtendedAbstractBlockState ===

    @Override
    public boolean isConditionallyFullOpaque() {
        // 1.12.2 has no VoxelShape, so never conditionally opaque
        return false;
    }

    @Override
    public int getOpacityIfCached() {
        if (this.cachedOpacity == -2) {
            final int defaultOpacity = this.block.getLightOpacity((BlockStateContainer.StateImplementation)(Object)this);

            // Only cache values guaranteed to be position-independent:
            // - 0 (fully transparent: air, glass, etc.)
            // - >= 15 (fully opaque: stone, dirt, etc.)
            // Everything else might vary by position in Forge's getLightOpacity(state, world, pos),
            // so return -1 (uncacheable) to force the caller to query the world.
            if (defaultOpacity == 0 || defaultOpacity >= 15) {
                this.cachedOpacity = defaultOpacity;
            } else {
                this.cachedOpacity = -1;
            }
        }
        return this.cachedOpacity;
    }

    // === ILightInfoProvider ===
    // Delegates to the Block's ILitBlock methods, passing this state as context.

    @Override
    public int starlight$getLightFor(final IBlockAccess blockAccess, final EnumSkyBlock lightType, final BlockPos blockPos) {
        return ((ILitBlock) this.block).starlight$getLightFor((IBlockState) this, blockAccess, lightType, blockPos);
    }

    @Override
    public boolean starlight$useNeighborBrightness(final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        return ((ILitBlock) this.block).starlight$useNeighborBrightness((IBlockState) this, facing, blockAccess, blockPos);
    }

    @Override
    public int starlight$getLightOpacity(final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        return ((ILitBlock) this.block).starlight$getLightOpacity((IBlockState) this, facing, blockAccess, blockPos);
    }
}
