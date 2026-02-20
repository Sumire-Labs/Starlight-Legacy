package jp.s12kuma01.starlightlegacy.mixin.common.world;

import jp.s12kuma01.starlightlegacy.common.light.StarLightInterface;
import jp.s12kuma01.starlightlegacy.common.world.ExtendedWorld;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerMixin {

    @Shadow
    @Final
    public WorldServer world;

    /**
     * Process all pending light updates before saving chunks.
     */
    @Inject(method = "saveChunks", at = @At("HEAD"))
    private void onSaveChunks(final boolean all, final CallbackInfoReturnable<Boolean> cir) {
        final StarLightInterface lightEngine = ((ExtendedWorld) this.world).getLightEngine();
        if (lightEngine != null) {
            lightEngine.propagateChanges();
        }
    }

    /**
     * Process pending light updates during server tick to prevent queue buildup.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(final CallbackInfoReturnable<Boolean> cir) {
        final StarLightInterface lightEngine = ((ExtendedWorld) this.world).getLightEngine();
        if (lightEngine != null && lightEngine.hasUpdates()) {
            lightEngine.propagateChanges();
            // Sync nibbles to vanilla for all loaded chunks that have pending updates
        }
    }

    /**
     * Ensure chunk is lit when provided to callers.
     * Hook into provideChunk to initialize lighting for newly loaded/generated chunks.
     */
    @Inject(method = "provideChunk", at = @At("RETURN"))
    private void onProvideChunk(final int x, final int z, final CallbackInfoReturnable<Chunk> cir) {
        final Chunk chunk = cir.getReturnValue();
        if (chunk != null) {
            StarLightInterface.ensureChunkLit(this.world, chunk);
        }
    }
}
