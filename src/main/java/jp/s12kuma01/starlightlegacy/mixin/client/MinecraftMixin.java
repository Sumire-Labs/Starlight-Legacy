package jp.s12kuma01.starlightlegacy.mixin.client;

import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedRenderGlobal;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.profiler.Profiler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    @Final
    public Profiler profiler;

    @Shadow
    public RenderGlobal renderGlobal;
    @Shadow
    public WorldClient world;
    @Shadow
    private boolean isGamePaused;

    /**
     * Process client-side light propagation and render global light updates during runTick.
     */
    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=level", shift = At.Shift.BEFORE))
    private void onRunTick(final CallbackInfo ci) {
        this.profiler.endStartSection("starlightClientLightUpdates");

        if (!this.isGamePaused && this.world != null) {
            // Process client-side light changes
            final StarLightInterface lightEngine = ((ExtendedWorld) this.world).getLightEngine();
            if (lightEngine != null && lightEngine.hasUpdates()) {
                lightEngine.propagateChanges();
            }

            // Process render global light updates (batched notifyLightSet calls)
            ((ExtendedRenderGlobal) this.renderGlobal).starlight$processLightUpdates();
        }
    }
}
