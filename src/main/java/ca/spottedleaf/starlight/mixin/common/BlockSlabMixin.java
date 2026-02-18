package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.block.ILitBlock;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.block.BlockSlab.EnumBlockHalf.TOP;

/**
 * Fixes non-full-block lighting for slabs.
 * A top slab borrows brightness from below (its exposed face).
 * A bottom slab borrows brightness from above (its exposed face).
 */
@Mixin(BlockSlab.class)
public abstract class BlockSlabMixin extends BlockMixin implements ILitBlock {

    @Shadow @Final
    public static PropertyEnum<BlockSlab.EnumBlockHalf> HALF;

    @Override
    public boolean starlight$useNeighborBrightness(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        if (facing.getAxis() != EnumFacing.Axis.Y) {
            return false;
        }

        // Double slabs are full cubes - no neighbor brightness needed
        if (((BlockSlab) (Object) this).isFullCube(blockState)) {
            return false;
        }

        // Top slab → exposed face is DOWN → borrow brightness from below
        // Bottom slab → exposed face is UP → borrow brightness from above
        return facing == (blockState.getValue(HALF) == TOP ? EnumFacing.DOWN : EnumFacing.UP);
    }
}
