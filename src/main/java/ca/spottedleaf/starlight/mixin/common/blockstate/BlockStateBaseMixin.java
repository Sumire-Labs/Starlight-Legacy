package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into StateImplementation (inner class of BlockStateContainer) to cache opacity data.
 * Uses lazy initialization - computed on first access.
 */
@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class BlockStateBaseMixin implements ExtendedAbstractBlockState {

    @Unique
    private int starlight$opacityIfCached = Integer.MIN_VALUE; // sentinel for uninitialized

    @Unique
    private boolean starlight$isConditionallyFullOpaque;

    @Unique
    private void starlight$initCache() {
        if (this.starlight$opacityIfCached != Integer.MIN_VALUE) {
            return;
        }

        final IBlockState self = (IBlockState)(Object)this;
        final Block block = self.getBlock();

        // A block is "conditionally full opaque" if it is opaque but uses shape for light occlusion
        // In 1.12.2 terms: it's a full opaque cube that might have directional transparency (stairs, slabs)
        // We check isOpaqueCube and whether the block has variable opacity
        final boolean isOpaque = self.isOpaqueCube();
        final boolean hasVariableOpacity = block.isOpaqueCube(self) != self.isOpaqueCube();
        // Use a heuristic: if it's opaque but the block uses model occlusion, it might be conditionally transparent
        this.starlight$isConditionallyFullOpaque = isOpaque && self.useNeighborBrightness();

        if (this.starlight$isConditionallyFullOpaque) {
            // Variable opacity - cannot cache
            this.starlight$opacityIfCached = -1;
        } else {
            // Try to get a constant opacity
            final int opacity = self.getLightOpacity();
            this.starlight$opacityIfCached = opacity;
        }
    }

    @Override
    public final boolean isConditionallyFullOpaque() {
        this.starlight$initCache();
        return this.starlight$isConditionallyFullOpaque;
    }

    @Override
    public final int getOpacityIfCached() {
        this.starlight$initCache();
        return this.starlight$opacityIfCached;
    }
}
