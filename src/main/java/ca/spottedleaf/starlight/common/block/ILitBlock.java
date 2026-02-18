package ca.spottedleaf.starlight.common.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;

/**
 * Interface injected onto {@link net.minecraft.block.Block} via Mixin.
 * Provides per-block directional neighbor brightness for non-full-block lighting fixes.
 * Subclass mixins (BlockSlabMixin, BlockStairsMixin) override these methods for specific block types.
 */
public interface ILitBlock {

    int starlight$getLightFor(final IBlockState blockState, final IBlockAccess blockAccess, final EnumSkyBlock lightType, final BlockPos blockPos);

    boolean starlight$useNeighborBrightness(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos);

    int starlight$getLightOpacity(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos);
}
