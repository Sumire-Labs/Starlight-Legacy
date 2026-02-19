package ca.spottedleaf.starlight.mixin.common.network;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {

    /**
     * Redirect a call to Chunk.getWorld() in the constructor to force light updates
     * to be processed and nibbles synced before creating the client payload.
     */
    @Redirect(
            method = "<init>(Lnet/minecraft/world/chunk/Chunk;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;getWorld()Lnet/minecraft/world/World;"
            )
    )
    private World processLightUpdatesBeforePacket(final Chunk chunk) {
        final World world = chunk.getWorld();
        // Sync SWMR nibble data to vanilla NibbleArrays for this chunk.
        // No need to drain the entire light queue here — that's handled by the tick handler.
        StarLightInterface.syncNibbleToVanilla(chunk);
        return world;
    }
}
