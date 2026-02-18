package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.block.ILitBlock;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.block.BlockStairs.EnumHalf.TOP;

/**
 * Fixes non-full-block lighting for stairs.
 * An upside-down stair borrows brightness from below (its exposed face).
 * A right-side-up stair borrows brightness from above (its exposed face).
 */
@Mixin(BlockStairs.class)
public abstract class BlockStairsMixin extends BlockMixin implements ILitBlock {

    @Shadow @Final
    public static PropertyEnum<BlockStairs.EnumHalf> HALF;

    @Override
    public boolean starlight$useNeighborBrightness(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        if (facing.getAxis() != EnumFacing.Axis.Y) {
            return false;
        }

        // Top (upside-down) stair → exposed face is DOWN → borrow brightness from below
        // Bottom (normal) stair → exposed face is UP → borrow brightness from above
        return facing == (blockState.getValue(HALF) == TOP ? EnumFacing.DOWN : EnumFacing.UP);
    }
}
