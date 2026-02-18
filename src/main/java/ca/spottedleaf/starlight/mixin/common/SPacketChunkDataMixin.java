package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import ca.spottedleaf.starlight.common.util.WorldUtil;
import net.minecraft.network.play.server.SPacketChunkData;
import java.util.Arrays;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {

    /**
     * Sync SWMR nibble data to vanilla NibbleArrays BEFORE the constructor serializes chunk data.
     * Must be static because @At("HEAD") on a constructor injects before super().
     */
    @Inject(method = "<init>(Lnet/minecraft/world/chunk/Chunk;I)V", at = @At("HEAD"))
    private static void starlight$beforeSerialize(Chunk chunk, int changedSectionFilter, CallbackInfo ci) {
        if (chunk.getWorld() == null || chunk.getWorld().isRemote) {
            return;
        }

        // Ensure pending light updates are processed before serialization
        final StarLightInterface lightEngine = ((StarLightLightingProvider)chunk.getWorld()).getLightEngine();
        if (lightEngine != null) {
            lightEngine.propagateChanges();
        }

        // Sync SWMR visible data to vanilla NibbleArrays so extractChunkData reads correct light
        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        final SWMRNibbleArray[] blockNibbles = ((ExtendedChunk)chunk).getBlockNibbles();
        final SWMRNibbleArray[] skyNibbles = ((ExtendedChunk)chunk).getSkyNibbles();
        final int minLightSection = WorldUtil.getMinLightSection(chunk.getWorld());
        final boolean hasSkyLight = chunk.getWorld().provider.hasSkyLight();

        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) continue;

            final int lightIndex = i - minLightSection;
            if (lightIndex < 0 || lightIndex >= blockNibbles.length) continue;

            // Update SWMR visible buffers first
            blockNibbles[lightIndex].updateVisible();
            skyNibbles[lightIndex].updateVisible();

            // Zero-copy SWMR visible data into vanilla NibbleArrays.
            // copyVisibleDataInto() writes directly into the existing byte[], avoiding
            // the NibbleArray + byte[] clone allocation overhead of toVanillaNibble().

            // Block light
            final NibbleArray existingBlock = sections[i].getBlockLight();
            if (existingBlock != null) {
                if (!blockNibbles[lightIndex].copyVisibleDataInto(existingBlock.getData())) {
                    // NULL, UNINIT, or HIDDEN → default block light = 0
                    Arrays.fill(existingBlock.getData(), (byte)0);
                }
            }

            // Sky light
            if (hasSkyLight) {
                final NibbleArray existingSky = sections[i].getSkyLight();
                if (existingSky != null) {
                    if (!skyNibbles[lightIndex].copyVisibleDataInto(existingSky.getData())) {
                        // NULL, UNINIT, or HIDDEN → default sky light = 15 (0xFF)
                        Arrays.fill(existingSky.getData(), (byte)0xFF);
                    }
                }
            }
        }
    }
}
