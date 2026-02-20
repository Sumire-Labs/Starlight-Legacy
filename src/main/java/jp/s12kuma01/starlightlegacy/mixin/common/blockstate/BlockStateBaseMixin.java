package jp.s12kuma01.starlightlegacy.mixin.common.blockstate;

import jp.s12kuma01.starlightlegacy.common.blockstate.ExtendedAbstractBlockState;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
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

        final IBlockState self = (IBlockState) (Object) this;

        // A block is "conditionally full opaque" if it is opaque but has per-face transparency.
        // In 1.12.2, useNeighborBrightness() is the best proxy for "has directional light behavior"
        // (e.g. modded blocks with per-face occlusion). No vanilla block satisfies both conditions,
        // but the doesSideBlockRendering() check in the propagation code handles the per-face case
        // when BLOCK_SPECIAL_TRANSPARENCY is set.
        final boolean isOpaque = self.isOpaqueCube();
        this.starlight$isConditionallyFullOpaque = isOpaque && self.useNeighborBrightness();

        if (this.starlight$isConditionallyFullOpaque) {
            // Variable opacity - cannot cache
            this.starlight$opacityIfCached = -1;
        } else {
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
