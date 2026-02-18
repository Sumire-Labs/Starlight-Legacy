package ca.spottedleaf.starlight.mixin.client;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {

    @Shadow private WorldClient world;

    @Inject(method = "updateClouds", at = @At("HEAD"))
    private void onUpdateClouds(CallbackInfo ci) {
        // Process light updates on the client side
        if (this.world != null) {
            final StarLightInterface lightEngine = ((StarLightLightingProvider)this.world).getLightEngine();
            if (lightEngine != null) {
                lightEngine.propagateChanges();
            }
        }
    }
}
