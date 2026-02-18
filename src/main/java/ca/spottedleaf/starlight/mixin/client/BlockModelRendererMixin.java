package ca.spottedleaf.starlight.mixin.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fix MC-225516: blocks with light emission value of 1 (brown mushrooms, dragon eggs, etc.)
 * incorrectly skip ambient occlusion in the block model renderer.
 *
 * In vanilla, renderModel checks if getLightValue > 0 to decide whether to use AO.
 * This redirect subtracts 1 and clamps, so light=1 → 0 (AO still applies),
 * while light >= 2 still correctly skips AO.
 */
@SideOnly(Side.CLIENT)
@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererMixin {

    @Redirect(
            method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getLightValue(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I", remap = false),
            require = 0
    )
    private int starlight$adjustGetLightValue(final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos blockPos) {
        return Math.max(0, Math.min(15, blockState.getLightValue(blockAccess, blockPos) - 1));
    }
}
