package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {

    /**
     * Ensure this chunk's light data is fully computed before the constructor serializes it.
     * Only processes the target chunk's pending tasks (not all chunks).
     * Auto-sync in StarLightEngine.updateVisible() keeps vanilla NibbleArrays in sync.
     */
    @Inject(method = "<init>(Lnet/minecraft/world/chunk/Chunk;I)V", at = @At("HEAD"))
    private static void starlight$beforeSerialize(Chunk chunk, int changedSectionFilter, CallbackInfo ci) {
        if (chunk.getWorld() == null || chunk.getWorld().isRemote) {
            return;
        }

        final StarLightInterface lightEngine = ((StarLightLightingProvider)chunk.getWorld()).getLightEngine();
        if (lightEngine != null) {
            lightEngine.ensureChunkLit(chunk.x, chunk.z);
        }
    }
}
