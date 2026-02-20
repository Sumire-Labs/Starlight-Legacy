package jp.s12kuma01.starlightlegacy.mixin.common.world;

import jp.s12kuma01.starlightlegacy.common.chunk.ExtendedChunk;
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

    @Shadow
    public abstract Chunk getLoadedChunk(int x, int z);

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
     * Ensure chunk is lit when provided to callers, then recheck edges of
     * already-lit cardinal neighbours so light propagates across chunk boundaries.
     */
    @Inject(method = "provideChunk", at = @At("RETURN"))
    private void onProvideChunk(final int x, final int z, final CallbackInfoReturnable<Chunk> cir) {
        final Chunk chunk = cir.getReturnValue();
        if (chunk == null) {
            return;
        }

        final boolean wasLit = ((ExtendedChunk) chunk).isStarlightLit();
        StarLightInterface.ensureChunkLit(this.world, chunk);

        // If this chunk was just freshly lit, recheck edges of loaded neighbours.
        // checkChunkEdges is self-contained (sets up caches, runs BFS, calls updateVisible),
        // so we only need to sync the vanilla NibbleArrays afterwards.
        if (!wasLit) {
            final StarLightInterface lightEngine = ((ExtendedWorld) this.world).getLightEngine();
            if (lightEngine != null) {
                final int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                for (final int[] off : offsets) {
                    final Chunk neighbor = this.getLoadedChunk(x + off[0], z + off[1]);
                    if (neighbor != null && ((ExtendedChunk) neighbor).isStarlightLit()) {
                        lightEngine.checkChunkEdges(x + off[0], z + off[1]);
                        StarLightInterface.syncNibbleToVanilla(neighbor);
                    }
                }
            }
        }
    }
}
