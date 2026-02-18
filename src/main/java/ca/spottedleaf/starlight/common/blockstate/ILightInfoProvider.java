package ca.spottedleaf.starlight.common.blockstate;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;

/**
 * Interface injected onto {@link net.minecraft.block.state.IBlockState} implementations via Mixin.
 * Delegates directional lighting queries to the block's {@link ca.spottedleaf.starlight.common.block.ILitBlock}.
 */
public interface ILightInfoProvider {

    int starlight$getLightFor(final IBlockAccess blockAccess, final EnumSkyBlock lightType, final BlockPos blockPos);

    boolean starlight$useNeighborBrightness(final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos);

    int starlight$getLightOpacity(final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos);
}
