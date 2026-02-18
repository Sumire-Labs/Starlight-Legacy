package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.block.ILitBlock;
import ca.spottedleaf.starlight.common.blockstate.ILightInfoProvider;
import ca.spottedleaf.starlight.common.util.LightUtil;
import ca.spottedleaf.starlight.common.world.ILightLevelProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Block.class)
public abstract class BlockMixin implements ILitBlock {

    @Shadow @Deprecated
    public abstract int getLightValue(final IBlockState blockState);

    /**
     * @author Starlight (ported from Alfheim)
     * @reason Simplify getPackedLightmapCoords to delegate to getCombinedLight.
     *         Removes vanilla's built-in neighbor brightness check (only checked UP),
     *         because our getLightForExt override in ChunkCacheMixin handles directional
     *         neighbor brightness properly for all faces.
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    public int getPackedLightmapCoords(final IBlockState blockState, final IBlockAccess source, final BlockPos blockPos) {
        return source.getCombinedLight(blockPos, LightUtil.getLightValueForState(blockState, source, blockPos));
    }

    /**
     * @author Starlight (ported from Alfheim)
     * @reason Fix MC-225516: blocks with light value 1 (mushrooms, dragon eggs, etc.)
     *         incorrectly skip ambient occlusion. Subtract 1 and clamp so light=1 → 0,
     *         which re-enables AO. Blocks with light >= 2 still bypass AO as intended.
     */
    @Overwrite
    @SideOnly(Side.CLIENT)
    public float getAmbientOcclusionLightValue(final IBlockState blockState) {
        final int lightValue = Math.max(0, Math.min(15, blockState.getLightValue() - 1));

        if (lightValue == 0) {
            return blockState.isBlockNormalCube() ? 0.2F : 1.0F;
        } else {
            return 1.0F;
        }
    }

    @Override
    public int starlight$getLightFor(final IBlockState blockState, final IBlockAccess blockAccess, final EnumSkyBlock lightType, final BlockPos blockPos) {
        int lightLevel = ((ILightLevelProvider) blockAccess).starlight$getLight(lightType, blockPos);

        if (lightLevel == 15) {
            return lightLevel;
        }

        // Check if this block should use neighbor brightness:
        // - blocks with the useNeighborBrightness flag (stairs, slabs, fences, etc.)
        // - liquid blocks (vanilla doesn't set the flag for liquids, but they need it)
        final Block block = blockState.getBlock();
        if (!blockState.useNeighborBrightness()
                && !(block instanceof BlockLiquid)
                && !(block instanceof BlockFluidBase)) {
            return lightLevel;
        }

        for (final EnumFacing facing : EnumFacing.VALUES) {
            if (((ILightInfoProvider) blockState).starlight$useNeighborBrightness(facing, blockAccess, blockPos)) {
                int opacity = ((ILightInfoProvider) blockState).starlight$getLightOpacity(facing, blockAccess, blockPos);
                final int neighborLightLevel = ((ILightLevelProvider) blockAccess).starlight$getLight(lightType, blockPos.offset(facing));

                // Prevent sky light from propagating through transparent blocks without any cost
                if (opacity == 0 && (lightType != EnumSkyBlock.SKY || neighborLightLevel != EnumSkyBlock.SKY.defaultLightValue)) {
                    opacity = 1;
                }

                lightLevel = Math.max(lightLevel, neighborLightLevel - opacity);

                if (lightLevel == 15) {
                    return lightLevel;
                }
            }
        }

        return lightLevel;
    }

    @Override
    public boolean starlight$useNeighborBrightness(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        // Default: only borrow brightness from the face above (UP).
        // Overridden by BlockSlabMixin and BlockStairsMixin for directional behavior.
        return facing == EnumFacing.UP;
    }

    @Override
    public int starlight$getLightOpacity(final IBlockState blockState, final EnumFacing facing, final IBlockAccess blockAccess, final BlockPos blockPos) {
        return 0;
    }
}
